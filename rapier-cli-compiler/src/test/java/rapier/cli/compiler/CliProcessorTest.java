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
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import rapier.core.RapierTestBase;

public class CliProcessorTest extends RapierTestBase {
  @Test
  public void givenSimpleComponentWithEnvironmentVariableWithoutDefaultValue_whenCompile_thenExpectedtModuleIsGenerated()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source = prepareSourceFile("""
        package com.example;

        @rapier.cli.CliHelp(name="foobar", description="A simple commmand to foo the bar")
        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.PositionalCliParameter(0)
            @rapier.cli.CliHelp(name="zulu", description="The first zulu parameter")
            public Integer provisionPositional0AsInt();

            @rapier.cli.CliHelp(description="The value to use for alpha")
            @rapier.cli.OptionCliParameter(shortName = 'a', longName = "alpha")
            public Long provisionAlphaOptionAsLong();

            @rapier.cli.CliHelp(description="Whether or not to bravo")
            @rapier.cli.FlagCliParameter(positiveShortName = 'b', positiveLongName = "bravo",
                negativeShortName = 'B', negativeLongName = "no-bravo")
            public Boolean provisionBravoFlagAsBoolean();
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    // Assert generated file content
    // NOTE: If you get a parse error, it could be from the helpMessage() method below. It's easy
    // to forget that we're still quoted because triple quotes are so easy to use, but we are, so
    // we have to double-escape the newline.
    final JavaFileObject expectedOutput = JavaFileObjects.forSourceString(
        "com.example.RapierExampleComponentCliModule",
        """
            package com.example;

            import static java.util.Arrays.asList;
            import static java.util.Collections.emptyList;

            import dagger.Module;
            import dagger.Provides;
            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import javax.annotation.Nullable;
            import rapier.cli.FlagCliParameter;
            import rapier.cli.OptionCliParameter;
            import rapier.cli.PositionalCliParameter;
            import rapier.cli.compiler.thirdparty.com.sigpwned.just.args.JustArgs;

            @Module
            public class RapierExampleComponentCliModule {

                // Positional parameters
                /**
                 * Position 0
                 */
                private final String positional0;


                // Option parameters
                /**
                 * Option parameter
                 * shortName a
                 * longName alpha
                 */
                private final String optionda97be1;


                // Flag parameters
                /**
                 * Flag parameter
                 * positiveShortName b
                 * positiveLongName bravo
                 * negativeShortName B
                 * negativeLongName no-bravo
                 */
                private final Boolean flag59dcb7a;


                public RapierExampleComponentCliModule(String[] args) {
                    this(asList(args));
                }

                public RapierExampleComponentCliModule(List<String> args) {
                    // Generate the maps for option short names and long names
                    final Map<Character, String> optionShortNames = new HashMap<>();
                    final Map<String, String> optionLongNames = new HashMap<>();
                    optionShortNames.put('a', "da97be1");
                    optionLongNames.put("alpha", "da97be1");


                    // Generate the maps for flag short names and long names
                    final Map<Character, String> flagPositiveShortNames = new HashMap<>();
                    final Map<String, String> flagPositiveLongNames = new HashMap<>();
                    final Map<Character, String> flagNegativeShortNames = new HashMap<>();
                    final Map<String, String> flagNegativeLongNames = new HashMap<>();
                    flagPositiveShortNames.put('b', "59dcb7a");
                    flagPositiveLongNames.put("bravo", "59dcb7a");
                    flagNegativeShortNames.put('B', "59dcb7a");
                    flagNegativeLongNames.put("no-bravo", "59dcb7a");


                    // Parse the arguments
                    final JustArgs.ParsedArgs parsed = JustArgs.parseArgs(
                        args,
                        optionShortNames,
                        optionLongNames,
                        flagPositiveShortNames,
                        flagPositiveLongNames,
                        flagNegativeShortNames,
                        flagNegativeLongNames);

                    // Initialize positional parameters
                    if(parsed.getArgs().size() > 0) {
                        this.positional0 = parsed.getArgs().get(0);
                    } else {
                        throw new IllegalArgumentException(
                            "Missing required positional parameter 0");
                    }


                    // Initialize option parameters
                    if(parsed.getOptions().containsKey("da97be1")) {
                        List<String> optionda97be1 = parsed.getOptions().get("da97be1");
                        this.optionda97be1 = optionda97be1.get(optionda97be1.size()-1);
                    } else {
                        throw new IllegalArgumentException(
                            "Missing required option parameter -a, --alpha");
                    }


                    // Initialize flag parameters
                    if(parsed.getFlags().containsKey("59dcb7a")) {
                        List<Boolean> flag59dcb7a = parsed.getFlags().get("59dcb7a");
                        this.flag59dcb7a = flag59dcb7a.get(flag59dcb7a.size()-1);
                    } else {
                        throw new IllegalArgumentException(
                            "Missing required flag parameter -b, --bravo, -B, --no-bravo");
                    }

                }

                @Provides
                @PositionalCliParameter(0)
                public java.lang.Integer providePositional0AsInteger(@PositionalCliParameter(0) String value) {
                    return java.lang.Integer.valueOf(value);
                }

                @Provides
                @PositionalCliParameter(0)
                public String providePositional0AsString() {
                    return positional0;
                }

                @Provides
                @OptionCliParameter(shortName='a', longName="alpha")
                public java.lang.Long provideOptionda97be1AsLong(@OptionCliParameter(shortName='a', longName="alpha") String value) {
                    return java.lang.Long.valueOf(value);
                }

                @Provides
                @OptionCliParameter(shortName='a', longName="alpha")
                public String provideOptionda97be1AsString() {
                    return optionda97be1;
                }

                @Provides
                @FlagCliParameter(positiveShortName='b', positiveLongName="bravo", negativeShortName='B', negativeLongName="no-bravo")
                public Boolean provideFlag59dcb7aAsBoolean() {
                    return flag59dcb7a;
                }

                public String helpMessage() {
                    return String.join("\\n",
                        "Usage: foobar [OPTIONS | FLAGS] <zulu>",
                        "",
                        "Description: A simple commmand to foo the bar",
                        "",
                        "Positional parameters:",
                        "  zulu    The first zulu parameter",
                        "",
                        "Option parameters:",
                        "  -a, --alpha    The value to use for alpha",
                        "",
                        "Flag parameters:",
                        "  -b, --bravo       Whether or not to bravo",
                        "  -B, --no-bravo    ",
                        "",
                        "");
                }

            }
            """);

    assertThat(compilation).generatedSourceFile("com.example.RapierExampleComponentCliModule")
        .hasSourceEquivalentTo(expectedOutput);
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
