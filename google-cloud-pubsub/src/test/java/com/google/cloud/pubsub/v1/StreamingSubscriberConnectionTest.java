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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.core.Distribution;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.protobuf.Any;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ModifyAckDeadlineRequest;
import com.google.rpc.ErrorInfo;
import com.google.rpc.Status;
import io.grpc.StatusException;
import io.grpc.protobuf.StatusProto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.threeten.bp.Duration;

/** Tests for {@link StreamingSubscriberConnection}. */
public class StreamingSubscriberConnectionTest {
  @Rule public TestName testName = new TestName();

  FakeClock mockClock;
  FakeScheduledExecutorService systemExecutor;
  FakeScheduledExecutorService executor;
  SubscriberStub mockSubscriberStub;

  String MOCK_SUBSCRIPTION_NAME = "MOCK-SUBSCRIPTION";
  String MOCK_ACK_ID_1 = "MOCK-ACK-ID-1";
  String MOCK_ACK_ID_2 = "MOCK-ACK-ID-2";
  String MOCK_ACK_ID_3 = "MOCK-ACK-ID-3";

  String PERMANENT_FAILURE_METADATA_PREFIX = "PERMANENT_FAILURE_";
  String TRANSIENT_FAILURE_ERROR_METADATA_PREFIX = "TRANSIENT_FAILURE_";

  Integer MOCK_ACK_EXTENSION_DEFAULT = 10;

  @Before
  public void setUp() {
    mockClock = new FakeClock();
    systemExecutor = new FakeScheduledExecutorService();
    mockSubscriberStub = mock(SubscriberStub.class, RETURNS_DEEP_STUBS);
  }

  @After
  public void tearDown() {
    systemExecutor.shutdown();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuilderInvalidConfiguration() {
    StreamingSubscriberConnection.newBuilder(mock(MessageReceiverWithAckResponse.class))
        .setExactlyOnceDeliveryEnabled(false);
  }

  @Test
  public void testSendAckOperations() {
    when(mockSubscriberStub.modifyAckDeadlineCallable().futureCall(any()))
        .thenReturn(ApiFutures.immediateFuture(null));
    StreamingSubscriberConnection streamingSubscriberConnection =
        getStreamingSubscriberBuilderReceiver(mockSubscriberStub, false).build();
    List<MessageDispatcher.PendingModifyAckDeadline> modifyAckDeadlineList =
        new ArrayList<MessageDispatcher.PendingModifyAckDeadline>();
    modifyAckDeadlineList.add(
        new MessageDispatcher.PendingModifyAckDeadline(MOCK_ACK_EXTENSION_DEFAULT, MOCK_ACK_ID_1));

    List<MessageDispatcher.AckWithMessageFuture> ackWithMessageFutureList =
        new ArrayList<MessageDispatcher.AckWithMessageFuture>();
    ackWithMessageFutureList.add(new MessageDispatcher.AckWithMessageFuture(MOCK_ACK_ID_1));
    streamingSubscriberConnection.sendAckOperations(
        ackWithMessageFutureList, modifyAckDeadlineList);
  }

  @Test
  public void testSendAckOperationsExactlyOnceMessageFutures() throws Throwable {
    // Only sending ack operations with messages futures
    List<MessageDispatcher.AckWithMessageFuture> ackWithMessageFutureList =
        new ArrayList<MessageDispatcher.AckWithMessageFuture>();
    Map<String, String> metadataMap = new HashMap<String, String>();
    List<String> ackIds = new ArrayList<String>();

    // Request Success - MOCK_ACK_ID_1
    ackIds.add(MOCK_ACK_ID_1);
    SettableApiFuture<AckResponse> messageFutureSuccess = SettableApiFuture.create();
    MessageDispatcher.AckWithMessageFuture mockAckWithMessageFutureSuccess =
        new MessageDispatcher.AckWithMessageFuture(MOCK_ACK_ID_1, messageFutureSuccess);
    ackWithMessageFutureList.add(mockAckWithMessageFutureSuccess);

    // Ack Request Invalid - MOCK_ACK_ID_2
    ackIds.add(MOCK_ACK_ID_2);
    SettableApiFuture<AckResponse> messageFutureInvalid = SettableApiFuture.create();
    MessageDispatcher.AckWithMessageFuture mockAckWithMessageFutureInvalid =
        new MessageDispatcher.AckWithMessageFuture(MOCK_ACK_ID_2, messageFutureInvalid);
    ackWithMessageFutureList.add(mockAckWithMessageFutureInvalid);
    metadataMap.put(MOCK_ACK_ID_2, PERMANENT_FAILURE_METADATA_PREFIX + "INVALID_ACK_ID");

    // Ack Request Transient Failure - MOCK_ACK_ID_3
    ackIds.add(MOCK_ACK_ID_3);
    SettableApiFuture<AckResponse> messageFutureTransientFailureSuccess =
        SettableApiFuture.create();
    MessageDispatcher.AckWithMessageFuture mockAckWithMessageFutureTransientFailureSuccess =
        new MessageDispatcher.AckWithMessageFuture(
            MOCK_ACK_ID_3, messageFutureTransientFailureSuccess);
    ackWithMessageFutureList.add(mockAckWithMessageFutureTransientFailureSuccess);
    metadataMap.put(MOCK_ACK_ID_3, TRANSIENT_FAILURE_ERROR_METADATA_PREFIX + "UNORDERED_ACK_ID");

    AcknowledgeRequest ackRequestInitial =
        AcknowledgeRequest.newBuilder()
            .setSubscription(MOCK_SUBSCRIPTION_NAME)
            .addAllAckIds(ackIds)
            .build();

    when(mockSubscriberStub.acknowledgeCallable().futureCall(ackRequestInitial))
        .thenReturn(ApiFutures.immediateFailedFuture(getMockStatusException(metadataMap)));

    // Need to mock a second request/response for the retry for transient
    AcknowledgeRequest ackRequestRetry =
        AcknowledgeRequest.newBuilder()
            .setSubscription(MOCK_SUBSCRIPTION_NAME)
            .addAckIds(MOCK_ACK_ID_3)
            .build();
    when(mockSubscriberStub.acknowledgeCallable().futureCall(ackRequestRetry))
        .thenReturn(ApiFutures.immediateFuture(null));

    StreamingSubscriberConnection streamingSubscriberConnection =
        getStreamingSubscriberBuilderReceiverWithAckResponse(mockSubscriberStub).build();

    streamingSubscriberConnection.sendAckOperations(
        ackWithMessageFutureList, new ArrayList<MessageDispatcher.PendingModifyAckDeadline>());

    assertEquals(AckResponse.SUCCESSFUL, messageFutureSuccess.get());
    assertEquals(AckResponse.INVALID, messageFutureInvalid.get());
    assertEquals(AckResponse.SUCCESSFUL, messageFutureTransientFailureSuccess.get());
  }

  @Test
  public void testSendAckOperationsModacksExactlyOnceMessageFutures() throws Throwable {
    // Only sending ack operations with messages futures
    Map<String, String> metadataMap = new HashMap<String, String>();
    List<String> modackIds = new ArrayList<String>();

    // Success - MOCK_ACK_ID_1
    modackIds.add(MOCK_ACK_ID_1);

    // Permanent Failure Invalid - MOCK_ACK_ID_2
    modackIds.add(MOCK_ACK_ID_2);
    metadataMap.put(MOCK_ACK_ID_2, PERMANENT_FAILURE_METADATA_PREFIX + "INVALID_ACK_ID");

    // Transient Failure - MOCK_ACK_ID_3
    modackIds.add(MOCK_ACK_ID_3);
    metadataMap.put(MOCK_ACK_ID_3, TRANSIENT_FAILURE_ERROR_METADATA_PREFIX + "UNORDERED_ACK_ID");

    StreamingSubscriberConnection streamingSubscriberConnection =
        getStreamingSubscriberBuilderReceiverWithAckResponse(mockSubscriberStub).build();

    ModifyAckDeadlineRequest modackRequestInitial =
        ModifyAckDeadlineRequest.newBuilder()
            .setSubscription(MOCK_SUBSCRIPTION_NAME)
            .addAllAckIds(modackIds)
            .setAckDeadlineSeconds(MOCK_ACK_EXTENSION_DEFAULT)
            .build();

    when(mockSubscriberStub.modifyAckDeadlineCallable().futureCall(modackRequestInitial))
        .thenReturn(ApiFutures.immediateFailedFuture(getMockStatusException(metadataMap)));

    // Need to mock a second request/response for the retry for transient
    ModifyAckDeadlineRequest modackRequestRetry =
        ModifyAckDeadlineRequest.newBuilder()
            .setSubscription(MOCK_SUBSCRIPTION_NAME)
            .setAckDeadlineSeconds(MOCK_ACK_EXTENSION_DEFAULT)
            .addAckIds(MOCK_ACK_ID_3)
            .build();
    when(mockSubscriberStub.modifyAckDeadlineCallable().futureCall(modackRequestRetry))
        .thenReturn(ApiFutures.immediateFuture(null));

    List<MessageDispatcher.PendingModifyAckDeadline> modifyAckDeadlineList =
        new ArrayList<MessageDispatcher.PendingModifyAckDeadline>();
    modifyAckDeadlineList.add(
        new MessageDispatcher.PendingModifyAckDeadline(MOCK_ACK_EXTENSION_DEFAULT, modackIds));

    // Set up our ack to confirm failure
    List<MessageDispatcher.AckWithMessageFuture> ackWithMessageFutureList =
        new ArrayList<MessageDispatcher.AckWithMessageFuture>();
    SettableApiFuture<AckResponse> messageFutureInvalid = SettableApiFuture.create();
    MessageDispatcher.AckWithMessageFuture mockAckWithMessageFutureInvalid =
        new MessageDispatcher.AckWithMessageFuture(MOCK_ACK_ID_2, messageFutureInvalid);
    ackWithMessageFutureList.add(mockAckWithMessageFutureInvalid);

    streamingSubscriberConnection.sendAckOperations(
        ackWithMessageFutureList, modifyAckDeadlineList);

    assertEquals(AckResponse.INVALID, messageFutureInvalid.get());
  }

  private StreamingSubscriberConnection.Builder getStreamingSubscriberBuilderReceiver(
      SubscriberStub mockSubscriberStub, boolean setExactlyOnceDeliveryEnabled) {
    return StreamingSubscriberConnection.newBuilder(mock(MessageReceiver.class))
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
        .setExactlyOnceDeliveryEnabled(setExactlyOnceDeliveryEnabled);
  }

  private StreamingSubscriberConnection.Builder
      getStreamingSubscriberBuilderReceiverWithAckResponse(SubscriberStub mockSubscriberStub) {
    return StreamingSubscriberConnection.newBuilder(mock(MessageReceiverWithAckResponse.class))
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
        .setExactlyOnceDeliveryEnabled(true);
  }

  private StatusException getMockStatusException(Map<String, String> metadata) {
    ErrorInfo errorInfo = ErrorInfo.newBuilder().putAllMetadata(metadata).build();
    Status status =
        Status.newBuilder()
            .setCode(StatusCode.Code.OK.ordinal())
            .addDetails(Any.pack(errorInfo))
            .build();
    return StatusProto.toStatusException(status);
  }
}
