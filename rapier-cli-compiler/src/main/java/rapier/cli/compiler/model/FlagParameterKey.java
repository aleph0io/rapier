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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import rapier.cli.CliFlagParameter;
import rapier.core.model.DaggerInjectionSite;

public class FlagParameterKey {
  public static FlagParameterKey fromInjectionSite(DaggerInjectionSite dependency) {
    final AnnotationMirror qualifier = dependency.getQualifier().orElseThrow(() -> {
      return new IllegalArgumentException("Dependency must have qualifier");
    });

    if (!qualifier.getAnnotationType().toString()
        .equals(CliFlagParameter.class.getCanonicalName())) {
      throw new IllegalArgumentException("Dependency qualifier must be @CliFlagParameter");
    }

    final Character shortPositiveName = extractFlagParameterShortPositiveName(qualifier);

    final String positiveLongName = extractFlagParameterLongPositiveName(qualifier);

    final Character negativeShortName = extractFlagParameterShortNegativeName(qualifier);

    final String negativeLongName = extractFlagParameterLongNegativeName(qualifier);

    return new FlagParameterKey(shortPositiveName, positiveLongName, negativeShortName,
        negativeLongName);
  }

  private final Character positiveShortName;
  private final String positiveLongName;
  private final Character negativeShortName;
  private final String negativeLongName;

  public FlagParameterKey(Character shortPositiveName, String positiveLongName,
      Character negativeShortName, String negativeLongName) {
    if (positiveLongName != null && positiveLongName.isEmpty())
      throw new IllegalArgumentException("positiveLongName must not be empty");
    if (negativeLongName != null && negativeLongName.isEmpty())
      throw new IllegalArgumentException("negativeLongName must not be empty");
    if (shortPositiveName == null && positiveLongName == null && negativeShortName == null
        && negativeLongName == null)
      throw new IllegalArgumentException(
          "At least one of positiveShortName, positiveLongName, negativeShortName, negativeLongName must be non-null");
    this.positiveShortName = shortPositiveName;
    this.positiveLongName = positiveLongName;
    this.negativeShortName = negativeShortName;
    this.negativeLongName = negativeLongName;
  }

  public Optional<Character> getPositiveShortName() {
    return Optional.ofNullable(positiveShortName);
  }

  public Optional<String> getPositiveLongName() {
    return Optional.ofNullable(positiveLongName);
  }

  public Optional<Character> getNegativeShortName() {
    return Optional.ofNullable(negativeShortName);
  }

  public Optional<String> getNegativeLongName() {
    return Optional.ofNullable(negativeLongName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(negativeLongName, positiveLongName, negativeShortName, positiveShortName);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    FlagParameterKey other = (FlagParameterKey) obj;
    return Objects.equals(negativeLongName, other.negativeLongName)
        && Objects.equals(positiveLongName, other.positiveLongName)
        && Objects.equals(negativeShortName, other.negativeShortName)
        && Objects.equals(positiveShortName, other.positiveShortName);
  }

  @Override
  public String toString() {
    return "FlagParameterKey [positiveShortName=" + positiveShortName + ", positiveLongName="
        + positiveLongName + ", negativeShortName=" + negativeShortName + ", negativeLongName="
        + negativeLongName + "]";
  }

  /* default */ static Character extractFlagParameterShortPositiveName(
      AnnotationMirror annotation) {
    return extractFlagParameterShortName(annotation, "positiveShortName");
  }

  /* default */ static String extractFlagParameterLongPositiveName(AnnotationMirror annotation) {
    return extractFlagParameterLongName(annotation, "positiveLongName");
  }

  /* default */ static Character extractFlagParameterShortNegativeName(
      AnnotationMirror annotation) {
    return extractFlagParameterShortName(annotation, "negativeShortName");
  }

  /* default */ static String extractFlagParameterLongNegativeName(AnnotationMirror annotation) {
    return extractFlagParameterLongName(annotation, "negativeLongName");
  }

  private static Character extractFlagParameterShortName(AnnotationMirror annotation, String name) {
    if (name == null)
      return null;
    assert annotation.getAnnotationType().toString()
        .equals(CliFlagParameter.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals(name)).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<Character, Void>() {
          @Override
          public Character visitChar(char c, Void p) {
            if (c == '\0')
              return null;
            return c;
          }
        }, null)).orElse(null);
  }

  private static String extractFlagParameterLongName(AnnotationMirror annotation, String name) {
    if (name == null)
      return null;
    assert annotation.getAnnotationType().toString()
        .equals(CliFlagParameter.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals(name)).findFirst()
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
