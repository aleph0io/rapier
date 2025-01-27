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
public class CliProcessorConversionFromStringTest extends RapierTestBase {
  @Test
  public void givenComponentWithPositionalOptionFlagParametersAndStandardHelpAndStandardVersion_whenRunWithStandardHelpAndOtherArgs_thenPrintHelpAndExit()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;

        @dagger.Component(modules = {RapierExampleComponentCliModule.class})
        public interface ExampleComponent {
            @rapier.cli.CliOptionParameter(shortName='d')
            public Byte provisionOptionDAsBoxedByte();

            @rapier.cli.CliOptionParameter(shortName='d')
            public byte provisionOptionDAsByte();

            @rapier.cli.CliOptionParameter(shortName='d')
            public Short provisionOptionDAsBoxedShort();

            @rapier.cli.CliOptionParameter(shortName='d')
            public short provisionOptionDAsShort();

            @rapier.cli.CliOptionParameter(shortName='d')
            public Integer provisionOptionDAsBoxedInt();

            @rapier.cli.CliOptionParameter(shortName='d')
            public int provisionOptionDAsInt();

            @rapier.cli.CliOptionParameter(shortName='d')
            public Long provisionOptionDAsBoxedLong();

            @rapier.cli.CliOptionParameter(shortName='d')
            public long provisionOptionDAsLong();

            @rapier.cli.CliOptionParameter(shortName='f')
            public Float provisionOptionFAsBoxedFloat();

            @rapier.cli.CliOptionParameter(shortName='f')
            public float provisionOptionFAsFloat();

            @rapier.cli.CliOptionParameter(shortName='f')
            public Double provisionOptionFAsBoxedDouble();

            @rapier.cli.CliOptionParameter(shortName='f')
            public double provisionOptionFAsDouble();

            @rapier.cli.CliOptionParameter(shortName='s')
            public String provisionOptionSAsString();
            
            @rapier.cli.CliOptionParameter(shortName='s')
            public Character provisionOptionSAsBoxedChar();

            @rapier.cli.CliOptionParameter(shortName='s')
            public char provisionOptionSAsChar();

            @rapier.cli.CliOptionParameter(shortName='s')
            public FromStringExample provisionOptionSFromStringExample();

            @rapier.cli.CliOptionParameter(shortName='s')
            public ValueOfExample provisionOptionSValueOfExample();

            @rapier.cli.CliOptionParameter(shortName='s')
            public SingleArgumentConstructorExample
              provisionOptionSSingleArgumentConstructorExample();

            @rapier.cli.CliOptionParameter(shortName='b')
            public Boolean provisionOptionBAsBoxedBoolean();

            @rapier.cli.CliOptionParameter(shortName='b')
            public boolean provisionOptionBAsBoolean();
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

    final JavaFileObject valueOfExample = prepareSourceFile("""
        package com.example;

        public class ValueOfExample {
            public static ValueOfExample valueOf(String s) {
                return new ValueOfExample(s);
            }

            private final String s;

            private ValueOfExample(String s) {
                this.s = s;
            }

            @Override
            public String toString() {
                return "ValueOfExample [s=" + s + "]";
            }
        }
        """);

    final JavaFileObject singleArgumentConstructorExample = prepareSourceFile("""
        package com.example;

        public class SingleArgumentConstructorExample {
            public SingleArgumentConstructorExample(String s) {
                this.s = s;
            }

            private final String s;

            @Override
            public String toString() {
                return "SingleArgumentConstructorExample [s=" + s + "]";
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
                System.out.println(component.provisionOptionDAsBoxedByte());
                System.out.println(component.provisionOptionDAsByte());
                System.out.println(component.provisionOptionDAsBoxedShort());
                System.out.println(component.provisionOptionDAsShort());
                System.out.println(component.provisionOptionDAsBoxedInt());
                System.out.println(component.provisionOptionDAsInt());
                System.out.println(component.provisionOptionDAsBoxedLong());
                System.out.println(component.provisionOptionDAsLong());
                System.out.println(component.provisionOptionFAsBoxedFloat());
                System.out.println(component.provisionOptionFAsFloat());
                System.out.println(component.provisionOptionFAsBoxedDouble());
                System.out.println(component.provisionOptionFAsDouble());
                System.out.println(component.provisionOptionSAsString());
                System.out.println(component.provisionOptionSAsBoxedChar());
                System.out.println(component.provisionOptionSAsChar());
                System.out.println(component.provisionOptionSFromStringExample());
                System.out.println(component.provisionOptionSValueOfExample());
                System.out.println(component.provisionOptionSSingleArgumentConstructorExample());
                System.out.println(component.provisionOptionBAsBoxedBoolean());
                System.out.println(component.provisionOptionBAsBoolean());
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource, fromStringExample,
        valueOfExample, singleArgumentConstructorExample);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output =
        doRun(compilation, "-d", "123", "-f", "1.23", "-s", "xyz", "-b", "true").trim();

    assertEquals("""
        123
        123
        123
        123
        123
        123
        123
        123
        1.23
        1.23
        1.23
        1.23
        xyz
        x
        x
        FromStringExample [s=xyz]
        ValueOfExample [s=xyz]
        SingleArgumentConstructorExample [s=xyz]
        true
        true""", output);
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
