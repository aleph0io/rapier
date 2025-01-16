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
package rapier.compiler.core.model;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

public class DaggerInjectionSite {
  /**
   * The element that this dependency is declared on.
   */
  private final Element element;

  /**
   * The type of the injection site. Useful for debugging and generating error messages.
   */
  private final DaggerInjectionSiteType siteType;

  /**
   * The style of provision for this dependency. Useful for analysis, debugging, and generating
   * error messages.
   */
  private final DaggerProvisionStyle provisionStyle;

  /**
   * The raw provisioned type of this dependency.
   */
  private final TypeMirror provisionedType;

  /**
   * The processed provisioned type of this dependency. This is the type that should be used for
   * generating code.
   */
  private final TypeMirror providedType;

  /**
   * The qualifier annotation for this dependency, if any.
   */
  private final AnnotationMirror qualifier;

  /**
   * All annotations on this dependency. This includes the qualifier annotation, if any.
   */
  private final List<AnnotationMirror> annotations;

  /**
   * Whether or not this injection site can receive logical null values.
   * 
   * <p>
   * An injection site is implicitly nullable if:
   * 
   * <ul>
   * <li>It provisions an {@link DaggerProvisionStyle#OPTIONAL Optional} type</li>
   * </ul>
   * 
   * <p>
   * An injection site is implicitly non-nullable if:
   * 
   * <ul>
   * <li>It provisions an {@link DaggerProvisionStyle#PRIMITIVE primitive} type</li> </u>
   * 
   * <p>
   * An injection site is explicitly nullable if:
   * 
   * <ul>
   * <li>It is annotated with {@code @Nullable}</li>
   * </ul>
   * 
   * <p>
   * It is an error for an injection site to be both implicity non-nullable and explicitly nullable.
   * It is redundant, but not an error, for an injection site to be both implicitly nullable and
   * explicitly nullable.
   */
  private final boolean nullable;

  public DaggerInjectionSite(Element element, DaggerInjectionSiteType siteType,
      DaggerProvisionStyle provisionStyle, TypeMirror provisionedType, TypeMirror providedType,
      AnnotationMirror qualifier, List<AnnotationMirror> annotations, boolean nullable) {
    this.element = requireNonNull(element);
    this.siteType = requireNonNull(siteType);
    this.provisionStyle = requireNonNull(provisionStyle);
    this.provisionedType = requireNonNull(provisionedType);
    this.providedType = requireNonNull(providedType);
    this.qualifier = qualifier;
    this.annotations = unmodifiableList(annotations);
    this.nullable = nullable;
  }

  public Element getElement() {
    return element;
  }

  public DaggerInjectionSiteType getSiteType() {
    return siteType;
  }

  public DaggerProvisionStyle getProvisionStyle() {
    return provisionStyle;
  }

  public TypeMirror getProvisionedType() {
    return provisionedType;
  }

  public TypeMirror getProvidedType() {
    return providedType;
  }

  public Optional<AnnotationMirror> getQualifier() {
    return Optional.ofNullable(qualifier);
  }

  public List<AnnotationMirror> getAnnotations() {
    return annotations;
  }

  public boolean isNullable() {
    return nullable;
  }

  @Override
  public int hashCode() {
    return Objects.hash(annotations, element, nullable, providedType, provisionStyle,
        provisionedType, qualifier, siteType);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    DaggerInjectionSite other = (DaggerInjectionSite) obj;
    return Objects.equals(annotations, other.annotations) && Objects.equals(element, other.element)
        && nullable == other.nullable && Objects.equals(providedType, other.providedType)
        && provisionStyle == other.provisionStyle
        && Objects.equals(provisionedType, other.provisionedType)
        && Objects.equals(qualifier, other.qualifier) && siteType == other.siteType;
  }

  @Override
  public String toString() {
    return "DaggerInjectionSite [element=" + element + ", siteType=" + siteType
        + ", provisionStyle=" + provisionStyle + ", provisionedType=" + provisionedType
        + ", providedType=" + providedType + ", qualifier=" + qualifier + ", annotations="
        + annotations + ", nullable=" + nullable + "]";
  }
}
