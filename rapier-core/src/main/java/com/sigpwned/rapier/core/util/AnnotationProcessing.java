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
package com.sigpwned.rapier.core.util;

import static java.util.Collections.unmodifiableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
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
      if (annotation.getAnnotationType().toString().equals(qualifiedName))
        return Optional.of(annotation);

    return Optional.empty();
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
}
