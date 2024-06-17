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

import com.google.common.base.Preconditions;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

public class PubsubMessageWrapper {
  private final PubsubMessage message;

  private final String topicName;
  private final String projectName;

  private final String PUBLISHER_SPAN_NAME;
  private final String PUBLISH_FLOW_CONTROL_SPAN_NAME = "publisher flow control";
  private final String PUBLISH_BATCHING_SPAN_NAME = "publisher batching";

  private static final String SYSTEM_ATTR_KEY = "messaging.system";
  private static final String SYSTEM_ATTR_VALUE = "gcp_pubsub";
  private static final String DESTINATION_ATTR_KEY = "messaging.destination.name";
  private static final String CODE_FUNCTION_ATTR_KEY = "code.function";
  private static final String MESSAGE_ID_ATTR_KEY = "messaging.message.id";
  private static final String MESSAGE_SIZE_ATTR_KEY = "messaging.message.envelope.size";
  private static final String ORDERING_KEY_ATTR_KEY = "messaging.gcp_pubsub.message.ordering_key";
  private static final String OPERATION_ATTR_KEY = "messaging.operation";
  private static final String PROJECT_ATTR_KEY = "gcp_pubsub.project_id";

  private Span publisherSpan;
  private Span publishFlowControlSpan;
  private Span publishBatchingSpan;

  public PubsubMessageWrapper(Builder builder) {
    this.message = builder.message;
    this.topicName = builder.topicName;
    this.projectName = builder.projectName;
    this.PUBLISHER_SPAN_NAME = builder.topicName + " create";
  }

  public static Builder newBuilder(PubsubMessage message, String topicName) {
    return new Builder(message, TopicName.parse(topicName));
  }

  public PubsubMessage getPubsubMessage() {
    return message;
  }

  // Start spans
  public void startPublisherSpan(Tracer tracer) {
    AttributesBuilder attributesBuilder = OpenTelemetryUtil.createPublishSpanAttributesBuilder(topicName, projectName, "Publisher.publish", "create");
    attributesBuilder.put(MESSAGE_SIZE_ATTR_KEY, message.getSerializedSize());
    if (!message.getMessageId().isEmpty()) {
      attributesBuilder.put(MESSAGE_ID_ATTR_KEY, message.getMessageId());
    }
    if (!message.getOrderingKey().isEmpty()) {
      attributesBuilder.put(ORDERING_KEY_ATTR_KEY, message.getOrderingKey());
    }

    publisherSpan = tracer.spanBuilder(PUBLISHER_SPAN_NAME).setSpanKind(SpanKind.PRODUCER)
        .setAllAttributes(attributesBuilder.build()).startSpan();

    try {
      publisherSpan.makeCurrent();
    } catch (Throwable t) {
      publisherSpan.setStatus(StatusCode.ERROR, "Unable to set the Publisher span as the current span.");
      publisherSpan.recordException(t);
    } finally {
      publisherSpan.end();
    }
  }

  public void startPublishFlowControlSpan(Tracer tracer) {
    publishFlowControlSpan = startChildSpan(tracer, PUBLISH_FLOW_CONTROL_SPAN_NAME, publisherSpan);
  }

  public void startPublishBatchingSpan(Tracer tracer) {
    publishBatchingSpan = startChildSpan(tracer, PUBLISH_BATCHING_SPAN_NAME, publishFlowControlSpan);
  }

  // End spans
  public void endPublisherSpan() {
    if (publisherSpan != null) {
      publisherSpan.end();
    }
  }

  public void endPublishFlowControlSpan() {
    if (publishFlowControlSpan != null) {
      publishFlowControlSpan.end();
    }
  }

  public void endPublishBatchingSpan() {
    if (publishBatchingSpan != null) {
      publishBatchingSpan.end();
    }
  }

  // Exceptions
  public void setPublishFlowControlSpanException(Throwable t) {
    if (publishFlowControlSpan != null) {
      publishFlowControlSpan.setStatus(StatusCode.ERROR, "Exception thrown during publish flow control.");
      publishFlowControlSpan.recordException(t);
      endAllPublishSpans();
    }
  }

  public void setPublishBatchingSpanException(Throwable t) {
    if (publishBatchingSpan != null) {
      publishBatchingSpan.setStatus(StatusCode.ERROR, "Exception thrown during publish batching.");
      publishBatchingSpan.recordException(t);
      endAllPublishSpans();
    }
  }

  // Helpers
  public Span startChildSpan(Tracer tracer, String name, Span parent) {
    return tracer.spanBuilder(name).setParent(Context.current().with(parent)).startSpan();
  }

  private void endAllPublishSpans() {
    endPublishFlowControlSpan();
    endPublisherSpan();
  }

  protected static final class Builder {
    private PubsubMessage message = null;
    private String topicName = null;
    private String projectName = null;

    public Builder(PubsubMessage message, TopicName topicName) {
      this.message = message;
      this.topicName = topicName.getTopic();
      this.projectName = topicName.getProject();
    }

    public PubsubMessageWrapper build() {
      Preconditions.checkArgument(this.topicName != null);
      Preconditions.checkArgument(this.projectName != null);
      return new PubsubMessageWrapper(this);
    }
  }
}
