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
import static java.util.function.Predicate.not;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import rapier.cli.CliOptionParameterHelp;
import rapier.compiler.core.model.DaggerInjectionSite;

public class OptionParameterHelp {
  public static Optional<OptionParameterHelp> fromInjectionSite(DaggerInjectionSite dependency) {
    final AnnotationMirror annotation =
        dependency.getAnnotations().stream().filter(a -> a.getAnnotationType().toString()
            .equals(CliOptionParameterHelp.class.getCanonicalName())).findFirst().orElse(null);
    if (annotation == null)
      return Optional.empty();
    return fromAnnotationMirror(annotation);
  }

  public static Optional<OptionParameterHelp> fromAnnotationMirror(AnnotationMirror annotation) {
    if (annotation == null)
      throw new NullPointerException();

    if (!annotation.getAnnotationType().toString()
        .equals(CliOptionParameterHelp.class.getCanonicalName()))
      return Optional.empty();

    final String valueName = extractValueName(annotation);
    final String description = extractDescription(annotation);

    return Optional.of(new OptionParameterHelp(valueName, description));
  }

  private final String valueName;

  private final String description;

  public OptionParameterHelp(String valueName, String description) {
    this.valueName = requireNonNull(valueName);
    this.description = description;
  }

  public String getValueName() {
    return valueName;
  }

  public Optional<String> getDescription() {
    return Optional.ofNullable(description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(description, valueName);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    OptionParameterHelp other = (OptionParameterHelp) obj;
    return Objects.equals(description, other.description)
        && Objects.equals(valueName, other.valueName);
  }

  @Override
  public String toString() {
    return "CliHelpMetadata [valueName=" + valueName + ", description=" + description + "]";
  }

  private static String extractValueName(AnnotationMirror annotation) {
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("valueName")).map(e -> e.getValue())
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            return s;
          }
        }, null)).filter(not(String::isEmpty)).findFirst().orElse("value");
  }

  private static String extractDescription(AnnotationMirror annotation) {
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("description")).map(e -> e.getValue())
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            return s;
          }
        }, null)).filter(not(String::isEmpty)).findFirst().orElse(null);
  }
}
