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

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertNotNull;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.pubsub.v1.SchemaServiceClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.pubsub.v1.Encoding;
import com.google.pubsub.v1.SchemaName;
import com.google.pubsub.v1.TopicName;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class SchemaIT {
  private ByteArrayOutputStream bout;
  private PrintStream out;

  private static final String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
  private static final String _suffix = UUID.randomUUID().toString();
  private static final String topicId = "schema-topic-" + _suffix;
  private static final String subscriptionId = "schema-subscription-" + _suffix;
  private static final String schemaId = "schema-" + _suffix;
  private static final String avscFile = "src/main/resources/us-states.avsc";
  private static final String avroFile = "src/main/resources/us-states.avro";

  private static final TopicName topicName = TopicName.of(projectId, topicId);
  private static final SchemaName schemaName = SchemaName.of(projectId, schemaId);

  private static void requireEnvVar(String varName) {
    assertNotNull(
        "Environment variable " + varName + " is required to perform these tests.",
        System.getenv(varName));
  }

  @Rule public Timeout globalTimeout = Timeout.seconds(300); // 5 minute timeout

  @BeforeClass
  public static void checkRequirements() {
    requireEnvVar("GOOGLE_CLOUD_PROJECT");
  }

  @Before
  public void setUp() throws Exception {
    bout = new ByteArrayOutputStream();
    out = new PrintStream(bout);
    System.setOut(out);
  }

  @After
  public void tearDown() throws Exception {
    // Delete the schema if it has not been cleaned.
    try (SchemaServiceClient schemaServiceClient = SchemaServiceClient.create()) {
      schemaServiceClient.deleteSchema(schemaName);
    } catch (NotFoundException ignored) {
      // ignore this as resources may not have been created
    }

    // Delete the topic if it has not been cleaned.
    try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
      topicAdminClient.deleteTopic(topicName.toString());
    } catch (NotFoundException ignored) {
      // ignore this as resources may not have been created
    }
    System.setOut(null);
  }

  @Test
  public void testSchema() throws Exception {
    // Test creating Avro schema.
    CreateAvroSchemaExample.createAvroSchemaExample(projectId, schemaId, avscFile);
    assertThat(bout.toString()).contains("Created schema:");
    assertThat(bout.toString()).contains(schemaName.toString());

    bout.reset();
    // Test creating a topic with schema.
    CreateTopicWithSchemaExample.createTopicWithSchemaExample(
        projectId, topicId, schemaId, Encoding.BINARY);
    assertThat(bout.toString()).contains("Created topic with schema: " + topicName.toString());

    bout.reset();
    // Test publishing binary-encoded Avro records.
    PublishAvroRecordsExample.publishAvroRecordsExample(projectId, topicId, schemaId, avroFile);
    assertThat(bout.toString())
        .contains("Prepared to publish BINARY-encoded message to " + topicName.toString());
  }
}
