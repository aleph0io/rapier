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

import java.util.Map;
import java.util.Objects;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import rapier.core.model.DaggerInjectionSite;
import rapier.processor.cli.PositionalCliParameter;

public class PositionalParameterKey {
  public static PositionalParameterKey fromInjectionSite(DaggerInjectionSite dependency) {
    final AnnotationMirror qualifier = dependency.getQualifier().orElseThrow(() -> {
      return new IllegalArgumentException("Dependency must have qualifier");
    });

    if (!qualifier.getAnnotationType().toString()
        .equals(PositionalCliParameter.class.getCanonicalName())) {
      throw new IllegalArgumentException("Dependency qualifier must be @PositionalCliParameter");
    }

    final int position = extractPositionalParameterPosition(qualifier);

    return new PositionalParameterKey(position);
  }

  public static PositionalParameterKey forPosition(int position) {
    return new PositionalParameterKey(position);
  }

  private final int position;

  public PositionalParameterKey(int position) {
    this.position = position;
    if (position < 0)
      throw new IllegalArgumentException("Position must be non-negative");
  }

  public int getPosition() {
    return position;
  }

  @Override
  public int hashCode() {
    return Objects.hash(position);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    PositionalParameterKey other = (PositionalParameterKey) obj;
    return position == other.position;
  }

  @Override
  public String toString() {
    return "PositionalParameterKey [position=" + position + "]";
  }

  private static int extractPositionalParameterPosition(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(PositionalCliParameter.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("value")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<Integer, Void>() {
          @Override
          public Integer visitInt(int i, Void p) {
            return i;
          }
        }, null)).orElseThrow(() -> {
          return new AssertionError("No position value for @PositionalCliParameter");
        });
  }
}
