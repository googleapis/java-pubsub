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

// [START pubsub_quickstart_subscriber]
// [START pubsub_subscriber_async_pull]

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.api.gax.core.ExecutorProvider;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.lang.Thread;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import org.threeten.bp.Duration;
import com.google.api.gax.rpc.TransportChannel;
import com.google.auth.Credentials;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.Map;

public class SubscribeAsyncExample {
  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "ordering-keys-testing";
    String subscriptionId = "threads-test";

    subscribeAsyncExample(projectId, subscriptionId);
  }

  private static class SinglePubSubChannelProvider implements TransportChannelProvider {
    TransportChannel channel;
    TransportChannelProvider channelProvider;

    SinglePubSubChannelProvider(ExecutorProvider executorProvider) {
  int MAX_INBOUND_MESSAGE_SIZE =
      20 * 1024 * 1024; // 20MB API maximum message size.
      int MAX_INBOUND_METADATA_SIZE =
      4 * 1024 * 1024; // 4MB API maximum metadata size
      channelProvider =
        SubscriptionAdminSettings.defaultGrpcTransportProviderBuilder()
            .setMaxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
            .setMaxInboundMetadataSize(MAX_INBOUND_METADATA_SIZE)
            .setKeepAliveTime(Duration.ofMinutes(5))
            .setExecutor(executorProvider.getExecutor()).build();
    }

    @Override
    public TransportChannel getTransportChannel() {
      if (channel == null) {
        try {
          channel = channelProvider.getTransportChannel();
        } catch (Exception e) {
        }
      }
      return channel;
    }

    @Override
    public boolean acceptsPoolSize() {
      return channelProvider.acceptsPoolSize();
    }

    @Override
    public String getTransportName() {
      return channelProvider.getTransportName();
    }

    @Override
    public boolean needsCredentials() {
      return channelProvider.needsCredentials();
    }

    @Override
    public boolean needsEndpoint() {
      return channelProvider.needsEndpoint();
    }

    @Override
    public boolean needsExecutor() {
      return channelProvider.needsExecutor();
    }

    @Override
    public boolean needsHeaders() {
      return channelProvider.needsHeaders();
    }

    @Override
    public boolean shouldAutoClose() {
      return channelProvider.shouldAutoClose();
    }

    @Override
    public TransportChannelProvider withCredentials(Credentials credentials) {
      channelProvider = channelProvider.withCredentials(credentials);
      return this;
    }

    @Override
    public TransportChannelProvider withEndpoint(String endpoint) {
      channelProvider = channelProvider.withEndpoint(endpoint);
      return this;
    }

    @Override
    public TransportChannelProvider withExecutor(Executor executor) {
      channelProvider = channelProvider.withExecutor(executor);
      return this;
    }

    @Override
    public TransportChannelProvider withExecutor(ScheduledExecutorService executor) {
      channelProvider = channelProvider.withExecutor(executor);
      return this;
    }

    @Override
    public TransportChannelProvider withHeaders(Map<String,String> headers) {
      channelProvider = channelProvider.withHeaders(headers);
      return this;
    }

    @Override
    public TransportChannelProvider withPoolSize(int size) {
      channelProvider = channelProvider.withPoolSize(size);
      return this;
    }
  }

  public static void subscribeAsyncExample(String projectId, String subscriptionId) {
    int subCount = 100;

    // Instantiate an asynchronous message receiver.
    MessageReceiver receiver =
        (PubsubMessage message, AckReplyConsumer consumer) -> {
          // Handle incoming message, then ack the received message.
          System.out.println("Id: " + message.getMessageId());
          System.out.println("Data: " + message.getData().toStringUtf8());
          consumer.ack();
        };

    FixedExecutorProvider executorProvider =
          FixedExecutorProvider.create(new ScheduledThreadPoolExecutor(1));
    SinglePubSubChannelProvider channelProvider = new SinglePubSubChannelProvider(executorProvider);

    List<Subscriber> subscribers = new ArrayList<>();
    for (int i = 0; i < subCount; ++i) {
      ProjectSubscriptionName subscriptionName =
        ProjectSubscriptionName.of(projectId, subscriptionId + i);
      Subscriber subscriber = null;
        subscriber = Subscriber.newBuilder(subscriptionName, receiver).setChannelProvider(channelProvider).setExecutorProvider(executorProvider).setSystemExecutorProvider(executorProvider).build();
        // Start the subscriber.
        subscriber.startAsync().awaitRunning();
      subscribers.add(subscriber);
    }
    System.out.printf("Thread count: %d\n", Thread.activeCount());
    System.out.println("Listening for messages.");
    for (Subscriber subscriber : subscribers) {
      try {
        // Allow the subscriber to run for 30s unless an unrecoverable error occurs.
        subscriber.awaitTerminated(300, TimeUnit.SECONDS);
      } catch (TimeoutException timeoutException) {
      // Shut down the subscriber after 30s. Stop receiving messages.
        subscriber.stopAsync();
      }
    }
  }
}
// [END pubsub_subscriber_async_pull]
// [END pubsub_quickstart_subscriber]
