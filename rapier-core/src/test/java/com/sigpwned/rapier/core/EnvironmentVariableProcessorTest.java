/*-
 * =================================LICENSE_START==================================
 * rapier-core
 * ====================================SECTION=====================================
 * Copyright (C) 2024 Andy Boothe
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
package com.sigpwned.rapier.core;

import static com.google.testing.compile.CompilationSubject.assertThat;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

public class EnvironmentVariableProcessorTest {
  @Test
  public void testAnnotationProcessor() {
    // Define the source file to test
    JavaFileObject source = JavaFileObjects.forSourceString("com.example.ExampleComponent",
    // @formatter:off
      "package com.example;\n" +
      "\n" +
      "@dagger.Component\n" +
      "public interface ExampleComponent {\n" +
      "    @com.sigpwned.rapier.core.EnvironmentVariable(\"FOO_BAR\")\n" +
      "    public String getFooBar();\n" +
      "}\n");
    // @formatter:on

    // Run the annotation processor
    Compilation compilation = Compiler.javac().withProcessors(new EnvironmentVariableProcessor())
        .withOptions("-Arapier.targetPackage=com.example").compile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    // Assert generated file content
    JavaFileObject expectedOutput =
        JavaFileObjects.forSourceString("com.example.RapierEnvironmentVariableModule",
        // @formatter:off
      "package com.example;\n" +
      "\n" +
      "import static java.util.Collections.unmodifiableMap;\n" +
      "\n" +
      "import com.sigpwned.rapier.core.EnvironmentVariable;\n" +
      "import dagger.Module;\n" +
      "import dagger.Provides;\n" +
      "import java.util.Map;\n" +
      "import java.util.Optional;\n" +
      "import javax.inject.Inject;\n" +
      "\n" +
      "@Module\n" +
      "public class RapierEnvironmentVariableModule {\n" +
      "    private final Map<String, String> env;\n" +
      "\n" +
      "    @Inject\n" +
      "    public RapierEnvironmentVariableModule() {\n" +
      "        this(System.getenv());\n" +
      "    }\n" +
      "\n" +
      "    public RapierEnvironmentVariableModule(Map<String, String> env) {\n" +
      "        this.env = unmodifiableMap(env);\n" +
      "    }\n" +
      "\n" +
      "    // FOO_BAR\n" +
      "    @Provides\n" +
      "    @EnvironmentVariable(\"FOO_BAR\")\n" +
      "    public String provideEnvironmentVariableFooBar() {\n" +
      "        return env.get(\"FOO_BAR\");\n" +
      "    }\n" +
      "\n" +
      "    @Provides\n" +
      "    @EnvironmentVariable(\"FOO_BAR\")\n" +
      "    public Byte provideEnvironmentVariableFooBarAsByte() {\n" +
      "        return Optional.ofNullable(provideEnvironmentVariableFooBar()).map(Byte::parseByte).orElse(null);\n" +
      "    }\n" +
      "\n" +
      "    @Provides\n" +
      "    @EnvironmentVariable(\"FOO_BAR\")\n" +
      "    public Short provideEnvironmentVariableFooBarAsShort() {\n" +
      "        return Optional.ofNullable(provideEnvironmentVariableFooBar()).map(Short::parseShort).orElse(null);\n" +
      "    }\n" +
      "\n" +
      "    @Provides\n" +
      "    @EnvironmentVariable(\"FOO_BAR\")\n" +
      "    public Integer provideEnvironmentVariableFooBarAsInteger() {\n" +
      "        return Optional.ofNullable(provideEnvironmentVariableFooBar()).map(Integer::parseInt).orElse(null);\n" +
      "    }\n" +
      "\n" +
      "    @Provides\n" +
      "    @EnvironmentVariable(\"FOO_BAR\")\n" +
      "    public Long provideEnvironmentVariableFooBarAsLong() {\n" +
      "        return Optional.ofNullable(provideEnvironmentVariableFooBar()).map(Long::parseLong).orElse(null);\n" +
      "    }\n" +
      "\n" +
      "    @Provides\n" +
      "    @EnvironmentVariable(\"FOO_BAR\")\n" +
      "    public Float provideEnvironmentVariableFooBarAsFloat() {\n" +
      "        return Optional.ofNullable(provideEnvironmentVariableFooBar()).map(Float::parseFloat).orElse(null);\n" +
      "    }\n" +
      "\n" +
      "    @Provides\n" +
      "    @EnvironmentVariable(\"FOO_BAR\")\n" +
      "    public Double provideEnvironmentVariableFooBarAsDouble() {\n" +
      "        return Optional.ofNullable(provideEnvironmentVariableFooBar()).map(Double::parseDouble).orElse(null);\n" +
      "    }\n" +
      "\n" +
      "    @Provides\n" +
      "    @EnvironmentVariable(\"FOO_BAR\")\n" +
      "    public Boolean provideEnvironmentVariableFooBarAsBoolean() {\n" +
      "        return Optional.ofNullable(provideEnvironmentVariableFooBar()).map(Boolean::parseBoolean).orElse(null);\n" +
      "    }\n" +
      "}\n");
    // @formatter:on

    assertThat(compilation).generatedSourceFile("com.example.RapierEnvironmentVariableModule")
        .hasSourceEquivalentTo(expectedOutput);
  }
}
