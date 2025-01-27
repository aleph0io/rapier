/*-
 * =================================LICENSE_START==================================
 * rapier-compiler-core
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
package rapier.compiler.core.conversion.expr;

import static java.util.Objects.requireNonNull;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import rapier.compiler.core.ConversionExprFactory;

public class ValueOfConversionExprFactory implements ConversionExprFactory {
  private final Types types;
  private final TypeMirror sourceType;

  public ValueOfConversionExprFactory(Types types, TypeMirror sourceType) {
    this.types = requireNonNull(types);
    this.sourceType = requireNonNull(sourceType);
  }

  @Override
  public Optional<String> generateConversionExpr(TypeMirror targetType, String sourceValue) {
    if (targetType.getKind() != TypeKind.DECLARED)
      return Optional.empty();
    final TypeElement targetElement = (TypeElement) getTypes().asElement(targetType);
    final DeclaredType targetDeclaredType = (DeclaredType) targetType;

    // Look for the "fromString" method
    for (Element enclosedElement : targetElement.getEnclosedElements()) {
      if (enclosedElement.getKind() != ElementKind.METHOD)
        continue;
      final ExecutableElement methodElement = (ExecutableElement) enclosedElement;
      if (methodElement.getSimpleName().contentEquals("valueOf")
          && methodElement.getModifiers().contains(Modifier.PUBLIC)
          && methodElement.getModifiers().contains(Modifier.STATIC)
          && methodElement.getParameters().size() == 1
          && methodElement.getReturnType().getKind() != TypeKind.VOID) {

        final ExecutableType methodType =
            (ExecutableType) getTypes().asMemberOf(targetDeclaredType, methodElement);

        if (!getTypes().isSameType(getSourceType(), methodType.getParameterTypes().get(0)))
          continue;
        if (!getTypes().isSameType(targetType, methodType.getReturnType()))
          continue;

        return Optional.of(targetType.toString() + ".valueOf(" + sourceValue + ")");
      }
    }

    return Optional.empty();
  }

  private Types getTypes() {
    return types;
  }

  private TypeMirror getSourceType() {
    return sourceType;
  }
}
