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
package rapier.processor.core.model;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

public class Dependency {
  /**
   * The element that this dependency is declared on.
   */
  private final Element element;

  /**
   * The type of this dependency.
   */
  private final TypeMirror type;

  /**
   * The qualifier annotation for this dependency, if any.
   */
  private final AnnotationMirror qualifier;

  /**
   * All annotations on this dependency. This includes the qualifier annotation, if any.
   */
  private final List<AnnotationMirror> annotations;

  public Dependency(Element element, TypeMirror type, AnnotationMirror qualifier,
      List<AnnotationMirror> annotations) {
    this.element = requireNonNull(element);
    this.type = requireNonNull(type);
    this.qualifier = qualifier;
    this.annotations = unmodifiableList(annotations);
  }

  public Element getElement() {
    return element;
  }

  public TypeMirror getType() {
    return type;
  }

  public Optional<AnnotationMirror> getQualifier() {
    return Optional.ofNullable(qualifier);
  }

  public List<AnnotationMirror> getAnnotations() {
    return annotations;
  }

  @Override
  public int hashCode() {
    return Objects.hash(annotations, element, qualifier, type);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Dependency other = (Dependency) obj;
    return Objects.equals(annotations, other.annotations) && Objects.equals(element, other.element)
        && Objects.equals(qualifier, other.qualifier) && Objects.equals(type, other.type);
  }

  @Override
  public String toString() {
    return "Dependency [element=" + element + ", type=" + type + ", qualifier=" + qualifier
        + ", annotations=" + annotations + "]";
  }
}
