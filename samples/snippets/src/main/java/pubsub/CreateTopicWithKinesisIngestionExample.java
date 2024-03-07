/*
 * Copyright 2024 Google LLC
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

// [START pubsub_create_topic_with_kinesis_ingestion]

import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.pubsub.v1.IngestionDataSourceSettings;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;
import java.io.IOException;

public class CreateTopicWithKinesisIngestionExample {
  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "your-project-id";
    String topicId = "your-topic-id";
    // Kinesis ingestion settings.
    String streamArn = "stream-arn";
    String consumerArn = "consumer-arn";
    String awsRoleArn = "aws-role-arn";
    String gcpServiceAccount = "gcp-service-account";

    createTopicWithKinesisIngestionExample(
        projectId, topicId, streamArn, consumerArn, awsRoleArn, gcpServiceAccount);
  }

  public static void createTopicWithKinesisIngestionExample(
      String projectId,
      String topicId,
      String streamArn,
      String consumerArn,
      String awsRoleArn,
      String gcpServiceAccount)
      throws IOException {
    try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
      TopicName topicName = TopicName.of(projectId, topicId);

      IngestionDataSourceSettings.AwsKinesis awsKinesis =
          IngestionDataSourceSettings.AwsKinesis.newBuilder()
              .setStreamArn(streamArn)
              .setConsumerArn(consumerArn)
              .setAwsRoleArn(awsRoleArn)
              .setGcpServiceAccount(gcpServiceAccount)
              .build();
      IngestionDataSourceSettings ingestionDataSourceSettings =
          IngestionDataSourceSettings.newBuilder().setAwsKinesis(awsKinesis).build();

      Topic topic =
          topicAdminClient.createTopic(
              Topic.newBuilder()
                  .setName(topicName.toString())
                  .setIngestionDataSourceSettings(ingestionDataSourceSettings)
                  .build());

      System.out.println("Created topic with Kinesis ingestion settings: " + topic.getAllFields());
    }
  }
}
// [END pubsub_create_topic_with_kinesis_ingestion]
