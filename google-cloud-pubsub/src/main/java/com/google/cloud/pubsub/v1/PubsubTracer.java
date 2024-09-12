/*
 * Copyright 2024 Google LLC
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

import io.opentelemetry.api.trace.Span;
import java.util.List;

public interface PubsubTracer {
  default void startPublisherSpan(PubsubMessageWrapper message) {
    // noop
  }

  default void endPublisherSpan(PubsubMessageWrapper message) {
    // noop
  }

  default void setPublisherMessageIdSpanAttribute(PubsubMessageWrapper message, String messageId) {
    // noop
  }

  default void startPublishFlowControlSpan(PubsubMessageWrapper message) {
    // noop
  }

  default void endPublishFlowControlSpan(PubsubMessageWrapper message) {
    // noop
  }

  default void setPublishFlowControlSpanException(PubsubMessageWrapper message, Throwable t) {
    // noop
  }

  default void startPublishBatchingSpan(PubsubMessageWrapper message) {
    // noop
  }

  default void endPublishBatchingSpan(PubsubMessageWrapper message) {
    // noop
  }

  default Span startPublishRpcSpan(String topic, List<PubsubMessageWrapper> messages) {
    // noop
    return null;
  }

  default void endPublishRpcSpan(Span publishRpcSpan) {
    // noop
  }

  default void setPublishRpcSpanException(Span publishRpcSpan, Throwable t) {
    // noop
  }

  default void startSubscriberSpan(
      PubsubMessageWrapper message, boolean exactlyOnceDeliveryEnabled) {
    // noop
  }

  default void endSubscriberSpan(PubsubMessageWrapper message) {
    // noop
  }

  default void setSubscriberSpanExpirationResult(PubsubMessageWrapper message) {
    // noop
  }

  default void setSubscriberSpanException(
      PubsubMessageWrapper message, Throwable t, String exception) {
    // noop
  }

  default void startSubscribeConcurrencyControlSpan(PubsubMessageWrapper message) {
    // noop
  }

  default void endSubscribeConcurrencyControlSpan(PubsubMessageWrapper message) {
    // noop
  }

  default void setSubscribeConcurrencyControlSpanException(
      PubsubMessageWrapper message, Throwable t) {
    // noop
  }

  default void startSubscribeSchedulerSpan(PubsubMessageWrapper message) {
    // noop
  }

  default void endSubscribeSchedulerSpan(PubsubMessageWrapper message) {
    // noop
  }

  default void startSubscribeProcessSpan(PubsubMessageWrapper message) {
    // noop
  }

  default void endSubscribeProcessSpan(PubsubMessageWrapper message, String action) {
    // noop
  }

  default Span startSubscribeRpcSpan(
      String subscription,
      String rpcOperation,
      List<PubsubMessageWrapper> messages,
      int ackDeadline,
      boolean isReceiptModack) {
    // noop
    return null;
  }

  default void endSubscribeRpcSpan(Span rpcSpan) {
    // noop
  }

  default void setSubscribeRpcSpanException(
      Span rpcSpan, boolean isModack, int ackDeadline, Throwable t) {
    // noop
  }

  default void addEndRpcEvent(PubsubMessageWrapper message, boolean isModack, int ackDeadline) {
    // noop
  }
}
