/*
 * Copyright 2016 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.pubsub.v1;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.api.core.AbstractApiService;
import com.google.api.core.ApiClock;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.core.InternalApi;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.core.Distribution;
import com.google.api.gax.grpc.GrpcCallContext;
import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.*;
import com.google.cloud.pubsub.v1.MessageDispatcher.AckProcessor;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ModifyAckDeadlineRequest;
import com.google.pubsub.v1.StreamingPullRequest;
import com.google.pubsub.v1.StreamingPullResponse;
import com.google.rpc.ErrorInfo;
import io.grpc.Status;
import io.grpc.protobuf.StatusProto;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.threeten.bp.Duration;

/** Implementation of {@link AckProcessor} based on Cloud Pub/Sub streaming pull. */
final class StreamingSubscriberConnection extends AbstractApiService implements AckProcessor {
  private static final Logger logger =
      Logger.getLogger(StreamingSubscriberConnection.class.getName());

  @InternalApi static final Duration DEFAULT_STREAM_ACK_DEADLINE = Duration.ofSeconds(60);
  @InternalApi static final Duration MAX_STREAM_ACK_DEADLINE = Duration.ofSeconds(600);
  @InternalApi static final Duration MIN_STREAM_ACK_DEADLINE = Duration.ofSeconds(10);

  @InternalApi
  static final Duration STREAM_ACK_DEADLINE_DEFAULT_EXACTLY_ONCE_ENABLED = Duration.ofSeconds(60);

  private static final Duration INITIAL_CHANNEL_RECONNECT_BACKOFF = Duration.ofMillis(100);
  private static final Duration MAX_CHANNEL_RECONNECT_BACKOFF = Duration.ofSeconds(10);
  private static final int MAX_PER_REQUEST_CHANGES = 1000;

  private final String PERMANENT_FAILURE_INVALID_ACK_ID_METADATA =
      "PERMANENT_FAILURE_INVALID_ACK_ID";
  private final String TRANSIENT_FAILURE_METADATA_PREFIX = "TRANSIENT_";

  private Duration streamAckDeadline;
  private final boolean defaultStreamAckDeadline;
  private final SubscriberStub subscriberStub;
  private final int channelAffinity;
  private final String subscription;
  private final ScheduledExecutorService systemExecutor;
  private final MessageDispatcher messageDispatcher;

  private final FlowControlSettings flowControlSettings;
  private final boolean useLegacyFlowControl;

  private AtomicBoolean enableExactlyOnceDelivery;

  private final AtomicLong channelReconnectBackoffMillis =
      new AtomicLong(INITIAL_CHANNEL_RECONNECT_BACKOFF.toMillis());
  private final Waiter ackOperationsWaiter = new Waiter();
  private final ApiClock clock;

  private final Lock lock = new ReentrantLock();
  private ClientStream<StreamingPullRequest> clientStream;

  /**
   * The same clientId is used across all streaming pull connections that are created. This is
   * intentional, as it indicates to the server that any guarantees made for a stream that
   * disconnected will be made for the stream that is created to replace it.
   */
  private final String clientId = UUID.randomUUID().toString();

  private StreamingSubscriberConnection(Builder builder) {
    subscription = builder.subscription;
    systemExecutor = builder.systemExecutor;

    if (builder.maxDurationPerAckExtension != null) {
      this.defaultStreamAckDeadline = false;
      this.streamAckDeadline = builder.maxDurationPerAckExtension;
    } else {
      this.defaultStreamAckDeadline = true;
      if (builder.exactlyOnceDeliveryEnabled) {
        this.streamAckDeadline = STREAM_ACK_DEADLINE_DEFAULT_EXACTLY_ONCE_ENABLED;
      } else {
        this.streamAckDeadline = DEFAULT_STREAM_ACK_DEADLINE;
      }
    }

    subscriberStub = builder.subscriberStub;
    channelAffinity = builder.channelAffinity;
    enableExactlyOnceDelivery = new AtomicBoolean(builder.exactlyOnceDeliveryEnabled);
    clock = builder.clock;

    MessageDispatcher.Builder messageDispatcherBuilder;
    if (builder.receiver != null) {
      messageDispatcherBuilder = MessageDispatcher.newBuilder(builder.receiver);
    } else {
      messageDispatcherBuilder = MessageDispatcher.newBuilder(builder.receiverWithAckResponse);
    }

    messageDispatcher =
        messageDispatcherBuilder
            .setAckProcessor(this)
            .setAckExpirationPadding(builder.ackExpirationPadding)
            .setMaxAckExtensionPeriod(builder.maxAckExtensionPeriod)
            .setMaxDurationPerAckExtension(this.streamAckDeadline)
            .setAckLatencyDistribution(builder.ackLatencyDistribution)
            .setFlowController(builder.flowController)
            .setEnableExactlyOnceDelivery(enableExactlyOnceDelivery.get())
            .setExecutor(builder.executor)
            .setSystemExecutor(builder.systemExecutor)
            .setApiClock(clock)
            .build();

    flowControlSettings = builder.flowControlSettings;
    useLegacyFlowControl = builder.useLegacyFlowControl;
  }

  public Duration getStreamAckDeadline() {
    return streamAckDeadline;
  }

  @Override
  protected void doStart() {
    logger.config("Starting subscriber.");
    messageDispatcher.start();
    initialize();
    notifyStarted();
  }

  @Override
  protected void doStop() {
    runShutdown();

    lock.lock();
    try {
      clientStream.closeSendWithError(Status.CANCELLED.asException());
    } finally {
      lock.unlock();
      notifyStopped();
    }
  }

  private void runShutdown() {
    messageDispatcher.stop();
    ackOperationsWaiter.waitComplete();
  }

  private class StreamingPullResponseObserver implements ResponseObserver<StreamingPullResponse> {

    final SettableApiFuture<Void> errorFuture;

    /**
     * When a batch finsihes processing, we want to request one more batch from the server. But by
     * the time this happens, our stream might have already errored, and new stream created. We
     * don't want to request more batches from the new stream -- that might pull more messages than
     * the user can deal with -- so we save the request observer this response observer is "paired
     * with". If the stream has already errored, requesting more messages is a no-op.
     */
    StreamController thisController;

    StreamingPullResponseObserver(SettableApiFuture<Void> errorFuture) {
      this.errorFuture = errorFuture;
    }

    @Override
    public void onStart(StreamController controller) {
      thisController = controller;
      thisController.disableAutoInboundFlowControl();
      thisController.request(1);
    }

    @Override
    public void onResponse(StreamingPullResponse response) {
      channelReconnectBackoffMillis.set(INITIAL_CHANNEL_RECONNECT_BACKOFF.toMillis());
      boolean isExactlyOnceDeliveryEnabled =
          response.getSubscriptionProperties().getExactlyOnceDeliveryEnabled();
      if (enableExactlyOnceDelivery.get() != isExactlyOnceDeliveryEnabled) {
        enableExactlyOnceDelivery.set(isExactlyOnceDeliveryEnabled);
        messageDispatcher.setEnableExactlyOnceDelivery(isExactlyOnceDeliveryEnabled);

        // Update modack extension defaults if applicable
        if (defaultStreamAckDeadline) {
          messageDispatcher.setMessageDeadlineSeconds(
              Math.toIntExact(STREAM_ACK_DEADLINE_DEFAULT_EXACTLY_ONCE_ENABLED.getSeconds()));
        }
      }

      messageDispatcher.processReceivedMessages(response.getReceivedMessagesList());

      // Only request more if we're not shutdown.
      // If errorFuture is done, the stream has either failed or hung up,
      // and we don't need to request.
      if (isAlive() && !errorFuture.isDone()) {
        lock.lock();
        try {
          thisController.request(1);
        } catch (Exception e) {
          logger.log(Level.WARNING, "cannot request more messages", e);
        } finally {
          lock.unlock();
        }
      }
    }

    @Override
    public void onError(Throwable t) {
      errorFuture.setException(t);
    }

    @Override
    public void onComplete() {
      logger.fine("Streaming pull terminated successfully!");
      errorFuture.set(null);
    }
  }

  private void initialize() {
    final SettableApiFuture<Void> errorFuture = SettableApiFuture.create();
    final ResponseObserver<StreamingPullResponse> responseObserver =
        new StreamingPullResponseObserver(errorFuture);
    ClientStream<StreamingPullRequest> initClientStream =
        subscriberStub
            .streamingPullCallable()
            .splitCall(
                responseObserver,
                GrpcCallContext.createDefault().withChannelAffinity(channelAffinity));

    logger.log(Level.FINER, "Initializing stream to subscription {0}", subscription);
    // We need to set streaming ack deadline, but it's not useful since we'll modack to send receipt
    // anyway. Set to some big-ish value in case we modack late.
    initClientStream.send(
        StreamingPullRequest.newBuilder()
            .setSubscription(subscription)
            .setStreamAckDeadlineSeconds((int) streamAckDeadline.getSeconds())
            .setClientId(clientId)
            .setMaxOutstandingMessages(
                this.useLegacyFlowControl
                    ? 0
                    : valueOrZero(flowControlSettings.getMaxOutstandingElementCount()))
            .setMaxOutstandingBytes(
                this.useLegacyFlowControl
                    ? 0
                    : valueOrZero(flowControlSettings.getMaxOutstandingRequestBytes()))
            .build());

    /**
     * Must make sure we do this after sending the subscription name and deadline. Otherwise, some
     * other thread might use this stream to do something else before we could send the first
     * request.
     */
    lock.lock();
    try {
      this.clientStream = initClientStream;
    } finally {
      lock.unlock();
    }

    ApiFutures.addCallback(
        errorFuture,
        new ApiFutureCallback<Void>() {
          @Override
          public void onSuccess(@Nullable Void result) {
            if (!isAlive()) {
              return;
            }
            channelReconnectBackoffMillis.set(INITIAL_CHANNEL_RECONNECT_BACKOFF.toMillis());
            // The stream was closed. And any case we want to reopen it to continue receiving
            // messages.
            initialize();
          }

          @Override
          public void onFailure(Throwable cause) {
            if (!isAlive()) {
              // we don't care about subscription failures when we're no longer running.
              logger.log(Level.FINE, "pull failure after service no longer running", cause);
              return;
            }
            if (!StatusUtil.isRetryable(cause)) {
              ApiException gaxException =
                  ApiExceptionFactory.createException(
                      cause, GrpcStatusCode.of(Status.fromThrowable(cause).getCode()), false);
              logger.log(Level.SEVERE, "terminated streaming with exception", gaxException);
              runShutdown();
              notifyFailed(gaxException);
              return;
            }
            logger.log(Level.FINE, "stream closed with retryable exception; will reconnect", cause);
            long backoffMillis = channelReconnectBackoffMillis.get();
            long newBackoffMillis =
                Math.min(backoffMillis * 2, MAX_CHANNEL_RECONNECT_BACKOFF.toMillis());
            channelReconnectBackoffMillis.set(newBackoffMillis);

            systemExecutor.schedule(
                new Runnable() {
                  @Override
                  public void run() {
                    initialize();
                  }
                },
                backoffMillis,
                TimeUnit.MILLISECONDS);
          }
        },
        MoreExecutors.directExecutor());
  }

  private Long valueOrZero(Long value) {
    return value != null ? value : 0;
  }

  private boolean isAlive() {
    State state = state(); // Read the state only once.
    return state == State.RUNNING || state == State.STARTING;
  }

  @Override
  public void sendAckOperations(
      List<ModackWithMessageFuture> modackWithMessageFutures,
      List<AckIdMessageFuture> ackIdMessageFutures) {
    // Process modacks first
    Set<String> failedAckIds = sendModacks(modackWithMessageFutures);

    // Remove failed modacks from acks
    ackIdMessageFutures.removeIf(
        ackIdMessageFuture -> {
          return failedAckIds.contains(ackIdMessageFuture.getAckId());
        });

    sendAcks(ackIdMessageFutures);
  }

  private class RetryModacksFailedModacks {
    private List<ModackWithMessageFuture> retryModackWithMessageFutures;
    private Set<String> failedAckIds;

    RetryModacksFailedModacks(
        List<ModackWithMessageFuture> retryModackWithMessageFutures, Set<String> failedAckIds) {
      this.retryModackWithMessageFutures = retryModackWithMessageFutures;
      this.failedAckIds = failedAckIds;
    }
  }

  private Map<String, String> getMetadataMapFromThrowable(Throwable t) {
    // This converts a Throwable (from a "OK" grpc response) to a map of metadata
    // will be of the format:
    // {
    //    "ACK-ID-1": "PERMANENT_*",
    //    "ACK-ID-2": "TRANSIENT_*"
    // }
    com.google.rpc.Status status = StatusProto.fromThrowable(t);
    Map<String, String> metadataMap = new HashMap<>();
    if (status != null) {
      for (Any any : status.getDetailsList()) {
        if (any.is(ErrorInfo.class)) {
          try {
            ErrorInfo errorInfo = any.unpack(ErrorInfo.class);
            metadataMap = errorInfo.getMetadataMap();
          } catch (Throwable throwable) {
          }
        }
      }
    }
    return metadataMap;
  }

  private ApiFutureCallback<Empty> getLoggingCallback(
      SettableApiFuture<Map<String, String>> responseFuture) {
    return new ApiFutureCallback<Empty>() {
      @Override
      public void onSuccess(Empty empty) {
        ackOperationsWaiter.incrementPendingCount(-1);
        responseFuture.set(new HashMap<>());
      }

      @Override
      public void onFailure(Throwable t) {
        ackOperationsWaiter.incrementPendingCount(-1);
        Map<String, String> metadataMap = getMetadataMapFromThrowable(t);
        if (!metadataMap.isEmpty()) {
          responseFuture.set(metadataMap);
        }
        Level level = isAlive() ? Level.WARNING : Level.FINER;
        logger.log(level, "failed to send operations", t);
      }
    };
  }

  private ApiFutureCallback<Empty> getLoggingCallback() {
    return new ApiFutureCallback<Empty>() {
      @Override
      public void onSuccess(Empty empty) {
        ackOperationsWaiter.incrementPendingCount(-1);
      }

      @Override
      public void onFailure(Throwable t) {
        ackOperationsWaiter.incrementPendingCount(-1);
        Level level = isAlive() ? Level.WARNING : Level.FINER;
        logger.log(level, "failed to send operations", t);
      }
    };
  }

  private Set<String> sendModacks(List<ModackWithMessageFuture> modacksToSend) {
    // We want to send modacks (and retry failures),
    // then process the results - propagating permanent failures back to the client via the Message
    // future

    // Split our modacks (if needed) so we have the correct batch size per request - we do this here
    // so we
    // easily keep track of the requests in our map
    List<ModackWithMessageFuture> modacksToSendPartitioned =
        ModackWithMessageFuture.partitionByAckId(modacksToSend, MAX_PER_REQUEST_CHANGES);
    Map<ModackWithMessageFuture, SettableApiFuture<Map<String, String>>> modAckFutureMap =
        sendUnaryModacks(modacksToSendPartitioned);
    RetryModacksFailedModacks retryModacksFailedModacks = processModackFutures(modAckFutureMap);

    Set<String> failedAckIds = retryModacksFailedModacks.failedAckIds;

    if (!retryModacksFailedModacks.retryModackWithMessageFutures.isEmpty()) {
      failedAckIds.addAll(sendModacks(retryModacksFailedModacks.retryModackWithMessageFutures));
    }

    return failedAckIds;
  }

  private Map<ModackWithMessageFuture, SettableApiFuture<Map<String, String>>> sendUnaryModacks(
      List<ModackWithMessageFuture> modackWithMessageFutures) {
    int pendingOperations = 0;
    Map<ModackWithMessageFuture, SettableApiFuture<Map<String, String>>> futureMap =
        new HashMap<ModackWithMessageFuture, SettableApiFuture<Map<String, String>>>();
    for (ModackWithMessageFuture modackWithMessageFuture : modackWithMessageFutures) {
      List<String> ackIdsInRequest = new ArrayList<String>();
      ApiFutureCallback<Empty> loggingCallback;
      for (AckIdMessageFuture ackIdMessageFuture :
          modackWithMessageFuture.getAckIdMessageFutures()) {
        ackIdsInRequest.add(ackIdMessageFuture.getAckId());
      }

      if (enableExactlyOnceDelivery.get()) {
        // If enableExactlyOnceDelivery is true:
        // 1) make a response future
        // 2) add it to our future map
        // 3) add it to the logging callback
        SettableApiFuture<Map<String, String>> responseFuture = SettableApiFuture.create();
        futureMap.put(modackWithMessageFuture, responseFuture);
        loggingCallback = getLoggingCallback(responseFuture);
      } else {
        loggingCallback = getLoggingCallback();
      }
      ApiFuture<Empty> future =
          subscriberStub
              .modifyAckDeadlineCallable()
              .futureCall(
                  ModifyAckDeadlineRequest.newBuilder()
                      .setSubscription(subscription)
                      .addAllAckIds(ackIdsInRequest)
                      .setAckDeadlineSeconds(modackWithMessageFuture.getDeadlineExtensionSeconds())
                      .build());
      ApiFutures.addCallback(future, loggingCallback, directExecutor());
      pendingOperations++;
    }
    ackOperationsWaiter.incrementPendingCount(pendingOperations);
    return futureMap;
  }

  private RetryModacksFailedModacks processModackFutures(
      Map<ModackWithMessageFuture, SettableApiFuture<Map<String, String>>>
          modackWithMessageFutureMap) {
    Map<Integer, ModackWithMessageFuture> modacksToRetryMap =
        new HashMap<Integer, ModackWithMessageFuture>();
    Set<String> modackIdsFailed = new HashSet<String>();
    modackWithMessageFutureMap.forEach(
        (modackWithMessageFuture, settableApiFuture) -> {
          try {
            Map<String, String> metadataMap = settableApiFuture.get();
            modackWithMessageFuture
                .getAckIdMessageFutures()
                .forEach(
                    (ackIdMessageFuture) -> {
                      if (metadataMap.containsKey(ackIdMessageFuture.getAckId())) {
                        String errorMessage = metadataMap.get(ackIdMessageFuture.getAckId());
                        if (errorMessage.startsWith(TRANSIENT_FAILURE_METADATA_PREFIX)) {
                          // Retry all "TRANSIENT_*" error messages - do not set message future
                          logger.log(
                              Level.WARNING, "Transient error message, will resend", errorMessage);
                          ModackWithMessageFuture modacksToRetryMapEntry =
                              modacksToRetryMap.computeIfAbsent(
                                  modackWithMessageFuture.getDeadlineExtensionSeconds(),
                                  deadlineExtensionSeconds ->
                                      new ModackWithMessageFuture(deadlineExtensionSeconds));
                          modacksToRetryMapEntry.addAckIdMessageFuture(ackIdMessageFuture);
                        } else if (errorMessage.startsWith(
                            PERMANENT_FAILURE_INVALID_ACK_ID_METADATA)) {
                          logger.log(
                              Level.WARNING,
                              "Permanent error invalid ack id message, will not resend",
                              errorMessage);
                          ackIdMessageFuture.getMessageFuture().set(AckResponse.INVALID);
                          modackIdsFailed.add(ackIdMessageFuture.getAckId());
                        } else {
                          logger.log(
                              Level.WARNING,
                              "Unknown error message, will not resend",
                              errorMessage);
                          ackIdMessageFuture.getMessageFuture().set(AckResponse.OTHER);
                          modackIdsFailed.add(ackIdMessageFuture.getAckId());
                        }
                        // Check if nack - we only propagate success to the message if this is a
                        // nack
                      } else if (modackWithMessageFuture.getDeadlineExtensionSeconds() == 0) {
                        // Make sure this is not a failure nack which will have a completed future
                        if (ackIdMessageFuture.getMessageFuture().isDone()) {
                          modackIdsFailed.add(ackIdMessageFuture.getAckId());
                        } else {
                          ackIdMessageFuture.getMessageFuture().set(AckResponse.SUCCESSFUL);
                        }
                      }
                    });
          } catch (InterruptedException | ExecutionException t) {
            // Exception caused by accesssing the future, not from the future so we should retry
            logger.log(Level.WARNING, "Failed to retrieve future, resending modacks.", t);
            ModackWithMessageFuture modacksToRetryMapEntry =
                modacksToRetryMap.computeIfAbsent(
                    modackWithMessageFuture.getDeadlineExtensionSeconds(),
                    deadlineExtensionSeconds ->
                        new ModackWithMessageFuture(deadlineExtensionSeconds));
            modacksToRetryMapEntry.addAllAckIdMessageFuture(
                modackWithMessageFuture.getAckIdMessageFutures());
          }
        });
    return new RetryModacksFailedModacks(
        new ArrayList<ModackWithMessageFuture>(modacksToRetryMap.values()), modackIdsFailed);
  }

  private void sendAcks(List<AckIdMessageFuture> ackIdWithMessageFutureToSend) {
    Map<AckIdMessageFuture, SettableApiFuture<Map<String, String>>> ackFutureMap =
        sendUnaryAcks(ackIdWithMessageFutureToSend);
    List<AckIdMessageFuture> acksToResend = processAckFutures(ackFutureMap);

    if (!acksToResend.isEmpty()) {
      // TODO: Do we want to use exponential backoff here?
      sendAcks(acksToResend);
    }
  }

  private Map<AckIdMessageFuture, SettableApiFuture<Map<String, String>>> sendUnaryAcks(
      List<AckIdMessageFuture> ackIdMessageFutureListToSend) {
    int pendingOperations = 0;
    Map<AckIdMessageFuture, SettableApiFuture<Map<String, String>>> futureMap =
        new HashMap<AckIdMessageFuture, SettableApiFuture<Map<String, String>>>();
    for (List<AckIdMessageFuture> ackIdWithMessageFutureInRequest :
        Lists.partition(ackIdMessageFutureListToSend, MAX_PER_REQUEST_CHANGES)) {
      List<String> ackIdsInRequest = new ArrayList<>();
      ApiFutureCallback<Empty> loggingCallback;
      if (enableExactlyOnceDelivery.get()) {
        // If enableExactlyOnceDelivery is true:
        // 1) make a response future
        // 2) add it to our map
        // 3) add it to the logging callback
        // 4) populate the ackIds for the request
        SettableApiFuture<Map<String, String>> responseFuture = SettableApiFuture.create();
        for (AckIdMessageFuture ackIdMessageFuture : ackIdWithMessageFutureInRequest) {
          futureMap.put(ackIdMessageFuture, responseFuture);
          ackIdsInRequest.add(ackIdMessageFuture.getAckId());
        }
        loggingCallback = getLoggingCallback(responseFuture);
      } else {
        // else, we just need to populate the ackIds for the request
        for (AckIdMessageFuture ackIdMessageFuture : ackIdWithMessageFutureInRequest) {
          ackIdsInRequest.add(ackIdMessageFuture.getAckId());
        }
        loggingCallback = getLoggingCallback();
      }
      ApiFuture<Empty> future =
          subscriberStub
              .acknowledgeCallable()
              .futureCall(
                  AcknowledgeRequest.newBuilder()
                      .setSubscription(subscription)
                      .addAllAckIds(ackIdsInRequest)
                      .build());
      ApiFutures.addCallback(future, loggingCallback, directExecutor());
      pendingOperations++;
    }
    ackOperationsWaiter.incrementPendingCount(pendingOperations);
    return futureMap;
  }

  private List<AckIdMessageFuture> processAckFutures(
      Map<AckIdMessageFuture, SettableApiFuture<Map<String, String>>> futureMap) {
    List<AckIdMessageFuture> retryAckIdsWithMessageFuture = new ArrayList<>();
    futureMap.forEach(
        (ackIdMessageFuture, future) -> {
          try {
            String ackId = ackIdMessageFuture.getAckId();
            // Blocking operation to check the response of the ack request
            Map<String, String> metadataMap = future.get();
            if (metadataMap.containsKey(ackId)) {
              String errorMessage = metadataMap.get(ackId);
              if (errorMessage.startsWith(TRANSIENT_FAILURE_METADATA_PREFIX)) {
                // Retry and do not set message future
                logger.log(Level.WARNING, "Transient error message, will resend", errorMessage);
                retryAckIdsWithMessageFuture.add(ackIdMessageFuture);
              } else if (errorMessage.startsWith(PERMANENT_FAILURE_INVALID_ACK_ID_METADATA)) {
                logger.log(Level.WARNING, "Permanent error message, will not resend", errorMessage);
                ackIdMessageFuture.getMessageFuture().set(AckResponse.INVALID);
              } else {
                logger.log(Level.WARNING, "Unknown error message, will not resend", errorMessage);
                ackIdMessageFuture.getMessageFuture().set(AckResponse.OTHER);
              }
            } else {
              ackIdMessageFuture.getMessageFuture().set(AckResponse.SUCCESSFUL);
            }
          } catch (Throwable t) {
            logger.log(Level.WARNING, "Failed to retrieve future. resending ackIds", t);
            retryAckIdsWithMessageFuture.add(ackIdMessageFuture);
          }
        });
    return retryAckIdsWithMessageFuture;
  }

  /** Builder of {@link StreamingSubscriberConnection StreamingSubscriberConnections}. */
  public static final class Builder {
    private MessageReceiver receiver;
    private MessageReceiverWithAckResponse receiverWithAckResponse;
    private String subscription;
    private Duration ackExpirationPadding;
    private Duration maxAckExtensionPeriod;
    private Duration maxDurationPerAckExtension;
    private Distribution ackLatencyDistribution;
    private SubscriberStub subscriberStub;
    private int channelAffinity;
    private FlowController flowController;
    private FlowControlSettings flowControlSettings;
    private boolean exactlyOnceDeliveryEnabled;
    private boolean useLegacyFlowControl;
    private ScheduledExecutorService executor;
    private ScheduledExecutorService systemExecutor;
    private ApiClock clock;

    Builder(MessageReceiver receiver) {
      this.receiver = receiver;
    }

    Builder(MessageReceiverWithAckResponse receiverWithAckResponse) {
      this.receiverWithAckResponse = receiverWithAckResponse;
    }

    public Builder setSubscription(String subscription) {
      this.subscription = subscription;
      return this;
    }

    public Builder setAckExpirationPadding(Duration ackExpirationPadding) {
      this.ackExpirationPadding = ackExpirationPadding;
      return this;
    }

    public Builder setMaxAckExtensionPeriod(Duration maxAckExtensionPeriod) {
      this.maxAckExtensionPeriod = maxAckExtensionPeriod;
      return this;
    }

    public Builder setMaxDurationPerAckExtension(Duration maxDurationPerAckExtension) {
      if (maxDurationPerAckExtension.compareTo(MIN_STREAM_ACK_DEADLINE) < 0) {
        logger.log(
            Level.WARNING,
            "maxDurationPerAckExtension too small, should be >= {0} seconds",
            MIN_STREAM_ACK_DEADLINE.getSeconds());
        this.maxDurationPerAckExtension = MIN_STREAM_ACK_DEADLINE;
      } else if (maxDurationPerAckExtension.compareTo(MAX_STREAM_ACK_DEADLINE) > 0) {
        logger.log(
            Level.WARNING,
            "maxDurationPerAckExtension too large, should be <= {0} seconds",
            MAX_STREAM_ACK_DEADLINE.getSeconds());
        this.maxDurationPerAckExtension = MAX_STREAM_ACK_DEADLINE;
      } else {
        this.maxDurationPerAckExtension = maxDurationPerAckExtension;
      }
      return this;
    }

    public Duration getMaxDurationPerAckExtension() {
      return maxDurationPerAckExtension;
    }

    public Builder setAckLatencyDistribution(Distribution ackLatencyDistribution) {
      this.ackLatencyDistribution = ackLatencyDistribution;
      return this;
    }

    public Builder setSubscriberStub(SubscriberStub subscriberStub) {
      this.subscriberStub = subscriberStub;
      return this;
    }

    public Builder setChannelAffinity(int channelAffinity) {
      this.channelAffinity = channelAffinity;
      return this;
    }

    public Builder setFlowController(FlowController flowController) {
      this.flowController = flowController;
      return this;
    }

    public Builder setFlowControlSettings(FlowControlSettings flowControlSettings) {
      this.flowControlSettings = flowControlSettings;
      return this;
    }

    public Builder setUseLegacyFlowControl(boolean useLegacyFlowControl) {
      this.useLegacyFlowControl = useLegacyFlowControl;
      return this;
    }

    public Builder setExactlyOnceDeliveryEnabled(boolean exactlyOnceDeliveryEnabled) {
      // If the receiverWithAckResponse has been set, we must have exactlyOnceDelivery enabled
      Preconditions.checkArgument(
          (this.receiverWithAckResponse == null)
              || ((this.receiverWithAckResponse != null) && (exactlyOnceDeliveryEnabled)));
      this.exactlyOnceDeliveryEnabled = exactlyOnceDeliveryEnabled;
      return this;
    }

    public Builder setExecutor(ScheduledExecutorService executor) {
      this.executor = executor;
      return this;
    }

    public Builder setSystemExecutor(ScheduledExecutorService systemExecutor) {
      this.systemExecutor = systemExecutor;
      return this;
    }

    public Builder setClock(ApiClock clock) {
      this.clock = clock;
      return this;
    }

    public StreamingSubscriberConnection build() {
      return new StreamingSubscriberConnection(this);
    }
  }

  public static Builder newBuilder(MessageReceiver receiver) {
    return new Builder(receiver);
  }

  public static Builder newBuilder(MessageReceiverWithAckResponse receiverWithAckResponse) {
    return new Builder(receiverWithAckResponse);
  }
}
