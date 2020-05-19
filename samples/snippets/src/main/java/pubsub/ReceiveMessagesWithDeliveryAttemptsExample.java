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

// [START pubsub_dead_letter_delivery_attempt]

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;

public class ReceiveMessagesWithDeliveryAttemptsExample {

  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "Your Project ID";
    String subscriptionId = "Your Subscription ID";

    projectId = "tz-playground-bigdata";
    subscriptionId = "uno";
    ReceiveMessagesWithDeliveryAttemptsExample.receiveMessagesWithDeliveryAttemptsExample(
        projectId, subscriptionId);
  }

  public static void receiveMessagesWithDeliveryAttemptsExample(
      String projectId, String subscriptionId) {

    ProjectSubscriptionName subscriptionName =
        ProjectSubscriptionName.of(projectId, subscriptionId);

    // Instantiate an asynchronous message receiver
    MessageReceiver receiver =
        new MessageReceiver() {
          @Override
          public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
            // handle incoming message, then ack/nack the received message
            System.out.println("Id : " + message.getMessageId());
            System.out.println("Data : " + message.getData().toStringUtf8());
            System.out.println("Deliver Attempt : " + Subscriber.getDeliveryAttempt(message));
            consumer.ack();
          }
        };

    Subscriber subscriber = null;
    try {
      subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
      subscriber.startAsync().awaitRunning();
      System.out.printf("Listening for messages on %s:\n", subscriptionName.toString());
      // Allow the subscriber to run indefinitely unless an unrecoverable error occurs
      subscriber.awaitTerminated();
    } finally {
      // Stop receiving messages
      if (subscriber != null) {
        subscriber.stopAsync();
      }
    }
  }
}
// [END pubsub_dead_letter_delivery_attempt]
