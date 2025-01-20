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
 * Test the validation of CLI parameter positions and names
 */
public class CliProcessorValidationTest extends RapierTestBase {
  @Test
  public void givenComponentWithNegativePositionalParameter_whenCompiled_thenCompileError()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliPositionalParameter(-1)
            public String provisionPositionalAsString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation failed
    assertThat(compilation).failed();

    // Assert the compilation error message
    assertTrue(compilation.diagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
        && d.getMessage(null).contains("Positional parameter position must not be negative")));
  }

  @Test
  public void givenComponentWithOptionParameterWithNoNames_whenCompiled_thenCompileError()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliOptionParameter()
            public String provisionPositionalAsString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation failed
    assertThat(compilation).failed();

    // Assert the compilation error message
    assertTrue(compilation.diagnostics().stream()
        .anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR && d.getMessage(null)
            .contains("At least one of option parameter shortName, longName must be non-null")));
  }

  @Test
  public void givenComponentWithOptionParameterWithInvalidShortName_whenCompiled_thenCompileError()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliOptionParameter(shortName='!')
            public String provisionPositionalAsString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation failed
    assertThat(compilation).failed();

    // Assert the compilation error message
    assertTrue(compilation.diagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
        && d.getMessage(null).contains("Option parameter shortName is invalid")));
  }

  @Test
  public void givenComponentWithOptionParameterWithInvalidLongName_whenCompiled_thenCompileError()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliOptionParameter(longName="foo!")
            public String provisionPositionalAsString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation failed
    assertThat(compilation).failed();

    // Assert the compilation error message
    assertTrue(compilation.diagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
        && d.getMessage(null).contains("Option parameter longName is invalid")));
  }

  @Test
  public void givenComponentWithFlagParameterWithNoNames_whenCompiled_thenCompileError()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliFlagParameter()
            public String provisionPositionalAsString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation failed
    assertThat(compilation).failed();

    // Assert the compilation error message
    assertTrue(compilation.diagnostics().stream()
        .anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR && d.getMessage(null).contains(
            "At least one of flag parameter positiveShortName, positiveLongName, negativeShortName, negativeLongName must be non-null")));
  }

  @Test
  public void givenComponentWithFlagParameterWithInvalidPositiveShortName_whenCompiled_thenCompileError()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliFlagParameter(positiveShortName='!')
            public String provisionPositionalAsString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation failed
    assertThat(compilation).failed();

    // Assert the compilation error message
    assertTrue(compilation.diagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
        && d.getMessage(null).contains("Flag parameter positiveShortName is invalid")));
  }

  @Test
  public void givenComponentWithFlagParameterWithInvalidPositiveLongName_whenCompiled_thenCompileError()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliFlagParameter(positiveLongName="foo!")
            public String provisionPositionalAsString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation failed
    assertThat(compilation).failed();

    // Assert the compilation error message
    assertTrue(compilation.diagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
        && d.getMessage(null).contains("Flag parameter positiveLongName is invalid")));
  }

  @Test
  public void givenComponentWithFlagParameterWithInvalidNegativeShortName_whenCompiled_thenCompileError()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliFlagParameter(negativeShortName='!')
            public String provisionPositionalAsString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation failed
    assertThat(compilation).failed();

    // Assert the compilation error message
    assertTrue(compilation.diagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
        && d.getMessage(null).contains("Flag parameter negativeShortName is invalid")));
  }

  @Test
  public void givenComponentWithFlagParameterWithInvalidNegativeLongName_whenCompiled_thenCompileError()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliFlagParameter(negativeLongName="foo!")
            public String provisionPositionalAsString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation failed
    assertThat(compilation).failed();

    // Assert the compilation error message
    assertTrue(compilation.diagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
        && d.getMessage(null).contains("Flag parameter negativeLongName is invalid")));
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
