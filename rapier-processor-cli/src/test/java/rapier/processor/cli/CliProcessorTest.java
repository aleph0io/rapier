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
package rapier.processor.cli;

import static com.google.testing.compile.CompilationSubject.assertThat;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import rapier.core.DaggerTestBase;

public class CliProcessorTest extends DaggerTestBase {
  @Test
  public void givenSimpleComponentWithEnvironmentVariableWithoutDefaultValue_whenCompile_thenExpectedtModuleIsGenerated() {
    // Define the source file to test
    final JavaFileObject source = JavaFileObjects.forSourceString("com.example.ExampleComponent",
        """
            package com.example;

            @dagger.Component
            public interface ExampleComponent {
                @rapier.processor.cli.PositionalCliParameter(0)
                public Integer provisionPositional0AsInt();

                @rapier.processor.cli.OptionCliParameter(shortName = 'a', longName = "alpha")
                public Long provisionAlphaOptionAsLong();

                @rapier.processor.cli.FlagCliParameter(positiveShortName = 'b', positiveLongName = "bravo")
                public Boolean provisionBravoFlagAsBoolean();
            }
            """);

    // Run the annotation processor
    final Compilation compilation =
        Compiler.javac().withProcessors(new CliProcessor()).compile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    // Assert generated file content
    final JavaFileObject expectedOutput = JavaFileObjects.forSourceString(
        "com.example.RapierExampleComponentEnvironmentVariableModule",
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
            import rapier.processor.cli.FlagCliParameter;
            import rapier.processor.cli.OptionCliParameter;
            import rapier.processor.cli.PositionalCliParameter;
            import rapier.processor.cli.thirdparty.com.sigpwned.just.args.JustArgs;

            @Module
            public class RapierExampleComponentEnvironmentVariableModule {

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
                 */
                private final Boolean flage85f9a9;


                public RapierExampleComponentEnvironmentVariableModule(String[] args) {
                    this(asList(args));
                }

                public RapierExampleComponentEnvironmentVariableModule(List<String> args) {
                    // Generate the maps for option short names and long names
                    final Map<Character, String> optionShortNames = new HashMap<>();
                    final Map<String, String> optionLongNames = new HashMap<>();
                    optionShortNames.put('a', "da97be1");
                    optionLongNames.put("alpha", "da97be1");


                    // Generate the maps for flag short names and long names
                    final Map<Character, String> flagShortPositiveNames = new HashMap<>();
                    final Map<String, String> flagpositiveLongNames = new HashMap<>();
                    final Map<Character, String> flagnegativeShortNames = new HashMap<>();
                    final Map<String, String> flagnegativeLongNames = new HashMap<>();
                    flagShortPositiveNames.put('b', "e85f9a9");
                    flagpositiveLongNames.put("bravo", "e85f9a9");


                    // Parse the arguments
                    final JustArgs.ParsedArgs parsed = JustArgs.parseArgs(
                        args,
                        optionShortNames,
                        optionLongNames,
                        flagShortPositiveNames,
                        flagpositiveLongNames,
                        flagnegativeShortNames,
                        flagnegativeLongNames);

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
                            "Missing required option parameter -a / --alpha");
                    }


                    // Initialize flag parameters
                    if(parsed.getFlags().containsKey("e85f9a9")) {
                        List<Boolean> flage85f9a9 = parsed.getFlags().get("e85f9a9");
                        this.flage85f9a9 = flage85f9a9.get(flage85f9a9.size()-1);
                    } else {
                        throw new IllegalArgumentException(
                            "Missing required flag parameter -b / --bravo");
                    }

                }

                @Provides
                @PositionalCliParameter(0)
                public java.lang.Integer providePositional0AsInteger(@PositionalCliParameter(0) String value) {
                    return java.lang.Integer.valueOf(value);
                }

                @Provides
                @OptionCliParameter(shortName='a', longName="alpha")
                public java.lang.Long provideOptionda97be1AsLong(@OptionCliParameter(shortName='a', longName="alpha") String value) {
                    return java.lang.Long.valueOf(value);
                }

                @Provides
                @FlagCliParameter(positiveShortName='b', positiveLongName="bravo")
                public Boolean provideFlage85f9a9AsBoolean() {
                    return flage85f9a9;
                }

            }
            """);

    assertThat(compilation)
        .generatedSourceFile("com.example.RapierExampleComponentEnvironmentVariableModule")
        .hasSourceEquivalentTo(expectedOutput);
  }
}
