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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.core.ApiFuture;
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.grpc.testing.LocalChannelProvider;
import com.google.api.gax.rpc.DataLossException;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.Publisher.Builder;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PublishResponse;
import com.google.pubsub.v1.PubsubMessage;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class PublisherImplTest {

  private static final ProjectTopicName TEST_TOPIC =
      ProjectTopicName.of("test-project", "test-topic");

  private static final ExecutorProvider SINGLE_THREAD_EXECUTOR =
      InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(1).build();

  private static final TransportChannelProvider TEST_CHANNEL_PROVIDER =
      LocalChannelProvider.create("test-server");

  private FakeScheduledExecutorService fakeExecutor;

  private FakePublisherServiceImpl testPublisherServiceImpl;

  private ManagedChannel testChannel;

  private Server testServer;

  @Before
  public void setUp() throws Exception {
    testPublisherServiceImpl = new FakePublisherServiceImpl();

    InProcessServerBuilder serverBuilder = InProcessServerBuilder.forName("test-server");
    serverBuilder.addService(testPublisherServiceImpl);
    testServer = serverBuilder.build();
    testChannel = InProcessChannelBuilder.forName("test-server").build();
    testServer.start();

    fakeExecutor = new FakeScheduledExecutorService();
  }

  @After
  public void tearDown() throws Exception {
    testServer.shutdownNow().awaitTermination();
    testChannel.shutdown();
  }

  @Test
  public void testPublishByDuration() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            // To demonstrate that reaching duration will trigger publish
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setDelayThreshold(Duration.ofSeconds(5))
                    .setElementCountThreshold(10L)
                    .build())
            .build();

    testPublisherServiceImpl.addPublishResponse(
        PublishResponse.newBuilder().addMessageIds("1").addMessageIds("2"));

    ApiFuture<String> publishFuture1 = sendTestMessage(publisher, "A");
    ApiFuture<String> publishFuture2 = sendTestMessage(publisher, "B");

    assertFalse(publishFuture1.isDone());
    assertFalse(publishFuture2.isDone());

    fakeExecutor.advanceTime(Duration.ofSeconds(10));

    assertEquals("1", publishFuture1.get());
    assertEquals("2", publishFuture2.get());

    assertEquals(2, testPublisherServiceImpl.getCapturedRequests().get(0).getMessagesCount());
    shutdownTestPublisher(publisher);
  }

  @Test
  public void testPublishByNumBatchedMessages() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setElementCountThreshold(2L)
                    .setDelayThreshold(Duration.ofSeconds(100))
                    .build())
            .build();

    testPublisherServiceImpl
        .addPublishResponse(PublishResponse.newBuilder().addMessageIds("1").addMessageIds("2"))
        .addPublishResponse(PublishResponse.newBuilder().addMessageIds("3").addMessageIds("4"));

    ApiFuture<String> publishFuture1 = sendTestMessage(publisher, "A");
    ApiFuture<String> publishFuture2 = sendTestMessage(publisher, "B");
    ApiFuture<String> publishFuture3 = sendTestMessage(publisher, "C");

    // Note we are not advancing time but message should still get published

    assertEquals("1", publishFuture1.get());
    assertEquals("2", publishFuture2.get());

    assertFalse(publishFuture3.isDone());

    ApiFuture<String> publishFuture4 = sendTestMessage(publisher, "D");

    assertEquals("3", publishFuture3.get());
    assertEquals("4", publishFuture4.get());

    assertEquals(2, testPublisherServiceImpl.getCapturedRequests().get(0).getMessagesCount());
    assertEquals(2, testPublisherServiceImpl.getCapturedRequests().get(1).getMessagesCount());

    fakeExecutor.advanceTime(Duration.ofSeconds(100));
    shutdownTestPublisher(publisher);
  }

  @Test
  public void testSinglePublishByNumBytes() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setElementCountThreshold(2L)
                    .setDelayThreshold(Duration.ofSeconds(100))
                    .build())
            .build();

    testPublisherServiceImpl
        .addPublishResponse(PublishResponse.newBuilder().addMessageIds("1").addMessageIds("2"))
        .addPublishResponse(PublishResponse.newBuilder().addMessageIds("3").addMessageIds("4"));

    ApiFuture<String> publishFuture1 = sendTestMessage(publisher, "A");
    ApiFuture<String> publishFuture2 = sendTestMessage(publisher, "B");
    ApiFuture<String> publishFuture3 = sendTestMessage(publisher, "C");

    // Note we are not advancing time but message should still get published

    assertEquals("1", publishFuture1.get());
    assertEquals("2", publishFuture2.get());
    assertFalse(publishFuture3.isDone());

    ApiFuture<String> publishFuture4 = sendTestMessage(publisher, "D");
    assertEquals("3", publishFuture3.get());
    assertEquals("4", publishFuture4.get());

    assertEquals(2, testPublisherServiceImpl.getCapturedRequests().size());

    fakeExecutor.advanceTime(Duration.ofSeconds(100));
    shutdownTestPublisher(publisher);
  }

  @Test
  public void testPublishByShutdown() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setDelayThreshold(Duration.ofSeconds(100))
                    .setElementCountThreshold(10L)
                    .build())
            .build();

    testPublisherServiceImpl.addPublishResponse(
        PublishResponse.newBuilder().addMessageIds("1").addMessageIds("2"));

    ApiFuture<String> publishFuture1 = sendTestMessage(publisher, "A");
    ApiFuture<String> publishFuture2 = sendTestMessage(publisher, "B");

    // Note we are not advancing time or reaching the count threshold but messages should
    // still get published by call to shutdown
    publisher.shutdown();

    // Verify the publishes completed
    assertTrue(publishFuture1.isDone());
    assertTrue(publishFuture2.isDone());
    assertEquals("1", publishFuture1.get());
    assertEquals("2", publishFuture2.get());

    fakeExecutor.advanceTime(Duration.ofSeconds(100));
    publisher.awaitTermination(1, TimeUnit.MINUTES);
  }

  @Test
  public void testPublishMixedSizeAndDuration() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            // To demonstrate that reaching duration will trigger publish
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setElementCountThreshold(2L)
                    .setDelayThreshold(Duration.ofSeconds(5))
                    .build())
            .build();

    testPublisherServiceImpl.addPublishResponse(
        PublishResponse.newBuilder().addMessageIds("1").addMessageIds("2"));
    testPublisherServiceImpl.addPublishResponse(PublishResponse.newBuilder().addMessageIds("3"));

    ApiFuture<String> publishFuture1 = sendTestMessage(publisher, "A");

    fakeExecutor.advanceTime(Duration.ofSeconds(2));
    assertFalse(publishFuture1.isDone());

    ApiFuture<String> publishFuture2 = sendTestMessage(publisher, "B");

    // Publishing triggered by batch size
    assertEquals("1", publishFuture1.get());
    assertEquals("2", publishFuture2.get());

    ApiFuture<String> publishFuture3 = sendTestMessage(publisher, "C");

    assertFalse(publishFuture3.isDone());

    // Publishing triggered by time
    fakeExecutor.advanceTime(Duration.ofSeconds(5));

    assertEquals("3", publishFuture3.get());

    assertEquals(2, testPublisherServiceImpl.getCapturedRequests().get(0).getMessagesCount());
    assertEquals(1, testPublisherServiceImpl.getCapturedRequests().get(1).getMessagesCount());
    shutdownTestPublisher(publisher);
  }

  private ApiFuture<String> sendTestMessage(Publisher publisher, String data) {
    return publisher.publish(
        PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8(data)).build());
  }

  @Test
  public void testBatchedMessagesWithOrderingKeyByNum() throws Exception {
    // Limit the number of maximum elements in a single batch to 3.
    Publisher publisher =
        getTestPublisherBuilder()
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setElementCountThreshold(3L)
                    .setDelayThreshold(Duration.ofSeconds(100))
                    .build())
            .setEnableMessageOrdering(true)
            .build();
    testPublisherServiceImpl.setAutoPublishResponse(true);

    // Publish two messages with ordering key, "OrderA", and other two messages with "OrderB".
    ApiFuture<String> publishFuture1 = sendTestMessageWithOrderingKey(publisher, "m1", "OrderA");
    ApiFuture<String> publishFuture2 = sendTestMessageWithOrderingKey(publisher, "m2", "OrderB");
    ApiFuture<String> publishFuture3 = sendTestMessageWithOrderingKey(publisher, "m3", "OrderA");
    ApiFuture<String> publishFuture4 = sendTestMessageWithOrderingKey(publisher, "m4", "OrderB");

    // Verify that none of them were published since the batching size is 3.
    assertFalse(publishFuture1.isDone());
    assertFalse(publishFuture2.isDone());
    assertFalse(publishFuture3.isDone());
    assertFalse(publishFuture4.isDone());

    // One of the batches reaches the limit.
    ApiFuture<String> publishFuture5 = sendTestMessageWithOrderingKey(publisher, "m5", "OrderA");
    // Verify that they were delivered in order per ordering key.
    assertTrue(Integer.parseInt(publishFuture1.get()) < Integer.parseInt(publishFuture3.get()));
    assertTrue(Integer.parseInt(publishFuture3.get()) < Integer.parseInt(publishFuture5.get()));

    // The other batch reaches the limit.
    ApiFuture<String> publishFuture6 = sendTestMessageWithOrderingKey(publisher, "m6", "OrderB");
    assertTrue(Integer.parseInt(publishFuture2.get()) < Integer.parseInt(publishFuture4.get()));
    assertTrue(Integer.parseInt(publishFuture4.get()) < Integer.parseInt(publishFuture6.get()));

    // Verify that every message within the same batch has the same ordering key.
    List<PublishRequest> requests = testPublisherServiceImpl.getCapturedRequests();
    for (PublishRequest request : requests) {
      if (request.getMessagesCount() > 1) {
        String orderingKey = request.getMessages(0).getOrderingKey();
        for (PubsubMessage message : request.getMessagesList()) {
          assertEquals(message.getOrderingKey(), orderingKey);
        }
      }
    }

    fakeExecutor.advanceTime(Duration.ofSeconds(100));
    shutdownTestPublisher(publisher);
  }

  @Test
  public void testBatchedMessagesWithOrderingKeyByDuration() throws Exception {
    // Limit the batching timeout to 100 seconds.
    Publisher publisher =
        getTestPublisherBuilder()
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setElementCountThreshold(10L)
                    .setDelayThreshold(Duration.ofSeconds(100))
                    .build())
            .setEnableMessageOrdering(true)
            .build();
    testPublisherServiceImpl.setAutoPublishResponse(true);
    testPublisherServiceImpl.setExecutor(fakeExecutor);
    testPublisherServiceImpl.setPublishResponseDelay(Duration.ofSeconds(300));

    // Publish two messages with ordering key, "OrderA", and other two messages with "OrderB".
    ApiFuture<String> publishFuture1 = sendTestMessageWithOrderingKey(publisher, "m1", "OrderA");
    ApiFuture<String> publishFuture2 = sendTestMessageWithOrderingKey(publisher, "m2", "OrderB");
    ApiFuture<String> publishFuture3 = sendTestMessageWithOrderingKey(publisher, "m3", "OrderA");
    ApiFuture<String> publishFuture4 = sendTestMessageWithOrderingKey(publisher, "m4", "OrderB");

    // Verify that none of them were published since the batching size is 10 and timeout has not
    // been expired.
    assertFalse(publishFuture1.isDone());
    assertFalse(publishFuture2.isDone());
    assertFalse(publishFuture3.isDone());
    assertFalse(publishFuture4.isDone());

    // The timeout expires.
    fakeExecutor.advanceTime(Duration.ofSeconds(100));

    // Publish one more message on "OrderA" while publishes are outstanding.
    testPublisherServiceImpl.setPublishResponseDelay(Duration.ZERO);
    ApiFuture<String> publishFuture5 = sendTestMessageWithOrderingKey(publisher, "m5", "OrderA");

    // The second timeout expires.
    fakeExecutor.advanceTime(Duration.ofSeconds(100));

    // Publishing completes on the first four messages.
    fakeExecutor.advanceTime(Duration.ofSeconds(200));

    // Verify that they were delivered in order per ordering key.
    assertTrue(Integer.parseInt(publishFuture1.get()) < Integer.parseInt(publishFuture3.get()));
    assertTrue(Integer.parseInt(publishFuture2.get()) < Integer.parseInt(publishFuture4.get()));

    // Verify that they were delivered in order per ordering key.
    assertTrue(Integer.parseInt(publishFuture3.get()) < Integer.parseInt(publishFuture5.get()));

    // Verify that every message within the same batch has the same ordering key.
    List<PublishRequest> requests = testPublisherServiceImpl.getCapturedRequests();
    for (PublishRequest request : requests) {
      if (request.getMessagesCount() > 1) {
        String orderingKey = request.getMessages(0).getOrderingKey();
        for (PubsubMessage message : request.getMessagesList()) {
          assertEquals(message.getOrderingKey(), orderingKey);
        }
      }
    }
    shutdownTestPublisher(publisher);
  }

  @Test
  public void testLargeMessagesDoNotReorderBatches() throws Exception {
    // Set the maximum batching size to 20 bytes.
    Publisher publisher =
        getTestPublisherBuilder()
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setElementCountThreshold(10L)
                    .setRequestByteThreshold(20L)
                    .setDelayThreshold(Duration.ofSeconds(100))
                    .build())
            .setEnableMessageOrdering(true)
            .build();
    testPublisherServiceImpl.setAutoPublishResponse(true);
    ApiFuture<String> publishFuture1 = sendTestMessageWithOrderingKey(publisher, "m1", "OrderA");
    ApiFuture<String> publishFuture2 = sendTestMessageWithOrderingKey(publisher, "m2", "OrderB");

    assertFalse(publishFuture1.isDone());
    assertFalse(publishFuture2.isDone());

    ApiFuture<String> publishFuture3 =
        sendTestMessageWithOrderingKey(publisher, "VeryLargeMessage", "OrderB");
    // Verify that messages with "OrderB" were delivered in order.
    assertTrue(Integer.parseInt(publishFuture2.get()) < Integer.parseInt(publishFuture3.get()));

    fakeExecutor.advanceTime(Duration.ofSeconds(100));
    shutdownTestPublisher(publisher);
  }

  @Test
  public void testOrderingKeyWhenDisabled_throwsException() throws Exception {
    // Message ordering is disabled by default.
    Publisher publisher = getTestPublisherBuilder().build();
    try {
      ApiFuture<String> publishFuture = sendTestMessageWithOrderingKey(publisher, "m1", "orderA");
      fail("Should have thrown an IllegalStateException");
    } catch (IllegalStateException expected) {
      // expected
    }
    shutdownTestPublisher(publisher);
  }

  @Test
  public void testEnableMessageOrdering_overwritesMaxAttempts() throws Exception {
    // Set maxAttempts to 1 and enableMessageOrdering to true at the same time.
    Publisher publisher =
        getTestPublisherBuilder()
            .setExecutorProvider(SINGLE_THREAD_EXECUTOR)
            .setRetrySettings(
                Publisher.Builder.DEFAULT_RETRY_SETTINGS
                    .toBuilder()
                    .setTotalTimeout(Duration.ofSeconds(10))
                    .setMaxAttempts(1)
                    .build())
            .setEnableMessageOrdering(true)
            .build();

    // Although maxAttempts is 1, the publisher will retry until it succeeds since
    // enableMessageOrdering is true.
    testPublisherServiceImpl.addPublishError(new Throwable("Transiently failing"));
    testPublisherServiceImpl.addPublishError(new Throwable("Transiently failing"));
    testPublisherServiceImpl.addPublishError(new Throwable("Transiently failing"));
    testPublisherServiceImpl.addPublishResponse(PublishResponse.newBuilder().addMessageIds("1"));

    ApiFuture<String> publishFuture1 = sendTestMessageWithOrderingKey(publisher, "m1", "orderA");
    assertEquals("1", publishFuture1.get());

    assertEquals(4, testPublisherServiceImpl.getCapturedRequests().size());
    publisher.shutdown();
    assertTrue(publisher.awaitTermination(1, TimeUnit.MINUTES));
  }

  @Test
  /**
   * Make sure that resume publishing works as expected:
   *
   * <ol>
   *   <li>publish with key orderA which returns a failure.
   *   <li>publish with key orderA again, which should fail immediately
   *   <li>publish with key orderB, which should succeed
   *   <li>resume publishing on key orderA
   *   <li>publish with key orderA, which should now succeed
   * </ol>
   */
  public void testResumePublish() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setElementCountThreshold(2L)
                    .build())
            .setEnableMessageOrdering(true)
            .build();

    ApiFuture<String> future1 = sendTestMessageWithOrderingKey(publisher, "m1", "orderA");
    ApiFuture<String> future2 = sendTestMessageWithOrderingKey(publisher, "m2", "orderA");

    fakeExecutor.advanceTime(Duration.ZERO);
    assertFalse(future1.isDone());
    assertFalse(future2.isDone());

    // This exception should stop future publishing to the same key
    testPublisherServiceImpl.addPublishError(new StatusException(Status.INVALID_ARGUMENT));

    fakeExecutor.advanceTime(Duration.ZERO);

    try {
      future1.get();
      Assert.fail("This should fail.");
    } catch (ExecutionException e) {
    }

    try {
      future2.get();
      Assert.fail("This should fail.");
    } catch (ExecutionException e) {
    }

    // Submit new requests with orderA that should fail.
    ApiFuture<String> future3 = sendTestMessageWithOrderingKey(publisher, "m3", "orderA");
    ApiFuture<String> future4 = sendTestMessageWithOrderingKey(publisher, "m4", "orderA");

    try {
      future3.get();
      Assert.fail("This should fail.");
    } catch (ExecutionException e) {
      assertEquals(SequentialExecutorService.CallbackExecutor.CANCELLATION_EXCEPTION, e.getCause());
    }

    try {
      future4.get();
      Assert.fail("This should fail.");
    } catch (ExecutionException e) {
      assertEquals(SequentialExecutorService.CallbackExecutor.CANCELLATION_EXCEPTION, e.getCause());
    }

    // Submit a new request with orderB, which should succeed
    ApiFuture<String> future5 = sendTestMessageWithOrderingKey(publisher, "m5", "orderB");
    ApiFuture<String> future6 = sendTestMessageWithOrderingKey(publisher, "m6", "orderB");

    testPublisherServiceImpl.addPublishResponse(
        PublishResponse.newBuilder().addMessageIds("5").addMessageIds("6"));

    Assert.assertEquals("5", future5.get());
    Assert.assertEquals("6", future6.get());

    // Resume publishing of "orderA", which should now succeed
    publisher.resumePublish("orderA");

    ApiFuture<String> future7 = sendTestMessageWithOrderingKey(publisher, "m7", "orderA");
    ApiFuture<String> future8 = sendTestMessageWithOrderingKey(publisher, "m8", "orderA");

    testPublisherServiceImpl.addPublishResponse(
        PublishResponse.newBuilder().addMessageIds("7").addMessageIds("8"));

    Assert.assertEquals("7", future7.get());
    Assert.assertEquals("8", future8.get());

    shutdownTestPublisher(publisher);
  }

  @Test
  public void testPublishThrowExceptionForUnsubmittedOrderingKeyMessage() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setExecutorProvider(SINGLE_THREAD_EXECUTOR)
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setElementCountThreshold(2L)
                    .setDelayThreshold(Duration.ofSeconds(500))
                    .build())
            .setEnableMessageOrdering(true)
            .build();

    // Send two messages that will fulfill the first batch, which will return a failure.
    testPublisherServiceImpl.addPublishError(new StatusException(Status.INVALID_ARGUMENT));
    ApiFuture<String> publishFuture1 = sendTestMessageWithOrderingKey(publisher, "A", "a");
    ApiFuture<String> publishFuture2 = sendTestMessageWithOrderingKey(publisher, "B", "a");

    // A third message will fail because the first attempt to publish failed.
    ApiFuture<String> publishFuture3 = sendTestMessageWithOrderingKey(publisher, "C", "a");

    try {
      publishFuture1.get();
      fail("Should have failed.");
    } catch (ExecutionException e) {
    }

    try {
      publishFuture2.get();
      fail("Should have failed.");
    } catch (ExecutionException e) {
    }

    try {
      publishFuture3.get();
      fail("Should have failed.");
    } catch (ExecutionException e) {
      assertEquals(SequentialExecutorService.CallbackExecutor.CANCELLATION_EXCEPTION, e.getCause());
    }

    // A subsequent attempt fails immediately.
    ApiFuture<String> publishFuture4 = sendTestMessageWithOrderingKey(publisher, "D", "a");
    try {
      publishFuture4.get();
      fail("Should have failed.");
    } catch (ExecutionException e) {
      assertEquals(SequentialExecutorService.CallbackExecutor.CANCELLATION_EXCEPTION, e.getCause());
    }
  }

  private ApiFuture<String> sendTestMessageWithOrderingKey(
      Publisher publisher, String data, String orderingKey) {
    return publisher.publish(
        PubsubMessage.newBuilder()
            .setOrderingKey(orderingKey)
            .setData(ByteString.copyFromUtf8(data))
            .build());
  }

  @Test
  public void testErrorPropagation() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setExecutorProvider(SINGLE_THREAD_EXECUTOR)
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setElementCountThreshold(1L)
                    .setDelayThreshold(Duration.ofSeconds(5))
                    .build())
            .build();
    testPublisherServiceImpl.addPublishError(Status.DATA_LOSS.asException());
    try {
      sendTestMessage(publisher, "A").get();
      fail("should throw exception");
    } catch (ExecutionException e) {
      assertThat(e.getCause()).isInstanceOf(DataLossException.class);
    }
  }

  @Test
  public void testPublishFailureRetries() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setExecutorProvider(SINGLE_THREAD_EXECUTOR)
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setElementCountThreshold(1L)
                    .setDelayThreshold(Duration.ofSeconds(5))
                    .build())
            .build(); // To demonstrate that reaching duration will trigger publish

    testPublisherServiceImpl.addPublishError(new Throwable("Transiently failing"));
    testPublisherServiceImpl.addPublishResponse(PublishResponse.newBuilder().addMessageIds("1"));

    ApiFuture<String> publishFuture1 = sendTestMessage(publisher, "A");

    assertEquals("1", publishFuture1.get());

    assertEquals(2, testPublisherServiceImpl.getCapturedRequests().size());
    shutdownTestPublisher(publisher);
  }

  @Test(expected = ExecutionException.class)
  public void testPublishFailureRetries_retriesDisabled() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setExecutorProvider(SINGLE_THREAD_EXECUTOR)
            .setRetrySettings(
                Publisher.Builder.DEFAULT_RETRY_SETTINGS
                    .toBuilder()
                    .setTotalTimeout(Duration.ofSeconds(10))
                    .setMaxAttempts(1)
                    .build())
            .build();

    testPublisherServiceImpl.addPublishError(new Throwable("Transiently failing"));

    ApiFuture<String> publishFuture1 = sendTestMessage(publisher, "A");

    try {
      publishFuture1.get();
    } finally {
      assertSame(testPublisherServiceImpl.getCapturedRequests().size(), 1);
      shutdownTestPublisher(publisher);
    }
  }

  @Test
  public void testPublishFailureRetries_maxRetriesSetup() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setExecutorProvider(SINGLE_THREAD_EXECUTOR)
            .setRetrySettings(
                Publisher.Builder.DEFAULT_RETRY_SETTINGS
                    .toBuilder()
                    .setTotalTimeout(Duration.ofSeconds(10))
                    .setMaxAttempts(3)
                    .build())
            .build();

    testPublisherServiceImpl.addPublishError(new Throwable("Transiently failing"));
    testPublisherServiceImpl.addPublishError(new Throwable("Transiently failing"));
    testPublisherServiceImpl.addPublishResponse(PublishResponse.newBuilder().addMessageIds("1"));

    ApiFuture<String> publishFuture1 = sendTestMessage(publisher, "A");

    assertEquals("1", publishFuture1.get());

    assertEquals(3, testPublisherServiceImpl.getCapturedRequests().size());
    shutdownTestPublisher(publisher);
  }

  @Test
  public void testPublishFailureRetries_maxRetriesSetUnlimited() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setExecutorProvider(SINGLE_THREAD_EXECUTOR)
            .setRetrySettings(
                Publisher.Builder.DEFAULT_RETRY_SETTINGS
                    .toBuilder()
                    .setTotalTimeout(Duration.ofSeconds(10))
                    .setMaxAttempts(0)
                    .build())
            .build();

    testPublisherServiceImpl.addPublishError(new Throwable("Transiently failing"));
    testPublisherServiceImpl.addPublishError(new Throwable("Transiently failing"));
    testPublisherServiceImpl.addPublishResponse(PublishResponse.newBuilder().addMessageIds("1"));

    ApiFuture<String> publishFuture1 = sendTestMessage(publisher, "A");

    assertEquals("1", publishFuture1.get());

    assertEquals(3, testPublisherServiceImpl.getCapturedRequests().size());
    publisher.shutdown();
    assertTrue(publisher.awaitTermination(1, TimeUnit.MINUTES));
  }

  @Test(expected = ExecutionException.class)
  public void testPublishFailureRetries_nonRetryableFailsImmediately() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setExecutorProvider(SINGLE_THREAD_EXECUTOR)
            .setRetrySettings(
                Publisher.Builder.DEFAULT_RETRY_SETTINGS
                    .toBuilder()
                    .setTotalTimeout(Duration.ofSeconds(10))
                    .build())
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setElementCountThreshold(1L)
                    .setDelayThreshold(Duration.ofSeconds(5))
                    .build())
            .build(); // To demonstrate that reaching duration will trigger publish

    testPublisherServiceImpl.addPublishError(new StatusException(Status.INVALID_ARGUMENT));
    ApiFuture<String> publishFuture1 = sendTestMessage(publisher, "A");

    try {
      publishFuture1.get();
    } finally {
      assertTrue(testPublisherServiceImpl.getCapturedRequests().size() >= 1);
      publisher.shutdown();
      assertTrue(publisher.awaitTermination(1, TimeUnit.MINUTES));
    }
  }

  @Test
  public void testPublisherGetters() throws Exception {
    Publisher.Builder builder = Publisher.newBuilder(TEST_TOPIC);
    builder.setChannelProvider(
        FixedTransportChannelProvider.create(GrpcTransportChannel.create(testChannel)));
    builder.setExecutorProvider(SINGLE_THREAD_EXECUTOR);
    builder.setBatchingSettings(
        BatchingSettings.newBuilder()
            .setRequestByteThreshold(10L)
            .setDelayThreshold(Duration.ofMillis(11))
            .setElementCountThreshold(12L)
            .build());
    builder.setCredentialsProvider(NoCredentialsProvider.create());
    Publisher publisher = builder.build();

    assertEquals(TEST_TOPIC, publisher.getTopicName());
    assertEquals(10, (long) publisher.getBatchingSettings().getRequestByteThreshold());
    assertEquals(Duration.ofMillis(11), publisher.getBatchingSettings().getDelayThreshold());
    assertEquals(12, (long) publisher.getBatchingSettings().getElementCountThreshold());
    publisher.shutdown();
    assertTrue(publisher.awaitTermination(1, TimeUnit.MINUTES));
  }

  @Test
  public void testBuilderParametersAndDefaults() {
    Publisher.Builder builder = Publisher.newBuilder(TEST_TOPIC);
    assertEquals(TEST_TOPIC.toString(), builder.topicName);
    assertEquals(Publisher.Builder.DEFAULT_EXECUTOR_PROVIDER, builder.executorProvider);
    assertEquals(
        Publisher.Builder.DEFAULT_REQUEST_BYTES_THRESHOLD,
        builder.batchingSettings.getRequestByteThreshold().longValue());
    assertEquals(
        Publisher.Builder.DEFAULT_DELAY_THRESHOLD, builder.batchingSettings.getDelayThreshold());
    assertEquals(
        Publisher.Builder.DEFAULT_ELEMENT_COUNT_THRESHOLD,
        builder.batchingSettings.getElementCountThreshold().longValue());
    assertEquals(Publisher.Builder.DEFAULT_RETRY_SETTINGS, builder.retrySettings);
  }

  @Test
  public void testBuilderInvalidArguments() {
    Publisher.Builder builder = Publisher.newBuilder(TEST_TOPIC);

    try {
      builder.setChannelProvider(null);
      fail("Should have thrown an IllegalArgumentException");
    } catch (NullPointerException expected) {
      // Expected
    }

    try {
      builder.setExecutorProvider(null);
      fail("Should have thrown an IllegalArgumentException");
    } catch (NullPointerException expected) {
      // Expected
    }
    try {
      builder.setBatchingSettings(
          Publisher.Builder.DEFAULT_BATCHING_SETTINGS
              .toBuilder()
              .setRequestByteThreshold(null)
              .build());
      fail("Should have thrown an NullPointerException");
    } catch (NullPointerException expected) {
      // Expected
    }
    try {
      builder.setBatchingSettings(
          Publisher.Builder.DEFAULT_BATCHING_SETTINGS
              .toBuilder()
              .setRequestByteThreshold(0L)
              .build());
      fail("Should have thrown an IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // Expected
    }
    try {
      builder.setBatchingSettings(
          Publisher.Builder.DEFAULT_BATCHING_SETTINGS
              .toBuilder()
              .setRequestByteThreshold(-1L)
              .build());
      fail("Should have thrown an IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // Expected
    }

    builder.setBatchingSettings(
        Publisher.Builder.DEFAULT_BATCHING_SETTINGS
            .toBuilder()
            .setDelayThreshold(Duration.ofMillis(1))
            .build());
    try {
      builder.setBatchingSettings(
          Publisher.Builder.DEFAULT_BATCHING_SETTINGS.toBuilder().setDelayThreshold(null).build());
      fail("Should have thrown an NullPointerException");
    } catch (NullPointerException expected) {
      // Expected
    }
    try {
      builder.setBatchingSettings(
          Publisher.Builder.DEFAULT_BATCHING_SETTINGS
              .toBuilder()
              .setDelayThreshold(Duration.ofMillis(-1))
              .build());
      fail("Should have thrown an IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // Expected
    }

    builder.setBatchingSettings(
        Publisher.Builder.DEFAULT_BATCHING_SETTINGS
            .toBuilder()
            .setElementCountThreshold(1L)
            .build());
    try {
      builder.setBatchingSettings(
          Publisher.Builder.DEFAULT_BATCHING_SETTINGS
              .toBuilder()
              .setElementCountThreshold(null)
              .build());
      fail("Should have thrown an NullPointerException");
    } catch (NullPointerException expected) {
      // Expected
    }
    try {
      builder.setBatchingSettings(
          Publisher.Builder.DEFAULT_BATCHING_SETTINGS
              .toBuilder()
              .setElementCountThreshold(0L)
              .build());
      fail("Should have thrown an IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // Expected
    }
    try {
      builder.setBatchingSettings(
          Publisher.Builder.DEFAULT_BATCHING_SETTINGS
              .toBuilder()
              .setElementCountThreshold(-1L)
              .build());
      fail("Should have thrown an IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // Expected
    }

    builder.setRetrySettings(
        Publisher.Builder.DEFAULT_RETRY_SETTINGS
            .toBuilder()
            .setInitialRpcTimeout(Publisher.Builder.MIN_RPC_TIMEOUT)
            .build());
    try {
      builder.setRetrySettings(
          Publisher.Builder.DEFAULT_RETRY_SETTINGS
              .toBuilder()
              .setInitialRpcTimeout(Publisher.Builder.MIN_RPC_TIMEOUT.minusMillis(1))
              .build());
      fail("Should have thrown an IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // Expected
    }
    builder.setRetrySettings(
        Publisher.Builder.DEFAULT_RETRY_SETTINGS
            .toBuilder()
            .setTotalTimeout(Publisher.Builder.MIN_TOTAL_TIMEOUT)
            .build());
    try {
      builder.setRetrySettings(
          Publisher.Builder.DEFAULT_RETRY_SETTINGS
              .toBuilder()
              .setTotalTimeout(Publisher.Builder.MIN_TOTAL_TIMEOUT.minusMillis(1))
              .build());
      fail("Should have thrown an IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // Expected
    }
  }

  @Test
  public void testPartialBatchingSettings() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setBatchingSettings(
                Publisher.Builder.getDefaultBatchingSettings()
                    .toBuilder()
                    .setRequestByteThreshold(5000L)
                    .build())
            .build();
    assertEquals((long) publisher.getBatchingSettings().getRequestByteThreshold(), 5000);
    assertEquals(
        publisher.getBatchingSettings().getElementCountThreshold(),
        Publisher.Builder.DEFAULT_BATCHING_SETTINGS.getElementCountThreshold());

    publisher =
        getTestPublisherBuilder()
            .setBatchingSettings(
                Publisher.Builder.getDefaultBatchingSettings()
                    .toBuilder()
                    .setElementCountThreshold(500L)
                    .build())
            .build();
    assertEquals((long) publisher.getBatchingSettings().getElementCountThreshold(), 500);
    assertEquals(
        publisher.getBatchingSettings().getRequestByteThreshold(),
        Publisher.Builder.DEFAULT_BATCHING_SETTINGS.getRequestByteThreshold());
  }

  @Test
  public void testAwaitTermination() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setExecutorProvider(SINGLE_THREAD_EXECUTOR)
            .setRetrySettings(
                Publisher.Builder.DEFAULT_RETRY_SETTINGS
                    .toBuilder()
                    .setTotalTimeout(Duration.ofSeconds(10))
                    .setMaxAttempts(0)
                    .build())
            .build();
    ApiFuture<String> publishFuture1 = sendTestMessage(publisher, "A");
    publisher.shutdown();
    assertTrue(publisher.awaitTermination(1, TimeUnit.MINUTES));
  }

  @Test
  public void testShutDown() throws Exception {
    ApiFuture apiFuture = EasyMock.mock(ApiFuture.class);
    Publisher publisher = EasyMock.mock(Publisher.class);
    EasyMock.expect(
            publisher.publish(
                PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8("A")).build()))
        .andReturn(apiFuture);
    EasyMock.expect(publisher.awaitTermination(1, TimeUnit.MINUTES)).andReturn(true);
    publisher.shutdown();
    EasyMock.expectLastCall().once();
    EasyMock.replay(publisher);
    sendTestMessage(publisher, "A");
    publisher.shutdown();
    assertTrue(publisher.awaitTermination(1, TimeUnit.MINUTES));
  }

  @Test
  public void invalidFlowControlBytes_throwException() throws Exception {
    try {
      Publisher publisher =
          getTestPublisherBuilder()
              .setBatchingSettings(
                  Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                      .toBuilder()
                      .setElementCountThreshold(1L)
                      .setDelayThreshold(Duration.ofSeconds(5))
                      .setFlowControlSettings(
                          FlowControlSettings.newBuilder()
                              .setLimitExceededBehavior(
                                  FlowController.LimitExceededBehavior.ThrowException)
                              .setMaxOutstandingElementCount(1L)
                              .setMaxOutstandingRequestBytes(0L)
                              .build())
                      .build())
              .build();
      fail("Expected an IllegalArgumentException");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  public void invalidFlowControlElementCount_throwException() throws Exception {
    try {
      Publisher publisher =
          getTestPublisherBuilder()
              .setBatchingSettings(
                  Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                      .toBuilder()
                      .setElementCountThreshold(1L)
                      .setDelayThreshold(Duration.ofSeconds(5))
                      .setFlowControlSettings(
                          FlowControlSettings.newBuilder()
                              .setLimitExceededBehavior(
                                  FlowController.LimitExceededBehavior.ThrowException)
                              .setMaxOutstandingElementCount(0L)
                              .setMaxOutstandingRequestBytes(1000L)
                              .build())
                      .build())
              .build();
      fail("Expected an IllegalArgumentException");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  public void testMessageExceedsFlowControlLimits_throwException() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setExecutorProvider(SINGLE_THREAD_EXECUTOR)
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setElementCountThreshold(1L)
                    .setDelayThreshold(Duration.ofSeconds(5))
                    .setFlowControlSettings(
                        FlowControlSettings.newBuilder()
                            .setLimitExceededBehavior(FlowController.LimitExceededBehavior.Block)
                            .setMaxOutstandingElementCount(1L)
                            .setMaxOutstandingRequestBytes(1L)
                            .build())
                    .build())
            .build();

    try {
      sendTestMessage(publisher, "AAAAAAAAAAAAAAAAAAAAA").get();
      fail("Should have thrown a FlowController.MaxOutstandingRequestBytesReachedException");
    } catch (ExecutionException e) {
      assertThat(e.getCause())
          .isInstanceOf(FlowController.MaxOutstandingRequestBytesReachedException.class);
    }
  }

  @Test
  public void testPublishFlowControl_throwException() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setExecutorProvider(SINGLE_THREAD_EXECUTOR)
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setElementCountThreshold(1L)
                    .setDelayThreshold(Duration.ofSeconds(5))
                    .setFlowControlSettings(
                        FlowControlSettings.newBuilder()
                            .setLimitExceededBehavior(
                                FlowController.LimitExceededBehavior.ThrowException)
                            .setMaxOutstandingElementCount(1L)
                            .setMaxOutstandingRequestBytes(10L)
                            .build())
                    .build())
            .build();

    // Sending a message less than the byte limit succeeds.
    ApiFuture<String> publishFuture1 = sendTestMessage(publisher, "AAAA");

    // Sending another message fails because of the outstanding message.
    ApiFuture<String> publishFuture2 = sendTestMessage(publisher, "AA");
    try {
      publishFuture2.get();
      fail("Should have thrown an FlowController.MaxOutstandingElementCountReachedException");
    } catch (ExecutionException e) {
      assertThat(e.getCause())
          .isInstanceOf(FlowController.MaxOutstandingElementCountReachedException.class);
    }

    testPublisherServiceImpl.addPublishResponse(PublishResponse.newBuilder().addMessageIds("1"));
    assertEquals("1", publishFuture1.get());

    // Sending another message succeeds.
    ApiFuture<String> publishFuture4 = sendTestMessage(publisher, "AAAA");
    testPublisherServiceImpl.addPublishResponse(PublishResponse.newBuilder().addMessageIds("2"));
    assertEquals("2", publishFuture4.get());
  }

  @Test
  public void testPublishFlowControl_throwExceptionWithOrderingKey() throws Exception {
    Publisher publisher =
        getTestPublisherBuilder()
            .setExecutorProvider(SINGLE_THREAD_EXECUTOR)
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setElementCountThreshold(1L)
                    .setDelayThreshold(Duration.ofSeconds(5))
                    .setFlowControlSettings(
                        FlowControlSettings.newBuilder()
                            .setLimitExceededBehavior(
                                FlowController.LimitExceededBehavior.ThrowException)
                            .setMaxOutstandingElementCount(1L)
                            .setMaxOutstandingRequestBytes(10L)
                            .build())
                    .build())
            .setEnableMessageOrdering(true)
            .build();

    // Sending a message less than the byte limit succeeds.
    ApiFuture<String> publishFuture1 = sendTestMessageWithOrderingKey(publisher, "AAAA", "a");

    // Sending another message fails because of the outstanding message.
    ApiFuture<String> publishFuture2 = sendTestMessageWithOrderingKey(publisher, "AA", "a");
    try {
      publishFuture2.get();
      fail("Should have thrown an FlowController.MaxOutstandingElementCountReachedException");
    } catch (ExecutionException e) {
      assertThat(e.getCause())
          .isInstanceOf(FlowController.MaxOutstandingElementCountReachedException.class);
    }

    testPublisherServiceImpl.addPublishResponse(PublishResponse.newBuilder().addMessageIds("1"));
    assertEquals("1", publishFuture1.get());

    // Sending another message for the same ordering key fails because the one before failed.
    ApiFuture<String> publishFuture3 = sendTestMessageWithOrderingKey(publisher, "AAAA", "a");
    try {
      publishFuture3.get();
      fail("This should fail.");
    } catch (ExecutionException e) {
      assertEquals(SequentialExecutorService.CallbackExecutor.CANCELLATION_EXCEPTION, e.getCause());
    }
  }

  @Test
  public void testPublishFlowControl_block() throws Exception {
    final Publisher publisher =
        getTestPublisherBuilder()
            .setExecutorProvider(SINGLE_THREAD_EXECUTOR)
            .setBatchingSettings(
                Publisher.Builder.DEFAULT_BATCHING_SETTINGS
                    .toBuilder()
                    .setElementCountThreshold(1L)
                    .setDelayThreshold(Duration.ofSeconds(5))
                    .setFlowControlSettings(
                        FlowControlSettings.newBuilder()
                            .setLimitExceededBehavior(FlowController.LimitExceededBehavior.Block)
                            .setMaxOutstandingElementCount(2L)
                            .setMaxOutstandingRequestBytes(10L)
                            .build())
                    .build())
            .build();
    Executor responseExecutor = Executors.newScheduledThreadPool(10);
    final CountDownLatch sendResponse1 = new CountDownLatch(1);
    final CountDownLatch response1Sent = new CountDownLatch(1);
    final CountDownLatch sendResponse2 = new CountDownLatch(1);
    responseExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              sendResponse1.await();
              testPublisherServiceImpl.addPublishResponse(
                  PublishResponse.newBuilder().addMessageIds("1"));
              response1Sent.countDown();
              sendResponse2.await();
              testPublisherServiceImpl.addPublishResponse(
                  PublishResponse.newBuilder().addMessageIds("2"));
            } catch (Exception e) {
            }
          }
        });

    // Sending two messages succeeds.
    ApiFuture<String> publishFuture1 = sendTestMessage(publisher, "AA");
    ApiFuture<String> publishFuture2 = sendTestMessage(publisher, "AA");

    // Sending a third message blocks because messages are outstanding.
    final CountDownLatch publish3Completed = new CountDownLatch(1);
    final CountDownLatch response3Sent = new CountDownLatch(1);
    responseExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            ApiFuture<String> publishFuture3 = sendTestMessage(publisher, "AAAAAA");
            publish3Completed.countDown();
          }
        });

    responseExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              sendResponse1.countDown();
              response1Sent.await();
              sendResponse2.countDown();
            } catch (Exception e) {
            }
          }
        });

    // Sending a fourth message blocks because although only one message has been sent,
    // the third message claimed the tokens for outstanding bytes.
    final CountDownLatch publish4Completed = new CountDownLatch(1);
    responseExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              publish3Completed.await();
              ApiFuture<String> publishFuture4 = sendTestMessage(publisher, "A");
              publish4Completed.countDown();
            } catch (Exception e) {
            }
          }
        });

    publish3Completed.await();
    testPublisherServiceImpl.addPublishResponse(PublishResponse.newBuilder().addMessageIds("3"));
    response3Sent.countDown();

    publish4Completed.await();
  }

  private Builder getTestPublisherBuilder() {
    return Publisher.newBuilder(TEST_TOPIC)
        .setExecutorProvider(FixedExecutorProvider.create(fakeExecutor))
        .setChannelProvider(
            FixedTransportChannelProvider.create(GrpcTransportChannel.create(testChannel)))
        .setCredentialsProvider(NoCredentialsProvider.create());
  }

  private void shutdownTestPublisher(Publisher publisher) throws InterruptedException {
    publisher.shutdown();
    fakeExecutor.advanceTime(Duration.ofSeconds(10));
    assertTrue(publisher.awaitTermination(1, TimeUnit.MINUTES));
  }
}
