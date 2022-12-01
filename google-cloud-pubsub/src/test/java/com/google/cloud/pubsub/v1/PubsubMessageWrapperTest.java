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
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
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
  private static final String SUBSCRIBE_FLOW_CONTROL_SPAN_NAME = "subscribe flow control";
  private static final String SUBSCRIBE_SCHEDULE_SPAN_NAME = "subscribe scheduler";
  private static final String SUBSCRIBE_PROCESS_SPAN_NAME =
      "projects/my-project/subscriptions/my-sub process";
  private static final String MODACK_SPAN_NAME = "send modifyAckDeadline";
  private static final String ACK_SPAN_NAME = "send Acknowledgement";
  private static final String NACK_SPAN_NAME = "send Negative Acknowledgement";

  @Test
  public void testPublishSpans(
      @TestParameter boolean useTracer,
      @TestParameter boolean useFlowControl,
      @TestParameter boolean useScheduler) {

    PubsubMessageWrapper pubsubMessageWrapper =
        PubsubMessageWrapper.newBuilder(getPubsubMessage()).setTopicName(FULL_TOPIC_NAME).build();

    Optional<Tracer> mockTracer = Optional.empty();

    Span mockPublishSpan = mock(Span.class);
    Span mockFlowControlSpan = mock(Span.class);
    Span mockSchedulerSpan = mock(Span.class);
    Span mockPublishRpcSpan = mock(Span.class);

    if (useTracer) {
      // Set up our mocks if needed
      mockTracer = Optional.of(mock(Tracer.class, RETURNS_DEEP_STUBS));
      when(mockTracer.get().spanBuilder(PUBLISH_SPAN_NAME).startSpan()).thenReturn(mockPublishSpan);

      if (useFlowControl) {
        when(mockTracer
                .get()
                .spanBuilder(PUBLISH_FLOW_CONTROL_SPAN_NAME)
                .setParent(Context.current().with(mockPublishSpan))
                .startSpan())
            .thenReturn(mockFlowControlSpan);
      }

      if (useScheduler) {
        when(mockTracer
                .get()
                .spanBuilder(PUBLISH_SCHEDULER_SPAN_NAME)
                .setParent(Context.current().with(mockPublishSpan))
                .startSpan())
            .thenReturn(mockSchedulerSpan);
      }

      when(mockTracer
              .get()
              .spanBuilder(PUBLISH_RPC_SPAN_NAME)
              .setParent(Context.current().with(mockPublishSpan))
              .startSpan())
          .thenReturn(mockPublishRpcSpan);
    }

    pubsubMessageWrapper.startPublishSpan(mockTracer);
    if (useFlowControl) {
      pubsubMessageWrapper.startPublishFlowControlSpan(mockTracer);
    }

    if (useScheduler) {
      pubsubMessageWrapper.startPublishSchedulerSpan(mockTracer);
    }

    pubsubMessageWrapper.startPublishRpcSpan(mockTracer, 1);

    pubsubMessageWrapper.endPublishRpcSpan();
    pubsubMessageWrapper.endPublishSchedulerSpan();
    pubsubMessageWrapper.endPublishFlowControlSpan();
    pubsubMessageWrapper.endPublishSpan();

    verify(mockPublishSpan, times(useTracer ? 1 : 0)).end();
    // If we are using a tracer and flow control we expect 1 call, otherwise 0
    verify(mockFlowControlSpan, times((useTracer && useFlowControl) ? 1 : 0)).end();
    // If we are using a tracer and scheduler we expect 1 call, otherwise 0
    verify(mockSchedulerSpan, times((useTracer && useScheduler) ? 1 : 0)).end();
    verify(mockPublishRpcSpan, times(useTracer ? 1 : 0)).end();
  }

  @Test
  public void testSubscribeSpans(
      @TestParameter boolean useTracer,
      @TestParameter boolean isAck,
      @TestParameter boolean useFlowControl,
      @TestParameter boolean useScheduler) {
    PubsubMessageWrapper pubsubMessageWrapper =
        PubsubMessageWrapper.newBuilder(getPubsubMessage())
            .setSubscriptionName(FULL_SUBSCRIPTION_NAME)
            .build();

    Optional<Tracer> mockTracer = Optional.empty();

    Span mockReceiveSpan = mock(Span.class);
    Span mockFlowControlSpan = mock(Span.class);
    Span mockSchedulerSpan = mock(Span.class);
    Span mockProcessSpan = mock(Span.class);
    Span mockModackSpan = mock(Span.class);
    Span mockAckNackSpan = mock(Span.class);

    if (useTracer) {
      // Set up our mocks if needed
      mockTracer = Optional.of(mock(Tracer.class, RETURNS_DEEP_STUBS));
      when(mockTracer.get().spanBuilder(RECEIVE_SPAN_NAME).startSpan()).thenReturn(mockReceiveSpan);

      // Need to set up our parent span(s) for the optional spans
      Span processSpanParent = mockReceiveSpan;

      if (useFlowControl) {
        when(mockTracer
                .get()
                .spanBuilder(SUBSCRIBE_FLOW_CONTROL_SPAN_NAME)
                .setParent(Context.current().with(mockReceiveSpan))
                .startSpan())
            .thenReturn(mockFlowControlSpan);
        processSpanParent = mockFlowControlSpan;
      }

      if (useScheduler) {
        Span schedulerSpanParent = useFlowControl ? mockFlowControlSpan : mockReceiveSpan;
        when(mockTracer
                .get()
                .spanBuilder(SUBSCRIBE_SCHEDULE_SPAN_NAME)
                .setParent(Context.current().with(schedulerSpanParent))
                .startSpan())
            .thenReturn(mockSchedulerSpan);
        processSpanParent = schedulerSpanParent;
      }

      when(mockTracer
              .get()
              .spanBuilder(SUBSCRIBE_PROCESS_SPAN_NAME)
              .setParent(Context.current().with(processSpanParent))
              .startSpan())
          .thenReturn(mockProcessSpan);

      when(mockTracer
              .get()
              .spanBuilder(MODACK_SPAN_NAME)
              .setParent(Context.current().with(mockProcessSpan))
              .startSpan())
          .thenReturn(mockModackSpan);

      when(mockTracer
              .get()
              .spanBuilder(isAck ? ACK_SPAN_NAME : NACK_SPAN_NAME)
              .setParent(Context.current().with(mockModackSpan))
              .startSpan())
          .thenReturn(mockAckNackSpan);
    }

    pubsubMessageWrapper.startSubscribeReceiveSpan(mockTracer);
    if (useFlowControl) {
      pubsubMessageWrapper.startSubscribeFlowControlSpan(mockTracer);
    }

    if (useScheduler) {
      pubsubMessageWrapper.startSubscribeSchedulerSpan(mockTracer);
    }

    pubsubMessageWrapper.startSubscribeProcessSpan(mockTracer);
    pubsubMessageWrapper.startSubscribeModAckSpan(mockTracer);

    if (isAck) {
      pubsubMessageWrapper.startSubscribeAckSpan(mockTracer);
      pubsubMessageWrapper.endSubscribeAckSpan();
    } else {
      pubsubMessageWrapper.startSubscribeNackSpan(mockTracer);
      pubsubMessageWrapper.endSubscribeNackSpan();
    }

    pubsubMessageWrapper.endSubscribeModAckSpan();
    pubsubMessageWrapper.endSubscribeProcessSpan();
    pubsubMessageWrapper.endSubscribeSchedulerSpan();
    pubsubMessageWrapper.endSubscribeFlowControlSpan();
    pubsubMessageWrapper.endSubscribeReceiveSpan();

    verify(mockReceiveSpan, times(useTracer ? 1 : 0)).end();
    // If we are using a tracer and flow control we expect 1 call, otherwise 0
    verify(mockFlowControlSpan, times((useTracer && useFlowControl) ? 1 : 0)).end();
    // If we are using a tracer and scheduler we expect 1 call, otherwise 0
    verify(mockSchedulerSpan, times((useTracer && useScheduler) ? 1 : 0)).end();
    verify(mockProcessSpan, times(useTracer ? 1 : 0)).end();
    verify(mockModackSpan, times(useTracer ? 1 : 0)).end();
    verify(mockAckNackSpan, times(useTracer ? 1 : 0)).end();
  }

  private PubsubMessage getPubsubMessage() {
    return PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8("sample-data")).build();
  }
}
