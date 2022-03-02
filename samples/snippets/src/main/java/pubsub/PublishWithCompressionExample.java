/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pubsub;

// [START pubsub_publish_with_compression]
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;

public class PublishWithCompressionExample {

  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "your-project-id";
    String topicId = "your-topic-id";
    boolean allowLogging = false; // Set to true to get the stdout logs

    if (allowLogging) {
      setUpLogs();
    }
    publishWithCompressionExample(projectId, topicId);
  }

  public static void publishWithCompressionExample(String projectId, String topicId)
      throws IOException, ExecutionException, InterruptedException {
    TopicName topicName = TopicName.of(projectId, topicId);

    Publisher publisher = null;
    try {
      // Create a publisher instance bound to the topic with compression enabled and a compression
      // bytes threshold.
      publisher =
          Publisher.newBuilder(topicName)
              .setEnableCompression(true)
              .setCompressionBytesThreshold(500)
              .build();

      ByteString data = generateData("Hello!", 2000);
      PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).build();

      // Once published, returns a server-assigned message id (unique within the topic)
      ApiFuture<String> messageIdFuture = publisher.publish(pubsubMessage);
      String messageId = messageIdFuture.get();
      System.out.println("Published compressed message, ID:" + messageId);
    } finally {
      if (publisher != null) {
        // When finished with the publisher, shutdown to free up resources.
        publisher.shutdown();
        publisher.awaitTermination(1, TimeUnit.MINUTES);
      }
    }
  }

  /** Generates data of given bytes by repeatedly concatenating a token. */
  // TODO(developer): Replace this method with your own data generation logic
  private static ByteString generateData(String token, int bytes) {
    String message = "";
    int tokenBytes = token.length();
    for (int i = 0; i < Math.floor(bytes / tokenBytes) + 1; i++) {
      message = message.concat(token);
    }
    return ByteString.copyFromUtf8(message);
  }

  /**
   * Sets up logging to observe the outbound data (and its length) over the network to analyze the
   * effectiveness of compression. A sample log line:
   * [2022-03-02] FINE [] OUTBOUND DATA: streamId=3 padding=0 endStream=true length=196 bytes=01..
   */
  private static void setUpLogs() throws IOException {
    String handlers = "handlers = java.util.logging.ConsoleHandler";
    String handlerLevelProp = "java.util.logging.ConsoleHandler.level = ALL";
    String fineProp = ".level = FINE";
    String handlerFormatterProp =
        "java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter";
    String format = "java.util.logging.SimpleFormatter.format=[%1$tF %1$tT] %4$-5s %5$s %n";

    LogManager.getLogManager()
        .readConfiguration(
            new ByteArrayInputStream(
                (handlers
                        + "\n"
                        + handlerLevelProp
                        + "\n"
                        + fineProp
                        + "\n"
                        + handlerFormatterProp
                        + "\n"
                        + format)
                    .getBytes(StandardCharsets.UTF_8)));
  }
}
// [END pubsub_publish_with_compression]
