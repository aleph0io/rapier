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

public class SingleArgumentConstructorConversionExprFactory implements ConversionExprFactory {
  private final Types types;
  private final TypeMirror sourceType;

  public SingleArgumentConstructorConversionExprFactory(Types types, TypeMirror sourceType) {
    this.types = requireNonNull(types);
    this.sourceType = requireNonNull(sourceType);
  }

  @Override
  public Optional<String> generateConversionExpr(TypeMirror targetType, String sourceValue) {
    if (targetType.getKind() != TypeKind.DECLARED)
      return Optional.empty();
    final TypeElement targetElement = (TypeElement) getTypes().asElement(targetType);
    final DeclaredType targetDeclaredType = (DeclaredType) targetType;

    // Look for the single-argument constructor
    for (Element enclosedElement : targetElement.getEnclosedElements()) {
      if (enclosedElement.getKind() != ElementKind.CONSTRUCTOR)
        continue;
      final ExecutableElement constructorElement = (ExecutableElement) enclosedElement;
      if (constructorElement.getModifiers().contains(Modifier.PUBLIC)
          && constructorElement.getParameters().size() == 1) {

        final ExecutableType constructorType =
            (ExecutableType) getTypes().asMemberOf(targetDeclaredType, constructorElement);

        if (!getTypes().isSameType(getSourceType(), constructorType.getParameterTypes().get(0)))
          continue;

        return Optional.of("new " + targetType.toString() + "(" + sourceValue + ")");
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
