/*
 * Copyright 2016 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.pubsub.v1;

import com.google.pubsub.v1.PubsubMessage;

final class LoggingUtil {
  private LoggingUtil() {}

  static String getLogPrefix(
      PubsubMessageWrapper messageWrapper, String ackId, boolean exactlyOnceDeliveryEnabled) {
    if (messageWrapper == null || messageWrapper.getPubsubMessage() == null) {
      return " Ack ID: "
          + ackId
          + ", Exactly Once Delivery: "
          + exactlyOnceDeliveryEnabled
          + " (Message details not available)";
    }

    PubsubMessage message = messageWrapper.getPubsubMessage();
    String messageId = message.getMessageId();
    String orderingKey = message.getOrderingKey();

    StringBuilder sb = new StringBuilder();
    sb.append("Message ID: ").append(messageId);
    sb.append(", Ack ID: ").append(ackId);
    if (orderingKey != null && !orderingKey.isEmpty()) {
      sb.append(", Ordering Key: ").append(orderingKey);
    }
    sb.append(", Exactly Once Delivery: ").append(exactlyOnceDeliveryEnabled);
    return sb.toString();
  }

  static String getPublisherLogPrefix(PubsubMessageWrapper messageWrapper) {
    if (messageWrapper == null || messageWrapper.getPubsubMessage() == null) {
      return " (Message details not available)";
    }

    PubsubMessage message = messageWrapper.getPubsubMessage();
    String messageId = message.getMessageId();
    String orderingKey = message.getOrderingKey();

    StringBuilder sb = new StringBuilder();
    sb.append("Message ID: ").append(messageId);
    if (orderingKey != null && !orderingKey.isEmpty()) {
      sb.append(", Ordering Key: ").append(orderingKey);
    }
    return sb.toString();
  }
}
