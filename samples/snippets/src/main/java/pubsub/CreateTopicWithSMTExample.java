/*
 * Copyright 2025 Google LLC
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

// [START pubsub_create_topic_with_smt]

import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.pubsub.v1.JavaScriptUDF;
import com.google.pubsub.v1.MessageTransform;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;
import java.io.IOException;

public class CreateTopicWithSMTExample {

  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "your-project-id";
    String topicId = "your-topic-id";

    createTopicWithSMTExample(projectId, topicId);
  }

  public static void createTopicWithSMTExample(
      String projectId, String topicId) throws IOException {
    TopicName topicName = TopicName.of(projectId, topicId);

    // UDF that removes the 'ssn' field, if present
    String code = "function redactSSN(message, metadata) {" +
                  "  const data = JSON.parse(message.data);" +
                  "  delete data['ssn'];" +
                  "  message.data = JSON.stringify(data);" +
                  "  return message;" +
                  "}";
    String functionName = "redactSSN";

    JavaScriptUDF udf = JavaScriptUDF.newBuilder().setCode(code).setFunctionName(functionName).build();
    MessageTransform transform = MessageTransform.newBuilder().setJavascriptUdf(udf).build();
    try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {

      Topic topic =
          topicAdminClient.createTopic(
              Topic.newBuilder()
                  .setName(topicName.toString())
                  // Add the UDF message transform
                  .addMessageTransforms(transform)
                  .build());

      System.out.println("Created topic with SMT: " + topic.getName());
    } catch (AlreadyExistsException e) {
      System.out.println(topicName + "already exists.");
    }
  }
}
// [END pubsub_create_topic_with_smt]
