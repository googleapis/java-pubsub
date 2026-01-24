/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.pubsub.v1;

import static com.google.cloud.pubsub.v1.SchemaServiceClient.ListSchemaRevisionsPagedResponse;
import static com.google.cloud.pubsub.v1.SchemaServiceClient.ListSchemasPagedResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GaxGrpcProperties;
import com.google.api.gax.grpc.testing.LocalChannelProvider;
import com.google.api.gax.grpc.testing.MockGrpcService;
import com.google.api.gax.grpc.testing.MockServiceHelper;
import com.google.api.gax.rpc.ApiClientHeaderProvider;
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.common.collect.Lists;
import com.google.iam.v1.AuditConfig;
import com.google.iam.v1.Binding;
import com.google.iam.v1.GetIamPolicyRequest;
import com.google.iam.v1.GetPolicyOptions;
import com.google.iam.v1.Policy;
import com.google.iam.v1.SetIamPolicyRequest;
import com.google.iam.v1.TestIamPermissionsRequest;
import com.google.iam.v1.TestIamPermissionsResponse;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;
import com.google.pubsub.v1.CommitSchemaRequest;
import com.google.pubsub.v1.CreateSchemaRequest;
import com.google.pubsub.v1.DeleteSchemaRequest;
import com.google.pubsub.v1.DeleteSchemaRevisionRequest;
import com.google.pubsub.v1.Encoding;
import com.google.pubsub.v1.GetSchemaRequest;
import com.google.pubsub.v1.ListSchemaRevisionsRequest;
import com.google.pubsub.v1.ListSchemaRevisionsResponse;
import com.google.pubsub.v1.ListSchemasRequest;
import com.google.pubsub.v1.ListSchemasResponse;
import com.google.pubsub.v1.ProjectName;
import com.google.pubsub.v1.RollbackSchemaRequest;
import com.google.pubsub.v1.Schema;
import com.google.pubsub.v1.SchemaName;
import com.google.pubsub.v1.ValidateMessageRequest;
import com.google.pubsub.v1.ValidateMessageResponse;
import com.google.pubsub.v1.ValidateSchemaRequest;
import com.google.pubsub.v1.ValidateSchemaResponse;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.annotation.Generated;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Generated("by gapic-generator-java")
public class SchemaServiceClientTest {
  private static MockIAMPolicy mockIAMPolicy;
  private static MockSchemaService mockSchemaService;
  private static MockServiceHelper mockServiceHelper;
  private LocalChannelProvider channelProvider;
  private SchemaServiceClient client;

  @BeforeAll
  public static void startStaticServer() {
    mockSchemaService = new MockSchemaService();
    mockIAMPolicy = new MockIAMPolicy();
    mockServiceHelper =
        new MockServiceHelper(
            UUID.randomUUID().toString(),
            Arrays.<MockGrpcService>asList(mockSchemaService, mockIAMPolicy));
    mockServiceHelper.start();
  }

  @AfterAll
  public static void stopServer() {
    mockServiceHelper.stop();
  }

  @BeforeEach
  public void setUp() throws IOException {
    mockServiceHelper.reset();
    channelProvider = mockServiceHelper.createChannelProvider();
    SchemaServiceSettings settings =
        SchemaServiceSettings.newBuilder()
            .setTransportChannelProvider(channelProvider)
            .setCredentialsProvider(NoCredentialsProvider.create())
            .build();
    client = SchemaServiceClient.create(settings);
  }

  @AfterEach
  public void tearDown() throws Exception {
    client.close();
  }

  @Test
  public void createSchemaTest() throws Exception {
    Schema expectedResponse =
        Schema.newBuilder()
            .setName(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
            .setDefinition("definition-1014418093")
            .setRevisionId("revisionId-1507445162")
            .setRevisionCreateTime(Timestamp.newBuilder().build())
            .build();
    mockSchemaService.addResponse(expectedResponse);

    ProjectName parent = ProjectName.of("[PROJECT]");
    Schema schema = Schema.newBuilder().build();
    String schemaId = "schemaId-697673060";

    Schema actualResponse = client.createSchema(parent, schema, schemaId);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    CreateSchemaRequest actualRequest = ((CreateSchemaRequest) actualRequests.get(0));

    assertEquals(parent.toString(), actualRequest.getParent());
    assertEquals(schema, actualRequest.getSchema());
    assertEquals(schemaId, actualRequest.getSchemaId());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void createSchemaExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      ProjectName parent = ProjectName.of("[PROJECT]");
      Schema schema = Schema.newBuilder().build();
      String schemaId = "schemaId-697673060";
      client.createSchema(parent, schema, schemaId);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void createSchemaTest2() throws Exception {
    Schema expectedResponse =
        Schema.newBuilder()
            .setName(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
            .setDefinition("definition-1014418093")
            .setRevisionId("revisionId-1507445162")
            .setRevisionCreateTime(Timestamp.newBuilder().build())
            .build();
    mockSchemaService.addResponse(expectedResponse);

    String parent = "parent-995424086";
    Schema schema = Schema.newBuilder().build();
    String schemaId = "schemaId-697673060";

    Schema actualResponse = client.createSchema(parent, schema, schemaId);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    CreateSchemaRequest actualRequest = ((CreateSchemaRequest) actualRequests.get(0));

    assertEquals(parent, actualRequest.getParent());
    assertEquals(schema, actualRequest.getSchema());
    assertEquals(schemaId, actualRequest.getSchemaId());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void createSchemaExceptionTest2() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      String parent = "parent-995424086";
      Schema schema = Schema.newBuilder().build();
      String schemaId = "schemaId-697673060";
      client.createSchema(parent, schema, schemaId);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void getSchemaTest() throws Exception {
    Schema expectedResponse =
        Schema.newBuilder()
            .setName(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
            .setDefinition("definition-1014418093")
            .setRevisionId("revisionId-1507445162")
            .setRevisionCreateTime(Timestamp.newBuilder().build())
            .build();
    mockSchemaService.addResponse(expectedResponse);

    SchemaName name = SchemaName.of("[PROJECT]", "[SCHEMA]");

    Schema actualResponse = client.getSchema(name);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    GetSchemaRequest actualRequest = ((GetSchemaRequest) actualRequests.get(0));

    assertEquals(name.toString(), actualRequest.getName());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void getSchemaExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      SchemaName name = SchemaName.of("[PROJECT]", "[SCHEMA]");
      client.getSchema(name);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void getSchemaTest2() throws Exception {
    Schema expectedResponse =
        Schema.newBuilder()
            .setName(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
            .setDefinition("definition-1014418093")
            .setRevisionId("revisionId-1507445162")
            .setRevisionCreateTime(Timestamp.newBuilder().build())
            .build();
    mockSchemaService.addResponse(expectedResponse);

    String name = "name3373707";

    Schema actualResponse = client.getSchema(name);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    GetSchemaRequest actualRequest = ((GetSchemaRequest) actualRequests.get(0));

    assertEquals(name, actualRequest.getName());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void getSchemaExceptionTest2() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      String name = "name3373707";
      client.getSchema(name);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void listSchemasTest() throws Exception {
    Schema responsesElement = Schema.newBuilder().build();
    ListSchemasResponse expectedResponse =
        ListSchemasResponse.newBuilder()
            .setNextPageToken("")
            .addAllSchemas(Arrays.asList(responsesElement))
            .build();
    mockSchemaService.addResponse(expectedResponse);

    ProjectName parent = ProjectName.of("[PROJECT]");

    ListSchemasPagedResponse pagedListResponse = client.listSchemas(parent);

    List<Schema> resources = Lists.newArrayList(pagedListResponse.iterateAll());

    assertEquals(1, resources.size());
    assertEquals(expectedResponse.getSchemasList().get(0), resources.get(0));

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    ListSchemasRequest actualRequest = ((ListSchemasRequest) actualRequests.get(0));

    assertEquals(parent.toString(), actualRequest.getParent());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void listSchemasExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      ProjectName parent = ProjectName.of("[PROJECT]");
      client.listSchemas(parent);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void listSchemasTest2() throws Exception {
    Schema responsesElement = Schema.newBuilder().build();
    ListSchemasResponse expectedResponse =
        ListSchemasResponse.newBuilder()
            .setNextPageToken("")
            .addAllSchemas(Arrays.asList(responsesElement))
            .build();
    mockSchemaService.addResponse(expectedResponse);

    String parent = "parent-995424086";

    ListSchemasPagedResponse pagedListResponse = client.listSchemas(parent);

    List<Schema> resources = Lists.newArrayList(pagedListResponse.iterateAll());

    assertEquals(1, resources.size());
    assertEquals(expectedResponse.getSchemasList().get(0), resources.get(0));

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    ListSchemasRequest actualRequest = ((ListSchemasRequest) actualRequests.get(0));

    assertEquals(parent, actualRequest.getParent());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void listSchemasExceptionTest2() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      String parent = "parent-995424086";
      client.listSchemas(parent);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void listSchemaRevisionsTest() throws Exception {
    Schema responsesElement = Schema.newBuilder().build();
    ListSchemaRevisionsResponse expectedResponse =
        ListSchemaRevisionsResponse.newBuilder()
            .setNextPageToken("")
            .addAllSchemas(Arrays.asList(responsesElement))
            .build();
    mockSchemaService.addResponse(expectedResponse);

    SchemaName name = SchemaName.of("[PROJECT]", "[SCHEMA]");

    ListSchemaRevisionsPagedResponse pagedListResponse = client.listSchemaRevisions(name);

    List<Schema> resources = Lists.newArrayList(pagedListResponse.iterateAll());

    assertEquals(1, resources.size());
    assertEquals(expectedResponse.getSchemasList().get(0), resources.get(0));

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    ListSchemaRevisionsRequest actualRequest = ((ListSchemaRevisionsRequest) actualRequests.get(0));

    assertEquals(name.toString(), actualRequest.getName());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void listSchemaRevisionsExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      SchemaName name = SchemaName.of("[PROJECT]", "[SCHEMA]");
      client.listSchemaRevisions(name);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void listSchemaRevisionsTest2() throws Exception {
    Schema responsesElement = Schema.newBuilder().build();
    ListSchemaRevisionsResponse expectedResponse =
        ListSchemaRevisionsResponse.newBuilder()
            .setNextPageToken("")
            .addAllSchemas(Arrays.asList(responsesElement))
            .build();
    mockSchemaService.addResponse(expectedResponse);

    String name = "name3373707";

    ListSchemaRevisionsPagedResponse pagedListResponse = client.listSchemaRevisions(name);

    List<Schema> resources = Lists.newArrayList(pagedListResponse.iterateAll());

    assertEquals(1, resources.size());
    assertEquals(expectedResponse.getSchemasList().get(0), resources.get(0));

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    ListSchemaRevisionsRequest actualRequest = ((ListSchemaRevisionsRequest) actualRequests.get(0));

    assertEquals(name, actualRequest.getName());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void listSchemaRevisionsExceptionTest2() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      String name = "name3373707";
      client.listSchemaRevisions(name);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void commitSchemaTest() throws Exception {
    Schema expectedResponse =
        Schema.newBuilder()
            .setName(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
            .setDefinition("definition-1014418093")
            .setRevisionId("revisionId-1507445162")
            .setRevisionCreateTime(Timestamp.newBuilder().build())
            .build();
    mockSchemaService.addResponse(expectedResponse);

    SchemaName name = SchemaName.of("[PROJECT]", "[SCHEMA]");
    Schema schema = Schema.newBuilder().build();

    Schema actualResponse = client.commitSchema(name, schema);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    CommitSchemaRequest actualRequest = ((CommitSchemaRequest) actualRequests.get(0));

    assertEquals(name.toString(), actualRequest.getName());
    assertEquals(schema, actualRequest.getSchema());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void commitSchemaExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      SchemaName name = SchemaName.of("[PROJECT]", "[SCHEMA]");
      Schema schema = Schema.newBuilder().build();
      client.commitSchema(name, schema);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void commitSchemaTest2() throws Exception {
    Schema expectedResponse =
        Schema.newBuilder()
            .setName(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
            .setDefinition("definition-1014418093")
            .setRevisionId("revisionId-1507445162")
            .setRevisionCreateTime(Timestamp.newBuilder().build())
            .build();
    mockSchemaService.addResponse(expectedResponse);

    String name = "name3373707";
    Schema schema = Schema.newBuilder().build();

    Schema actualResponse = client.commitSchema(name, schema);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    CommitSchemaRequest actualRequest = ((CommitSchemaRequest) actualRequests.get(0));

    assertEquals(name, actualRequest.getName());
    assertEquals(schema, actualRequest.getSchema());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void commitSchemaExceptionTest2() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      String name = "name3373707";
      Schema schema = Schema.newBuilder().build();
      client.commitSchema(name, schema);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void rollbackSchemaTest() throws Exception {
    Schema expectedResponse =
        Schema.newBuilder()
            .setName(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
            .setDefinition("definition-1014418093")
            .setRevisionId("revisionId-1507445162")
            .setRevisionCreateTime(Timestamp.newBuilder().build())
            .build();
    mockSchemaService.addResponse(expectedResponse);

    SchemaName name = SchemaName.of("[PROJECT]", "[SCHEMA]");
    String revisionId = "revisionId-1507445162";

    Schema actualResponse = client.rollbackSchema(name, revisionId);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    RollbackSchemaRequest actualRequest = ((RollbackSchemaRequest) actualRequests.get(0));

    assertEquals(name.toString(), actualRequest.getName());
    assertEquals(revisionId, actualRequest.getRevisionId());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void rollbackSchemaExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      SchemaName name = SchemaName.of("[PROJECT]", "[SCHEMA]");
      String revisionId = "revisionId-1507445162";
      client.rollbackSchema(name, revisionId);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void rollbackSchemaTest2() throws Exception {
    Schema expectedResponse =
        Schema.newBuilder()
            .setName(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
            .setDefinition("definition-1014418093")
            .setRevisionId("revisionId-1507445162")
            .setRevisionCreateTime(Timestamp.newBuilder().build())
            .build();
    mockSchemaService.addResponse(expectedResponse);

    String name = "name3373707";
    String revisionId = "revisionId-1507445162";

    Schema actualResponse = client.rollbackSchema(name, revisionId);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    RollbackSchemaRequest actualRequest = ((RollbackSchemaRequest) actualRequests.get(0));

    assertEquals(name, actualRequest.getName());
    assertEquals(revisionId, actualRequest.getRevisionId());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void rollbackSchemaExceptionTest2() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      String name = "name3373707";
      String revisionId = "revisionId-1507445162";
      client.rollbackSchema(name, revisionId);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void deleteSchemaRevisionTest() throws Exception {
    Schema expectedResponse =
        Schema.newBuilder()
            .setName(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
            .setDefinition("definition-1014418093")
            .setRevisionId("revisionId-1507445162")
            .setRevisionCreateTime(Timestamp.newBuilder().build())
            .build();
    mockSchemaService.addResponse(expectedResponse);

    SchemaName name = SchemaName.of("[PROJECT]", "[SCHEMA]");
    String revisionId = "revisionId-1507445162";

    Schema actualResponse = client.deleteSchemaRevision(name, revisionId);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    DeleteSchemaRevisionRequest actualRequest =
        ((DeleteSchemaRevisionRequest) actualRequests.get(0));

    assertEquals(name.toString(), actualRequest.getName());
    assertEquals(revisionId, actualRequest.getRevisionId());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void deleteSchemaRevisionExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      SchemaName name = SchemaName.of("[PROJECT]", "[SCHEMA]");
      String revisionId = "revisionId-1507445162";
      client.deleteSchemaRevision(name, revisionId);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void deleteSchemaRevisionTest2() throws Exception {
    Schema expectedResponse =
        Schema.newBuilder()
            .setName(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
            .setDefinition("definition-1014418093")
            .setRevisionId("revisionId-1507445162")
            .setRevisionCreateTime(Timestamp.newBuilder().build())
            .build();
    mockSchemaService.addResponse(expectedResponse);

    String name = "name3373707";
    String revisionId = "revisionId-1507445162";

    Schema actualResponse = client.deleteSchemaRevision(name, revisionId);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    DeleteSchemaRevisionRequest actualRequest =
        ((DeleteSchemaRevisionRequest) actualRequests.get(0));

    assertEquals(name, actualRequest.getName());
    assertEquals(revisionId, actualRequest.getRevisionId());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void deleteSchemaRevisionExceptionTest2() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      String name = "name3373707";
      String revisionId = "revisionId-1507445162";
      client.deleteSchemaRevision(name, revisionId);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void deleteSchemaTest() throws Exception {
    Empty expectedResponse = Empty.newBuilder().build();
    mockSchemaService.addResponse(expectedResponse);

    SchemaName name = SchemaName.of("[PROJECT]", "[SCHEMA]");

    client.deleteSchema(name);

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    DeleteSchemaRequest actualRequest = ((DeleteSchemaRequest) actualRequests.get(0));

    assertEquals(name.toString(), actualRequest.getName());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void deleteSchemaExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      SchemaName name = SchemaName.of("[PROJECT]", "[SCHEMA]");
      client.deleteSchema(name);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void deleteSchemaTest2() throws Exception {
    Empty expectedResponse = Empty.newBuilder().build();
    mockSchemaService.addResponse(expectedResponse);

    String name = "name3373707";

    client.deleteSchema(name);

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    DeleteSchemaRequest actualRequest = ((DeleteSchemaRequest) actualRequests.get(0));

    assertEquals(name, actualRequest.getName());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void deleteSchemaExceptionTest2() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      String name = "name3373707";
      client.deleteSchema(name);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void validateSchemaTest() throws Exception {
    ValidateSchemaResponse expectedResponse = ValidateSchemaResponse.newBuilder().build();
    mockSchemaService.addResponse(expectedResponse);

    ProjectName parent = ProjectName.of("[PROJECT]");
    Schema schema = Schema.newBuilder().build();

    ValidateSchemaResponse actualResponse = client.validateSchema(parent, schema);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    ValidateSchemaRequest actualRequest = ((ValidateSchemaRequest) actualRequests.get(0));

    assertEquals(parent.toString(), actualRequest.getParent());
    assertEquals(schema, actualRequest.getSchema());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void validateSchemaExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      ProjectName parent = ProjectName.of("[PROJECT]");
      Schema schema = Schema.newBuilder().build();
      client.validateSchema(parent, schema);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void validateSchemaTest2() throws Exception {
    ValidateSchemaResponse expectedResponse = ValidateSchemaResponse.newBuilder().build();
    mockSchemaService.addResponse(expectedResponse);

    String parent = "parent-995424086";
    Schema schema = Schema.newBuilder().build();

    ValidateSchemaResponse actualResponse = client.validateSchema(parent, schema);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    ValidateSchemaRequest actualRequest = ((ValidateSchemaRequest) actualRequests.get(0));

    assertEquals(parent, actualRequest.getParent());
    assertEquals(schema, actualRequest.getSchema());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void validateSchemaExceptionTest2() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      String parent = "parent-995424086";
      Schema schema = Schema.newBuilder().build();
      client.validateSchema(parent, schema);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void validateMessageTest() throws Exception {
    ValidateMessageResponse expectedResponse = ValidateMessageResponse.newBuilder().build();
    mockSchemaService.addResponse(expectedResponse);

    ValidateMessageRequest request =
        ValidateMessageRequest.newBuilder()
            .setParent(ProjectName.of("[PROJECT]").toString())
            .setMessage(ByteString.EMPTY)
            .setEncoding(Encoding.forNumber(0))
            .build();

    ValidateMessageResponse actualResponse = client.validateMessage(request);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockSchemaService.getRequests();
    assertEquals(1, actualRequests.size());
    ValidateMessageRequest actualRequest = ((ValidateMessageRequest) actualRequests.get(0));

    assertEquals(request.getParent(), actualRequest.getParent());
    assertEquals(request.getName(), actualRequest.getName());
    assertEquals(request.getSchema(), actualRequest.getSchema());
    assertEquals(request.getMessage(), actualRequest.getMessage());
    assertEquals(request.getEncoding(), actualRequest.getEncoding());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void validateMessageExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockSchemaService.addException(exception);

    try {
      ValidateMessageRequest request =
          ValidateMessageRequest.newBuilder()
              .setParent(ProjectName.of("[PROJECT]").toString())
              .setMessage(ByteString.EMPTY)
              .setEncoding(Encoding.forNumber(0))
              .build();
      client.validateMessage(request);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void setIamPolicyTest() throws Exception {
    Policy expectedResponse =
        Policy.newBuilder()
            .setVersion(351608024)
            .addAllBindings(new ArrayList<Binding>())
            .addAllAuditConfigs(new ArrayList<AuditConfig>())
            .setEtag(ByteString.EMPTY)
            .build();
    mockIAMPolicy.addResponse(expectedResponse);

    SetIamPolicyRequest request =
        SetIamPolicyRequest.newBuilder()
            .setResource(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
            .setPolicy(Policy.newBuilder().build())
            .setUpdateMask(FieldMask.newBuilder().build())
            .build();

    Policy actualResponse = client.setIamPolicy(request);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockIAMPolicy.getRequests();
    assertEquals(1, actualRequests.size());
    SetIamPolicyRequest actualRequest = ((SetIamPolicyRequest) actualRequests.get(0));

    assertEquals(request.getResource(), actualRequest.getResource());
    assertEquals(request.getPolicy(), actualRequest.getPolicy());
    assertEquals(request.getUpdateMask(), actualRequest.getUpdateMask());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void setIamPolicyExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockIAMPolicy.addException(exception);

    try {
      SetIamPolicyRequest request =
          SetIamPolicyRequest.newBuilder()
              .setResource(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
              .setPolicy(Policy.newBuilder().build())
              .setUpdateMask(FieldMask.newBuilder().build())
              .build();
      client.setIamPolicy(request);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void getIamPolicyTest() throws Exception {
    Policy expectedResponse =
        Policy.newBuilder()
            .setVersion(351608024)
            .addAllBindings(new ArrayList<Binding>())
            .addAllAuditConfigs(new ArrayList<AuditConfig>())
            .setEtag(ByteString.EMPTY)
            .build();
    mockIAMPolicy.addResponse(expectedResponse);

    GetIamPolicyRequest request =
        GetIamPolicyRequest.newBuilder()
            .setResource(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
            .setOptions(GetPolicyOptions.newBuilder().build())
            .build();

    Policy actualResponse = client.getIamPolicy(request);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockIAMPolicy.getRequests();
    assertEquals(1, actualRequests.size());
    GetIamPolicyRequest actualRequest = ((GetIamPolicyRequest) actualRequests.get(0));

    assertEquals(request.getResource(), actualRequest.getResource());
    assertEquals(request.getOptions(), actualRequest.getOptions());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void getIamPolicyExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockIAMPolicy.addException(exception);

    try {
      GetIamPolicyRequest request =
          GetIamPolicyRequest.newBuilder()
              .setResource(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
              .setOptions(GetPolicyOptions.newBuilder().build())
              .build();
      client.getIamPolicy(request);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }

  @Test
  public void testIamPermissionsTest() throws Exception {
    TestIamPermissionsResponse expectedResponse =
        TestIamPermissionsResponse.newBuilder().addAllPermissions(new ArrayList<String>()).build();
    mockIAMPolicy.addResponse(expectedResponse);

    TestIamPermissionsRequest request =
        TestIamPermissionsRequest.newBuilder()
            .setResource(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
            .addAllPermissions(new ArrayList<String>())
            .build();

    TestIamPermissionsResponse actualResponse = client.testIamPermissions(request);
    assertEquals(expectedResponse, actualResponse);

    List<AbstractMessage> actualRequests = mockIAMPolicy.getRequests();
    assertEquals(1, actualRequests.size());
    TestIamPermissionsRequest actualRequest = ((TestIamPermissionsRequest) actualRequests.get(0));

    assertEquals(request.getResource(), actualRequest.getResource());
    assertEquals(request.getPermissionsList(), actualRequest.getPermissionsList());
    assertTrue(
        channelProvider.isHeaderSent(
            ApiClientHeaderProvider.getDefaultApiClientHeaderKey(),
            GaxGrpcProperties.getDefaultApiClientHeaderPattern()));
  }

  @Test
  public void testIamPermissionsExceptionTest() throws Exception {
    StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT);
    mockIAMPolicy.addException(exception);

    try {
      TestIamPermissionsRequest request =
          TestIamPermissionsRequest.newBuilder()
              .setResource(SchemaName.of("[PROJECT]", "[SCHEMA]").toString())
              .addAllPermissions(new ArrayList<String>())
              .build();
      client.testIamPermissions(request);
      fail("No exception raised");
    } catch (InvalidArgumentException e) {
      // Expected exception.
    }
  }
}
