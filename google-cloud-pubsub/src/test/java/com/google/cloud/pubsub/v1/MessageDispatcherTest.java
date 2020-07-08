/*
 * Copyright 2017 Google LLC
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.core.Distribution;
import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.ReceivedMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.threeten.bp.Duration;

public class MessageDispatcherTest {
  private static final ByteString MESSAGE_DATA = ByteString.copyFromUtf8("message-data");
  private static final int DELIVERY_INFO_COUNT = 3;
  private static final ReceivedMessage TEST_MESSAGE =
      ReceivedMessage.newBuilder()
          .setAckId("ackid")
          .setMessage(PubsubMessage.newBuilder().setData(MESSAGE_DATA).build())
          .setDeliveryAttempt(DELIVERY_INFO_COUNT)
          .build();
  private static final Runnable NOOP_RUNNABLE =
      new Runnable() {
        @Override
        public void run() {
          // No-op; don't do anything.
        }
      };
  private static final int MAX_SECONDS_PER_ACK_EXTENSION = 60;

  private MessageDispatcher dispatcher;
  private LinkedBlockingQueue<AckReplyConsumer> consumers;
  private List<String> sentAcks;
  private List<ModAckItem> sentModAcks;
  private FakeClock clock;
  private FlowController flowController;
  private boolean messageContainsDeliveryAttempt;

  @AutoValue
  abstract static class ModAckItem {
    abstract String ackId();

    abstract int seconds();

    static ModAckItem of(String ackId, int seconds) {
      return new AutoValue_MessageDispatcherTest_ModAckItem(ackId, seconds);
    }
  }

  @Before
  public void setUp() {
    consumers = new LinkedBlockingQueue<>();
    sentAcks = new ArrayList<>();
    sentModAcks = new ArrayList<>();

    MessageReceiver receiver =
        new MessageReceiver() {
          @Override
          public void receiveMessage(final PubsubMessage message, final AckReplyConsumer consumer) {
            assertThat(message.getData()).isEqualTo(MESSAGE_DATA);
            if (messageContainsDeliveryAttempt) {
              assertTrue(message.containsAttributes("googclient_deliveryattempt"));
              assertThat(message.getAttributesOrThrow("googclient_deliveryattempt"))
                  .isEqualTo(Integer.toString(DELIVERY_INFO_COUNT));
            } else {
              assertFalse(message.containsAttributes("googclient_deliveryattempt"));
            }
            consumers.add(consumer);
          }
        };
    MessageDispatcher.AckProcessor processor =
        new MessageDispatcher.AckProcessor() {
          public void sendAckOperations(
              List<String> acksToSend,
              List<MessageDispatcher.PendingModifyAckDeadline> ackDeadlineExtensions) {
            sentAcks.addAll(acksToSend);
            for (MessageDispatcher.PendingModifyAckDeadline modack : ackDeadlineExtensions) {
              for (String ackId : modack.ackIds) {
                sentModAcks.add(ModAckItem.of(ackId, modack.deadlineExtensionSeconds));
              }
            }
          }
        };

    // This executor isn't used because we're not actually scheduling anything until we call
    // dispatcher.start(), which we're not doing here.
    ScheduledThreadPoolExecutor systemExecutor = new ScheduledThreadPoolExecutor(1);
    systemExecutor.shutdownNow();

    clock = new FakeClock();
    flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(1L)
                .setLimitExceededBehavior(FlowController.LimitExceededBehavior.Block)
                .build());

    dispatcher =
        new MessageDispatcher(
            receiver,
            processor,
            Duration.ofSeconds(5),
            Duration.ofMinutes(60),
            Duration.ofSeconds(MAX_SECONDS_PER_ACK_EXTENSION),
            new Distribution(Subscriber.MAX_ACK_DEADLINE_SECONDS + 1),
            flowController,
            MoreExecutors.directExecutor(),
            systemExecutor,
            clock);
    dispatcher.setMessageDeadlineSeconds(Subscriber.MIN_ACK_DEADLINE_SECONDS);

    messageContainsDeliveryAttempt = true;
  }

  @Test
  public void testReceipt() {
    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
    dispatcher.processOutstandingAckOperations();
    assertThat(sentModAcks)
        .contains(ModAckItem.of(TEST_MESSAGE.getAckId(), Subscriber.MIN_ACK_DEADLINE_SECONDS));
  }

  @Test
  public void testReceiptNoDeliveryAttempt() {
    messageContainsDeliveryAttempt = false;
    ReceivedMessage messageNoDeliveryAttempt =
        ReceivedMessage.newBuilder()
            .setAckId("ackid")
            .setMessage(PubsubMessage.newBuilder().setData(MESSAGE_DATA).build())
            .build();
    dispatcher.processReceivedMessages(Collections.singletonList(messageNoDeliveryAttempt));
    dispatcher.processOutstandingAckOperations();
    assertThat(sentModAcks)
        .contains(
            ModAckItem.of(
                messageNoDeliveryAttempt.getAckId(), Subscriber.MIN_ACK_DEADLINE_SECONDS));
  }

  @Test
  public void testAck() throws Exception {
    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
    consumers.take().ack();
    dispatcher.processOutstandingAckOperations();
    assertThat(sentAcks).contains(TEST_MESSAGE.getAckId());
  }

  @Test
  public void testNack() throws Exception {
    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
    consumers.take().nack();
    dispatcher.processOutstandingAckOperations();
    assertThat(sentModAcks).contains(ModAckItem.of(TEST_MESSAGE.getAckId(), 0));
  }

  @Test
  public void testExtension() throws Exception {
    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
    dispatcher.extendDeadlines();
    assertThat(sentModAcks)
        .contains(ModAckItem.of(TEST_MESSAGE.getAckId(), Subscriber.MIN_ACK_DEADLINE_SECONDS));

    sentModAcks.clear();
    consumers.take().ack();
    dispatcher.extendDeadlines();
    assertThat(sentModAcks).isEmpty();
  }

  @Test
  public void testExtension_Close() {
    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
    dispatcher.extendDeadlines();
    assertThat(sentModAcks)
        .contains(ModAckItem.of(TEST_MESSAGE.getAckId(), Subscriber.MIN_ACK_DEADLINE_SECONDS));
    sentModAcks.clear();

    // Default total expiration is an hour (60*60 seconds). We normally would extend by 10s.
    // However, only extend by 5s here, since there's only 5s left before total expiration.
    clock.advance(60 * 60 - 5, TimeUnit.SECONDS);
    dispatcher.extendDeadlines();
    assertThat(sentModAcks).contains(ModAckItem.of(TEST_MESSAGE.getAckId(), 5));
  }

  @Test
  public void testExtension_GiveUp() throws Exception {
    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
    dispatcher.extendDeadlines();
    assertThat(sentModAcks)
        .contains(ModAckItem.of(TEST_MESSAGE.getAckId(), Subscriber.MIN_ACK_DEADLINE_SECONDS));
    sentModAcks.clear();

    // If we run extendDeadlines after totalExpiration, we shouldn't send anything.
    // In particular, don't send negative modacks.
    clock.advance(1, TimeUnit.DAYS);
    dispatcher.extendDeadlines();
    assertThat(sentModAcks).isEmpty();

    // We should be able to reserve another item in the flow controller and not block.
    flowController.reserve(1, 0);
    dispatcher.stop();
  }

  @Test
  public void testDeadlineAdjustment() throws Exception {
    assertThat(dispatcher.computeDeadlineSeconds()).isEqualTo(10);

    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
    clock.advance(42, TimeUnit.SECONDS);
    consumers.take().ack();

    assertThat(dispatcher.computeDeadlineSeconds()).isEqualTo(42);
  }

  @Test
  public void testMaxDurationPerAckExtension() throws Exception {
    assertThat(dispatcher.computeDeadlineSeconds()).isEqualTo(10);

    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
    clock.advance(MAX_SECONDS_PER_ACK_EXTENSION + 5, TimeUnit.SECONDS);
    consumers.take().ack();

    assertThat(dispatcher.computeDeadlineSeconds()).isEqualTo(MAX_SECONDS_PER_ACK_EXTENSION);
  }
}
