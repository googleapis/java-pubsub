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

// [START pubsub_dead_letter_update_subscription]

import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.protobuf.FieldMask;
import com.google.pubsub.v1.DeadLetterPolicy;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.TopicName;
import com.google.pubsub.v1.UpdateSubscriptionRequest;
import java.io.IOException;

public class UpdateDeadLetterPolicyExample {
  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "Your Project ID";
    String subscriptionId = "Your Subscription ID";
    String topicId = "Your Topic ID";
    String deadLetterTopicId = "Your Dead Letter Topic ID";

    projectId = "tz-playground-bigdata";
    subscriptionId = "une";
    topicId = "jan";
    deadLetterTopicId = "jan-dlq";

    UpdateDeadLetterPolicyExample.updateDeadLetterPolicyExample(
        projectId, subscriptionId, topicId, deadLetterTopicId);
  }

  public static void updateDeadLetterPolicyExample(
      String projectId, String subscriptionId, String topicId, String deadLetterTopicId)
      throws IOException {
    try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {

      ProjectSubscriptionName subscriptionName =
          ProjectSubscriptionName.of(projectId, subscriptionId);

      System.out.println(
          "Before: " + subscriptionAdminClient.getSubscription(subscriptionName).getAllFields());

      TopicName topicName = TopicName.of(projectId, topicId);
      TopicName deadLetterTopicName = TopicName.of(projectId, deadLetterTopicId);

       DeadLetterPolicy deadLetterPolicy = DeadLetterPolicy.newBuilder()
          .setDeadLetterTopic(deadLetterTopicName.toString())
          .setMaxDeliveryAttempts(20)
          .build();

      Subscription subscription =
          Subscription.newBuilder()
              .setName(subscriptionName.toString())
              .setTopic(topicName.toString())
              .setDeadLetterPolicy(deadLetterPolicy)
              .build();

      FieldMask updateMask =
          FieldMask.newBuilder().addPaths("dead_letter_policy.max_delivery_attempts").build();

      UpdateSubscriptionRequest request =
          UpdateSubscriptionRequest.newBuilder()
              .setSubscription(subscription)
              .setUpdateMask(updateMask)
              .build();

      Subscription response = subscriptionAdminClient.updateSubscription(request);

      System.out.println("After: " + response.getAllFields());
    }
  }
}
// [END pubsub_dead_letter_update_subscription]
