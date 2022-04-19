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

// [START pubsub_create_push_subscription]

import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import java.io.IOException;

public class CreatePushSubscriptionExample {
  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "your-project-id";
    String subscriptionId = "your-subscription-id";
    String topicId = "your-topic-id";
    String pushEndpoint = "https://my-test-project.appspot.com/push";

    createPushSubscriptionExample(projectId, subscriptionId, topicId, pushEndpoint);
  }

  public static void createPushSubscriptionExample(
      String projectId, String subscriptionId, String topicId, String pushEndpoint)
      throws IOException {
    try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
      TopicName topicName = TopicName.of(projectId, topicId);
      SubscriptionName subscriptionName = SubscriptionName.of(projectId, subscriptionId);
      PushConfig pushConfig = PushConfig.newBuilder().setPushEndpoint(pushEndpoint).build();

      // Create a push subscription with default acknowledgement deadline of 10 seconds.
      // Messages not successfully acknowledged within 10 seconds will get resent by the server.
      Subscription subscription =
          subscriptionAdminClient.createSubscription(subscriptionName, topicName, pushConfig, 10);
      System.out.println("Created push subscription: " + subscription.getName());
    }
  }
}
// [END pubsub_create_push_subscription]
