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
package com.sigpwned.rapier.core;

import static java.util.Objects.requireNonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import com.sigpwned.rapier.core.util.AnnotationProcessing;
import dagger.Provides;

public class DaggerModuleWalker {
  public static interface Visitor {
    /**
     * Called at the beginning of the walk.
     * 
     * @param component the logical module being visited
     */
    public void beginModule(TypeElement module);

    /**
     * Called for each module {@link dagger.Module#includes() included} by the given module.
     * 
     * @param module the module being walked
     * @param includedModule the included module
     */
    public void visitModuleIncludedModule(TypeElement module, TypeMirror includedModule);

    // TODO add a method for subcomponents

    /**
     * Called for each valid {@link Provides @Provides}-annotated method in the module.
     * 
     * @param component the logical module being visited
     * @param method the method being visited
     * 
     */
    public void visitModuleProvidesMethod(TypeElement module, ExecutableElement method);

    /**
     * Called at the end of the walk.
     * 
     * @param component the logical module being visited
     */
    public void endModule(TypeElement module);
  }

  private final ProcessingEnvironment processingEnv;

  public DaggerModuleWalker(ProcessingEnvironment processingEnv) {
    this.processingEnv = requireNonNull(processingEnv);
  }

  public void walk(TypeElement module, Visitor visitor) {
    if (module == null)
      throw new NullPointerException();
    if (visitor == null)
      throw new NullPointerException();

    final AnnotationMirror annotation = AnnotationProcessing
        .findFirstAnnotationByQualifiedName(module, "dagger.Module").orElse(null);
    if (annotation == null)
      throw new IllegalArgumentException("Dagger module classes must have the @Module annotation");

    visitor.beginModule(module);

    walkModuleAnnotation(module, visitor, annotation);

    walkModuleClass(module, visitor);

    visitor.endModule(module);
  }

  private void walkModuleAnnotation(TypeElement module, Visitor visitor,
      AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString().equals("dagger.Module");

    final List<TypeMirror> includedModules = new ArrayList<>();
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : annotation
        .getElementValues().entrySet()) {
      if (!e.getKey().getSimpleName().contentEquals("includes"))
        continue;

      e.getValue().accept(new SimpleAnnotationValueVisitor8<Void, Void>() {
        @Override
        public Void visitArray(List<? extends AnnotationValue> vals, Void p) {
          for (AnnotationValue val : vals) {
            val.accept(this, null);
          }
          return null;
        }

        @Override
        public Void visitType(TypeMirror t, Void p) {
          includedModules.add(t);
          return null;
        }
      }, null);

      break;
    }

    for (TypeMirror includedModule : includedModules) {
      visitor.visitModuleIncludedModule(module, includedModule);
    }

  }

  private void walkModuleClass(TypeElement module, Visitor visitor) {
    List<TypeElement> lineage = new ArrayList<>();
    lineage.add(module);
    lineage.addAll(AnnotationProcessing.superclasses(processingEnv.getTypeUtils(), module));

    for (TypeElement type : lineage) {
      for (Element element : type.getEnclosedElements()) {
        if (element.getKind() != ElementKind.METHOD)
          continue;
        final ExecutableElement methodElement = (ExecutableElement) element;

        // We only care about public concrete instance methods
        if (!methodElement.getModifiers().contains(Modifier.PUBLIC))
          continue;
        if (methodElement.getModifiers().contains(Modifier.ABSTRACT))
          continue;
        if (methodElement.getModifiers().contains(Modifier.STATIC))
          continue;

        if (methodElement.getAnnotationMirrors().stream()
            .anyMatch(a -> a.getAnnotationType().toString().equals("dagger.Provides"))
            && methodElement.getReturnType().getKind() != TypeKind.VOID) {
          // This is a valid @Provides-annotated method
          visitor.visitModuleProvidesMethod(module, methodElement);
        }
      }
    }
  }
}
