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
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

public class EnvironmentVariableProcessorTest extends DaggerTestBase {
  @Test
  public void givenSimpleComponentWithEnvironmentVariable_whenCompile_thenExpectedtModuleIsGenerated() {
    // Define the source file to test
    final JavaFileObject source =
        JavaFileObjects.forSourceString("com.example.ExampleComponent", """
            package com.example;

            @dagger.Component
            public interface ExampleComponent {
                @com.sigpwned.rapier.core.EnvironmentVariable("FOO_BAR")
                public Integer provisionFooBarAsInt();
            }
            """);

    // Run the annotation processor
    final Compilation compilation =
        Compiler.javac().withProcessors(new EnvironmentVariableProcessor()).compile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    // Assert generated file content
    final JavaFileObject expectedOutput = JavaFileObjects.forSourceString(
        "com.example.RapierExampleComponentEnvironmentVariableModule",
        """
            package com.example;

            import static java.util.Collections.unmodifiableMap;

            import com.sigpwned.rapier.core.EnvironmentVariable;
            import dagger.Module;
            import dagger.Provides;
            import java.util.Map;
            import java.util.Optional;
            import javax.inject.Inject;

            @Module
            public class RapierExampleComponentEnvironmentVariableModule {
                private final Map<String, String> env;

                @Inject
                public RapierExampleComponentEnvironmentVariableModule() {
                    this(System.getenv());
                }

                public RapierEnvironmentVariableModule(Map<String, String> env) {
                    this.env = unmodifiableMap(env);
                }

                // FOO_BAR
                @Provides
                @EnvironmentVariable("FOO_BAR")
                public String provideEnvironmentVariableFooBar() {
                    return env.get("FOO_BAR");
                }

                @Provides
                @EnvironmentVariable("FOO_BAR")
                public java.lang.Integer provideEnvironmentVariableFooBarAsInteger(@EnvironmentVariable("FOO_BAR") String value) {
                    return value != null ? java.lang.Integer.valueOf(value) : null;
                }

            }
            """);

    assertThat(compilation)
        .generatedSourceFile("com.example.RapierExampleComponentEnvironmentVariableModule")
        .hasSourceEquivalentTo(expectedOutput);
  }

  @Test
  public void givenSimpleComponentWithEnvironmentVariable_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    // Define the source file to test
    final String componentSource = """
        @dagger.Component(modules={RapierExampleComponentEnvironmentVariableModule.class})
        public interface ExampleComponent {
            @com.sigpwned.rapier.core.EnvironmentVariable("FOO_BAR")
            public Integer provisionFooBarAsInt();
        }
        """;

    final String appSource =
        """
            import java.util.Map;

            public class App {
                public static void main(String[] args) {
                    ExampleComponent component = DaggerExampleComponent.builder()
                        .rapierExampleComponentEnvironmentVariableModule(new RapierExampleComponentEnvironmentVariableModule(Map.of("FOO_BAR", "42")))
                        .build();
                    System.out.println(component.provisionFooBarAsInt());
                }
            }
            """;

    final String output = compileAndRunSourceCode(List.of(componentSource, appSource),
        List.of("com.sigpwned.rapier.core.EnvironmentVariableProcessor",
            DAGGER_COMPONENT_ANNOTATION_PROCESSOR)).trim();

    assertEquals("42", output);
  }
}
