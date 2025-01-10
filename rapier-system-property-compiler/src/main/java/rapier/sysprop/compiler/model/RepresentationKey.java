/*-
 * =================================LICENSE_START==================================
 * rapier-processor-environment-variable
 * ====================================SECTION=====================================
 * Copyright (C) 2024 - 2025 Andy Boothe
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
package rapier.sysprop.compiler.model;

import static java.util.Objects.requireNonNull;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import rapier.core.model.DaggerInjectionSite;
import rapier.sysprop.SystemProperty;

/**
 * A grouping key for the JSR 330 representation of a system property, namely type and qualifier,
 * which is comprised of a name and an optional default value in this case.
 */
public class RepresentationKey implements Comparable<RepresentationKey> {
  public static RepresentationKey fromInjectionSite(DaggerInjectionSite dependency) {
    final AnnotationMirror qualifier = dependency.getQualifier().orElseThrow(() -> {
      return new IllegalArgumentException("Dependency must have qualifier");
    });

    if (!qualifier.getAnnotationType().toString().equals(SystemProperty.class.getCanonicalName())) {
      throw new IllegalArgumentException("Dependency qualifier must be @SystemProperty");
    }

    final TypeMirror type = dependency.getProvidedType();
    final String name = extractName(qualifier);
    final String defaultValue = extractDefaultValue(qualifier);

    return new RepresentationKey(type, name, defaultValue);
  }

  private final TypeMirror type;
  private final String name;
  private final String defaultValue;

  public RepresentationKey(TypeMirror type, String name, String defaultValue) {
    this.type = requireNonNull(type);
    this.name = requireNonNull(name);
    this.defaultValue = defaultValue;
  }

  public TypeMirror getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public Optional<String> getDefaultValue() {
    return Optional.ofNullable(defaultValue);
  }

  private final Comparator<RepresentationKey> COMPARATOR =
      Comparator.comparing(RepresentationKey::getName)
          .thenComparing(Comparator.comparing(x -> x.getDefaultValue().orElse(null),
              Comparator.nullsFirst(Comparator.naturalOrder())))
          .thenComparing(x -> x.getType().toString());

  public int compareTo(RepresentationKey that) {
    return COMPARATOR.compare(this, that);
  }

  @Override
  public int hashCode() {
    return Objects.hash(defaultValue, name, type.toString());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    RepresentationKey other = (RepresentationKey) obj;
    return Objects.equals(defaultValue, other.defaultValue) && Objects.equals(name, other.name)
        && Objects.equals(type.toString(), other.type.toString());
  }

  @Override
  public String toString() {
    return "SystemPropertyKey [type=" + type + ", name=" + name + ", defaultValue=" + defaultValue
        + "]";
  }

  private static String extractName(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(SystemProperty.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("value")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            return s;
          }
        }, null)).orElseThrow(() -> {
          return new AssertionError("No string value for @SystemProperty(name)");
        });
  }

  private static String extractDefaultValue(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(SystemProperty.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("defaultValue")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            if (s.equals(SystemProperty.DEFAULT_VALUE_NOT_SET))
              return null;
            return s;
          }
        }, null)).orElse(null);
  }
}
