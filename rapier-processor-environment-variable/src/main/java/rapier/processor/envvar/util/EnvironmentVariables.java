/*-
 * =================================LICENSE_START==================================
 * rapier-processor-environment-variable
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
package rapier.processor.envvar.util;

import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import rapier.processor.envvar.EnvironmentVariable;

public final class EnvironmentVariables {
  private EnvironmentVariables() {}

  public static String extractEnvironmentVariableName(AnnotationMirror annotation) {
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

  public static String extractEnvironmentVariableDefaultValue(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(EnvironmentVariable.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("defaultValue")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            if (s.equals(EnvironmentVariable.DEFAULT_VALUE_NOT_SET))
              return null;
            return s;
          }
        }, null)).orElse(null);
  }
}
