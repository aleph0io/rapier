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
public class CliProcessorConversionFromListOfBooleanTest extends RapierTestBase {
  @Test
  public void givenComponentWithPositionalOptionFlagParametersAndStandardHelpAndStandardVersion_whenRunWithStandardHelpAndOtherArgs_thenPrintHelpAndExit()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile(
        """
            package com.example;

            import java.util.List;

            @dagger.Component(modules = {RapierExampleComponentCliModule.class})
            public interface ExampleComponent {
                @rapier.cli.CliFlagParameter(positiveShortName='b', negativeShortName='B')
                public List<Boolean> provisionFlagBAsListOfBoolean();

                @rapier.cli.CliFlagParameter(positiveShortName='b', negativeShortName='B')
                public List<String> provisionFlagBAsListOfString();

                @rapier.cli.CliFlagParameter(positiveShortName='b', negativeShortName='B')
                public List<ValueOfBooleanExample> provisionFlagBAsListOfValueOfBooleanExample();

                @rapier.cli.CliFlagParameter(positiveShortName='b', negativeShortName='B')
                public ValueOfListOfBooleanExample provisionFlagBAsValueOfListOfBooleanExample();

                @rapier.cli.CliFlagParameter(positiveShortName='b', negativeShortName='B')
                public List<SingleArgumentBooleanConstructorExample> provisionFlagBAsListOfSingleArgumentBooleanConstructorExample();

                @rapier.cli.CliFlagParameter(positiveShortName='b', negativeShortName='B')
                public SingleArgumentListOfBooleanConstructorExample provisionFlagBAsSingleArgumentListOfBooleanConstructorExample();
            }
            """);

    final JavaFileObject valueOfBooleanExample = prepareSourceFile("""
        package com.example;

        public class ValueOfBooleanExample {
            public static ValueOfBooleanExample valueOf(Boolean b) {
                return new ValueOfBooleanExample(b);
            }

            private final Boolean b;

            private ValueOfBooleanExample(Boolean b) {
                this.b = b;
            }

            @Override
            public String toString() {
                return "ValueOfBooleanExample [b=" + b + "]";
            }
        }
        """);

    final JavaFileObject valueOfListOfBooleanExample = prepareSourceFile("""
        package com.example;

        import java.util.List;

        public class ValueOfListOfBooleanExample {
            public static ValueOfListOfBooleanExample valueOf(List<Boolean> bs) {
                return new ValueOfListOfBooleanExample(bs);
            }

            private final List<Boolean> bs;

            private ValueOfListOfBooleanExample(List<Boolean> bs) {
                this.bs = bs;
            }

            @Override
            public String toString() {
                return "ValueOfListOfBooleanExample [bs=" + bs + "]";
            }
        }
        """);

    final JavaFileObject singleArgumentBooleanConstructorExample = prepareSourceFile("""
        package com.example;

        public class SingleArgumentBooleanConstructorExample {
            public SingleArgumentBooleanConstructorExample(Boolean b) {
                this.b = b;
            }

            private final Boolean b;

            @Override
            public String toString() {
                return "SingleArgumentBooleanConstructorExample [b=" + b + "]";
            }
        }
        """);

    final JavaFileObject singleArgumentListOfBooleanConstructorExample = prepareSourceFile("""
        package com.example;

        import java.util.List;

        public class SingleArgumentListOfBooleanConstructorExample {
            public SingleArgumentListOfBooleanConstructorExample(List<Boolean> bs) {
                this.bs = bs;
            }

            private final List<Boolean> bs;

            @Override
            public String toString() {
                return "SingleArgumentListOfBooleanConstructorExample [bs=" + bs + "]";
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
                    System.out.println(component.provisionFlagBAsListOfBoolean());
                    System.out.println(component.provisionFlagBAsListOfString());
                    System.out.println(component.provisionFlagBAsListOfValueOfBooleanExample());
                    System.out.println(component.provisionFlagBAsValueOfListOfBooleanExample());
                    System.out.println(component.provisionFlagBAsListOfSingleArgumentBooleanConstructorExample());
                    System.out.println(component.provisionFlagBAsSingleArgumentListOfBooleanConstructorExample());
                }
            }
            """);

    // Run the annotation processor
    final Compilation compilation =
        doCompile(componentSource, appSource, valueOfBooleanExample, valueOfListOfBooleanExample,
            singleArgumentListOfBooleanConstructorExample, singleArgumentBooleanConstructorExample);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation, "-b", "-B", "-b").trim();

    assertEquals(
        """
            [true, false, true]
            [true, false, true]
            [ValueOfBooleanExample [b=true], ValueOfBooleanExample [b=false], ValueOfBooleanExample [b=true]]
            ValueOfListOfBooleanExample [bs=[true, false, true]]
            [SingleArgumentBooleanConstructorExample [b=true], SingleArgumentBooleanConstructorExample [b=false], SingleArgumentBooleanConstructorExample [b=true]]
            SingleArgumentListOfBooleanConstructorExample [bs=[true, false, true]]""",
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
