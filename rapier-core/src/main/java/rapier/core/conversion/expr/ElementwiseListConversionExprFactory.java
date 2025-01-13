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
import java.util.List;
import java.util.Optional;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import rapier.core.ConversionExprFactory;

public class ElementwiseListConversionExprFactory implements ConversionExprFactory {
  private final Types types;
  private final ConversionExprFactory elementConversionExprFactory;

  public ElementwiseListConversionExprFactory(Types types,
      ConversionExprFactory elementConversionExprFactory) {
    this.types = requireNonNull(types);
    this.elementConversionExprFactory = requireNonNull(elementConversionExprFactory);
  }

  @Override
  public Optional<String> generateConversionExpr(TypeMirror targetType, String sourceValue) {
    if (targetType.getKind() != TypeKind.DECLARED)
      return Optional.empty();
    final DeclaredType targetDeclaredType = (DeclaredType) targetType;

    final TypeMirror targetErasedType = getTypes().erasure(targetType);
    if (!targetErasedType.toString().equals("java.util.List"))
      return Optional.empty();
    
    // Get the type arguments of the target type. If there are no type arguments, then this is a
    // raw List type, and we can't see the element type, so just return.
    List<? extends TypeMirror> typeArguments = targetDeclaredType.getTypeArguments();
    if (typeArguments.size() == 0)
      return Optional.empty();

    // Get the type argument. If it's not an exact declared type, then we're done.
    final TypeMirror targeElementType = typeArguments.get(0);
    if (targeElementType.getKind() != TypeKind.DECLARED)
      return Optional.empty();

    // Generate conversion expression for each element
    final Optional<String> maybeElementConversionExpr =
        getElementConversionExprFactory().generateConversionExpr(targeElementType, "element");
    if (maybeElementConversionExpr.isEmpty())
      return Optional.empty();
    final String elementConversionExpr = maybeElementConversionExpr.orElseThrow();

    return Optional.of(sourceValue + ".stream().map(element -> " + elementConversionExpr
        + ").collect(java.util.stream.Collectors.toList())");
  }

  private Types getTypes() {
    return types;
  }

  private ConversionExprFactory getElementConversionExprFactory() {
    return elementConversionExprFactory;
  }
}
