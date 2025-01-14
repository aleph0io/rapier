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
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import rapier.cli.CliFlagParameter;
import rapier.compiler.core.model.DaggerInjectionSite;

public class FlagRepresentationKey {
  public static FlagRepresentationKey fromInjectionSite(DaggerInjectionSite dependency) {
    final AnnotationMirror qualifier = dependency.getQualifier().orElseThrow(() -> {
      return new IllegalArgumentException("Dependency must have qualifier");
    });

    if (!qualifier.getAnnotationType().toString()
        .equals(CliFlagParameter.class.getCanonicalName())) {
      throw new IllegalArgumentException("Dependency qualifier must be @CliFlagParameter");
    }

    final TypeMirror type = dependency.getProvidedType();
    final Character positiveShortName = extractFlagParameterpositiveShortName(qualifier);
    final String positiveLongName = extractFlagParameterpositiveLongName(qualifier);
    final Character negativeShortName = extractFlagParameternegativeShortName(qualifier);
    final String negativeLongName = extractFlagParameternegativeLongName(qualifier);
    final Boolean defaultValue = extractFlagParameterDefaultValue(qualifier);

    return new FlagRepresentationKey(type, positiveShortName, positiveLongName, negativeShortName,
        negativeLongName, defaultValue);
  }

  private final TypeMirror type;
  private final Character positiveShortName;
  private final String positiveLongName;
  private final Character negativeShortName;
  private final String negativeLongName;
  private final Boolean defaultValue;

  public FlagRepresentationKey(TypeMirror type, Character positiveShortName,
      String positiveLongName, Character negativeShortName, String negativeLongName,
      Boolean defaultValue) {
    this.type = requireNonNull(type);
    this.positiveShortName = positiveShortName;
    this.positiveLongName = positiveLongName;
    this.negativeShortName = negativeShortName;
    this.negativeLongName = negativeLongName;
    this.defaultValue = defaultValue;
    if (positiveLongName != null && positiveLongName.isEmpty())
      throw new IllegalArgumentException("positiveLongName must not be empty");
    if (negativeLongName != null && negativeLongName.isEmpty())
      throw new IllegalArgumentException("negativeLongName must not be empty");
    if (positiveShortName == null && positiveLongName == null && negativeShortName == null
        && negativeLongName == null)
      throw new IllegalArgumentException(
          "At least one of positiveShortName, positiveLongName, negativeShortName, negativeLongName must be non-null");
  }

  public TypeMirror getType() {
    return type;
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

  public Optional<Boolean> getDefaultValue() {
    return Optional.ofNullable(defaultValue);
  }


  @Override
  public int hashCode() {
    return Objects.hash(defaultValue, negativeLongName, positiveLongName, negativeShortName,
        positiveShortName, type.toString());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    FlagRepresentationKey other = (FlagRepresentationKey) obj;
    return Objects.equals(defaultValue, other.defaultValue)
        && Objects.equals(negativeLongName, other.negativeLongName)
        && Objects.equals(positiveLongName, other.positiveLongName)
        && Objects.equals(negativeShortName, other.negativeShortName)
        && Objects.equals(positiveShortName, other.positiveShortName)
        && Objects.equals(type.toString(), other.type.toString());
  }

  @Override
  public String toString() {
    return "FlagRepresentationKey [type=" + type + ", positiveShortName=" + positiveShortName
        + ", positiveLongName=" + positiveLongName + ", negativeShortName=" + negativeShortName
        + ", negativeLongName=" + negativeLongName + ", defaultValue=" + defaultValue + "]";
  }

  private static Character extractFlagParameterpositiveShortName(AnnotationMirror annotation) {
    return FlagParameterKey.extractFlagParameterShortPositiveName(annotation);
  }

  private static String extractFlagParameterpositiveLongName(AnnotationMirror annotation) {
    return FlagParameterKey.extractFlagParameterLongPositiveName(annotation);
  }

  private static Character extractFlagParameternegativeShortName(AnnotationMirror annotation) {
    return FlagParameterKey.extractFlagParameterShortNegativeName(annotation);
  }

  private static String extractFlagParameternegativeLongName(AnnotationMirror annotation) {
    return FlagParameterKey.extractFlagParameterLongNegativeName(annotation);
  }

  private static Boolean extractFlagParameterDefaultValue(AnnotationMirror annotation) {
    // Ensure the annotation is of the correct type
    assert annotation.getAnnotationType().toString()
        .equals(CliFlagParameter.class.getCanonicalName());

    // Find the 'defaultValue' attribute in the annotation's element values
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("defaultValue")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<Boolean, Void>() {
          @Override
          public Boolean visitEnumConstant(VariableElement c, Void p) {
            // Map the enum constant to its corresponding Boolean value
            String enumName = c.getSimpleName().toString();
            switch (enumName) {
              case "TRUE":
                return Boolean.TRUE;
              case "FALSE":
                return Boolean.FALSE;
              case "NONE":
                return null;
              default:
                // TODO Throw an exception?
                return null; // No default value
            }
          }
        }, null)).orElse(null); // Return null if 'defaultValue' is not present
  }
}
