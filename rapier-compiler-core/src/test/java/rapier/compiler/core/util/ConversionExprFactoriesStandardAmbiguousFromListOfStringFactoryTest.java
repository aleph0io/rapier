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
 * {@link ConversionExprFactories#standardAmbiguousFromListOfStringFactory(ProcessingEnvironment)}.
 * Implemented as a standalone class to customize compilation with custom annotation processor.
 */
public class ConversionExprFactoriesStandardAmbiguousFromListOfStringFactoryTest
    extends RapierTestBase {
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

    final JavaFileObject valueOfListOfStringExampleSource = prepareSourceFile("""
        package com.example;

        public class ValueOfListOfStringExample {
          public static ValueOfListOfStringExample valueOf(java.util.List<String> value) {
            return new ValueOfListOfStringExample();
          }
        }
        """);

    final JavaFileObject valueOfStringExampleSource = prepareSourceFile("""
        package com.example;

        public class ValueOfStringExample {
          public static ValueOfStringExample valueOf(String value) {
            return new ValueOfStringExample();
          }
        }
        """);

    final JavaFileObject singleArgumentListOfStringConstructorSource = prepareSourceFile("""
        package com.example;

        public class SingleArgumentListOfStringConstructorExample {
          public SingleArgumentListOfStringConstructorExample(java.util.List<String> value) {
          }
        }
        """);

    final JavaFileObject singleArgumentStringConstructorSource = prepareSourceFile("""
        package com.example;

        public class SingleArgumentStringConstructorExample {
          public SingleArgumentStringConstructorExample(String value) {
          }
        }
        """);

    final JavaFileObject conversionTargetSource = prepareSourceFile(
        """
            package com.example;

            public class ConversionTarget {
              @ConvertMe
              public java.util.List<String> listOfStringExample;

              @ConvertMe
              public ValueOfListOfStringExample valueOfExample;

              @ConvertMe
              public SingleArgumentListOfStringConstructorExample singleArgumentConstructorExample;

              @ConvertMe
              public java.util.List<Byte> listOfByteExample;

              @ConvertMe
              public java.util.List<Short> listOfShortExample;

              @ConvertMe
              public java.util.List<Integer> listOfIntExample;

              @ConvertMe
              public java.util.List<Long> listOfLongExample;

              @ConvertMe
              public java.util.List<Float> listOfFloatExample;

              @ConvertMe
              public java.util.List<Double> listOfDoubleExample;

              @ConvertMe
              public java.util.List<Character> listOfCharExample;

              @ConvertMe
              public java.util.List<Boolean> listOfBooleanExample;

              @ConvertMe
              public java.util.List<ValueOfStringExample> listOfValueOfExample;

              @ConvertMe
              public java.util.List<SingleArgumentStringConstructorExample> listOfSingleArgumentConstructorExample;
            }
            """);

    // Compile the test class
    Compilation compilation = doCompile(annotationSource, valueOfListOfStringExampleSource,
        singleArgumentListOfStringConstructorSource, valueOfStringExampleSource,
        singleArgumentStringConstructorSource, conversionTargetSource);

    assertThat(compilation).succeeded();

    final Map<String, String> expectedConversionExprs = new HashMap<>();
    expectedConversionExprs.put("listOfStringExample", "value");
    expectedConversionExprs.put("valueOfExample",
        "com.example.ValueOfListOfStringExample.valueOf(value)");
    expectedConversionExprs.put("singleArgumentConstructorExample",
        "new com.example.SingleArgumentListOfStringConstructorExample(value)");
    expectedConversionExprs.put("listOfByteExample",
        "value.stream().map(element -> { try { return java.lang.Byte.valueOf(element); } catch(RuntimeException e) { throw e; } catch(Exception e) { throw new RuntimeException(e); } }).collect(java.util.stream.Collectors.toList())");
    expectedConversionExprs.put("listOfShortExample",
        "value.stream().map(element -> { try { return java.lang.Short.valueOf(element); } catch(RuntimeException e) { throw e; } catch(Exception e) { throw new RuntimeException(e); } }).collect(java.util.stream.Collectors.toList())");
    expectedConversionExprs.put("listOfIntExample",
        "value.stream().map(element -> { try { return java.lang.Integer.valueOf(element); } catch(RuntimeException e) { throw e; } catch(Exception e) { throw new RuntimeException(e); } }).collect(java.util.stream.Collectors.toList())");
    expectedConversionExprs.put("listOfLongExample",
        "value.stream().map(element -> { try { return java.lang.Long.valueOf(element); } catch(RuntimeException e) { throw e; } catch(Exception e) { throw new RuntimeException(e); } }).collect(java.util.stream.Collectors.toList())");
    expectedConversionExprs.put("listOfFloatExample",
        "value.stream().map(element -> { try { return java.lang.Float.valueOf(element); } catch(RuntimeException e) { throw e; } catch(Exception e) { throw new RuntimeException(e); } }).collect(java.util.stream.Collectors.toList())");
    expectedConversionExprs.put("listOfDoubleExample",
        "value.stream().map(element -> { try { return java.lang.Double.valueOf(element); } catch(RuntimeException e) { throw e; } catch(Exception e) { throw new RuntimeException(e); } }).collect(java.util.stream.Collectors.toList())");
    expectedConversionExprs.put("listOfCharExample",
        "value.stream().map(element -> { try { return Optional.of(element).map(s -> s.isEmpty() ? null : s.charAt(0)).orElseThrow(() -> new IllegalStateException(\"Cannot convert empty string to char\")); } catch(RuntimeException e) { throw e; } catch(Exception e) { throw new RuntimeException(e); } }).collect(java.util.stream.Collectors.toList())");
    expectedConversionExprs.put("listOfBooleanExample",
        "value.stream().map(element -> { try { return java.lang.Boolean.valueOf(element); } catch(RuntimeException e) { throw e; } catch(Exception e) { throw new RuntimeException(e); } }).collect(java.util.stream.Collectors.toList())");
    expectedConversionExprs.put("listOfValueOfExample",
        "value.stream().map(element -> { try { return com.example.ValueOfStringExample.valueOf(element); } catch(RuntimeException e) { throw e; } catch(Exception e) { throw new RuntimeException(e); } }).collect(java.util.stream.Collectors.toList())");
    expectedConversionExprs.put("listOfSingleArgumentConstructorExample",
        "value.stream().map(element -> { try { return new com.example.SingleArgumentStringConstructorExample(element); } catch(RuntimeException e) { throw e; } catch(Exception e) { throw new RuntimeException(e); } }).collect(java.util.stream.Collectors.toList())");

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
      this.unit =
          ConversionExprFactories.standardAmbiguousFromListOfStringFactory(processingEnvironment);
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
