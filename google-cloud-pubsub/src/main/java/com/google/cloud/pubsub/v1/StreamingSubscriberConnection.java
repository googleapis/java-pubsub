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

import static com.google.cloud.pubsub.v1.Subscriber.DEFAULT_MAX_DURATION_PER_ACK_EXTENSION;
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
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auto.value.AutoValue;
import com.google.cloud.pubsub.v1.MessageDispatcher.AckProcessor;
import com.google.cloud.pubsub.v1.MessageDispatcher.PendingModifyAckDeadline;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
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
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

import io.grpc.protobuf.StatusProto;
import org.threeten.bp.Duration;

/** Implementation of {@link AckProcessor} based on Cloud Pub/Sub streaming pull. */
final class StreamingSubscriberConnection extends AbstractApiService implements AckProcessor {
  private static final Logger logger =
      Logger.getLogger(StreamingSubscriberConnection.class.getName());

  @InternalApi static final Duration DEFAULT_STREAM_ACK_DEADLINE = Duration.ofSeconds(60);
  @InternalApi static final Duration MAX_STREAM_ACK_DEADLINE = Duration.ofSeconds(600);
  @InternalApi static final Duration MIN_STREAM_ACK_DEADLINE = Duration.ofSeconds(10);
  private static final Duration INITIAL_CHANNEL_RECONNECT_BACKOFF = Duration.ofMillis(100);
  private static final Duration MAX_CHANNEL_RECONNECT_BACKOFF = Duration.ofSeconds(10);
  private static final int MAX_PER_REQUEST_CHANGES = 1000;

  private final String PERMANENT_ERROR_METADATA_PREFIX = "PERMANENT_";
  private final String TRANSIENT_ERROR_METADATA_PREFIX = "TRANSIENT_";

  private final Duration streamAckDeadline;
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
    if (builder.maxDurationPerAckExtension.compareTo(DEFAULT_MAX_DURATION_PER_ACK_EXTENSION) == 0) {
      this.streamAckDeadline = DEFAULT_STREAM_ACK_DEADLINE;
    } else if (builder.maxDurationPerAckExtension.compareTo(MIN_STREAM_ACK_DEADLINE) < 0) {
      this.streamAckDeadline = MIN_STREAM_ACK_DEADLINE;
    } else if (builder.maxDurationPerAckExtension.compareTo(MAX_STREAM_ACK_DEADLINE) > 0) {
      this.streamAckDeadline = MAX_STREAM_ACK_DEADLINE;
    } else {
      this.streamAckDeadline = builder.maxDurationPerAckExtension;
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
            .setMaxDurationPerAckExtension(builder.maxDurationPerAckExtension)
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
        // TODO: ModAckDeadline changes
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
      List<String> acksToSend, List<PendingModifyAckDeadline> ackDeadlineExtensions) {
//    send_modacks(ackDeadlineExtensions);
   send_acks(acksToSend);
  }

  private void send_modacks(List<PendingModifyAckDeadline> ackDeadlineExtensions) {
    int pendingOperations = 0;
    for (PendingModifyAckDeadline modack : ackDeadlineExtensions) {
      for (List<String> idChunk : Lists.partition(modack.ackIds, MAX_PER_REQUEST_CHANGES)) {
        ApiFutureCallback<Empty> loggingCallback =
            new ApiFutureCallback<Empty>() {
              @Override
              public void onSuccess(Empty empty) {
                ackOperationsWaiter.incrementPendingCount(-1);
              }

              @Override
              public void onFailure(Throwable t) {
                com.google.rpc.Status status = StatusProto.fromThrowable(t);
                if (status != null) {
                  for (Any any : status.getDetailsList()) {
                    if (any.is(ErrorInfo.class)) {
                      try {
                        ErrorInfo errorInfo = any.unpack(ErrorInfo.class);
                        Map<String, String> metadataMap = errorInfo.getMetadataMap();
                        logger.log(Level.FINE, "failed to send operations. errorInfo.metadataMap", metadataMap);
                      } catch (Throwable throwable) {
                      }
                    }
                  }
                }
                ackOperationsWaiter.incrementPendingCount(-1);
                Level level = isAlive() ? Level.WARNING : Level.FINER;
                logger.log(level, "failed to send operations", t);
              }
            };

        ApiFuture<Empty> future =
            subscriberStub
                .modifyAckDeadlineCallable()
                .futureCall(
                    ModifyAckDeadlineRequest.newBuilder()
                        .setSubscription(subscription)
                        .addAllAckIds(idChunk)
                        .setAckDeadlineSeconds(modack.deadlineExtensionSeconds)
                        .build());
        ApiFutures.addCallback(future, loggingCallback, directExecutor());
        pendingOperations++;
      }
    }
    ackOperationsWaiter.incrementPendingCount(pendingOperations);
  }

  private void send_acks(List<String> acksToSend) {

    if (acksToSend.isEmpty()) {
      return;
    }

    Map<String, SettableApiFuture<Map<String, String>>> ackFutureMap = send_unary_acks(acksToSend);
    AcksToRetryAcksToFail acksToRetryAcksToFail = processAckFutures(ackFutureMap);

    List<String> acksToRetry = acksToRetryAcksToFail.AcksToRetry();
    if (!acksToRetry.isEmpty()) {
      send_acks(acksToRetry);
    }
  }

  private Map<String, SettableApiFuture<Map<String, String>>> send_unary_acks(List<String> acksToSend) {
    int pendingOperations = 0;
    Map<String, SettableApiFuture<Map<String, String>>> futureMap = new HashMap<>();
    for (List<String> idChunk : Lists.partition(acksToSend, MAX_PER_REQUEST_CHANGES)) {
      SettableApiFuture<Map<String, String>> errorFuture = SettableApiFuture.create();
      for (String ackId : idChunk) {
        futureMap.put(ackId, errorFuture);
      }
      ApiFutureCallback<Empty> loggingCallback =
          new ApiFutureCallback<Empty>() {
            @Override
            public void onSuccess(Empty empty) {
              ackOperationsWaiter.incrementPendingCount(-1);
            }

            @Override
            public void onFailure(Throwable t) {
              ackOperationsWaiter.incrementPendingCount(-1);
              com.google.rpc.Status status = StatusProto.fromThrowable(t);
              Map<String, String> metadataMap = new HashMap<>();
              if (status != null) {
                for (Any any : status.getDetailsList()) {
                  if (any.is(ErrorInfo.class)) {
                    try {
                      ErrorInfo errorInfo = any.unpack(ErrorInfo.class);
                      metadataMap = errorInfo.getMetadataMap();
                      errorFuture.set(metadataMap);
                      // remove this log
                      logger.log(Level.FINE, "failed to send operations. errorInfo.metadataMap", metadataMap);
                    } catch (Throwable throwable) {
                    }
                  }
                }
              }
              Level level = isAlive() ? Level.WARNING : Level.FINER;
              logger.log(level, "failed to send operations", t);
            }
          };

      // msg1 - testing-publish-subscribe-exactly-once-subscription-10
       String msg1AckId = "KxkOdxoCUUY3KSIxOw1KX1VYBSEdHEpPPAkeagZSCDtZOz1oa1kQbgJFU35fWhxbaFhZfA9UXx94fWt1QmoLpPzA80hfazkzYF9ccAdUDB17emZ1als7tpOPw-yeeQExUPmi8KdnLcPSiL9HZjc9KBJLLD5-MzVFQV5AEkw4AERJUytDCypYEU4EISE-MD4ZV1BVHA0pQBteXw";
      List<String> badAckIds = new ArrayList<>();
      badAckIds.add(msg1AckId);
      idChunk = badAckIds;
      ApiFuture<Empty> future =
              subscriberStub
                      .acknowledgeCallable()
                      .futureCall(
                              AcknowledgeRequest.newBuilder()
                                      .setSubscription(subscription)
                                      .addAllAckIds(idChunk)
                                      .build());
      ApiFutures.addCallback(future, loggingCallback, directExecutor());
      pendingOperations++;
    }
    ackOperationsWaiter.incrementPendingCount(pendingOperations);
    return futureMap;
  }

  @AutoValue
  abstract static class AcksToRetryAcksToFail {
    abstract List<String> AcksToRetry();
    abstract List<String> AcksToFail();
  }

  private AcksToRetryAcksToFail processAckFutures(Map<String, SettableApiFuture<Map<String, String>>> futureCallbackMap) {
    List<String> acksToRetry = new ArrayList<>();
    List<String> acksToFail = new ArrayList<>();
    futureCallbackMap.forEach(
            (ackId, errorFuture) -> {
              try {
                Map<String, String> metadataMap = errorFuture.get();
                if (metadataMap.containsKey(ackId)) {
                  String errorMessage = metadataMap.get(ackId);
                  if (errorMessage.startsWith(TRANSIENT_ERROR_METADATA_PREFIX)) {
                    acksToRetry.add(ackId);
                    logger.log(Level.WARNING, "Transient error, will retry.", errorMessage);
                  } else if (errorMessage.startsWith(PERMANENT_ERROR_METADATA_PREFIX)) {
                    acksToFail.add(ackId);
                    logger.log(Level.WARNING, "Permanent error, will not retry.", errorMessage);
                  } else {
                    acksToFail.add(ackId);
                    logger.log(Level.WARNING, "unknown error message", errorMessage);
                  }
                }
              } catch (Throwable t) {
                // TODO: Make this to something useful
                logger.log(Level.WARNING, "here for a breakpoint");
              }
            }
    );

    return new AutoValue_StreamingSubscriberConnection_AcksToRetryAcksToFail(acksToRetry, acksToFail);
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
      this.maxDurationPerAckExtension = maxDurationPerAckExtension;
      return this;
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
