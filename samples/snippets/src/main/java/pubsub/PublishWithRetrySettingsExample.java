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

// [START pubsub_publisher_retry_settings]

import com.google.api.core.ApiFuture;
import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.threeten.bp.Duration;

public class PublishWithRetrySettingsExample {
  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "your-project-id";
    String topicId = "your-topic-id";

    publishWithRetrySettingsExample(projectId, topicId);
  }

  public static void publishWithRetrySettingsExample(String projectId, String topicId)
      throws IOException, ExecutionException, InterruptedException {
    TopicName topicName = TopicName.of(projectId, topicId);
    Publisher publisher = null;

    try {
      // Retry settings control how the publisher handles retry-able failures
      Duration initialRetryDelay = Duration.ofMillis(100); // default: 100 ms
      double retryDelayMultiplier = 2.0; // back off for repeated failures, default: 1.3
      Duration maxRetryDelay = Duration.ofSeconds(60); // default : 60 seconds
      Duration initialRpcTimeout = Duration.ofSeconds(1); // default: 5 seconds
      double rpcTimeoutMultiplier = 1.0; // default: 1.0
      Duration maxRpcTimeout = Duration.ofSeconds(600); // default: 600 seconds
      Duration totalTimeout = Duration.ofSeconds(600); // default: 600 seconds

      RetrySettings retrySettings =
          RetrySettings.newBuilder()
              .setInitialRetryDelay(initialRetryDelay)
              .setRetryDelayMultiplier(retryDelayMultiplier)
              .setMaxRetryDelay(maxRetryDelay)
              .setInitialRpcTimeout(initialRpcTimeout)
              .setRpcTimeoutMultiplier(rpcTimeoutMultiplier)
              .setMaxRpcTimeout(maxRpcTimeout)
              .setTotalTimeout(totalTimeout)
              .build();

      // Create a publisher instance with default settings bound to the topic
      publisher = Publisher.newBuilder(topicName).setRetrySettings(retrySettings).build();

      String message = "first message";
      ByteString data = ByteString.copyFromUtf8(message);
      PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).build();

      // Once published, returns a server-assigned message id (unique within the topic)
      ApiFuture<String> messageIdFuture = publisher.publish(pubsubMessage);
      String messageId = messageIdFuture.get();
      System.out.println("Published a message with retry settings: " + messageId);

    } finally {
      if (publisher != null) {
        // When finished with the publisher, shutdown to free up resources.
        publisher.shutdown();
        publisher.awaitTermination(1, TimeUnit.MINUTES);
      }
    }
  }
}
// [END pubsub_publisher_retry_settings]
