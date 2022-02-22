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
 import com.google.pubsub.v1.AcknowledgeRequest;
 import com.google.pubsub.v1.PubsubMessage;
 import com.google.pubsub.v1.ReceivedMessage;

 import java.sql.Array;
 import java.util.*;
 import java.util.concurrent.LinkedBlockingQueue;
 import java.util.concurrent.ScheduledExecutorService;
 import java.util.concurrent.ScheduledThreadPoolExecutor;
 import java.util.concurrent.TimeUnit;
 import org.junit.Before;
 import org.junit.Test;
 import org.mockito.ArgumentMatcher;
 import org.threeten.bp.Duration;

 import static org.mockito.Mockito.*;

 public class MessageDispatcherTest {
  private static final ByteString MESSAGE_DATA = ByteString.copyFromUtf8("message-data");
  private static final int DELIVERY_INFO_COUNT = 3;
  private static final String ACK_ID = "ACK-ID";
  private static final ReceivedMessage TEST_MESSAGE =
      ReceivedMessage.newBuilder()
          .setAckId(ACK_ID)
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
  private MessageDispatcher.AckProcessor mockAckProcessor;
  private LinkedBlockingQueue<AckReplyConsumer> consumers;
  private FakeClock clock;
  private FlowController flowController;
  private boolean messageContainsDeliveryAttempt;

  @Before
  public void setUp() {
    consumers = new LinkedBlockingQueue<>();

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

    ScheduledExecutorService systemExecutor = new FakeScheduledExecutorService();


    clock = new FakeClock();
    flowController =
        new FlowController(
            FlowControlSettings.newBuilder()
                .setMaxOutstandingElementCount(1L)
                .setLimitExceededBehavior(FlowController.LimitExceededBehavior.Block)
                .build());

    mockAckProcessor = mock(MessageDispatcher.AckProcessor.class);

    dispatcher = MessageDispatcher.newBuilder(receiver)
            .setAckProcessor(mockAckProcessor)
            .setAckExpirationPadding(Duration.ofSeconds(5))
            .setMaxAckExtensionPeriod(Duration.ofMinutes(60))
            .setMaxDurationPerAckExtension(Duration.ofSeconds(MAX_SECONDS_PER_ACK_EXTENSION))
            .setAckLatencyDistribution(mock(Distribution.class))
            .setFlowController(flowController)
            .setExecutor(MoreExecutors.directExecutor())
            .setSystemExecutor(systemExecutor)
            .setApiClock(clock)
            .build();

    dispatcher.setMessageDeadlineSeconds(Subscriber.MIN_ACK_DEADLINE_SECONDS);

    messageContainsDeliveryAttempt = true;
  }

  @Test
  public void testReceipt() {
    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
    dispatcher.processOutstandingAckOperations();
    List<ModackWithMessageFuture> modackWithMessageFutureList = new ArrayList<ModackWithMessageFuture>();
    modackWithMessageFutureList.add(new ModackWithMessageFuture(Subscriber.MIN_ACK_DEADLINE_SECONDS, new AckIdMessageFuture(TEST_MESSAGE.getAckId())));

    verify(mockAckProcessor, times(1)).sendAckOperations(argThat(new ModackWithMessageFutureListMatcher(modackWithMessageFutureList)), any());
  }

  @Test
  public void testAck() {
    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));

    dispatcher.processOutstandingAckOperations();

    List<AckIdMessageFuture> ackIdMessageFutureList = new ArrayList<AckIdMessageFuture>();
    AckIdMessageFuture ackIdMessageFuture = new AckIdMessageFuture(TEST_MESSAGE.getAckId());
    ackIdMessageFutureList.add(ackIdMessageFuture);

    List<ModackWithMessageFuture> modackWithMessageFutureList = new ArrayList<ModackWithMessageFuture>();
    modackWithMessageFutureList.add(new ModackWithMessageFuture(Subscriber.MIN_ACK_DEADLINE_SECONDS, ackIdMessageFuture));

//      verify(mockAckProcessor, times(1)).sendAckOperations(argThat(new ModackWithMessageFutureListMatcher(modackWithMessageFutureList)), argThat(new AckIdMessageFutureListMatcher(ackIdMessageFutureList)));
  }

//  @Test
//  public void testNack() throws Exception {
//    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
//    consumers.take().nack();
//    dispatcher.processOutstandingAckOperations();
//    assertThat(sentModAcks).contains(ModAckItem.of(TEST_MESSAGE.getAckId(), 0));
//  }
//
//  @Test
//  public void testExtension() throws Exception {
//    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
//    dispatcher.extendDeadlines();
//    assertThat(sentModAcks)
//        .contains(ModAckItem.of(TEST_MESSAGE.getAckId(), Subscriber.MIN_ACK_DEADLINE_SECONDS));
//
//    sentModAcks.clear();
//    consumers.take().ack();
//    dispatcher.extendDeadlines();
//    assertThat(sentModAcks).isEmpty();
//  }
//
//  @Test
//  public void testExtension_Close() {
//    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
//    dispatcher.extendDeadlines();
//    assertThat(sentModAcks)
//        .contains(ModAckItem.of(TEST_MESSAGE.getAckId(), Subscriber.MIN_ACK_DEADLINE_SECONDS));
//    sentModAcks.clear();
//
//    // Default total expiration is an hour (60*60 seconds). We normally would extend by 10s.
//    // However, only extend by 5s here, since there's only 5s left before total expiration.
//    clock.advance(60 * 60 - 5, TimeUnit.SECONDS);
//    dispatcher.extendDeadlines();
//    assertThat(sentModAcks).contains(ModAckItem.of(TEST_MESSAGE.getAckId(), 5));
//  }
//
//  @Test
//  public void testExtension_GiveUp() throws Exception {
//    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
//    dispatcher.extendDeadlines();
//    assertThat(sentModAcks)
//        .contains(ModAckItem.of(TEST_MESSAGE.getAckId(), Subscriber.MIN_ACK_DEADLINE_SECONDS));
//    sentModAcks.clear();
//
//    // If we run extendDeadlines after totalExpiration, we shouldn't send anything.
//    // In particular, don't send negative modacks.
//    clock.advance(1, TimeUnit.DAYS);
//    dispatcher.extendDeadlines();
//    assertThat(sentModAcks).isEmpty();
//
//    // We should be able to reserve another item in the flow controller and not block.
//    flowController.reserve(1, 0);
//    dispatcher.stop();
//  }
//
//  @Test
//  public void testDeadlineAdjustment() throws Exception {
//    assertThat(dispatcher.computeDeadlineSeconds()).isEqualTo(10);
//
//    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
//    clock.advance(42, TimeUnit.SECONDS);
//    consumers.take().ack();
//
//    assertThat(dispatcher.computeDeadlineSeconds()).isEqualTo(42);
//  }
//
//  @Test
//  public void testMaxDurationPerAckExtension() throws Exception {
//    assertThat(dispatcher.computeDeadlineSeconds()).isEqualTo(10);
//
//    dispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
//    clock.advance(MAX_SECONDS_PER_ACK_EXTENSION + 5, TimeUnit.SECONDS);
//    consumers.take().ack();
//
//    assertThat(dispatcher.computeDeadlineSeconds()).isEqualTo(MAX_SECONDS_PER_ACK_EXTENSION);
//  }

     // Custom ArgumentMatchers
     public class ModackWithMessageFutureListMatcher implements ArgumentMatcher<List<ModackWithMessageFuture>> {
         private List<ModackWithMessageFuture> left;

         ModackWithMessageFutureListMatcher(List<ModackWithMessageFuture> modackWithMessageFutureList) {
             this.left = modackWithMessageFutureList;
         }

         @Override
         public boolean matches(List<ModackWithMessageFuture> right) {
             // We only really care about the ackIds, the futures will be mocked
             if (this.left.size() != right.size()) {
                 return false;
             }

             Iterator<ModackWithMessageFuture> iteratorLeft = this.left.iterator();
             Iterator<ModackWithMessageFuture> iteratorRight = right.iterator();

             while(iteratorLeft.hasNext() && iteratorRight.hasNext()) {
                 ModackWithMessageFuture leftElement = iteratorLeft.next();
                 ModackWithMessageFuture rightElement = iteratorRight.next();
                 AckIdMessageFutureListMatcher ackIdMessageFutureListMatcher = new AckIdMessageFutureListMatcher(leftElement.ackIdMessageFutures);
                 if (leftElement.deadlineExtensionSeconds != rightElement.deadlineExtensionSeconds || !ackIdMessageFutureListMatcher.matches(rightElement.ackIdMessageFutures)) {
                     return false;
                 }
             }
             return true;
         }
     }

     public class AckIdMessageFutureListMatcher implements ArgumentMatcher<List<AckIdMessageFuture>> {
         private List<AckIdMessageFuture> left;

         AckIdMessageFutureListMatcher(List<AckIdMessageFuture> ackIdMessageFutureList) {
             this.left = ackIdMessageFutureList;
         }

         @Override
         public boolean matches(List<AckIdMessageFuture> right) {
             // We only really care about the ackIds, the futures will be mocked
             if (this.left.size() != right.size()) {
                 return false;
             }

             Iterator<AckIdMessageFuture> iteratorLeft = this.left.iterator();
             Iterator<AckIdMessageFuture> iteratorRight = right.iterator();

             while(iteratorLeft.hasNext() && iteratorRight.hasNext()) {
                 if (iteratorLeft.next().ackId != iteratorRight.next().ackId) {
                     return false;
                 }
             }
             return true;
         }
     }
 }
