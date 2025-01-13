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
package rapier.sysprop.compiler;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static java.util.Collections.unmodifiableList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import rapier.core.RapierTestBase;

public class SystemPropertyProcessorCompileTest extends RapierTestBase {
  @Test
  public void givenSimpleComponentWithEnvironmentVariableWithoutDefaultValue_whenCompile_thenExpectedtModuleIsGenerated()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @rapier.sysprop.SystemProperty("foo.bar")
            public Integer provisionFooBarAsInt();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("com.example.RapierExampleComponentSystemPropertyModule")
        .hasSourceEquivalentTo(prepareSourceFile(
            """
                                package com.example;

                import static java.util.Collections.unmodifiableMap;
                import static java.util.stream.Collectors.toMap;

                import rapier.sysprop.SystemProperty;
                import dagger.Module;
                import dagger.Provides;
                import java.util.Map;
                import java.util.Optional;
                import java.util.Properties;
                import javax.annotation.Nullable;
                import javax.inject.Inject;

                @Module
                public class RapierExampleComponentSystemPropertyModule {
                    private final Map<String, String> sysprop;

                    @Inject
                    public RapierExampleComponentSystemPropertyModule() {
                        this(System.getProperties());
                    }

                    public RapierExampleComponentSystemPropertyModule(Properties properties) {
                        this(properties.entrySet().stream()
                            .collect(toMap(
                                e -> e.getKey().toString(),
                                e -> e.getValue().toString())));
                    }

                    public RapierExampleComponentSystemPropertyModule(Map<String, String> sysprop) {
                        this.sysprop = unmodifiableMap(sysprop);
                    }

                    @Provides
                    @SystemProperty("foo.bar")
                    public java.lang.Integer provideSystemPropertyFooBarAsInteger(@SystemProperty("foo.bar") String value) {
                        java.lang.Integer result = java.lang.Integer.valueOf(value);
                        if (result == null)
                            throw new IllegalStateException("System property foo.bar representation java.lang.Integer not set");
                        return result;
                    }

                    @Provides
                    @SystemProperty("foo.bar")
                    public String provideSystemPropertyFooBarAsString() {
                        String result=sysprop.get("foo.bar");
                        if (result == null)
                            throw new IllegalStateException("System property foo.bar not set");
                        return result;
                    }

                }
                """));
  }

  @Test
  public void givenSimpleComponentWithEnvironmentVariableWithDefaultValue_whenCompile_thenExpectedModuleIsGenerated()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules={RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @rapier.sysprop.SystemProperty(value="foo.bar", defaultValue="42")
            public Integer provisionFooBarAsInt();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("com.example.RapierExampleComponentSystemPropertyModule")
        .hasSourceEquivalentTo(prepareSourceFile(
            """
                package com.example;

                import static java.util.Collections.unmodifiableMap;
                import static java.util.stream.Collectors.toMap;

                import rapier.sysprop.SystemProperty;
                import dagger.Module;
                import dagger.Provides;
                import java.util.Map;
                import java.util.Optional;
                import java.util.Properties;
                import javax.annotation.Nullable;
                import javax.inject.Inject;

                @Module
                public class RapierExampleComponentSystemPropertyModule {
                    private final Map<String, String> sysprop;

                    @Inject
                    public RapierExampleComponentSystemPropertyModule() {
                        this(System.getProperties());
                    }

                    public RapierExampleComponentSystemPropertyModule(Properties properties) {
                        this(properties.entrySet().stream()
                            .collect(toMap(
                                e -> e.getKey().toString(),
                                e -> e.getValue().toString())));
                    }

                    public RapierExampleComponentSystemPropertyModule(Map<String, String> sysprop) {
                        this.sysprop = unmodifiableMap(sysprop);
                    }

                    @Provides
                    @SystemProperty(value="foo.bar", defaultValue="42")
                    public java.lang.Integer provideSystemPropertyFooBarWithDefaultValue92cfcebAsInteger(@SystemProperty(value="foo.bar", defaultValue="42") String value) {
                        return java.lang.Integer.valueOf(value);
                    }

                    @Provides
                    @SystemProperty(value="foo.bar", defaultValue="42")
                    public String provideSystemPropertyFooBarWithDefaultValue92cfcebAsString() {
                        return Optional.ofNullable(sysprop.get("foo.bar")).orElse("42");
                    }

                }
                """));
  }

  @Test
  public void givenSimpleComponentWithEnvironmentVariableWithGivenValue_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    // Define the source file to test
    final JavaFileObject componentSource = prepareSourceFile("""
        @dagger.Component(modules={RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @javax.annotation.Nullable
            @rapier.sysprop.SystemProperty("foo.bar")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentSystemPropertyModule(
                        new RapierExampleComponentSystemPropertyModule(Map.of("foo.bar", "42")))
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
  public void givenSimpleComponentWithEnvironmentVariableWithDefaultValue_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        @dagger.Component(modules={RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @rapier.sysprop.SystemProperty(value="foo.bar", defaultValue="43")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentSystemPropertyModule(
                        new RapierExampleComponentSystemPropertyModule(Map.of()))
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
  public void givenComponentWithInconsistentEnvironmentVariableParameterRequirednessFromNullable_whenCompile_thenCompileWarning()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules={RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @javax.annotation.Nullable
            @rapier.sysprop.SystemProperty(value="foo.bar")
            public Integer provisionFooBarAsInt();

            @rapier.sysprop.SystemProperty(value="foo.bar")
            public String provisionFooBarAsString();
        }
        """);

    final Compilation compilation = doCompile(source);

    assertThat(compilation).succeeded();

    assertTrue(
        compilation.warnings().stream().anyMatch(e -> e.getMessage(Locale.getDefault()).equals(
            "Conflicting requiredness for system property foo.bar, will be treated as required")));
    assertTrue(compilation.warnings().stream().anyMatch(e -> e.getMessage(Locale.getDefault())
        .equals("Effectively required system property foo.bar is treated as nullable")));
  }

  @Test
  public void givenComponentWithInconsistentEnvironmentVariableParameterRequirednessFromDefaultValue_whenCompile_thenCompileWarning()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules={RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @rapier.sysprop.SystemProperty(value="foo.bar")
            public Integer provisionFooBarAsInt();

            @rapier.sysprop.SystemProperty(value="foo.bar", defaultValue="42")
            public String provisionFooBarAsString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    assertTrue(
        compilation.warnings().stream().anyMatch(e -> e.getMessage(Locale.getDefault()).equals(
            "Conflicting requiredness for system property foo.bar, will be treated as required")));
    assertTrue(compilation.warnings().stream().anyMatch(e -> e.getMessage(Locale.getDefault())
        .equals("Effectively required system property foo.bar has default value")));
  }

  /**
   * We need to include the generated classes from the rapier-environment-variable module in the
   * classpath for our tests.
   */
  @Override
  protected List<File> getCompileClasspath() throws FileNotFoundException {
    List<File> result = new ArrayList<>();
    result.addAll(super.getCompileClasspath());
    result.add(resolveProjectFile("../rapier-system-property/target/classes"));
    return unmodifiableList(result);
  }
}
