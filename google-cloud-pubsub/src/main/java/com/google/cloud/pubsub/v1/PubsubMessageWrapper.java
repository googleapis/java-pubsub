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

import com.google.common.base.Preconditions;
import com.google.pubsub.v1.PubsubMessage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.util.Optional;

public class PubsubMessageWrapper {
  private final PubsubMessage pubsubMessage;

  private static final String MESSAGE_ATTRIBUTE_PREFIX = "googclient_";

  /**
   * Publish Spans are hierarchical - they must be open and closed in the following order:
   *
   * <p>Publish -> (optional) Flow Control -> (optional) Scheduler -> PublishRpc
   */
  private static final String SEND = "send";

  private String PUBLISH_SPAN_NAME;
  private static final String PUBLISH_FLOW_CONTROL_SPAN_NAME = "publish flow control";
  private static final String PUBLISH_SCHEDULER_SPAN_NAME = "publish scheduler";
  private static final String PUBLISH_RPC_SPAN_NAME = "send Publish";

  private Optional<Span> publishSpan = Optional.empty();
  private Optional<Span> publishFlowControlSpan = Optional.empty();
  private Optional<Span> publishSchedulerSpan = Optional.empty();
  private Optional<Span> publishRpcSpan = Optional.empty();

  /**
   * Subscribe Spans are hierarchical - they must be open and closed in the following order:
   *
   * <p>Receive -> (optional) Flow Control -> (optional) Scheduler -> Process -> ModifyAckDeadline
   * -> Acknowledgement OR Negative Acknowledgement
   */
  private static final String RECEIVE = "receive";

  private String RECEIVE_SPAN_NAME;
  private static final String SUBSCRIBE_FLOW_CONTROL_SPAN_NAME = "subscribe flow control";
  private static final String SUBSCRIBE_SCHEDULE_SPAN_NAME = "subscribe scheduler";
  private static final String PROCESS = "process";
  private String PROCESS_SPAN_NAME;
  private static final String MODACK_SPAN_NAME = "send modifyAckDeadline";
  private static final String ACK_SPAN_NAME = "send Acknowledgement";
  private static final String NACK_SPAN_NAME = "send Negative Acknowledgement";

  private Optional<Span> subscribeSpan = Optional.empty();
  private Optional<Span> subscribeFlowControlSpan = Optional.empty();
  private Optional<Span> subscribeSchedulerSpan = Optional.empty();
  private Optional<Span> processSpan = Optional.empty();
  private Optional<Span> modackSpan = Optional.empty();
  private Optional<Span> ackSpan = Optional.empty();
  private Optional<Span> nackSpan = Optional.empty();

  protected PubsubMessageWrapper(Builder builder) {
    this.pubsubMessage = builder.pubsubMessage;

    if (builder.topicName.isPresent()) {
      this.PUBLISH_SPAN_NAME = builder.topicName.get() + " " + this.SEND;
    }

    if (builder.subscriptionName.isPresent()) {
      this.RECEIVE_SPAN_NAME = builder.subscriptionName.get() + " " + this.RECEIVE;
      this.PROCESS_SPAN_NAME = builder.subscriptionName.get() + " " + this.PROCESS;
    }
  }

  public void startPublishSpan(Optional<Tracer> tracer) {
    if (tracer.isPresent()) {
      this.publishSpan = Optional.of(createAndStartSpan(tracer.get(), PUBLISH_SPAN_NAME));
    }
  }

  public void endPublishSpan() {
    if (this.publishSpan.isPresent()) {
      this.publishSpan.get().end();
    }
  }

  /** (Optional) Start Publish Flow Control Span */
  public void startPublishFlowControlSpan(Optional<Tracer> tracer) {
    if (tracer.isPresent()) {
      this.publishFlowControlSpan =
          Optional.of(
              createAndStartSpan(
                  tracer.get(), PUBLISH_FLOW_CONTROL_SPAN_NAME, this.publishSpan.get()));
    }
  }

  /** (Optional) End Publish Flow Control Span */
  public void endPublishFlowControlSpan() {
    if (this.publishFlowControlSpan.isPresent()) {
      this.publishFlowControlSpan.get().end();
    }
  }

  /** (Optional) Start Flow Control Span */
  public void startPublishSchedulerSpan(Optional<Tracer> tracer) {
    if (tracer.isPresent()) {
      // Check for optional parent
      Span parent =
          this.publishFlowControlSpan.isPresent()
              ? this.publishFlowControlSpan.get()
              : this.publishSpan.get();
      this.publishSchedulerSpan =
          Optional.of(this.createAndStartSpan(tracer.get(), PUBLISH_SCHEDULER_SPAN_NAME, parent));
    }
  }

  /** (Optional) End Publish Scheduler Span */
  public void endPublishSchedulerSpan() {
    if (this.publishSchedulerSpan.isPresent()) {
      this.publishSchedulerSpan.get().end();
    }
  }

  public void startPublishRpcSpan(Optional<Tracer> tracer) {
    if (tracer.isPresent()) {
      Span parent;
      if (this.publishSchedulerSpan.isPresent()) {
        parent = this.publishSchedulerSpan.get();
      } else if (this.publishFlowControlSpan.isPresent()) {
        parent = this.publishFlowControlSpan.get();
      } else {
        parent = this.publishSpan.get();
      }
      this.publishRpcSpan =
          Optional.of(createAndStartSpan(tracer.get(), PUBLISH_RPC_SPAN_NAME, parent));
    }
  }

  public void endPublishRpcSpan() {
    if (this.publishRpcSpan.isPresent()) {
      this.publishRpcSpan.get().end();
    }
  }

  public void startSubscribeReceiveSpan(Optional<Tracer> tracer) {
    if (tracer.isPresent()) {
      this.subscribeSpan = Optional.of(createAndStartSpan(tracer.get(), RECEIVE_SPAN_NAME));
    }
  }

  public void endSubscribeReceiveSpan() {
    if (this.subscribeSpan.isPresent()) {
      this.subscribeSpan.get().end();
    }
  }

  public void startSubscribeFlowControlSpan(Optional<Tracer> tracer) {
    if (tracer.isPresent()) {
      this.subscribeFlowControlSpan =
          Optional.of(
              this.createAndStartSpan(
                  tracer.get(), SUBSCRIBE_FLOW_CONTROL_SPAN_NAME, this.subscribeSpan.get()));
    }
  }

  public void endSubscribeFlowControlSpan() {
    if (this.subscribeFlowControlSpan.isPresent()) {
      this.subscribeFlowControlSpan.get().end();
    }
  }

  public void startSubscribeSchedulerSpan(Optional<Tracer> tracer) {
    if (tracer.isPresent()) {
      Span parent =
          this.subscribeFlowControlSpan.isPresent()
              ? this.subscribeFlowControlSpan.get()
              : this.subscribeSpan.get();
      this.subscribeSchedulerSpan =
          Optional.of(this.createAndStartSpan(tracer.get(), SUBSCRIBE_SCHEDULE_SPAN_NAME, parent));
    }
  }

  public void endSubscribeSchedulerSpan() {
    if (this.subscribeSchedulerSpan.isPresent()) {
      this.subscribeSchedulerSpan.get().end();
    }
  }

  public void startSubscribeProcessSpan(Optional<Tracer> tracer) {
    if (tracer.isPresent()) {
      Span parent;

      if (this.subscribeSchedulerSpan.isPresent()) {
        parent = this.subscribeSchedulerSpan.get();
      } else if (this.subscribeFlowControlSpan.isPresent()) {
        parent = this.subscribeFlowControlSpan.get();
      } else {
        parent = this.subscribeSpan.get();
      }

      this.processSpan =
          Optional.of(this.createAndStartSpan(tracer.get(), PROCESS_SPAN_NAME, parent));
    }
  }

  public void endSubscribeProcessSpan() {
    if (this.processSpan.isPresent()) {
      this.processSpan.get().end();
    }
  }

  public void startSubscribeModAckSpan(Optional<Tracer> tracer) {
    if (tracer.isPresent()) {
      this.modackSpan =
          Optional.of(
              this.createAndStartSpan(tracer.get(), MODACK_SPAN_NAME, this.processSpan.get()));
    }
  }

  public void endSubscribeModAckSpan() {
    if (this.modackSpan.isPresent()) {
      this.modackSpan.get().end();
    }
  }

  public void startSubscribeAckSpan(Optional<Tracer> tracer) {
    if (tracer.isPresent()) {
      this.ackSpan =
          Optional.of(this.createAndStartSpan(tracer.get(), ACK_SPAN_NAME, this.modackSpan.get()));
    }
  }

  public void endSubscribeAckSpan() {
    if (this.ackSpan.isPresent()) {
      this.ackSpan.get().end();
    }
  }

  public void startSubscribeNackSpan(Optional<Tracer> tracer) {
    if (tracer.isPresent()) {
      this.nackSpan =
          Optional.of(this.createAndStartSpan(tracer.get(), NACK_SPAN_NAME, this.modackSpan.get()));
    }
  }

  public void endSubscribeNackSpan() {
    if (this.nackSpan.isPresent()) {
      this.nackSpan.get().end();
    }
  }

  private Span createAndStartSpan(Tracer tracer, String spanName) {
    return tracer.spanBuilder(spanName).startSpan();
  }

  private Span createAndStartSpan(Tracer tracer, String spanName, Span parent) {
    return tracer.spanBuilder(spanName).setParent(Context.current().with(parent)).startSpan();
  }

  public static PubsubMessageWrapper.Builder newBuilder(PubsubMessage pubsubMessage) {
    return new Builder(pubsubMessage);
  }

  /** Builder of {@link PubsubMessageWrapper PubsubMessageWrapper}. */
  protected static final class Builder {
    private final PubsubMessage pubsubMessage;
    private Optional<String> topicName;
    private Optional<String> subscriptionName;

    protected Builder(PubsubMessage pubsubMessage) {
      this.pubsubMessage = pubsubMessage;
      this.topicName = Optional.empty();
      this.subscriptionName = Optional.empty();
    }

    /** Set the topic name to allow for publish span names */
    public Builder setTopicName(String topicName) {
      this.topicName = Optional.of(topicName);
      return this;
    }

    /** Set the subscription name to allow for subscribe span names */
    public Builder setSubscriptionName(String subscriptionName) {
      this.subscriptionName = Optional.of(subscriptionName);
      return this;
    }

    public PubsubMessageWrapper build() {
      // Requires one of {topicName, subscriptionName}
      Preconditions.checkArgument(this.topicName.isPresent() || this.subscriptionName.isPresent());
      return new PubsubMessageWrapper(this);
    }
  }
}
