/*
 * Copyright 2022 Google LLC
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

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class PubsubMessageWrapperTest {
    @Rule public TestName testName = new TestName();

    private static final String PUBLISH_SPAN_NAME = "projects/my-project/topics/my-topic send";
    private static final String FLOW_CONTROL_SPAN_NAME = "publisher flow control";
    private static final String PUBLISH_RPC_SPAN_NAME = "send Publish";

    private Tracer mockTracer;
    private Span mockPublishSpan;
    private Span mockPublishRpcSpan;

    @Before
    public void setUp() {
        // Setting up our mocks for consistency
        this.mockTracer = mock(Tracer.class, RETURNS_DEEP_STUBS);
        this.mockPublishSpan = mock(Span.class);
        this.mockPublishRpcSpan = mock(Span.class);
    }

    @Test
    public void testRequiredSpans() {
        PubsubMessageWrapper pubsubMessageWrapper = new PubsubMessageWrapper(getPubsubMessage());

        when(this.mockTracer.spanBuilder(PUBLISH_SPAN_NAME).startSpan()).thenReturn(this.mockPublishSpan);
        when(this.mockTracer.spanBuilder(PUBLISH_RPC_SPAN_NAME).startSpan()).thenReturn(this.mockPublishSpan);

        pubsubMessageWrapper.startPublishSpan(this.mockTracer);
        pubsubMessageWrapper.startPublishRpcSpan(this.mockTracer);
        pubsubMessageWrapper.endPublishRpcSpan();
        pubsubMessageWrapper.endPublishSpan();

        verify(this.mockPublishSpan, times(1)).end();
    }

    @Test
    public void testPublishSpansOutOfOrder() {

    }

    @Test
    public void testPublishOptionalSpans() {}

    private PubsubMessage getPubsubMessage() {
        return PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8("sample-data")).build();
    }
}