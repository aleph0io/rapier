/*-
 * =================================LICENSE_START==================================
 * rapier-cli-compiler
 * ====================================SECTION=====================================
 * Copyright (C) 2024 - 2025 aleph0
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
package rapier.cli.compiler.model;

import static java.util.Objects.requireNonNull;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import rapier.cli.CliOptionParameter;
import rapier.compiler.core.model.DaggerInjectionSite;

public class OptionRepresentationKey {
  public static OptionRepresentationKey fromInjectionSite(DaggerInjectionSite dependency) {
    final AnnotationMirror qualifier = dependency.getQualifier().orElseThrow(() -> {
      return new IllegalArgumentException("Dependency must have qualifier");
    });

    if (!qualifier.getAnnotationType().toString()
        .equals(CliOptionParameter.class.getCanonicalName())) {
      throw new IllegalArgumentException("Dependency qualifier must be @CliOptionParameter");
    }

    final TypeMirror type = dependency.getProvidedType();
    final Character shortName = extractOptionParameterShortName(qualifier);
    final String longName = extractOptionParameterLongName(qualifier);
    final String defaultValue = extractOptionParameterDefaultValue(qualifier);

    return new OptionRepresentationKey(type, shortName, longName, defaultValue);
  }

  private final TypeMirror type;
  private final Character shortName;
  private final String longName;
  private final String defaultValue;

  public OptionRepresentationKey(TypeMirror type, Character shortName, String longName,
      String defaultValue) {
    this.type = requireNonNull(type);
    this.shortName = shortName;
    this.longName = longName;
    this.defaultValue = defaultValue;
    if (longName != null && longName.isEmpty())
      throw new IllegalArgumentException("longName must not be empty");
    if (shortName == null && longName == null)
      throw new IllegalArgumentException("At least one of shortName or longName must be non-null");
  }

  public TypeMirror getType() {
    return type;
  }

  public Optional<Character> getShortName() {
    return Optional.ofNullable(shortName);
  }

  public Optional<String> getLongName() {
    return Optional.ofNullable(longName);
  }

  public Optional<String> getDefaultValue() {
    return Optional.ofNullable(defaultValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(defaultValue, longName, shortName, type.toString());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    OptionRepresentationKey other = (OptionRepresentationKey) obj;
    return Objects.equals(defaultValue, other.defaultValue)
        && Objects.equals(longName, other.longName) && Objects.equals(shortName, other.shortName)
        && Objects.equals(type.toString(), other.type.toString());
  }

  @Override
  public String toString() {
    return "OptionRepresentationKey [type=" + type + ", shortName=" + shortName + ", longName="
        + longName + ", defaultValue=" + defaultValue + "]";
  }

  private static Character extractOptionParameterShortName(AnnotationMirror annotation) {
    return OptionParameterKey.extractOptionParameterShortName(annotation);
  }

  private static String extractOptionParameterLongName(AnnotationMirror annotation) {
    return OptionParameterKey.extractOptionParameterLongName(annotation);
  }

  private static String extractOptionParameterDefaultValue(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(CliOptionParameter.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("defaultValue")).findFirst()
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
