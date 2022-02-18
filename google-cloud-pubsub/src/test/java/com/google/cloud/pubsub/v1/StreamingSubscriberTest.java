/*
 * Copyright 2022 Google LLC
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

import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.core.Distribution;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.PubsubMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.threeten.bp.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/** Tests for {@link StreamingSubscriberConnection}. */
public class StreamingSubscriberTest {
    @Rule public TestName testName = new TestName();

    FakeClock mockClock;
    FakeScheduledExecutorService systemExecutor;
    FakeScheduledExecutorService executor;


    String MOCK_SUBSCRIPTION_NAME = "MOCK-SUBSCRIPTION";
    String MOCK_ACK_ID_1 = "MOCK-ACK-ID-1";
    String MOCK_ACK_ID_2 = "MOCK-ACK-ID-2";
    String MOCK_ACK_ID_3 = "MOCK-ACK-ID-3";

    Integer DEFAULT_MOCK_ACK_EXTENSION = 10;

    @Before
    public void setUp() {
        mockClock = new FakeClock();
        systemExecutor = new FakeScheduledExecutorService();

    }

    @After
    public void tearDown() {
    }

    private StreamingSubscriberConnection.Builder getStreamingSubscriberBuilderReceiver(SubscriberStub mockSubscriberStub) {
        MessageReceiver messageReceiver = new MessageReceiver() {
            @Override
            public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
                consumer.ack();
            }
        };

        return StreamingSubscriberConnection.newBuilder(messageReceiver)
                .setSubscription(MOCK_SUBSCRIPTION_NAME)
                .setAckExpirationPadding(Duration.ofSeconds(5))
                .setMaxDurationPerAckExtension(Duration.ofSeconds(5))
                .setAckLatencyDistribution(mock(Distribution.class))
                .setSubscriberStub(mockSubscriberStub)
                .setChannelAffinity(0)
                .setFlowControlSettings(mock(FlowControlSettings.class))
                .setFlowController(mock(FlowController.class))
                .setExecutor(executor)
                .setSystemExecutor(systemExecutor)
                .setClock(mockClock)
                .setExactlyOnceDeliveryEnabled(false);
    }

    private StreamingSubscriberConnection.Builder getStreamingSubscriberBuilderReceiver(SubscriberStub mockSubscriberStub, MessageReceiverWithAckResponse receiverWithAckResponse) {
        return StreamingSubscriberConnection.newBuilder(receiverWithAckResponse)
                .setSubscription(MOCK_SUBSCRIPTION_NAME)
                .setAckExpirationPadding(Duration.ofSeconds(5))
                .setMaxDurationPerAckExtension(Duration.ofSeconds(5))
                .setAckLatencyDistribution(mock(Distribution.class))
                .setSubscriberStub(mockSubscriberStub)
                .setChannelAffinity(0)
                .setFlowControlSettings(mock(FlowControlSettings.class))
                .setFlowController(mock(FlowController.class))
                .setExecutor(executor)
                .setSystemExecutor(systemExecutor)
                .setClock(mockClock)
                .setExactlyOnceDeliveryEnabled(false);
    }

    private List<MessageDispatcher.AckWithMessageFuture> getMockAckWithFutureNoFuture(int numAcks) {
        List<MessageDispatcher.AckWithMessageFuture> mockAckWithNoFutures = new ArrayList<>();
        for (int i = 0; i < numAcks; i++) {
            mockAckWithNoFutures.add(new MessageDispatcher.AckWithMessageFuture("MOCK-ACK-ID-" + i));
        }
        return mockAckWithNoFutures;
    }

    private List<MessageDispatcher.PendingModifyAckDeadline> getMockModAcks(int numModAcks) {
        List<MessageDispatcher.PendingModifyAckDeadline> mockModAcks = new ArrayList<>();
        List<String> modAckIds = new ArrayList<>();
        for (int i = 0; i < numModAcks; i++) {
            modAckIds.add("MOCK-ACK-ID-"  + i);
        }

        mockModAcks.add(
                new MessageDispatcher.PendingModifyAckDeadline(
                        DEFAULT_MOCK_ACK_EXTENSION,
                        modAckIds
                )
        );

        return mockModAcks;
    }

    @Test
    public void testSendAckOperations() {
        SubscriberStub mockSubscriberStub = mock(SubscriberStub.class, RETURNS_DEEP_STUBS);
        when(mockSubscriberStub.modifyAckDeadlineCallable().futureCall(any())).thenReturn(
                ApiFutures.immediateFuture(null)
        );
        StreamingSubscriberConnection streamingSubscriberConnection = getStreamingSubscriberBuilderReceiver(mockSubscriberStub).build();
        streamingSubscriberConnection.sendAckOperations(getMockAckWithFutureNoFuture(2), getMockModAcks(1));
    }

    @Test
    public void testSendAckOperationsFutures() {
        final BlockingQueue<Object> receiveQueue = new LinkedBlockingQueue<>();
        MessageReceiverWithAckResponse receiverWithAckResponse = new MessageReceiverWithAckResponse() {
            @Override
            public void receiveMessage(final PubsubMessage message, final AckReplyConsumerWithResponse ackReplyConsumerWithResponse) {
                receiveQueue.offer(ackReplyConsumerWithResponse);
            }
        };

        SettableApiFuture<AckResponse> messageFutureSuccess = SettableApiFuture.create();
        MessageDispatcher.AckWithMessageFuture mockAckWithMessageFutureSuccess = new MessageDispatcher.AckWithMessageFuture(
                MOCK_ACK_ID_1,
                messageFutureSuccess
        );

        AcknowledgeRequest ackRequestSuccess = AcknowledgeRequest.newBuilder()
                .setSubscription(MOCK_SUBSCRIPTION_NAME)
                .addAckIds(MOCK_ACK_ID_1)
                .build();

        SettableApiFuture<AckResponse> messageFutureInvalid = SettableApiFuture.create();
        MessageDispatcher.AckWithMessageFuture mockAckWithMessageFutureInvalid = new MessageDispatcher.AckWithMessageFuture(
                MOCK_ACK_ID_2,
                messageFutureInvalid
        );

        AcknowledgeRequest ackRequestInvalid = AcknowledgeRequest.newBuilder()
                .setSubscription(MOCK_SUBSCRIPTION_NAME)
                .addAckIds(MOCK_ACK_ID_2)
                .build();

        List<MessageDispatcher.AckWithMessageFuture> mockAcksWithFuture = new ArrayList<>();
        mockAcksWithFuture.add(mockAckWithMessageFutureSuccess);
        mockAcksWithFuture.add(mockAckWithMessageFutureInvalid);

        SubscriberStub mockSubscriberStub = mock(SubscriberStub.class, RETURNS_DEEP_STUBS);

        when(mockSubscriberStub.acknowledgeCallable().futureCall(ackRequestSuccess)).thenReturn(
                ApiFutures.immediateFuture(null)
        );

//        when(mockSubscriberStub.acknowledgeCallable().futureCall(ackRequestSuccess)).thenReturn(
//                ApiFutures.immediateFailedFuture()
//        );

        StreamingSubscriberConnection streamingSubscriberConnection = getStreamingSubscriberBuilderReceiver(mockSubscriberStub, receiverWithAckResponse).build();

        streamingSubscriberConnection.sendAckOperations(
                mockAcksWithFuture,
                getMockModAcks(1)
        );

        assertEquals(AckResponse.SUCCESSFUL, messageFutureSuccess);
        assertEquals(AckResponse.INVALID, messageFutureSuccess);

    }
}
