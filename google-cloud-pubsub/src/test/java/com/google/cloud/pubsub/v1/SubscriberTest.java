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

import static com.google.cloud.pubsub.v1.StreamingSubscriberConnection.DEFAULT_STREAM_ACK_DEADLINE;
import static com.google.cloud.pubsub.v1.StreamingSubscriberConnection.MAX_STREAM_ACK_DEADLINE;
import static com.google.cloud.pubsub.v1.StreamingSubscriberConnection.MIN_STREAM_ACK_DEADLINE;
import static com.google.cloud.pubsub.v1.Subscriber.DEFAULT_MAX_DURATION_PER_ACK_EXTENSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.pubsub.v1.Subscriber.Builder;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.threeten.bp.Duration;

/** Tests for {@link Subscriber}. */
public class SubscriberTest {

  private static final ProjectSubscriptionName TEST_SUBSCRIPTION =
      ProjectSubscriptionName.of("test-project", "test-subscription");

  private ManagedChannel testChannel;
  private FakeScheduledExecutorService fakeExecutor;
  private FakeSubscriberServiceImpl fakeSubscriberServiceImpl;
  private Server testServer;

  private final MessageReceiver testReceiver =
      new MessageReceiver() {
        @Override
        public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
          consumer.ack();
        }
      };

  @Rule public TestName testName = new TestName();

  @Before
  public void setUp() throws Exception {
    InProcessServerBuilder serverBuilder = InProcessServerBuilder.forName(testName.getMethodName());
    fakeSubscriberServiceImpl = new FakeSubscriberServiceImpl();
    fakeExecutor = new FakeScheduledExecutorService();
    testChannel = InProcessChannelBuilder.forName(testName.getMethodName()).build();
    serverBuilder.addService(fakeSubscriberServiceImpl);
    testServer = serverBuilder.build();
    testServer.start();
  }

  @After
  public void tearDown() throws Exception {
    testServer.shutdownNow().awaitTermination();
    testChannel.shutdown();
  }

  @Test
  public void testDeliveryAttemptHelper() {
    Integer deliveryAttempt = 3;
    PubsubMessage message =
        PubsubMessage.newBuilder()
            .putAttributes("googclient_deliveryattempt", Integer.toString(deliveryAttempt))
            .build();
    assertEquals(Subscriber.getDeliveryAttempt(message), deliveryAttempt);

    // In the case where delivery attempt attribute is not populated, expect null
    PubsubMessage emptyMessage = PubsubMessage.newBuilder().build();
    assertEquals(Subscriber.getDeliveryAttempt(emptyMessage), null);
  }

  @Test
  public void testOpenedChannels() throws Exception {
    int expectedChannelCount = 1;

    Subscriber subscriber = startSubscriber(getTestSubscriberBuilder(testReceiver));

    assertEquals(
        expectedChannelCount, fakeSubscriberServiceImpl.waitForOpenedStreams(expectedChannelCount));

    subscriber.stopAsync().awaitTerminated();
  }

  @Test
  public void testFailedChannel_recoverableError_channelReopened() throws Exception {
    int expectedChannelCount = 1;

    Subscriber subscriber =
        startSubscriber(
            getTestSubscriberBuilder(testReceiver)
                .setSystemExecutorProvider(
                    InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(1).build()));

    // Recoverable error
    fakeSubscriberServiceImpl.sendError(new StatusException(Status.INTERNAL));

    assertEquals(1, fakeSubscriberServiceImpl.waitForClosedStreams(1));
    assertEquals(
        expectedChannelCount, fakeSubscriberServiceImpl.waitForOpenedStreams(expectedChannelCount));

    subscriber.stopAsync().awaitTerminated();
  }

  @Test
  public void testFailedChannel_testTerminated() throws Exception {
    final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(5);
    ExecutorProvider provider =
        new ExecutorProvider() {
          @Override
          public boolean shouldAutoClose() {
            return true;
          }

          @Override
          public ScheduledExecutorService getExecutor() {
            return scheduledExecutorService;
          }
        };

    try {
      Subscriber subscriber =
          startSubscriber(
              getTestSubscriberBuilder(testReceiver).setSystemExecutorProvider(provider));

      // wait long enough for the MessageDispatcher to set up, which at one point
      // caused shutdown problems.
      Thread.sleep(100);
      subscriber.stopAsync().awaitTerminated();

      assertTrue(scheduledExecutorService.awaitTermination(10, TimeUnit.SECONDS));
    } finally {
      scheduledExecutorService.shutdownNow();
    }
  }

  @Test(expected = IllegalStateException.class)
  public void testFailedChannel_fatalError_subscriberFails() throws Exception {
    Subscriber subscriber =
        startSubscriber(
            getTestSubscriberBuilder(testReceiver)
                .setSystemExecutorProvider(
                    InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(10).build()));

    // Fatal error
    fakeSubscriberServiceImpl.sendError(new StatusException(Status.INVALID_ARGUMENT));

    try {
      subscriber.awaitTerminated();
    } finally {
      // The subscriber must finish with an state error because its FAILED status.
      assertEquals(Subscriber.State.FAILED, subscriber.state());

      Throwable t = subscriber.failureCause();
      assertTrue(t instanceof ApiException);

      ApiException ex = (ApiException) (t);
      assertTrue(ex.getStatusCode() instanceof GrpcStatusCode);

      GrpcStatusCode grpcCode = (GrpcStatusCode) ex.getStatusCode();
      assertEquals(StatusCode.Code.INVALID_ARGUMENT, grpcCode.getCode());
    }
  }

  @Test(expected = IllegalStateException.class)
  public void testFailedChannel_shutdownBackgroundResources() throws Exception {
    ExecutorProvider provider =
        new ExecutorProvider() {
          @Override
          public boolean shouldAutoClose() {
            return true;
          }

          @Override
          public ScheduledExecutorService getExecutor() {
            return fakeExecutor;
          }
        };

    Subscriber subscriber =
        startSubscriber(getTestSubscriberBuilder(testReceiver).setExecutorProvider(provider));

    // Fatal error
    fakeSubscriberServiceImpl.sendError(new StatusException(Status.INVALID_ARGUMENT));

    try {
      subscriber.awaitTerminated();
    } finally {
      // The subscriber must finish with an state error because its FAILED status.
      assertEquals(Subscriber.State.FAILED, subscriber.state());

      // Make sure that our executor is shut down after a failure
      assertTrue(fakeExecutor.isShutdown());
    }
  }

  @Test
  public void testStreamAckDeadlineIsSetCorrectly() throws Exception {
    int expectedChannelCount = 1;
    // Deadline is smaller than the allowed streamAckDeadline.
    int maxDurationPerAckExtension = 5;

    Subscriber subscriber =
        startSubscriber(
            getTestSubscriberBuilder(testReceiver)
                .setMaxDurationPerAckExtension(Duration.ofSeconds(maxDurationPerAckExtension)));
    assertEquals(
        expectedChannelCount, fakeSubscriberServiceImpl.waitForOpenedStreams(expectedChannelCount));
    assertEquals(
        MIN_STREAM_ACK_DEADLINE.getSeconds(),
        fakeSubscriberServiceImpl.getLastSeenRequest().getStreamAckDeadlineSeconds());

    subscriber.stopAsync().awaitTerminated();

    // Deadline is higher than the allowed streamAckDeadline.
    maxDurationPerAckExtension = 700;
    subscriber =
        startSubscriber(
            getTestSubscriberBuilder(testReceiver)
                .setMaxDurationPerAckExtension(Duration.ofSeconds(maxDurationPerAckExtension)));
    assertEquals(
        expectedChannelCount, fakeSubscriberServiceImpl.waitForOpenedStreams(expectedChannelCount));
    assertEquals(
        MAX_STREAM_ACK_DEADLINE.getSeconds(),
        fakeSubscriberServiceImpl.getLastSeenRequest().getStreamAckDeadlineSeconds());

    subscriber.stopAsync().awaitTerminated();

    // Deadline is within the allowed limits for streamAckDeadline.
    maxDurationPerAckExtension = 100;
    subscriber =
        startSubscriber(
            getTestSubscriberBuilder(testReceiver)
                .setMaxDurationPerAckExtension(Duration.ofSeconds(maxDurationPerAckExtension)));
    assertEquals(
        expectedChannelCount, fakeSubscriberServiceImpl.waitForOpenedStreams(expectedChannelCount));
    assertEquals(
        maxDurationPerAckExtension,
        fakeSubscriberServiceImpl.getLastSeenRequest().getStreamAckDeadlineSeconds());

    subscriber.stopAsync().awaitTerminated();

    // maxDurationPerAckExtension is unset.
    maxDurationPerAckExtension = (int) DEFAULT_MAX_DURATION_PER_ACK_EXTENSION.getSeconds();
    subscriber =
        startSubscriber(
            getTestSubscriberBuilder(testReceiver)
                .setMaxDurationPerAckExtension(Duration.ofSeconds(maxDurationPerAckExtension)));
    assertEquals(
        expectedChannelCount, fakeSubscriberServiceImpl.waitForOpenedStreams(expectedChannelCount));
    assertEquals(
        DEFAULT_STREAM_ACK_DEADLINE.getSeconds(),
        fakeSubscriberServiceImpl.getLastSeenRequest().getStreamAckDeadlineSeconds());

    subscriber.stopAsync().awaitTerminated();
  }

  @Test
  public void testPartialFlowControlSettings() throws Exception {
    Subscriber subscriber =
        getTestSubscriberBuilder(testReceiver)
            .setFlowControlSettings(
                Subscriber.Builder.DEFAULT_FLOW_CONTROL_SETTINGS
                    .toBuilder()
                    .setMaxOutstandingElementCount(500L)
                    .build())
            .build();
    assertEquals((long) subscriber.getFlowControlSettings().getMaxOutstandingElementCount(), 500);
    assertEquals(
        subscriber.getFlowControlSettings().getMaxOutstandingRequestBytes(),
        Subscriber.Builder.DEFAULT_FLOW_CONTROL_SETTINGS.getMaxOutstandingRequestBytes());

    subscriber =
        getTestSubscriberBuilder(testReceiver)
            .setFlowControlSettings(
                Subscriber.Builder.DEFAULT_FLOW_CONTROL_SETTINGS
                    .toBuilder()
                    .setMaxOutstandingRequestBytes(5_000_000_000L)
                    .build())
            .build();
    assertEquals(
        (long) subscriber.getFlowControlSettings().getMaxOutstandingRequestBytes(), 5_000_000_000L);
    assertEquals(
        subscriber.getFlowControlSettings().getMaxOutstandingElementCount(),
        Subscriber.Builder.DEFAULT_FLOW_CONTROL_SETTINGS.getMaxOutstandingElementCount());
  }

  private Subscriber startSubscriber(Builder testSubscriberBuilder) {
    Subscriber subscriber = testSubscriberBuilder.build();
    subscriber.startAsync().awaitRunning();
    return subscriber;
  }

  private Builder getTestSubscriberBuilder(MessageReceiver receiver) {
    return Subscriber.newBuilder(TEST_SUBSCRIPTION, receiver)
        .setExecutorProvider(FixedExecutorProvider.create(fakeExecutor))
        .setSystemExecutorProvider(FixedExecutorProvider.create(fakeExecutor))
        .setChannelProvider(
            FixedTransportChannelProvider.create(GrpcTransportChannel.create(testChannel)))
        .setCredentialsProvider(NoCredentialsProvider.create())
        .setClock(fakeExecutor.getClock())
        .setParallelPullCount(1)
        .setMaxDurationPerAckExtension(Duration.ofSeconds(5))
        .setFlowControlSettings(
            FlowControlSettings.newBuilder().setMaxOutstandingElementCount(1000L).build());
  }
}
