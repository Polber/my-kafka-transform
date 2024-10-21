/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.example;

import com.google.auto.service.AutoService;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import org.apache.beam.sdk.io.kafka.KafkaReadSchemaTransformConfiguration;
import org.apache.beam.sdk.schemas.transforms.SchemaTransform;
import org.apache.beam.sdk.schemas.transforms.SchemaTransformProvider;
import org.apache.beam.sdk.io.kafka.KafkaReadSchemaTransformProvider;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@AutoService(SchemaTransformProvider.class)
public class CustomKafkaReadSchemaTransformProvider extends KafkaReadSchemaTransformProvider {

  @Override
  protected @NonNull SchemaTransform from(@NonNull KafkaReadSchemaTransformConfiguration configuration) {

    // Replace secrets in consumer config with actual values
    if (configuration.getConsumerConfigUpdates() != null) {
      parseConsumerConfigUpdates(configuration.getConsumerConfigUpdates());
    }

    return super.from(configuration);
  }

  /**
   * Helper method to replace values in Consumer Config that match
   * "{secret projects/12345/secrets/secretName/versions/1}", and replace with the actual
   * secret value.
   *
   * @param consumerConfigUpdates map of consumer config updates.
   */
  private static void parseConsumerConfigUpdates(Map<String, String> consumerConfigUpdates) {

    // Parse each consumer config update
    for (String consumerConfigKey : consumerConfigUpdates.keySet()) {

      // Look for values that match "{secret projects/12345/secrets/secretName/versions/1}"
      Pattern pattern = Pattern.compile("\\{secret projects/[0-9]+/secrets/[\\w-]+/versions/[0-9]+}", Pattern.CASE_INSENSITIVE);
      Matcher matcher = pattern.matcher(consumerConfigUpdates.get(consumerConfigKey));

      // Loop over each match and pull secret from Google Secret Manager
      String newValue = consumerConfigUpdates.get(consumerConfigKey);
      while (matcher.find()) {
        String secretString = consumerConfigUpdates.get(consumerConfigKey).substring(matcher.start() + 8, matcher.end() - 1);

        String projectId = secretString.split("/")[1];
        String secretName = secretString.split("/")[3];
        String secretVersion = secretString.split("/")[5];

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
          SecretVersion addedVersion = client.getSecretVersion(
              SecretVersionName.of(ProjectName.of(projectId).getProject(), secretName, secretVersion));

          // Access the secret version.
          AccessSecretVersionResponse response = client.accessSecretVersion(addedVersion.getName());

          // Replace original string with secret value
          String data = response.getPayload().getData().toStringUtf8();
          newValue = newValue.replace(matcher.group(), data);

        } catch (IOException e) {
          throw new RuntimeException("Failed to parse secret: ", e);
        }
      }

      consumerConfigUpdates.put(consumerConfigKey, newValue);
    }
  }

  @Override
  public @NonNull String identifier() {
    return "my-kafka-transform:schematransform:org.example:custom_kafka_read:v1";
  }
}
