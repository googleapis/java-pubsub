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
  private static final String PROJECT_ATTR_KEY = "gcp_pubsub.project_id";

  private static final String PUBLISH_RPC_SPAN_SUFFIX = " publish";

  /** Populates attributes that are common the publisher parent span and publish RPC span. */
  public static final AttributesBuilder createPublishSpanAttributesBuilder(
      TopicName topicName, String codeFunction, String operation) {
    AttributesBuilder attributesBuilder =
        Attributes.builder()
            .put(SemanticAttributes.MESSAGING_SYSTEM, MESSAGING_SYSTEM_VALUE)
            .put(SemanticAttributes.MESSAGING_DESTINATION_NAME, topicName.getTopic())
            .put(PROJECT_ATTR_KEY, topicName.getProject())
            .put(SemanticAttributes.CODE_FUNCTION, codeFunction)
            .put(SemanticAttributes.MESSAGING_OPERATION, operation);

    return attributesBuilder;
  }

  /**
   * Creates, starts, and returns a publish RPC span for the given message batch. Bi-directional
   * links with the publisher parent span are created for each message in the batch.
   */
  public static final Span startPublishRpcSpan(
      Tracer tracer,
      TopicName topicName,
      List<PubsubMessageWrapper> messages,
      boolean enableOpenTelemetryTracing) {
    if (enableOpenTelemetryTracing && tracer != null) {
      Attributes attributes =
          createPublishSpanAttributesBuilder(topicName, "Publisher.publishCall", "publish")
              .put(SemanticAttributes.MESSAGING_BATCH_MESSAGE_COUNT, messages.size())
              .build();
      Span publishRpcSpan =
          tracer
              .spanBuilder(topicName.getTopic() + PUBLISH_RPC_SPAN_SUFFIX)
              .setSpanKind(SpanKind.CLIENT)
              .setAllAttributes(attributes)
              .startSpan();

      for (PubsubMessageWrapper message : messages) {
        Attributes linkAttributes =
            Attributes.builder().put(SemanticAttributes.MESSAGING_OPERATION, "publish").build();
        publishRpcSpan.addLink(message.getPublisherSpan().getSpanContext(), linkAttributes);
        message.getPublisherSpan().addLink(publishRpcSpan.getSpanContext(), linkAttributes);
        message.addPublishStartEvent();
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
      endPublishRpcSpan(publishRpcSpan, enableOpenTelemetryTracing);
    }
  }
}
