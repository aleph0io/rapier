/*-
 * =================================LICENSE_START==================================
 * rapier-cli-compiler
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

import static java.util.function.Predicate.not;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import rapier.cli.CliFlagParameterHelp;
import rapier.core.model.DaggerInjectionSite;

public class FlagParameterHelp {
  public static Optional<FlagParameterHelp> fromInjectionSite(DaggerInjectionSite dependency) {
    final AnnotationMirror annotation = dependency.getAnnotations().stream().filter(
        a -> a.getAnnotationType().toString().equals(CliFlagParameterHelp.class.getCanonicalName()))
        .findFirst().orElse(null);
    if (annotation == null)
      return Optional.empty();
    return fromAnnotationMirror(annotation);
  }

  public static Optional<FlagParameterHelp> fromAnnotationMirror(AnnotationMirror annotation) {
    if (annotation == null)
      throw new NullPointerException();

    if (!annotation.getAnnotationType().toString()
        .equals(CliFlagParameterHelp.class.getCanonicalName()))
      return Optional.empty();

    final String description = extractDescription(annotation);

    return Optional.of(new FlagParameterHelp(description));
  }

  private final String description;

  public FlagParameterHelp(String description) {
    this.description = description;
  }

  public Optional<String> getDescription() {
    return Optional.ofNullable(description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(description);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    FlagParameterHelp other = (FlagParameterHelp) obj;
    return Objects.equals(description, other.description);
  }

  @Override
  public String toString() {
    return "FlagParameterHelp [description=" + description + "]";
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
