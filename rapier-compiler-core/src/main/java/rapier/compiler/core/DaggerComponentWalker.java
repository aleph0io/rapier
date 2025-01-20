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
package rapier.compiler.core;

import static java.util.Objects.requireNonNull;
import java.util.ArrayList;
import java.util.List;
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
import javax.lang.model.util.Types;
import rapier.compiler.core.util.AnnotationProcessing;

public class DaggerComponentWalker {
  public static interface Visitor {
    /**
     * Called at the beginning of the walk.
     * 
     * @param component the logical component being visited
     */
    public void beginComponent(TypeElement component);

    /**
     * Called for each "module" referenced by the component.
     * 
     * @param component the logical component being visited
     * @param module the module being visited
     */
    public void visitComponentModule(TypeElement component, TypeMirror module);

    /**
     * Called for each component dependency referenced by the component.
     * 
     * @param component the logical component being visited
     * @param dependency the dependency being visited
     */
    public void visitComponentDependency(TypeElement component, TypeMirror dependency);

    /**
     * Called for each "provision method" in the component.
     * 
     * @param component the logical component being visited
     * @param method the method being visited
     * 
     */
    public void visitComponentProvisionMethod(TypeElement component, ExecutableElement method);

    // TODO add a method for members injection methods
    // TODO add a method for subcomponent factory methods

    /**
     * Called at the end of the walk.
     * 
     * @param component the logical component being visited
     */
    public void endComponent(TypeElement component);
  }

  private final ProcessingEnvironment processingEnv;

  public DaggerComponentWalker(ProcessingEnvironment processingEnv) {
    this.processingEnv = requireNonNull(processingEnv);
  }

  public void walk(TypeElement component, Visitor visitor) {
    if (component == null)
      throw new NullPointerException();
    if (visitor == null)
      throw new NullPointerException();

    final AnnotationMirror annotation = AnnotationProcessing
        .findFirstAnnotationByQualifiedName(component, "dagger.Component").orElse(null);
    if (annotation == null)
      throw new IllegalArgumentException(
          "Dagger component classes must have the @Component annotation");

    visitor.beginComponent(component);

    walkComponentAnnotation(component, visitor, annotation);

    walkComponentClass(component, visitor);

    visitor.endComponent(component);
  }

  private void walkComponentAnnotation(TypeElement component, Visitor visitor,
      AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString().equals("dagger.Component");

    final List<TypeMirror> modules = new ArrayList<>();
    final AnnotationValue modulesAnnotationValue =
        AnnotationProcessing.findAnnotationValueByName(annotation, "modules").orElse(null);
    if (modulesAnnotationValue != null) {
      modulesAnnotationValue.accept(new SimpleAnnotationValueVisitor8<Void, Void>() {
        @Override
        public Void visitArray(List<? extends AnnotationValue> vals, Void p) {
          for (AnnotationValue val : vals) {
            val.accept(this, null);
          }
          return null;
        }

        @Override
        public Void visitType(TypeMirror t, Void p) {
          modules.add(t);
          return null;
        }
      }, null);
    }

    final List<TypeMirror> dependencies = new ArrayList<>();
    final AnnotationValue dependenciesAnnotationValue =
        AnnotationProcessing.findAnnotationValueByName(annotation, "dependencies").orElse(null);
    if (dependenciesAnnotationValue != null) {
      dependenciesAnnotationValue.accept(new SimpleAnnotationValueVisitor8<Void, Void>() {
        @Override
        public Void visitArray(List<? extends AnnotationValue> vals, Void p) {
          for (AnnotationValue val : vals) {
            val.accept(this, null);
          }
          return null;
        }

        @Override
        public Void visitType(TypeMirror t, Void p) {
          dependencies.add(t);
          return null;
        }
      }, null);
    }

    for (TypeMirror module : modules) {
      visitor.visitComponentModule(component, module);
    }

    for (TypeMirror dependency : dependencies) {
      visitor.visitComponentDependency(component, dependency);
    }
  }

  private void walkComponentClass(TypeElement component, Visitor visitor) {
    List<TypeElement> lineage = AnnotationProcessing.lineage(getTypes(), component);
    for (TypeElement ancestor : lineage) {
      walkComponentAncestor(component, visitor, ancestor);
    }
  }

  private void walkComponentAncestor(TypeElement component, Visitor visitor, TypeElement ancestor) {
    for (final Element element : ancestor.getEnclosedElements()) {
      // Obviously, only methods can be provision methods
      if (element.getKind() != ElementKind.METHOD)
        continue;
      final ExecutableElement methodElement = (ExecutableElement) element;

      // We only care about public abstract instance methods
      if (!methodElement.getModifiers().contains(Modifier.PUBLIC))
        continue;
      if (!methodElement.getModifiers().contains(Modifier.ABSTRACT))
        continue;
      if (methodElement.getModifiers().contains(Modifier.STATIC))
        continue;

      // Is this method interesting?
      if (methodElement.getReturnType().getKind() != TypeKind.VOID
          && methodElement.getParameters().isEmpty()) {
        // Provision methods must have a return type and no arguments
        visitor.visitComponentProvisionMethod(component, methodElement);
      }
    }
  }

  private ProcessingEnvironment getProcessingEnv() {
    return processingEnv;
  }

  private Types getTypes() {
    return getProcessingEnv().getTypeUtils();
  }
}
