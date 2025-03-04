/*-
 * =================================LICENSE_START==================================
 * rapier-environment-variable-compiler
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
package rapier.envvar.compiler;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static java.util.Collections.unmodifiableList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import rapier.compiler.core.RapierTestBase;

public class EnvironmentVariableProcessorCompileTest extends RapierTestBase {
  @Test
  @Disabled("Disabled until generated source code stabilizes")
  public void givenSimpleComponentWithEnvironmentVariableWithoutDefaultValue_whenCompile_thenExpectedtModuleIsGenerated()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentEnvironmentVariableModule.class})
        public interface ExampleComponent {
            @rapier.envvar.EnvironmentVariable("FOO_BAR")
            public Integer provisionFooBarAsInt();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("com.example.RapierExampleComponentEnvironmentVariableModule")
        .hasSourceEquivalentTo(prepareSourceFile(
            """
                package com.example;

                import static java.util.Collections.unmodifiableMap;
                import static java.util.stream.Collectors.toMap;

                import rapier.envvar.EnvironmentVariable;
                import dagger.Module;
                import dagger.Provides;
                import java.util.Map;
                import java.util.Optional;
                import java.util.Properties;
                import javax.annotation.Nullable;
                import javax.annotation.processing.Generated;
                import javax.inject.Inject;
                import rapier.internal.RapierGenerated;

                @Module
                @RapierGenerated
                @Generated(
                    value = "rapier.envvar.compiler.EnvironmentVariableProcessor@1.2.3",
                    comments = "https://www.example.com",
                    date = "2024-01-01T12:34:56Z")
                public class RapierExampleComponentEnvironmentVariableModule {
                    private final Map<String, String> env;
                    private final Map<String, String> sys;

                    @Inject
                    public RapierExampleComponentEnvironmentVariableModule() {
                        this(System.getenv());
                    }

                    public RapierExampleComponentEnvironmentVariableModule(Map<String, String> env) {
                        this(env, System.getProperties());
                    }

                    public RapierExampleComponentEnvironmentVariableModule(Map<String, String> env, Properties sys) {
                        this(env, sys.entrySet().stream()
                            .collect(toMap(
                                e -> e.getKey().toString(),
                                e -> e.getValue().toString())));
                    }

                    public RapierExampleComponentEnvironmentVariableModule(Map<String, String> env, Map<String, String> sys) {
                        this.env = unmodifiableMap(env);
                        this.sys = unmodifiableMap(sys);
                    }

                    @Provides
                    @EnvironmentVariable("FOO_BAR")
                    public java.lang.Integer provideEnvironmentVariableFooBarAsInteger(@EnvironmentVariable("FOO_BAR") String value) {
                        final java.lang.Integer result = java.lang.Integer.valueOf(value);
                        if (result == null) {
                            final String name="FOO_BAR";
                            throw new IllegalStateException("Environment variable " + name + " representation java.lang.Integer not set");
                        }
                        return result;
                    }

                    @Provides
                    @EnvironmentVariable("FOO_BAR")
                    public String provideEnvironmentVariableFooBarAsString() {
                        final String name="FOO_BAR";
                        final String value=env.get(name);
                        if (value == null)
                            throw new IllegalStateException("Environment variable " + name + " not set");
                        return value;
                    }

                }"""));
  }

  @Test
  @Disabled("Disabled until generated source code stabilizes")
  public void givenSimpleComponentWithEnvironmentVariableWithDefaultValue_whenCompile_thenExpectedtModuleIsGenerated()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules={RapierExampleComponentEnvironmentVariableModule.class})
        public interface ExampleComponent {
            @rapier.envvar.EnvironmentVariable(value="FOO_BAR", defaultValue="42")
            public Integer provisionFooBarAsInt();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("com.example.RapierExampleComponentEnvironmentVariableModule")
        .hasSourceEquivalentTo(prepareSourceFile(
            """
                package com.example;

                import static java.util.Collections.unmodifiableMap;
                import static java.util.stream.Collectors.toMap;

                import rapier.envvar.EnvironmentVariable;
                import dagger.Module;
                import dagger.Provides;
                import java.util.Map;
                import java.util.Optional;
                import java.util.Properties;
                import javax.annotation.Nullable;
                import javax.annotation.processing.Generated;
                import javax.inject.Inject;
                import rapier.internal.RapierGenerated;

                @Module
                @RapierGenerated
                @Generated(
                    value = "rapier.envvar.compiler.EnvironmentVariableProcessor@1.2.3",
                    comments = "https://www.example.com",
                    date = "2024-01-01T12:34:56Z")
                public class RapierExampleComponentEnvironmentVariableModule {
                    private final Map<String, String> env;
                    private final Map<String, String> sys;

                    @Inject
                    public RapierExampleComponentEnvironmentVariableModule() {
                        this(System.getenv());
                    }

                    public RapierExampleComponentEnvironmentVariableModule(Map<String, String> env) {
                        this(env, System.getProperties());
                    }

                    public RapierExampleComponentEnvironmentVariableModule(Map<String, String> env, Properties sys) {
                        this(env, sys.entrySet().stream()
                            .collect(toMap(
                                e -> e.getKey().toString(),
                                e -> e.getValue().toString())));
                    }

                    public RapierExampleComponentEnvironmentVariableModule(Map<String, String> env, Map<String, String> sys) {
                        this.env = unmodifiableMap(env);
                        this.sys = unmodifiableMap(sys);
                    }

                    @Provides
                    @EnvironmentVariable(value="FOO_BAR", defaultValue="42")
                    public java.lang.Integer provideEnvironmentVariableFooBarWithDefaultValue92cfcebAsInteger(@EnvironmentVariable(value="FOO_BAR", defaultValue="42") String value) {
                        return java.lang.Integer.valueOf(value);
                    }

                    @Provides
                    @EnvironmentVariable(value="FOO_BAR", defaultValue="42")
                    public String provideEnvironmentVariableFooBarWithDefaultValue92cfcebAsString() {
                        final String name="FOO_BAR";
                        final String value=env.get(name);
                        return Optional.ofNullable(value).orElse("42");
                    }

                }
                """));
  }

  @Test
  public void givenSimpleComponentWithEnvironmentVariable_whenCompileAndRunWithValue_thenExpectedOutput()
      throws IOException {
    // Define the source file to test
    final JavaFileObject componentSource = prepareSourceFile("""
        @dagger.Component(modules={RapierExampleComponentEnvironmentVariableModule.class})
        public interface ExampleComponent {
            @javax.annotation.Nullable
            @rapier.envvar.EnvironmentVariable("FOO_BAR")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentEnvironmentVariableModule(
                    new RapierExampleComponentEnvironmentVariableModule(Map.of("FOO_BAR", "42")))
                    .build();
                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    final Compilation compilation = doCompile(componentSource, appSource);

    assertThat(compilation).succeeded();

    final String appOutput = doRun(compilation).trim();

    assertEquals("42", appOutput);
  }

  @Test
  public void givenSimpleComponentWithEnvironmentVariableWithDefaultValue_whenCompileAndRunWithValue_thenExpectedOutput()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        @dagger.Component(modules={RapierExampleComponentEnvironmentVariableModule.class})
        public interface ExampleComponent {
            @rapier.envvar.EnvironmentVariable(value="FOO_BAR", defaultValue="43")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentEnvironmentVariableModule(
                        new RapierExampleComponentEnvironmentVariableModule(Map.of(
                            "FOO_BAR", "42")))
                    .build();
                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    final Compilation compilation = doCompile(componentSource, appSource);

    assertThat(compilation).succeeded();

    final String appOutput = doRun(compilation).trim();

    assertEquals("42", appOutput);
  }

  @Test
  public void givenSimpleComponentWithEnvironmentVariableWithDefaultValue_whenCompileAndRunWithoutValue_thenExpectedOutput()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        @dagger.Component(modules={RapierExampleComponentEnvironmentVariableModule.class})
        public interface ExampleComponent {
            @rapier.envvar.EnvironmentVariable(value="FOO_BAR", defaultValue="43")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentEnvironmentVariableModule(
                        new RapierExampleComponentEnvironmentVariableModule(Map.of()))
                    .build();
                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    final Compilation compilation = doCompile(componentSource, appSource);

    assertThat(compilation).succeeded();

    final String appOutput = doRun(compilation).trim();

    assertEquals("43", appOutput);
  }

  @Test
  public void givenSimpleComponentWithNullableEnvironmentVariable_whenCompileAndRunWithoutValue_thenExpectedtOutput()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        @dagger.Component(modules={RapierExampleComponentEnvironmentVariableModule.class})
        public interface ExampleComponent {
            @javax.annotation.Nullable
            @rapier.envvar.EnvironmentVariable(value="FOO_BAR")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentEnvironmentVariableModule(
                        new RapierExampleComponentEnvironmentVariableModule(Map.of()))
                    .build();
                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    final Compilation compilation = doCompile(componentSource, appSource);

    assertThat(compilation).succeeded();

    final String appOutput = doRun(compilation).trim();

    assertEquals("null", appOutput);
  }

  @Test
  public void givenComponentWithInconsistentEnvironmentVariableParameterRequirednessFromNullable_whenCompile_thenCompileWarning()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules={RapierExampleComponentEnvironmentVariableModule.class})
        public interface ExampleComponent {
            @javax.annotation.Nullable
            @rapier.envvar.EnvironmentVariable(value="FOO_BAR")
            public Integer provisionFooBarAsInt();

            @rapier.envvar.EnvironmentVariable(value="FOO_BAR")
            public String provisionFooBarAsString();
        }
        """);

    final Compilation compilation = doCompile(source);

    assertThat(compilation).succeeded();

    assertTrue(
        compilation.warnings().stream().anyMatch(e -> e.getMessage(Locale.getDefault()).equals(
            "Conflicting requiredness for environment variable FOO_BAR, will be treated as required")));
    assertTrue(compilation.warnings().stream().anyMatch(e -> e.getMessage(Locale.getDefault())
        .equals("Effectively required environment variable FOO_BAR is treated as nullable")));
  }

  @Test
  public void givenComponentWithInconsistentEnvironmentVariableParameterRequirednessFromDefaultValue_whenCompile_thenCompileWarning()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules={RapierExampleComponentEnvironmentVariableModule.class})
        public interface ExampleComponent {
            @rapier.envvar.EnvironmentVariable(value="FOO_BAR")
            public Integer provisionFooBarAsInt();

            @rapier.envvar.EnvironmentVariable(value="FOO_BAR", defaultValue="42")
            public String provisionFooBarAsString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    assertTrue(
        compilation.warnings().stream().anyMatch(e -> e.getMessage(Locale.getDefault()).equals(
            "Conflicting requiredness for environment variable FOO_BAR, will be treated as required")));
    assertTrue(compilation.warnings().stream().anyMatch(e -> e.getMessage(Locale.getDefault())
        .equals("Effectively required environment variable FOO_BAR has default value")));
  }

  @Test
  public void givenComponentWithInvalidEnvironmentVariableName_whenCompile_thenCompileError()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules={RapierExampleComponentEnvironmentVariableModule.class})
        public interface ExampleComponent {
            @rapier.envvar.EnvironmentVariable(value="FOO.BAR")
            public String provisionFooBarAsString();
        }
        """);

    final Compilation compilation = doCompile(source);

    assertThat(compilation).failed();

    assertTrue(compilation.errors().stream().anyMatch(e -> e.getMessage(Locale.getDefault())
        .equals("Invalid environment variable name template")));
  }

  public static final OffsetDateTime TEST_DATE =
      OffsetDateTime.of(2024, 1, 1, 12, 34, 56, 0, ZoneOffset.UTC);

  public static final String TEST_VERSION = "1.2.3";

  public static final String TEST_URL = "https://www.example.com";

  /**
   * We need to set the date and version so our generated source code is deterministic.
   */
  @Override
  protected List<Processor> getAnnotationProcessors() {
    List<Processor> result = super.getAnnotationProcessors();
    for (Processor processor : result) {
      if (processor instanceof EnvironmentVariableProcessor) {
        EnvironmentVariableProcessor evp = (EnvironmentVariableProcessor) processor;
        evp.setDate(TEST_DATE);
        evp.setVersion(TEST_VERSION);
        evp.setUrl(TEST_URL);
      }
    }
    return unmodifiableList(result);
  }

  /**
   * We need to include the generated classes from the rapier-environment-variable module in the
   * classpath for our tests.
   */
  @Override
  protected List<File> getCompileClasspath() throws FileNotFoundException {
    List<File> result = new ArrayList<>();
    result.addAll(super.getCompileClasspath());
    result.add(resolveProjectFile("../rapier-environment-variable/target/classes"));
    result.add(resolveProjectFile("../rapier-core/target/classes"));
    return unmodifiableList(result);
  }
}
