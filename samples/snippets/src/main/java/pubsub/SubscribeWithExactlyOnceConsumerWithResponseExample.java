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
// [START pubsub_create_subscription_with_exactly_once_delivery]

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
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
        ProjectSubscriptionName subscriptionName =
                ProjectSubscriptionName.of(projectId, subscriptionId);

        // Create a subscription with exactly once delivery enabled
        try {
          CreateSubscriptionWithExactlyOnceDelivery.createSubscriptionWithExactlyOnceDeliveryExample(
                  projectId, topicId, subscriptionId);
        } catch (IOException e) {
          // Handle exception
        }

        // Instantiate an asynchronous message receiver.
        MessageReceiverWithAckResponse receiverWithResponse =
                (PubsubMessage message, AckReplyConsumerWithResponse consumerWithResponse) -> {
                    // Handle incoming message, then ack the received message, and receive the response
                    System.out.println("Id: " + message.getMessageId());
                    System.out.println("Data: " + message.getData().toStringUtf8());
                    Future<AckResponse> ackResponseFuture = consumerWithResponse.ack();

                    // Retreive the completed future
                    AckResponse ackResponse = ackResponseFuture.get();

                    // Parse the response
                    switch (ackResponse) {
                        case AckResponse.SUCCESSFUL:
                            System.out.println("Message successfully acked");
                            break;
                        case AckResponse.INVALID:
                        case AckResponse.OTHER:
                            System.out.println("Message failed to acked with response: {0}", ackResponse);
                            break;
                        default:
                            break;
                    }
                };

        Subscriber subscriber = null;
        try {
            subscriber = Subscriber.newBuilder(subscriptionName, receiverWithResponse).build();
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
// [END pubsub_create_subscription_with_exactly_once_delivery]
