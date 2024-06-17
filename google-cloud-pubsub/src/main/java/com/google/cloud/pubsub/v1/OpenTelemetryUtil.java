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
import java.util.List;

public class OpenTelemetryUtil {
  private static final String PUBLISH_RPC_SPAN_SUFFIX = " publish";

  private static final String SYSTEM_ATTR_KEY = "messaging.system";
  private static final String SYSTEM_ATTR_VALUE = "gcp_pubsub";
  private static final String DESTINATION_ATTR_KEY = "messaging.destination.name";
  private static final String CODE_FUNCTION_ATTR_KEY = "code.function";
  private static final String MESSAGE_ID_ATTR_KEY = "messaging.message.id";
  private static final String MESSAGE_SIZE_ATTR_KEY = "messaging.message.envelope.size";
  private static final String ORDERING_KEY_ATTR_KEY = "messaging.gcp_pubsub.message.ordering_key";
  private static final String MESSAGE_BATCH_SIZE_ATTR_KEY = "messaging.batch.message_count";
  private static final String OPERATION_ATTR_KEY = "messaging.operation";
  private static final String PROJECT_ATTR_KEY = "gcp_pubsub.project_id";

  public static final AttributesBuilder createPublishSpanAttributesBuilder(
      String topicName, String projectName, String codeFunction, String operation) {
    AttributesBuilder attributesBuilder = Attributes.builder()
        .put(SYSTEM_ATTR_KEY, SYSTEM_ATTR_VALUE)
        .put(DESTINATION_ATTR_KEY, topicName)
        .put(PROJECT_ATTR_KEY, projectName)
        .put(CODE_FUNCTION_ATTR_KEY, codeFunction)
        .put(OPERATION_ATTR_KEY, operation);

    return attributesBuilder;
  }

  public static final Span startPublishRpcSpan(Tracer tracer, TopicName topicName,
      List<PubsubMessageWrapper> messages) {
    Attributes attributes = createPublishSpanAttributesBuilder(topicName.getTopic(), topicName.getProject(),
        "Publisher.publishCall",
        "publish")
        .put(MESSAGE_BATCH_SIZE_ATTR_KEY, messages.size()).build();
    Span publishRpcSpan = tracer.spanBuilder(topicName + PUBLISH_RPC_SPAN_SUFFIX).setSpanKind(SpanKind.CLIENT)
        .setAllAttributes(attributes)
        .startSpan();
    return publishRpcSpan;
  }

  public static final void endPublishRpcSpan(Span publishRpcSpan) {
    if (publishRpcSpan != null) {
      publishRpcSpan.end();
    }
  }

  public static final void setPublishRpcSpanException(Span publishRpcSpan, Throwable t) {
    if (publishRpcSpan != null) {
      publishRpcSpan.setStatus(StatusCode.ERROR, "Exception thrown on publish RPC.");
      publishRpcSpan.recordException(t);
      endPublishRpcSpan(publishRpcSpan);
    }
  }
}
