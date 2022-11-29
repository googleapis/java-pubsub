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

import com.google.pubsub.v1.PubsubMessage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

public class PubsubMessageWrapper {
    private static final String MESSAGE_ATTRIBUTE_PREFIX = "googclient_";
    // TODO: Update this to use the actual project + topic name
    private static final String PUBLISH_SPAN_NAME = "projects/my-project/topics/my-topic send";
    private static final String FLOW_CONTROL_SPAN_NAME = "publisher flow control";
    private static final String PUBLISH_RPC_SPAN_NAME = "send Publish";

    private final PubsubMessage pubsubMessage;
    private Span publishSpan;
    private Span flowControlSpan;
    private Span publishRpcSpan;

    protected PubsubMessageWrapper(PubsubMessage pubsubMessage) {
        this.pubsubMessage = pubsubMessage;
    }

    public void startPublishSpan(Tracer tracer) {
        this.publishSpan = createAndStartSpan(tracer, PUBLISH_SPAN_NAME);
    }

    public void endPublishSpan() throws {
        // TODO: Check that child spans are completed


        this.publishSpan.end();
    }

    public void startPublishRpcSpan(Tracer tracer) {
        this.publishRpcSpan = createAndStartSpan(tracer, PUBLISH_RPC_SPAN_NAME, publishSpan);
    }

    public void endPublishRpcSpan() {
        this.publishRpcSpan.end();
    }

//    /** (Optional) Start Flow Control Span */
//    public void startFlowControlSpan(Tracer tracer) {
//
//    }
//
//    /** (Optional) End Flow Control Span */
//    public void endFlowControlSpan() {}

    private Span createAndStartSpan(Tracer tracer, String spanName) {
        return tracer.spanBuilder(PUBLISH_SPAN_NAME).startSpan();
    }

    private Span createAndStartSpan(Tracer tracer, String spanName, Span parent) {
        return tracer.spanBuilder(PUBLISH_SPAN_NAME).setParent(Context.current().with(parent)).startSpan();
    }

    public abstract static class PubsubMessageWrapperException extends Exception {
        private PubsubMessageWrapperException() {
        }
    }
}