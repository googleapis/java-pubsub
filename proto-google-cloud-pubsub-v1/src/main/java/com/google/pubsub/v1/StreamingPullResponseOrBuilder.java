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

public interface StreamingPullResponseOrBuilder
    extends
    // @@protoc_insertion_point(interface_extends:google.pubsub.v1.StreamingPullResponse)
    com.google.protobuf.MessageOrBuilder {

  /**
   *
   *
   * <pre>
   * Optional. Received Pub/Sub messages. This will not be empty.
   * </pre>
   *
   * <code>
   * repeated .google.pubsub.v1.ReceivedMessage received_messages = 1 [(.google.api.field_behavior) = OPTIONAL];
   * </code>
   */
  java.util.List<com.google.pubsub.v1.ReceivedMessage> getReceivedMessagesList();

  /**
   *
   *
   * <pre>
   * Optional. Received Pub/Sub messages. This will not be empty.
   * </pre>
   *
   * <code>
   * repeated .google.pubsub.v1.ReceivedMessage received_messages = 1 [(.google.api.field_behavior) = OPTIONAL];
   * </code>
   */
  com.google.pubsub.v1.ReceivedMessage getReceivedMessages(int index);

  /**
   *
   *
   * <pre>
   * Optional. Received Pub/Sub messages. This will not be empty.
   * </pre>
   *
   * <code>
   * repeated .google.pubsub.v1.ReceivedMessage received_messages = 1 [(.google.api.field_behavior) = OPTIONAL];
   * </code>
   */
  int getReceivedMessagesCount();

  /**
   *
   *
   * <pre>
   * Optional. Received Pub/Sub messages. This will not be empty.
   * </pre>
   *
   * <code>
   * repeated .google.pubsub.v1.ReceivedMessage received_messages = 1 [(.google.api.field_behavior) = OPTIONAL];
   * </code>
   */
  java.util.List<? extends com.google.pubsub.v1.ReceivedMessageOrBuilder>
      getReceivedMessagesOrBuilderList();

  /**
   *
   *
   * <pre>
   * Optional. Received Pub/Sub messages. This will not be empty.
   * </pre>
   *
   * <code>
   * repeated .google.pubsub.v1.ReceivedMessage received_messages = 1 [(.google.api.field_behavior) = OPTIONAL];
   * </code>
   */
  com.google.pubsub.v1.ReceivedMessageOrBuilder getReceivedMessagesOrBuilder(int index);

  /**
   *
   *
   * <pre>
   * Optional. This field will only be set if `enable_exactly_once_delivery` is
   * set to `true` and is not guaranteed to be populated.
   * </pre>
   *
   * <code>
   * .google.pubsub.v1.StreamingPullResponse.AcknowledgeConfirmation acknowledge_confirmation = 5 [(.google.api.field_behavior) = OPTIONAL];
   * </code>
   *
   * @return Whether the acknowledgeConfirmation field is set.
   */
  boolean hasAcknowledgeConfirmation();

  /**
   *
   *
   * <pre>
   * Optional. This field will only be set if `enable_exactly_once_delivery` is
   * set to `true` and is not guaranteed to be populated.
   * </pre>
   *
   * <code>
   * .google.pubsub.v1.StreamingPullResponse.AcknowledgeConfirmation acknowledge_confirmation = 5 [(.google.api.field_behavior) = OPTIONAL];
   * </code>
   *
   * @return The acknowledgeConfirmation.
   */
  com.google.pubsub.v1.StreamingPullResponse.AcknowledgeConfirmation getAcknowledgeConfirmation();

  /**
   *
   *
   * <pre>
   * Optional. This field will only be set if `enable_exactly_once_delivery` is
   * set to `true` and is not guaranteed to be populated.
   * </pre>
   *
   * <code>
   * .google.pubsub.v1.StreamingPullResponse.AcknowledgeConfirmation acknowledge_confirmation = 5 [(.google.api.field_behavior) = OPTIONAL];
   * </code>
   */
  com.google.pubsub.v1.StreamingPullResponse.AcknowledgeConfirmationOrBuilder
      getAcknowledgeConfirmationOrBuilder();

  /**
   *
   *
   * <pre>
   * Optional. This field will only be set if `enable_exactly_once_delivery` is
   * set to `true` and is not guaranteed to be populated.
   * </pre>
   *
   * <code>
   * .google.pubsub.v1.StreamingPullResponse.ModifyAckDeadlineConfirmation modify_ack_deadline_confirmation = 3 [(.google.api.field_behavior) = OPTIONAL];
   * </code>
   *
   * @return Whether the modifyAckDeadlineConfirmation field is set.
   */
  boolean hasModifyAckDeadlineConfirmation();

  /**
   *
   *
   * <pre>
   * Optional. This field will only be set if `enable_exactly_once_delivery` is
   * set to `true` and is not guaranteed to be populated.
   * </pre>
   *
   * <code>
   * .google.pubsub.v1.StreamingPullResponse.ModifyAckDeadlineConfirmation modify_ack_deadline_confirmation = 3 [(.google.api.field_behavior) = OPTIONAL];
   * </code>
   *
   * @return The modifyAckDeadlineConfirmation.
   */
  com.google.pubsub.v1.StreamingPullResponse.ModifyAckDeadlineConfirmation
      getModifyAckDeadlineConfirmation();

  /**
   *
   *
   * <pre>
   * Optional. This field will only be set if `enable_exactly_once_delivery` is
   * set to `true` and is not guaranteed to be populated.
   * </pre>
   *
   * <code>
   * .google.pubsub.v1.StreamingPullResponse.ModifyAckDeadlineConfirmation modify_ack_deadline_confirmation = 3 [(.google.api.field_behavior) = OPTIONAL];
   * </code>
   */
  com.google.pubsub.v1.StreamingPullResponse.ModifyAckDeadlineConfirmationOrBuilder
      getModifyAckDeadlineConfirmationOrBuilder();

  /**
   *
   *
   * <pre>
   * Optional. Properties associated with this subscription.
   * </pre>
   *
   * <code>
   * .google.pubsub.v1.StreamingPullResponse.SubscriptionProperties subscription_properties = 4 [(.google.api.field_behavior) = OPTIONAL];
   * </code>
   *
   * @return Whether the subscriptionProperties field is set.
   */
  boolean hasSubscriptionProperties();

  /**
   *
   *
   * <pre>
   * Optional. Properties associated with this subscription.
   * </pre>
   *
   * <code>
   * .google.pubsub.v1.StreamingPullResponse.SubscriptionProperties subscription_properties = 4 [(.google.api.field_behavior) = OPTIONAL];
   * </code>
   *
   * @return The subscriptionProperties.
   */
  com.google.pubsub.v1.StreamingPullResponse.SubscriptionProperties getSubscriptionProperties();

  /**
   *
   *
   * <pre>
   * Optional. Properties associated with this subscription.
   * </pre>
   *
   * <code>
   * .google.pubsub.v1.StreamingPullResponse.SubscriptionProperties subscription_properties = 4 [(.google.api.field_behavior) = OPTIONAL];
   * </code>
   */
  com.google.pubsub.v1.StreamingPullResponse.SubscriptionPropertiesOrBuilder
      getSubscriptionPropertiesOrBuilder();
}
