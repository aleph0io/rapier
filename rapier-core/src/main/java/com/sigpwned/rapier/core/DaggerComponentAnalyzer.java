package com.sigpwned.rapier.core;

import static java.util.Objects.requireNonNull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import com.sigpwned.rapier.core.model.DaggerComponentAnalysis;
import com.sigpwned.rapier.core.model.Dependency;
import com.sigpwned.rapier.core.util.AnnotationProcessing;

public class DaggerComponentAnalyzer {
  private final ProcessingEnvironment processingEnv;

  public DaggerComponentAnalyzer(ProcessingEnvironment processingEnv) {
    this.processingEnv = requireNonNull(processingEnv);
  }

  public DaggerComponentAnalysis analyzeComponent(TypeElement componentType) {
    final Set<Dependency> dependencies = new HashSet<>();
    final Deque<TypeMirror> modulesQueue = new ArrayDeque<>();
    new DaggerComponentWalker(getProcessingEnv()).walk(componentType,
        new DaggerComponentWalker.Visitor() {
          @Override
          public void beginComponent(TypeElement component) {}

          @Override
          public void visitComponentModule(TypeElement component, TypeMirror module) {
            modulesQueue.offer(module);
          }

          @Override
          public void visitComponentProvisionMethod(TypeElement component,
              ExecutableElement method) {
            final TypeMirror returnTypeMirror = method.getReturnType();

            final List<AnnotationMirror> annotations =
                new ArrayList<>(method.getAnnotationMirrors());

            final AnnotationMirror qualifier = annotations.stream()
                .filter(a -> AnnotationProcessing.isQualifierAnnotated(getTypes(), a)).findFirst()
                .orElse(null);

            dependencies.add(new Dependency(returnTypeMirror, qualifier, annotations));
          }

          @Override
          public void endComponent(TypeElement component) {}
        });

    // We use a queue here because modules can reference other modules
    // TODO Handle module includes when added
    final Set<TypeMirror> visitedModules = new HashSet<>();
    while (!modulesQueue.isEmpty()) {
      final TypeMirror module = modulesQueue.poll();

      boolean added = visitedModules.add(module);
      if (added == false)
        continue;

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
            public void visitModuleProvidesMethod(TypeElement module, ExecutableElement method) {
              for (VariableElement parameter : method.getParameters()) {
                final TypeMirror parameterType = parameter.asType();

                final List<AnnotationMirror> annotations =
                    new ArrayList<>(method.getAnnotationMirrors());

                final AnnotationMirror qualifier = annotations.stream()
                    .filter(a -> AnnotationProcessing.isQualifierAnnotated(getTypes(), a))
                    .findFirst().orElse(null);

                dependencies.add(new Dependency(parameterType, qualifier, annotations));
              }
            }

            @Override
            public void endModule(TypeElement module) {}
          });
    }

    final Deque<TypeMirror> dependenciesQueue = new ArrayDeque<>();
    for (Dependency dependency : dependencies) {
      dependenciesQueue.offer(dependency.getType());
    }

    final Set<TypeMirror> visitedDependencies = new HashSet<>();
    while (!dependenciesQueue.isEmpty()) {
      final TypeMirror dependency = dependenciesQueue.poll();

      boolean added = visitedDependencies.add(dependency);
      if (added == false)
        continue;

      final TypeElement dependencyElement = (TypeElement) getTypes().asElement(dependency);

      new DaggerJsr330Walker(getProcessingEnv()).walk(dependencyElement,
          new DaggerJsr330Walker.Visitor() {
            @Override
            public void beginClass(TypeElement type) {}

            @Override
            public void visitClassMethodInjectionSite(TypeElement type, ExecutableElement method) {
              for (VariableElement parameter : method.getParameters()) {
                final TypeMirror parameterType = parameter.asType();

                final List<AnnotationMirror> annotations =
                    new ArrayList<>(method.getAnnotationMirrors());

                final AnnotationMirror qualifier = annotations.stream()
                    .filter(a -> AnnotationProcessing.isQualifierAnnotated(getTypes(), a))
                    .findFirst().orElse(null);

                dependenciesQueue.offer(parameterType);
                dependencies.add(new Dependency(parameterType, qualifier, annotations));
              }
            }

            @Override
            public void visitClassFieldInjectionSite(TypeElement type, VariableElement field) {
              final TypeMirror parameterType = field.asType();

              final List<AnnotationMirror> annotations =
                  new ArrayList<>(field.getAnnotationMirrors());

              final AnnotationMirror qualifier = annotations.stream()
                  .filter(a -> AnnotationProcessing.isQualifierAnnotated(getTypes(), a)).findFirst()
                  .orElse(null);

              dependenciesQueue.offer(parameterType);
              dependencies.add(new Dependency(parameterType, qualifier, annotations));
            }

            @Override
            public void visitClassConstructorInjectionSite(TypeElement type,
                ExecutableElement constructor) {
              for (VariableElement parameter : constructor.getParameters()) {
                final TypeMirror parameterType = parameter.asType();

                final List<AnnotationMirror> annotations =
                    new ArrayList<>(parameter.getAnnotationMirrors());

                final AnnotationMirror qualifier = annotations.stream()
                    .filter(a -> AnnotationProcessing.isQualifierAnnotated(getTypes(), a))
                    .findFirst().orElse(null);

                dependenciesQueue.offer(parameterType);
                dependencies.add(new Dependency(parameterType, qualifier, annotations));
              }
            }

            @Override
            public void endClass(TypeElement type) {}
          });
    }

    return new DaggerComponentAnalysis(componentType, dependencies);
  }

  private ProcessingEnvironment getProcessingEnv() {
    return processingEnv;
  }

  private Types getTypes() {
    return processingEnv.getTypeUtils();
  }
}
