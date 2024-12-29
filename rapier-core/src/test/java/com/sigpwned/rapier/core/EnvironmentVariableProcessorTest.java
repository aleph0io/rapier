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
    JavaFileObject source = JavaFileObjects.forSourceString("com.example.ExampleComponent", """
        package com.example;

        @dagger.Component
        public interface ExampleComponent {
            @com.sigpwned.rapier.core.EnvironmentVariable("FOO_BAR")
            public String getFooBar();
        }
        """);

    // Run the annotation processor
    Compilation compilation = Compiler.javac().withProcessors(new EnvironmentVariableProcessor())
        .withOptions("-Arapier.targetPackage=com.example").compile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    // Assert generated file content
    JavaFileObject expectedOutput = JavaFileObjects.forSourceString(
        "com.example.RapierEnvironmentVariableModule",
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
            public class RapierEnvironmentVariableModule {
                private final Map<String, String> env;

                @Inject
                public RapierEnvironmentVariableModule() {
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
                public Byte provideEnvironmentVariableFooBarAsByte() {
                    return Optional.ofNullable(provideEnvironmentVariableFooBar()).map(Byte::parseByte).orElse(null);
                }

                @Provides
                @EnvironmentVariable("FOO_BAR")
                public Short provideEnvironmentVariableFooBarAsShort() {
                    return Optional.ofNullable(provideEnvironmentVariableFooBar()).map(Short::parseShort).orElse(null);
                }

                @Provides
                @EnvironmentVariable("FOO_BAR")
                public Integer provideEnvironmentVariableFooBarAsInteger() {
                    return Optional.ofNullable(provideEnvironmentVariableFooBar()).map(Integer::parseInt).orElse(null);
                }

                @Provides
                @EnvironmentVariable("FOO_BAR")
                public Long provideEnvironmentVariableFooBarAsLong() {
                    return Optional.ofNullable(provideEnvironmentVariableFooBar()).map(Long::parseLong).orElse(null);
                }

                @Provides
                @EnvironmentVariable("FOO_BAR")
                public Float provideEnvironmentVariableFooBarAsFloat() {
                    return Optional.ofNullable(provideEnvironmentVariableFooBar()).map(Float::parseFloat).orElse(null);
                }

                @Provides
                @EnvironmentVariable("FOO_BAR")
                public Double provideEnvironmentVariableFooBarAsDouble() {
                    return Optional.ofNullable(provideEnvironmentVariableFooBar()).map(Double::parseDouble).orElse(null);
                }

                @Provides
                @EnvironmentVariable("FOO_BAR")
                public Boolean provideEnvironmentVariableFooBarAsBoolean() {
                    return Optional.ofNullable(provideEnvironmentVariableFooBar()).map(Boolean::parseBoolean).orElse(null);
                }
            }
            """);

    assertThat(compilation).generatedSourceFile("com.example.RapierEnvironmentVariableModule")
        .hasSourceEquivalentTo(expectedOutput);
  }
}
