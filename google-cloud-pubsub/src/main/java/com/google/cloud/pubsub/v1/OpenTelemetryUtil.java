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

import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;

public class OpenTelemetryUtil {
  private static final String MESSAGING_SYSTEM_VALUE = "gcp_pubsub";
  private static final String PROJECT_ATTR_KEY = "gcp.project_id";
  private static final String ACK_DEADLINE_ATTR_KEY = "messaging.gcp_pubsub.message.ack_deadline";
  private static final String RECEIPT_MODACK_ATTR_KEY = "messaging.gcp_pubsub.is_receipt_modack";

  private static final String PUBLISH_RPC_SPAN_SUFFIX = " publish";

  /** Populates attributes that are common the publisher parent span and publish RPC span. */
  public static final AttributesBuilder createCommonSpanAttributesBuilder(
      String destinationName, String projectName, String codeFunction, String operation) {
    AttributesBuilder attributesBuilder =
        Attributes.builder()
            .put(SemanticAttributes.MESSAGING_SYSTEM, MESSAGING_SYSTEM_VALUE)
            .put(SemanticAttributes.MESSAGING_DESTINATION_NAME, destinationName)
            .put(PROJECT_ATTR_KEY, projectName)
            .put(SemanticAttributes.CODE_FUNCTION, codeFunction);
    if (operation != null) {
      attributesBuilder.put(SemanticAttributes.MESSAGING_OPERATION, operation);
    }

    return attributesBuilder;
  }

  /**
   * Creates, starts, and returns a publish RPC span for the given message batch. Bi-directional
   * links with the publisher parent span are created for sampled messages in the batch.
   */
  public static final Span startPublishRpcSpan(
      Tracer tracer,
      String topic,
      List<PubsubMessageWrapper> messages,
      boolean enableOpenTelemetryTracing) {
    if (enableOpenTelemetryTracing && tracer != null) {
      TopicName topicName = TopicName.parse(topic);
      Attributes attributes =
          createCommonSpanAttributesBuilder(
                  topicName.getTopic(), topicName.getProject(), "Publisher.publishCall", "publish")
              .put(SemanticAttributes.MESSAGING_BATCH_MESSAGE_COUNT, messages.size())
              .build();
      Span publishRpcSpan =
          tracer
              .spanBuilder(topicName.getTopic() + PUBLISH_RPC_SPAN_SUFFIX)
              .setSpanKind(SpanKind.CLIENT)
              .setAllAttributes(attributes)
              .startSpan();

      for (PubsubMessageWrapper message : messages) {
        if (message.getPublisherSpan().getSpanContext().isSampled()) {
          Attributes linkAttributes =
              Attributes.builder().put(SemanticAttributes.MESSAGING_OPERATION, "publish").build();
          publishRpcSpan.addLink(message.getPublisherSpan().getSpanContext(), linkAttributes);
          message.getPublisherSpan().addLink(publishRpcSpan.getSpanContext(), linkAttributes);
          message.addPublishStartEvent();
        }
      }
      return publishRpcSpan;
    }
    return null;
  }

  /** Ends the given publish RPC span if it exists. */
  public static final void endPublishRpcSpan(
      Span publishRpcSpan, boolean enableOpenTelemetryTracing) {
    if (enableOpenTelemetryTracing && publishRpcSpan != null) {
      publishRpcSpan.end();
    }
  }

  /**
   * Sets an error status and records an exception when an exception is thrown when publishing the
   * message batch.
   */
  public static final void setPublishRpcSpanException(
      Span publishRpcSpan, Throwable t, boolean enableOpenTelemetryTracing) {
    if (enableOpenTelemetryTracing && publishRpcSpan != null) {
      publishRpcSpan.setStatus(StatusCode.ERROR, "Exception thrown on publish RPC.");
      publishRpcSpan.recordException(t);
      publishRpcSpan.end();
    }
  }

  /**
   * Creates, starts, and returns spans for ModAck, Nack, and Ack RPC requests. Bi-directional links
   * to parent subscribe span for sampled messages are added.
   */
  public static final Span startSubscribeRpcSpan(
      Tracer tracer,
      String subscription,
      String rpcOperation,
      List<PubsubMessageWrapper> messages,
      int ackDeadline,
      boolean isReceiptModack,
      boolean enableOpenTelemetryTracing) {
    if (enableOpenTelemetryTracing && tracer != null) {
      String codeFunction =
          rpcOperation == "ack"
              ? "StreamingSubscriberConnection.sendAckOperations"
              : "StreamingSubscriberConnection.sendModAckOperations";
      SubscriptionName subscriptionName = SubscriptionName.parse(subscription);
      AttributesBuilder attributesBuilder =
          createCommonSpanAttributesBuilder(
                  subscriptionName.getSubscription(),
                  subscriptionName.getProject(),
                  codeFunction,
                  rpcOperation)
              .put(SemanticAttributes.MESSAGING_BATCH_MESSAGE_COUNT, messages.size());

      // Ack deadline and receipt modack are specific to the modack operation
      if (rpcOperation == "modack") {
        attributesBuilder
            .put(ACK_DEADLINE_ATTR_KEY, ackDeadline)
            .put(RECEIPT_MODACK_ATTR_KEY, isReceiptModack);
      }

      Span rpcSpan =
          tracer
              .spanBuilder(subscriptionName.getSubscription() + " " + rpcOperation)
              .setSpanKind(SpanKind.CLIENT)
              .setAllAttributes(attributesBuilder.build())
              .startSpan();

      for (PubsubMessageWrapper message : messages) {
        if (message.getSubscriberSpan().getSpanContext().isSampled()) {
          Attributes linkAttributes =
              Attributes.builder()
                  .put(SemanticAttributes.MESSAGING_OPERATION, rpcOperation)
                  .build();
          rpcSpan.addLink(message.getSubscriberSpan().getSpanContext(), linkAttributes);
          message.getSubscriberSpan().addLink(rpcSpan.getSpanContext(), linkAttributes);
          switch (rpcOperation) {
            case "ack":
              message.addAckStartEvent();
              break;
            case "modack":
              message.addModAckStartEvent();
              break;
            case "nack":
              message.addNackStartEvent();
              break;
          }
        }
      }
      return rpcSpan;
    }
    return null;
  }

  /** Ends the given subscribe RPC span if it exists. */
  public static final void endSubscribeRpcSpan(Span rpcSpan, boolean enableOpenTelemetryTracing) {
    if (enableOpenTelemetryTracing && rpcSpan != null) {
      rpcSpan.end();
    }
  }

  public static final void setSubscribeRpcSpanException(
      Span rpcSpan,
      boolean isModack,
      int ackDeadline,
      Throwable t,
      boolean enableOpenTelemetryTracing) {
    if (enableOpenTelemetryTracing && rpcSpan != null) {
      String operation = !isModack ? "ack" : (ackDeadline == 0 ? "nack" : "modack");
      rpcSpan.setStatus(StatusCode.ERROR, "Exception thrown on " + operation + " RPC.");
      rpcSpan.recordException(t);
      rpcSpan.end();
    }
  }
}
