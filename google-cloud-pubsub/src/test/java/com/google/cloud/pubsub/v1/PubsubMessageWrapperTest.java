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
import io.opentelemetry.context.Context;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class PubsubMessageWrapperTest {
  @Rule public TestName testName = new TestName();

  private static final String FULL_TOPIC_NAME = "projects/my-project/topics/my-topic";
  private static final String PUBLISH_SPAN_NAME = "projects/my-project/topics/my-topic send";
  private static final String PUBLISH_FLOW_CONTROL_SPAN_NAME = "publish flow control";
  private static final String PUBLISH_SCHEDULER_SPAN_NAME = "publish scheduler";
  private static final String PUBLISH_RPC_SPAN_NAME = "send Publish";

  private static final String FULL_SUBSCRIPTION_NAME = "projects/my-project/subscriptions/my-sub";
  private static final String RECEIVE_SPAN_NAME =
      "projects/my-project/subscriptions/my-sub receive";
  private static final String SUBSCRIBE_PROCESS_SPAN_NAME =
      "projects/my-project/subscriptions/my-sub process";
  private static final String MODACK_SPAN_NAME = "send modifyAckDeadline";
  private static final String ACK_SPAN_NAME = "send Acknowledgement";
  private static final String NACK_SPAN_NAME = "send Negative Acknowledgement";

  @Test
  public void testPublishSpans() {
    Tracer mockTracer = mock(Tracer.class, RETURNS_DEEP_STUBS);

    Span mockPublishSpan = mock(Span.class);
    Span mockPublishFlowControlSpan = mock(Span.class);
    Span mockPublishSchedulerSpan = mock(Span.class);
    Span mockPublishRpcSpan = mock(Span.class);

    PubsubMessageWrapper pubsubMessageWrapper =
        PubsubMessageWrapper.newBuilder(getPubsubMessage()).setTopicName(FULL_TOPIC_NAME).build();

    when(mockTracer.spanBuilder(PUBLISH_SPAN_NAME).startSpan()).thenReturn(mockPublishSpan);
    when(mockTracer
            .spanBuilder(PUBLISH_FLOW_CONTROL_SPAN_NAME)
            .setParent(Context.current().with(mockPublishSpan))
            .startSpan())
        .thenReturn(mockPublishFlowControlSpan);
    when(mockTracer
            .spanBuilder(PUBLISH_SCHEDULER_SPAN_NAME)
            .setParent(Context.current().with(mockPublishFlowControlSpan))
            .startSpan())
        .thenReturn(mockPublishSchedulerSpan);
    when(mockTracer
            .spanBuilder(PUBLISH_RPC_SPAN_NAME)
            .setParent(Context.current().with(mockPublishSchedulerSpan))
            .startSpan())
        .thenReturn(mockPublishRpcSpan);

    pubsubMessageWrapper.startPublishSpan(mockTracer);
    pubsubMessageWrapper.startPublishFlowControlSpan(mockTracer);
    pubsubMessageWrapper.startPublishSchedulerSpan(mockTracer);
    pubsubMessageWrapper.startPublishRpcSpan(mockTracer);

    pubsubMessageWrapper.endPublishRpcSpan();
    pubsubMessageWrapper.endPublishSchedulerSpan();
    pubsubMessageWrapper.endPublishFlowControlSpan();
    pubsubMessageWrapper.endPublishSpan();

    verify(mockPublishSpan, times(1)).end();
    verify(mockPublishFlowControlSpan, times(1)).end();
    verify(mockPublishSchedulerSpan, times(1)).end();
    verify(mockPublishRpcSpan, times(1)).end();
  }

  @Test
  public void testSubscribeSpans() {
    testSubscribeSpan(true);
    testSubscribeSpan(false);
  }

  private void testSubscribeSpan(boolean isAck) {
    Tracer mockTracer = mock(Tracer.class, RETURNS_DEEP_STUBS);

    PubsubMessageWrapper pubsubMessageWrapper =
        PubsubMessageWrapper.newBuilder(getPubsubMessage())
            .setSubscriptionName(FULL_SUBSCRIPTION_NAME)
            .build();

    Span mockReceiveSpan = mock(Span.class);
    Span mockSchedulerSpan = mock(Span.class);
    Span mockFlowControlSpan = mock(Span.class);
    Span mockProcessSpan = mock(Span.class);
    Span mockModackSpan = mock(Span.class);
    Span mockAckNackSpan = mock(Span.class);

    when(mockTracer.spanBuilder(RECEIVE_SPAN_NAME).startSpan()).thenReturn(mockReceiveSpan);
    when(mockTracer
            .spanBuilder(SUBSCRIBE_PROCESS_SPAN_NAME)
            .setParent(Context.current().with(mockReceiveSpan))
            .startSpan())
        .thenReturn(mockProcessSpan);

    when(mockTracer
            .spanBuilder(MODACK_SPAN_NAME)
            .setParent(Context.current().with(mockProcessSpan))
            .startSpan())
        .thenReturn(mockModackSpan);

    if (isAck) {
      when(mockTracer
              .spanBuilder(ACK_SPAN_NAME)
              .setParent(Context.current().with(mockModackSpan))
              .startSpan())
          .thenReturn(mockAckNackSpan);
    } else {
      when(mockTracer
              .spanBuilder(NACK_SPAN_NAME)
              .setParent(Context.current().with(mockModackSpan))
              .startSpan())
          .thenReturn(mockAckNackSpan);
    }

    pubsubMessageWrapper.startSubscribeReceiveSpan(mockTracer);
    //    pubsubMessageWrapper.startSubscribeFlowControlSpan(mockTracer);
    //    pubsubMessageWrapper.startSubscribeSchedulerSpan(mockTracer);
    pubsubMessageWrapper.startSubscribeProcessSpan(mockTracer);
    pubsubMessageWrapper.startSubscribeModAckSpan(mockTracer);

    if (isAck) {
      pubsubMessageWrapper.startSubscribeAckSpan(mockTracer);
      pubsubMessageWrapper.endSubscribeAckSpan();
    } else {
      // Nack
      pubsubMessageWrapper.startSubscribeNackSpan(mockTracer);
      pubsubMessageWrapper.endSubscribeNackSpan();
    }

    pubsubMessageWrapper.endSubscribeModAckSpan();
    pubsubMessageWrapper.endSubscribeProcessSpan();
    //    pubsubMessageWrapper.endSubscribeSchedulerSpan();
    //    pubsubMessageWrapper.endSubscribeFlowControlSpan();
    pubsubMessageWrapper.endSubscribeReceiveSpan();

    verify(mockReceiveSpan, times(1)).end();
    verify(mockProcessSpan, times(1)).end();
    verify(mockModackSpan, times(1)).end();
    verify(mockAckNackSpan, times(1)).end();
  }

  private PubsubMessage getPubsubMessage() {
    return PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8("sample-data")).build();
  }
}
