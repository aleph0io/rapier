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
package rapier.compiler.core.conversion.expr;

import static java.util.Objects.requireNonNull;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import rapier.compiler.core.ConversionExprFactory;

public class StringToCharacterConversionExprFactory implements ConversionExprFactory {
  private final Types types;

  public StringToCharacterConversionExprFactory(Types types) {
    this.types = requireNonNull(types);
  }

  @Override
  public Optional<String> generateConversionExpr(TypeMirror targetType, String sourceValue) {
    if (!targetType.toString().equals("java.lang.Character"))
      return Optional.empty();

    return Optional.of("Optional.of(" + sourceValue
        + ").map(s -> s.isEmpty() ? null : s.charAt(0)).orElseThrow(() -> new IllegalStateException(\"Cannot convert empty string to char\"))");
  }

  @SuppressWarnings("unused")
  private Types getTypes() {
    return types;
  }
}
