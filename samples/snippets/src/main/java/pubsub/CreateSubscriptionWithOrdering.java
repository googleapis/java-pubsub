/*
 * Copyright 2020 Google LLC
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

// [START pubsub_enable_subscription_ordering]
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.Subscription;
import java.io.IOException;

public class CreateSubscriptionWithOrdering {
  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "your-project-id";
    String topicId = "your-topic-id";
    String subscriptionId = "your-subscription-id";

    createSubscriptionWithOrderingExample(projectId, topicId, subscriptionId);
  }

  public static void createSubscriptionWithOrderingExample(
      String projectId, String topicId, String subscriptionId) throws IOException {
    try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {

      ProjectTopicName topicName = ProjectTopicName.of(projectId, topicId);
      ProjectSubscriptionName subscriptionName =
          ProjectSubscriptionName.of(projectId, subscriptionId);

      Subscription subscription =
          subscriptionAdminClient.createSubscription(
              Subscription.newBuilder()
                  .setName(subscriptionName.toString())
                  .setTopic(topicName.toString())
                  // Set message ordering to true for ordered messages in the subscription.
                  .setEnableMessageOrdering(true)
                  .build());

      System.out.println("Created a subscription with ordering: " + subscription.getAllFields());
    }
  }
}
// [END pubsub_enable_subscription_ordering]
