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
package rapier.core.conversion.expr;

import static java.util.Objects.requireNonNull;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import rapier.core.ConversionExprFactory;

public class StringToPrimitiveConversionExprFactory implements ConversionExprFactory {
  private final Types types;

  public StringToPrimitiveConversionExprFactory(Types types) {
    this.types = requireNonNull(types);
  }

  @Override
  public Optional<String> generateConversionExpr(TypeMirror targetType, String sourceValue) {
    switch (targetType.getKind()) {
      case BOOLEAN:
        return Optional.of("Boolean.parseBoolean(" + sourceValue + ")");
      case BYTE:
        return Optional.of("Byte.parseByte(" + sourceValue + ")");
      case SHORT:
        return Optional.of("Short.parseShort(" + sourceValue + ")");
      case INT:
        return Optional.of("Integer.parseInt(" + sourceValue + ")");
      case LONG:
        return Optional.of("Long.parseLong(" + sourceValue + ")");
      case CHAR:
        return Optional.of(sourceValue + ".charAt(0)");
      case FLOAT:
        return Optional.of("Float.parseFloat(" + sourceValue + ")");
      case DOUBLE:
        return Optional.of("Double.parseDouble(" + sourceValue + ")");
      default:
        return Optional.empty();
    }
  }

  @SuppressWarnings("unused")
  private Types getTypes() {
    return types;
  }
}
