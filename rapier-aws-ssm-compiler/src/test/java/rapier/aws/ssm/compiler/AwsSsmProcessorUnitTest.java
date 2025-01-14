/*-
 * =================================LICENSE_START==================================
 * rapier-aws-ssm-compiler
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
package rapier.aws.ssm.compiler;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import rapier.compiler.core.RapierTestBase;
import rapier.compiler.core.util.Maven;

/**
 * These tests run against a mock implementation of the AWS Java SDK v2 SSM client. This allows us
 * to test the code generation of the AWS SSM processor using a very controlled environment. We have
 * {@link AwsSsmProcessorLocalStackTest a separate set of tests} that uses a live AWS Java SDK v2
 * client against LocalStack for a more realistic test.
 */
public class AwsSsmProcessorUnitTest extends RapierTestBase {
  @Test
  public void givenComponentWithOneRequiredParameterThatExistsValue_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    // Define the source file to test
    final JavaFileObject componentSource = prepareSourceFile("""
        @dagger.Component(modules={RapierExampleComponentAwsSsmModule.class})
        public interface ExampleComponent {
            @rapier.aws.ssm.AwsSsmStringParameter(value="foo.bar")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject clientStubSource =
        prepareSourceFile(generateMockSsmClientSourceCode(new AwsSsmClientMethodStubGenerator() {
          @Override
          public void generateGetParameterOfGetParameterRequestMethodStub(PrintWriter out) {
            out.println("        return GetParameterResponse.builder()");
            out.println("            .parameter(Parameter.builder()");
            out.println("                .name(request.name())");
            out.println("                .value(\"42\")");
            out.println("                .build())");
            out.println("            .build();");
          }
        }));

    final JavaFileObject appSource = prepareSourceFile("""
        import java.util.Map;
        import java.net.URI;
        import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
        import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
        import software.amazon.awssdk.regions.Region;
        import software.amazon.awssdk.services.ssm.SsmClient;

        public class App {
            public static void main(String[] args) {
                final SsmClient client = new MockSsmClient();

                ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentAwsSsmModule(
                        new RapierExampleComponentAwsSsmModule(client))
                    .build();
                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    final Compilation compilation = doCompile(componentSource, clientStubSource, appSource);

    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("42", output);
  }

  @Test
  public void givenComponentWithOneRequiredParameterThatDoesNotExistValue_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        @dagger.Component(modules={RapierExampleComponentAwsSsmModule.class})
        public interface ExampleComponent {
            @rapier.aws.ssm.AwsSsmStringParameter(value="foo.bar")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject clientStubSource =
        prepareSourceFile(generateMockSsmClientSourceCode(new AwsSsmClientMethodStubGenerator() {
          @Override
          public void generateGetParameterOfGetParameterRequestMethodStub(PrintWriter out) {
            out.println("        throw software.amazon.awssdk.services.ssm.model.ParameterNotFoundException.builder()");
            out.println("            .message(request.name())");
            out.println("            .build();");
          }
        }));

    final JavaFileObject appSource = prepareSourceFile("""
        import java.util.Map;
        import java.net.URI;
        import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
        import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
        import software.amazon.awssdk.regions.Region;
        import software.amazon.awssdk.services.ssm.SsmClient;

        public class App {
            public static void main(String[] args) {
                final SsmClient client = new MockSsmClient();

                try {
                    ExampleComponent component = DaggerExampleComponent.builder()
                        .rapierExampleComponentAwsSsmModule(
                            new RapierExampleComponentAwsSsmModule(client))
                        .build();
                    System.out.println(component.provisionFooBarAsInt());
                } catch (Exception e) {
                    System.out.println(e.getClass().getName());
                }
            }
        }
        """);

    final Compilation compilation = doCompile(componentSource, clientStubSource, appSource);

    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("java.lang.IllegalStateException", output);
  }
  
  @Test
  public void givenComponentWithOneNullableParameterThatExistsValue_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    // Define the source file to test
    final JavaFileObject componentSource = prepareSourceFile("""
        @dagger.Component(modules={RapierExampleComponentAwsSsmModule.class})
        public interface ExampleComponent {
            @javax.annotation.Nullable
            @rapier.aws.ssm.AwsSsmStringParameter(value="foo.bar")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject clientStubSource =
        prepareSourceFile(generateMockSsmClientSourceCode(new AwsSsmClientMethodStubGenerator() {
          @Override
          public void generateGetParameterOfGetParameterRequestMethodStub(PrintWriter out) {
            out.println("        return GetParameterResponse.builder()");
            out.println("            .parameter(Parameter.builder()");
            out.println("                .name(request.name())");
            out.println("                .value(\"42\")");
            out.println("                .build())");
            out.println("            .build();");
          }
        }));

    final JavaFileObject appSource = prepareSourceFile("""
        import java.util.Map;
        import java.net.URI;
        import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
        import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
        import software.amazon.awssdk.regions.Region;
        import software.amazon.awssdk.services.ssm.SsmClient;

        public class App {
            public static void main(String[] args) {
                final SsmClient client = new MockSsmClient();

                ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentAwsSsmModule(
                        new RapierExampleComponentAwsSsmModule(client))
                    .build();
                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    final Compilation compilation = doCompile(componentSource, clientStubSource, appSource);

    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("42", output);
  }
  
  @Test
  public void givenComponentWithOneNullableParameterThatDoesNotExistValue_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    // Define the source file to test
    final JavaFileObject componentSource = prepareSourceFile("""
        @dagger.Component(modules={RapierExampleComponentAwsSsmModule.class})
        public interface ExampleComponent {
            @javax.annotation.Nullable
            @rapier.aws.ssm.AwsSsmStringParameter(value="foo.bar")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject clientStubSource =
        prepareSourceFile(generateMockSsmClientSourceCode(new AwsSsmClientMethodStubGenerator() {
          @Override
          public void generateGetParameterOfGetParameterRequestMethodStub(PrintWriter out) {
            out.println("        throw software.amazon.awssdk.services.ssm.model.ParameterNotFoundException.builder()");
            out.println("            .message(request.name())");
            out.println("            .build();");
          }
        }));

    final JavaFileObject appSource = prepareSourceFile("""
        import java.util.Map;
        import java.net.URI;
        import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
        import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
        import software.amazon.awssdk.regions.Region;
        import software.amazon.awssdk.services.ssm.SsmClient;

        public class App {
            public static void main(String[] args) {
                final SsmClient client = new MockSsmClient();

                ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentAwsSsmModule(
                        new RapierExampleComponentAwsSsmModule(client))
                    .build();
                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    final Compilation compilation = doCompile(componentSource, clientStubSource, appSource);

    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("null", output);
  }
  
  @Test
  public void givenComponentWithParameterWithEnvNameTemplate_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    // Define the source file to test
    final JavaFileObject componentSource = prepareSourceFile("""
        @dagger.Component(modules={RapierExampleComponentAwsSsmModule.class})
        public interface ExampleComponent {
            @rapier.aws.ssm.AwsSsmStringParameter(value="foo.${env.QUUX}")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject clientStubSource =
        prepareSourceFile(generateMockSsmClientSourceCode(new AwsSsmClientMethodStubGenerator() {
          @Override
          public void generateGetParameterOfGetParameterRequestMethodStub(PrintWriter out) {
            out.println("        if(!request.name().equals(\"foo.bar\")) {");
            out.println("            throw software.amazon.awssdk.services.ssm.model.ParameterNotFoundException.builder()");
            out.println("                .message(request.name())");
            out.println("                .build();");
            out.println("        }");
            out.println("        return GetParameterResponse.builder()");
            out.println("            .parameter(Parameter.builder()");
            out.println("                .name(request.name())");
            out.println("                .value(\"42\")");
            out.println("                .build())");
            out.println("            .build();");
          }
        }));

    final JavaFileObject appSource = prepareSourceFile("""
        import java.util.Map;
        import java.net.URI;
        import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
        import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
        import software.amazon.awssdk.regions.Region;
        import software.amazon.awssdk.services.ssm.SsmClient;

        public class App {
            public static void main(String[] args) {
                final SsmClient client = new MockSsmClient();

                ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentAwsSsmModule(
                        new RapierExampleComponentAwsSsmModule(
                            client,
                            Map.of("QUUX", "bar"),
                            Map.of()))
                    .build();
                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    final Compilation compilation = doCompile(componentSource, clientStubSource, appSource);

    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("42", output);
  }  
  
  @Test
  public void givenComponentWithParameterWithSysNameTemplate_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    // Define the source file to test
    final JavaFileObject componentSource = prepareSourceFile("""
        @dagger.Component(modules={RapierExampleComponentAwsSsmModule.class})
        public interface ExampleComponent {
            @rapier.aws.ssm.AwsSsmStringParameter(value="foo.${sys.QUUX}")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject clientStubSource =
        prepareSourceFile(generateMockSsmClientSourceCode(new AwsSsmClientMethodStubGenerator() {
          @Override
          public void generateGetParameterOfGetParameterRequestMethodStub(PrintWriter out) {
            out.println("        if(!request.name().equals(\"foo.bar\")) {");
            out.println("            throw software.amazon.awssdk.services.ssm.model.ParameterNotFoundException.builder()");
            out.println("                .message(request.name())");
            out.println("                .build();");
            out.println("        }");
            out.println("        return GetParameterResponse.builder()");
            out.println("            .parameter(Parameter.builder()");
            out.println("                .name(request.name())");
            out.println("                .value(\"42\")");
            out.println("                .build())");
            out.println("            .build();");
          }
        }));

    final JavaFileObject appSource = prepareSourceFile("""
        import java.util.Map;
        import java.net.URI;
        import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
        import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
        import software.amazon.awssdk.regions.Region;
        import software.amazon.awssdk.services.ssm.SsmClient;

        public class App {
            public static void main(String[] args) {
                final SsmClient client = new MockSsmClient();

                ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentAwsSsmModule(
                        new RapierExampleComponentAwsSsmModule(
                            client,
                            Map.of(),
                            Map.of("QUUX", "bar")))
                    .build();
                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    final Compilation compilation = doCompile(componentSource, clientStubSource, appSource);

    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("42", output);
  }  
  
  private static final String AWS_SDK_VERSION =
      Optional.ofNullable(System.getProperty("maven.awssdk.version")).orElseThrow(
          () -> new IllegalStateException("maven.awssdk.version system property not set"));

  @Override
  protected List<File> getCompileClasspath() throws FileNotFoundException {
    final List<File> result = new ArrayList<>(super.getCompileClasspath());

    // We need a few AWS SDK JARs to be available for the mock
    result.add(Maven.findJarInLocalRepository("software.amazon.awssdk", "ssm", AWS_SDK_VERSION));
    result
        .add(Maven.findJarInLocalRepository("software.amazon.awssdk", "aws-core", AWS_SDK_VERSION));
    result
        .add(Maven.findJarInLocalRepository("software.amazon.awssdk", "sdk-core", AWS_SDK_VERSION));
    result.add(Maven.findJarInLocalRepository("software.amazon.awssdk", "utils", AWS_SDK_VERSION));
    result.add(Maven.findJarInLocalRepository("software.amazon.awssdk", "auth", AWS_SDK_VERSION));
    result
        .add(Maven.findJarInLocalRepository("software.amazon.awssdk", "regions", AWS_SDK_VERSION));

    // We need our sister project classes to be available
    result.add(resolveProjectFile("../rapier-aws-ssm/target/classes"));

    return result;
  }  

  public static interface AwsSsmClientMethodStubGenerator {
    public void generateGetParameterOfGetParameterRequestMethodStub(PrintWriter out);
  }

  protected String generateMockSsmClientSourceCode(AwsSsmClientMethodStubGenerator g) {
    final StringWriter buf = new StringWriter();
    final PrintWriter out = new PrintWriter(buf);

    out.println("import java.util.function.Consumer;");
    out.println("import software.amazon.awssdk.services.ssm.SsmClient;");
    out.println("import software.amazon.awssdk.services.ssm.model.GetParameterRequest;");
    out.println("import software.amazon.awssdk.services.ssm.model.GetParameterResponse;");
    out.println("import software.amazon.awssdk.services.ssm.model.GetParameterResponse.Builder;");
    out.println("import software.amazon.awssdk.services.ssm.model.Parameter;");
    out.println();
    out.println("public class MockSsmClient implements SsmClient {");
    out.println("    @Override");
    out.println("    public void close() {");
    out.println("        // NOP");
    out.println("    }");
    out.println();
    out.println("    @Override");
    out.println("    public String serviceName() {");
    out.println("        return \"ssm\";");
    out.println("    }");
    out.println();
    out.println("    @Override");
    out.println("    public GetParameterResponse getParameter(GetParameterRequest request) {");
    g.generateGetParameterOfGetParameterRequestMethodStub(out);
    out.println("    }");
    out.println();
    out.println("    @Override");
    out.println("    public GetParameterResponse getParameter(");
    out.println("            Consumer<GetParameterRequest.Builder> consumer) {");
    out.println("        GetParameterRequest.Builder builder = GetParameterRequest.builder();");
    out.println("        consumer.accept(builder);");
    out.println("        return getParameter(builder.build());");
    out.println("    }");
    out.println("}");

    return buf.toString();
  }
}
