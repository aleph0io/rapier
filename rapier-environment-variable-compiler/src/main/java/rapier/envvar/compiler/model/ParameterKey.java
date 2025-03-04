/*-
 * =================================LICENSE_START==================================
 * rapier-environment-variable-compiler
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
package rapier.envvar.compiler.model;

import static java.util.Objects.requireNonNull;
import java.util.Map;
import java.util.Objects;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import rapier.compiler.core.model.DaggerInjectionSite;
import rapier.envvar.EnvironmentVariable;

/**
 * A grouping key for the physical parameter provided by the user
 */
public class ParameterKey {
  public static ParameterKey fromRepresentationKey(RepresentationKey rk) {
    final String name = rk.getName();
    return new ParameterKey(name);
  }

  public static ParameterKey fromInjectionSite(DaggerInjectionSite injectionSite) {
    final AnnotationMirror qualifier = injectionSite.getQualifier().orElseThrow(() -> {
      return new IllegalArgumentException("Dependency must have qualifier");
    });

    if (!qualifier.getAnnotationType().toString()
        .equals(EnvironmentVariable.class.getCanonicalName())) {
      throw new IllegalArgumentException("Dependency qualifier must be @EnvironmentVariable");
    }

    final String name = extractName(qualifier);

    return new ParameterKey(name);
  }

  private final String name;

  public ParameterKey(String name) {
    this.name = requireNonNull(name);
  }

  public String getName() {
    return name;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ParameterKey other = (ParameterKey) obj;
    return Objects.equals(name, other.name);
  }

  @Override
  public String toString() {
    return "ParameterKey [name=" + name + "]";
  }

  private static String extractName(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(EnvironmentVariable.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("value")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            return s;
          }
        }, null)).orElseThrow(() -> {
          return new AssertionError("No string value for @EnvironmentVariable");
        });
  }
}
