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
import dagger.Module;

public class DaggerModuleWalkerTest {
  @Test
  public void givenModule_whenCompile_thenVisitExpectedElements() {
    // Source code for a mock Dagger module
    String moduleSource = """
            package com.example;

            import dagger.Module;
            import dagger.Provides;

            @Module(includes={IncludedModule.class})
            public class MyModule extends MyModuleParent {
                @Provides
                public String provideString() {
                    return "Hello";
                }

                public void helperMethod() {
                    // Not a @Provides method, should be ignored
                }
            }

            @Module
            class IncludedModule {
            }

            class MyModuleParent {
                @Provides
                public Integer provideInteger() {
                    return 42;
                }
            }
        """;

    final AtomicReference<TypeElement> began = new AtomicReference<>();
    final AtomicReference<TypeElement> ended = new AtomicReference<>();
    final List<TypeMirror> includedModules = new ArrayList<>();
    final List<ExecutableElement> providesMethods = new ArrayList<>();

    // Compile the source and check results
    Compilation compilation = javac().withProcessors(new AbstractProcessor() {
      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ProcessingEnvironment processingEnv = this.processingEnv;

        roundEnv.getElementsAnnotatedWith(Module.class).forEach(element -> {
          if (element instanceof TypeElement) {
            final TypeElement moduleElement = (TypeElement) element;

            // We only want to walk MyModule in this test
            if (!moduleElement.getSimpleName().contentEquals("MyModule"))
              return;

            DaggerModuleWalker walker = new DaggerModuleWalker(processingEnv);

            // Mock Visitor
            DaggerModuleWalker.Visitor visitor = new DaggerModuleWalker.Visitor() {
              private TypeElement expectedModule;

              @Override
              public void beginModule(TypeElement module) {
                assertNull(expectedModule);
                expectedModule = module;
                began.set(module);
              }

              @Override
              public void visitModuleIncludedModule(TypeElement module, TypeMirror includedModule) {
                assertEquals(expectedModule, module);
                includedModules.add(includedModule);
              }

              @Override
              public void visitModuleInstanceProvidesMethod(TypeElement module,
                  ExecutableElement method) {
                assertEquals(expectedModule, module);
                providesMethods.add(method);
              }

              @Override
              public void visitModuleStaticProvidesMethod(TypeElement module,
                  ExecutableElement method) {
                assertEquals(expectedModule, module);
                providesMethods.add(method);
              }

              @Override
              public void endModule(TypeElement module) {
                assertEquals(expectedModule, module);
                ended.set(module);
              }
            };

            walker.walk(moduleElement, visitor);
          }
        });

        return false; // Allow other processors to process this round
      }

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("dagger.Module");
      }
    }).compile(JavaFileObjects.forSourceString("com.example.MyModule", moduleSource));

    // Validate compilation
    assertThat(compilation).succeeded();

    // Validate visitor interactions
    assertNotNull(began.get());
    assertEquals("com.example.MyModule", began.get().getQualifiedName().toString());

    assertNotNull(ended.get());
    assertEquals("com.example.MyModule", ended.get().getQualifiedName().toString());

    // Validate included modules
    assertEquals(1, includedModules.size());
    assertTrue(
        includedModules.stream().anyMatch(m -> m.toString().equals("com.example.IncludedModule")));

    // Validate @Provides methods
    assertEquals(2, providesMethods.size());
    assertTrue(providesMethods.stream()
        .anyMatch(m -> m.getSimpleName().toString().equals("provideString")));
    assertTrue(providesMethods.stream()
        .anyMatch(m -> m.getSimpleName().toString().equals("provideInteger")));
  }
}
