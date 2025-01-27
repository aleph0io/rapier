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

/**
 * Test code generation for modules
 */
public class CliProcessorStandardHelpAndVersionTest extends RapierTestBase {
  @Test
  public void givenComponentWithPositionalOptionFlagParametersAndStandardHelpAndStandardVersion_whenRunWithStandardHelpAndOtherArgs_thenPrintHelpAndExit()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @rapier.cli.CliCommandHelp(
            name="foobar",
            description="A simple commmand to foo the bar",
            testing=true)
        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliPositionalParameter(0)
            @rapier.cli.CliPositionalParameterHelp(
                name="zulu",
                description="The first zulu parameter")
            public Integer provisionPositional0AsInt();

            @rapier.cli.CliOptionParameter(shortName = 'a', longName = "alpha")
            @rapier.cli.CliOptionParameterHelp(
                valueName = "alpha",
                description="The value to use for alpha")
            public Long provisionAlphaOptionAsLong();

            @rapier.cli.CliFlagParameter(
                positiveShortName = 'b', positiveLongName = "bravo",
                negativeShortName = 'B', negativeLongName = "no-bravo")
            @rapier.cli.CliFlagParameterHelp(
                description = "Whether or not to bravo")
            public Boolean provisionBravoFlagAsBoolean();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        package com.example;

        public class App {
            public static void main(String[] args) {
                try {
                    DaggerExampleComponent.builder()
                        .rapierExampleComponentCliModule(new RapierExampleComponentCliModule(args))
                        .build();
                } catch (RapierExampleComponentCliModule.ExitException e) {
                    System.out.println(e.getStatus());
                }
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation, "-a", "0", "-b", "-B", "-h", "0").trim();

    assertEquals("""
        Usage: foobar [OPTIONS | FLAGS] <zulu>

        Description: A simple commmand to foo the bar

        Positional parameters:
          zulu              The first zulu parameter

        Option parameters:
          -a <alpha>, --alpha <alpha>
                            The value to use for alpha

        Flag parameters:
          -b, --bravo, -B, --no-bravo
                            Whether or not to bravo
          -h, --help        Print this help message and exit
          -V, --version     Print a version message and exit


        0""", output);
  }

  @Test
  public void givenComponentWithPositionalOptionFlagParametersAndStandardHelpAndStandardVersion_whenRunWithStandardVersionAndOtherArgs_thenPrintVersionAndExit()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @rapier.cli.CliCommandHelp(
            name="foobar",
            description="A simple commmand to foo the bar",
            testing=true)
        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliPositionalParameter(0)
            @rapier.cli.CliPositionalParameterHelp(
                name="zulu",
                description="The first zulu parameter")
            public Integer provisionPositional0AsInt();

            @rapier.cli.CliOptionParameter(shortName = 'a', longName = "alpha")
            @rapier.cli.CliOptionParameterHelp(
                valueName = "alpha",
                description="The value to use for alpha")
            public Long provisionAlphaOptionAsLong();

            @rapier.cli.CliFlagParameter(
                positiveShortName = 'b', positiveLongName = "bravo",
                negativeShortName = 'B', negativeLongName = "no-bravo")
            @rapier.cli.CliFlagParameterHelp(
                description = "Whether or not to bravo")
            public Boolean provisionBravoFlagAsBoolean();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        package com.example;

        public class App {
            public static void main(String[] args) {
                try {
                    DaggerExampleComponent.builder()
                        .rapierExampleComponentCliModule(new RapierExampleComponentCliModule(args))
                        .build();
                } catch (RapierExampleComponentCliModule.ExitException e) {
                    System.out.println(e.getStatus());
                }
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation, "-a", "0", "-b", "-B", "-V", "0").trim();

    assertEquals("""
        foobar version 0.0.0
        0""", output);
  }

  @Test
  public void givenComponentWithPositionalOptionFlagParametersAndStandardHelpAndStandardVersion_whenRunWithStandardHelpAndVersionAndOtherArgs_thenPrintHelpAndVersionAndExit()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @rapier.cli.CliCommandHelp(
            name="foobar",
            description="A simple commmand to foo the bar",
            testing=true)
        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliPositionalParameter(0)
            @rapier.cli.CliPositionalParameterHelp(
                name="zulu",
                description="The first zulu parameter")
            public Integer provisionPositional0AsInt();

            @rapier.cli.CliOptionParameter(shortName = 'a', longName = "alpha")
            @rapier.cli.CliOptionParameterHelp(
                valueName = "alpha",
                description="The value to use for alpha")
            public Long provisionAlphaOptionAsLong();

            @rapier.cli.CliFlagParameter(
                positiveShortName = 'b', positiveLongName = "bravo",
                negativeShortName = 'B', negativeLongName = "no-bravo")
            @rapier.cli.CliFlagParameterHelp(
                description = "Whether or not to bravo")
            public Boolean provisionBravoFlagAsBoolean();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        package com.example;

        public class App {
            public static void main(String[] args) {
                try {
                    DaggerExampleComponent.builder()
                        .rapierExampleComponentCliModule(new RapierExampleComponentCliModule(args))
                        .build();
                } catch (RapierExampleComponentCliModule.ExitException e) {
                    System.out.println(e.getStatus());
                }
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation, "-a", "0", "-b", "-B", "-h", "-V", "0").trim();

    assertEquals("""
        foobar version 0.0.0
        Usage: foobar [OPTIONS | FLAGS] <zulu>

        Description: A simple commmand to foo the bar

        Positional parameters:
          zulu              The first zulu parameter

        Option parameters:
          -a <alpha>, --alpha <alpha>
                            The value to use for alpha

        Flag parameters:
          -b, --bravo, -B, --no-bravo
                            Whether or not to bravo
          -h, --help        Print this help message and exit
          -V, --version     Print a version message and exit


        0""", output);
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
