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

  private Span publishSpan;
  private Optional<Span> publishFlowControlSpan = Optional.empty();
  private Optional<Span> publishSchedulerSpan = Optional.empty();
  private Span publishRpcSpan;

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

  private Span subscribeSpan;
  private Optional<Span> subscribeFlowControlSpan = Optional.empty();
  private Optional<Span> subscribeSchedulerSpan = Optional.empty();
  private Span processSpan;
  private Span modackSpan;
  private Span ackSpan;
  private Span nackSpan;

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

  public PubsubMessage getPubsubMessage() {
    return this.pubsubMessage;
  }

  public void startPublishSpan(Optional<Tracer> tracer) {
    if (tracer.isPresent()) {
      this.publishSpan = createAndStartSpan(tracer.get(), PUBLISH_SPAN_NAME);
    }
  }

  public void endPublishSpan() {
    this.publishSpan.end();
  }

  /** (Optional) Start Publish Flow Control Span */
  public void startPublishFlowControlSpan(Tracer tracer) {
    this.publishFlowControlSpan =
        Optional.of(createAndStartSpan(tracer, PUBLISH_FLOW_CONTROL_SPAN_NAME, this.publishSpan));
  }

  /** (Optional) End Publish Flow Control Span */
  public void endPublishFlowControlSpan() {
    if (this.publishFlowControlSpan.isPresent()) {
      this.publishFlowControlSpan.get().end();
    }
  }

  /** (Optional) Start Flow Control Span */
  public void startPublishSchedulerSpan(Tracer tracer) {
    // Check for optional parent
    Span parent =
        this.publishFlowControlSpan.isPresent()
            ? this.publishFlowControlSpan.get()
            : this.publishSpan;
    this.publishSchedulerSpan =
        Optional.of(this.createAndStartSpan(tracer, PUBLISH_SCHEDULER_SPAN_NAME, parent));
  }

  /** (Optional) End Publish Scheduler Span */
  public void endPublishSchedulerSpan() {
    if (this.publishSchedulerSpan.isPresent()) {
      this.publishSchedulerSpan.get().end();
    }
  }

  public void startPublishRpcSpan(Tracer tracer) {
    this.publishRpcSpan = createAndStartSpan(tracer, PUBLISH_RPC_SPAN_NAME, publishSpan);
  }

  public void endPublishRpcSpan() {
    this.publishRpcSpan.end();
  }

  public void startSubscribeReceiveSpan(Tracer tracer) {
    this.subscribeSpan = createAndStartSpan(tracer, RECEIVE_SPAN_NAME);
  }

  public void endSubscribeReceiveSpan() {
    this.subscribeSpan.end();
  }

  public void startSubscribeFlowControlSpan(Tracer tracer) {
    this.subscribeFlowControlSpan =
        Optional.of(
            this.createAndStartSpan(tracer, SUBSCRIBE_FLOW_CONTROL_SPAN_NAME, this.subscribeSpan));
  }

  public void endSubscribeFlowControlSpan() {
    if (this.subscribeFlowControlSpan.isPresent()) {
      this.subscribeFlowControlSpan.get().end();
    }
  }

  public void startSubscribeSchedulerSpan(Tracer tracer) {
    Span parent =
        this.subscribeFlowControlSpan.isPresent()
            ? this.subscribeFlowControlSpan.get()
            : this.subscribeSpan;
    this.subscribeSchedulerSpan =
        Optional.of(this.createAndStartSpan(tracer, SUBSCRIBE_SCHEDULE_SPAN_NAME, parent));
  }

  public void endSubscribeSchedulerSpan() {
    if (this.subscribeSchedulerSpan.isPresent()) {
      this.subscribeSchedulerSpan.get().end();
    }
  }

  public void startSubscribeProcessSpan(Tracer tracer) {
    Span parent = this.subscribeSpan;

    // Check for optional Scheduler and Flow Control spans
    if (this.subscribeSchedulerSpan.isPresent()) {
      parent = this.subscribeSchedulerSpan.get();
    } else if (this.subscribeFlowControlSpan.isPresent()) {
      parent = this.subscribeFlowControlSpan.get();
    }

    this.processSpan = this.createAndStartSpan(tracer, PROCESS_SPAN_NAME, parent);
  }

  public void endSubscribeProcessSpan() {
    this.processSpan.end();
  }

  public void startSubscribeModAckSpan(Tracer tracer) {
    this.modackSpan = this.createAndStartSpan(tracer, MODACK_SPAN_NAME, this.processSpan);
  }

  public void endSubscribeModAckSpan() {
    this.modackSpan.end();
  }

  public void startSubscribeAckSpan(Tracer tracer) {
    this.ackSpan = this.createAndStartSpan(tracer, ACK_SPAN_NAME, this.modackSpan);
  }

  public void endSubscribeAckSpan() {
    this.ackSpan.end();
  }

  public void startSubscribeNackSpan(Tracer tracer) {
    this.nackSpan = this.createAndStartSpan(tracer, NACK_SPAN_NAME, this.modackSpan);
  }

  public void endSubscribeNackSpan() {
    this.nackSpan.end();
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
