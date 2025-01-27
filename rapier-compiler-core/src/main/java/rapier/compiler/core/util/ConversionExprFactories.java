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
package rapier.compiler.core.util;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import rapier.compiler.core.ConversionExprFactory;
import rapier.compiler.core.conversion.expr.ConversionExprFactoryChain;
import rapier.compiler.core.conversion.expr.ElementwiseListConversionExprFactory;
import rapier.compiler.core.conversion.expr.FromStringConversionExprFactory;
import rapier.compiler.core.conversion.expr.IdentityConversionExprFactory;
import rapier.compiler.core.conversion.expr.SingleArgumentConstructorConversionExprFactory;
import rapier.compiler.core.conversion.expr.StringToCharacterConversionExprFactory;
import rapier.compiler.core.conversion.expr.StringToPrimitiveConversionExprFactory;
import rapier.compiler.core.conversion.expr.ValueOfConversionExprFactory;

public final class ConversionExprFactories {
  private ConversionExprFactories() {}

  public static ConversionExprFactory standardAmbiguousFromStringFactory(ProcessingEnvironment pe) {
    final Elements elements = pe.getElementUtils();
    final Types types = pe.getTypeUtils();
    final TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    return new ConversionExprFactoryChain(new IdentityConversionExprFactory(types, stringType),
        new StringToPrimitiveConversionExprFactory(types),
        new StringToCharacterConversionExprFactory(types),
        new ValueOfConversionExprFactory(types, stringType),
        new FromStringConversionExprFactory(types),
        new SingleArgumentConstructorConversionExprFactory(types, stringType));
  }

  public static ConversionExprFactory standardAmbiguousFromListOfStringFactory(
      ProcessingEnvironment pe) {
    final Elements elements = pe.getElementUtils();
    final Types types = pe.getTypeUtils();
    final TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    final TypeMirror listOfStringType =
        types.getDeclaredType(elements.getTypeElement("java.util.List"), stringType);
    final ConversionExprFactory standardAmbiguousFromStringFactory =
        standardAmbiguousFromStringFactory(pe);
    return new ConversionExprFactoryChain(
        new IdentityConversionExprFactory(types, listOfStringType),
        new ValueOfConversionExprFactory(types, listOfStringType),
        new SingleArgumentConstructorConversionExprFactory(types, listOfStringType),
        new ElementwiseListConversionExprFactory(types, standardAmbiguousFromStringFactory));
  }

}
