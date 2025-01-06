/*-
 * =================================LICENSE_START==================================
 * rapier-core
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
package rapier.processor.cli.conversion.expr;

import static java.util.Objects.requireNonNull;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import rapier.core.ConversionExprFactory;
import rapier.core.util.AnnotationProcessing;

public class ValueOfConversionExprFactory implements ConversionExprFactory {
  private final Types types;

  public ValueOfConversionExprFactory(Types types) {
    this.types = requireNonNull(types);
  }

  @Override
  public Optional<String> generateConversionExpr(TypeMirror targetType, TypeMirror sourceType,
      String sourceValue) {
    // Get the TypeElement representing the declared type
    final TypeElement targetElement = (TypeElement) getTypes().asElement(targetType);

    // Iterate through the enclosed elements to find the "valueOf" method
    final Optional<ExecutableElement> maybeValueOfMethod =
        AnnotationProcessing.findValueOfMethod(getTypes(), targetElement, sourceType);
    if (maybeValueOfMethod.isEmpty())
      return Optional.empty();

    return Optional.of(targetType.toString() + ".valueOf(" + sourceValue + ")");
  }

  private Types getTypes() {
    return types;
  }
}
