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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.assertj.AttributesAssert;
import io.opentelemetry.sdk.testing.assertj.EventDataAssert;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.junit4.OpenTelemetryRule;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class OpenTelemetryTest {
  private static final TopicName FULL_TOPIC_NAME =
      TopicName.parse("projects/test-project/topics/test-topic");
  private static final String PROJECT_NAME = "test-project";
  private static final String ORDERING_KEY = "abc";
  private static final String MESSAGE_ID = "m0";

  private static final String PUBLISHER_SPAN_NAME = FULL_TOPIC_NAME.getTopic() + " create";
  private static final String PUBLISH_FLOW_CONTROL_SPAN_NAME = "publisher flow control";
  private static final String PUBLISH_BATCHING_SPAN_NAME = "publisher batching";
  private static final String PUBLISH_RPC_SPAN_NAME = FULL_TOPIC_NAME.getTopic() + " publish";
  private static final String PUBLISH_START_EVENT = "publish start";
  private static final String PUBLISH_END_EVENT = "publish end";

  private static final String SYSTEM_ATTR_KEY = "messaging.system";
  private static final String SYSTEM_ATTR_VALUE = "gcp_pubsub";
  private static final String DESTINATION_ATTR_KEY = "messaging.destination.name";
  private static final String CODE_FUNCTION_ATTR_KEY = "code.function";
  private static final String MESSAGE_BATCH_SIZE_ATTR_KEY = "messaging.batch.message_count";
  private static final String OPERATION_ATTR_KEY = "messaging.operation";
  private static final String PROJECT_ATTR_KEY = "gcp_pubsub.project_id";
  private static final String MESSAGE_ID_ATTR_KEY = "messaging.message.id";
  private static final String MESSAGE_SIZE_ATTR_KEY = "messaging.message.envelope.size";
  private static final String ORDERING_KEY_ATTR_KEY = "messaging.gcp_pubsub.message.ordering_key";

  private static final String TRACEPARENT_ATTRIBUTE = "googclient_traceparent";

  private static final OpenTelemetryRule openTelemetryTesting = OpenTelemetryRule.create();

  @Test
  public void testPublishSpansSuccess() {
    openTelemetryTesting.clearSpans();

    PubsubMessageWrapper messageWrapper =
        PubsubMessageWrapper.newBuilder(getPubsubMessage(), FULL_TOPIC_NAME.toString(), true)
            .build();
    List<PubsubMessageWrapper> messageWrappers = Arrays.asList(messageWrapper);

    long messageSize = messageWrapper.getPubsubMessage().getSerializedSize();
    Tracer tracer = openTelemetryTesting.getOpenTelemetry().getTracer("test");

    // Call all span start/end methods in the expected order
    messageWrapper.startPublisherSpan(tracer);
    messageWrapper.startPublishFlowControlSpan(tracer);
    messageWrapper.endPublishFlowControlSpan();
    messageWrapper.startPublishBatchingSpan(tracer);
    messageWrapper.endPublishBatchingSpan();
    Span publishRpcSpan =
        OpenTelemetryUtil.startPublishRpcSpan(tracer, FULL_TOPIC_NAME, messageWrappers, true);
    OpenTelemetryUtil.endPublishRpcSpan(publishRpcSpan, true);
    messageWrapper.setMessageIdSpanAttribute(MESSAGE_ID);
    messageWrapper.endPublisherSpan();

    List<SpanData> allSpans = openTelemetryTesting.getSpans();
    assertEquals(4, allSpans.size());
    SpanData flowControlSpanData = allSpans.get(0);
    SpanData batchingSpanData = allSpans.get(1);
    SpanData publishRpcSpanData = allSpans.get(2);
    SpanData publisherSpanData = allSpans.get(3);

    SpanDataAssert flowControlSpanDataAssert =
        OpenTelemetryAssertions.assertThat(flowControlSpanData);
    flowControlSpanDataAssert
        .hasName(PUBLISH_FLOW_CONTROL_SPAN_NAME)
        .hasParent(publisherSpanData)
        .hasEnded();

    SpanDataAssert batchingSpanDataAssert = OpenTelemetryAssertions.assertThat(batchingSpanData);
    batchingSpanDataAssert
        .hasName(PUBLISH_BATCHING_SPAN_NAME)
        .hasParent(publisherSpanData)
        .hasEnded();

    // Check span data, links, and attributes for the publish RPC span
    SpanDataAssert publishRpcSpanDataAssert =
        OpenTelemetryAssertions.assertThat(publishRpcSpanData);
    publishRpcSpanDataAssert
        .hasName(PUBLISH_RPC_SPAN_NAME)
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasEnded();

    List<LinkData> publishRpcLinks = publishRpcSpanData.getLinks();
    assertEquals(messageWrappers.size(), publishRpcLinks.size());
    assertEquals(publisherSpanData.getSpanContext(), publishRpcLinks.get(0).getSpanContext());

    assertEquals(6, publishRpcSpanData.getAttributes().size());
    AttributesAssert publishRpcSpanAttributesAssert =
        OpenTelemetryAssertions.assertThat(publishRpcSpanData.getAttributes());
    publishRpcSpanAttributesAssert
        .containsEntry(SYSTEM_ATTR_KEY, SYSTEM_ATTR_VALUE)
        .containsEntry(DESTINATION_ATTR_KEY, FULL_TOPIC_NAME.getTopic())
        .containsEntry(PROJECT_ATTR_KEY, FULL_TOPIC_NAME.getProject())
        .containsEntry(CODE_FUNCTION_ATTR_KEY, "Publisher.publishCall")
        .containsEntry(OPERATION_ATTR_KEY, "publish")
        .containsEntry(MESSAGE_BATCH_SIZE_ATTR_KEY, messageWrappers.size());

    // Check span data, events, links, and attributes for the publisher create span
    SpanDataAssert publishSpanDataAssert = OpenTelemetryAssertions.assertThat(publisherSpanData);
    publishSpanDataAssert
        .hasName(PUBLISHER_SPAN_NAME)
        .hasKind(SpanKind.PRODUCER)
        .hasNoParent()
        .hasEnded();

    assertEquals(2, publisherSpanData.getEvents().size());
    EventDataAssert startEventAssert =
        OpenTelemetryAssertions.assertThat(publisherSpanData.getEvents().get(0));
    startEventAssert.hasName(PUBLISH_START_EVENT);
    EventDataAssert endEventAssert =
        OpenTelemetryAssertions.assertThat(publisherSpanData.getEvents().get(1));
    endEventAssert.hasName(PUBLISH_END_EVENT);

    List<LinkData> publisherLinks = publisherSpanData.getLinks();
    assertEquals(1, publisherLinks.size());
    assertEquals(publishRpcSpanData.getSpanContext(), publisherLinks.get(0).getSpanContext());

    assertEquals(8, publisherSpanData.getAttributes().size());
    AttributesAssert publisherSpanAttributesAssert =
        OpenTelemetryAssertions.assertThat(publisherSpanData.getAttributes());
    publisherSpanAttributesAssert
        .containsEntry(SYSTEM_ATTR_KEY, SYSTEM_ATTR_VALUE)
        .containsEntry(DESTINATION_ATTR_KEY, FULL_TOPIC_NAME.getTopic())
        .containsEntry(PROJECT_ATTR_KEY, PROJECT_NAME)
        .containsEntry(CODE_FUNCTION_ATTR_KEY, "Publisher.publish")
        .containsEntry(OPERATION_ATTR_KEY, "create")
        .containsEntry(ORDERING_KEY_ATTR_KEY, ORDERING_KEY)
        .containsEntry(MESSAGE_SIZE_ATTR_KEY, messageSize)
        .containsEntry(MESSAGE_ID_ATTR_KEY, MESSAGE_ID);

    // Check that the message has the attribute containing the trace context.
    PubsubMessage message = messageWrapper.getPubsubMessage();
    assertEquals(1, message.getAttributesMap().size());
    assertTrue(message.containsAttributes(TRACEPARENT_ATTRIBUTE));
    assertTrue(message.getAttributesOrDefault(TRACEPARENT_ATTRIBUTE, "").contains(publisherSpanData.getTraceId()));
    assertTrue(message.getAttributesOrDefault(TRACEPARENT_ATTRIBUTE, "").contains(publisherSpanData.getSpanId()));
  }

  @Test
  public void testPublishFlowControlSpanFailure() {
    openTelemetryTesting.clearSpans();

    PubsubMessageWrapper messageWrapper =
        PubsubMessageWrapper.newBuilder(getPubsubMessage(), FULL_TOPIC_NAME.toString(), true)
            .build();

    Tracer tracer = openTelemetryTesting.getOpenTelemetry().getTracer("test");

    messageWrapper.startPublisherSpan(tracer);
    messageWrapper.startPublishFlowControlSpan(tracer);

    Exception e = new Exception("test-exception");
    messageWrapper.setPublishFlowControlSpanException(e);

    List<SpanData> allSpans = openTelemetryTesting.getSpans();
    assertEquals(2, allSpans.size());
    SpanData flowControlSpanData = allSpans.get(0);
    SpanData publisherSpanData = allSpans.get(1);

    SpanDataAssert flowControlSpanDataAssert =
        OpenTelemetryAssertions.assertThat(flowControlSpanData);
    StatusData expectedStatus =
        StatusData.create(StatusCode.ERROR, "Exception thrown during publish flow control.");
    flowControlSpanDataAssert
        .hasName(PUBLISH_FLOW_CONTROL_SPAN_NAME)
        .hasParent(publisherSpanData)
        .hasStatus(expectedStatus)
        .hasException(e)
        .hasEnded();

    SpanDataAssert publishSpanDataAssert = OpenTelemetryAssertions.assertThat(publisherSpanData);
    publishSpanDataAssert
        .hasName(PUBLISHER_SPAN_NAME)
        .hasKind(SpanKind.PRODUCER)
        .hasNoParent()
        .hasEnded();
  }

  @Test
  public void testPublishBatchingSpanFailure() {
    openTelemetryTesting.clearSpans();

    PubsubMessageWrapper messageWrapper =
        PubsubMessageWrapper.newBuilder(getPubsubMessage(), FULL_TOPIC_NAME.toString(), true)
            .build();

    Tracer tracer = openTelemetryTesting.getOpenTelemetry().getTracer("test");

    messageWrapper.startPublisherSpan(tracer);
    messageWrapper.startPublishBatchingSpan(tracer);

    Exception e = new Exception("test-exception");
    messageWrapper.setPublishBatchingSpanException(e);

    List<SpanData> allSpans = openTelemetryTesting.getSpans();
    assertEquals(2, allSpans.size());
    SpanData batchingSpanData = allSpans.get(0);
    SpanData publisherSpanData = allSpans.get(1);

    SpanDataAssert batchingSpanDataAssert = OpenTelemetryAssertions.assertThat(batchingSpanData);
    StatusData expectedStatus =
        StatusData.create(StatusCode.ERROR, "Exception thrown during publish batching.");
    batchingSpanDataAssert
        .hasName(PUBLISH_BATCHING_SPAN_NAME)
        .hasParent(publisherSpanData)
        .hasStatus(expectedStatus)
        .hasException(e)
        .hasEnded();

    SpanDataAssert publishSpanDataAssert = OpenTelemetryAssertions.assertThat(publisherSpanData);
    publishSpanDataAssert
        .hasName(PUBLISHER_SPAN_NAME)
        .hasKind(SpanKind.PRODUCER)
        .hasNoParent()
        .hasEnded();
  }

  @Test
  public void testPublishRpcSpanFailure() {
    openTelemetryTesting.clearSpans();

    PubsubMessageWrapper messageWrapper =
        PubsubMessageWrapper.newBuilder(getPubsubMessage(), FULL_TOPIC_NAME.toString(), true)
            .build();

    List<PubsubMessageWrapper> messageWrappers = Arrays.asList(messageWrapper);
    Tracer tracer = openTelemetryTesting.getOpenTelemetry().getTracer("test");

    messageWrapper.startPublisherSpan(tracer);
    Span publishRpcSpan =
        OpenTelemetryUtil.startPublishRpcSpan(tracer, FULL_TOPIC_NAME, messageWrappers, true);

    Exception e = new Exception("test-exception");
    OpenTelemetryUtil.setPublishRpcSpanException(publishRpcSpan, e, true);
    messageWrapper.endPublisherSpan();

    List<SpanData> allSpans = openTelemetryTesting.getSpans();
    assertEquals(2, allSpans.size());
    SpanData rpcSpanData = allSpans.get(0);
    SpanData publisherSpanData = allSpans.get(1);

    SpanDataAssert rpcSpanDataAssert = OpenTelemetryAssertions.assertThat(rpcSpanData);
    StatusData expectedStatus =
        StatusData.create(StatusCode.ERROR, "Exception thrown on publish RPC.");
    rpcSpanDataAssert
        .hasName(PUBLISH_RPC_SPAN_NAME)
        .hasKind(SpanKind.CLIENT)
        .hasStatus(expectedStatus)
        .hasException(e)
        .hasEnded();

    SpanDataAssert publishSpanDataAssert = OpenTelemetryAssertions.assertThat(publisherSpanData);
    publishSpanDataAssert
        .hasName(PUBLISHER_SPAN_NAME)
        .hasKind(SpanKind.PRODUCER)
        .hasNoParent()
        .hasEnded();
  }

  private PubsubMessage getPubsubMessage() {
    return PubsubMessage.newBuilder()
        .setData(ByteString.copyFromUtf8("test-data"))
        .setOrderingKey(ORDERING_KEY)
        .build();
  }
}
