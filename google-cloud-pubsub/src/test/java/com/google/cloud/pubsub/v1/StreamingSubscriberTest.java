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

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.core.Distribution;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ModifyAckDeadlineRequest;
import com.google.pubsub.v1.PubsubMessage;
import com.google.rpc.ErrorInfo;
import com.google.rpc.Status;
import io.grpc.StatusException;
import io.grpc.protobuf.StatusProto;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.threeten.bp.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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

    String PERMANENT_FAILURE_METADATA_PREFIX = "PERMANENT_FAILURE_";
    String TRANSIENT_FAILURE_ERROR_METADATA_PREFIX = "TRANSIENT_FAILURE_";

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

    private StatusException getMockStatusException(Map<String, String> metadata) {
        ErrorInfo errorInfo = ErrorInfo.newBuilder().putAllMetadata(metadata).build();
        Status status = Status.newBuilder().setCode(StatusCode.Code.OK.ordinal()).addDetails(Any.pack(errorInfo)).build();
        return StatusProto.toStatusException(status);
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
    public void testSendAckOperationsFutures() throws Throwable {
        final BlockingQueue<Object> receiveQueue = new LinkedBlockingQueue<>();
        MessageReceiverWithAckResponse receiverWithAckResponse = new MessageReceiverWithAckResponse() {
            @Override
            public void receiveMessage(final PubsubMessage message, final AckReplyConsumerWithResponse ackReplyConsumerWithResponse) {
                receiveQueue.offer(ackReplyConsumerWithResponse);
            }
        };

        SubscriberStub mockSubscriberStub = mock(SubscriberStub.class, RETURNS_DEEP_STUBS);

        // Mock Modacks
        List<MessageDispatcher.PendingModifyAckDeadline> pendingModifyAckDeadlineList = new ArrayList<>();

        // Request Success - MOCK_ACK_ID_1
        MessageDispatcher.PendingModifyAckDeadline pendingModifyAckDeadlineSuccess = new MessageDispatcher.PendingModifyAckDeadline(DEFAULT_MOCK_ACK_EXTENSION, MOCK_ACK_ID_1);
//        pendingModifyAckDeadlineList.add(pendingModifyAckDeadlineSuccess);

        ModifyAckDeadlineRequest modAckDeadlineRequestSuccess = ModifyAckDeadlineRequest.newBuilder()
                .setSubscription(MOCK_SUBSCRIPTION_NAME)
                .addAckIds(MOCK_ACK_ID_1)
                .build();

        when(mockSubscriberStub.modifyAckDeadlineCallable().futureCall(modAckDeadlineRequestSuccess)).thenReturn(
                ApiFutures.immediateFuture(null)
        );

        // Mock Acks
        List<MessageDispatcher.AckWithMessageFuture> ackWithMessageFutureList = new ArrayList<>();
        Map<String, String> metadataMap = new HashMap<>();
        List<String> ackIds = new ArrayList<>();

        // Request Success - MOCK_ACK_ID_1
        ackIds.add(MOCK_ACK_ID_1);
        SettableApiFuture<AckResponse> messageFutureSuccess = SettableApiFuture.create();
        MessageDispatcher.AckWithMessageFuture mockAckWithMessageFutureSuccess = new MessageDispatcher.AckWithMessageFuture(
                MOCK_ACK_ID_1,
                messageFutureSuccess
        );
        ackWithMessageFutureList.add(mockAckWithMessageFutureSuccess);

        // Ack Request Invalid - MOCK_ACK_ID_2
        ackIds.add(MOCK_ACK_ID_2);
        SettableApiFuture<AckResponse> messageFutureInvalid = SettableApiFuture.create();
        MessageDispatcher.AckWithMessageFuture mockAckWithMessageFutureInvalid = new MessageDispatcher.AckWithMessageFuture(
                MOCK_ACK_ID_2,
                messageFutureInvalid
        );
        ackWithMessageFutureList.add(mockAckWithMessageFutureInvalid);
        metadataMap.put(MOCK_ACK_ID_2, PERMANENT_FAILURE_METADATA_PREFIX + "INVALID_ACK_ID");

        // Ack Request Transient Failure - MOCK_ACK_ID_3
        ackIds.add(MOCK_ACK_ID_3);
        SettableApiFuture<AckResponse> messageFutureTransientFailureSuccess = SettableApiFuture.create();
        MessageDispatcher.AckWithMessageFuture mockAckWithMessageFutureTransientFailureSuccess = new MessageDispatcher.AckWithMessageFuture(
                MOCK_ACK_ID_3,
                messageFutureTransientFailureSuccess
        );
        ackWithMessageFutureList.add(mockAckWithMessageFutureTransientFailureSuccess);
        metadataMap.put(MOCK_ACK_ID_3, TRANSIENT_FAILURE_ERROR_METADATA_PREFIX + "UNORDERED_ACK_ID");

        AcknowledgeRequest ackRequestInitial = AcknowledgeRequest.newBuilder()
                .setSubscription(MOCK_SUBSCRIPTION_NAME)
                .addAllAckIds(ackIds)
                .build();

        when(mockSubscriberStub.acknowledgeCallable().futureCall(ackRequestInitial)).thenReturn(
                ApiFutures.immediateFailedFuture(getMockStatusException(metadataMap))
        );

        // Need to mock a second request/response for the retry for transient
        AcknowledgeRequest ackRequestRetry = AcknowledgeRequest.newBuilder()
                .setSubscription(MOCK_SUBSCRIPTION_NAME)
                .addAckIds(MOCK_ACK_ID_3)
                .build();
        when(mockSubscriberStub.acknowledgeCallable().futureCall(ackRequestRetry)).thenReturn(
                ApiFutures.immediateFuture(null)
        );

        StreamingSubscriberConnection streamingSubscriberConnection = getStreamingSubscriberBuilderReceiver(mockSubscriberStub, receiverWithAckResponse).build();

        streamingSubscriberConnection.sendAckOperations(
                ackWithMessageFutureList,
                pendingModifyAckDeadlineList
        );

        assertEquals(AckResponse.SUCCESSFUL, messageFutureSuccess.get());
        assertEquals(AckResponse.INVALID, messageFutureInvalid.get());
        assertEquals(AckResponse.SUCCESSFUL, messageFutureTransientFailureSuccess.get());
        assertEquals(true, true);
    }
}
