/*
 * Copyright 2021 Google LLC
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

// [START pubsub_subscribe_avro_records]
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.Encoding;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificData;

public class SubscribeWithAvroSchemaExample {
  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "your-project-id";
    // Use a topic that has a schema attached.
    String topicId = "your-topic-id";
    // Use a subscription attached to your topic.
    String subscriptionId = "your-subscription-id";

    subscribeWithAvroSchemaExample(projectId, topicId, subscriptionId);
  }

  public static void subscribeWithAvroSchemaExample(String projectId, String topicId, String subscriptionId)
      throws IOException {

    TopicName topicName = TopicName.of(projectId, topicId);
    ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId);

    // Get the topic encoding type.
    Encoding encoding = null;
    try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
      encoding = topicAdminClient.getTopic(topicName).getSchemaSettings().getEncoding();
    }
    Encoding finalEncoding = encoding;

    // Prepare a reader for incoming Avro records.
    GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(
        State.getClassSchema()
    );

    // Instantiate an asynchronous message receiver.
    MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {

      ByteString data = message.getData();

      // Send the message data to a byte[] input stream.
      InputStream inputStream = new ByteArrayInputStream(data.toByteArray());

      Decoder decoder = null;

      // Prepare an appropriate decoder for messages in this subscription.
      block: try {
        switch (finalEncoding) {
          case BINARY:
            decoder = DecoderFactory.get().directBinaryDecoder(inputStream, /*reuse=*/null);
            System.out.println("Receiving a binary-encoded message:");
            break;
          case ENCODING_UNSPECIFIED:
          case UNRECOGNIZED:
            break block;
          case JSON:
            decoder = DecoderFactory.get().jsonDecoder(State.getClassSchema(), inputStream);
            System.out.println("Receiving a JSON-encoded message:");
            break;
        }

        // Obtain a generic Avro record using the decoder.
        GenericRecord record = reader.read(null, decoder);

        // Cast the generic Avro record to the avro-tools-generated class.
        State state = (State) SpecificData.get().deepCopy(State.getClassSchema(), record);
        System.out.println(state.getName() + "-" + state.getPostAbbr());

      } catch (IOException e) {
        System.err.println(e);
      }

      // Ack the message.
      consumer.ack();
    };

    Subscriber subscriber = null;
    try {
      subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
      subscriber.startAsync().awaitRunning();
      System.out.printf("Listening for messages on %s:\n", subscriptionName.toString());
      subscriber.awaitTerminated(30, TimeUnit.SECONDS);
    } catch (TimeoutException timeoutException) {
      subscriber.stopAsync();
    }
  }
}
// [END pubsub_subscribe_avro_records]
