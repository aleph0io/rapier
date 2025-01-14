/*-
 * =================================LICENSE_START==================================
 * rapier-system-property-compiler
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
package rapier.sysprop.compiler;

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
 * Validate that system property processor supports expected type conversion rules
 */
public class SystemPropertyProcessorConversionTest extends RapierTestBase {
  @Test
  public void test() throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        package com.example;
        
        @dagger.Component(modules = {RapierExampleComponentSystemPropertyModule.class})
        public interface ExampleComponent {
            @rapier.sysprop.SystemProperty("INT")
            public Byte provisionIntAsBoxedByte();

            @rapier.sysprop.SystemProperty("INT")
            public byte provisionIntAsByte();

            @rapier.sysprop.SystemProperty("INT")
            public Short provisionIntAsBoxedShort();

            @rapier.sysprop.SystemProperty("INT")
            public short provisionIntAsShort();

            @rapier.sysprop.SystemProperty("INT")
            public Integer provisionIntAsBoxedInt();

            @rapier.sysprop.SystemProperty("INT")
            public int provisionIntAsInt();

            @rapier.sysprop.SystemProperty("INT")
            public Long provisionIntAsBoxedLong();

            @rapier.sysprop.SystemProperty("INT")
            public long provisionIntAsLong();

            @rapier.sysprop.SystemProperty("FLOAT")
            public Float provisionFloatAsBoxedFloat();

            @rapier.sysprop.SystemProperty("FLOAT")
            public float provisionFloatAsFloat();

            @rapier.sysprop.SystemProperty("FLOAT")
            public Double provisionFloatAsBoxedDouble();

            @rapier.sysprop.SystemProperty("FLOAT")
            public double provisionFloatAsDouble();

            @rapier.sysprop.SystemProperty("STRING")
            public String provisionStringAsString();

            @rapier.sysprop.SystemProperty("STRING")
            public Character provisionStringAsBoxedChar();

            @rapier.sysprop.SystemProperty("STRING")
            public char provisionStringAsChar();

            @rapier.sysprop.SystemProperty("STRING")
            public FromStringExample provisionStringAsFromStringExample();

            @rapier.sysprop.SystemProperty("STRING")
            public ValueOfExample provisionStringAsValueOfExample();

            @rapier.sysprop.SystemProperty("STRING")
            public SingleArgumentConstructorExample
              provisionStringAsSingleArgumentConstructorExample();

            @rapier.sysprop.SystemProperty("BOOLEAN")
            public Boolean provisionBooleanAsBoxedBoolean();

            @rapier.sysprop.SystemProperty("BOOLEAN")
            public boolean provisionBooleanAsBoolean();
            
            @rapier.sysprop.SystemProperty("STRING")
            public dagger.Lazy<String> provisionLazyOfString();
            
            @rapier.sysprop.SystemProperty("STRING")
            public javax.inject.Provider<String> provisionProviderOfString();
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
                    .rapierExampleComponentSystemPropertyModule(
                        new RapierExampleComponentSystemPropertyModule(java.util.Map.of(
                            "INT", "123",
                            "FLOAT", "1.23",
                            "STRING", "xyz",
                            "BOOLEAN", "true")))
                    .build();
                System.out.println(component.provisionIntAsBoxedByte());
                System.out.println(component.provisionIntAsByte());
                System.out.println(component.provisionIntAsBoxedShort());
                System.out.println(component.provisionIntAsShort());
                System.out.println(component.provisionIntAsBoxedInt());
                System.out.println(component.provisionIntAsInt());
                System.out.println(component.provisionIntAsBoxedLong());
                System.out.println(component.provisionIntAsLong());
                System.out.println(component.provisionFloatAsBoxedFloat());
                System.out.println(component.provisionFloatAsFloat());
                System.out.println(component.provisionFloatAsBoxedDouble());
                System.out.println(component.provisionFloatAsDouble());
                System.out.println(component.provisionStringAsString());
                System.out.println(component.provisionStringAsBoxedChar());
                System.out.println(component.provisionStringAsChar());
                System.out.println(component.provisionStringAsFromStringExample());
                System.out.println(component.provisionStringAsValueOfExample());
                System.out.println(component.provisionStringAsSingleArgumentConstructorExample());
                System.out.println(component.provisionBooleanAsBoxedBoolean());
                System.out.println(component.provisionBooleanAsBoolean());
                System.out.println(component.provisionLazyOfString().get());
                System.out.println(component.provisionProviderOfString().get());
            }
        }
        """);

    // Run the annotation processor
    final Compilation compilation = doCompile(componentSource, appSource, fromStringExample,
        valueOfExample, singleArgumentConstructorExample);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

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
        true
        xyz
        xyz""", output);
  }

  /**
   * We need to include the generated classes from the rapier-cli module in the classpath for our
   * tests.
   */
  @Override
  protected List<File> getCompileClasspath() throws FileNotFoundException {
    List<File> result = new ArrayList<>();
    result.addAll(super.getCompileClasspath());
    result.add(resolveProjectFile("../rapier-system-property/target/classes"));
    result.add(resolveProjectFile("../rapier-core/target/classes"));
    return unmodifiableList(result);
  }

}
