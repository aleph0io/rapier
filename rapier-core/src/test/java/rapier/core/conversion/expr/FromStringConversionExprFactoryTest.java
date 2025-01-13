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
package rapier.core.conversion.expr;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static java.util.Collections.unmodifiableList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import rapier.core.RapierTestBase;

class FromStringConversionExprFactoryTest extends RapierTestBase {
  private String conversionExpr;

  @BeforeEach
  public void setupFromStringConversionExprFactoryTest() {
    conversionExpr = null;
  }

  @Test
  void givenClassWithMatchingFromStringMethod_whenCompile_thenGenerateExpectedConversionExpr()
      throws IOException {
    final JavaFileObject testAnnotationSource = prepareSourceFile("""
        package com.example;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;

        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.TYPE})
        public @interface TestAnnotation {
        }
        """);

    final JavaFileObject testClassSource = prepareSourceFile("""
        package com.example;

        @com.example.TestAnnotation
        public class TestClass {
          public static TestClass fromString(String value) {
            return new TestClass();
          }
        }
        """);

    // Compile the test class
    Compilation compilation = doCompile(testAnnotationSource, testClassSource);

    assertThat(compilation).succeeded();

    assertEquals("com.example.TestClass.fromString(value)", conversionExpr);
  }

  @Test
  void givenClassWithNonMatchingParameterTypeFromStringMethod_whenCompile_thenGenerateNoConversionExpr()
      throws IOException {
    final JavaFileObject testAnnotationSource = prepareSourceFile("""
        package com.example;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;

        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.TYPE})
        public @interface TestAnnotation {
        }
        """);

    final JavaFileObject testClassSource = prepareSourceFile("""
        package com.example;

        @com.example.TestAnnotation
        public class TestClass {
          public static TestClass fromString(Integer value) {
            return new TestClass();
          }
        }
        """);

    // Compile the test class
    Compilation compilation = doCompile(testAnnotationSource, testClassSource);

    assertThat(compilation).succeeded();

    assertEquals(NO_CONVERSION_EXPR, conversionExpr);
  }

  @Test
  void givenClassWithNonMatchingReturnTypeFromStringMethod_whenCompile_thenGenerateNoConversionExpr()
      throws IOException {
    final JavaFileObject testAnnotationSource = prepareSourceFile("""
        package com.example;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;

        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.TYPE})
        public @interface TestAnnotation {
        }
        """);

    final JavaFileObject testClassSource = prepareSourceFile("""
        package com.example;

        @com.example.TestAnnotation
        public class TestClass {
          public static String fromString(String value) {
            return "";
          }
        }
        """);

    // Compile the test class
    Compilation compilation = doCompile(testAnnotationSource, testClassSource);

    assertThat(compilation).succeeded();

    assertEquals(NO_CONVERSION_EXPR, conversionExpr);
  }

  @Test
  void givenClassWithoutFromStringMethod_whenCompile_thenGenerateNoConversionExpr()
      throws IOException {
    final JavaFileObject testAnnotationSource = prepareSourceFile("""
        package com.example;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;

        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.TYPE})
        public @interface TestAnnotation {
        }
        """);

    final JavaFileObject testClassSource = prepareSourceFile("""
        package com.example;

        @com.example.TestAnnotation
        public class TestClass {
          public static TestClass foobar(String value) {
            return new TestClass();
          }
        }
        """);

    // Compile the test class
    Compilation compilation = doCompile(testAnnotationSource, testClassSource);

    assertThat(compilation).succeeded();

    assertEquals(NO_CONVERSION_EXPR, conversionExpr);
  }

  public static final String NO_CONVERSION_EXPR = "NONE";

  /**
   * Simple annotation processor that looks for classes annotated with {@link TestAnnotation} and
   * generates a conversion expression for them. Used to test
   * {@link FromStringConversionExprFactory}.
   */
  @SupportedAnnotationTypes("com.example.TestAnnotation")
  @SupportedSourceVersion(SourceVersion.RELEASE_11)
  private class TestProcessor extends AbstractProcessor {
    private ProcessingEnvironment processingEnvironment;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
      this.processingEnvironment = processingEnvironment;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
      final TypeElement annotation = getElements().getTypeElement("com.example.TestAnnotation");

      final Set<? extends Element> annotatedElements = round.getElementsAnnotatedWith(annotation);
      for (Element annotatedElement : annotatedElements) {
        if (!annotatedElement.getKind().isClass())
          continue;
        conversionExpr = new FromStringConversionExprFactory(getTypes())
            .generateConversionExpr(annotatedElement.asType(), "value").orElse(NO_CONVERSION_EXPR);
      }

      return true;
    }

    private Elements getElements() {
      return getProcessingEnvironment().getElementUtils();
    }

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
