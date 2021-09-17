/*
 * Copyright 2016 Google LLC
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

import com.google.api.core.AbstractApiService;
import com.google.api.core.ApiClock;
import com.google.api.core.ApiService;
import com.google.api.core.BetaApi;
import com.google.api.core.CurrentMillisClock;
import com.google.api.core.InternalApi;
import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.batching.FlowController.LimitExceededBehavior;
import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.Distribution;
import com.google.api.gax.core.ExecutorAsBackgroundResource;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.api.gax.rpc.NoHeaderProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.stub.GrpcSubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.threeten.bp.Duration;

/**
 * A Cloud Pub/Sub <a href="https://cloud.google.com/pubsub/docs/subscriber">subscriber</a> that is
 * associated with a specific subscription at creation.
 *
 * <p>A {@link Subscriber} allows you to provide an implementation of a {@link MessageReceiver
 * receiver} to which messages are going to be delivered as soon as they are received by the
 * subscriber. The delivered messages then can be {@link AckReplyConsumer#ack() acked} or {@link
 * AckReplyConsumer#nack() nacked} at will as they get processed by the receiver. Nacking a messages
 * implies a later redelivery of such message.
 *
 * <p>The subscriber handles the ack management, by automatically extending the ack deadline while
 * the message is being processed, to then issue the ack or nack of such message when the processing
 * is done (see {@link Builder#setMaxAckExtensionPeriod(Duration)}). <strong>Note:</strong> message
 * redelivery is still possible.
 *
 * <p>It also provides customizable options that control:
 *
 * <ul>
 *   <li>Ack deadline extension: such as the amount of time ahead to trigger the extension of
 *       message acknowledgement expiration.
 *   <li>Flow control: such as the maximum outstanding messages or maximum outstanding bytes to keep
 *       in memory before the receiver either ack or nack them.
 * </ul>
 *
 * <p>{@link Subscriber} will use the credentials set on the channel, which uses application default
 * credentials through {@link GoogleCredentials#getApplicationDefault} by default.
 *
 * <p>{@code Subscriber} is implemented using <a
 * href="http://google.github.io/guava/releases/snapshot/api/docs/com/google/common/util/concurrent/Service.html">Guava's
 * Service</a> and provides the same methods. See <a
 * href="https://github.com/google/guava/wiki/ServiceExplained">Guava documentation</a> for more
 * details.
 */
public class Subscriber extends AbstractApiService implements SubscriberInterface {
  @InternalApi static final Duration DEFAULT_MAX_DURATION_PER_ACK_EXTENSION = Duration.ofMillis(0);
  private static final int THREADS_PER_CHANNEL = 5;
  private static final int MAX_INBOUND_MESSAGE_SIZE =
      20 * 1024 * 1024; // 20MB API maximum message size.
  @InternalApi static final int MAX_ACK_DEADLINE_SECONDS = 600;
  @InternalApi static final int MIN_ACK_DEADLINE_SECONDS = 10;
  private static final Duration ACK_EXPIRATION_PADDING = Duration.ofSeconds(5);

  private static final Logger logger = Logger.getLogger(Subscriber.class.getName());

  private final String subscriptionName;
  private final FlowControlSettings flowControlSettings;
  private final boolean useLegacyFlowControl;
  private final Duration maxAckExtensionPeriod;
  private final Duration maxDurationPerAckExtension;
  // The ExecutorProvider used to generate executors for processing messages.
  private final ExecutorProvider executorProvider;
  // An instantiation of the SystemExecutorProvider used for processing acks
  // and other system actions.
  @Nullable private final ScheduledExecutorService alarmsExecutor;
  private final Distribution ackLatencyDistribution =
      new Distribution(MAX_ACK_DEADLINE_SECONDS + 1);

  private SubscriberStub subStub;
  private final SubscriberStubSettings subStubSettings;
  private final FlowController flowController;
  private final int numPullers;

  private final MessageReceiver receiver;
  private final List<StreamingSubscriberConnection> streamingSubscriberConnections;
  private final ApiClock clock;
  private final List<BackgroundResource> backgroundResources = new ArrayList<>();

  private Subscriber(Builder builder) {
    receiver = builder.receiver;
    flowControlSettings = builder.flowControlSettings;
    useLegacyFlowControl = builder.useLegacyFlowControl;
    subscriptionName = builder.subscriptionName;

    maxAckExtensionPeriod = builder.maxAckExtensionPeriod;
    maxDurationPerAckExtension = builder.maxDurationPerAckExtension;
    clock = builder.clock.isPresent() ? builder.clock.get() : CurrentMillisClock.getDefaultClock();

    flowController =
        new FlowController(
            builder
                .flowControlSettings
                .toBuilder()
                .setLimitExceededBehavior(LimitExceededBehavior.Block)
                .build());

    this.numPullers = builder.parallelPullCount;

    executorProvider = builder.executorProvider;

    ExecutorProvider systemExecutorProvider = builder.systemExecutorProvider;
    alarmsExecutor = systemExecutorProvider.getExecutor();

    if (systemExecutorProvider.shouldAutoClose()) {
      backgroundResources.add(new ExecutorAsBackgroundResource((alarmsExecutor)));
    }

    TransportChannelProvider channelProvider = builder.channelProvider;
    if (channelProvider.acceptsPoolSize()) {
      channelProvider = channelProvider.withPoolSize(numPullers);
    }

    try {
      this.subStubSettings =
          SubscriberStubSettings.newBuilder()
              .setExecutorProvider(systemExecutorProvider)
              .setCredentialsProvider(builder.credentialsProvider)
              .setTransportChannelProvider(channelProvider)
              .setHeaderProvider(builder.headerProvider)
              .setEndpoint(builder.endpoint)
              .build();
      // TODO(pongad): what about internal header??
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }

    streamingSubscriberConnections = new ArrayList<StreamingSubscriberConnection>(numPullers);

    // We regularly look up the distribution for a good subscription deadline.
    // So we seed the distribution with the minimum value to start with.
    // Distribution is percentile-based, so this value will eventually lose importance.
    ackLatencyDistribution.record(MIN_ACK_DEADLINE_SECONDS);
  }

  /**
   * Constructs a new {@link Builder}.
   *
   * @param subscription Cloud Pub/Sub subscription to bind the subscriber to
   * @param receiver an implementation of {@link MessageReceiver} used to process the received
   *     messages
   */
  public static Builder newBuilder(ProjectSubscriptionName subscription, MessageReceiver receiver) {
    return newBuilder(subscription.toString(), receiver);
  }

  /**
   * Constructs a new {@link Builder}.
   *
   * @param subscription Cloud Pub/Sub subscription to bind the subscriber to
   * @param receiver an implementation of {@link MessageReceiver} used to process the received
   *     messages
   */
  public static Builder newBuilder(String subscription, MessageReceiver receiver) {
    return new Builder(subscription, receiver);
  }

  /** Returns the delivery attempt count for a received {@link PubsubMessage} */
  public static Integer getDeliveryAttempt(PubsubMessage message) {
    if (!message.containsAttributes("googclient_deliveryattempt")) {
      return null;
    }
    return Integer.parseInt(message.getAttributesOrThrow("googclient_deliveryattempt"));
  }

  /** Subscription which the subscriber is subscribed to. */
  public String getSubscriptionNameString() {
    return subscriptionName;
  }

  /** The flow control settings the Subscriber is configured with. */
  public FlowControlSettings getFlowControlSettings() {
    return flowControlSettings;
  }

  /**
   * Initiates service startup and returns immediately.
   *
   * <p>Example of receiving a specific number of messages.
   *
   * <pre>{@code
   * Subscriber subscriber = Subscriber.newBuilder(subscription, receiver).build();
   * subscriber.addListener(new Subscriber.Listener() {
   *   public void failed(Subscriber.State from, Throwable failure) {
   *     // Handle error.
   *   }
   * }, executor);
   * subscriber.startAsync();
   *
   * // Wait for a stop signal.
   * // In a server, this might be a signal to stop serving.
   * // In this example, the signal is just a dummy Future.
   * //
   * // By default, Subscriber uses daemon threads (see
   * // https://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html).
   * // Consequently, once other threads have terminated, Subscriber will not stop the JVM from
   * // exiting.
   * // If the Subscriber should simply run forever, either use the setExecutorProvider method in
   * // Subscriber.Builder
   * // to use non-daemon threads or run
   * //   for (;;) {
   * //     Thread.sleep(Long.MAX_VALUE);
   * //   }
   * // at the end of main() to previent the main thread from exiting.
   * done.get();
   * subscriber.stopAsync().awaitTerminated();
   * }</pre>
   */
  @Override
  public ApiService startAsync() {
    // Override only for the docs.
    return super.startAsync();
  }

  @Override
  protected void doStart() {
    logger.log(Level.FINE, "Starting subscriber group.");

    try {
      this.subStub = GrpcSubscriberStub.create(subStubSettings);
    } catch (IOException e) {
      // doesn't matter what we throw, the Service will just catch it and fail to start.
      throw new IllegalStateException(e);
    }

    // When started, connections submit tasks to the executor.
    // These tasks must finish before the connections can declare themselves running.
    // If we have a single-thread executor and call startStreamingConnections from the
    // same executor, it will deadlock: the thread will be stuck waiting for connections
    // to start but cannot start the connections.
    // For this reason, we spawn a dedicated thread. Starting subscriber should be rare.
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                try {
                  startStreamingConnections();
                  notifyStarted();
                } catch (Throwable t) {
                  notifyFailed(t);
                }
              }
            })
        .start();
  }

  @Override
  protected void doStop() {
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                try {
                  // stop connection is no-op if connections haven't been started.
                  runShutdown();
                  notifyStopped();
                } catch (Exception e) {
                  notifyFailed(e);
                }
              }
            })
        .start();
  }

  private void runShutdown() {
    stopAllStreamingConnections();
    shutdownBackgroundResources();
    subStub.shutdownNow();
  }

  private void startStreamingConnections() {
    synchronized (streamingSubscriberConnections) {
      for (int i = 0; i < numPullers; i++) {
        final ScheduledExecutorService executor = executorProvider.getExecutor();
        if (executorProvider.shouldAutoClose()) {
          backgroundResources.add(new ExecutorAsBackgroundResource((executor)));
        }

        streamingSubscriberConnections.add(
            new StreamingSubscriberConnection(
                subscriptionName,
                receiver,
                ACK_EXPIRATION_PADDING,
                maxAckExtensionPeriod,
                maxDurationPerAckExtension,
                ackLatencyDistribution,
                subStub,
                i,
                flowControlSettings,
                useLegacyFlowControl,
                flowController,
                executor,
                alarmsExecutor,
                clock));
      }
      startConnections(
          streamingSubscriberConnections,
          new Listener() {
            @Override
            public void failed(State from, Throwable failure) {
              // If a connection failed is because of a fatal error, we should fail the
              // whole subscriber.
              runShutdown();
              try {
                notifyFailed(failure);
              } catch (IllegalStateException e) {
                if (isRunning()) {
                  throw e;
                }
                // It could happen that we are shutting down while some channels fail.
              }
            }
          });
    }
  }

  private void stopAllStreamingConnections() {
    stopConnections(streamingSubscriberConnections);
  }

  private void shutdownBackgroundResources() {
    for (BackgroundResource resource : backgroundResources) {
      resource.shutdown();
    }
  }

  private void startConnections(
      List<? extends ApiService> connections, final ApiService.Listener connectionsListener) {
    for (ApiService subscriber : connections) {
      subscriber.addListener(connectionsListener, alarmsExecutor);
      subscriber.startAsync();
    }
    for (ApiService subscriber : connections) {
      subscriber.awaitRunning();
    }
  }

  private void stopConnections(List<? extends ApiService> connections) {
    ArrayList<ApiService> liveConnections;
    synchronized (connections) {
      liveConnections = new ArrayList<ApiService>(connections);
      connections.clear();
    }
    for (ApiService subscriber : liveConnections) {
      subscriber.stopAsync();
    }
    for (ApiService subscriber : liveConnections) {
      try {
        subscriber.awaitTerminated();
      } catch (IllegalStateException e) {
        // If the service fails, awaitTerminated will throw an exception.
        // However, we could be stopping services because at least one
        // has already failed, so we just ignore this exception.
      }
    }
  }

  /** Builder of {@link Subscriber Subscribers}. */
  public static final class Builder {
    private static final Duration DEFAULT_MAX_ACK_EXTENSION_PERIOD = Duration.ofMinutes(60);
    static final FlowControlSettings DEFAULT_FLOW_CONTROL_SETTINGS =
        FlowControlSettings.newBuilder()
            .setMaxOutstandingElementCount(1000L)
            .setMaxOutstandingRequestBytes(100L * 1024L * 1024L) // 100MB
            .build();

    private static final ExecutorProvider DEFAULT_EXECUTOR_PROVIDER =
        InstantiatingExecutorProvider.newBuilder()
            .setExecutorThreadCount(THREADS_PER_CHANNEL)
            .build();
    private static final AtomicInteger SYSTEM_EXECUTOR_COUNTER = new AtomicInteger();

    private String subscriptionName;
    private MessageReceiver receiver;

    private Duration maxAckExtensionPeriod = DEFAULT_MAX_ACK_EXTENSION_PERIOD;
    private Duration maxDurationPerAckExtension = DEFAULT_MAX_DURATION_PER_ACK_EXTENSION;

    private boolean useLegacyFlowControl = false;
    private FlowControlSettings flowControlSettings = DEFAULT_FLOW_CONTROL_SETTINGS;

    private ExecutorProvider executorProvider = DEFAULT_EXECUTOR_PROVIDER;
    private ExecutorProvider systemExecutorProvider = null;
    private TransportChannelProvider channelProvider =
        SubscriptionAdminSettings.defaultGrpcTransportProviderBuilder()
            .setMaxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
            .setKeepAliveTime(Duration.ofMinutes(5))
            .build();
    private HeaderProvider headerProvider = new NoHeaderProvider();
    private CredentialsProvider credentialsProvider =
        SubscriptionAdminSettings.defaultCredentialsProviderBuilder().build();
    private Optional<ApiClock> clock = Optional.absent();
    private int parallelPullCount = 1;
    private String endpoint = SubscriberStubSettings.getDefaultEndpoint();

    Builder(String subscriptionName, MessageReceiver receiver) {
      this.subscriptionName = subscriptionName;
      this.receiver = receiver;
    }

    /**
     * {@code ChannelProvider} to use to create Channels, which must point at Cloud Pub/Sub
     * endpoint.
     *
     * <p>For performance, this client benefits from having multiple channels open at once. Users
     * are encouraged to provide instances of {@code ChannelProvider} that creates new channels
     * instead of returning pre-initialized ones.
     */
    public Builder setChannelProvider(TransportChannelProvider channelProvider) {
      this.channelProvider = Preconditions.checkNotNull(channelProvider);
      return this;
    }

    /**
     * Sets the static header provider. The header provider will be called during client
     * construction only once. The headers returned by the provider will be cached and supplied as
     * is for each request issued by the constructed client. Some reserved headers can be overridden
     * (e.g. Content-Type) or merged with the default value (e.g. User-Agent) by the underlying
     * transport layer.
     *
     * @param headerProvider the header provider
     * @return the builder
     */
    @BetaApi
    public Builder setHeaderProvider(HeaderProvider headerProvider) {
      this.headerProvider = Preconditions.checkNotNull(headerProvider);
      return this;
    }

    /**
     * Sets the flow control settings.
     *
     * <p>In the example below, the {@link Subscriber} will make sure that
     *
     * <ul>
     *   <li>there are at most ten thousand outstanding messages, and
     *   <li>the combined size of outstanding messages does not exceed 1GB.
     * </ul>
     *
     * "Outstanding messages" here means the messages that have already been given to {@link
     * MessageReceiver} but not yet {@code acked()} or {@code nacked()}.
     *
     * <pre>{@code
     * FlowControlSettings flowControlSettings =
     *  FlowControlSettings.newBuilder()
     *      .setMaxOutstandingElementCount(10_000L)
     *      .setMaxOutstandingRequestBytes(1_000_000_000L)
     *      .build();
     * Subscriber subscriber =
     *     Subscriber.newBuilder(subscriptionName, receiver)
     *         .setFlowControlSettings(flowControlSettings)
     *         .build();
     * }</pre>
     */
    public Builder setFlowControlSettings(FlowControlSettings flowControlSettings) {
      this.flowControlSettings = Preconditions.checkNotNull(flowControlSettings);
      return this;
    }

    /**
     * Disables enforcing flow control settings at the Cloud PubSub server and uses the less
     * accurate method of only enforcing flow control at the client side.
     */
    public Builder setUseLegacyFlowControl(boolean value) {
      this.useLegacyFlowControl = value;
      return this;
    }

    /**
     * Set the maximum period a message ack deadline will be extended. Defaults to one hour.
     *
     * <p>It is recommended to set this value to a reasonable upper bound of the subscriber time to
     * process any message. This maximum period avoids messages to be <i>locked</i> by a subscriber
     * in cases when the ack reply is lost.
     *
     * <p>A zero duration effectively disables auto deadline extensions.
     */
    public Builder setMaxAckExtensionPeriod(Duration maxAckExtensionPeriod) {
      Preconditions.checkArgument(maxAckExtensionPeriod.toMillis() >= 0);
      this.maxAckExtensionPeriod = maxAckExtensionPeriod;
      return this;
    }

    /**
     * Set the upper bound for a single mod ack extention period.
     *
     * <p>The ack deadline will continue to be extended by up to this duration until
     * MaxAckExtensionPeriod is reached. Setting MaxDurationPerAckExtension bounds the maximum
     * amount of time before a mesage re-delivery in the event the Subscriber fails to extend the
     * deadline.
     *
     * <p>MaxDurationPerAckExtension configuration can be disabled by specifying a zero duration.
     */
    public Builder setMaxDurationPerAckExtension(Duration maxDurationPerAckExtension) {
      Preconditions.checkArgument(maxDurationPerAckExtension.toMillis() >= 0);
      this.maxDurationPerAckExtension = maxDurationPerAckExtension;
      return this;
    }

    /**
     * Gives the ability to set a custom executor. {@link ExecutorProvider#getExecutor()} will be
     * called {@link Builder#parallelPullCount} times.
     */
    public Builder setExecutorProvider(ExecutorProvider executorProvider) {
      this.executorProvider = Preconditions.checkNotNull(executorProvider);
      return this;
    }

    /** {@code CredentialsProvider} to use to create Credentials to authenticate calls. */
    public Builder setCredentialsProvider(CredentialsProvider credentialsProvider) {
      this.credentialsProvider = Preconditions.checkNotNull(credentialsProvider);
      return this;
    }

    /**
     * Gives the ability to set a custom executor for managing lease extensions. If none is provided
     * a shared one will be used by all {@link Subscriber} instances.
     */
    public Builder setSystemExecutorProvider(ExecutorProvider executorProvider) {
      this.systemExecutorProvider = Preconditions.checkNotNull(executorProvider);
      return this;
    }

    /**
     * Sets the number of StreamingPull streams to pull messages from the subscription. Defaults to
     * one.
     */
    public Builder setParallelPullCount(int parallelPullCount) {
      this.parallelPullCount = parallelPullCount;
      return this;
    }

    /** Gives the ability to override the gRPC endpoint. */
    public Builder setEndpoint(String endpoint) {
      this.endpoint = endpoint;
      return this;
    }

    /** Gives the ability to set a custom clock. */
    Builder setClock(ApiClock clock) {
      this.clock = Optional.of(clock);
      return this;
    }

    /** Returns the default FlowControlSettings used by the client if settings are not provided. */
    public static FlowControlSettings getDefaultFlowControlSettings() {
      return DEFAULT_FLOW_CONTROL_SETTINGS;
    }

    public Subscriber build() {
      if (systemExecutorProvider == null) {
        ThreadFactory threadFactory =
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("Subscriber-SE-" + SYSTEM_EXECUTOR_COUNTER.incrementAndGet() + "-%d")
                .build();
        int threadCount = Math.max(6, 2 * parallelPullCount);
        final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(threadCount, threadFactory);
        systemExecutorProvider =
            new ExecutorProvider() {
              @Override
              public boolean shouldAutoClose() {
                return true;
              }

              @Override
              public ScheduledExecutorService getExecutor() {
                return executor;
              }
            };
      }
      return new Subscriber(this);
    }
  }
}
