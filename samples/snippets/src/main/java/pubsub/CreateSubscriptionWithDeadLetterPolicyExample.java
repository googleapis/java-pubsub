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

// [START pubsub_dead_letter_create_subscription]

public class CreateSubscriptionWithDeadLetterPolicyExample {

  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String PROJECT_ID = "Your Project ID";
    String TOPIC_NAME = "Your Topic Name";
    String SUBSCRIPTION_NAME = "Your Subscription Name";
    String DEAD_LETTER_TOPIC = "Your Dead Letter Topic";

    CreateSubscriptionWithDeadLetterPolicyExample
        .createSubscriptionWithDeadLetterPolicyExample(PROJECT_ID,
            SUBSCRIPTION_NAME, TOPIC_NAME, DEAD_LETTER_TOPIC);
  }

  public static void createSubscriptionWithDeadLetterPolicyExample(
      String PROJECT_ID, String SUBSCRIPTION_NAME, String TOPIC_NAME,
      String DEAD_LETTER_TOPIC_NAME) {

  }
}

// [END pubsub_dead_letter_create_subscription]
