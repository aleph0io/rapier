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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import rapier.compiler.core.RapierTestBase;

public class SystemPropertyProcessorRunTest extends RapierTestBase {
  @Test
  public void givenSimpleComponent_whenCompileAndRun_thenGetExpectedOutput() throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @rapier.sysprop.SystemProperty("foo.bar")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        package com.example;

        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                final ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentSystemPropertyModule(
                        new RapierExampleComponentSystemPropertyModule(
                            Map.of(),
                            Map.of("foo.bar", "42")))
                    .build();

                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("42", output);
  }

  @Test
  public void givenSimpleComponentWithNameTemplateEnvWithoutDefaultValue_whenCompileAndRunWithReferencedVariable_thenGetExpectedOutput()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @rapier.sysprop.SystemProperty("foo.${env.QUUX}")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        package com.example;

        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                final ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentSystemPropertyModule(
                        new RapierExampleComponentSystemPropertyModule(
                            Map.of("QUUX", "bar"),
                            Map.of("foo.bar", "42")))
                    .build();

                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("42", output);
  }

  @Test
  public void givenSimpleComponentWithNameTemplateEnvWithoutDefaultValue_whenCompileAndRunWithoutReferencedVariable_thenCatchException()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @rapier.sysprop.SystemProperty("foo.${env.QUUX}")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        package com.example;

        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                try {
                    final ExampleComponent component = DaggerExampleComponent.builder()
                        .rapierExampleComponentSystemPropertyModule(
                            new RapierExampleComponentSystemPropertyModule(
                                Map.of("FOO_BAR", "42")))
                        .build();

                    System.out.println(component.provisionFooBarAsInt());
                } catch (IllegalStateException e) {
                    System.out.println(e.getClass().getName());
                }
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("java.lang.IllegalStateException", output);
  }

  @Test
  public void givenSimpleComponentWithNameTemplateEnvWithDefaultValue_whenCompileAndRunWithReferencedVariable_thenGetExpectedOutput()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @rapier.sysprop.SystemProperty("foo.${env.QUUX:-pants}")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        package com.example;

        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                final ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentSystemPropertyModule(
                        new RapierExampleComponentSystemPropertyModule(
                            Map.of("QUUX", "bar"),
                            Map.of("foo.bar", "42")))
                    .build();

                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("42", output);
  }

  @Test
  public void givenSimpleComponentWithNameTemplateEnvWithDefaultValue_whenCompileAndRunWithoutReferencedVariable_thenCatchException()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @rapier.sysprop.SystemProperty("foo.${env.QUUX:-bar}")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        package com.example;

        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                final ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentSystemPropertyModule(
                        new RapierExampleComponentSystemPropertyModule(
                            Map.of(),
                            Map.of("foo.bar", "42")))
                    .build();

                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("42", output);
  }

  @Test
  public void givenSimpleComponentWithNameTemplateSysWithoutDefaultValue_whenCompileAndRunWithReferencedVariable_thenGetExpectedOutput()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @rapier.sysprop.SystemProperty("foo.${sys.QUUX}")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        package com.example;

        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                final ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentSystemPropertyModule(
                        new RapierExampleComponentSystemPropertyModule(
                            Map.of(),
                            Map.of("foo.bar", "42", "QUUX", "bar")))
                    .build();

                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("42", output);
  }

  @Test
  public void givenSimpleComponentWithNameTemplateSysWithoutDefaultValue_whenCompileAndRunWithoutReferencedVariable_thenCatchException()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @rapier.sysprop.SystemProperty("foo.${sys.QUUX}")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        package com.example;

        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                try {
                    final ExampleComponent component = DaggerExampleComponent.builder()
                        .rapierExampleComponentSystemPropertyModule(
                            new RapierExampleComponentSystemPropertyModule(
                                Map.of("FOO_BAR", "42"),
                                Map.of()))
                        .build();

                    System.out.println(component.provisionFooBarAsInt());
                } catch (IllegalStateException e) {
                    System.out.println(e.getClass().getName());
                }
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("java.lang.IllegalStateException", output);
  }

  @Test
  public void givenSimpleComponentWithNameTemplateSysWithDefaultValue_whenCompileAndRunWithReferencedVariable_thenGetExpectedOutput()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @rapier.sysprop.SystemProperty("foo.${sys.QUUX:-pants}")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        package com.example;

        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                final ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentSystemPropertyModule(
                        new RapierExampleComponentSystemPropertyModule(
                            Map.of(),
                            Map.of("foo.bar", "42", "QUUX", "bar")))
                    .build();

                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("42", output);
  }

  @Test
  public void givenSimpleComponentWithNameTemplateSysWithDefaultValue_whenCompileAndRunWithoutReferencedVariable_thenCatchException()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @rapier.sysprop.SystemProperty("foo.${env.QUUX:-bar}")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        package com.example;

        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                final ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentSystemPropertyModule(
                        new RapierExampleComponentSystemPropertyModule(
                            Map.of(),
                            Map.of("foo.bar", "42")))
                    .build();

                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("42", output);
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
    result.add(resolveProjectFile("../rapier-core/target/classes"));
    return unmodifiableList(result);
  }
}
