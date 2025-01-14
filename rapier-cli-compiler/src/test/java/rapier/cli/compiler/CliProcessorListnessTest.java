/*-
 * =================================LICENSE_START==================================
 * rapier-processor-cli
 * ====================================SECTION=====================================
 * Copyright (C) 2024 - 2025 Andy Boothe
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
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import rapier.compiler.core.RapierTestBase;

/**
 * Test code generation for modules
 */
public class CliProcessorListnessTest extends RapierTestBase {
  /**
   * Positional parameters are NOT allowed to treat their data as both a list and a scalar. A list
   * means varargs, and a scalar means a single value, so the difference is semantically important.
   */
  @Test
  public void givenComponentWithAmbiguousListnessOnPositionalParameter_whenCompiled_thenCompileFailureWithErrorMessage()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliPositionalParameter(0)
            public String provisionPositional0AsString();

            @rapier.cli.CliPositionalParameter(0)
            public java.util.List<String> provisionPositional0AsListOfString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation failed
    assertThat(compilation).failed();

    // Assert the compilation error message
    assertTrue(compilation.diagnostics().stream()
        .anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR && d.getMessage(null).contains(
            "Positional parameter 0 can partially be converted to String and List<String>, but cannot completely be converted to either")),
        "Expected error message not found in diagnostics");
  }

  /**
   * Option parameters are allowed to treat their data as both a list and a scalar
   */
  @Test
  public void givenComponentWithAmbiguousListnessOnOptionParameter_whenCompiled_thenCompileSucceeds()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliOptionParameter(shortName='x')
            public String provisionOptionXAsString();

            @rapier.cli.CliOptionParameter(shortName='x')
            public java.util.List<String> provisionOptionXAsListOfString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();
  }

  /**
   * Flag parameters are allowed to treat their data as both a list and a scalar
   */
  @Test
  public void givenComponentWithAmbiguousListnessOnFlagParameter_whenCompiled_thenCompileFSucceeds()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliFlagParameter(positiveShortName='x')
            public Boolean provisionFlagXAsBoolean();

            @rapier.cli.CliFlagParameter(positiveShortName='x')
            public java.util.List<Boolean> provisionFlagXAsListOfBoolean();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();
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
    return unmodifiableList(result);
  }
}
