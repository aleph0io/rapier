/*-
 * =================================LICENSE_START==================================
 * rapier-cli-compiler
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
package rapier.cli.compiler;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static java.util.Collections.unmodifiableList;
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
import rapier.compiler.core.RapierTestBase;

/**
 * Verify that positional parameters work as expected
 */
public class CliProcessorPositionalParameterTest extends RapierTestBase {
  @Test
  public void givenComponentWithValidRequiredOptionalVarargsParameters_whenCompiled_thenNoErrorMessage()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            // required
            @rapier.cli.CliPositionalParameter(0)
            public String provisionPositional0AsString();

            // optional, due to default value
            @rapier.cli.CliPositionalParameter(value=1, defaultValue="alpha")
            public String provisionPositional1AsString();

            // optional, due to @Nullable annotation
            @javax.annotation.Nullable
            @rapier.cli.CliPositionalParameter(value=2)
            public String provisionPositional2AsString();

            // varargs
            @rapier.cli.CliPositionalParameter(value=3)
            public java.util.List<String> provisionPositional3AsListOfString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();
  }

  @Test
  public void givenComponentWithRequiredAfterOptionalParameter_whenCompiled_thenGetExpectedErrorMessage()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            // optional, due to default value
            @rapier.cli.CliPositionalParameter(value=0, defaultValue="alpha")
            public String provisionPositional0AsString();

            // required
            @rapier.cli.CliPositionalParameter(1)
            public String provisionPositional1AsString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation succeeded
    assertThat(compilation).failed();

    assertTrue(
        compilation.errors().stream()
            .anyMatch(d -> d.getMessage(Locale.getDefault())
                .contains("Required positional parameter 1 follows optional parameter")),
        "Expected error message not found");
  }

  @Test
  public void givenComponentWithParameterAfterVarargs_whenCompiled_thenGetExpectedErrorMessage()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            // varargs
            @rapier.cli.CliPositionalParameter(0)
            public java.util.List<String> provisionPositional0AsListOfString();

            // error, since after varargs
            @javax.annotation.Nullable
            @rapier.cli.CliPositionalParameter(1)
            public String provisionPositional1AsString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation succeeded
    assertThat(compilation).failed();

    assertTrue(
        compilation.errors().stream().anyMatch(d -> d.getMessage(Locale.getDefault()).contains(
            "Positional parameter 0 appears to be varargs parameter, but is not last positional parameter")),
        "Expected error message not found");
    assertTrue(
        compilation.errors().stream().anyMatch(d -> d.getMessage(Locale.getDefault()).contains(
            "Positional parameter 1 follows varargs parameter, which should be last positional parameter")),
        "Expected error message not found");
  }

  @Test
  public void givenComponentWithParameterWithAmbiguousListiness_whenCompiled_thenGetExpectedErrorMessage()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            // position 0 as a list
            @rapier.cli.CliPositionalParameter(0)
            public java.util.List<String> provisionPositional0AsListOfString();

            // position 0 as a string
            @rapier.cli.CliPositionalParameter(0)
            public String provisionPositiona01AsString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation succeeded
    assertThat(compilation).failed();

    assertTrue(
        compilation.errors().stream().anyMatch(d -> d.getMessage(Locale.getDefault()).contains(
            "Positional parameter 0 can partially be converted to String and List<String>, but cannot completely be converted to either")),
        "Expected error message not found");
  }

  /**
   * We need to include the generated classes from the rapier-cli module in the classpath for our
   * tests.
   */
  @Override
  protected List<File> getCompileClasspath() throws FileNotFoundException {
    List<File> result = new ArrayList<>();
    result.addAll(super.getCompileClasspath());
    result.add(resolveProjectFile("../rapier-cli/target/classes"));
    result.add(resolveProjectFile("../rapier-core/target/classes"));
    return unmodifiableList(result);
  }
}
