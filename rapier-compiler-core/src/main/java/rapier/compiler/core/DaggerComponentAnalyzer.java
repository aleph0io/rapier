/*-
 * =================================LICENSE_START==================================
 * rapier-processor-core
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import rapier.compiler.core.model.DaggerComponentAnalysis;
import rapier.compiler.core.model.DaggerInjectionSite;
import rapier.compiler.core.model.DaggerInjectionSiteType;
import rapier.compiler.core.model.DaggerProvisionStyle;
import rapier.compiler.core.util.AnnotationProcessing;

public class DaggerComponentAnalyzer {
  private final ProcessingEnvironment processingEnv;
  private final Set<DaggerInjectionSite> dependencies;
  private final Set<TypeMirror> visitedModules;
  private final Deque<TypeMirror> modulesQueue;
  private final Set<TypeMirror> visitedComponents;
  private final Deque<TypeMirror> componentsQueue;
  private final Set<TypeMirror> visitedDependencies;
  private final Deque<TypeMirror> dependenciesQueue;

  public DaggerComponentAnalyzer(ProcessingEnvironment processingEnv) {
    this.processingEnv = requireNonNull(processingEnv);
    this.dependencies = new HashSet<>();
    this.visitedModules = new HashSet<>();
    this.modulesQueue = new ArrayDeque<>();
    this.visitedComponents = new HashSet<>();
    this.componentsQueue = new ArrayDeque<>();
    this.visitedDependencies = new HashSet<>();
    this.dependenciesQueue = new ArrayDeque<>();
  }

  public DaggerComponentAnalysis analyzeComponent(TypeElement componentType) {
    dependencies.clear();
    visitedModules.clear();
    modulesQueue.clear();
    visitedComponents.clear();
    componentsQueue.clear();
    visitedDependencies.clear();
    dependenciesQueue.clear();

    walkComponent(componentType);

    // Visit our components first to gather all our modules
    while (!componentsQueue.isEmpty()) {
      final TypeMirror componentDependencyType = componentsQueue.poll();
      final TypeElement componentDependencyElement =
          (TypeElement) getTypes().asElement(componentDependencyType);
      walkComponent(componentDependencyElement);
    }

    // Now visit our modules
    while (!modulesQueue.isEmpty()) {
      final TypeMirror module = modulesQueue.poll();
      walkModule(module);
    }

    assert componentsQueue.isEmpty();
    assert modulesQueue.isEmpty();


    for (DaggerInjectionSite dependency : dependencies) {
      dependenciesQueue.offer(dependency.getProvidedType());
    }

    while (!dependenciesQueue.isEmpty()) {
      final TypeMirror dependency = dependenciesQueue.poll();
      walkDependency(dependency);
    }

    return new DaggerComponentAnalysis(componentType, dependencies);
  }

  private void walkComponent(TypeElement component) {
    final TypeMirror componentType = component.asType();

    final boolean added = visitedComponents.add(componentType);
    if (added == false) {
      // This means we have a circular dependency. Dagger will fail the build. No need to do
      // anything drastic here. Just break the loop.
      return;
    }

    new DaggerComponentWalker(getProcessingEnv()).walk(component,
        new DaggerComponentWalker.Visitor() {
          @Override
          public void beginComponent(TypeElement component) {}

          @Override
          public void visitComponentModule(TypeElement component, TypeMirror module) {
            modulesQueue.offer(module);
          }

          @Override
          public void visitComponentDependency(TypeElement component, TypeMirror dependency) {
            componentsQueue.offer(dependency);
          }

          @Override
          public void visitComponentProvisionMethod(TypeElement component,
              ExecutableElement methodElement) {
            final DeclaredType componentType = (DeclaredType) component.asType();

            final ExecutableType methodType =
                (ExecutableType) getTypes().asMemberOf(componentType, methodElement);

            final TypeMirror returnTypeMirror = methodType.getReturnType();
            if (isValidInjectionSiteType(methodElement, returnTypeMirror) == false)
              return;

            final List<AnnotationMirror> annotations =
                new ArrayList<>(methodElement.getAnnotationMirrors());

            newInjectionSite(DaggerInjectionSiteType.COMPONENT_PROVISION_METHOD_RESULT,
                methodElement, returnTypeMirror, annotations).ifPresent(dependencies::add);
          }

          @Override
          public void endComponent(TypeElement component) {}
        });
  }

  private void walkModule(TypeMirror module) {
    boolean added = visitedModules.add(module);
    if (added == false) {
      // This means we have a circular dependency. Dagger will fail the build. No need to do
      // anything drastic here. Just break the loop.
      return;
    }

    final TypeElement moduleElement = (TypeElement) getTypes().asElement(module);

    new DaggerModuleWalker(getProcessingEnv()).walk(moduleElement,
        new DaggerModuleWalker.Visitor() {
          @Override
          public void beginModule(TypeElement module) {}

          @Override
          public void visitModuleIncludedModule(TypeElement module, TypeMirror includedModule) {
            modulesQueue.offer(includedModule);
          }

          @Override
          public void visitModuleStaticProvidesMethod(TypeElement module,
              ExecutableElement methodElement) {
            visitModuleProvidesMethod(module, methodElement);
          }

          @Override
          public void visitModuleInstanceProvidesMethod(TypeElement module,
              ExecutableElement methodElement) {
            visitModuleProvidesMethod(module, methodElement);
          }

          private void visitModuleProvidesMethod(TypeElement module,
              ExecutableElement methodElement) {
            final DeclaredType moduleType = (DeclaredType) module.asType();

            final ExecutableType methodType =
                (ExecutableType) getTypes().asMemberOf(moduleType, methodElement);

            final int parameterCount = methodType.getParameterTypes().size();

            assert parameterCount == methodElement.getParameters().size();

            for (int i = 0; i < parameterCount; i++) {
              // for (VariableElement parameter : methodElement.getParameters()) {
              final VariableElement parameterElement = methodElement.getParameters().get(i);
              final TypeMirror parameterType = methodType.getParameterTypes().get(i);
              if (isValidInjectionSiteType(parameterElement, parameterType) == false)
                continue;

              final List<AnnotationMirror> annotations =
                  new ArrayList<>(parameterElement.getAnnotationMirrors());

              final DaggerInjectionSiteType siteType;
              if (methodElement.getModifiers().contains(javax.lang.model.element.Modifier.STATIC)) {
                siteType = DaggerInjectionSiteType.MODULE_STATIC_PROVIDES_METHOD_PARAMETER;
              } else {
                siteType = DaggerInjectionSiteType.MODULE_INSTANCE_PROVIDES_METHOD_PARAMETER;
              }

              newInjectionSite(siteType, parameterElement, parameterType, annotations)
                  .ifPresent(dependencies::add);
            }
          }

          @Override
          public void endModule(TypeElement module) {}
        });
  }

  private void walkDependency(TypeMirror dependency) {
    boolean added = visitedDependencies.add(dependency);
    if (added == false) {
      // This is fine. It is perfectly legal to have multiple injection sites with the same type.
      // We just don't need to visit any type more than once.
      return;
    }

    // We are only interested in walking concrete types
    final TypeElement dependencyElement = (TypeElement) getTypes().asElement(dependency);
    if (dependencyElement.getModifiers().contains(Modifier.ABSTRACT)) {
      return;
    }

    new DaggerJsr330Walker(getProcessingEnv()).walk(dependencyElement,
        new DaggerJsr330Walker.Visitor() {
          @Override
          public void beginClass(TypeElement type) {}

          @Override
          public void visitClassMethodInjectionSite(TypeElement enclosingElement,
              ExecutableElement methodElement) {
            final DeclaredType enclosingType = (DeclaredType) enclosingElement.asType();

            final ExecutableType methodType =
                (ExecutableType) getTypes().asMemberOf(enclosingType, methodElement);

            final int parameterCount = methodType.getParameterTypes().size();

            assert parameterCount == methodElement.getParameters().size();

            for (int i = 0; i < parameterCount; i++) {
              final VariableElement parameterElement = methodElement.getParameters().get(i);
              final TypeMirror parameterType = methodType.getParameterTypes().get(i);
              if (isValidInjectionSiteType(parameterElement, parameterType) == false)
                continue;

              final List<AnnotationMirror> annotations =
                  new ArrayList<>(parameterElement.getAnnotationMirrors());

              newInjectionSite(DaggerInjectionSiteType.INJECT_INSTANCE_METHOD, methodElement,
                  parameterType, annotations).ifPresent(site -> {
                    dependenciesQueue.offer(site.getProvidedType());
                    dependencies.add(site);
                  });
            }
          }

          @Override
          public void visitClassFieldInjectionSite(TypeElement enclosingElement,
              VariableElement fieldElement) {
            final DeclaredType enclosingType = (DeclaredType) enclosingElement.asType();

            final TypeMirror fieldType = getTypes().asMemberOf(enclosingType, fieldElement);
            if (isValidInjectionSiteType(fieldElement, fieldType) == false)
              return;

            final List<AnnotationMirror> annotations =
                new ArrayList<>(fieldElement.getAnnotationMirrors());

            newInjectionSite(DaggerInjectionSiteType.INJECT_INSTANCE_FIELD, fieldElement, fieldType,
                annotations).ifPresent(site -> {
                  dependenciesQueue.offer(site.getProvidedType());
                  dependencies.add(site);
                });
          }

          @Override
          public void visitClassConstructorInjectionSite(TypeElement enclosingElement,
              ExecutableElement constructorElement) {
            final DeclaredType enclosingType = (DeclaredType) enclosingElement.asType();

            final ExecutableType constructorType =
                (ExecutableType) getTypes().asMemberOf(enclosingType, constructorElement);

            final int parameterCount = constructorType.getParameterTypes().size();

            assert parameterCount == constructorElement.getParameters().size();

            for (int i = 0; i < parameterCount; i++) {
              final VariableElement parameterElement = constructorElement.getParameters().get(i);
              final TypeMirror parameterType = constructorType.getParameterTypes().get(i);
              if (isValidInjectionSiteType(parameterElement, parameterType) == false)
                continue;

              final List<AnnotationMirror> annotations =
                  new ArrayList<>(parameterElement.getAnnotationMirrors());

              newInjectionSite(DaggerInjectionSiteType.INJECT_CONSTRUCTOR_PARAMETER,
                  parameterElement, parameterType, annotations).ifPresent(site -> {
                    dependenciesQueue.offer(site.getProvidedType());
                    dependencies.add(site);
                  });
            }
          }

          @Override
          public void endClass(TypeElement type) {}
        });
  }

  private Optional<DaggerInjectionSite> newInjectionSite(DaggerInjectionSiteType siteType,
      Element element, TypeMirror provisionedType, List<AnnotationMirror> annotations) {

    final AnnotationMirror qualifier =
        annotations.stream().filter(a -> AnnotationProcessing.isQualifierAnnotated(getTypes(), a))
            .findFirst().orElse(null);
    final boolean hasNullableAnnotation = annotations.stream()
        .filter(a -> AnnotationProcessing.isNullable(a)).findFirst().isPresent();

    if (provisionedType.getKind().isPrimitive()) {
      if (hasNullableAnnotation) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Primitive types cannot be annotated with @Nullable", element);
      }
      final TypeMirror providedType =
          getTypes().boxedClass(getTypes().getPrimitiveType(provisionedType.getKind())).asType();
      return Optional.of(new DaggerInjectionSite(element, siteType, DaggerProvisionStyle.PRIMITIVE,
          provisionedType, providedType, qualifier, annotations, false));
    }

    final TypeMirror provisionedErasure = getTypes().erasure(provisionedType);
    final TypeMirror literalJavaxInjectProviderType =
        getTypes().erasure(getElements().getTypeElement("javax.inject.Provider").asType());
    final TypeMirror literalJakaInjectProviderType =
        getTypes().erasure(getElements().getTypeElement("jakarta.inject.Provider").asType());
    final TypeMirror literalDaggerLazyType =
        getTypes().erasure(getElements().getTypeElement("dagger.Lazy").asType());
    final TypeMirror literalJavaUtilOptionalType =
        getElements().getTypeElement("java.util.Optional").asType();

    if (getTypes().isSameType(provisionedErasure, literalJavaxInjectProviderType)
        || getTypes().isSameType(provisionedErasure, literalJakaInjectProviderType)) {
      // It is safe to use the type parameters directly here because the injection sites are checked
      // for validity using isValidInjectionSiteType.
      final DeclaredType providerType = (DeclaredType) provisionedType;

      if (providerType.getTypeArguments().size() == 0) {
        getMessager().printMessage(Diagnostic.Kind.ERROR, "Provider must have a type argument",
            element);
        return Optional.empty();
      }

      final TypeMirror arg = providerType.getTypeArguments().get(0);
      if (arg.getKind() == TypeKind.WILDCARD) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Provider type argument must be a concrete type", element);
        return Optional.empty();
      }

      return Optional.of(new DaggerInjectionSite(element, siteType, DaggerProvisionStyle.PROVIDER,
          provisionedType, arg, qualifier, annotations, hasNullableAnnotation));
    } else if (getTypes().isSameType(provisionedErasure, literalDaggerLazyType)) {
      // It is safe to use the type parameters directly here because the injection sites are checked
      // for validity using isValidInjectionSiteType.
      final DeclaredType lazyType = (DeclaredType) provisionedType;

      if (lazyType.getTypeArguments().size() == 0) {
        getMessager().printMessage(Diagnostic.Kind.ERROR, "Provider must have a type argument",
            element);
        return Optional.empty();
      }

      final TypeMirror arg = lazyType.getTypeArguments().get(0);
      if (arg.getKind() == TypeKind.WILDCARD) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Lazy type argument must be a concrete type", element);
        return Optional.empty();
      }

      return Optional.of(new DaggerInjectionSite(element, siteType, DaggerProvisionStyle.LAZY,
          provisionedType, arg, qualifier, annotations, hasNullableAnnotation));
    } else if (getTypes().isSameType(provisionedErasure, literalJavaUtilOptionalType)) {
      // NOTE: We do not support Guava Optional at this time
      if (hasNullableAnnotation) {
        // TODO Should we print this at all?
        getMessager().printMessage(Diagnostic.Kind.NOTE,
            "Optional types are implicitly nullable, and therefore do not require @Nullable annotation",
            element);
      }
      final DeclaredType optionalType = (DeclaredType) provisionedType;
      return Optional.of(new DaggerInjectionSite(element, siteType, DaggerProvisionStyle.OPTIONAL,
          provisionedType, optionalType.getTypeArguments().get(0), qualifier, annotations, true));
    } else {
      return Optional.of(new DaggerInjectionSite(element, siteType, DaggerProvisionStyle.VERBATIM,
          provisionedType, provisionedType, qualifier, annotations, hasNullableAnnotation));
    }
  }

  /**
   * Determines if the given type is a valid type for an injection site. If the type is invalid, an
   * error message will be printed.
   * 
   * @param element the element that the type is associated. Any errors will be reported against
   *        this element.
   * @param type the type to validate
   * @return {@code true} if the type is valid, {@code false} otherwise
   */
  private boolean isValidInjectionSiteType(Element element, TypeMirror type) {
    if (element == null)
      throw new NullPointerException();
    if (type == null)
      throw new NullPointerException();

    switch (type.getKind()) {
      case ARRAY:
      case DECLARED:
      case BYTE:
      case SHORT:
      case INT:
      case LONG:
      case FLOAT:
      case DOUBLE:
      case CHAR:
      case BOOLEAN:
        // These are all fine.
        return true;
      case VOID:
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Dagger injection sites cannot have void type", element);
        return false;
      case TYPEVAR:
      case WILDCARD:
      case UNION:
      case INTERSECTION:
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Dagger injection sites must have fully-reified types", element);
        return false;
      case EXECUTABLE:
        // I'm not even really sure what these are. I can't imagine how they would happen.
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Dagger injection sites must have data type, not executable type", element);
        return false;
      case PACKAGE:
        // I'm not even really sure what these are. I can't imagine how they would happen.
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Dagger injection sites must have data type, not package type", element);
        return false;
      case MODULE:
        // I'm not even really sure what these are. I can't imagine how they would happen.
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Dagger injection sites must have data type, not module type", element);
        return false;
      case ERROR:
      case NONE:
      case NULL:
      case OTHER:
      default:
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Could not resolve return type of injection site", element);
        return false;
    }
  }

  private ProcessingEnvironment getProcessingEnv() {
    return processingEnv;
  }

  private Types getTypes() {
    return processingEnv.getTypeUtils();
  }

  private Elements getElements() {
    return processingEnv.getElementUtils();
  }

  private Messager getMessager() {
    return processingEnv.getMessager();
  }
}
