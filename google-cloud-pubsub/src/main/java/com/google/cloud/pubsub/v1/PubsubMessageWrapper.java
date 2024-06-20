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

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

public class PubsubMessageWrapper {
  public PubsubMessage message;

  private final String topicName;
  private final String projectName;

  private final boolean enableOpenTelemetryTracing;

  private final String PUBLISHER_SPAN_NAME;
  private static final String PUBLISH_FLOW_CONTROL_SPAN_NAME = "publisher flow control";
  private static final String PUBLISH_BATCHING_SPAN_NAME = "publisher batching";
  private static final String PUBLISH_START_EVENT = "publish start";
  private static final String PUBLISH_END_EVENT = "publish end";

  private static final String GOOGCLIENT_PREFIX = "googclient_";

  private static final String MESSAGE_ID_ATTR_KEY = "messaging.message.id";
  private static final String MESSAGE_SIZE_ATTR_KEY = "messaging.message.envelope.size";
  private static final String ORDERING_KEY_ATTR_KEY = "messaging.gcp_pubsub.message.ordering_key";

  private Span publisherSpan;
  private Span publishFlowControlSpan;
  private Span publishBatchingSpan;

  public PubsubMessageWrapper(Builder builder) {
    this.message = builder.message;
    this.topicName = builder.topicName;
    this.projectName = builder.projectName;
    this.enableOpenTelemetryTracing = builder.enableOpenTelemetryTracing;
    this.PUBLISHER_SPAN_NAME = builder.topicName + " create";
  }

  public static Builder newBuilder(PubsubMessage message, String topicName, boolean enableOpenTelemetryTracing) {
    return new Builder(message, TopicName.parse(topicName), enableOpenTelemetryTracing);
  }

  // Getters

  public PubsubMessage getPubsubMessage() {
    return message;
  }

  public Span getPublisherSpan() {
    return publisherSpan;
  }

  // Start spans
  public void startPublisherSpan(Tracer tracer, TextMapPropagator propagator) {
    if (enableOpenTelemetryTracing && tracer != null) {

      AttributesBuilder attributesBuilder = OpenTelemetryUtil.createPublishSpanAttributesBuilder(topicName, projectName,
          "Publisher.publish", "create");
      attributesBuilder.put(MESSAGE_SIZE_ATTR_KEY, message.getSerializedSize());
      if (!message.getMessageId().isEmpty()) {
        attributesBuilder.put(MESSAGE_ID_ATTR_KEY, message.getMessageId());
      }
      if (!message.getOrderingKey().isEmpty()) {
        attributesBuilder.put(ORDERING_KEY_ATTR_KEY, message.getOrderingKey());
      }

      publisherSpan = tracer.spanBuilder(PUBLISHER_SPAN_NAME).setSpanKind(SpanKind.PRODUCER)
          .setAllAttributes(attributesBuilder.build()).startSpan();

      if (publisherSpan.getSpanContext().isValid()) {
        injectSpanContext(propagator);
      }
    }
  }

  public void startPublishFlowControlSpan(Tracer tracer) {
    if (enableOpenTelemetryTracing && tracer != null) {
      publishFlowControlSpan = startChildSpan(tracer, PUBLISH_FLOW_CONTROL_SPAN_NAME, publisherSpan);
    }
  }

  public void startPublishBatchingSpan(Tracer tracer) {
    if (enableOpenTelemetryTracing && tracer != null) {
      publishBatchingSpan = startChildSpan(tracer, PUBLISH_BATCHING_SPAN_NAME, publisherSpan);
    }
  }

  public void addPublishStartEvent() {
    if (enableOpenTelemetryTracing && publisherSpan != null) {
      publisherSpan.addEvent(PUBLISH_START_EVENT);
    }
  }

  public void addPublishEndEvent() {
    if (enableOpenTelemetryTracing && publisherSpan != null) {
      publisherSpan.addEvent(PUBLISH_END_EVENT);
    }
  }

  private void injectSpanContext(TextMapPropagator propagator) {
    if (enableOpenTelemetryTracing && publisherSpan != null) {
      TextMapSetter<PubsubMessageWrapper> injectMessageAttributes = new TextMapSetter<PubsubMessageWrapper>() {
        @Override
        public void set(PubsubMessageWrapper carrier, String key, String value) {
          PubsubMessage newMessage = PubsubMessage.newBuilder(carrier.message).putAttributes(GOOGCLIENT_PREFIX + key, value).build();
          carrier.message = newMessage;
        }
      };
      propagator.inject(Context.current().with(publisherSpan), this, injectMessageAttributes);
    }
  }

  public void setMessageIdSpanAttribute(String messageId) {
    if (enableOpenTelemetryTracing && publisherSpan != null) {
      publisherSpan.setAttribute(MESSAGE_ID_ATTR_KEY, messageId);
    }
  }

  // End spans
  public void endPublisherSpan() {
    if (enableOpenTelemetryTracing && publisherSpan != null) {
      addPublishEndEvent();
      publisherSpan.end();
    }
  }

  public void endPublishFlowControlSpan() {
    if (enableOpenTelemetryTracing && publishFlowControlSpan != null) {
      publishFlowControlSpan.end();
    }
  }

  public void endPublishBatchingSpan() {
    if (enableOpenTelemetryTracing && publishBatchingSpan != null) {
      publishBatchingSpan.end();
    }
  }

  // Exceptions
  public void setPublishFlowControlSpanException(Throwable t) {
    if (enableOpenTelemetryTracing && publishFlowControlSpan != null) {
      publishFlowControlSpan.setStatus(StatusCode.ERROR, "Exception thrown during publish flow control.");
      publishFlowControlSpan.recordException(t);
      endAllPublishSpans();
    }
  }

  public void setPublishBatchingSpanException(Throwable t) {
    if (enableOpenTelemetryTracing && publishBatchingSpan != null) {
      publishBatchingSpan.setStatus(StatusCode.ERROR, "Exception thrown during publish batching.");
      publishBatchingSpan.recordException(t);
      endAllPublishSpans();
    }
  }

  // Helpers
  private Span startChildSpan(Tracer tracer, String name, Span parent) {
    return tracer.spanBuilder(name).setParent(Context.current().with(parent)).startSpan();
  }

  private void endAllPublishSpans() {
    endPublishFlowControlSpan();
    endPublishBatchingSpan();
    endPublisherSpan();
  }

  protected static final class Builder {
    private PubsubMessage message = null;
    private String topicName = null;
    private String projectName = null;
    private boolean enableOpenTelemetryTracing = false;

    public Builder(PubsubMessage message, TopicName topicName, boolean enableOpenTelemetryTracing) {
      this.message = message;
      this.topicName = topicName.getTopic();
      this.projectName = topicName.getProject();
      this.enableOpenTelemetryTracing = enableOpenTelemetryTracing;
    }

    public PubsubMessageWrapper build() {
      Preconditions.checkArgument(this.topicName != null);
      Preconditions.checkArgument(this.projectName != null);
      return new PubsubMessageWrapper(this);
    }
  }
}
