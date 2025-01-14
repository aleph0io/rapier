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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import rapier.core.RapierTestBase;

/**
 * Test code generation for modules
 */
public class CliProcessorSyntaxTest extends RapierTestBase {
  @Test
  public void givenComponentWithRequiredOptionAndStandardHelp_whenCompileAndRunWithoutOption_thenAppFailsWithHelpMessageAndSyntaxError()
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

    final String output = doRun(compilation, "-b", "-B", "0").trim();

    assertEquals("""
        Missing required option parameter -a / --alpha
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


        1""", output);
  }

  @Test
  public void givenComponentWithRequiredFlagAndStandardHelp_whenCompileAndRunWithoutFlag_thenAppFailsWithHelpMessageAndSyntaxError()
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

    final String output = doRun(compilation, "-a", "0", "0").trim();

    assertEquals("""
        Missing required flag parameter -b / --bravo / -B / --no-bravo
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


        1""", output);
  }

  @Test
  public void givenComponentWithRequiredPositionalAndStandardHelp_whenCompileAndRunWithoutPositional_thenAppFailsWithHelpMessageAndSyntaxError()
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

    final String output = doRun(compilation, "-a", "0", "-b").trim();

    assertEquals("""
        Missing required positional parameter 0
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


        1""", output);
  }

  @Test
  public void givenComponentWithFlagAndStandardHelp_whenCompileAndRunGivingFlagAValue_thenAppFailsWithHelpMessageAndSyntaxError()
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

    final String output = doRun(compilation, "-a", "0", "--bravo=true", "0").trim();

    assertEquals("""
        Flag --bravo does not take a value
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


        1""", output);
  }

  @Test
  public void givenComponentWithStandardHelp_whenCompileAndRunWithTooManyPositionalParameters_thenAppFailsWithHelpMessageAndSyntaxError()
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

    final String output = doRun(compilation, "-a", "0", "-b", "0", "1", "2").trim();

    assertEquals("""
        Too many positional parameters
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


        1""", output);
  }

  @Test
  public void givenComponentWithRequiredOptionWithoutStandardHelp_whenCompileAndRunWithoutOption_thenCatchInvalidSyntaxException()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @rapier.cli.CliCommandHelp(
            name="foobar",
            description="A simple commmand to foo the bar",
            provideStandardHelp=false,
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

    // We can't use assertThrows() here because our test uses a different ClassLoader, so any
    // exception fails the type test.
    Exception problem = null;
    try {
      doRun(compilation, "-b", "-B", "0");
    } catch (Exception e) {
      problem = e;
    }

    assertNotNull(problem);
    assertEquals(problem.getClass().getName(), "rapier.cli.CliSyntaxException");
    assertEquals(problem.getMessage(), "Missing required option parameter -a / --alpha");
  }

  @Test
  public void givenComponentWithRequiredFlagWithoutStandardHelp_whenCompileAndRunWithoutFlag_thenCatchInvalidSyntaxException()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @rapier.cli.CliCommandHelp(
            name="foobar",
            description="A simple commmand to foo the bar",
            provideStandardHelp=false,
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

    // We can't use assertThrows() here because our test uses a different ClassLoader, so any
    // exception fails the type test.
    Exception problem = null;
    try {
      doRun(compilation, "-a", "0", "0");
    } catch (Exception e) {
      problem = e;
    }

    assertNotNull(problem);
    assertEquals(problem.getClass().getName(), "rapier.cli.CliSyntaxException");
    assertEquals(problem.getMessage(),
        "Missing required flag parameter -b / --bravo / -B / --no-bravo");
  }

  @Test
  public void givenComponentWithRequiredPositionalWithoutStandardHelp_whenCompileAndRunWithoutPositional_thenCatchInvalidSyntaxException()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @rapier.cli.CliCommandHelp(
            name="foobar",
            description="A simple commmand to foo the bar",
            provideStandardHelp=false,
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

    // We can't use assertThrows() here because our test uses a different ClassLoader, so any
    // exception fails the type test.
    Exception problem = null;
    try {
      doRun(compilation, "-a", "0", "-b", "-B");
    } catch (Exception e) {
      problem = e;
    }

    assertNotNull(problem);
    assertEquals(problem.getClass().getName(), "rapier.cli.CliSyntaxException");
    assertEquals(problem.getMessage(), "Missing required positional parameter 0");
  }

  @Test
  public void givenComponentWithFlagWithoutStandardHelp_whenCompileAndRunGivingFlagAValue_thenAppFailsWithHelpMessageAndSyntaxError()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @rapier.cli.CliCommandHelp(
            name="foobar",
            description="A simple commmand to foo the bar",
            provideStandardHelp=false,
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

    // We can't use assertThrows() here because our test uses a different ClassLoader, so any
    // exception fails the type test.
    Exception problem = null;
    try {
      doRun(compilation, "-a", "0", "--bravo=true", "-B", "0");
    } catch (Exception e) {
      problem = e;
    }

    assertNotNull(problem);
    assertEquals(problem.getClass().getName(), "rapier.cli.CliSyntaxException");
    assertEquals(problem.getMessage(), "Flag --bravo does not take a value");
  }

  @Test
  public void givenComponentWithoutStandardHelp_whenCompileAndRunWithTooManyPositionalParameters_thenAppFailsWithHelpMessageAndSyntaxError()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @rapier.cli.CliCommandHelp(
            name="foobar",
            description="A simple commmand to foo the bar",
            provideStandardHelp=false,
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

    // We can't use assertThrows() here because our test uses a different ClassLoader, so any
    // exception fails the type test.
    Exception problem = null;
    try {
      doRun(compilation, "-a", "0", "-b", "-B", "0", "1", "2");
    } catch (Exception e) {
      problem = e;
    }

    assertNotNull(problem);
    assertEquals(problem.getClass().getName(), "rapier.cli.CliSyntaxException");
    assertEquals(problem.getMessage(), "Too many positional parameters");
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
