/*
 * Copyright 2025 Google LLC
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
// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/pubsub/v1/pubsub.proto

// Protobuf Java Version: 3.25.8
package com.google.pubsub.v1;

public interface DeadLetterPolicyOrBuilder
    extends
    // @@protoc_insertion_point(interface_extends:google.pubsub.v1.DeadLetterPolicy)
    com.google.protobuf.MessageOrBuilder {

  /**
   *
   *
   * <pre>
   * Optional. The name of the topic to which dead letter messages should be
   * published. Format is `projects/{project}/topics/{topic}`.The Pub/Sub
   * service account associated with the enclosing subscription's parent project
   * (i.e., service-{project_number}&#64;gcp-sa-pubsub.iam.gserviceaccount.com) must
   * have permission to Publish() to this topic.
   *
   * The operation will fail if the topic does not exist.
   * Users should ensure that there is a subscription attached to this topic
   * since messages published to a topic with no subscriptions are lost.
   * </pre>
   *
   * <code>string dead_letter_topic = 1 [(.google.api.field_behavior) = OPTIONAL];</code>
   *
   * @return The deadLetterTopic.
   */
  java.lang.String getDeadLetterTopic();

  /**
   *
   *
   * <pre>
   * Optional. The name of the topic to which dead letter messages should be
   * published. Format is `projects/{project}/topics/{topic}`.The Pub/Sub
   * service account associated with the enclosing subscription's parent project
   * (i.e., service-{project_number}&#64;gcp-sa-pubsub.iam.gserviceaccount.com) must
   * have permission to Publish() to this topic.
   *
   * The operation will fail if the topic does not exist.
   * Users should ensure that there is a subscription attached to this topic
   * since messages published to a topic with no subscriptions are lost.
   * </pre>
   *
   * <code>string dead_letter_topic = 1 [(.google.api.field_behavior) = OPTIONAL];</code>
   *
   * @return The bytes for deadLetterTopic.
   */
  com.google.protobuf.ByteString getDeadLetterTopicBytes();

  /**
   *
   *
   * <pre>
   * Optional. The maximum number of delivery attempts for any message. The
   * value must be between 5 and 100.
   *
   * The number of delivery attempts is defined as 1 + (the sum of number of
   * NACKs and number of times the acknowledgment deadline has been exceeded
   * for the message).
   *
   * A NACK is any call to ModifyAckDeadline with a 0 deadline. Note that
   * client libraries may automatically extend ack_deadlines.
   *
   * This field will be honored on a best effort basis.
   *
   * If this parameter is 0, a default value of 5 is used.
   * </pre>
   *
   * <code>int32 max_delivery_attempts = 2 [(.google.api.field_behavior) = OPTIONAL];</code>
   *
   * @return The maxDeliveryAttempts.
   */
  int getMaxDeliveryAttempts();
}
