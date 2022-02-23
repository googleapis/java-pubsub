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
import static org.mockito.Mockito.*;

import com.google.api.gax.batching.FlowController;
import com.google.api.gax.core.Distribution;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.ReceivedMessage;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Before;
import org.junit.Test;
import org.threeten.bp.Duration;

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
  private static final int MAX_SECONDS_PER_ACK_EXTENSION = 60;
  private static final int MIN_ACK_DEADLINE_SECONDS = 10;
  private static final Duration MAX_ACK_EXTENSION_PERIOD = Duration.ofMinutes(60);

  private MessageDispatcher.AckProcessor mockAckProcessor;
  private FakeClock clock;
  private boolean messageContainsDeliveryAttempt;

  private FakeScheduledExecutorService systemExecutor;

  private static MessageReceiver messageReceiver;
  private static MessageReceiverWithAckResponse messageReceiverWithAckResponse;

  private LinkedBlockingQueue<AckReplyConsumer> consumers;
  private LinkedBlockingQueue<AckReplyConsumerWithResponse> consumersWithResponse;

  @Before
  public void setUp() {
    systemExecutor = new FakeScheduledExecutorService();
    clock = new FakeClock();

    mockAckProcessor = mock(MessageDispatcher.AckProcessor.class);
    messageContainsDeliveryAttempt = true;

    consumers = new LinkedBlockingQueue<>();
    consumersWithResponse = new LinkedBlockingQueue<>();

    // We are instantiating "real" message receivers to easily ack/nack messages
    messageReceiver =
        new MessageReceiver() {
          @Override
          public void receiveMessage(
              final PubsubMessage message, final AckReplyConsumer ackReplyConsumer) {
            assertThat(message.getData()).isEqualTo(MESSAGE_DATA);
            if (messageContainsDeliveryAttempt) {
              assertTrue(message.containsAttributes("googclient_deliveryattempt"));
              assertThat(message.getAttributesOrThrow("googclient_deliveryattempt"))
                  .isEqualTo(Integer.toString(DELIVERY_INFO_COUNT));
            } else {
              assertFalse(message.containsAttributes("googclient_deliveryattempt"));
            }
            consumers.add(ackReplyConsumer);
          }
        };

    messageReceiverWithAckResponse =
        new MessageReceiverWithAckResponse() {
          @Override
          public void receiveMessage(
              PubsubMessage message, AckReplyConsumerWithResponse ackReplyConsumerWithResponse) {
            assertThat(message.getData()).isEqualTo(MESSAGE_DATA);
            if (messageContainsDeliveryAttempt) {
              assertTrue(message.containsAttributes("googclient_deliveryattempt"));
              assertThat(message.getAttributesOrThrow("googclient_deliveryattempt"))
                  .isEqualTo(Integer.toString(DELIVERY_INFO_COUNT));
            } else {
              assertFalse(message.containsAttributes("googclient_deliveryattempt"));
            }
            consumersWithResponse.add(ackReplyConsumerWithResponse);
          }
        };
  }

  @Test
  public void testSetupAndTeardown() {
    MessageDispatcher messageDispatcher = getMessageDispatcher();

    messageDispatcher.start();
    messageDispatcher.stop();
  }

  @Test
  public void testReceiptMessageReceiver() {
    MessageReceiver mockMessageReceiver = mock(MessageReceiver.class);
    MessageDispatcher messageDispatcher = getMessageDispatcher(mockMessageReceiver);
    messageDispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
    messageDispatcher.processOutstandingAckOperations();

    // Assert expected behavior
    List<ModackWithMessageFuture> modackWithMessageFutureList =
        new ArrayList<ModackWithMessageFuture>();
    modackWithMessageFutureList.add(
        new ModackWithMessageFuture(
            MIN_ACK_DEADLINE_SECONDS, new AckIdMessageFuture(TEST_MESSAGE.getAckId())));

    verify(mockAckProcessor, times(1))
        .sendAckOperations(
            argThat(
                new CustomArgumentMatchers.ModackWithMessageFutureListMatcher(
                    modackWithMessageFutureList)),
            eq(Collections.emptyList()));
    verify(mockMessageReceiver, never())
        .receiveMessage(eq(TEST_MESSAGE.getMessage()), any(AckReplyConsumer.class));
  }

  @Test
  public void testReceiptMessageReceiverWithAckResponse() {
    MessageReceiverWithAckResponse mockMessageReceiverWithAckResponse =
        mock(MessageReceiverWithAckResponse.class);
    MessageDispatcher messageDispatcher = getMessageDispatcher(mockMessageReceiverWithAckResponse);
    messageDispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
    messageDispatcher.processOutstandingAckOperations();

    // Assert expected behavior
    List<ModackWithMessageFuture> modackWithMessageFutureList =
        new ArrayList<ModackWithMessageFuture>();
    modackWithMessageFutureList.add(
        new ModackWithMessageFuture(
            MIN_ACK_DEADLINE_SECONDS, new AckIdMessageFuture(TEST_MESSAGE.getAckId())));

    verify(mockAckProcessor, times(1))
        .sendAckOperations(
            argThat(
                new CustomArgumentMatchers.ModackWithMessageFutureListMatcher(
                    modackWithMessageFutureList)),
            eq(Collections.emptyList()));
    verify(mockMessageReceiverWithAckResponse, never())
        .receiveMessage(eq(TEST_MESSAGE.getMessage()), any(AckReplyConsumerWithResponse.class));
  }

  @Test
  public void testConsumerAckMessageReceiver() {
    MessageDispatcher messageDispatcher = getMessageDispatcher(messageReceiver);
    messageDispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));

    try {
      // Ack a message
      consumers.take().ack();
    } catch (Throwable t) {
      // In case our consumers fail
      throw new AssertionError();
    }

    messageDispatcher.processOutstandingAckOperations();

    // Assert expected behavior
    List<AckIdMessageFuture> ackIdMessageFutureList = new ArrayList<AckIdMessageFuture>();
    AckIdMessageFuture ackIdMessageFuture = new AckIdMessageFuture(TEST_MESSAGE.getAckId());
    ackIdMessageFutureList.add(ackIdMessageFuture);

    List<ModackWithMessageFuture> modackWithMessageFutureList =
        new ArrayList<ModackWithMessageFuture>();
    modackWithMessageFutureList.add(
        new ModackWithMessageFuture(MIN_ACK_DEADLINE_SECONDS, ackIdMessageFuture));

    verify(mockAckProcessor, times(1))
        .sendAckOperations(
            argThat(
                new CustomArgumentMatchers.ModackWithMessageFutureListMatcher(
                    modackWithMessageFutureList)),
            argThat(
                new CustomArgumentMatchers.AckIdMessageFutureListMatcher(ackIdMessageFutureList)));
  }

  @Test
  public void testConsumerAckMessageReceiverWithAckResponse() {
    MessageDispatcher messageDispatcher = getMessageDispatcher(messageReceiverWithAckResponse);
    messageDispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
    Future<AckResponse> ackResponseFuture;

    try {
      // Ack a message - at this point we do not care about the message future so just drop it
      consumersWithResponse.take().ack();
    } catch (Throwable t) {
      // In case our consumers fail
      throw new AssertionError();
    }

    messageDispatcher.processOutstandingAckOperations();

    // Assert expected behavior
    List<AckIdMessageFuture> ackIdMessageFutureList = new ArrayList<AckIdMessageFuture>();
    AckIdMessageFuture ackIdMessageFuture = new AckIdMessageFuture(TEST_MESSAGE.getAckId());
    ackIdMessageFutureList.add(ackIdMessageFuture);

    List<ModackWithMessageFuture> modackWithMessageFutureList =
        new ArrayList<ModackWithMessageFuture>();
    modackWithMessageFutureList.add(
        new ModackWithMessageFuture(MIN_ACK_DEADLINE_SECONDS, ackIdMessageFuture));

    verify(mockAckProcessor, times(1))
        .sendAckOperations(
            argThat(
                new CustomArgumentMatchers.ModackWithMessageFutureListMatcher(
                    modackWithMessageFutureList)),
            argThat(
                new CustomArgumentMatchers.AckIdMessageFutureListMatcher(ackIdMessageFutureList)));
  }

  @Test
  public void testConsumerNackMessageReceiver() {
    MessageDispatcher messageDispatcher = getMessageDispatcher(messageReceiver);
    messageDispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));

    try {
      consumers.take().nack();
    } catch (Throwable t) {
      // Just in case something went wrong with our consumers
      throw new AssertionError();
    }

    messageDispatcher.processOutstandingAckOperations();

    // Assert expected behavior
    AckIdMessageFuture ackIdMessageFuture = new AckIdMessageFuture(TEST_MESSAGE.getAckId());
    List<ModackWithMessageFuture> modackWithMessageFutureList =
        new ArrayList<ModackWithMessageFuture>();
    modackWithMessageFutureList.add(new ModackWithMessageFuture(0, ackIdMessageFuture));
    modackWithMessageFutureList.add(
        new ModackWithMessageFuture(MIN_ACK_DEADLINE_SECONDS, ackIdMessageFuture));

    verify(mockAckProcessor, times(1))
        .sendAckOperations(
            argThat(
                new CustomArgumentMatchers.ModackWithMessageFutureListMatcher(
                    modackWithMessageFutureList)),
            eq(Collections.emptyList()));
  }

  @Test
  public void testConsumerNackMessageReceiverWithAckResponse() {
    MessageDispatcher messageDispatcher = getMessageDispatcher(messageReceiverWithAckResponse);
    messageDispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));

    try {
      // Ack a message - at this point we do not care about the message future so just drop it
      consumersWithResponse.take().nack();
    } catch (Throwable t) {
      // Just in case something went wrong with our consumers
      throw new AssertionError();
    }

    messageDispatcher.processOutstandingAckOperations();

    // Assert expected behavior
    AckIdMessageFuture ackIdMessageFuture = new AckIdMessageFuture(TEST_MESSAGE.getAckId());
    List<ModackWithMessageFuture> modackWithMessageFutureList =
        new ArrayList<ModackWithMessageFuture>();
    modackWithMessageFutureList.add(new ModackWithMessageFuture(0, ackIdMessageFuture));
    modackWithMessageFutureList.add(
        new ModackWithMessageFuture(MIN_ACK_DEADLINE_SECONDS, ackIdMessageFuture));

    verify(mockAckProcessor, times(1))
        .sendAckOperations(
            argThat(
                new CustomArgumentMatchers.ModackWithMessageFutureListMatcher(
                    modackWithMessageFutureList)),
            eq(Collections.emptyList()));
  }

  @Test
  public void testExtension() {
    MessageDispatcher messageDispatcher = getMessageDispatcher();
    messageDispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
    messageDispatcher.extendDeadlines();

    // Assert expected behavior
    List<AckIdMessageFuture> ackIdMessageFutureList = new ArrayList<AckIdMessageFuture>();

    AckIdMessageFuture ackIdMessageFuture = new AckIdMessageFuture(TEST_MESSAGE.getAckId());
    ackIdMessageFutureList.add(ackIdMessageFuture);

    List<ModackWithMessageFuture> modackWithMessageFutureList =
        new ArrayList<ModackWithMessageFuture>();
    modackWithMessageFutureList.add(
        new ModackWithMessageFuture(MIN_ACK_DEADLINE_SECONDS, ackIdMessageFuture));

    verify(mockAckProcessor, times(1))
        .sendAckOperations(
            argThat(
                new CustomArgumentMatchers.ModackWithMessageFutureListMatcher(
                    modackWithMessageFutureList)),
            eq(Collections.emptyList()));
  }

  @Test
  public void testExtension_ExpirationExtension() {
    MessageDispatcher messageDispatcher = getMessageDispatcher();
    messageDispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));
    int secondsLeft = 5;
    // Advance clock to have 5 seconds left in extension period
    clock.advance(MAX_ACK_EXTENSION_PERIOD.getSeconds() - secondsLeft, TimeUnit.SECONDS);
    messageDispatcher.extendDeadlines();

    // Assert expected behavior
    List<AckIdMessageFuture> ackIdMessageFutureList = new ArrayList<AckIdMessageFuture>();
    AckIdMessageFuture ackIdMessageFuture = new AckIdMessageFuture(TEST_MESSAGE.getAckId());
    ackIdMessageFutureList.add(ackIdMessageFuture);
    List<ModackWithMessageFuture> modackWithMessageFutureList =
        new ArrayList<ModackWithMessageFuture>();
    modackWithMessageFutureList.add(new ModackWithMessageFuture(secondsLeft, ackIdMessageFuture));

    verify(mockAckProcessor, times(1))
        .sendAckOperations(
            argThat(
                new CustomArgumentMatchers.ModackWithMessageFutureListMatcher(
                    modackWithMessageFutureList)),
            eq(Collections.emptyList()));
  }

  @Test
  public void testExtension_GiveUp() throws Exception {
    MessageDispatcher messageDispatcher = getMessageDispatcher();
    messageDispatcher.processReceivedMessages(Collections.singletonList(TEST_MESSAGE));

    // If we run extendDeadlines after totalExpiration, we shouldn't send anything.
    clock.advance(1, TimeUnit.DAYS);
    messageDispatcher.extendDeadlines();

    // Assert expected behavior
    verify(mockAckProcessor, times(1))
        .sendAckOperations(eq(Collections.emptyList()), eq(Collections.emptyList()));
  }

  private MessageDispatcher getMessageDispatcher() {
    return getMessageDispatcher(mock(MessageReceiver.class));
  }

  private MessageDispatcher getMessageDispatcher(MessageReceiver messageReceiver) {
    return getMessageDispatcherFromBuilder(MessageDispatcher.newBuilder(messageReceiver));
  }

  private MessageDispatcher getMessageDispatcher(
      MessageReceiverWithAckResponse messageReceiverWithAckResponse) {
    return getMessageDispatcherFromBuilder(
        MessageDispatcher.newBuilder(messageReceiverWithAckResponse));
  }

  private MessageDispatcher getMessageDispatcherFromBuilder(MessageDispatcher.Builder builder) {
    MessageDispatcher messageDispatcher =
        builder
            .setAckProcessor(mockAckProcessor)
            .setAckExpirationPadding(Duration.ofSeconds(5))
            .setMaxAckExtensionPeriod(MAX_ACK_EXTENSION_PERIOD)
            .setMaxDurationPerAckExtension(Duration.ofSeconds(MAX_SECONDS_PER_ACK_EXTENSION))
            .setAckLatencyDistribution(mock(Distribution.class))
            .setFlowController(mock(FlowController.class))
            .setExecutor(MoreExecutors.directExecutor())
            .setSystemExecutor(systemExecutor)
            .setApiClock(clock)
            .build();

    messageDispatcher.setMessageDeadlineSeconds(MIN_ACK_DEADLINE_SECONDS);
    return messageDispatcher;
  }
}
