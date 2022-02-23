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

import static org.junit.Assert.*;
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
import java.util.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.threeten.bp.Duration;

/** Tests for {@link StreamingSubscriberConnection}. */
public class StreamingSubscriberConnectionTest {
  @Rule public TestName testName = new TestName();

  private FakeClock mockClock;
  private FakeScheduledExecutorService systemExecutor;
  private FakeScheduledExecutorService executor;
  private SubscriberStub mockSubscriberStub;

  private static final String MOCK_SUBSCRIPTION_NAME = "MOCK-SUBSCRIPTION";
  private static final String MOCK_ACK_ID_SUCCESS = "MOCK-ACK-ID-SUCCESS";
  private static final String MOCK_ACK_ID_NACK_SUCCESS = "MOCK-ACK-ID-NACK-SUCCESS";
  // Successful modacks should not return a message
  private static final String MOCK_ACK_ID_SUCCESS_NO_MESSAGE = "MOCK-ACK-ID-SUCCESS-NO-MESSAGE";
  private static final String MOCK_ACK_ID_TRANSIENT_FAILURE_UNORDERED_ACK_ID_THEN_SUCCESS =
      "MOCK-ACK-ID-TRANSIENT-FAILURE-UNORDERED-ACK-ID-THEN-SUCCESS";
  private static final String MOCK_ACK_ID_TRANSIENT_FAILURE_SERVICE_UNAVAILABLE_THEN_SUCCESS =
      "MOCK-ACK-ID-TRANSIENT-FAILURE-SERVICE-UNAVAILABLE-THEN-SUCCESS";
  private static final String MOCK_ACK_ID_INVALID = "MOCK-ACK-ID-INVALID";
  private static final String MOCK_ACK_ID_OTHER = "MOCK-ACK-ID-OTHER";

  private static final String PERMANENT_FAILURE_INVALID_ACK_ID = "PERMANENT_FAILURE_INVALID_ACK_ID";
  private static final String TRANSIENT_FAILURE_UNORDERED_ACK_ID =
      "TRANSIENT_FAILURE_UNORDERED_ACK_ID";
  private static final String TRANSIENT_FAILURE_SERVICE_UNAVAILABLE =
      "TRANSIENT_FAILURE_SERVICE_UNAVAILABLE";
  private static final String PERMANENT_FAILURE_OTHER = "I_DO_NOT_MATCH_ANY_KNOWN_ERRORS";

  private Integer MOCK_ACK_EXTENSION_DEFAULT = 10;

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
  public void testSendAckOperationsExactlyOnceDisabledNoMessageFutures() {
    // Setup mocks
    List<ModackWithMessageFuture> modackWithMessageFutureList =
        new ArrayList<ModackWithMessageFuture>();

    ModackWithMessageFuture modackWithMessageFutureSuccess =
        new ModackWithMessageFuture(
            MOCK_ACK_EXTENSION_DEFAULT, new AckIdMessageFuture(MOCK_ACK_ID_SUCCESS));
    modackWithMessageFutureList.add(modackWithMessageFutureSuccess);

    ModackWithMessageFuture modackWithMessageFutureNack =
        new ModackWithMessageFuture(0, new AckIdMessageFuture(MOCK_ACK_ID_SUCCESS));
    modackWithMessageFutureList.add(modackWithMessageFutureNack);

    List<AckIdMessageFuture> ackIdMessageFutureList = new ArrayList<AckIdMessageFuture>();
    AckIdMessageFuture ackIdMessageFutureSuccess = new AckIdMessageFuture(MOCK_ACK_ID_SUCCESS);
    ackIdMessageFutureList.add(ackIdMessageFutureSuccess);

    // Instantiate class and run operation(s)
    StreamingSubscriberConnection streamingSubscriberConnection =
        getStreamingSubscriberReceiver(mockSubscriberStub, false);
    streamingSubscriberConnection.sendAckOperations(
        modackWithMessageFutureList, ackIdMessageFutureList);

    // Assert expected behavior
    verify(mockSubscriberStub, times(2)).modifyAckDeadlineCallable();
    verify(mockSubscriberStub, times(1)).acknowledgeCallable();
  }

  @Test
  public void testSendAckOperationsExactlyOnceEnabledMessageFuturesModacks() {
    // Setup

    // The list(s) of ackIds allows us to mock the grpc response(s)
    List<String> ackIdsInitialRequest = new ArrayList<>();
    List<String> ackIdsRetryRequest = new ArrayList<>();

    Map<String, String> errorInfoMetadataMapInitialRequest = new HashMap<String, String>();
    List<ModackWithMessageFuture> modackWithMessageFutureList =
        new ArrayList<ModackWithMessageFuture>();

    ModackWithMessageFuture modackWithMessageFutureDefault =
        new ModackWithMessageFuture(MOCK_ACK_EXTENSION_DEFAULT);

    // Nack SUCCESS
    SettableApiFuture<AckResponse> messageFutureSuccessExpected = SettableApiFuture.create();
    ModackWithMessageFuture modackWithMessageFutureSuccess =
        new ModackWithMessageFuture(
            0, new AckIdMessageFuture(MOCK_ACK_ID_NACK_SUCCESS, messageFutureSuccessExpected));
    modackWithMessageFutureList.add(modackWithMessageFutureSuccess);

    // SUCCESS
    SettableApiFuture<AckResponse> messageFutureNotDoneExpected = SettableApiFuture.create();
    modackWithMessageFutureDefault.addAckIdMessageFuture(
        new AckIdMessageFuture(MOCK_ACK_ID_SUCCESS_NO_MESSAGE, messageFutureNotDoneExpected));
    ackIdsInitialRequest.add(MOCK_ACK_ID_SUCCESS_NO_MESSAGE);

    // INVALID
    SettableApiFuture<AckResponse> messageFutureInvalidExpected = SettableApiFuture.create();
    modackWithMessageFutureDefault.addAckIdMessageFuture(
        new AckIdMessageFuture(MOCK_ACK_ID_INVALID, messageFutureInvalidExpected));
    errorInfoMetadataMapInitialRequest.put(MOCK_ACK_ID_INVALID, PERMANENT_FAILURE_INVALID_ACK_ID);
    ackIdsInitialRequest.add(MOCK_ACK_ID_INVALID);

    // OTHER
    SettableApiFuture<AckResponse> messageFutureOtherExpected = SettableApiFuture.create();
    modackWithMessageFutureDefault.addAckIdMessageFuture(
        new AckIdMessageFuture(MOCK_ACK_ID_OTHER, messageFutureOtherExpected));
    errorInfoMetadataMapInitialRequest.put(MOCK_ACK_ID_OTHER, PERMANENT_FAILURE_OTHER);
    ackIdsInitialRequest.add(MOCK_ACK_ID_OTHER);

    // Initial) FAILURE - TRANSIENT SERVICE UNAVAILABLE
    // Retry) SUCCESS - but no message future set
    SettableApiFuture<AckResponse> messageFutureTransientFailureServiceUnavailableThenSuccess =
        SettableApiFuture.create();
    modackWithMessageFutureDefault.addAckIdMessageFuture(
        new AckIdMessageFuture(
            MOCK_ACK_ID_TRANSIENT_FAILURE_SERVICE_UNAVAILABLE_THEN_SUCCESS,
            messageFutureTransientFailureServiceUnavailableThenSuccess));
    errorInfoMetadataMapInitialRequest.put(
        MOCK_ACK_ID_TRANSIENT_FAILURE_SERVICE_UNAVAILABLE_THEN_SUCCESS,
        TRANSIENT_FAILURE_SERVICE_UNAVAILABLE);
    ackIdsInitialRequest.add(MOCK_ACK_ID_TRANSIENT_FAILURE_SERVICE_UNAVAILABLE_THEN_SUCCESS);
    ackIdsRetryRequest.add(MOCK_ACK_ID_TRANSIENT_FAILURE_SERVICE_UNAVAILABLE_THEN_SUCCESS);

    // Initial) FAILURE - TRANSIENT - UNORDERED ACK ID
    // Retry) SUCCESS - but no message future set
    SettableApiFuture<AckResponse> messageFutureTransientFailureUnorderedAckIdThenSuccess =
        SettableApiFuture.create();
    modackWithMessageFutureDefault.addAckIdMessageFuture(
        new AckIdMessageFuture(
            MOCK_ACK_ID_TRANSIENT_FAILURE_UNORDERED_ACK_ID_THEN_SUCCESS,
            messageFutureTransientFailureUnorderedAckIdThenSuccess));
    errorInfoMetadataMapInitialRequest.put(
        MOCK_ACK_ID_TRANSIENT_FAILURE_UNORDERED_ACK_ID_THEN_SUCCESS,
        TRANSIENT_FAILURE_UNORDERED_ACK_ID);
    ackIdsInitialRequest.add(MOCK_ACK_ID_TRANSIENT_FAILURE_UNORDERED_ACK_ID_THEN_SUCCESS);
    ackIdsRetryRequest.add(MOCK_ACK_ID_TRANSIENT_FAILURE_UNORDERED_ACK_ID_THEN_SUCCESS);

    modackWithMessageFutureList.add(modackWithMessageFutureDefault);

    // Build our requests so we can set our mock responses
    ModifyAckDeadlineRequest modifyAckDeadlineRequestNack =
        ModifyAckDeadlineRequest.newBuilder()
            .setSubscription(MOCK_SUBSCRIPTION_NAME)
            .addAckIds(MOCK_ACK_ID_NACK_SUCCESS)
            .setAckDeadlineSeconds(0)
            .build();

    ModifyAckDeadlineRequest modifyAckDeadlineRequestInitial =
        ModifyAckDeadlineRequest.newBuilder()
            .setSubscription(MOCK_SUBSCRIPTION_NAME)
            .addAllAckIds(ackIdsInitialRequest)
            .setAckDeadlineSeconds(MOCK_ACK_EXTENSION_DEFAULT)
            .build();

    ModifyAckDeadlineRequest modifyAckDeadlineRequestRetry =
        ModifyAckDeadlineRequest.newBuilder()
            .setSubscription(MOCK_SUBSCRIPTION_NAME)
            .addAllAckIds(ackIdsRetryRequest)
            .setAckDeadlineSeconds(MOCK_ACK_EXTENSION_DEFAULT)
            .build();

    // Set mock grpc responses
    when(mockSubscriberStub.modifyAckDeadlineCallable().futureCall(modifyAckDeadlineRequestNack))
        .thenReturn(ApiFutures.immediateFuture(null));
    when(mockSubscriberStub.modifyAckDeadlineCallable().futureCall(modifyAckDeadlineRequestInitial))
        .thenReturn(
            ApiFutures.immediateFailedFuture(
                getMockStatusException(errorInfoMetadataMapInitialRequest)));
    when(mockSubscriberStub
            .modifyAckDeadlineCallable()
            .futureCall(
                argThat(
                    new CustomArgumentMatchers.ModifyAckDeadlineRequestMatcher(
                        modifyAckDeadlineRequestRetry))))
        .thenReturn(ApiFutures.immediateFuture(null));

    // Instantiate class and run operation(s)
    StreamingSubscriberConnection streamingSubscriberConnection =
        getStreamingSubscriberReceiver(mockSubscriberStub, true);

    streamingSubscriberConnection.sendAckOperations(
        modackWithMessageFutureList, Collections.emptyList());

    // Assert expected behavior
    verify(mockSubscriberStub.modifyAckDeadlineCallable(), times(1))
        .futureCall(modifyAckDeadlineRequestNack);
    verify(mockSubscriberStub.modifyAckDeadlineCallable(), times(1))
        .futureCall(modifyAckDeadlineRequestInitial);
    verify(mockSubscriberStub.modifyAckDeadlineCallable(), times(1))
        .futureCall(modifyAckDeadlineRequestRetry);
    verify(mockSubscriberStub, never()).acknowledgeCallable();

    try {
      assertEquals(AckResponse.SUCCESSFUL, messageFutureSuccessExpected.get());
      assertEquals(AckResponse.INVALID, messageFutureInvalidExpected.get());
      assertEquals(AckResponse.OTHER, messageFutureOtherExpected.get());
      assertFalse(messageFutureTransientFailureServiceUnavailableThenSuccess.isDone());
      assertFalse(messageFutureTransientFailureUnorderedAckIdThenSuccess.isDone());
    } catch (Throwable t) {
      // Just in case something went wrong when retrieving our futures
      throw new AssertionError();
    }
  }

  @Test
  public void testSendAckOperationsExactlyOnceEnabledMessageFuturesAcks() {
    // Setup

    // The list(s) of ackIds allows us to mock the grpc response(s)
    List<String> ackIdsInitialRequest = new ArrayList<>();
    List<String> ackIdsRetryRequest = new ArrayList<>();

    Map<String, String> errorInfoMetadataMapInitialRequest = new HashMap<String, String>();
    List<AckIdMessageFuture> ackIdMessageFutureList = new ArrayList<AckIdMessageFuture>();

    // SUCCESS
    SettableApiFuture<AckResponse> messageFutureSuccessExpected = SettableApiFuture.create();
    ackIdMessageFutureList.add(
        new AckIdMessageFuture(MOCK_ACK_ID_SUCCESS, messageFutureSuccessExpected));
    ackIdsInitialRequest.add(MOCK_ACK_ID_SUCCESS);

    // INVALID
    SettableApiFuture<AckResponse> messageFutureInvalidExpected = SettableApiFuture.create();
    ackIdMessageFutureList.add(
        new AckIdMessageFuture(MOCK_ACK_ID_INVALID, messageFutureInvalidExpected));
    errorInfoMetadataMapInitialRequest.put(MOCK_ACK_ID_INVALID, PERMANENT_FAILURE_INVALID_ACK_ID);
    ackIdsInitialRequest.add(MOCK_ACK_ID_INVALID);

    // OTHER
    SettableApiFuture<AckResponse> messageFutureOtherExpected = SettableApiFuture.create();
    ackIdMessageFutureList.add(
        new AckIdMessageFuture(MOCK_ACK_ID_OTHER, messageFutureOtherExpected));
    errorInfoMetadataMapInitialRequest.put(MOCK_ACK_ID_OTHER, PERMANENT_FAILURE_OTHER);
    ackIdsInitialRequest.add(MOCK_ACK_ID_OTHER);

    // Initial) FAILURE - TRANSIENT SERVICE UNAVAILABLE
    // Retry) SUCCESS - but no message future set
    SettableApiFuture<AckResponse> messageFutureTransientFailureServiceUnavailableThenSuccess =
        SettableApiFuture.create();
    ackIdMessageFutureList.add(
        new AckIdMessageFuture(
            MOCK_ACK_ID_TRANSIENT_FAILURE_SERVICE_UNAVAILABLE_THEN_SUCCESS,
            messageFutureTransientFailureServiceUnavailableThenSuccess));
    errorInfoMetadataMapInitialRequest.put(
        MOCK_ACK_ID_TRANSIENT_FAILURE_SERVICE_UNAVAILABLE_THEN_SUCCESS,
        TRANSIENT_FAILURE_SERVICE_UNAVAILABLE);
    ackIdsInitialRequest.add(MOCK_ACK_ID_TRANSIENT_FAILURE_SERVICE_UNAVAILABLE_THEN_SUCCESS);
    ackIdsRetryRequest.add(MOCK_ACK_ID_TRANSIENT_FAILURE_SERVICE_UNAVAILABLE_THEN_SUCCESS);

    // Initial) FAILURE - TRANSIENT - UNORDERED ACK ID
    // Retry) SUCCESS - but no message future set
    SettableApiFuture<AckResponse> messageFutureTransientFailureUnorderedAckIdThenSuccess =
        SettableApiFuture.create();
    ackIdMessageFutureList.add(
        new AckIdMessageFuture(
            MOCK_ACK_ID_TRANSIENT_FAILURE_UNORDERED_ACK_ID_THEN_SUCCESS,
            messageFutureTransientFailureUnorderedAckIdThenSuccess));
    errorInfoMetadataMapInitialRequest.put(
        MOCK_ACK_ID_TRANSIENT_FAILURE_UNORDERED_ACK_ID_THEN_SUCCESS,
        TRANSIENT_FAILURE_UNORDERED_ACK_ID);
    ackIdsInitialRequest.add(MOCK_ACK_ID_TRANSIENT_FAILURE_UNORDERED_ACK_ID_THEN_SUCCESS);
    ackIdsRetryRequest.add(MOCK_ACK_ID_TRANSIENT_FAILURE_UNORDERED_ACK_ID_THEN_SUCCESS);

    // Build our requests so we can set our mock responses
    AcknowledgeRequest acknowledgeRequestInitial =
        AcknowledgeRequest.newBuilder()
            .setSubscription(MOCK_SUBSCRIPTION_NAME)
            .addAllAckIds(ackIdsInitialRequest)
            .build();

    AcknowledgeRequest acknowledgeRequestRetry =
        AcknowledgeRequest.newBuilder()
            .setSubscription(MOCK_SUBSCRIPTION_NAME)
            .addAllAckIds(ackIdsRetryRequest)
            .build();

    // Set mock grpc responses
    when(mockSubscriberStub.acknowledgeCallable().futureCall(acknowledgeRequestInitial))
        .thenReturn(
            ApiFutures.immediateFailedFuture(
                getMockStatusException(errorInfoMetadataMapInitialRequest)));
    when(mockSubscriberStub
            .acknowledgeCallable()
            .futureCall(
                argThat(
                    new CustomArgumentMatchers.AcknowledgeRequestMatcher(acknowledgeRequestRetry))))
        .thenReturn(ApiFutures.immediateFuture(null));

    // Instantiate class and run operation(s)
    StreamingSubscriberConnection streamingSubscriberConnection =
        getStreamingSubscriberReceiver(mockSubscriberStub, true);

    streamingSubscriberConnection.sendAckOperations(
        Collections.emptyList(), ackIdMessageFutureList);

    // Assert expected behavior;
    verify(mockSubscriberStub.acknowledgeCallable(), times(1))
        .futureCall(acknowledgeRequestInitial);
    verify(mockSubscriberStub.acknowledgeCallable(), times(1))
        .futureCall(
            argThat(new CustomArgumentMatchers.AcknowledgeRequestMatcher(acknowledgeRequestRetry)));
    verify(mockSubscriberStub, never()).modifyAckDeadlineCallable();

    try {
      assertEquals(AckResponse.SUCCESSFUL, messageFutureSuccessExpected.get());
      assertEquals(AckResponse.INVALID, messageFutureInvalidExpected.get());
      assertEquals(AckResponse.OTHER, messageFutureOtherExpected.get());
      assertEquals(
          AckResponse.SUCCESSFUL, messageFutureTransientFailureServiceUnavailableThenSuccess.get());
      assertEquals(
          AckResponse.SUCCESSFUL, messageFutureTransientFailureUnorderedAckIdThenSuccess.get());
    } catch (Throwable t) {
      // Just in case something went wrong when retrieving our futures
      throw new AssertionError();
    }
  }

  @Test
  public void testSendAckOperationsExactlyOnceEnabledEnabledModackFailedCancelAckMessageFuture() {
    // Setup

    // The list(s) of ackIds allows us to mock the grpc response(s)
    List<String> ackIdsModackRequest = new ArrayList<>();
    List<String> ackIdsAckRequest = new ArrayList<>();

    Map<String, String> errorInfoMetadataMapModackRequest = new HashMap<String, String>();

    List<ModackWithMessageFuture> ackIdMessageFutureModackList =
        new ArrayList<ModackWithMessageFuture>();
    List<AckIdMessageFuture> ackIdMessageFutureAckList = new ArrayList<AckIdMessageFuture>();
    ModackWithMessageFuture modackWithMessageFuture =
        new ModackWithMessageFuture(MOCK_ACK_EXTENSION_DEFAULT);

    // SUCCESS
    SettableApiFuture<AckResponse> messageFutureSuccessExpected = SettableApiFuture.create();
    AckIdMessageFuture ackIdMessageFutureSuccess =
        new AckIdMessageFuture(MOCK_ACK_ID_NACK_SUCCESS, messageFutureSuccessExpected);
    ackIdMessageFutureAckList.add(ackIdMessageFutureSuccess);
    modackWithMessageFuture.addAckIdMessageFuture(ackIdMessageFutureSuccess);
    ackIdsModackRequest.add(MOCK_ACK_ID_NACK_SUCCESS);
    ackIdsAckRequest.add(MOCK_ACK_ID_NACK_SUCCESS);

    // INVALID
    SettableApiFuture<AckResponse> messageFutureInvalidExpected = SettableApiFuture.create();
    AckIdMessageFuture ackIdMessageFutureInvalid =
        new AckIdMessageFuture(MOCK_ACK_ID_INVALID, messageFutureInvalidExpected);
    ackIdMessageFutureAckList.add(ackIdMessageFutureInvalid);
    modackWithMessageFuture.addAckIdMessageFuture(ackIdMessageFutureInvalid);
    errorInfoMetadataMapModackRequest.put(MOCK_ACK_ID_INVALID, PERMANENT_FAILURE_INVALID_ACK_ID);
    ackIdsModackRequest.add(MOCK_ACK_ID_INVALID);

    // OTHER
    SettableApiFuture<AckResponse> messageFutureOtherExpected = SettableApiFuture.create();
    AckIdMessageFuture ackIdMessageFutureOther =
        new AckIdMessageFuture(MOCK_ACK_ID_OTHER, messageFutureOtherExpected);
    ackIdMessageFutureAckList.add(ackIdMessageFutureOther);
    modackWithMessageFuture.addAckIdMessageFuture(ackIdMessageFutureOther);
    errorInfoMetadataMapModackRequest.put(MOCK_ACK_ID_OTHER, PERMANENT_FAILURE_OTHER);
    ackIdsModackRequest.add(MOCK_ACK_ID_OTHER);

    ackIdMessageFutureModackList.add(modackWithMessageFuture);

    // Build our requests so we can set our mock responses
    ModifyAckDeadlineRequest modifyAckDeadlineRequest =
        ModifyAckDeadlineRequest.newBuilder()
            .setSubscription(MOCK_SUBSCRIPTION_NAME)
            .setAckDeadlineSeconds(MOCK_ACK_EXTENSION_DEFAULT)
            .addAllAckIds(ackIdsModackRequest)
            .build();

    AcknowledgeRequest acknowledgeRequest =
        AcknowledgeRequest.newBuilder()
            .setSubscription(MOCK_SUBSCRIPTION_NAME)
            .addAllAckIds(ackIdsAckRequest)
            .build();

    // Set mock grpc responses
    when(mockSubscriberStub.modifyAckDeadlineCallable().futureCall(modifyAckDeadlineRequest))
        .thenReturn(
            ApiFutures.immediateFailedFuture(
                getMockStatusException(errorInfoMetadataMapModackRequest)));
    when(mockSubscriberStub.acknowledgeCallable().futureCall(acknowledgeRequest))
        .thenReturn(ApiFutures.immediateFuture(null));

    // Instantiate class and run operation(s)
    StreamingSubscriberConnection streamingSubscriberConnection =
        getStreamingSubscriberReceiver(mockSubscriberStub, true);

    streamingSubscriberConnection.sendAckOperations(
        ackIdMessageFutureModackList, ackIdMessageFutureAckList);

    // Assert expected behavior;
    verify(mockSubscriberStub.modifyAckDeadlineCallable(), times(1))
        .futureCall(modifyAckDeadlineRequest);
    verify(mockSubscriberStub.acknowledgeCallable(), times(1)).futureCall(acknowledgeRequest);

    try {
      assertEquals(AckResponse.SUCCESSFUL, messageFutureSuccessExpected.get());
      assertEquals(AckResponse.INVALID, messageFutureInvalidExpected.get());
      assertEquals(AckResponse.OTHER, messageFutureOtherExpected.get());
    } catch (Throwable t) {
      // Just in case something went wrong when retrieving our futures
      throw new AssertionError();
    }
  }

  private StreamingSubscriberConnection getStreamingSubscriberReceiver(
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
        .setExactlyOnceDeliveryEnabled(setExactlyOnceDeliveryEnabled)
        .build();
  }

  private StreamingSubscriberConnection getStreamingSubscriberReceiverWithAckResponse(
      SubscriberStub mockSubscriberStub) {
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
        .setExactlyOnceDeliveryEnabled(true)
        .build();
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
