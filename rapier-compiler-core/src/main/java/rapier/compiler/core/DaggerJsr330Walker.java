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

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import rapier.compiler.core.util.AnnotationProcessing;
import rapier.compiler.core.util.MoreMaps;

/**
 * Visits all the JSR 330 injection sites in the given class that are supported by Dagger. Note that
 * this may not match the set of JSR 330 injection sites defined by the standard, or the set of JSR
 * 330 injection sites used by other frameworks.
 */
public class DaggerJsr330Walker {
  public static interface Visitor {
    public void beginClass(TypeElement type);

    public void visitClassConstructorInjectionSite(TypeElement type, ExecutableElement constructor);

    public void visitClassFieldInjectionSite(TypeElement type, VariableElement field);

    public void visitClassMethodInjectionSite(TypeElement type, ExecutableElement method);

    public void endClass(TypeElement type);
  }

  public static class MethodSignatureKey {
    public static MethodSignatureKey forMethod(Types types, DeclaredType enclosing,
        ExecutableElement method) {
      if (method == null)
        throw new NullPointerException();
      if (method.getKind() != ElementKind.METHOD)
        throw new IllegalArgumentException("element must be a method");

      final ExecutableType methodType = (ExecutableType) types.asMemberOf(enclosing, method);

      final List<String> parameterTypes = new ArrayList<>();
      for (TypeMirror paramType : methodType.getParameterTypes()) {
        parameterTypes.add(paramType.toString());
      }

      return new MethodSignatureKey(method.getSimpleName().toString(), parameterTypes);
    }

    private final String methodName;
    private final List<String> parameterTypes;

    public MethodSignatureKey(String methodName, List<String> parameterTypes) {
      this.methodName = requireNonNull(methodName);
      this.parameterTypes = unmodifiableList(parameterTypes);
    }

    public String getMethodName() {
      return methodName;
    }

    public List<String> getParameterTypes() {
      return parameterTypes;
    }

    @Override
    public int hashCode() {
      return Objects.hash(methodName, parameterTypes);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      MethodSignatureKey that = (MethodSignatureKey) o;
      return methodName.equals(that.methodName) && parameterTypes.equals(that.parameterTypes);
    }
  }

  private final ProcessingEnvironment processingEnv;

  public DaggerJsr330Walker(ProcessingEnvironment processingEnv) {
    this.processingEnv = requireNonNull(processingEnv);
  }

  public void walk(TypeElement type, Visitor visitor) {
    if (type == null)
      throw new NullPointerException();
    if (visitor == null)
      throw new NullPointerException();

    // Ensure the type is a concrete class-like element
    if (type.getKind() != ElementKind.CLASS && type.getKind() != ElementKind.ENUM) {
      throw new IllegalArgumentException("type must be a class or enum");
    }
    if (type.getModifiers().contains(Modifier.ABSTRACT))
      throw new IllegalArgumentException("type must not be abstract");

    visitor.beginClass(type);

    final List<TypeElement> lineage = AnnotationProcessing.lineage(getTypes(), type);

    final List<ExecutableElement> staticMethods = new ArrayList<>();
    final Map<MethodSignatureKey, List<ExecutableElement>> instanceMethods = new HashMap<>();
    for (TypeElement ancestor : lineage) {
      if (ancestor == type) {
        visitClassConstructorInjectionSite(ancestor, visitor);
      }

      final Map<MethodSignatureKey, List<ExecutableElement>> ancestorInstanceMethods =
          collectInstanceMethods(ancestor);
      MoreMaps.mergeAll(instanceMethods, ancestorInstanceMethods, (a, b) -> {
        a.addAll(b);
        return a;
      }, ArrayList::new);

      final List<ExecutableElement> ancestorStaticMethods = collectStaticMethods(ancestor);
      staticMethods.addAll(ancestorStaticMethods);

      visitClassFieldInjectionSites(ancestor, visitor);
    }

    for (Map.Entry<MethodSignatureKey, List<ExecutableElement>> entry : instanceMethods
        .entrySet()) {
      final MethodSignatureKey signature = entry.getKey();
      final List<ExecutableElement> methods = entry.getValue();

      // Methods are visited in depth-first order. The first method in the list is the "prime"
      // method, which is the most derived method in the inheritance hierarchy, i.e., the version
      // of the method closest to the class being analyzed.
      final ExecutableElement primeMethod = methods.get(0);

      // Dagger does not support abstract methods
      if (primeMethod.getModifiers().contains(Modifier.ABSTRACT))
        continue;

      // Dagger does not support private methods
      if (primeMethod.getModifiers().contains(Modifier.PRIVATE))
        continue;

      // Dagger does not support methods with more than one parameter
      if (signature.getParameterTypes().size() != 1)
        continue;

      // Only @Inject-annotated methods are injection sites
      if (methods.stream().noneMatch(m -> m.getAnnotationMirrors().stream()
          .anyMatch(a -> a.getAnnotationType().toString().equals("javax.inject.Inject"))))
        continue;

      visitor.visitClassMethodInjectionSite(type, primeMethod);
    }

    visitor.endClass(type);
  }

  private void visitClassConstructorInjectionSite(TypeElement type, Visitor visitor) {
    boolean visited = false;
    ExecutableElement defaultConstructor = null;
    for (Element element : type.getEnclosedElements()) {
      if (element.getKind() != ElementKind.CONSTRUCTOR)
        continue;

      final ExecutableElement constructor = (ExecutableElement) element;

      // Dagger does not support private constructors
      if (constructor.getModifiers().contains(Modifier.PRIVATE))
        continue;

      if (constructor.getParameters().isEmpty()) {
        defaultConstructor = constructor;
        continue;
      }

      if (constructor.getAnnotationMirrors().stream()
          .anyMatch(a -> a.getAnnotationType().toString().equals("javax.inject.Inject"))) {
        // Only visit one constructor. If there's more than one, that's the user's problem.
        // TODO Handle this more gracefully?
        visitor.visitClassConstructorInjectionSite(type, constructor);
        return;
      }
    }
    if (!visited && defaultConstructor != null) {
      visitor.visitClassConstructorInjectionSite(type, defaultConstructor);
    }
  }

  private Map<MethodSignatureKey, List<ExecutableElement>> collectInstanceMethods(
      TypeElement type) {
    Map<MethodSignatureKey, List<ExecutableElement>> instanceMethods = new HashMap<>();

    for (Element element : type.getEnclosedElements()) {
      if (element.getKind() != ElementKind.METHOD)
        continue;
      final ExecutableElement method = (ExecutableElement) element;

      if (method.getModifiers().contains(Modifier.STATIC))
        continue;

      final MethodSignatureKey key =
          MethodSignatureKey.forMethod(getTypes(), (DeclaredType) type.asType(), method);

      instanceMethods.computeIfAbsent(key, k -> new ArrayList<>()).add(method);
    }

    return instanceMethods.entrySet().stream()
        .collect(toUnmodifiableMap(Map.Entry::getKey, e -> unmodifiableList(e.getValue())));
  }

  private List<ExecutableElement> collectStaticMethods(TypeElement type) {
    List<ExecutableElement> result = new ArrayList<>();

    for (Element element : type.getEnclosedElements()) {
      if (element.getKind() != ElementKind.METHOD)
        continue;
      final ExecutableElement method = (ExecutableElement) element;

      // We only care about static methods
      if (!method.getModifiers().contains(Modifier.STATIC))
        continue;

      // Dagger does not support private methods
      if (method.getModifiers().contains(Modifier.PRIVATE))
        continue;

      // Dagger does not support methods with more than one parameter
      if (method.getParameters().size() != 1)
        continue;

      // Only @Inject-annotated methods are injection sites
      if (method.getAnnotationMirrors().stream()
          .noneMatch(a -> a.getAnnotationType().toString().equals("javax.inject.Inject")))
        continue;;

      result.add(method);
    }

    return unmodifiableList(result);
  }

  private void visitClassFieldInjectionSites(TypeElement type, Visitor visitor) {
    for (Element element : type.getEnclosedElements()) {
      if (element.getKind() != ElementKind.FIELD)
        continue;
      final VariableElement field = (VariableElement) element;

      // Dagger does not support injecting static fields
      if (field.getModifiers().contains(Modifier.STATIC))
        continue;

      // Dagger does not support injecting final fields
      if (field.getModifiers().contains(Modifier.FINAL))
        continue;

      // Dagger does not support injecting private fields
      if (field.getModifiers().contains(Modifier.PRIVATE))
        continue;

      // Only @Inject-annotated fields are injection sites
      if (field.getAnnotationMirrors().stream()
          .noneMatch(a -> a.getAnnotationType().toString().equals("javax.inject.Inject")))
        continue;

      visitor.visitClassFieldInjectionSite(type, field);
    }
  }

  private ProcessingEnvironment getProcessingEnv() {
    return processingEnv;
  }

  private Types getTypes() {
    return getProcessingEnv().getTypeUtils();
  }
}
