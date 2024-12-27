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

public class SystemPropertyProcessorTest {
  @Test
  public void testAnnotationProcessor() {
    // Define the source file to test
    JavaFileObject source = JavaFileObjects.forSourceString("com.example.ExampleComponent",
    // @formatter:off
      "package com.example;\n" +
      "\n" +
      "@dagger.Component\n" +
      "public interface ExampleComponent {\n" +
      "    @com.sigpwned.rapier.core.SystemProperty(\"FOO_BAR\")\n" +
      "    public String getFooBar();\n" +
      "}\n");
    // @formatter:on

    // Run the annotation processor
    Compilation compilation = Compiler.javac().withProcessors(new SystemPropertyProcessor())
        .withOptions("-Arapier.targetPackage=com.example").compile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    // Assert generated file content
    JavaFileObject expectedOutput =
        JavaFileObjects.forSourceString("com.example.RapierSystemPropertyModule",
        // @formatter:off
      "package com.example;\n" +
      "\n" +
      "import static java.util.Collections.unmodifiableMap;\n" +
      "import static java.util.stream.Collectors.toMap;\n" +
      "\n" +
      "import com.sigpwned.rapier.core.SystemProperty;\n" +
      "import dagger.Module;\n" +
      "import dagger.Provides;\n" +
      "import java.util.Map;\n" +
      "import java.util.Optional;\n" +
      "import java.util.Properties;\n" +
      "import javax.inject.Inject;\n" +
      "\n" +
      "@Module\n" +
      "public class RapierSystemPropertyModule {\n" +
      "    private final Map<String, String> properties;\n" +
      "\n" +
      "    @Inject\n" +
      "    public RapierSystemPropertyModule() {\n" +
      "        this(System.getProperties());\n" +
      "    }\n" +
      "\n" +
      "    public RapierSystemPropertyModule(Properties properties) {\n" +
      "        this(properties.entrySet()\n" +
      "            .stream()\n" +
      "            .collect(toMap(\n" +
      "                e -> String.valueOf(e.getKey()),\n" +
      "                e -> String.valueOf(e.getValue()))));\n" +
      "    }\n" +
      "\n" +
      "    public RapierSystemPropertyModule(Map<String, String> properties) {\n" +
      "        this.properties = unmodifiableMap(properties);\n" +
      "    }\n" +
      "\n" +
      "    // FOO_BAR\n" +
      "    @Provides\n" +
      "    @SystemProperty(\"FOO_BAR\")\n" +
      "    public String provideSystemPropertyFooBar() {\n" +
      "        return properties.get(\"FOO_BAR\");\n" +
      "    }\n" +
      "\n" +
      "    @Provides\n" +
      "    @SystemProperty(\"FOO_BAR\")\n" +
      "    public Byte provideSystemPropertyFooBarAsByte() {\n" +
      "        return Optional.ofNullable(provideSystemPropertyFooBar()).map(Byte::parseByte).orElse(null);\n" +
      "    }\n" +
      "\n" +
      "    @Provides\n" +
      "    @SystemProperty(\"FOO_BAR\")\n" +
      "    public Short provideSystemPropertyFooBarAsShort() {\n" +
      "        return Optional.ofNullable(provideSystemPropertyFooBar()).map(Short::parseShort).orElse(null);\n" +
      "    }\n" +
      "\n" +
      "    @Provides\n" +
      "    @SystemProperty(\"FOO_BAR\")\n" +
      "    public Integer provideSystemPropertyFooBarAsInteger() {\n" +
      "        return Optional.ofNullable(provideSystemPropertyFooBar()).map(Integer::parseInt).orElse(null);\n" +
      "    }\n" +
      "\n" +
      "    @Provides\n" +
      "    @SystemProperty(\"FOO_BAR\")\n" +
      "    public Long provideSystemPropertyFooBarAsLong() {\n" +
      "        return Optional.ofNullable(provideSystemPropertyFooBar()).map(Long::parseLong).orElse(null);\n" +
      "    }\n" +
      "\n" +
      "    @Provides\n" +
      "    @SystemProperty(\"FOO_BAR\")\n" +
      "    public Float provideSystemPropertyFooBarAsFloat() {\n" +
      "        return Optional.ofNullable(provideSystemPropertyFooBar()).map(Float::parseFloat).orElse(null);\n" +
      "    }\n" +
      "\n" +
      "    @Provides\n" +
      "    @SystemProperty(\"FOO_BAR\")\n" +
      "    public Double provideSystemPropertyFooBarAsDouble() {\n" +
      "        return Optional.ofNullable(provideSystemPropertyFooBar()).map(Double::parseDouble).orElse(null);\n" +
      "    }\n" +
      "\n" +
      "    @Provides\n" +
      "    @SystemProperty(\"FOO_BAR\")\n" +
      "    public Boolean provideSystemPropertyFooBarAsBoolean() {\n" +
      "        return Optional.ofNullable(provideSystemPropertyFooBar()).map(Boolean::parseBoolean).orElse(null);\n" +
      "    }\n" +
      "}\n");
    // @formatter:on
    
    /*
        writer.println("    private final Map<String, String> properties;");
        writer.println();
        writer.println("    @Inject");
        writer.println("    public RapierSystemPropertyModule() {");
        writer.println("        this(System.getProperties());");
        writer.println("    }");
        writer.println();
        writer.println("    public RapierSystemPropertyModule(Properties properties) {");
        writer.println("        this(properties.entrySet()");
        writer.println("            .stream()");
        writer.println("            .collect(toMap(");
        writer.println("                e -> String.valueOf(e.getKey()),");
        writer.println("                e -> String.valueOf(e.getValue()))));");
        writer.println("    }");
        writer.println();
        writer.println("    public RapierSystemPropertyModule(Map<String, String> properties) {");
        writer.println("        this.properties = unmodifiableMap(properties);");
        writer.println("    }");
     
     */

    assertThat(compilation).generatedSourceFile("com.example.RapierSystemPropertyModule")
        .hasSourceEquivalentTo(expectedOutput);
  }
}
