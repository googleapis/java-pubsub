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
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

/**
 * A wrapper class for a {@link PubsubMessage} object that handles creation and tracking of
 * OpenTelemetry {@link Span} objects for different operations that occur during publishing.
 */
public class PubsubMessageWrapper {
  private PubsubMessage message;
  private final boolean enableOpenTelemetryTracing;

  private final TopicName topicName;
  private final SubscriptionName subscriptionName;

  // Attributes set only for messages received from a streaming pull response.
  private final String ackId;
  private final int deliveryAttempt;

  private String PUBLISHER_SPAN_NAME;
  private static final String PUBLISH_FLOW_CONTROL_SPAN_NAME = "publisher flow control";
  private static final String PUBLISH_BATCHING_SPAN_NAME = "publisher batching";
  private static final String PUBLISH_START_EVENT = "publish start";
  private static final String PUBLISH_END_EVENT = "publish end";

  private String SUBSCRIBER_SPAN_NAME;
  private static final String SUBSCRIBE_CONCURRENCY_CONTROL_SPAN_NAME =
      "subscriber concurrency control";
  private static final String SUBSCRIBE_SCHEDULER_SPAN_NAME = "subscriber scheduler";
  private String SUBSCRIBE_PROCESS_SPAN_NAME;
  private static final String MODACK_START_EVENT = "modack start";
  private static final String MODACK_END_EVENT = "modack end";
  private static final String NACK_START_EVENT = "nack start";
  private static final String NACK_END_EVENT = "nack end";
  private static final String ACK_START_EVENT = "ack start";
  private static final String ACK_END_EVENT = "ack end";

  private static final String GOOGCLIENT_PREFIX = "googclient_";

  private static final String MESSAGING_SYSTEM_VALUE = "gcp_pubsub";
  private static final String MESSAGE_SIZE_ATTR_KEY = "messaging.message.body.size";
  private static final String ORDERING_KEY_ATTR_KEY = "messaging.gcp_pubsub.message.ordering_key";
  private static final String MESSAGE_ACK_ID_ATTR_KEY = "messaging.gcp_pubsub.message.ack_id";
  private static final String MESSAGE_EXACTLY_ONCE_ATTR_KEY =
      "messaging.gcp_pubsub.message.exactly_once_delivery";
  private static final String MESSAGE_RESULT_ATTR_KEY = "messaging.gcp_pubsub.result";
  private static final String MESSAGE_DELIVERY_ATTEMPT_ATTR_KEY =
      "messaging.gcp_pubsub.message.delivery_attempt";

  private Span publisherSpan;
  private Span publishFlowControlSpan;
  private Span publishBatchingSpan;

  private Span subscriberSpan;
  private Span subscribeConcurrencyControlSpan;
  private Span subscribeSchedulerSpan;
  private Span subscribeProcessSpan;

  public PubsubMessageWrapper(Builder builder) {
    this.message = builder.message;
    this.topicName = builder.topicName;
    this.subscriptionName = builder.subscriptionName;
    this.ackId = builder.ackId;
    this.deliveryAttempt = builder.deliveryAttempt;
    this.enableOpenTelemetryTracing = builder.enableOpenTelemetryTracing;
    if (this.topicName != null) {
      this.PUBLISHER_SPAN_NAME = builder.topicName.getTopic() + " create";
    }
    if (this.subscriptionName != null) {
      this.SUBSCRIBER_SPAN_NAME = builder.subscriptionName.getSubscription() + " subscribe";
      this.SUBSCRIBE_PROCESS_SPAN_NAME = builder.subscriptionName.getSubscription() + " process";
    }
  }

  public static Builder newBuilder(
      PubsubMessage message, String topicName, boolean enableOpenTelemetryTracing) {
    return new Builder(message, topicName, enableOpenTelemetryTracing);
  }

  public static Builder newBuilder(
      PubsubMessage message,
      String subscriptionName,
      String ackId,
      int deliveryAttempt,
      boolean enableOpenTelemetryTracing) {
    return new Builder(
        message, subscriptionName, ackId, deliveryAttempt, enableOpenTelemetryTracing);
  }

  /** Returns the PubsubMessage associated with this wrapper. */
  public PubsubMessage getPubsubMessage() {
    return message;
  }

  /** Returns the parent publisher span for this message wrapper. */
  public Span getPublisherSpan() {
    return publisherSpan;
  }

  /** Returns the parent subscriber span for this message wrapper. */
  public Span getSubscriberSpan() {
    return subscriberSpan;
  }

  /** Returns the delivery attempt for the received PubsubMessage. */
  public int getDeliveryAttempt() {
    return deliveryAttempt;
  }

  /** Sets the PubsubMessage for this wrapper. */
  public void setPubsubMessage(PubsubMessage message) {
    this.message = message;
  }

  /**
   * Creates and starts the parent span with the appropriate span attributes and injects the span
   * context into the {@link PubsubMessage} attributes.
   */
  public void startPublisherSpan(Tracer tracer) {
    if (enableOpenTelemetryTracing && tracer != null) {
      AttributesBuilder attributesBuilder =
          OpenTelemetryUtil.createCommonSpanAttributesBuilder(
              topicName.getTopic(), topicName.getProject(), "Publisher.publish", "create");

      attributesBuilder.put(MESSAGE_SIZE_ATTR_KEY, message.getData().size());
      if (!message.getOrderingKey().isEmpty()) {
        attributesBuilder.put(ORDERING_KEY_ATTR_KEY, message.getOrderingKey());
      }

      publisherSpan =
          tracer
              .spanBuilder(PUBLISHER_SPAN_NAME)
              .setSpanKind(SpanKind.PRODUCER)
              .setAllAttributes(attributesBuilder.build())
              .startSpan();

      if (publisherSpan.getSpanContext().isValid()) {
        injectSpanContext();
      }
    }
  }

  /** Creates a span for publish-side flow control as a child of the parent publisher span. */
  public void startPublishFlowControlSpan(Tracer tracer) {
    if (enableOpenTelemetryTracing && tracer != null) {
      publishFlowControlSpan =
          startChildSpan(tracer, PUBLISH_FLOW_CONTROL_SPAN_NAME, publisherSpan);
    }
  }

  /** Creates a span for publish message batching as a child of the parent publisher span. */
  public void startPublishBatchingSpan(Tracer tracer) {
    if (enableOpenTelemetryTracing && tracer != null) {
      publishBatchingSpan = startChildSpan(tracer, PUBLISH_BATCHING_SPAN_NAME, publisherSpan);
    }
  }

  /**
   * Creates publish start and end events that are tied to the publish RPC span start and end times.
   */
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

  /**
   * Sets the message ID attribute in the publisher parent span. This is called after the publish
   * RPC returns with a message ID.
   */
  public void setPublisherMessageIdSpanAttribute(String messageId) {
    if (enableOpenTelemetryTracing && publisherSpan != null) {
      publisherSpan.setAttribute(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId);
    }
  }

  /** Ends the publisher parent span if it exists. */
  public void endPublisherSpan() {
    if (enableOpenTelemetryTracing && publisherSpan != null) {
      addPublishEndEvent();
      publisherSpan.end();
    }
  }

  /** Ends the publish flow control span if it exists. */
  public void endPublishFlowControlSpan() {
    if (enableOpenTelemetryTracing && publishFlowControlSpan != null) {
      publishFlowControlSpan.end();
    }
  }

  /** Ends the publish batching span if it exists. */
  public void endPublishBatchingSpan() {
    if (enableOpenTelemetryTracing && publishBatchingSpan != null) {
      publishBatchingSpan.end();
    }
  }

  /**
   * Sets an error status and records an exception when an exception is thrown during flow control.
   */
  public void setPublishFlowControlSpanException(Throwable t) {
    if (enableOpenTelemetryTracing && publishFlowControlSpan != null) {
      publishFlowControlSpan.setStatus(
          StatusCode.ERROR, "Exception thrown during publish flow control.");
      publishFlowControlSpan.recordException(t);
      endAllPublishSpans();
    }
  }

  /**
   * Sets an error status and records an exception when an exception is thrown during message
   * batching.
   */
  public void setPublishBatchingSpanException(Throwable t) {
    if (enableOpenTelemetryTracing && publishBatchingSpan != null) {
      publishBatchingSpan.setStatus(StatusCode.ERROR, "Exception thrown during publish batching.");
      publishBatchingSpan.recordException(t);
      endAllPublishSpans();
    }
  }

  /**
   * Creates the subscriber parent span using span context propagated in the message attributes and
   * sets the appropriate span attributes.
   */
  public void startSubscriberSpan(Tracer tracer, boolean exactlyOnceDeliveryEnabled) {
    if (enableOpenTelemetryTracing && tracer != null) {
      AttributesBuilder attributesBuilder =
          OpenTelemetryUtil.createCommonSpanAttributesBuilder(
              subscriptionName.getSubscription(),
              subscriptionName.getProject(),
              "StreamingSubscriberConnection.onResponse",
              null);

      attributesBuilder
          .put(SemanticAttributes.MESSAGING_MESSAGE_ID, message.getMessageId())
          .put(MESSAGE_SIZE_ATTR_KEY, message.getData().size())
          .put(MESSAGE_ACK_ID_ATTR_KEY, ackId)
          .put(MESSAGE_EXACTLY_ONCE_ATTR_KEY, exactlyOnceDeliveryEnabled);
      if (!message.getOrderingKey().isEmpty()) {
        attributesBuilder.put(ORDERING_KEY_ATTR_KEY, message.getOrderingKey());
      }
      if (deliveryAttempt > 0) {
        attributesBuilder.put(MESSAGE_DELIVERY_ATTEMPT_ATTR_KEY, deliveryAttempt);
      }
      subscriberSpan = extractSpanContext(tracer, attributesBuilder.build());
    }
  }

  /** Creates a span for subscribe concurrency control as a child of the parent subscriber span. */
  public void startSubscribeConcurrencyControlSpan(Tracer tracer) {
    if (enableOpenTelemetryTracing && tracer != null) {
      subscribeConcurrencyControlSpan =
          startChildSpan(tracer, SUBSCRIBE_CONCURRENCY_CONTROL_SPAN_NAME, subscriberSpan);
    }
  }

  /**
   * Creates a span for subscribe ordering key scheduling as a child of the parent subscriber span.
   */
  public void startSubscribeSchedulerSpan(Tracer tracer) {
    if (enableOpenTelemetryTracing && tracer != null) {
      subscribeSchedulerSpan =
          startChildSpan(tracer, SUBSCRIBE_SCHEDULER_SPAN_NAME, subscriberSpan);
    }
  }

  /** Creates a span for subscribe message processing as a child of the parent subscriber span. */
  public void startSubscribeProcessSpan(Tracer tracer) {
    if (enableOpenTelemetryTracing && tracer != null) {
      subscribeProcessSpan = startChildSpan(tracer, SUBSCRIBE_PROCESS_SPAN_NAME, subscriberSpan);
      subscribeProcessSpan.setAttribute(
          SemanticAttributes.MESSAGING_SYSTEM, MESSAGING_SYSTEM_VALUE);
      if (publisherSpan != null) {
        subscribeProcessSpan.addLink(publisherSpan.getSpanContext());
      }
    }
  }

  /**
   * Creates start and end events for ModAcks, Nacks, and Acks that are tied to the corresponding
   * RPC span start and end times.
   */
  public void addModAckStartEvent() {
    if (enableOpenTelemetryTracing && subscriberSpan != null) {
      subscriberSpan.addEvent(MODACK_START_EVENT);
    }
  }

  public void addModAckEndEvent() {
    if (enableOpenTelemetryTracing && subscriberSpan != null) {
      subscriberSpan.addEvent(MODACK_END_EVENT);
    }
  }

  public void addNackStartEvent() {
    if (enableOpenTelemetryTracing && subscriberSpan != null) {
      subscriberSpan.addEvent(NACK_START_EVENT);
    }
  }

  public void addNackEndEvent() {
    if (enableOpenTelemetryTracing && subscriberSpan != null) {
      subscriberSpan.addEvent(NACK_END_EVENT);
    }
  }

  public void addAckStartEvent() {
    if (enableOpenTelemetryTracing && subscriberSpan != null) {
      subscriberSpan.addEvent(ACK_START_EVENT);
    }
  }

  public void addAckEndEvent() {
    if (enableOpenTelemetryTracing && subscriberSpan != null) {
      subscriberSpan.addEvent(ACK_END_EVENT);
    }
  }

  public void addEndRpcEvent(boolean isModack, int ackDeadline) {
    if (!isModack) {
      addAckEndEvent();
    } else if (ackDeadline == 0) {
      addNackEndEvent();
    } else {
      addModAckEndEvent();
    }
  }

  /** Ends the subscriber parent span if exists. */
  public void endSubscriberSpan() {
    if (enableOpenTelemetryTracing && subscriberSpan != null) {
      subscriberSpan.end();
    }
  }

  /** Ends the subscribe concurreny control span if exists. */
  public void endSubscribeConcurrencyControlSpan() {
    if (enableOpenTelemetryTracing && subscribeConcurrencyControlSpan != null) {
      subscribeConcurrencyControlSpan.end();
    }
  }

  /** Ends the subscribe scheduler span if exists. */
  public void endSubscribeSchedulerSpan() {
    if (enableOpenTelemetryTracing && subscribeSchedulerSpan != null) {
      subscribeSchedulerSpan.end();
    }
  }

  /**
   * Ends the subscribe process span if it exists, creates an event with the appropriate result, and
   * sets the result on the parent subscriber span.
   */
  public void endSubscribeProcessSpan(String action) {
    if (enableOpenTelemetryTracing && subscribeProcessSpan != null) {
      subscribeProcessSpan.addEvent(action + " called");
      subscribeProcessSpan.end();
      subscriberSpan.setAttribute(MESSAGE_RESULT_ATTR_KEY, action);
    }
  }

  /** Sets an exception on the subscriber span during Ack/ModAck/Nack failures */
  public void setSubscriberSpanException(Throwable t, String exception) {
    if (enableOpenTelemetryTracing && subscriberSpan != null) {
      subscriberSpan.setStatus(StatusCode.ERROR, exception);
      subscriberSpan.recordException(t);
      endAllSubscribeSpans();
    }
  }

  /** Sets result of the parent subscriber span to expired and ends its. */
  public void setSubscriberSpanExpirationResult() {
    if (enableOpenTelemetryTracing && subscriberSpan != null) {
      subscriberSpan.setAttribute(MESSAGE_RESULT_ATTR_KEY, "expired");
      endSubscriberSpan();
    }
  }

  /**
   * Sets an error status and records an exception when an exception is thrown subscriber
   * concurrency control.
   */
  public void setSubscribeConcurrencyControlSpanException(Throwable t) {
    if (enableOpenTelemetryTracing && subscribeConcurrencyControlSpan != null) {
      subscribeConcurrencyControlSpan.setStatus(
          StatusCode.ERROR, "Exception thrown during subscribe concurrency control.");
      subscribeConcurrencyControlSpan.recordException(t);
      endAllSubscribeSpans();
    }
  }

  /** Creates a child span of the given parent span. */
  private Span startChildSpan(Tracer tracer, String name, Span parent) {
    return tracer.spanBuilder(name).setParent(Context.current().with(parent)).startSpan();
  }

  /** Ends all publisher-side spans associated with this message wrapper. */
  private void endAllPublishSpans() {
    endPublishFlowControlSpan();
    endPublishBatchingSpan();
    endPublisherSpan();
  }

  /** Ends all subscriber-side spans associated with this message wrapper. */
  private void endAllSubscribeSpans() {
    endSubscribeConcurrencyControlSpan();
    endSubscribeSchedulerSpan();
    endSubscriberSpan();
  }

  /**
   * Injects the span context into the attributes of a Pub/Sub message for propagation to the
   * subscriber client.
   */
  private void injectSpanContext() {
    TextMapSetter<PubsubMessageWrapper> injectMessageAttributes =
        new TextMapSetter<PubsubMessageWrapper>() {
          @Override
          public void set(PubsubMessageWrapper carrier, String key, String value) {
            PubsubMessage newMessage =
                PubsubMessage.newBuilder(carrier.message)
                    .putAttributes(GOOGCLIENT_PREFIX + key, value)
                    .build();
            carrier.message = newMessage;
          }
        };
    W3CTraceContextPropagator.getInstance()
        .inject(Context.current().with(publisherSpan), this, injectMessageAttributes);
  }

  /**
   * Extracts the span context from the attributes of a Pub/Sub message and creates the parent
   * subscriber span using that context.
   */
  private Span extractSpanContext(Tracer tracer, Attributes attributes) {
    TextMapGetter<PubsubMessageWrapper> extractMessageAttributes =
        new TextMapGetter<PubsubMessageWrapper>() {
          @Override
          public String get(PubsubMessageWrapper carrier, String key) {
            return carrier.message.getAttributesOrDefault(GOOGCLIENT_PREFIX + key, "");
          }

          public Iterable<String> keys(PubsubMessageWrapper carrier) {
            return carrier.message.getAttributesMap().keySet();
          }
        };
    Context context =
        W3CTraceContextPropagator.getInstance()
            .extract(Context.current(), this, extractMessageAttributes);
    publisherSpan = Span.fromContextOrNull(context);
    return tracer
        .spanBuilder(SUBSCRIBER_SPAN_NAME)
        .setSpanKind(SpanKind.CONSUMER)
        .setParent(context)
        .setAllAttributes(attributes)
        .startSpan();
  }

  /** Builder of {@link PubsubMessageWrapper PubsubMessageWrapper}. */
  protected static final class Builder {
    private PubsubMessage message = null;
    private TopicName topicName = null;
    private SubscriptionName subscriptionName = null;
    private String ackId = null;
    private int deliveryAttempt = 0;
    private boolean enableOpenTelemetryTracing = false;

    public Builder(PubsubMessage message, String topicName, boolean enableOpenTelemetryTracing) {
      this.message = message;
      if (topicName != null) {
        this.topicName = TopicName.parse(topicName);
      }
      this.enableOpenTelemetryTracing = enableOpenTelemetryTracing;
    }

    public Builder(
        PubsubMessage message,
        String subscriptionName,
        String ackId,
        int deliveryAttempt,
        boolean enableOpenTelemetryTracing) {
      this.message = message;
      if (subscriptionName != null) {
        this.subscriptionName = SubscriptionName.parse(subscriptionName);
      }
      this.ackId = ackId;
      this.deliveryAttempt = deliveryAttempt;
      this.enableOpenTelemetryTracing = enableOpenTelemetryTracing;
    }

    public Builder(
        PubsubMessage message,
        SubscriptionName subscriptionName,
        String ackId,
        int deliveryAttempt,
        boolean enableOpenTelemetryTracing) {
      this.message = message;
      this.subscriptionName = subscriptionName;
      this.ackId = ackId;
      this.deliveryAttempt = deliveryAttempt;
      this.enableOpenTelemetryTracing = enableOpenTelemetryTracing;
    }

    public PubsubMessageWrapper build() {
      Preconditions.checkArgument(
          this.enableOpenTelemetryTracing == false
              || this.topicName != null
              || this.subscriptionName != null);
      return new PubsubMessageWrapper(this);
    }
  }
}
