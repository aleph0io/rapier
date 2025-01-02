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
package rapier.core.util;

import static java.util.Collections.unmodifiableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

public final class AnnotationProcessing {
  private AnnotationProcessing() {}

  public static Optional<AnnotationMirror> findFirstAnnotationByQualifiedName(Element element,
      String qualifiedName) {
    if (element == null)
      throw new NullPointerException();
    if (qualifiedName == null)
      throw new NullPointerException();

    for (AnnotationMirror annotation : element.getAnnotationMirrors())
      if (hasQualifiedName(annotation, qualifiedName))
        return Optional.of(annotation);

    return Optional.empty();
  }

  public static boolean hasQualifiedName(AnnotationMirror annotation, String qualifiedName) {
    if (annotation == null)
      throw new NullPointerException();
    if (qualifiedName == null)
      throw new NullPointerException();

    return annotation.getAnnotationType().toString().equals(qualifiedName);
  }

  public static Optional<AnnotationMirror> findFirstAnnotationBySimpleName(Element element,
      String simpleName) {
    if (element == null)
      throw new NullPointerException();
    if (simpleName == null)
      throw new NullPointerException();

    for (AnnotationMirror annotation : element.getAnnotationMirrors())
      if (hasSimpleName(annotation, simpleName))
        return Optional.of(annotation);

    return Optional.empty();
  }

  public static boolean hasSimpleName(AnnotationMirror annotation, String simpleName) {
    if (annotation == null)
      throw new NullPointerException();
    if (simpleName == null)
      throw new NullPointerException();

    assert simpleName.indexOf('.') == -1;

    return annotation.getAnnotationType().toString().equals(simpleName)
        || annotation.getAnnotationType().toString().endsWith("." + simpleName);
  }

  /**
   * Returns the erased type of the given type.
   * 
   * @param types the types utility
   * @param type the type for which to compute the erased type
   * @return the computed erased type
   * 
   * @throws NullPointerException if {@code types} is {@code null}
   * @throws NullPointerException if {@code type} is {@code null}
   * @throws IllegalArgumentException if {@code type} is a primitive type
   */
  public static TypeMirror erasedType(Types types, TypeMirror type) {
    if (types == null)
      throw new NullPointerException();
    if (type == null)
      throw new NullPointerException();
    if (type.getKind().isPrimitive())
      throw new IllegalArgumentException("Primitive types do not have an erased type");
    return types.erasure(type);
  }

  public static boolean isDefaultConstructor(ExecutableElement constructor) {
    if (constructor == null)
      throw new NullPointerException();

    assert constructor.getKind() == ElementKind.CONSTRUCTOR;

    return constructor.getParameters().isEmpty();
  }

  /**
   * Returns the lineage of the given type, which includes the type itself, the type's superclass,
   * the superclass's superclass, and so on, and the interfaces the type implements and the
   * interfaces implemented by those interfaces, and so on.
   * 
   * @param types the types utility
   * @param type the type for which to compute the lineage
   * @return the computed lineage
   * 
   * @see #superclasses(Types, TypeElement)
   * @see #superinterfaces(Types, TypeElement)
   */
  public static List<TypeElement> lineage(Types types, TypeElement type) {
    final List<TypeElement> result = new ArrayList<>();

    result.add(type);
    result.addAll(superclasses(types, type));
    result.addAll(superinterfaces(types, type));

    return unmodifiableList(result);
  }

  /**
   * Returns the classes of the given type, which includes the type's superclass, the superclass's
   * superclass, and so on. For primitive types, the lineage is empty. For interface types, the
   * lineage is empty. For class types, the list always ends with {@code Object}. The order of the
   * classes is from the given type first to {@code Object} last.
   * 
   * @param types the types utility
   * @param type the type for which to compute the lineage
   * @return the computed lineage
   */
  public static List<TypeElement> superclasses(Types types, TypeElement type) {
    final List<TypeElement> result = new ArrayList<>();

    // Any superclasses of the component are also in the lineage
    TypeMirror ancestor = type.getSuperclass();
    while (ancestor.getKind() != TypeKind.NONE) {
      final Element ancestorAsElement = types.asElement(ancestor);
      if (ancestorAsElement == null)
        break;
      if (ancestorAsElement.getKind() != ElementKind.CLASS)
        break;

      final TypeElement ancestorAsClass = (TypeElement) ancestorAsElement;

      result.add(ancestorAsClass);

      ancestor = ancestorAsClass.getSuperclass();
    }

    return unmodifiableList(result);
  }

  /**
   * Returns the interfaces of the given type, which includes the interfaces it implements and the
   * interfaces implemented by those interfaces, and so on. The order of the interfaces is not
   * specified.
   * 
   * @param types the types utility
   * @param type the type for which to compute the lineage
   * @return the computed lineage
   */
  public static List<TypeElement> superinterfaces(Types types, TypeElement type) {
    final List<TypeElement> result = new ArrayList<>();

    // Any interfaces implemented by the component are also in the lineage
    final List<TypeMirror> interfaces = new ArrayList<>();
    interfaces.addAll(type.getInterfaces());
    while (!interfaces.isEmpty()) {
      final TypeMirror iface = interfaces.remove(0);

      final Element ifaceAsElement = types.asElement(iface);
      if (ifaceAsElement == null)
        continue;
      if (ifaceAsElement.getKind() != ElementKind.INTERFACE)
        continue;

      final TypeElement ifaceAsInterface = (TypeElement) ifaceAsElement;
      if (result.contains(ifaceAsInterface))
        continue;

      result.add(ifaceAsInterface);

      interfaces.addAll(ifaceAsInterface.getInterfaces());
    }

    return unmodifiableList(result);
  }

  /**
   * Returns the first {@code public static T fromString(String)} method for the given type element,
   * where {@code T} is the type element itself.
   * 
   * @param types the types utility
   * @param typeElement the type element for which to find the method, which must be a class, enum,
   *        or interface.
   * @return the method, if found, or {@link Optional#empty()} otherwise
   * 
   * @throws NullPointerException if {@code types} is {@code null}
   * @throws NullPointerException if {@code typeElement} is {@code null}
   * @throws IllegalArgumentException if {@code typeElement} is not a class, enum, or interface
   */
  public static Optional<ExecutableElement> findFromStringMethod(Types types,
      TypeElement typeElement) {
    if (types == null)
      throw new NullPointerException();
    if (typeElement == null)
      throw new NullPointerException();
    if (!typeElement.getKind().isClass() && !typeElement.getKind().isInterface())
      throw new IllegalArgumentException("Type element must be a declared type");

    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.METHOD)
        continue;
      final ExecutableElement method = (ExecutableElement) enclosed;
      if (method.getSimpleName().contentEquals("fromString")
          && method.getModifiers().contains(Modifier.PUBLIC)
          && method.getModifiers().contains(Modifier.STATIC) && method.getParameters().size() == 1
          && method.getParameters().get(0).asType().toString().equals("java.lang.String")
          && types.isSameType(method.getReturnType(), typeElement.asType())) {
        return Optional.of(method);
      }
    }

    return Optional.empty();
  }

  /**
   * Returns the first {@code public static T valueOf(P)} method for the given type element, where
   * {@code T} is the type element itself and {@code P} is the given parameter type.
   * 
   * @param types the types utility
   * @param typeElement the type element for which to find the method, which must be a class, enum,
   *        or interface.
   * @param parameterType the parameter type
   * @return the method, if found, or {@link Optional#empty()} otherwise
   * 
   * @throws NullPointerException if {@code types} is {@code null}
   * @throws NullPointerException if {@code typeElement} is {@code null}
   * @throws NullPointerException if {@code parameterType} is {@code null}
   * @throws IllegalArgumentException if {@code typeElement} is not a class, enum, or interface
   */
  public static Optional<ExecutableElement> findValueOfMethod(Types types, TypeElement typeElement,
      TypeMirror parameterType) {
    if (types == null)
      throw new NullPointerException();
    if (typeElement == null)
      throw new NullPointerException();
    if (parameterType == null)
      throw new NullPointerException();
    if (!typeElement.getKind().isClass() && !typeElement.getKind().isInterface())
      throw new IllegalArgumentException("Type element must be a declared type");

    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.METHOD)
        continue;
      final ExecutableElement method = (ExecutableElement) enclosed;
      if (method.getSimpleName().contentEquals("valueOf")
          && method.getModifiers().contains(Modifier.PUBLIC)
          && method.getModifiers().contains(Modifier.STATIC) && method.getParameters().size() == 1
          && types.isSameType(method.getParameters().get(0).asType(), parameterType)
          && types.isSameType(method.getReturnType(), typeElement.asType())) {
        return Optional.of(method);
      }
    }

    return Optional.empty();
  }

  /**
   * Returns the first public constructor of the given concrete type element that takes a single
   * parameter of the given type.
   * 
   * @param types the types utility
   * @param typeElement the type element for which to find the constructor, which must be a class
   * @param parameterType the parameter type
   * @return the constructor, if found, or {@link Optional#empty()} otherwise
   * 
   * @throws NullPointerException if {@code types} is {@code null}
   * @throws NullPointerException if {@code typeElement} is {@code null}
   * @throws NullPointerException if {@code parameterType} is {@code null}
   * @throws IllegalArgumentException if {@code typeElement} is not a class type
   * @throws IllegalArgumentException if {@code typeElement} is not a concrete type
   */
  public static Optional<ExecutableElement> findSingleArgumentConstructor(Types types,
      TypeElement typeElement, TypeMirror parameterType) {
    if (types == null)
      throw new NullPointerException();
    if (typeElement == null)
      throw new NullPointerException();
    if (parameterType == null)
      throw new NullPointerException();
    if (typeElement.getKind() != ElementKind.CLASS)
      throw new IllegalArgumentException("Type element must be a class type");
    if (typeElement.getModifiers().contains(Modifier.ABSTRACT))
      throw new IllegalArgumentException("Type element must be a concrete class type");

    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.CONSTRUCTOR)
        continue;
      final ExecutableElement method = (ExecutableElement) enclosed;
      if (method.getModifiers().contains(Modifier.PUBLIC) && method.getParameters().size() == 1
          && types.isSameType(method.getParameters().get(0).asType(), parameterType)) {
        return Optional.of(method);
      }
    }

    return Optional.empty();
  }


  public static boolean isInject(AnnotationMirror annotation) {
    return annotation.getAnnotationType().toString().endsWith(".Inject");
  }

  public static boolean isNullable(AnnotationMirror annotation) {
    return annotation.getAnnotationType().toString().endsWith(".Nullable");
  }

  public static boolean isQualifier(AnnotationMirror annotation) {
    return annotation.getAnnotationType().toString().endsWith(".Qualifier");
  }

  public static boolean isScope(AnnotationMirror annotation) {
    return annotation.getAnnotationType().toString().endsWith(".Scope");
  }

  /**
   * Returns {@code true} if the given annotation's type is itself annotated with
   * {@link javax.inject.Qualifier}, or {@code false} otherwise.
   */
  public static boolean isQualifierAnnotated(Types types, AnnotationMirror annotation) {
    final TypeMirror annotationType = annotation.getAnnotationType();
    final TypeElement annotationTypeElement = (TypeElement) types.asElement(annotationType);
    return annotationTypeElement.getAnnotationMirrors().stream()
        .anyMatch(AnnotationProcessing::isQualifier);
  }

  /**
   * Returns {@code true} if the given annotation's type is itself annotated with
   * {@link javax.inject.Qualifier}, or {@code false} otherwise.
   */
  public static boolean isScopeAnnotated(Types types, AnnotationMirror annotation) {
    final TypeMirror annotationType = annotation.getAnnotationType();
    final TypeElement annotationTypeElement = (TypeElement) types.asElement(annotationType);
    return annotationTypeElement.getAnnotationMirrors().stream()
        .anyMatch(AnnotationProcessing::isQualifier);
  }
}
