/*-
 * =================================LICENSE_START==================================
 * rapier-processor-cli
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
package rapier.processor.cli.model;

import static java.util.Objects.requireNonNull;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import rapier.core.model.DaggerInjectionSite;
import rapier.processor.cli.NamedCliParameter;

public class NamedKey implements Comparable<NamedKey> {
  public static NamedKey fromDependency(DaggerInjectionSite dependency) {
    final AnnotationMirror qualifier = dependency.getQualifier().orElseThrow(() -> {
      return new IllegalArgumentException("Dependency must have qualifier");
    });

    if (!qualifier.getAnnotationType().toString()
        .equals(NamedCliParameter.class.getCanonicalName())) {
      throw new IllegalArgumentException("Dependency qualifier must be @NamedCliParameter");
    }

    final TypeMirror type = dependency.getProvidedType();
    final String shortName = extractNamedParameterShortName(qualifier);
    final String longName = extractNamedParameterLongName(qualifier);

    return new NamedKey(type, shortName, longName);
  }

  private final TypeMirror type;
  private final String shortName;
  private final String longName;

  public NamedKey(TypeMirror type, String shortName, String longName) {
    this.type = requireNonNull(type);
    this.shortName = shortName;
    this.longName = longName;
    if (shortName != null && shortName.isEmpty())
      throw new IllegalArgumentException("shortName must not be empty");
    if (longName != null && longName.isEmpty())
      throw new IllegalArgumentException("longName must not be empty");
    if (shortName != null && shortName.length() > 1)
      throw new IllegalArgumentException("shortName must be a single character");
    if (shortName == null && longName == null)
      throw new IllegalArgumentException("At least one of shortName or longName must be non-null");
  }

  public TypeMirror getType() {
    return type;
  }

  public Optional<String> getShortName() {
    return Optional.ofNullable(shortName);
  }

  public Optional<String> getLongName() {
    return Optional.ofNullable(longName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(longName, shortName, type);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    NamedKey other = (NamedKey) obj;
    return Objects.equals(longName, other.longName) && Objects.equals(shortName, other.shortName)
        && Objects.equals(type, other.type);
  }

  private static final Comparator<NamedKey> COMPARATOR = Comparator
      .<NamedKey, String>comparing(k -> k.getShortName().orElse(null),
          Comparator.nullsFirst(Comparator.naturalOrder()))
      .thenComparing(k -> k.getLongName().orElse(null),
          Comparator.nullsFirst(Comparator.naturalOrder()))
      .thenComparing(k -> k.getType().toString());

  @Override
  public int compareTo(NamedKey that) {
    return COMPARATOR.compare(this, that);
  }

  @Override
  public String toString() {
    return "NamedKey [type=" + type + ", shortName=" + shortName + ", longName=" + longName + "]";
  }

  private static String extractNamedParameterShortName(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(NamedCliParameter.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("shortName")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            if (s.isEmpty())
              return null;
            return s;
          }
        }, null)).orElse(null);
  }

  private static String extractNamedParameterLongName(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(NamedCliParameter.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("longName")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            if (s.isEmpty())
              return null;
            return s;
          }
        }, null)).orElse(null);
  }
}
