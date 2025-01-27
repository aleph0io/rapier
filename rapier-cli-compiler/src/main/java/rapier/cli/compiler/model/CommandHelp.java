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
import rapier.cli.CliCommandHelp;

public class CommandHelp {
  public static Optional<CommandHelp> fromAnnotationMirror(AnnotationMirror annotation) {
    if (annotation == null)
      throw new NullPointerException();

    if (!annotation.getAnnotationType().toString().equals(CliCommandHelp.class.getCanonicalName()))
      return Optional.empty();

    final String name = extractName(annotation);
    final String version = extractVersion(annotation);
    final String description = extractDescription(annotation);
    final boolean provideStandardHelp = extractProvideStandardHelp(annotation);
    final boolean provideStandardVersion = extractProvideStandardVersion(annotation);
    final boolean testing = extractTesting(annotation);

    return Optional.of(new CommandHelp(name, version, description, provideStandardHelp,
        provideStandardVersion, testing));
  }

  private final String name;

  private final String version;

  private final String description;

  private final boolean provideStandardHelp;

  private final boolean provideStandardVersion;

  private final boolean testing;

  public CommandHelp(String name, String version, String description, boolean provideStandardHelp,
      boolean provideStandardVersion, boolean testing) {
    this.name = requireNonNull(name);
    this.version = requireNonNull(version);
    this.description = description;
    this.provideStandardHelp = provideStandardHelp;
    this.provideStandardVersion = provideStandardVersion;
    this.testing = testing;
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }

  public Optional<String> getDescription() {
    return Optional.ofNullable(description);
  }

  public boolean isProvideStandardHelp() {
    return provideStandardHelp;
  }

  public boolean isProvideStandardVersion() {
    return provideStandardVersion;
  }

  public boolean isTesting() {
    return testing;
  }

  @Override
  public String toString() {
    return "CommandHelp [name=" + name + ", version=" + version + ", description=" + description
        + ", provideStandardHelp=" + provideStandardHelp + ", provideStandardVersion="
        + provideStandardVersion + ", testing=" + testing + "]";
  }

  @Override
  public int hashCode() {
    return Objects.hash(description, name, provideStandardHelp, provideStandardVersion, testing,
        version);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    CommandHelp other = (CommandHelp) obj;
    return Objects.equals(description, other.description) && Objects.equals(name, other.name)
        && provideStandardHelp == other.provideStandardHelp
        && provideStandardVersion == other.provideStandardVersion && testing == other.testing
        && Objects.equals(version, other.version);
  }

  private static String extractName(AnnotationMirror annotation) {
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("name")).map(e -> e.getValue())
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            return s;
          }
        }, null)).findFirst().orElseThrow(() -> {
          // This should never happen, as the annotation processor should enforce the presence of
          // the required 'name' attribute.
          return new AssertionError("@CliCommandHelp(name) is required");
        });
  }

  private static String extractVersion(AnnotationMirror annotation) {
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("version")).map(e -> e.getValue())
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            return s;
          }
        }, null)).findFirst().orElse("0.0.0");
  }

  private static boolean extractProvideStandardHelp(AnnotationMirror annotation) {
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("provideStandardHelp"))
        .map(e -> e.getValue())
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<Boolean, Void>() {

          @Override
          public Boolean visitBoolean(boolean b, Void p) {
            return b;
          }
        }, null)).findFirst().orElse(true);
  }

  private static boolean extractProvideStandardVersion(AnnotationMirror annotation) {
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("provideStandardVersion"))
        .map(e -> e.getValue())
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<Boolean, Void>() {
          @Override
          public Boolean visitBoolean(boolean b, Void p) {
            return b;
          }
        }, null)).findFirst().orElse(true);
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

  private static boolean extractTesting(AnnotationMirror annotation) {
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("testing")).map(e -> e.getValue())
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<Boolean, Void>() {
          @Override
          public Boolean visitBoolean(boolean b, Void p) {
            return b;
          }
        }, null)).findFirst().orElse(false);
  }
}
