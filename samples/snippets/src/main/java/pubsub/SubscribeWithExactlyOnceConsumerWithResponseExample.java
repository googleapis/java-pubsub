/*
 * Copyright 2022 Google LLC
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

// [START pubsub_subscriber_exactly_once]

import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SubscribeWithExactlyOnceConsumerWithResponseExample {
  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "your-project-id";
    String topicId = "your-topic-id";
    String subscriptionId = "your-subscription-id";

    subscribeWithExactlyOnceConsumerWithResponseExample(projectId, topicId, subscriptionId);
  }

  public static void subscribeWithExactlyOnceConsumerWithResponseExample(
      String projectId, String topicId, String subscriptionId) {
    // For subscriptions with exactly once enabled, the AckResponse will:
    // return success OR permanent failures
    // For subscriptions without exactly once enabled the AckResponse will:
    // return success for messages ack/nack'd OR permanent failures
    ProjectSubscriptionName subscriptionName =
        ProjectSubscriptionName.of(projectId, subscriptionId);

    // Instantiate an asynchronous message receiver.
    MessageReceiverWithAckResponse receiverWithResponse =
        (PubsubMessage message, AckReplyConsumerWithResponse consumerWithResponse) -> {
          // Handle incoming message, then ack the received message, and receive the response
          Future<AckResponse> ackResponseFuture = consumerWithResponse.ack();

          try {
            // Retrieve the completed future
            AckResponse ackResponse = ackResponseFuture.get();

            switch (ackResponse) {
              case SUCCESSFUL:
                System.out.println("Successful MessageId: " + message.getMessageId());
                break;
              case INVALID:
                System.out.println("Invalid MessageId: " + message.getMessageId());
                break;
              case PERMISSION_DENIED:
                System.out.println("Permission denied. MessageId: " + message.getMessageId());
              case FAILED_PRECONDITION:
                System.out.println("Failed precondition. MessageId: " + message.getMessageId());
              case OTHER:
                System.out.println("Unknown error. MessageId: " + message.getMessageId());
              default:
                break;
            }
          } catch (InterruptedException | ExecutionException e) {
            // Something went wrong retrieving the future
            System.out.println("Failed to retrieve future for Id: " + message.getMessageId());
          }
        };

    Subscriber subscriber = null;
    try {
      subscriber =
          Subscriber.newBuilder(subscriptionName, receiverWithResponse)
              .setMinDurationPerAckExtension(Duration.ofSeconds(60))
              .build();
      // Start the subscriber.
      subscriber.startAsync().awaitRunning();
      System.out.printf("Listening for messages on %s:\n", subscriptionName.toString());
      // Allow the subscriber to run for 30s unless an unrecoverable error occurs.
      subscriber.awaitTerminated(30, TimeUnit.SECONDS);
    } catch (TimeoutException timeoutException) {
      // Shut down the subscriber after 30s. Stop receiving messages.
      subscriber.stopAsync();
    }
  }
}
// [END pubsub_subscriber_exactly_once]
