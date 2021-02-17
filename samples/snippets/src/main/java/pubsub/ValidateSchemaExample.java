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

// [START pubsub_validate_schema]
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.pubsub.v1.SchemaServiceClient;
import com.google.pubsub.v1.Schema;
import com.google.pubsub.v1.SchemaName;
import java.io.File;
import java.io.IOException;
import org.apache.avro.Schema.Parser;

public class ValidateSchemaExample {
  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "your-project-id";
    String schemaId = "your-schema-id";
    String avscFile = "path/to/an/avro/schema/file/formatted/in/json";

    validateSchemaExample(projectId, schemaId, avscFile);
  }

  public static void validateSchemaExample(String projectId, String schemaId, String avscFile)
      throws IOException {
    SchemaName schemaName = SchemaName.of(projectId, schemaId);

    try (SchemaServiceClient schemaServiceClient = SchemaServiceClient.create()) {

      String avscSource = new Parser().parse(new File(avscFile)).toString();

      Schema schema =
          Schema.newBuilder()
              .setName(schemaName.toString())
              .setType(Schema.Type.AVRO)
              .setDefinition(avscSource)
              .build();

      schemaServiceClient.validateSchema(schemaName.toString(), schema);
      System.out.println("Validated an Avro schema.");
    } catch (NotFoundException e) {
      System.out.println(schemaName + "not found.");
    }
  }
}
// [END pubsub_validate_schema]
