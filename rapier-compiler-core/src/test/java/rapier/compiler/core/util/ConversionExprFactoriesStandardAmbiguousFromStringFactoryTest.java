/*-
 * =================================LICENSE_START==================================
 * rapier-core
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
package rapier.compiler.core.util;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static java.util.Collections.unmodifiableList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import rapier.compiler.core.ConversionExprFactory;
import rapier.compiler.core.RapierTestBase;
import rapier.compiler.core.conversion.expr.FromStringConversionExprFactory;

/**
 * Tests for
 * {@link ConversionExprFactories#standardAmbiguousFromStringFactory(ProcessingEnvironment)}.
 * Implemented as a standalone class to customize compilation with custom annotation processor.
 */
public class ConversionExprFactoriesStandardAmbiguousFromStringFactoryTest extends RapierTestBase {
  private Map<String, String> actualConversionExprs;

  @BeforeEach
  public void setupFromStringConversionExprFactoryTest() {
    actualConversionExprs = new HashMap<>();
  }

  @Test
  void test() throws IOException {
    final JavaFileObject annotationSource = prepareSourceFile("""
        package com.example;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;

        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.FIELD})
        public @interface ConvertMe {
        }
        """);

    final JavaFileObject fromStringExampleSource = prepareSourceFile("""
        package com.example;

        public class FromStringExample {
          public static FromStringExample fromString(String value) {
            return new FromStringExample();
          }
        }
        """);

    final JavaFileObject valueOfExampleSource = prepareSourceFile("""
        package com.example;

        public class ValueOfExample {
          public static ValueOfExample valueOf(String value) {
            return new ValueOfExample();
          }
        }
        """);

    final JavaFileObject singleArgumentConstructorSource = prepareSourceFile("""
        package com.example;

        public class SingleArgumentConstructorExample {
          public SingleArgumentConstructorExample(String value) {
          }
        }
        """);

    final JavaFileObject conversionTargetSource = prepareSourceFile("""
        package com.example;

        public class ConversionTarget {
          @ConvertMe
          public String stringExample;

          @ConvertMe
          public byte primitiveByteExample;

          @ConvertMe
          public short primitiveShortExample;

          @ConvertMe
          public int primitiveIntExample;

          @ConvertMe
          public long primitiveLongExample;

          @ConvertMe
          public float primitiveFloatExample;

          @ConvertMe
          public double primitiveDoubleExample;

          @ConvertMe
          public char primitiveCharExample;

          @ConvertMe
          public boolean primitiveBooleanExample;

          @ConvertMe
          public Byte boxedByteExample;

          @ConvertMe
          public Short boxedShortExample;

          @ConvertMe
          public Integer boxedIntExample;

          @ConvertMe
          public Long boxedLongExample;

          @ConvertMe
          public Float boxedFloatExample;

          @ConvertMe
          public Double boxedDoubleExample;

          @ConvertMe
          public Character boxedCharExample;

          @ConvertMe
          public Boolean boxedBooleanExample;

          @ConvertMe
          public FromStringExample fromStringExample;

          @ConvertMe
          public ValueOfExample valueOfExample;

          @ConvertMe
          public SingleArgumentConstructorExample singleArgumentConstructorExample;

          @ConvertMe
          public java.util.List<String> listOfStringExample;
        }
        """);

    // Compile the test class
    Compilation compilation = doCompile(annotationSource, fromStringExampleSource,
        valueOfExampleSource, singleArgumentConstructorSource, conversionTargetSource);

    assertThat(compilation).succeeded();

    final Map<String, String> expectedConversionExprs = new HashMap<>();
    expectedConversionExprs.put("stringExample", "value");
    expectedConversionExprs.put("primitiveByteExample", "Byte.parseByte(value)");
    expectedConversionExprs.put("primitiveShortExample", "Short.parseShort(value)");
    expectedConversionExprs.put("primitiveIntExample", "Integer.parseInt(value)");
    expectedConversionExprs.put("primitiveLongExample", "Long.parseLong(value)");
    expectedConversionExprs.put("primitiveFloatExample", "Float.parseFloat(value)");
    expectedConversionExprs.put("primitiveDoubleExample", "Double.parseDouble(value)");
    expectedConversionExprs.put("primitiveCharExample",
        "Optional.of(value).map(s -> s.isEmpty() ? null : s.charAt(0)).orElseThrow(() -> new IllegalStateException(\"Cannot convert empty string to char\"))");
    expectedConversionExprs.put("primitiveBooleanExample", "Boolean.parseBoolean(value)");
    expectedConversionExprs.put("boxedByteExample", "java.lang.Byte.valueOf(value)");
    expectedConversionExprs.put("boxedShortExample", "java.lang.Short.valueOf(value)");
    expectedConversionExprs.put("boxedIntExample", "java.lang.Integer.valueOf(value)");
    expectedConversionExprs.put("boxedLongExample", "java.lang.Long.valueOf(value)");
    expectedConversionExprs.put("boxedFloatExample", "java.lang.Float.valueOf(value)");
    expectedConversionExprs.put("boxedDoubleExample", "java.lang.Double.valueOf(value)");
    expectedConversionExprs.put("boxedCharExample",
        "Optional.of(value).map(s -> s.isEmpty() ? null : s.charAt(0)).orElseThrow(() -> new IllegalStateException(\"Cannot convert empty string to char\"))");
    expectedConversionExprs.put("boxedBooleanExample", "java.lang.Boolean.valueOf(value)");
    expectedConversionExprs.put("fromStringExample",
        "com.example.FromStringExample.fromString(value)");
    expectedConversionExprs.put("valueOfExample", "com.example.ValueOfExample.valueOf(value)");
    expectedConversionExprs.put("singleArgumentConstructorExample",
        "new com.example.SingleArgumentConstructorExample(value)");
    expectedConversionExprs.put("listOfStringExample", null);

    assertEquals(expectedConversionExprs, actualConversionExprs);
  }

  /**
   * Simple annotation processor that looks for classes annotated with {@link TestAnnotation} and
   * generates a conversion expression for them. Used to test
   * {@link FromStringConversionExprFactory}.
   */
  @SupportedAnnotationTypes("com.example.ConvertMe")
  @SupportedSourceVersion(SourceVersion.RELEASE_11)
  private class TestProcessor extends AbstractProcessor {
    private ProcessingEnvironment processingEnvironment;
    private ConversionExprFactory unit;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
      this.processingEnvironment = processingEnvironment;
      this.unit = ConversionExprFactories.standardAmbiguousFromStringFactory(processingEnvironment);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
      final TypeElement annotation = getElements().getTypeElement("com.example.ConvertMe");

      final Set<? extends Element> annotatedElements = round.getElementsAnnotatedWith(annotation);
      for (Element annotatedElement : annotatedElements) {
        if (!annotatedElement.getKind().isField())
          continue;

        final TypeMirror targetType = annotatedElement.asType();

        final String conversionExpr = unit.generateConversionExpr(targetType, "value").orElse(null);

        actualConversionExprs.put(annotatedElement.getSimpleName().toString(), conversionExpr);
      }

      return true;
    }

    private Elements getElements() {
      return getProcessingEnvironment().getElementUtils();
    }

    @SuppressWarnings("unused")
    private Types getTypes() {
      return getProcessingEnvironment().getTypeUtils();
    }

    private ProcessingEnvironment getProcessingEnvironment() {
      return processingEnvironment;
    }
  }

  /**
   * Add our annotation processor to the list of processors to run.
   */
  @Override
  protected List<Processor> getAnnotationProcessors() {
    final List<Processor> result = new ArrayList<>(super.getAnnotationProcessors());
    result.add(new TestProcessor());
    return unmodifiableList(result);
  }
}
