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

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class SubscribeAsyncUnlimitedConcurrencyExample {
  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "my-topic";
    String subscriptionId = "my-subscription";

    subscribeAsyncUnlimitedConcurrencyExample(projectId, subscriptionId);
  }

  public static void subscribeAsyncUnlimitedConcurrencyExample(
      String projectId, String subscriptionId) {
    int subCount = 100;
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

    List<Subscriber> subscribers = new ArrayList<>();
    for (int i = 0; i < subCount; ++i) {
      ProjectSubscriptionName subscriptionName =
          ProjectSubscriptionName.of(projectId, subscriptionId + i);
      Subscriber subscriber = null;
      subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
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
        subscriber.awaitTerminated(300, TimeUnit.SECONDS);
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
