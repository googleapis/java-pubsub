/*
 * Copyright 2023 Google LLC
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

// [START pubsub_delete_schema_revision]

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.pubsub.v1.SchemaServiceClient;
import com.google.pubsub.v1.DeleteSchemaRevisionRequest;
import com.google.pubsub.v1.SchemaName;
import java.io.IOException;

public class DeleteSchemaRevisionExample {

  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "your-project-id";
    String schemaId = "your-schema-id@your-revision-id";

    deleteSchemaRevisionExample(projectId, schemaId);
  }

  public static void deleteSchemaRevisionExample(String projectId, String schemaId)
      throws IOException {
    SchemaName schemaName = SchemaName.of(projectId, schemaId);

    try (SchemaServiceClient schemaServiceClient = SchemaServiceClient.create()) {

      DeleteSchemaRevisionRequest request =
          DeleteSchemaRevisionRequest.newBuilder().setName(schemaName.toString()).build();

      schemaServiceClient.deleteSchemaRevision(request);

      System.out.println("Deleted a schema revision:" + schemaName);

    } catch (NotFoundException e) {
      System.out.println(schemaName + "not found.");
    }
  }
}
// [END pubsub_delete_schema_revision]
