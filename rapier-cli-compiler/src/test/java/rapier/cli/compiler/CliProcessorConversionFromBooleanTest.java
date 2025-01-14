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
public class CliProcessorConversionFromBooleanTest extends RapierTestBase {
  @Test
  public void givenComponentWithPositionalOptionFlagParametersAndStandardHelpAndStandardVersion_whenRunWithStandardHelpAndOtherArgs_thenPrintHelpAndExit()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile(
        """
            package com.example;

            @dagger.Component(modules = {RapierExampleComponentCliModule.class})
            public interface ExampleComponent {
                @rapier.cli.CliFlagParameter(positiveShortName='b', negativeShortName='B')
                public Boolean provisionFlagBAsBoxedBoolean();

                @rapier.cli.CliFlagParameter(positiveShortName='b', negativeShortName='B')
                public boolean provisionFlagBAsPrimitiveBoolean();

                @rapier.cli.CliFlagParameter(positiveShortName='b', negativeShortName='B')
                public String provisionFlagBAsString();

                @rapier.cli.CliFlagParameter(positiveShortName='b', negativeShortName='B')
                public ValueOfExample provisionFlagBAsValueOfExample();

                @rapier.cli.CliFlagParameter(positiveShortName='b', negativeShortName='B')
                public SingleArgumentConstructorExample provisionFlagBAsSingleArgumentConstructorExample();
            }
            """);

    final JavaFileObject valueOfExample = prepareSourceFile("""
        package com.example;

        public class ValueOfExample {
            public static ValueOfExample valueOf(Boolean b) {
                return new ValueOfExample(b);
            }

            private final Boolean b;

            private ValueOfExample(Boolean b) {
                this.b = b;
            }

            @Override
            public String toString() {
                return "ValueOfExample [b=" + b + "]";
            }
        }
        """);

    final JavaFileObject singleArgumentConstructorExample = prepareSourceFile("""
        package com.example;

        public class SingleArgumentConstructorExample {
            public SingleArgumentConstructorExample(Boolean b) {
                this.b = b;
            }

            private final Boolean b;

            @Override
            public String toString() {
                return "SingleArgumentConstructorExample [b=" + b + "]";
            }
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        package com.example;

        public class App {
            public static void main(String[] args) {
                ExampleComponent component=DaggerExampleComponent.builder()
                    .rapierExampleComponentCliModule(new RapierExampleComponentCliModule(args))
                    .build();
                System.out.println(component.provisionFlagBAsBoxedBoolean());
                System.out.println(component.provisionFlagBAsPrimitiveBoolean());
                System.out.println(component.provisionFlagBAsString());
                System.out.println(component.provisionFlagBAsValueOfExample());
                System.out.println(component.provisionFlagBAsSingleArgumentConstructorExample());
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation =
        doCompile(componentSource, appSource, valueOfExample, singleArgumentConstructorExample);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation, "-b").trim();

    assertEquals("""
        true
        true
        true
        ValueOfExample [b=true]
        SingleArgumentConstructorExample [b=true]""", output);
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
