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
 * Validate that CLI processor supports expected type conversion rules
 */
public class CliProcessorConversionFromListOfStringTest extends RapierTestBase {
  @Test
  public void givenComponentWithPositionalOptionFlagParametersAndStandardHelpAndStandardVersion_whenRunWithStandardHelpAndOtherArgs_thenPrintHelpAndExit()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile(
        """
            package com.example;

            import java.util.List;

            @dagger.Component(modules = {RapierExampleComponentCliModule.class})
            public interface ExampleComponent {
                @rapier.cli.CliOptionParameter(shortName='d')
                public List<Byte> provisionOptionDAsListOfByte();

                @rapier.cli.CliOptionParameter(shortName='d')
                public List<Short> provisionOptionDAsListOfShort();

                @rapier.cli.CliOptionParameter(shortName='d')
                public List<Integer> provisionOptionDAsListOfInteger();

                @rapier.cli.CliOptionParameter(shortName='d')
                public List<Long> provisionOptionDAsListOfLong();

                @rapier.cli.CliOptionParameter(shortName='f')
                public List<Float> provisionOptionFAsListOfFloat();

                @rapier.cli.CliOptionParameter(shortName='f')
                public List<Double> provisionOptionFAsListOfDouble();

                @rapier.cli.CliOptionParameter(shortName='s')
                public List<String> provisionOptionSAsListOfString();

                @rapier.cli.CliOptionParameter(shortName='s')
                public List<Character> provisionOptionSAsListOfCharacter();

                @rapier.cli.CliOptionParameter(shortName='s')
                public List<FromStringExample> provisionOptionSAsListOfFromStringExample();

                @rapier.cli.CliOptionParameter(shortName='s')
                public List<ValueOfStringExample> provisionOptionSAsListOfValueOfStringExample();

                @rapier.cli.CliOptionParameter(shortName='s')
                public ValueOfListOfStringExample provisionOptionSAsValueOfListOfStringExample();

                @rapier.cli.CliOptionParameter(shortName='s')
                public List<SingleArgumentStringConstructorExample> provisionOptionSAsListOfSingleArgumentStringConstructorExample();

                @rapier.cli.CliOptionParameter(shortName='s')
                public SingleArgumentListOfStringConstructorExample provisionOptionSAsListOfSingleArgumentListOfStringConstructorExample();
            }
            """);

    final JavaFileObject fromStringExample = prepareSourceFile("""
        package com.example;

        public class FromStringExample {
            public static FromStringExample fromString(String s) {
                return new FromStringExample(s);
            }

            private final String s;

            private FromStringExample(String s) {
                this.s = s;
            }

            @Override
            public String toString() {
                return "FromStringExample [s=" + s + "]";
            }
        }
        """);

    final JavaFileObject valueOfStringExample = prepareSourceFile("""
        package com.example;

        public class ValueOfStringExample {
            public static ValueOfStringExample valueOf(String s) {
                return new ValueOfStringExample(s);
            }

            private final String s;

            private ValueOfStringExample(String s) {
                this.s = s;
            }

            @Override
            public String toString() {
                return "ValueOfStringExample [s=" + s + "]";
            }
        }
        """);

    final JavaFileObject valueOfListOfStringExample = prepareSourceFile("""
        package com.example;

        import java.util.List;

        public class ValueOfListOfStringExample {
            public static ValueOfListOfStringExample valueOf(List<String> xs) {
                return new ValueOfListOfStringExample(xs);
            }

            private final List<String> xs;

            private ValueOfListOfStringExample(List<String> xs) {
                this.xs = xs;
            }

            @Override
            public String toString() {
                return "ValueOfListOfStringExample [xs=" + xs + "]";
            }
        }
        """);

    final JavaFileObject singleArgumentStringConstructorExample = prepareSourceFile("""
        package com.example;

        public class SingleArgumentStringConstructorExample {
            public SingleArgumentStringConstructorExample(String s) {
                this.s = s;
            }

            private final String s;

            @Override
            public String toString() {
                return "SingleArgumentStringConstructorExample [s=" + s + "]";
            }
        }
        """);

    final JavaFileObject singleArgumentListOfStringConstructorExample = prepareSourceFile("""
        package com.example;

        import java.util.List;

        public class SingleArgumentListOfStringConstructorExample {
            public SingleArgumentListOfStringConstructorExample(List<String> xs) {
                this.xs = xs;
            }

            private final List<String> xs;

            @Override
            public String toString() {
                return "SingleArgumentListOfStringConstructorExample [xs=" + xs + "]";
            }
        }
        """);

    final JavaFileObject appSource = prepareSourceFile(
        """
            package com.example;

            public class App {
                public static void main(String[] args) {
                    ExampleComponent component=DaggerExampleComponent.builder()
                        .rapierExampleComponentCliModule(new RapierExampleComponentCliModule(args))
                        .build();
                    System.out.println(component.provisionOptionDAsListOfByte());
                    System.out.println(component.provisionOptionDAsListOfShort());
                    System.out.println(component.provisionOptionDAsListOfInteger());
                    System.out.println(component.provisionOptionDAsListOfLong());
                    System.out.println(component.provisionOptionFAsListOfFloat());
                    System.out.println(component.provisionOptionFAsListOfDouble());
                    System.out.println(component.provisionOptionSAsListOfString());
                    System.out.println(component.provisionOptionSAsListOfCharacter());
                    System.out.println(component.provisionOptionSAsListOfFromStringExample());
                    System.out.println(component.provisionOptionSAsListOfValueOfStringExample());
                    System.out.println(component.provisionOptionSAsValueOfListOfStringExample());
                    System.out.println(component.provisionOptionSAsListOfSingleArgumentStringConstructorExample());
                    System.out.println(component.provisionOptionSAsListOfSingleArgumentListOfStringConstructorExample());
                }
            }
            """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource, fromStringExample,
        valueOfStringExample, valueOfListOfStringExample, singleArgumentStringConstructorExample,
        singleArgumentListOfStringConstructorExample);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output =
        doRun(compilation, "-d", "12", "-d", "23", "-f", "1.2", "-f", "2.3", "-s", "xy", "-s", "yz")
            .trim();

    assertEquals(
        """
            [12, 23]
            [12, 23]
            [12, 23]
            [12, 23]
            [1.2, 2.3]
            [1.2, 2.3]
            [xy, yz]
            [x, y]
            [FromStringExample [s=xy], FromStringExample [s=yz]]
            [ValueOfStringExample [s=xy], ValueOfStringExample [s=yz]]
            ValueOfListOfStringExample [xs=[xy, yz]]
            [SingleArgumentStringConstructorExample [s=xy], SingleArgumentStringConstructorExample [s=yz]]
            SingleArgumentListOfStringConstructorExample [xs=[xy, yz]]""",
        output);
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
