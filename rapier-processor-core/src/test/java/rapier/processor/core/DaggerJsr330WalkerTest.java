/*-
 * =================================LICENSE_START==================================
 * rapier-core
 * ====================================SECTION=====================================
 * Copyright (C) 2024 Andy Boothe
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
package rapier.processor.core;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;

public class DaggerJsr330WalkerTest {
  @Test
  public void givenInjectableClass_whenWalk_thenVisitExpectedInjectionSites() {
    // Source code for a mock class with JSR 330 injection annotations
    final String annotationSource = """
        package com.example;

        @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)
        @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
        public @interface Example {}
        """;

    final String classSource = """
        package com.example;

        import javax.inject.Inject;

        @Example
        public class MyClass extends MyParent {
            public String classConstructor;

            public String classMethod;

            @Inject
            public String classField;

            @Inject
            public MyClass(String value) {
                this.classConstructor = value;
            }

            @Inject
            public void setClassMethod(String value) {
                this.classMethod = value;
            }
        }

        class MyParent {
            @Inject
            public String parentField;

            public String parentMethod;

            @Inject
            public void setParentMethod(String value) {
                this.parentMethod = value;
            }
        }
        """;

    final AtomicReference<TypeElement> began = new AtomicReference<>();
    final AtomicReference<TypeElement> ended = new AtomicReference<>();
    final List<ExecutableElement> constructorInjectionSites = new ArrayList<>();
    final List<VariableElement> fieldInjectionSites = new ArrayList<>();
    final List<ExecutableElement> methodInjectionSites = new ArrayList<>();

    // Compile the source and check results
    final Compilation compilation = javac().withProcessors(new AbstractProcessor() {
      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        roundEnv.getRootElements().forEach(element -> {
          if (element instanceof TypeElement typeElement) {
            if (!typeElement.getSimpleName().toString().equals("MyClass"))
              return;

            DaggerJsr330Walker walker = new DaggerJsr330Walker(processingEnv);

            // Mock Visitor
            DaggerJsr330Walker.Visitor visitor = new DaggerJsr330Walker.Visitor() {
              @Override
              public void beginClass(TypeElement type) {
                began.set(type);
              }

              @Override
              public void visitClassConstructorInjectionSite(TypeElement type,
                  ExecutableElement constructor) {
                constructorInjectionSites.add(constructor);
              }

              @Override
              public void visitClassFieldInjectionSite(TypeElement type, VariableElement field) {
                fieldInjectionSites.add(field);
              }

              @Override
              public void visitClassMethodInjectionSite(TypeElement type,
                  ExecutableElement method) {
                methodInjectionSites.add(method);
              }

              @Override
              public void endClass(TypeElement type) {
                ended.set(type);
              }
            };

            walker.walk(typeElement, visitor);
          }
        });

        return false; // Allow other processors to process this round
      }

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("com.example.Example");
      }
    }).compile(JavaFileObjects.forSourceString("com.example.MyClass", classSource),
        JavaFileObjects.forSourceString("com.example.Example", annotationSource));

    // Validate compilation
    assertThat(compilation).succeeded();

    // Validate visitor interactions
    assertNotNull(began.get());
    assertEquals("com.example.MyClass", began.get().getQualifiedName().toString());

    assertNotNull(ended.get());
    assertEquals("com.example.MyClass", ended.get().getQualifiedName().toString());

    // Validate constructor injection site
    assertEquals(1, constructorInjectionSites.size());
    assertEquals("MyClass(java.lang.String)", constructorInjectionSites.get(0).toString());

    // Validate field injection site
    assertEquals(2, fieldInjectionSites.size());
    assertEquals("classField", fieldInjectionSites.get(0).getSimpleName().toString());
    assertEquals("parentField", fieldInjectionSites.get(1).getSimpleName().toString());

    // Validate method injection site
    assertEquals(2, methodInjectionSites.size());
    assertEquals("setClassMethod(java.lang.String)", methodInjectionSites.get(0).toString());
    assertEquals("setParentMethod(java.lang.String)", methodInjectionSites.get(1).toString());
  }
}
