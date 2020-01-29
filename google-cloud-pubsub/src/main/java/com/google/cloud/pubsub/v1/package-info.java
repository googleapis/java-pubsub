/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * A client to Google Cloud Pub/Sub API.
 *
 * <p>The interfaces provided are listed below, along with usage samples.
 *
 * <p>================ TopicAdminClient ================
 *
 * <p>Service Description: The service that an application uses to manipulate topics, and to send
 * messages to a topic.
 *
 * <p>To publish messages to a topic, see the Publisher class.
 *
 * <p>Sample for TopicAdminClient:
 *
 * <pre>
 * <code>
 * try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
 *   ProjectTopicName name = ProjectTopicName.of("[PROJECT]", "[TOPIC]");
 *   Topic response = topicAdminClient.createTopic(name);
 * }
 * </code>
 * </pre>
 *
 * ======================= SubscriptionAdminClient =======================
 *
 * <p>Service Description: The service that an application uses to manipulate subscriptions and to
 * consume messages from a subscription via the `Pull` method or by establishing a bi-directional
 * stream using the `StreamingPull` method.
 *
 * <p>To retrieve messages from a subscription, see the Subscriber class.
 *
 * <p>Sample for SubscriptionAdminClient:
 *
 * <pre>
 * <code>
 * try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
 *   ProjectSubscriptionName name = ProjectSubscriptionName.of("[PROJECT]", "[SUBSCRIPTION]");
 *   ProjectTopicName topic = ProjectTopicName.of("[PROJECT]", "[TOPIC]");
 *   PushConfig pushConfig = PushConfig.newBuilder().build();
 *   int ackDeadlineSeconds = 0;
 *   Subscription response = subscriptionAdminClient.createSubscription(name, topic, pushConfig, ackDeadlineSeconds);
 * }
 * </code>
 * </pre>
 */
@Generated("by gapic-generator")
package com.google.cloud.pubsub.v1;

import javax.annotation.Generated;
