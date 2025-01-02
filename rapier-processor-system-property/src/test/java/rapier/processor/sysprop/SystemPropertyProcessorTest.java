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
package rapier.processor.sysprop;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import rapier.core.DaggerTestBase;

public class SystemPropertyProcessorTest extends DaggerTestBase {
  @Test
  public void givenSimpleComponentWithSystemPropertyWithoutDefaultValue_whenCompile_thenExpectedtModuleIsGenerated() {
    // Define the source file to test
    final JavaFileObject source =
        JavaFileObjects.forSourceString("com.example.ExampleComponent", """
            package com.example;

            @dagger.Component
            public interface ExampleComponent {
                @rapier.processor.sysprop.SystemProperty("foo.bar")
                public Integer provisionFooBarAsInt();
            }
            """);

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

            import rapier.processor.sysprop.SystemProperty;
            import dagger.Module;
            import dagger.Provides;
            import java.util.Map;
            import java.util.Optional;
            import java.util.Properties;
            import javax.annotation.Nullable;
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

                @Provides
                @SystemProperty("foo.bar")
                public java.lang.Integer provideSystemPropertyFooBarAsInteger(@SystemProperty("foo.bar") String value) {
                    java.lang.Integer result= java.lang.Integer.valueOf(value);
                    if (result == null)
                        throw new IllegalStateException("System property foo.bar  as java.lang.Integer not set");
                    return result;
                }

                @Provides
                @SystemProperty("foo.bar")
                public String provideSystemPropertyFooBarAsString() {
                    String result=props.get("foo.bar");
                    if (result == null)
                        throw new IllegalStateException("System property foo.bar not set");
                    return result;
                }

            }
            """);

    assertThat(compilation)
        .generatedSourceFile("com.example.RapierExampleComponentSystemPropertyModule")
        .hasSourceEquivalentTo(expectedOutput);
  }

  @Test
  public void givenSimpleComponentWithSystemPropertyWithDefaultValue_whenCompile_thenExpectedtModuleIsGenerated() {
    // Define the source file to test
    final JavaFileObject source =
        JavaFileObjects.forSourceString("com.example.ExampleComponent", """
            package com.example;

            @dagger.Component
            public interface ExampleComponent {
                @rapier.processor.sysprop.SystemProperty(value="foo.bar", defaultValue="42")
                public Integer provisionFooBarAsInt();
            }
            """);

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

            import rapier.processor.sysprop.SystemProperty;
            import dagger.Module;
            import dagger.Provides;
            import java.util.Map;
            import java.util.Optional;
            import java.util.Properties;
            import javax.annotation.Nullable;
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

                @Provides
                @SystemProperty(value="foo.bar", defaultValue="42")
                public java.lang.Integer provideSystemPropertyFooBarWithDefaultValue92cfcebAsInteger(@SystemProperty(value="foo.bar", defaultValue="42") String value) {
                    return java.lang.Integer.valueOf(value);
                }

                @Provides
                @SystemProperty(value="foo.bar", defaultValue="42")
                public String provideSystemPropertyFooBarWithDefaultValue92cfcebAsString() {
                    return Optional.ofNullable(props.get("foo.bar")).orElse("42");
                }

            }
            """);

    assertThat(compilation)
        .generatedSourceFile("com.example.RapierExampleComponentSystemPropertyModule")
        .hasSourceEquivalentTo(expectedOutput);
  }

  @Test
  public void givenSimpleComponentWithSystemPropertyWithGivenValue_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    // Define the source file to test
    final String componentSource = """
        @dagger.Component(modules={RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @javax.annotation.Nullable
            @rapier.processor.sysprop.SystemProperty("foo.bar")
            public Integer provisionFooBarAsInt();
        }
        """;

    final String appSource =
        """
            import java.util.Map;

            public class App {
                public static void main(String[] args) {
                    ExampleComponent component = DaggerExampleComponent.builder()
                        .rapierExampleComponentSystemPropertyModule(new RapierExampleComponentSystemPropertyModule(Map.of("foo.bar", "42")))
                        .build();
                    System.out.println(component.provisionFooBarAsInt());
                }
            }
            """;

    final String output = compileAndRunSourceCode(List.of(componentSource, appSource),
        List.of(SystemPropertyProcessor.class.getName(), DAGGER_COMPONENT_ANNOTATION_PROCESSOR))
            .trim();

    assertEquals("42", output);
  }

  @Test
  public void givenSimpleComponentWithSystemPropertyWithDefaultValue_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    // Define the source file to test
    final String componentSource = """
        @dagger.Component(modules={RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @rapier.processor.sysprop.SystemProperty(value="foo.bar", defaultValue="43")
            public Integer provisionFooBarAsInt();
        }
        """;

    final String appSource =
        """
            import java.util.Map;

            public class App {
                public static void main(String[] args) {
                    ExampleComponent component = DaggerExampleComponent.builder()
                        .rapierExampleComponentSystemPropertyModule(new RapierExampleComponentSystemPropertyModule(Map.of()))
                        .build();
                    System.out.println(component.provisionFooBarAsInt());
                }
            }
            """;

    final String output = compileAndRunSourceCode(List.of(componentSource, appSource),
        List.of(SystemPropertyProcessor.class.getName(), DAGGER_COMPONENT_ANNOTATION_PROCESSOR))
            .trim();

    assertEquals("43", output);
  }
}
