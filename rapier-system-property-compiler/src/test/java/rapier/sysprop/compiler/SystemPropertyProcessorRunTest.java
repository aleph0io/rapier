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
              @rapier.sysprop.SystemProperty("FOO_BAR")
              public Integer provisionFooBarAsInt();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  Properties sys = new Properties();
                  sys.setProperty("FOO_BAR", "42");

                  final ExampleComponent component = DaggerExampleComponent.builder()
                      .rapierExampleComponentSystemPropertyModule(
                          new RapierExampleComponentSystemPropertyModule(sys))
                      .build();

                  System.out.println(component.provisionFooBarAsInt());
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("42", output);
  }

  @Test
  public void givenComponentWithRequiredParameter_whenCompileAndRunWithValue_thenGetExpectedOutput() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @rapier.sysprop.SystemProperty("FOO_BAR")
              public String provisionFooBar();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  Properties sys = new Properties();
                  sys.setProperty("FOO_BAR", "42");

                  final ExampleComponent component = DaggerExampleComponent.builder()
                      .rapierExampleComponentSystemPropertyModule(
                          new RapierExampleComponentSystemPropertyModule(sys))
                      .build();

                  System.out.println(component.provisionFooBar());
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("42", output);
  }

  @Test
  public void givenComponentWithRequiredParameter_whenCompileAndRunWithoutValue_thenCatchIllegalStateException() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @rapier.sysprop.SystemProperty("FOO_BAR")
              public String provisionFooBar();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  try {
                      DaggerExampleComponent.builder()
                          .rapierExampleComponentSystemPropertyModule(
                              new RapierExampleComponentSystemPropertyModule(new Properties()))
                          .build()
                          .provisionFooBar();
                  } catch (Exception e) {
                      System.out.println(e.getClass().getName());
                  }
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("java.lang.IllegalStateException", output);
  }

  @Test
  public void givenComponentWithNullableParameter_whenCompileAndRunWithoutValue_thenGetExpectedOutput() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @javax.annotation.Nullable
              @rapier.sysprop.SystemProperty("FOO_BAR")
              public String provisionFooBar();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  final ExampleComponent component = DaggerExampleComponent.builder()
                      .rapierExampleComponentSystemPropertyModule(
                          new RapierExampleComponentSystemPropertyModule(new Properties()))
                      .build();

                  System.out.println(component.provisionFooBar());
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("null", output);
  }


  @Test
  public void givenComponentWithNullableParameter_whenCompileAndRunWithValue_thenGetExpectedOutput() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @javax.annotation.Nullable
              @rapier.sysprop.SystemProperty("FOO_BAR")
              public String provisionFooBar();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  Properties sys = new Properties();
                  sys.setProperty("FOO_BAR", "42");

                  final ExampleComponent component = DaggerExampleComponent.builder()
                      .rapierExampleComponentSystemPropertyModule(
                          new RapierExampleComponentSystemPropertyModule(sys))
                      .build();

                  System.out.println(component.provisionFooBar());
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("42", output);
  }

  @Test
  public void givenComponentWithParameterWithDefaultValue_whenCompileAndRunWithoutValue_thenGetExpectedOutput() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @javax.annotation.Nullable
              @rapier.sysprop.SystemProperty(value="FOO_BAR", defaultValue="43")
              public String provisionFooBar();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  final ExampleComponent component = DaggerExampleComponent.builder()
                      .rapierExampleComponentSystemPropertyModule(
                          new RapierExampleComponentSystemPropertyModule(new Properties()))
                      .build();

                  System.out.println(component.provisionFooBar());
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("43", output);
  }

  @Test
  public void givenComponentWithParameterWithDefaultValue_whenCompileAndRunWithValue_thenGetExpectedOutput() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @javax.annotation.Nullable
              @rapier.sysprop.SystemProperty(value="FOO_BAR", defaultValue="43")
              public String provisionFooBar();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  Properties sys = new Properties();
                  sys.setProperty("FOO_BAR", "42");

                  final ExampleComponent component = DaggerExampleComponent.builder()
                      .rapierExampleComponentSystemPropertyModule(
                          new RapierExampleComponentSystemPropertyModule(sys))
                      .build();

                  System.out.println(component.provisionFooBar());
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("42", output);
  }

  @Test
  public void givenComponentWithIntParameter_whenCompileAndRunWithNonIntArgument_thenCatchIllegalArgumentException() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @rapier.sysprop.SystemProperty("FOO_BAR")
              public int provisionFooBar();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  try {
                      Properties sys = new Properties();
                      sys.setProperty("FOO_BAR", "not an int");

                      DaggerExampleComponent.builder()
                          .rapierExampleComponentSystemPropertyModule(
                              new RapierExampleComponentSystemPropertyModule(sys))
                          .build()
                          .provisionFooBar();
                  } catch (Exception e) {
                      System.out.println(e.getClass().getName());
                  }
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("java.lang.IllegalArgumentException", output);
  }

  @Test
  public void givenComponentWithTypeConversionThatThrowsCheckedException_whenCompileAndRunWithoutThrow_thenGetExpectedOutput() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @rapier.sysprop.SystemProperty("FOO_BAR")
              public ExampleType provisionFooBar();
          }
          """);

      final JavaFileObject typeSource = prepareSourceFile("""
          package com.example;

          public class ExampleType {
              public final String value;

              public ExampleType(String value) throws Exception {
                  this.value = value;
              }
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  Properties sys = new Properties();
                  sys.setProperty("FOO_BAR", "example");

                  ExampleComponent component = DaggerExampleComponent.builder()
                      .rapierExampleComponentSystemPropertyModule(
                          new RapierExampleComponentSystemPropertyModule(sys))
                      .build();
                  System.out.println(component.provisionFooBar().value);
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, typeSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("example", output);
  }

  @Test
  public void givenComponentWithTypeConversionThatThrowsCheckedException_whenCompileAndRunWithThrow_thenCatchIllegalArgumentException() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @rapier.sysprop.SystemProperty("FOO_BAR")
              public ExampleType provisionFooBar();
          }
          """);

      final JavaFileObject typeSource = prepareSourceFile("""
          package com.example;

          public class ExampleType {
              public final String value;

              public ExampleType(String value) throws Exception {
                  throw new Exception("hello");
              }
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  try {
                      Properties sys = new Properties();
                      sys.setProperty("FOO_BAR", "example");

                      DaggerExampleComponent.builder()
                          .rapierExampleComponentSystemPropertyModule(
                              new RapierExampleComponentSystemPropertyModule(sys))
                          .build()
                          .provisionFooBar();
                  } catch (Exception e) {
                      System.out.println(e.getClass().getName());
                  }
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, typeSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("java.lang.IllegalArgumentException", output);
  }

  @Test
  public void givenComponentWithNameTemplateEnvWithoutDefaultValue_whenCompileAndRunWithReferencedVariable_thenGetExpectedOutput() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @rapier.sysprop.SystemProperty("FOO_${env.QUUX}")
              public Integer provisionFooBarAsInt();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.HashMap;
          import java.util.Map;
          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  Map<String, String> env = new HashMap<>();
                  env.put("QUUX", "BAR");
              
                  Properties sys = new Properties();
                  sys.setProperty("FOO_BAR", "42");

                  final ExampleComponent component = DaggerExampleComponent.builder()
                      .rapierExampleComponentSystemPropertyModule(
                          new RapierExampleComponentSystemPropertyModule(env, sys))
                      .build();

                  System.out.println(component.provisionFooBarAsInt());
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("42", output);
  }

  @Test
  public void givenComponentWithNameTemplateEnvWithoutDefaultValue_whenCompileAndRunWithoutReferencedVariable_thenCatchException() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @rapier.sysprop.SystemProperty("FOO_${env.QUUX}")
              public Integer provisionFooBarAsInt();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  try {
                      Properties sys = new Properties();
                      sys.setProperty("FOO_BAR", "42");

                      final ExampleComponent component = DaggerExampleComponent.builder()
                          .rapierExampleComponentSystemPropertyModule(
                              new RapierExampleComponentSystemPropertyModule(sys))
                          .build();

                      System.out.println(component.provisionFooBarAsInt());
                  } catch (IllegalStateException e) {
                      System.out.println(e.getClass().getName());
                  }
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("java.lang.IllegalStateException", output);
  }


  @Test
  public void givenComponentWithNameTemplateEnvWithDefaultValue_whenCompileAndRunWithReferencedVariable_thenGetExpectedOutput() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @rapier.sysprop.SystemProperty("FOO_${env.QUUX:-PANTS}")
              public Integer provisionFooBarAsInt();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.HashMap;
          import java.util.Map;
          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  Map<String, String> env = new HashMap<>();
                  env.put("QUUX", "BAR");
              
                  Properties sys = new Properties();
                  sys.setProperty("FOO_BAR", "42");

                  final ExampleComponent component = DaggerExampleComponent.builder()
                      .rapierExampleComponentSystemPropertyModule(
                          new RapierExampleComponentSystemPropertyModule(env, sys))
                      .build();

                  System.out.println(component.provisionFooBarAsInt());
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("42", output);
  }

  @Test
  public void givenComponentWithNameTemplateEnvWithDefaultValue_whenCompileAndRunWithoutReferencedVariable_thenCatchException() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @rapier.sysprop.SystemProperty("FOO_${env.QUUX:-BAR}")
              public Integer provisionFooBarAsInt();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  Properties sys = new Properties();
                  sys.setProperty("FOO_BAR", "42");

                  final ExampleComponent component = DaggerExampleComponent.builder()
                      .rapierExampleComponentSystemPropertyModule(
                          new RapierExampleComponentSystemPropertyModule(sys))
                      .build();

                  System.out.println(component.provisionFooBarAsInt());
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("42", output);
  }

  @Test
  public void givenComponentWithNameTemplateSysWithoutDefaultValue_whenCompileAndRunWithReferencedVariable_thenGetExpectedOutput() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @rapier.sysprop.SystemProperty("FOO_${sys.QUUX}")
              public Integer provisionFooBarAsInt();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  Properties sys = new Properties();
                  sys.setProperty("FOO_BAR", "42");
                  sys.setProperty("QUUX", "BAR");

                  final ExampleComponent component = DaggerExampleComponent.builder()
                      .rapierExampleComponentSystemPropertyModule(
                          new RapierExampleComponentSystemPropertyModule(sys))
                      .build();

                  System.out.println(component.provisionFooBarAsInt());
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("42", output);
  }

  @Test
  public void givenComponentWithNameTemplateSysWithoutDefaultValue_whenCompileAndRunWithoutReferencedVariable_thenCatchException() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @rapier.sysprop.SystemProperty("FOO_${sys.QUUX}")
              public Integer provisionFooBarAsInt();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  try {
                      Properties sys = new Properties();
                      sys.setProperty("FOO_BAR", "42");

                      final ExampleComponent component = DaggerExampleComponent.builder()
                          .rapierExampleComponentSystemPropertyModule(
                              new RapierExampleComponentSystemPropertyModule(sys))
                          .build();

                      System.out.println(component.provisionFooBarAsInt());
                  } catch (IllegalStateException e) {
                      System.out.println(e.getClass().getName());
                  }
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("java.lang.IllegalStateException", output);
  }


  @Test
  public void givenComponentWithNameTemplateSysWithDefaultValue_whenCompileAndRunWithReferencedVariable_thenGetExpectedOutput() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @rapier.sysprop.SystemProperty("FOO_${sys.QUUX:-PANTS}")
              public Integer provisionFooBarAsInt();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  Properties sys = new Properties();
                  sys.setProperty("FOO_BAR", "42");
                  sys.setProperty("QUUX", "BAR");

                  final ExampleComponent component = DaggerExampleComponent.builder()
                      .rapierExampleComponentSystemPropertyModule(
                          new RapierExampleComponentSystemPropertyModule(sys))
                      .build();

                  System.out.println(component.provisionFooBarAsInt());
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

      assertThat(compilation).succeeded();

      final String output = doRun(compilation).trim();

      assertEquals("42", output);
  }

  @Test
  public void givenComponentWithNameTemplateSysWithDefaultValue_whenCompileAndRunWithoutReferencedVariable_thenCatchException() throws IOException {
      final JavaFileObject componentSource = prepareSourceFile("""
          package com.example;

          @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
          public interface ExampleComponent {
              @rapier.sysprop.SystemProperty("FOO_${env.QUUX:-BAR}")
              public Integer provisionFooBarAsInt();
          }
          """);

      final JavaFileObject appSource = prepareSourceFile("""
          package com.example;

          import java.util.Properties;

          public class App {
              public static void main(String[] args) {
                  Properties sys = new Properties();
                  sys.setProperty("FOO_BAR", "42");

                  final ExampleComponent component = DaggerExampleComponent.builder()
                      .rapierExampleComponentSystemPropertyModule(
                          new RapierExampleComponentSystemPropertyModule(sys))
                      .build();

                  System.out.println(component.provisionFooBarAsInt());
              }
          }
          """);

      final Compilation compilation = doCompile(componentSource, appSource);

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
