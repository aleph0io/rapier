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
import rapier.cli.CliHelp;
import rapier.core.model.DaggerInjectionSite;

public class CliHelpMetadata {
  public static Optional<CliHelpMetadata> fromInjectionSite(DaggerInjectionSite dependency) {
    final AnnotationMirror annotation = dependency.getAnnotations().stream()
        .filter(a -> a.getAnnotationType().toString().equals(CliHelp.class.getCanonicalName()))
        .findFirst().orElse(null);
    if (annotation == null)
      return Optional.empty();
    return fromAnnotationMirror(annotation);
  }

  public static Optional<CliHelpMetadata> fromAnnotationMirror(AnnotationMirror annotation) {
    if (annotation == null)
      throw new NullPointerException();

    if (!annotation.getAnnotationType().toString().equals(CliHelp.class.getCanonicalName()))
      return Optional.empty();

    final String name = extractCliHelpName(annotation);
    final String description = extractCliHelpDescription(annotation);

    return Optional.of(new CliHelpMetadata(name, description));
  }

  private final String name;

  private final String description;

  public CliHelpMetadata(String name, String description) {
    this.name = name;
    this.description = description;
  }

  public Optional<String> getName() {
    return Optional.ofNullable(name);
  }

  public Optional<String> getDescription() {
    return Optional.ofNullable(description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(description, name);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    CliHelpMetadata other = (CliHelpMetadata) obj;
    return Objects.equals(description, other.description) && Objects.equals(name, other.name);
  }

  @Override
  public String toString() {
    return "CliHelpMetadata [name=" + name + ", description=" + description + "]";
  }

  private static String extractCliHelpName(AnnotationMirror annotation) {
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("name")).map(e -> e.getValue())
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            return s;
          }
        }, null)).filter(not(String::isEmpty)).findFirst().orElse(null);
  }

  private static String extractCliHelpDescription(AnnotationMirror annotation) {
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
