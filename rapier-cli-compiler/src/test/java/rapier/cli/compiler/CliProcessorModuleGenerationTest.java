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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import rapier.compiler.core.RapierTestBase;

/**
 * Test code generation for modules
 */
@Disabled("Disabled until generated source code settles")
public class CliProcessorModuleGenerationTest extends RapierTestBase {
  @Test
  public void givenComponentWithPositionalOptionFlagParametersAndStandardHelpAndStandardVersion_whenCompile_thenExpectedtModuleIsGenerated()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source = prepareSourceFile("""
        package com.example;

        @rapier.cli.CliCommandHelp(
            name="foobar",
            description="A simple commmand to foo the bar")
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

    // Run the annotation processor
    final Compilation compilation = doCompile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    // Assert generated file content
    // NOTE: If you get a parse error, it could be from the helpMessage() method below. It's easy
    // to forget that we're still quoted because triple quotes are so easy to use, but we are, so
    // we have to double-escape the newline.
    final JavaFileObject expectedOutput = prepareSourceFile(
        """
            package com.example;

            import static java.util.Arrays.asList;
            import static java.util.Collections.emptyList;
            import static java.util.Collections.singletonList;
            import static java.util.Collections.unmodifiableList;

            import dagger.Module;
            import dagger.Provides;
            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import javax.annotation.Nullable;
            import javax.annotation.processing.Generated;
            import rapier.internal.RapierGenerated;
            import rapier.cli.CliSyntaxException;
            import rapier.cli.CliFlagParameter;
            import rapier.cli.CliOptionParameter;
            import rapier.cli.CliPositionalParameter;
            import rapier.cli.thirdparty.com.sigpwned.just.args.JustArgs;

            @Module
            @RapierGenerated
            @Generated(
                value = "rapier.cli.compiler.CliProcessor@1.2.3",
                comments = "https://www.example.com",
                date = "2024-01-01T12:34:56Z")
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
                private final List<String> optionda97be1;


                // Flag parameters
                /**
                 * Flag parameter
                 * positiveShortName b
                 * positiveLongName bravo
                 * negativeShortName B
                 * negativeLongName no-bravo
                 */
                private final List<Boolean> flag59dcb7a;


                public RapierExampleComponentCliModule(String[] args) {
                    this(asList(args));
                }

                public RapierExampleComponentCliModule(List<String> args) {
                    // Generate the maps for option short names and long names
                    final Map<Character, String> optionShortNames = new HashMap<>();
                    final Map<String, String> optionLongNames = new HashMap<>();
                    optionShortNames.put('a', "rapier.option.da97be1");
                    optionLongNames.put("alpha", "rapier.option.da97be1");


                    // Generate the maps for flag short names and long names
                    final Map<Character, String> flagPositiveShortNames = new HashMap<>();
                    final Map<String, String> flagPositiveLongNames = new HashMap<>();
                    final Map<Character, String> flagNegativeShortNames = new HashMap<>();
                    final Map<String, String> flagNegativeLongNames = new HashMap<>();
                    flagPositiveShortNames.put('b', "rapier.flag.59dcb7a");
                    flagPositiveLongNames.put("bravo", "rapier.flag.59dcb7a");
                    flagNegativeShortNames.put('B', "rapier.flag.59dcb7a");
                    flagNegativeLongNames.put("no-bravo", "rapier.flag.59dcb7a");

                    // Add the standard help flags
                    flagPositiveShortNames.put('h', "rapier.standard.help");
                    flagPositiveLongNames.put("help", "rapier.standard.help");

                    // Add the version flag
                    flagPositiveShortNames.put('V', "rapier.standard.version");
                    flagPositiveLongNames.put("version", "rapier.standard.version");


                    try {
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
                        if(parsed.getArgs().size() <= 0) {
                            throw new CliSyntaxException(
                                "Missing required positional parameter 0");
                        } else {
                            this.positional0 = parsed.getArgs().get(0);
                        }

                        if(parsed.getArgs().size() > 1) {
                            throw new CliSyntaxException(
                                "Too many positional parameters");
                        }

                        // Initialize option parameters
                        if(parsed.getOptions().getOrDefault("rapier.option.da97be1", emptyList()).isEmpty()) {
                            throw new CliSyntaxException(
                                "Missing required option parameter -a / --alpha");
                        } else {
                            this.optionda97be1 = unmodifiableList(parsed.getOptions().get("rapier.option.da97be1"));
                        }


                        // Initialize flag parameters
                        if(parsed.getFlags().getOrDefault("rapier.flag.59dcb7a", emptyList()).isEmpty()) {
                            throw new CliSyntaxException(
                                "Missing required flag parameter -b / --bravo / -B / --no-bravo");
                        } else {
                            this.flag59dcb7a = unmodifiableList(parsed.getFlags().get("rapier.flag.59dcb7a"));
                        }

                        // Check for standard help
                        final boolean standardHelpRequested = parsed.getFlags().containsKey("rapier.standard.help");

                        // Check for standard version
                        final boolean standardVersionRequested = parsed.getFlags().containsKey("rapier.standard.version");

                        if(standardVersionRequested) {
                            System.err.println(standardVersionMessage());
                        }

                        if(standardHelpRequested) {
                            System.err.println(standardHelpMessage());
                        }

                        if(standardHelpRequested || standardVersionRequested) {
                            System.exit(0);
                            throw new AssertionError("exited");
                        }
                    }
                    catch (JustArgs.IllegalSyntaxException e) {
                        // Standard help is active. Print the help message and exit.
                        System.err.println(e.getMessage());
                        System.err.println(standardHelpMessage());
                        System.exit(1);
                        throw new AssertionError("exited");
                    }
                    catch(CliSyntaxException e) {
                        // Standard help is active. Print the help message and exit.
                        System.err.println(e.getMessage());
                        System.err.println(standardHelpMessage());
                        System.exit(1);
                        throw new AssertionError("exited");
                    }
                }

                @Provides
                @CliPositionalParameter(0)
                public java.lang.Integer providePositional0AsInteger(@CliPositionalParameter(0) String value) {
                    return java.lang.Integer.valueOf(value);
                }

                @Provides
                @CliPositionalParameter(0)
                public String providePositional0AsString() {
                    return positional0;
                }

                @Provides
                @CliOptionParameter(shortName='a', longName="alpha")
                public java.lang.Long provideOptionda97be1AsLong(@CliOptionParameter(shortName='a', longName="alpha") String value) {
                    return java.lang.Long.valueOf(value);
                }

                @Provides
                @CliOptionParameter(shortName='a', longName="alpha")
                public String provideOptionda97be1AsString(
                        @CliOptionParameter(shortName='a', longName="alpha") List<String> values) {
                    return values.get(values.size()-1);
                }

                @Provides
                @CliOptionParameter(shortName='a', longName="alpha")
                public List<String> provideOptionda97be1AsListOfString() {
                    return optionda97be1;
                }

                @Provides
                @CliFlagParameter(positiveShortName='b', positiveLongName="bravo", negativeShortName='B', negativeLongName="no-bravo")
                public Boolean provideFlag59dcb7aAsBoolean(
                        @CliFlagParameter(positiveShortName='b', positiveLongName="bravo", negativeShortName='B', negativeLongName="no-bravo") List<Boolean> values) {
                    return values.get(values.size()-1);
                }

                @Provides
                @CliFlagParameter(positiveShortName='b', positiveLongName="bravo", negativeShortName='B', negativeLongName="no-bravo")
                public List<Boolean> provideFlag59dcb7aAsListOfBoolean() {
                    return flag59dcb7a;
                }

                public String standardHelpMessage() {
                    return String.join("\\n",
                        "Usage: foobar [OPTIONS | FLAGS] <zulu>",
                        "",
                        "Description: A simple commmand to foo the bar",
                        "",
                        "Positional parameters:",
                        "  zulu              The first zulu parameter",
                        "",
                        "Option parameters:",
                        "  -a <alpha>, --alpha <alpha>",
                        "                    The value to use for alpha",
                        "",
                        "Flag parameters:",
                        "  -b, --bravo, -B, --no-bravo",
                        "                    Whether or not to bravo",
                        "  -h, --help        Print this help message and exit",
                        "  -V, --version     Print a version message and exit",
                        "",
                        "");
                }

                public String standardVersionMessage() {
                    return "foobar version 0.0.0";
                }

            }
            """);

    assertThat(compilation).generatedSourceFile("com.example.RapierExampleComponentCliModule")
        .hasSourceEquivalentTo(expectedOutput);
  }

  @Test
  public void givenComponentWithPositionalOptionFlagParametersAndStandardHelp_whenCompile_thenExpectedtModuleIsGenerated()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source = prepareSourceFile("""
        package com.example;

        @rapier.cli.CliCommandHelp(
            name="foobar",
            description="A simple commmand to foo the bar",
            provideStandardVersion=false)
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

    // Run the annotation processor
    final Compilation compilation = doCompile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    // Assert generated file content
    // NOTE: If you get a parse error, it could be from the helpMessage() method below. It's easy
    // to forget that we're still quoted because triple quotes are so easy to use, but we are, so
    // we have to double-escape the newline.
    final JavaFileObject expectedOutput = prepareSourceFile(
        """
            package com.example;

            import static java.util.Arrays.asList;
            import static java.util.Collections.emptyList;
            import static java.util.Collections.singletonList;
            import static java.util.Collections.unmodifiableList;

            import dagger.Module;
            import dagger.Provides;
            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import javax.annotation.Nullable;
            import javax.annotation.processing.Generated;
            import rapier.internal.RapierGenerated;
            import rapier.cli.CliSyntaxException;
            import rapier.cli.CliFlagParameter;
            import rapier.cli.CliOptionParameter;
            import rapier.cli.CliPositionalParameter;
            import rapier.cli.thirdparty.com.sigpwned.just.args.JustArgs;

            @Module
            @RapierGenerated
            @Generated(
                value = "rapier.cli.compiler.CliProcessor@1.2.3",
                comments = "https://www.example.com",
                date = "2024-01-01T12:34:56Z")
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
                private final List<String> optionda97be1;


                // Flag parameters
                /**
                 * Flag parameter
                 * positiveShortName b
                 * positiveLongName bravo
                 * negativeShortName B
                 * negativeLongName no-bravo
                 */
                private final List<Boolean> flag59dcb7a;


                public RapierExampleComponentCliModule(String[] args) {
                    this(asList(args));
                }

                public RapierExampleComponentCliModule(List<String> args) {
                    // Generate the maps for option short names and long names
                    final Map<Character, String> optionShortNames = new HashMap<>();
                    final Map<String, String> optionLongNames = new HashMap<>();
                    optionShortNames.put('a', "rapier.option.da97be1");
                    optionLongNames.put("alpha", "rapier.option.da97be1");


                    // Generate the maps for flag short names and long names
                    final Map<Character, String> flagPositiveShortNames = new HashMap<>();
                    final Map<String, String> flagPositiveLongNames = new HashMap<>();
                    final Map<Character, String> flagNegativeShortNames = new HashMap<>();
                    final Map<String, String> flagNegativeLongNames = new HashMap<>();
                    flagPositiveShortNames.put('b', "rapier.flag.59dcb7a");
                    flagPositiveLongNames.put("bravo", "rapier.flag.59dcb7a");
                    flagNegativeShortNames.put('B', "rapier.flag.59dcb7a");
                    flagNegativeLongNames.put("no-bravo", "rapier.flag.59dcb7a");

                    // Add the standard help flags
                    flagPositiveShortNames.put('h', "rapier.standard.help");
                    flagPositiveLongNames.put("help", "rapier.standard.help");


                    try {
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
                        if(parsed.getArgs().size() <= 0) {
                            throw new CliSyntaxException(
                                "Missing required positional parameter 0");
                        } else {
                            this.positional0 = parsed.getArgs().get(0);
                        }

                        if(parsed.getArgs().size() > 1) {
                            throw new CliSyntaxException(
                                "Too many positional parameters");
                        }

                        // Initialize option parameters
                        if(parsed.getOptions().getOrDefault("rapier.option.da97be1", emptyList()).isEmpty()) {
                            throw new CliSyntaxException(
                                "Missing required option parameter -a / --alpha");
                        } else {
                            this.optionda97be1 = unmodifiableList(parsed.getOptions().get("rapier.option.da97be1"));
                        }


                        // Initialize flag parameters
                        if(parsed.getFlags().getOrDefault("rapier.flag.59dcb7a", emptyList()).isEmpty()) {
                            throw new CliSyntaxException(
                                "Missing required flag parameter -b / --bravo / -B / --no-bravo");
                        } else {
                            this.flag59dcb7a = unmodifiableList(parsed.getFlags().get("rapier.flag.59dcb7a"));
                        }

                        // Check for standard help
                        final boolean standardHelpRequested = parsed.getFlags().containsKey("rapier.standard.help");

                        if(standardHelpRequested) {
                            System.err.println(standardHelpMessage());
                        }

                        if(standardHelpRequested) {
                            System.exit(0);
                            throw new AssertionError("exited");
                        }
                    }
                    catch (JustArgs.IllegalSyntaxException e) {
                        // Standard help is active. Print the help message and exit.
                        System.err.println(e.getMessage());
                        System.err.println(standardHelpMessage());
                        System.exit(1);
                        throw new AssertionError("exited");
                    }
                    catch(CliSyntaxException e) {
                        // Standard help is active. Print the help message and exit.
                        System.err.println(e.getMessage());
                        System.err.println(standardHelpMessage());
                        System.exit(1);
                        throw new AssertionError("exited");
                    }
                }

                @Provides
                @CliPositionalParameter(0)
                public java.lang.Integer providePositional0AsInteger(@CliPositionalParameter(0) String value) {
                    return java.lang.Integer.valueOf(value);
                }

                @Provides
                @CliPositionalParameter(0)
                public String providePositional0AsString() {
                    return positional0;
                }

                @Provides
                @CliOptionParameter(shortName='a', longName="alpha")
                public java.lang.Long provideOptionda97be1AsLong(@CliOptionParameter(shortName='a', longName="alpha") String value) {
                    return java.lang.Long.valueOf(value);
                }

                @Provides
                @CliOptionParameter(shortName='a', longName="alpha")
                public String provideOptionda97be1AsString(
                        @CliOptionParameter(shortName='a', longName="alpha") List<String> values) {
                    return values.get(values.size()-1);
                }

                @Provides
                @CliOptionParameter(shortName='a', longName="alpha")
                public List<String> provideOptionda97be1AsListOfString() {
                    return optionda97be1;
                }

                @Provides
                @CliFlagParameter(positiveShortName='b', positiveLongName="bravo", negativeShortName='B', negativeLongName="no-bravo")
                public Boolean provideFlag59dcb7aAsBoolean(
                        @CliFlagParameter(positiveShortName='b', positiveLongName="bravo", negativeShortName='B', negativeLongName="no-bravo") List<Boolean> values) {
                    return values.get(values.size()-1);
                }

                @Provides
                @CliFlagParameter(positiveShortName='b', positiveLongName="bravo", negativeShortName='B', negativeLongName="no-bravo")
                public List<Boolean> provideFlag59dcb7aAsListOfBoolean() {
                    return flag59dcb7a;
                }

                public String standardHelpMessage() {
                    return String.join("\\n",
                        "Usage: foobar [OPTIONS | FLAGS] <zulu>",
                        "",
                        "Description: A simple commmand to foo the bar",
                        "",
                        "Positional parameters:",
                        "  zulu              The first zulu parameter",
                        "",
                        "Option parameters:",
                        "  -a <alpha>, --alpha <alpha>",
                        "                    The value to use for alpha",
                        "",
                        "Flag parameters:",
                        "  -b, --bravo, -B, --no-bravo",
                        "                    Whether or not to bravo",
                        "  -h, --help        Print this help message and exit",
                        "",
                        "");
                }

            }
            """);

    assertThat(compilation).generatedSourceFile("com.example.RapierExampleComponentCliModule")
        .hasSourceEquivalentTo(expectedOutput);
  }

  @Test
  public void givenComponentWithPositionalOptionFlagParametersAndStandardVersion_whenCompile_thenExpectedtModuleIsGenerated()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source = prepareSourceFile("""
        package com.example;

        @rapier.cli.CliCommandHelp(
            name="foobar",
            description="A simple commmand to foo the bar",
            provideStandardHelp=false)
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

    // Run the annotation processor
    final Compilation compilation = doCompile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    // Assert generated file content
    // NOTE: If you get a parse error, it could be from the helpMessage() method below. It's easy
    // to forget that we're still quoted because triple quotes are so easy to use, but we are, so
    // we have to double-escape the newline.
    final JavaFileObject expectedOutput = prepareSourceFile(
        """
            package com.example;

            import static java.util.Arrays.asList;
            import static java.util.Collections.emptyList;
            import static java.util.Collections.singletonList;
            import static java.util.Collections.unmodifiableList;

            import dagger.Module;
            import dagger.Provides;
            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import javax.annotation.Nullable;
            import javax.annotation.processing.Generated;
            import rapier.internal.RapierGenerated;
            import rapier.cli.CliSyntaxException;
            import rapier.cli.CliFlagParameter;
            import rapier.cli.CliOptionParameter;
            import rapier.cli.CliPositionalParameter;
            import rapier.cli.thirdparty.com.sigpwned.just.args.JustArgs;

            @Module
            @RapierGenerated
            @Generated(
                value = "rapier.cli.compiler.CliProcessor@1.2.3",
                comments = "https://www.example.com",
                date = "2024-01-01T12:34:56Z")
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
                private final List<String> optionda97be1;


                // Flag parameters
                /**
                 * Flag parameter
                 * positiveShortName b
                 * positiveLongName bravo
                 * negativeShortName B
                 * negativeLongName no-bravo
                 */
                private final List<Boolean> flag59dcb7a;


                public RapierExampleComponentCliModule(String[] args) {
                    this(asList(args));
                }

                public RapierExampleComponentCliModule(List<String> args) {
                    // Generate the maps for option short names and long names
                    final Map<Character, String> optionShortNames = new HashMap<>();
                    final Map<String, String> optionLongNames = new HashMap<>();
                    optionShortNames.put('a', "rapier.option.da97be1");
                    optionLongNames.put("alpha", "rapier.option.da97be1");


                    // Generate the maps for flag short names and long names
                    final Map<Character, String> flagPositiveShortNames = new HashMap<>();
                    final Map<String, String> flagPositiveLongNames = new HashMap<>();
                    final Map<Character, String> flagNegativeShortNames = new HashMap<>();
                    final Map<String, String> flagNegativeLongNames = new HashMap<>();
                    flagPositiveShortNames.put('b', "rapier.flag.59dcb7a");
                    flagPositiveLongNames.put("bravo", "rapier.flag.59dcb7a");
                    flagNegativeShortNames.put('B', "rapier.flag.59dcb7a");
                    flagNegativeLongNames.put("no-bravo", "rapier.flag.59dcb7a");

                    // Add the version flag
                    flagPositiveShortNames.put('V', "rapier.standard.version");
                    flagPositiveLongNames.put("version", "rapier.standard.version");


                    try {
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
                        if(parsed.getArgs().size() <= 0) {
                            throw new CliSyntaxException(
                                "Missing required positional parameter 0");
                        } else {
                            this.positional0 = parsed.getArgs().get(0);
                        }

                        if(parsed.getArgs().size() > 1) {
                            throw new CliSyntaxException(
                                "Too many positional parameters");
                        }

                        // Initialize option parameters
                        if(parsed.getOptions().getOrDefault("rapier.option.da97be1", emptyList()).isEmpty()) {
                            throw new CliSyntaxException(
                                "Missing required option parameter -a / --alpha");
                        } else {
                            this.optionda97be1 = unmodifiableList(parsed.getOptions().get("rapier.option.da97be1"));
                        }


                        // Initialize flag parameters
                        if(parsed.getFlags().getOrDefault("rapier.flag.59dcb7a", emptyList()).isEmpty()) {
                            throw new CliSyntaxException(
                                "Missing required flag parameter -b / --bravo / -B / --no-bravo");
                        } else {
                            this.flag59dcb7a = unmodifiableList(parsed.getFlags().get("rapier.flag.59dcb7a"));
                        }

                        // Check for standard version
                        final boolean standardVersionRequested = parsed.getFlags().containsKey("rapier.standard.version");

                        if(standardVersionRequested) {
                            System.err.println(standardVersionMessage());
                        }

                        if(standardVersionRequested) {
                            System.exit(0);
                            throw new AssertionError("exited");
                        }
                    }
                    catch (JustArgs.IllegalSyntaxException e) {
                        // Standard help is not active. Map and propagate the exception.
                        throw new CliSyntaxException(e.getMessage());
                    }
                    catch(CliSyntaxException e) {
                        // Standard help is not active. Propagate the exception.
                        throw e;
                    }
                }

                @Provides
                @CliPositionalParameter(0)
                public java.lang.Integer providePositional0AsInteger(@CliPositionalParameter(0) String value) {
                    return java.lang.Integer.valueOf(value);
                }

                @Provides
                @CliPositionalParameter(0)
                public String providePositional0AsString() {
                    return positional0;
                }

                @Provides
                @CliOptionParameter(shortName='a', longName="alpha")
                public java.lang.Long provideOptionda97be1AsLong(@CliOptionParameter(shortName='a', longName="alpha") String value) {
                    return java.lang.Long.valueOf(value);
                }

                @Provides
                @CliOptionParameter(shortName='a', longName="alpha")
                public String provideOptionda97be1AsString(
                        @CliOptionParameter(shortName='a', longName="alpha") List<String> values) {
                    return values.get(values.size()-1);
                }

                @Provides
                @CliOptionParameter(shortName='a', longName="alpha")
                public List<String> provideOptionda97be1AsListOfString() {
                    return optionda97be1;
                }

                @Provides
                @CliFlagParameter(positiveShortName='b', positiveLongName="bravo", negativeShortName='B', negativeLongName="no-bravo")
                public Boolean provideFlag59dcb7aAsBoolean(
                        @CliFlagParameter(positiveShortName='b', positiveLongName="bravo", negativeShortName='B', negativeLongName="no-bravo") List<Boolean> values) {
                    return values.get(values.size()-1);
                }

                @Provides
                @CliFlagParameter(positiveShortName='b', positiveLongName="bravo", negativeShortName='B', negativeLongName="no-bravo")
                public List<Boolean> provideFlag59dcb7aAsListOfBoolean() {
                    return flag59dcb7a;
                }

                public String standardVersionMessage() {
                    return "foobar version 0.0.0";
                }

            }
            """);

    assertThat(compilation).generatedSourceFile("com.example.RapierExampleComponentCliModule")
        .hasSourceEquivalentTo(expectedOutput);
  }

  @Test
  public void givenComponentWithPositionalOptionFlagParameters_whenCompile_thenExpectedtModuleIsGenerated()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source = prepareSourceFile("""
        package com.example;

        @rapier.cli.CliCommandHelp(
            name="foobar",
            description="A simple commmand to foo the bar",
            provideStandardVersion=false,
            provideStandardHelp=false)
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

    // Run the annotation processor
    final Compilation compilation = doCompile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    // Assert generated file content
    // NOTE: If you get a parse error, it could be from the helpMessage() method below. It's easy
    // to forget that we're still quoted because triple quotes are so easy to use, but we are, so
    // we have to double-escape the newline.
    final JavaFileObject expectedOutput = prepareSourceFile(
        """
            package com.example;

            import static java.util.Arrays.asList;
            import static java.util.Collections.emptyList;
            import static java.util.Collections.singletonList;
            import static java.util.Collections.unmodifiableList;

            import dagger.Module;
            import dagger.Provides;
            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import javax.annotation.Nullable;
            import javax.annotation.processing.Generated;
            import rapier.internal.RapierGenerated;
            import rapier.cli.CliSyntaxException;
            import rapier.cli.CliFlagParameter;
            import rapier.cli.CliOptionParameter;
            import rapier.cli.CliPositionalParameter;
            import rapier.cli.thirdparty.com.sigpwned.just.args.JustArgs;

            @Module
            @RapierGenerated
            @Generated(
                value = "rapier.cli.compiler.CliProcessor@1.2.3",
                comments = "https://www.example.com",
                date = "2024-01-01T12:34:56Z")
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
                private final List<String> optionda97be1;


                // Flag parameters
                /**
                 * Flag parameter
                 * positiveShortName b
                 * positiveLongName bravo
                 * negativeShortName B
                 * negativeLongName no-bravo
                 */
                private final List<Boolean> flag59dcb7a;


                public RapierExampleComponentCliModule(String[] args) {
                    this(asList(args));
                }

                public RapierExampleComponentCliModule(List<String> args) {
                    // Generate the maps for option short names and long names
                    final Map<Character, String> optionShortNames = new HashMap<>();
                    final Map<String, String> optionLongNames = new HashMap<>();
                    optionShortNames.put('a', "rapier.option.da97be1");
                    optionLongNames.put("alpha", "rapier.option.da97be1");


                    // Generate the maps for flag short names and long names
                    final Map<Character, String> flagPositiveShortNames = new HashMap<>();
                    final Map<String, String> flagPositiveLongNames = new HashMap<>();
                    final Map<Character, String> flagNegativeShortNames = new HashMap<>();
                    final Map<String, String> flagNegativeLongNames = new HashMap<>();
                    flagPositiveShortNames.put('b', "rapier.flag.59dcb7a");
                    flagPositiveLongNames.put("bravo", "rapier.flag.59dcb7a");
                    flagNegativeShortNames.put('B', "rapier.flag.59dcb7a");
                    flagNegativeLongNames.put("no-bravo", "rapier.flag.59dcb7a");


                    try {
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
                        if(parsed.getArgs().size() <= 0) {
                            throw new CliSyntaxException(
                                "Missing required positional parameter 0");
                        } else {
                            this.positional0 = parsed.getArgs().get(0);
                        }

                        if(parsed.getArgs().size() > 1) {
                            throw new CliSyntaxException(
                                "Too many positional parameters");
                        }

                        // Initialize option parameters
                        if(parsed.getOptions().getOrDefault("rapier.option.da97be1", emptyList()).isEmpty()) {
                            throw new CliSyntaxException(
                                "Missing required option parameter -a / --alpha");
                        } else {
                            this.optionda97be1 = unmodifiableList(parsed.getOptions().get("rapier.option.da97be1"));
                        }


                        // Initialize flag parameters
                        if(parsed.getFlags().getOrDefault("rapier.flag.59dcb7a", emptyList()).isEmpty()) {
                            throw new CliSyntaxException(
                                "Missing required flag parameter -b / --bravo / -B / --no-bravo");
                        } else {
                            this.flag59dcb7a = unmodifiableList(parsed.getFlags().get("rapier.flag.59dcb7a"));
                        }

                    }
                    catch (JustArgs.IllegalSyntaxException e) {
                        // Standard help is not active. Map and propagate the exception.
                        throw new CliSyntaxException(e.getMessage());
                    }
                    catch(CliSyntaxException e) {
                        // Standard help is not active. Propagate the exception.
                        throw e;
                    }
                }

                @Provides
                @CliPositionalParameter(0)
                public java.lang.Integer providePositional0AsInteger(@CliPositionalParameter(0) String value) {
                    return java.lang.Integer.valueOf(value);
                }

                @Provides
                @CliPositionalParameter(0)
                public String providePositional0AsString() {
                    return positional0;
                }

                @Provides
                @CliOptionParameter(shortName='a', longName="alpha")
                public java.lang.Long provideOptionda97be1AsLong(@CliOptionParameter(shortName='a', longName="alpha") String value) {
                    return java.lang.Long.valueOf(value);
                }

                @Provides
                @CliOptionParameter(shortName='a', longName="alpha")
                public String provideOptionda97be1AsString(
                        @CliOptionParameter(shortName='a', longName="alpha") List<String> values) {
                    return values.get(values.size()-1);
                }

                @Provides
                @CliOptionParameter(shortName='a', longName="alpha")
                public List<String> provideOptionda97be1AsListOfString() {
                    return optionda97be1;
                }

                @Provides
                @CliFlagParameter(positiveShortName='b', positiveLongName="bravo", negativeShortName='B', negativeLongName="no-bravo")
                public Boolean provideFlag59dcb7aAsBoolean(
                        @CliFlagParameter(positiveShortName='b', positiveLongName="bravo", negativeShortName='B', negativeLongName="no-bravo") List<Boolean> values) {
                    return values.get(values.size()-1);
                }

                @Provides
                @CliFlagParameter(positiveShortName='b', positiveLongName="bravo", negativeShortName='B', negativeLongName="no-bravo")
                public List<Boolean> provideFlag59dcb7aAsListOfBoolean() {
                    return flag59dcb7a;
                }

            }
            """);

    assertThat(compilation).generatedSourceFile("com.example.RapierExampleComponentCliModule")
        .hasSourceEquivalentTo(expectedOutput);
  }

  public static final OffsetDateTime TEST_DATE =
      OffsetDateTime.of(2024, 1, 1, 12, 34, 56, 0, ZoneOffset.UTC);

  public static final String TEST_VERSION = "1.2.3";

  public static final String TEST_URL = "https://www.example.com";

  /**
   * We need to set the date and version so our generated source code is deterministic.
   */
  @Override
  protected List<Processor> getAnnotationProcessors() {
    List<Processor> result = super.getAnnotationProcessors();
    for (Processor processor : result) {
      if (processor instanceof CliProcessor) {
        CliProcessor cp = (CliProcessor) processor;
        cp.setDate(TEST_DATE);
        cp.setVersion(TEST_VERSION);
        cp.setUrl(TEST_URL);
      }
    }
    return unmodifiableList(result);
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
