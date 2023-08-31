/*
 * Copyright 2016 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pubsub;

import com.google.api.gax.core.FixedExecutorProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class SubscribeAsyncLimitedConcurrencyExample {
  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "my-project";
    String subscriptionId = "my-subscription";

    subscribeAsyncLimitedConcurrencyExample(projectId, subscriptionId);
  }

  static final int MAX_INBOUND_MESSAGE_SIZE = 20 * 1024 * 1024; // 20MB API maximum message size.
  static final int MAX_INBOUND_METADATA_SIZE = 4 * 1024 * 1024; // 4MB API maximum metadata size

  private static ManagedChannel createSingleChannel(
      String serviceAddress, int port, Executor executor, Executor offloadExecutor)
      throws IOException {
    ManagedChannelBuilder<?> builder;
    builder = ManagedChannelBuilder.forAddress(serviceAddress, port);
    builder =
        builder
            .executor(executor)
            .offloadExecutor(offloadExecutor)
            .maxInboundMetadataSize(MAX_INBOUND_METADATA_SIZE)
            .keepAliveTime(30, TimeUnit.SECONDS);

    ManagedChannel managedChannel = builder.build();
    return managedChannel;
  }

  public static void subscribeAsyncLimitedConcurrencyExample(
      String projectId, String subscriptionId) {
    int subCount = 100;
    int transportChannelCount = 20;
    final AtomicLong receivedCount = new AtomicLong();

    // Instantiate an asynchronous message receiver.
    MessageReceiver receiver =
        (PubsubMessage message, AckReplyConsumer consumer) -> {
          // Handle incoming message, then ack the received message.
          consumer.ack();
          long currentCount = receivedCount.incrementAndGet();
          if (currentCount % 100 == 0) {
            System.out.println("Received " + currentCount);
          }
        };

    ThreadFactory callbackThreadFactory =
        new ThreadFactoryBuilder().setNameFormat("callback-pool-%d").build();
    ScheduledThreadPoolExecutor callbackExecutor =
        new ScheduledThreadPoolExecutor(10, callbackThreadFactory);
    callbackExecutor.setMaximumPoolSize(10);
    FixedExecutorProvider callbackExecutorProvider = FixedExecutorProvider.create(callbackExecutor);
    ThreadFactory leaseThreadFactory =
        new ThreadFactoryBuilder().setNameFormat("lease-pool-%d").build();
    ScheduledThreadPoolExecutor leaseExecutor =
        new ScheduledThreadPoolExecutor(10, leaseThreadFactory);
    leaseExecutor.setMaximumPoolSize(10);
    FixedExecutorProvider leaseExecutorProvider = FixedExecutorProvider.create(leaseExecutor);
    ThreadFactory channelThreadFactory =
        new ThreadFactoryBuilder().setNameFormat("channel-pool-%d").build();
    ScheduledThreadPoolExecutor channelExecutor =
        new ScheduledThreadPoolExecutor(10, channelThreadFactory);
    ThreadFactory channelOffloadThreadFactory =
        new ThreadFactoryBuilder().setNameFormat("channel-offload-pool-%d").build();
    ScheduledThreadPoolExecutor channelOffloadExecutor =
        new ScheduledThreadPoolExecutor(10, channelOffloadThreadFactory);

    ArrayList<TransportChannelProvider> transportChannelProviders =
        new ArrayList<>(transportChannelCount);

    for (int i = 0; i < transportChannelCount; ++i) {
      TransportChannelProvider channelProvider = null;
      try {
        channelProvider =
            FixedTransportChannelProvider.create(
                GrpcTransportChannel.create(
                    createSingleChannel(
                        "pubsub.googleapis.com", 443, channelExecutor, channelOffloadExecutor)));
        transportChannelProviders.add(channelProvider);
      } catch (Exception e) {
        System.out.println("Could not create channel provider: " + e);
        return;
      }
    }

    List<Subscriber> subscribers = new ArrayList<>();
    for (int i = 0; i < subCount; ++i) {

      ProjectSubscriptionName subscriptionName =
          ProjectSubscriptionName.of(projectId, subscriptionId + i);
      Subscriber subscriber = null;
      subscriber =
          Subscriber.newBuilder(subscriptionName, receiver)
              .setChannelProvider(transportChannelProviders.get(i % transportChannelCount))
              .setExecutorProvider(callbackExecutorProvider)
              .setSystemExecutorProvider(leaseExecutorProvider)
              .build();
      // Start the subscriber.
      subscriber.startAsync().awaitRunning();
      subscribers.add(subscriber);
    }
    printThreads();
    System.out.println("Listening for messages for 30s before checking threads again.");
    try {
      Thread.sleep(30000);
    } catch (Exception e) {

    }
    printThreads();

    for (Subscriber subscriber : subscribers) {
      try {
        // Allow the subscriber to run for 30s unless an unrecoverable error occurs.
        subscriber.awaitTerminated(120, TimeUnit.SECONDS);
      } catch (TimeoutException timeoutException) {
        // Shut down the subscriber after 30s. Stop receiving messages.
        subscriber.stopAsync();
      }
    }
  }

  private static void printThreads() {
    System.out.println("Thread names:");
    Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
    for (Thread t : threadSet) {
      System.out.println("\t" + t.getName());
    }
    System.out.printf("Thread count: %d\n", Thread.activeCount());
  }
}
