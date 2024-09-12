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

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

public class OpenTelemetryUtil {
  private static final String MESSAGING_SYSTEM_VALUE = "gcp_pubsub";
  private static final String PROJECT_ATTR_KEY = "gcp.project_id";

  /** Populates attributes that are common the publisher parent span and publish RPC span. */
  protected static final AttributesBuilder createCommonSpanAttributesBuilder(
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
}
