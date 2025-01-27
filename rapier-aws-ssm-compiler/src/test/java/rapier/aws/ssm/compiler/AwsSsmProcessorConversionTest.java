/*-
 * =================================LICENSE_START==================================
 * rapier-aws-ssm-compiler
 * ====================================SECTION=====================================
 * Copyright (C) 2024 - 2025 aleph0
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
 * Validate that AWS SSM processor supports expected type conversion rules
 */
public class AwsSsmProcessorConversionTest extends RapierTestBase {
  @Test
  public void test() throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentAwsSsmModule.class})
        public interface ExampleComponent {
            @rapier.aws.ssm.AwsSsmParameter("INT")
            public Byte provisionIntAsBoxedByte();

            @rapier.aws.ssm.AwsSsmParameter("INT")
            public byte provisionIntAsByte();

            @rapier.aws.ssm.AwsSsmParameter("INT")
            public Short provisionIntAsBoxedShort();

            @rapier.aws.ssm.AwsSsmParameter("INT")
            public short provisionIntAsShort();

            @rapier.aws.ssm.AwsSsmParameter("INT")
            public Integer provisionIntAsBoxedInt();

            @rapier.aws.ssm.AwsSsmParameter("INT")
            public int provisionIntAsInt();

            @rapier.aws.ssm.AwsSsmParameter("INT")
            public Long provisionIntAsBoxedLong();

            @rapier.aws.ssm.AwsSsmParameter("INT")
            public long provisionIntAsLong();

            @rapier.aws.ssm.AwsSsmParameter("FLOAT")
            public Float provisionFloatAsBoxedFloat();

            @rapier.aws.ssm.AwsSsmParameter("FLOAT")
            public float provisionFloatAsFloat();

            @rapier.aws.ssm.AwsSsmParameter("FLOAT")
            public Double provisionFloatAsBoxedDouble();

            @rapier.aws.ssm.AwsSsmParameter("FLOAT")
            public double provisionFloatAsDouble();

            @rapier.aws.ssm.AwsSsmParameter("STRING")
            public String provisionStringAsString();

            @rapier.aws.ssm.AwsSsmParameter("STRING")
            public Character provisionStringAsBoxedChar();

            @rapier.aws.ssm.AwsSsmParameter("STRING")
            public char provisionStringAsChar();

            @rapier.aws.ssm.AwsSsmParameter("STRING")
            public FromStringExample provisionStringAsFromStringExample();

            @rapier.aws.ssm.AwsSsmParameter("STRING")
            public ValueOfExample provisionStringAsValueOfExample();

            @rapier.aws.ssm.AwsSsmParameter("STRING")
            public SingleArgumentConstructorExample
              provisionStringAsSingleArgumentConstructorExample();

            @rapier.aws.ssm.AwsSsmParameter("BOOLEAN")
            public Boolean provisionBooleanAsBoxedBoolean();

            @rapier.aws.ssm.AwsSsmParameter("BOOLEAN")
            public boolean provisionBooleanAsBoolean();
        }
        """);

    final JavaFileObject fromStringExample = prepareSourceFile("""
        package com.example;

        public class FromStringExample {
            public static FromStringExample fromString(String s) {
                return new FromStringExample(s);
            }

            private final String s;

            private FromStringExample(String s) {
                this.s = s;
            }

            @Override
            public String toString() {
                return "FromStringExample [s=" + s + "]";
            }
        }
        """);

    final JavaFileObject valueOfExample = prepareSourceFile("""
        package com.example;

        public class ValueOfExample {
            public static ValueOfExample valueOf(String s) {
                return new ValueOfExample(s);
            }

            private final String s;

            private ValueOfExample(String s) {
                this.s = s;
            }

            @Override
            public String toString() {
                return "ValueOfExample [s=" + s + "]";
            }
        }
        """);

    final JavaFileObject singleArgumentConstructorExample = prepareSourceFile("""
        package com.example;

        public class SingleArgumentConstructorExample {
            public SingleArgumentConstructorExample(String s) {
                this.s = s;
            }

            private final String s;

            @Override
            public String toString() {
                return "SingleArgumentConstructorExample [s=" + s + "]";
            }
        }
        """);

    final JavaFileObject clientStubSource =
        prepareSourceFile(generateMockSsmClientSourceCode(new AwsSsmClientMethodStubGenerator() {
          @Override
          public void generateGetParameterOfGetParameterRequestMethodStub(PrintWriter out) {
            out.println("        final String name=request.name();");
            out.println("        if (name.equals(\"INT\")) {");
            out.println("            return GetParameterResponse.builder()");
            out.println("                .parameter(Parameter.builder().value(\"123\").build())");
            out.println("                .build();");
            out.println("        }");
            out.println("        if (name.equals(\"FLOAT\")) {");
            out.println("            return GetParameterResponse.builder()");
            out.println("                .parameter(Parameter.builder().value(\"1.23\").build())");
            out.println("                .build();");
            out.println("        }");
            out.println("        if (name.equals(\"STRING\")) {");
            out.println("            return GetParameterResponse.builder()");
            out.println("                .parameter(Parameter.builder().value(\"xyz\").build())");
            out.println("                .build();");
            out.println("        }");
            out.println("        if (name.equals(\"BOOLEAN\")) {");
            out.println("            return GetParameterResponse.builder()");
            out.println("                .parameter(Parameter.builder().value(\"true\").build())");
            out.println("                .build();");
            out.println("        }");
            out.println(
                "        throw software.amazon.awssdk.services.ssm.model.ParameterNotFoundException.builder()");
            out.println("            .message(name)");
            out.println("            .build();");
          }
        }));

    final JavaFileObject appSource = prepareSourceFile("""
        package com.example;

        import java.util.Map;
        import java.net.URI;
        import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
        import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
        import software.amazon.awssdk.regions.Region;
        import software.amazon.awssdk.services.ssm.SsmClient;

        public class App {
            public static void main(String[] args) {
                final SsmClient client = new MockSsmClient();
                final ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentAwsSsmModule(
                        new RapierExampleComponentAwsSsmModule(client))
                    .build();
                System.out.println(component.provisionIntAsBoxedByte());
                System.out.println(component.provisionIntAsByte());
                System.out.println(component.provisionIntAsBoxedShort());
                System.out.println(component.provisionIntAsShort());
                System.out.println(component.provisionIntAsBoxedInt());
                System.out.println(component.provisionIntAsInt());
                System.out.println(component.provisionIntAsBoxedLong());
                System.out.println(component.provisionIntAsLong());
                System.out.println(component.provisionFloatAsBoxedFloat());
                System.out.println(component.provisionFloatAsFloat());
                System.out.println(component.provisionFloatAsBoxedDouble());
                System.out.println(component.provisionFloatAsDouble());
                System.out.println(component.provisionStringAsString());
                System.out.println(component.provisionStringAsBoxedChar());
                System.out.println(component.provisionStringAsChar());
                System.out.println(component.provisionStringAsFromStringExample());
                System.out.println(component.provisionStringAsValueOfExample());
                System.out.println(component.provisionStringAsSingleArgumentConstructorExample());
                System.out.println(component.provisionBooleanAsBoxedBoolean());
                System.out.println(component.provisionBooleanAsBoolean());
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource, fromStringExample,
        valueOfExample, singleArgumentConstructorExample, clientStubSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("""
        123
        123
        123
        123
        123
        123
        123
        123
        1.23
        1.23
        1.23
        1.23
        xyz
        x
        x
        FromStringExample [s=xyz]
        ValueOfExample [s=xyz]
        SingleArgumentConstructorExample [s=xyz]
        true
        true""", output);
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
    result.add(resolveProjectFile("../rapier-core/target/classes"));

    return result;
  }

  public static interface AwsSsmClientMethodStubGenerator {
    public void generateGetParameterOfGetParameterRequestMethodStub(PrintWriter out);
  }

  protected String generateMockSsmClientSourceCode(AwsSsmClientMethodStubGenerator g) {
    final StringWriter buf = new StringWriter();
    final PrintWriter out = new PrintWriter(buf);

    out.println("package com.example;");
    out.println();
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
