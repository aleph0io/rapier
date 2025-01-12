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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import rapier.core.RapierTestBase;

/**
 * Test code generation for modules
 */
public class CliProcessorRequirednessTest extends RapierTestBase {
  @Test
  public void givenComponentWithAmbiguousRequirednessOnPositionalParameter_whenCompiled_thenCompileFailureWithErrorMessage()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliPositionalParameter(0)
            public String provisionPositional0AsString();

            @javax.annotation.Nullable
            @rapier.cli.CliPositionalParameter(0)
            public Integer provisionPositional0AsInt();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation succeeded
    assertThat(compilation).failed();

    // Assert the compilation error message
    compilation.diagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
        && d.getMessage(null).contains("Ambiguous requiredness for positional parameter 0"));
  }

  @Test
  public void givenComponentWithAmbiguousRequirednessOnOptionParameter_whenCompiled_thenCompileFailureWithErrorMessage()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliOptionParameter(shortName='x')
            public String provisionOptionXAsString();

            @javax.annotation.Nullable
            @rapier.cli.CliOptionParameter(shortName='x')
            public Integer provisionOptionXAsInt();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation succeeded
    assertThat(compilation).failed();

    // Assert the compilation error message
    compilation.diagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
        && d.getMessage(null).contains("Ambiguous requiredness for option parameter -x"));
  }

  @Test
  public void givenComponentWithAmbiguousRequirednessOnFlagParameter_whenCompiled_thenCompileFailureWithErrorMessage()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliFlagParameter(shortName='x')
            public Boolean provisionFlagXAsBoolean();

            @javax.annotation.Nullable
            @rapier.cli.CliFlagParameter(shortName='x')
            public String provisionFlagXAsString();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource);

    // Assert the compilation succeeded
    assertThat(compilation).failed();

    // Assert the compilation error message
    compilation.diagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
        && d.getMessage(null).contains("Ambiguous requiredness for flag parameter -x"));
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
