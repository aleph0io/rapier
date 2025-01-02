/*-
 * =================================LICENSE_START==================================
 * rapier-aws-ssm-processor
 * ====================================SECTION=====================================
 * Copyright (C) 2024 - 2025 Andy Boothe
 * ====================================SECTION=====================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ==================================LICENSE_END===================================
 */
package rapier.processor.aws.ssm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import rapier.core.DaggerTestBase;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.DescribeParametersResponse;

public class AwsSsmLocalStackTest extends DaggerTestBase {
  private static final DockerImageName LOCALSTACK_IMAGE =
      DockerImageName.parse("localstack/localstack:3.5.0");
  private static LocalStackContainer localstack;

  private static SsmClient client;

  @BeforeAll
  @SuppressWarnings("resource")
  public static void beforeAllAwsSsmLocalStackTest() {
    // Start LocalStack with S3 support
    localstack =
        new LocalStackContainer(LOCALSTACK_IMAGE).withServices(LocalStackContainer.Service.SSM);
    localstack.start();

    // Configure AWS SDK to use LocalStack
    client = SsmClient.builder()
        .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SSM))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
        .region(Region.of(localstack.getRegion())).build();
  }

  @AfterAll
  public static void afterAllAwsSsmLocalStackTest() {
    if (client != null)
      client.close();
    if (localstack != null)
      localstack.stop();
  }

  @Test
  public void smokeTest() {
    final DescribeParametersResponse response = client.describeParameters();
    assertEquals(0, response.parameters().size());
  }

  @Test
  public void givenComponentWithOneRequiredParameterThatExistsValue_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    client.putParameter(request -> request.name("foo.bar").type("String").value("42"));
    try {
      // Define the source file to test
      final String componentSource = """
          @dagger.Component(modules={RapierExampleComponentAwsSsmModule.class})
          public interface ExampleComponent {
              @rapier.processor.aws.ssm.AwsSsmStringParameter(value="foo.bar")
              public Integer provisionFooBarAsInt();
          }
          """;

      final String appSource =
          """
              import java.util.Map;
              import java.net.URI;
              import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
              import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
              import software.amazon.awssdk.regions.Region;
              import software.amazon.awssdk.services.ssm.SsmClient;

              public class App {
                  public static void main(String[] args) {
                      final URI endpoint = URI.create("%ENDPOINT%");
                      final String accessKey = "%ACCESS_KEY%";
                      final String secretKey = "%SECRET_KEY%";
                      final String regionName = "%REGION%";

                      final SsmClient client = SsmClient.builder()
                          .endpointOverride(endpoint)
                          .credentialsProvider(StaticCredentialsProvider.create(
                              AwsBasicCredentials.create(accessKey, secretKey)))
                          .region(Region.of(regionName)).build();

                      ExampleComponent component = DaggerExampleComponent.builder()
                          .rapierExampleComponentAwsSsmModule(new RapierExampleComponentAwsSsmModule(client))
                          .build();
                      System.out.println(component.provisionFooBarAsInt());
                  }
              }
              """
              .replace("%ENDPOINT%",
                  localstack.getEndpointOverride(LocalStackContainer.Service.SSM).toString())
              .replace("%ACCESS_KEY%", localstack.getAccessKey())
              .replace("%SECRET_KEY%", localstack.getSecretKey())
              .replace("%REGION%", localstack.getRegion());

      final String output = compileAndRunSourceCode(List.of(componentSource, appSource),
          List.of(AwsSsmProcessor.class.getName(), DAGGER_COMPONENT_ANNOTATION_PROCESSOR)).trim();

      assertEquals("42", output);
    } finally {
      client.deleteParameter(request -> request.name("foo.bar"));
    }
  }

  @Test
  public void givenComponentWithOneRequiredParameterThatDoesNotExistValue_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    // Define the source file to test
    final String componentSource = """
        @dagger.Component(modules={RapierExampleComponentAwsSsmModule.class})
        public interface ExampleComponent {
            @rapier.processor.aws.ssm.AwsSsmStringParameter(value="foo.bar")
            public Integer provisionFooBarAsInt();
        }
        """;

    final String appSource =
        """
            import java.util.Map;
            import java.net.URI;
            import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
            import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
            import software.amazon.awssdk.regions.Region;
            import software.amazon.awssdk.services.ssm.SsmClient;

            public class App {
                public static void main(String[] args) {
                    final URI endpoint = URI.create("%ENDPOINT%");
                    final String accessKey = "%ACCESS_KEY%";
                    final String secretKey = "%SECRET_KEY%";
                    final String regionName = "%REGION%";

                    final SsmClient client = SsmClient.builder()
                        .endpointOverride(endpoint)
                        .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                        .region(Region.of(regionName)).build();

                    try {
                        ExampleComponent component = DaggerExampleComponent.builder()
                            .rapierExampleComponentAwsSsmModule(new RapierExampleComponentAwsSsmModule(client))
                            .build();
                        System.out.println(component.provisionFooBarAsInt());
                    } catch (Exception e) {
                        System.out.println(e.getClass().getName());
                    }
                }
            }
            """
            .replace("%ENDPOINT%",
                localstack.getEndpointOverride(LocalStackContainer.Service.SSM).toString())
            .replace("%ACCESS_KEY%", localstack.getAccessKey())
            .replace("%SECRET_KEY%", localstack.getSecretKey())
            .replace("%REGION%", localstack.getRegion());

    final String output = compileAndRunSourceCode(List.of(componentSource, appSource),
        List.of(AwsSsmProcessor.class.getName(), DAGGER_COMPONENT_ANNOTATION_PROCESSOR)).trim();

    assertEquals("java.lang.IllegalStateException", output);
  }

  @Test
  public void givenComponentWithOneNullableParameterThatExistsValue_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    client.putParameter(request -> request.name("foo.bar").type("String").value("42"));
    try {
      // Define the source file to test
      final String componentSource = """
          @dagger.Component(modules={RapierExampleComponentAwsSsmModule.class})
          public interface ExampleComponent {
              @javax.annotation.Nullable
              @rapier.processor.aws.ssm.AwsSsmStringParameter(value="foo.bar")
              public Integer provisionFooBarAsInt();
          }
          """;

      final String appSource =
          """
              import java.util.Map;
              import java.net.URI;
              import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
              import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
              import software.amazon.awssdk.regions.Region;
              import software.amazon.awssdk.services.ssm.SsmClient;

              public class App {
                  public static void main(String[] args) {
                      final URI endpoint = URI.create("%ENDPOINT%");
                      final String accessKey = "%ACCESS_KEY%";
                      final String secretKey = "%SECRET_KEY%";
                      final String regionName = "%REGION%";

                      final SsmClient client = SsmClient.builder()
                          .endpointOverride(endpoint)
                          .credentialsProvider(StaticCredentialsProvider.create(
                              AwsBasicCredentials.create(accessKey, secretKey)))
                          .region(Region.of(regionName)).build();

                      ExampleComponent component = DaggerExampleComponent.builder()
                          .rapierExampleComponentAwsSsmModule(new RapierExampleComponentAwsSsmModule(client))
                          .build();
                      System.out.println(component.provisionFooBarAsInt());
                  }
              }
              """
              .replace("%ENDPOINT%",
                  localstack.getEndpointOverride(LocalStackContainer.Service.SSM).toString())
              .replace("%ACCESS_KEY%", localstack.getAccessKey())
              .replace("%SECRET_KEY%", localstack.getSecretKey())
              .replace("%REGION%", localstack.getRegion());

      final String output = compileAndRunSourceCode(List.of(componentSource, appSource),
          List.of(AwsSsmProcessor.class.getName(), DAGGER_COMPONENT_ANNOTATION_PROCESSOR)).trim();

      assertEquals("42", output);
    } finally {
      client.deleteParameter(request -> request.name("foo.bar"));
    }
  }

  @Test
  public void givenComponentWithOneNullableParameterThatDoesNotExistValue_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    // Define the source file to test
    final String componentSource = """
        @dagger.Component(modules={RapierExampleComponentAwsSsmModule.class})
        public interface ExampleComponent {
            @javax.annotation.Nullable
            @rapier.processor.aws.ssm.AwsSsmStringParameter(value="foo.bar")
            public Integer provisionFooBarAsInt();
        }
        """;

    final String appSource =
        """
            import java.util.Map;
            import java.net.URI;
            import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
            import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
            import software.amazon.awssdk.regions.Region;
            import software.amazon.awssdk.services.ssm.SsmClient;

            public class App {
                public static void main(String[] args) {
                    final URI endpoint = URI.create("%ENDPOINT%");
                    final String accessKey = "%ACCESS_KEY%";
                    final String secretKey = "%SECRET_KEY%";
                    final String regionName = "%REGION%";

                    final SsmClient client = SsmClient.builder()
                        .endpointOverride(endpoint)
                        .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                        .region(Region.of(regionName)).build();

                    ExampleComponent component = DaggerExampleComponent.builder()
                        .rapierExampleComponentAwsSsmModule(new RapierExampleComponentAwsSsmModule(client))
                        .build();
                    System.out.println(component.provisionFooBarAsInt());
                }
            }
            """
            .replace("%ENDPOINT%",
                localstack.getEndpointOverride(LocalStackContainer.Service.SSM).toString())
            .replace("%ACCESS_KEY%", localstack.getAccessKey())
            .replace("%SECRET_KEY%", localstack.getSecretKey())
            .replace("%REGION%", localstack.getRegion());

    final String output = compileAndRunSourceCode(List.of(componentSource, appSource),
        List.of(AwsSsmProcessor.class.getName(), DAGGER_COMPONENT_ANNOTATION_PROCESSOR)).trim();

    assertEquals("null", output);
  }
}
