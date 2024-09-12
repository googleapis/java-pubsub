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

import com.google.api.core.InternalApi;
import com.google.common.base.Preconditions;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;

@InternalApi("For use by the google-cloud-pubsub library only")
public class OpenTelemetryPubsubTracer implements PubsubTracer {
  private final Tracer tracer;

  private static final String PUBLISH_FLOW_CONTROL_SPAN_NAME = "publisher flow control";
  private static final String PUBLISH_BATCHING_SPAN_NAME = "publisher batching";
  private static final String SUBSCRIBE_CONCURRENCY_CONTROL_SPAN_NAME =
      "subscriber concurrency control";
  private static final String SUBSCRIBE_SCHEDULER_SPAN_NAME = "subscriber scheduler";

  private static final String MESSAGE_SIZE_ATTR_KEY = "messaging.message.body.size";
  private static final String ORDERING_KEY_ATTR_KEY = "messaging.gcp_pubsub.message.ordering_key";
  private static final String MESSAGE_ACK_ID_ATTR_KEY = "messaging.gcp_pubsub.message.ack_id";
  private static final String MESSAGE_EXACTLY_ONCE_ATTR_KEY =
      "messaging.gcp_pubsub.message.exactly_once_delivery";
  private static final String MESSAGE_DELIVERY_ATTEMPT_ATTR_KEY =
      "messaging.gcp_pubsub.message.delivery_attempt";
  private static final String ACK_DEADLINE_ATTR_KEY = "messaging.gcp_pubsub.message.ack_deadline";
  private static final String RECEIPT_MODACK_ATTR_KEY = "messaging.gcp_pubsub.is_receipt_modack";
  private static final String PUBLISH_RPC_SPAN_SUFFIX = " publish";

  private static final String MESSAGING_SYSTEM_VALUE = "gcp_pubsub";

  OpenTelemetryPubsubTracer(Tracer tracer) {
    this.tracer = Preconditions.checkNotNull(tracer, "OpenTelemetry tracer cannot be null");
  }

  private Span startChildSpan(String name, Span parent) {
    return tracer.spanBuilder(name).setParent(Context.current().with(parent)).startSpan();
  }

  /**
   * Creates and starts the parent span with the appropriate span attributes and injects the span
   * context into the {@link PubsubMessage} attributes.
   */
  @Override
  public void startPublisherSpan(PubsubMessageWrapper message) {
    AttributesBuilder attributesBuilder =
        OpenTelemetryUtil.createCommonSpanAttributesBuilder(
            message.getTopicName(), message.getTopicProject(), "publish", "create");

    attributesBuilder.put(MESSAGE_SIZE_ATTR_KEY, message.getDataSize());
    if (!message.getOrderingKey().isEmpty()) {
      attributesBuilder.put(ORDERING_KEY_ATTR_KEY, message.getOrderingKey());
    }

    Span publisherSpan =
        tracer
            .spanBuilder(message.getTopicName() + " create")
            .setSpanKind(SpanKind.PRODUCER)
            .setAllAttributes(attributesBuilder.build())
            .startSpan();

    message.setPublisherSpan(publisherSpan);
    if (publisherSpan.getSpanContext().isValid()) {
      message.injectSpanContext();
    }
  }

  public void endPublisherSpan(PubsubMessageWrapper message) {
    message.endPublisherSpan();
  }

  public void setPublisherMessageIdSpanAttribute(PubsubMessageWrapper message, String messageId) {
    message.setPublisherMessageIdSpanAttribute(messageId);
  }

  /** Creates a span for publish-side flow control as a child of the parent publisher span. */
  @Override
  public void startPublishFlowControlSpan(PubsubMessageWrapper message) {
    Span publisherSpan = message.getPublisherSpan();
    if (publisherSpan != null)
      message.setPublishFlowControlSpan(
          startChildSpan(PUBLISH_FLOW_CONTROL_SPAN_NAME, publisherSpan));
  }

  @Override
  public void endPublishFlowControlSpan(PubsubMessageWrapper message) {
    message.endPublishFlowControlSpan();
  }

  @Override
  public void setPublishFlowControlSpanException(PubsubMessageWrapper message, Throwable t) {
    message.setPublishFlowControlSpanException(t);
  }

  /** Creates a span for publish message batching as a child of the parent publisher span. */
  @Override
  public void startPublishBatchingSpan(PubsubMessageWrapper message) {
    Span publisherSpan = message.getPublisherSpan();
    if (publisherSpan != null) {
      message.setPublishBatchingSpan(startChildSpan(PUBLISH_BATCHING_SPAN_NAME, publisherSpan));
    }
  }

  @Override
  public void endPublishBatchingSpan(PubsubMessageWrapper message) {
    message.endPublishBatchingSpan();
  }

  /**
   * Creates, starts, and returns a publish RPC span for the given message batch. Bi-directional
   * links with the publisher parent span are created for sampled messages in the batch.
   */
  @Override
  public Span startPublishRpcSpan(String topic, List<PubsubMessageWrapper> messages) {
    TopicName topicName = TopicName.parse(topic);
    Attributes attributes =
        OpenTelemetryUtil.createCommonSpanAttributesBuilder(
                topicName.getTopic(), topicName.getProject(), "publishCall", "publish")
            .put(SemanticAttributes.MESSAGING_BATCH_MESSAGE_COUNT, messages.size())
            .build();
    SpanBuilder publishRpcSpanBuilder =
        tracer
            .spanBuilder(topicName.getTopic() + PUBLISH_RPC_SPAN_SUFFIX)
            .setSpanKind(SpanKind.CLIENT)
            .setAllAttributes(attributes);
    Attributes linkAttributes =
        Attributes.builder().put(SemanticAttributes.MESSAGING_OPERATION, "publish").build();
    for (PubsubMessageWrapper message : messages) {
      if (message.getPublisherSpan().getSpanContext().isSampled())
        publishRpcSpanBuilder.addLink(message.getPublisherSpan().getSpanContext(), linkAttributes);
    }
    Span publishRpcSpan = publishRpcSpanBuilder.startSpan();

    for (PubsubMessageWrapper message : messages) {
      if (message.getPublisherSpan().getSpanContext().isSampled()) {
        message.getPublisherSpan().addLink(publishRpcSpan.getSpanContext(), linkAttributes);
        message.addPublishStartEvent();
      }
    }
    return publishRpcSpan;
  }

  /** Ends the given publish RPC span if it exists. */
  @Override
  public void endPublishRpcSpan(Span publishRpcSpan) {
    if (publishRpcSpan != null) {
      publishRpcSpan.end();
    }
  }

  /**
   * Sets an error status and records an exception when an exception is thrown when publishing the
   * message batch.
   */
  @Override
  public void setPublishRpcSpanException(Span publishRpcSpan, Throwable t) {
    if (publishRpcSpan != null) {
      publishRpcSpan.setStatus(StatusCode.ERROR, "Exception thrown on publish RPC.");
      publishRpcSpan.recordException(t);
      publishRpcSpan.end();
    }
  }

  @Override
  public void startSubscriberSpan(
      PubsubMessageWrapper message, boolean exactlyOnceDeliveryEnabled) {
    AttributesBuilder attributesBuilder =
        OpenTelemetryUtil.createCommonSpanAttributesBuilder(
            message.getSubscriptionName(), message.getSubscriptionProject(), "onResponse", null);

    attributesBuilder
        .put(SemanticAttributes.MESSAGING_MESSAGE_ID, message.getMessageId())
        .put(MESSAGE_SIZE_ATTR_KEY, message.getDataSize())
        .put(MESSAGE_ACK_ID_ATTR_KEY, message.getAckId())
        .put(MESSAGE_EXACTLY_ONCE_ATTR_KEY, exactlyOnceDeliveryEnabled);
    if (!message.getOrderingKey().isEmpty()) {
      attributesBuilder.put(ORDERING_KEY_ATTR_KEY, message.getOrderingKey());
    }
    if (message.getDeliveryAttempt() > 0) {
      attributesBuilder.put(MESSAGE_DELIVERY_ATTEMPT_ATTR_KEY, message.getDeliveryAttempt());
    }
    Attributes attributes = attributesBuilder.build();
    Context publisherSpanContext = message.extractSpanContext(attributes);
    message.setPublisherSpan(Span.fromContextOrNull(publisherSpanContext));
    message.setSubscriberSpan(
        tracer
            .spanBuilder(message.getSubscriptionName() + " subscribe")
            .setSpanKind(SpanKind.CONSUMER)
            .setParent(publisherSpanContext)
            .setAllAttributes(attributes)
            .startSpan());
  }

  @Override
  public void endSubscriberSpan(PubsubMessageWrapper message) {
    message.endSubscriberSpan();
  }

  @Override
  public void setSubscriberSpanExpirationResult(PubsubMessageWrapper message) {
    message.setSubscriberSpanExpirationResult();
  }

  @Override
  public void setSubscriberSpanException(
      PubsubMessageWrapper message, Throwable t, String exception) {
    message.setSubscriberSpanException(t, exception);
  }

  /** Creates a span for subscribe concurrency control as a child of the parent subscriber span. */
  @Override
  public void startSubscribeConcurrencyControlSpan(PubsubMessageWrapper message) {
    Span subscriberSpan = message.getSubscriberSpan();
    if (subscriberSpan != null) {
      message.setSubscribeConcurrencyControlSpan(
          startChildSpan(SUBSCRIBE_CONCURRENCY_CONTROL_SPAN_NAME, subscriberSpan));
    }
  }

  @Override
  public void endSubscribeConcurrencyControlSpan(PubsubMessageWrapper message) {
    message.endSubscribeConcurrencyControlSpan();
  }

  @Override
  public void setSubscribeConcurrencyControlSpanException(
      PubsubMessageWrapper message, Throwable t) {
    message.setSubscribeConcurrencyControlSpanException(t);
  }

  /**
   * Creates a span for subscribe ordering key scheduling as a child of the parent subscriber span.
   */
  @Override
  public void startSubscribeSchedulerSpan(PubsubMessageWrapper message) {
    Span subscriberSpan = message.getSubscriberSpan();
    if (subscriberSpan != null) {
      message.setSubscribeSchedulerSpan(
          startChildSpan(SUBSCRIBE_SCHEDULER_SPAN_NAME, subscriberSpan));
    }
  }

  @Override
  public void endSubscribeSchedulerSpan(PubsubMessageWrapper message) {
    message.endSubscribeSchedulerSpan();
  }

  /** Creates a span for subscribe message processing as a child of the parent subscriber span. */
  @Override
  public void startSubscribeProcessSpan(PubsubMessageWrapper message) {
    Span subscriberSpan = message.getSubscriberSpan();
    if (subscriberSpan != null) {
      Span subscribeProcessSpan =
          startChildSpan(message.getSubscriptionName() + " process", subscriberSpan);
      subscribeProcessSpan.setAttribute(
          SemanticAttributes.MESSAGING_SYSTEM, MESSAGING_SYSTEM_VALUE);
      Span publisherSpan = message.getPublisherSpan();
      if (publisherSpan != null) {
        subscribeProcessSpan.addLink(publisherSpan.getSpanContext());
      }
      message.setSubscribeProcessSpan(subscribeProcessSpan);
    }
  }

  @Override
  public void endSubscribeProcessSpan(PubsubMessageWrapper message, String action) {
    message.endSubscribeProcessSpan(action);
  }

  /**
   * Creates, starts, and returns spans for ModAck, Nack, and Ack RPC requests. Bi-directional links
   * to parent subscribe span for sampled messages are added.
   */
  @Override
  public Span startSubscribeRpcSpan(
      String subscription,
      String rpcOperation,
      List<PubsubMessageWrapper> messages,
      int ackDeadline,
      boolean isReceiptModack) {
    String codeFunction = rpcOperation == "ack" ? "sendAckOperations" : "sendModAckOperations";
    SubscriptionName subscriptionName = SubscriptionName.parse(subscription);
    AttributesBuilder attributesBuilder =
        OpenTelemetryUtil.createCommonSpanAttributesBuilder(
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

    SpanBuilder rpcSpanBuilder =
        tracer
            .spanBuilder(subscriptionName.getSubscription() + " " + rpcOperation)
            .setSpanKind(SpanKind.CLIENT)
            .setAllAttributes(attributesBuilder.build());
    Attributes linkAttributes =
        Attributes.builder().put(SemanticAttributes.MESSAGING_OPERATION, rpcOperation).build();
    for (PubsubMessageWrapper message : messages) {
      if (message.getSubscriberSpan().getSpanContext().isSampled()) {
        rpcSpanBuilder.addLink(message.getSubscriberSpan().getSpanContext(), linkAttributes);
      }
    }
    Span rpcSpan = rpcSpanBuilder.startSpan();

    for (PubsubMessageWrapper message : messages) {
      if (message.getSubscriberSpan().getSpanContext().isSampled()) {
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

  /** Ends the given subscribe RPC span if it exists. */
  @Override
  public void endSubscribeRpcSpan(Span rpcSpan) {
    if (rpcSpan != null) {
      rpcSpan.end();
    }
  }

  /**
   * Sets an error status and records an exception when an exception is thrown when handling a
   * subscribe-side RPC.
   */
  @Override
  public void setSubscribeRpcSpanException(
      Span rpcSpan, boolean isModack, int ackDeadline, Throwable t) {
    if (rpcSpan != null) {
      String operation = !isModack ? "ack" : (ackDeadline == 0 ? "nack" : "modack");
      rpcSpan.setStatus(StatusCode.ERROR, "Exception thrown on " + operation + " RPC.");
      rpcSpan.recordException(t);
      rpcSpan.end();
    }
  }

  /** Adds the appropriate subscribe-side RPC end event. */
  @Override
  public void addEndRpcEvent(PubsubMessageWrapper message, boolean isModack, int ackDeadline) {
    if (!isModack) {
      message.addAckEndEvent();
    } else if (ackDeadline == 0) {
      message.addNackEndEvent();
    } else {
      message.addModAckEndEvent();
    }
  }
}
