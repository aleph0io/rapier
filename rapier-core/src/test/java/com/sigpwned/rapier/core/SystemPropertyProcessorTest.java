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
    final JavaFileObject source = JavaFileObjects.forSourceString("com.example.ExampleComponent",
    // @formatter:off
      "package com.example;\n" +
      "\n" +
      "@dagger.Component\n" +
      "public interface ExampleComponent {\n" +
      "    @com.sigpwned.rapier.core.SystemProperty(\"FOO_BAR\")\n" +
      "    public Integer provisionFooBarAsInt();\n" +
      "}\n");
    // @formatter:on

    // Run the annotation processor
    final Compilation compilation =
        Compiler.javac().withProcessors(new SystemPropertyProcessor()).compile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    // Assert generated file content
    final JavaFileObject expectedOutput = JavaFileObjects.forSourceString(
        "com.example.RapierExampleComponentSystemPropertyModule",
        """
            package com.example;

            import static java.util.Collections.unmodifiableMap;
            import static java.util.stream.Collectors.toMap;

            import com.sigpwned.rapier.core.SystemProperty;
            import dagger.Module;
            import dagger.Provides;
            import java.util.Map;
            import java.util.Optional;
            import java.util.Properties;
            import javax.inject.Inject;

            @Module
            public class RapierExampleComponentSystemPropertyModule {
                private final Map<String, String> props;

                @Inject
                public RapierExampleComponentSystemPropertyModule() {
                    this(System.getProperties());
                }

                public RapierExampleComponentSystemPropertyModule(Properties props) {
                    this(props.entrySet()
                        .stream()
                        .collect(toMap(
                            e -> String.valueOf(e.getKey()),
                            e -> String.valueOf(e.getValue()))));
                }

                public RapierExampleComponentSystemPropertyModule(Map<String, String> props) {
                    this.props = unmodifiableMap(props);
                }

                // FOO_BAR
                @Provides
                @SystemProperty("FOO_BAR")
                public String provideSystemPropertyFooBar() {
                    return props.get("FOO_BAR");
                }

                @Provides
                @SystemProperty("FOO_BAR")
                public java.lang.Integer provideSystemPropertyFooBarAsInteger(@SystemProperty("FOO_BAR") String value) {
                    return value != null ? java.lang.Integer.valueOf(value) : null;
                }

            }
            """);

    assertThat(compilation)
        .generatedSourceFile("com.example.RapierExampleComponentSystemPropertyModule")
        .hasSourceEquivalentTo(expectedOutput);
  }
}
