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

import com.google.api.core.ApiClock;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.core.InternalApi;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.batching.FlowController.FlowControlException;
import com.google.api.gax.core.Distribution;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.ReceivedMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;
import org.threeten.bp.temporal.ChronoUnit;

/**
 * Dispatches messages to a message receiver while handling the messages acking and lease
 * extensions.
 */
class MessageDispatcher {
  private static final Logger logger = Logger.getLogger(MessageDispatcher.class.getName());
  private static final double PERCENTILE_FOR_ACK_DEADLINE_UPDATES = 99.9;

  @InternalApi static final Duration PENDING_ACKS_SEND_DELAY = Duration.ofMillis(100);

  private final Executor executor;
  private final SequentialExecutorService.AutoExecutor sequentialExecutor;
  private final ScheduledExecutorService systemExecutor;
  private final ApiClock clock;

  private final Duration ackExpirationPadding;
  private final Duration maxAckExtensionPeriod;
  private final int maxSecondsPerAckExtension;
  private final MessageReceiver receiver;
  private final AckProcessor ackProcessor;

  private final FlowController flowController;
  private final Waiter messagesWaiter;

  // Maps ID to "total expiration time". If it takes longer than this, stop extending.
  private final ConcurrentMap<String, AckHandler> pendingMessages = new ConcurrentHashMap<>();

  private final LinkedBlockingQueue<String> pendingAcks = new LinkedBlockingQueue<>();
  private final LinkedBlockingQueue<String> pendingNacks = new LinkedBlockingQueue<>();
  private final LinkedBlockingQueue<String> pendingReceipts = new LinkedBlockingQueue<>();

  // Start the deadline at the minimum ack deadline so messages which arrive before this is
  // updated will not have a long ack deadline.
  private final AtomicInteger messageDeadlineSeconds =
      new AtomicInteger(Subscriber.MIN_ACK_DEADLINE_SECONDS);
  private final AtomicBoolean extendDeadline = new AtomicBoolean(true);
  private final Lock jobLock;
  private ScheduledFuture<?> backgroundJob;
  private ScheduledFuture<?> setExtendedDeadlineFuture;

  // To keep track of number of seconds the receiver takes to process messages.
  private final Distribution ackLatencyDistribution;

  /** Stores the data needed to asynchronously modify acknowledgement deadlines. */
  static class PendingModifyAckDeadline {
    final List<String> ackIds;
    final int deadlineExtensionSeconds;

    PendingModifyAckDeadline(int deadlineExtensionSeconds, String... ackIds) {
      this(deadlineExtensionSeconds, Arrays.asList(ackIds));
    }

    private PendingModifyAckDeadline(int deadlineExtensionSeconds, Collection<String> ackIds) {
      this.ackIds = new ArrayList<String>(ackIds);
      this.deadlineExtensionSeconds = deadlineExtensionSeconds;
    }

    @Override
    public String toString() {
      return String.format(
          "PendingModifyAckDeadline{extension: %d sec, ackIds: %s}",
          deadlineExtensionSeconds, ackIds);
    }
  }

  /** Internal representation of a reply to a Pubsub message, to be sent back to the service. */
  public enum AckReply {
    ACK,
    NACK
  }

  /** Handles callbacks for acking/nacking messages from the {@link MessageReceiver}. */
  private class AckHandler implements ApiFutureCallback<AckReply> {
    private final String ackId;
    private final int outstandingBytes;
    private final long receivedTimeMillis;
    private final Instant totalExpiration;

    private AckHandler(String ackId, int outstandingBytes, Instant totalExpiration) {
      this.ackId = ackId;
      this.outstandingBytes = outstandingBytes;
      this.receivedTimeMillis = clock.millisTime();
      this.totalExpiration = totalExpiration;
    }

    /** Stop extending deadlines for this message and free flow control. */
    private void forget() {
      if (pendingMessages.remove(ackId) == null) {
        /*
         * We're forgetting the message for the second time. Probably because we ran out of total
         * expiration, forget the message, then the user finishes working on the message, and forget
         * again. Turn the second forget into a no-op so we don't free twice.
         */
        return;
      }
      flowController.release(1, outstandingBytes);
      messagesWaiter.incrementPendingCount(-1);
    }

    @Override
    public void onFailure(Throwable t) {
      logger.log(
          Level.WARNING,
          "MessageReceiver failed to process ack ID: " + ackId + ", the message will be nacked.",
          t);
      pendingNacks.add(ackId);
      forget();
    }

    @Override
    public void onSuccess(AckReply reply) {
      LinkedBlockingQueue<String> destination;
      switch (reply) {
        case ACK:
          destination = pendingAcks;
          // Record the latency rounded to the next closest integer.
          ackLatencyDistribution.record(
              Ints.saturatedCast(
                  (long) Math.ceil((clock.millisTime() - receivedTimeMillis) / 1000D)));
          break;
        case NACK:
          destination = pendingNacks;
          break;
        default:
          throw new IllegalArgumentException(String.format("AckReply: %s not supported", reply));
      }
      destination.add(ackId);
      forget();
    }
  }

  interface AckProcessor {
    void sendAckOperations(
        List<String> acksToSend, List<PendingModifyAckDeadline> ackDeadlineExtensions);
  }

  MessageDispatcher(
      MessageReceiver receiver,
      AckProcessor ackProcessor,
      Duration ackExpirationPadding,
      Duration maxAckExtensionPeriod,
      Duration maxDurationPerAckExtension,
      Distribution ackLatencyDistribution,
      FlowController flowController,
      Executor executor,
      ScheduledExecutorService systemExecutor,
      ApiClock clock) {
    this.executor = executor;
    this.systemExecutor = systemExecutor;
    this.ackExpirationPadding = ackExpirationPadding;
    this.maxAckExtensionPeriod = maxAckExtensionPeriod;
    this.maxSecondsPerAckExtension = Math.toIntExact(maxDurationPerAckExtension.getSeconds());
    this.receiver = receiver;
    this.ackProcessor = ackProcessor;
    this.flowController = flowController;
    // 601 buckets of 1s resolution from 0s to MAX_ACK_DEADLINE_SECONDS
    this.ackLatencyDistribution = ackLatencyDistribution;
    jobLock = new ReentrantLock();
    messagesWaiter = new Waiter();
    this.clock = clock;
    this.sequentialExecutor = new SequentialExecutorService.AutoExecutor(executor);
  }

  void start() {
    final Runnable setExtendDeadline =
        new Runnable() {
          @Override
          public void run() {
            extendDeadline.set(true);
          }
        };

    jobLock.lock();
    try {
      // Do not adjust deadline concurrently with extendDeadline or processOutstandingAckOperations.
      // The following sequence can happen:
      //  0. Initially, deadline = 1 min
      //  1. Thread A (TA) wants to send receipts, reads deadline = 1m, but stalls before actually
      // sending request
      //  2. Thread B (TB) adjusts deadline to 2m
      //  3. TB calls extendDeadline, modack all messages to 2m, schedules next extension in 2m
      //  4. TA sends request, modacking messages to 1m.
      // Then messages will expire too early.
      // This can be resolved by adding locks in the right places, but at that point,
      // we might as well do things sequentially.
      backgroundJob =
          systemExecutor.scheduleWithFixedDelay(
              new Runnable() {
                @Override
                public void run() {
                  try {
                    if (extendDeadline.getAndSet(false)) {
                      int newDeadlineSec = computeDeadlineSeconds();
                      messageDeadlineSeconds.set(newDeadlineSec);
                      extendDeadlines();
                      if (setExtendedDeadlineFuture != null && !backgroundJob.isDone()) {
                        setExtendedDeadlineFuture.cancel(true);
                      }

                      setExtendedDeadlineFuture =
                          systemExecutor.schedule(
                              setExtendDeadline,
                              newDeadlineSec - ackExpirationPadding.getSeconds(),
                              TimeUnit.SECONDS);
                    }
                    processOutstandingAckOperations();
                  } catch (Throwable t) {
                    // Catch everything so that one run failing doesn't prevent subsequent runs.
                    logger.log(Level.WARNING, "failed to run periodic job", t);
                  }
                }
              },
              PENDING_ACKS_SEND_DELAY.toMillis(),
              PENDING_ACKS_SEND_DELAY.toMillis(),
              TimeUnit.MILLISECONDS);
    } finally {
      jobLock.unlock();
    }
  }

  void stop() {
    messagesWaiter.waitComplete();
    jobLock.lock();
    try {
      if (backgroundJob != null) {
        backgroundJob.cancel(false);
      }
      if (setExtendedDeadlineFuture != null) {
        setExtendedDeadlineFuture.cancel(true);
      }
      backgroundJob = null;
      setExtendedDeadlineFuture = null;
    } finally {
      jobLock.unlock();
    }
    processOutstandingAckOperations();
  }

  @InternalApi
  void setMessageDeadlineSeconds(int sec) {
    messageDeadlineSeconds.set(sec);
  }

  @InternalApi
  int getMessageDeadlineSeconds() {
    return messageDeadlineSeconds.get();
  }

  private static class OutstandingMessage {
    private final ReceivedMessage receivedMessage;
    private final AckHandler ackHandler;

    private OutstandingMessage(ReceivedMessage receivedMessage, AckHandler ackHandler) {
      this.receivedMessage = receivedMessage;
      this.ackHandler = ackHandler;
    }
  }

  void processReceivedMessages(List<ReceivedMessage> messages) {
    Instant totalExpiration = now().plus(maxAckExtensionPeriod);
    List<OutstandingMessage> outstandingBatch = new ArrayList<>(messages.size());
    for (ReceivedMessage message : messages) {
      AckHandler ackHandler =
          new AckHandler(
              message.getAckId(), message.getMessage().getSerializedSize(), totalExpiration);
      if (pendingMessages.putIfAbsent(message.getAckId(), ackHandler) != null) {
        // putIfAbsent puts ackHandler if ackID isn't previously mapped, then return the
        // previously-mapped element.
        // If the previous element is not null, we already have the message and the new one is
        // definitely a duplicate.
        // Don't nack this, because that'd also nack the one we already have in queue.
        // Don't update the existing one's total expiration either. If the user "loses" the message,
        // we want to eventually
        // totally expire so that pubsub service sends us the message again.
        continue;
      }
      outstandingBatch.add(new OutstandingMessage(message, ackHandler));
      pendingReceipts.add(message.getAckId());
    }

    processBatch(outstandingBatch);
  }

  private void processBatch(List<OutstandingMessage> batch) {
    messagesWaiter.incrementPendingCount(batch.size());
    for (OutstandingMessage message : batch) {
      // This is a blocking flow controller.  We have already incremented messagesWaiter, so
      // shutdown will block on processing of all these messages anyway.
      try {
        flowController.reserve(1, message.receivedMessage.getMessage().getSerializedSize());
      } catch (FlowControlException unexpectedException) {
        // This should be a blocking flow controller and never throw an exception.
        throw new IllegalStateException("Flow control unexpected exception", unexpectedException);
      }
      processOutstandingMessage(addDeliveryInfoCount(message.receivedMessage), message.ackHandler);
    }
  }

  private PubsubMessage addDeliveryInfoCount(ReceivedMessage receivedMessage) {
    PubsubMessage originalMessage = receivedMessage.getMessage();
    int deliveryAttempt = receivedMessage.getDeliveryAttempt();
    // Delivery Attempt will be set to 0 if DeadLetterPolicy is not set on the subscription. In
    // this case, do not populate the PubsubMessage with the delivery attempt attribute.
    if (deliveryAttempt > 0) {
      return PubsubMessage.newBuilder(originalMessage)
          .putAttributes("googclient_deliveryattempt", Integer.toString(deliveryAttempt))
          .build();
    }
    return originalMessage;
  }

  private void processOutstandingMessage(final PubsubMessage message, final AckHandler ackHandler) {
    final SettableApiFuture<AckReply> response = SettableApiFuture.create();
    final AckReplyConsumer consumer =
        new AckReplyConsumer() {
          @Override
          public void ack() {
            response.set(AckReply.ACK);
          }

          @Override
          public void nack() {
            response.set(AckReply.NACK);
          }
        };
    ApiFutures.addCallback(response, ackHandler, MoreExecutors.directExecutor());
    Runnable deliverMessageTask =
        new Runnable() {
          @Override
          public void run() {
            try {
              if (ackHandler
                  .totalExpiration
                  .plusSeconds(messageDeadlineSeconds.get())
                  .isBefore(now())) {
                // Message expired while waiting. We don't extend these messages anymore,
                // so it was probably sent to someone else. Don't work on it.
                // Don't nack it either, because we'd be nacking someone else's message.
                ackHandler.forget();
                return;
              }

              receiver.receiveMessage(message, consumer);
            } catch (Exception e) {
              response.setException(e);
            }
          }
        };
    if (message.getOrderingKey().isEmpty()) {
      executor.execute(deliverMessageTask);
    } else {
      sequentialExecutor.submit(message.getOrderingKey(), deliverMessageTask);
    }
  }

  /** Compute the ideal deadline, set subsequent modacks to this deadline, and return it. */
  @InternalApi
  int computeDeadlineSeconds() {
    int sec = ackLatencyDistribution.getPercentile(PERCENTILE_FOR_ACK_DEADLINE_UPDATES);

    if ((maxSecondsPerAckExtension > 0) && (sec > maxSecondsPerAckExtension)) {
      sec = maxSecondsPerAckExtension;
    }

    // Use Ints.constrainToRange when we get guava 21.
    if (sec < Subscriber.MIN_ACK_DEADLINE_SECONDS) {
      sec = Subscriber.MIN_ACK_DEADLINE_SECONDS;
    } else if (sec > Subscriber.MAX_ACK_DEADLINE_SECONDS) {
      sec = Subscriber.MAX_ACK_DEADLINE_SECONDS;
    }
    return sec;
  }

  @InternalApi
  void extendDeadlines() {
    int extendSeconds = getMessageDeadlineSeconds();
    List<PendingModifyAckDeadline> modacks = new ArrayList<>();
    PendingModifyAckDeadline modack = new PendingModifyAckDeadline(extendSeconds);
    Instant now = now();
    Instant extendTo = now.plusSeconds(extendSeconds);

    for (Map.Entry<String, AckHandler> entry : pendingMessages.entrySet()) {
      String ackId = entry.getKey();
      Instant totalExpiration = entry.getValue().totalExpiration;
      if (totalExpiration.isAfter(extendTo)) {
        modack.ackIds.add(ackId);
        continue;
      }

      // forget removes from pendingMessages; this is OK, concurrent maps can
      // handle concurrent iterations and modifications.
      entry.getValue().forget();
      if (totalExpiration.isAfter(now)) {
        int sec = Math.max(1, (int) now.until(totalExpiration, ChronoUnit.SECONDS));
        modacks.add(new PendingModifyAckDeadline(sec, ackId));
      }
    }
    logger.log(Level.FINER, "Sending {0} modacks", modack.ackIds.size() + modacks.size());
    modacks.add(modack);

    List<String> acksToSend = Collections.emptyList();
    ackProcessor.sendAckOperations(acksToSend, modacks);
  }

  @InternalApi
  void processOutstandingAckOperations() {
    List<PendingModifyAckDeadline> modifyAckDeadlinesToSend = new ArrayList<>();

    List<String> acksToSend = new ArrayList<>();
    pendingAcks.drainTo(acksToSend);
    logger.log(Level.FINER, "Sending {0} acks", acksToSend.size());

    PendingModifyAckDeadline nacksToSend = new PendingModifyAckDeadline(0);
    pendingNacks.drainTo(nacksToSend.ackIds);
    logger.log(Level.FINER, "Sending {0} nacks", nacksToSend.ackIds.size());
    if (!nacksToSend.ackIds.isEmpty()) {
      modifyAckDeadlinesToSend.add(nacksToSend);
    }

    PendingModifyAckDeadline receiptsToSend =
        new PendingModifyAckDeadline(getMessageDeadlineSeconds());
    pendingReceipts.drainTo(receiptsToSend.ackIds);
    logger.log(Level.FINER, "Sending {0} receipts", receiptsToSend.ackIds.size());
    if (!receiptsToSend.ackIds.isEmpty()) {
      modifyAckDeadlinesToSend.add(receiptsToSend);
    }

    ackProcessor.sendAckOperations(acksToSend, modifyAckDeadlinesToSend);
  }

  private Instant now() {
    return Instant.ofEpochMilli(clock.millisTime());
  }
}
