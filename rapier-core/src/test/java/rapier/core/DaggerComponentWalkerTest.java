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
package rapier.core;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.Component;
import rapier.core.DaggerComponentWalker;

public class DaggerComponentWalkerTest {
  @Test
  public void givenComponent_whenCompile_thenVistExpectedElements() {
    // Source code for a mock Dagger component
    String componentSource = """
            package com.example;

            import dagger.Component;

            @Component(modules = {MyModule.class})
            public interface MyComponent extends MyComponentParent {
                Integer provideInteger();
            }

            interface MyComponentParent {
                String provideString();
            }

            class MyModule {
                // Module implementation
            }
        """;

    final AtomicReference<TypeElement> began = new AtomicReference<>();
    final AtomicReference<TypeElement> ended = new AtomicReference<>();
    final List<TypeMirror> modules = new ArrayList<>();
    final List<ExecutableElement> provisionMethods = new ArrayList<>();

    // Compile the source and check results
    final Compilation compilation = javac().withProcessors(new AbstractProcessor() {
      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ProcessingEnvironment processingEnv = this.processingEnv;

        roundEnv.getElementsAnnotatedWith(Component.class).forEach(element -> {
          if (element instanceof TypeElement) {
            final TypeElement componentElement = (TypeElement) element;
            
            DaggerComponentWalker walker = new DaggerComponentWalker(processingEnv);

            // Mock Visitor
            DaggerComponentWalker.Visitor visitor = new DaggerComponentWalker.Visitor() {
              private TypeElement expectedComponent;

              @Override
              public void beginComponent(TypeElement component) {
                assertNull(expectedComponent);
                expectedComponent = component;
                began.set(component);
              }

              @Override
              public void visitComponentModule(TypeElement component, TypeMirror module) {
                assertEquals(expectedComponent, component);
                modules.add(module);
              }

              @Override
              public void visitComponentProvisionMethod(TypeElement component,
                  ExecutableElement method) {
                assertEquals(expectedComponent, component);
                provisionMethods.add(method);
              }

              @Override
              public void endComponent(TypeElement component) {
                assertEquals(expectedComponent, component);
                ended.set(component);
              }
            };

            walker.walk(componentElement, visitor);
          }
        });

        return false; // Allow other processors to process this round
      }

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("dagger.Component");
      }
    }).compile(JavaFileObjects.forSourceString("com.example.MyComponent", componentSource));

    assertThat(compilation).succeeded();

    assertNotNull(began.get());
    assertEquals("com.example.MyComponent", began.get().getQualifiedName().toString());

    assertNotNull(ended.get());
    assertEquals("com.example.MyComponent", ended.get().getQualifiedName().toString());

    assertEquals(1, modules.size());
    assertEquals("com.example.MyModule", modules.get(0).toString());

    assertEquals(2, provisionMethods.size());
    assertTrue(provisionMethods.stream()
        .anyMatch(m -> m.getSimpleName().toString().equals("provideInteger")));
    assertTrue(provisionMethods.stream()
        .anyMatch(m -> m.getSimpleName().toString().equals("provideString")));
  }
}
