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
package rapier.cli.compiler.model;

import static java.util.Objects.requireNonNull;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import rapier.cli.CliPositionalParameter;
import rapier.compiler.core.model.DaggerInjectionSite;

public class PositionalRepresentationKey {
  public static PositionalRepresentationKey fromInjectionSite(DaggerInjectionSite dependency) {
    final AnnotationMirror qualifier = dependency.getQualifier().orElseThrow(() -> {
      return new IllegalArgumentException("Dependency must have qualifier");
    });

    if (!qualifier.getAnnotationType().toString()
        .equals(CliPositionalParameter.class.getCanonicalName())) {
      throw new IllegalArgumentException("Dependency qualifier must be @CliPositionalParameter");
    }

    final TypeMirror type = dependency.getProvidedType();
    final int position = extractPositionalParameterPosition(qualifier);
    final String defaultValue = extractPositionalParameterDefaultValue(qualifier);

    return new PositionalRepresentationKey(type, position, defaultValue);
  }

  private final TypeMirror type;
  private final int position;
  private final String defaultValue;

  public PositionalRepresentationKey(TypeMirror type, int position, String defaultValue) {
    this.type = requireNonNull(type);
    this.position = position;
    this.defaultValue = defaultValue;
    if (position < 0)
      throw new IllegalArgumentException("Position must be non-negative");
  }

  public TypeMirror getType() {
    return type;
  }

  public int getPosition() {
    return position;
  }

  public Optional<String> getDefaultValue() {
    return Optional.ofNullable(defaultValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(defaultValue, position, type.toString());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    PositionalRepresentationKey other = (PositionalRepresentationKey) obj;
    return Objects.equals(defaultValue, other.defaultValue) && position == other.position
        && Objects.equals(type.toString(), other.type.toString());
  }

  @Override
  public String toString() {
    return "PositionalRepresentationKey [type=" + type + ", position=" + position
        + ", defaultValue=" + defaultValue + "]";
  }

  private static int extractPositionalParameterPosition(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(CliPositionalParameter.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("value")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<Integer, Void>() {
          @Override
          public Integer visitInt(int i, Void p) {
            return i;
          }
        }, null)).orElseThrow(() -> {
          return new AssertionError("No position value for @CliPositionalParameter");
        });
  }

  private static String extractPositionalParameterDefaultValue(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(CliPositionalParameter.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("defaultValue")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            if (s.equals(CliPositionalParameter.DEFAULT_VALUE_NOT_SET))
              return null;
            return s;
          }
        }, null)).orElse(null);
  }
}
