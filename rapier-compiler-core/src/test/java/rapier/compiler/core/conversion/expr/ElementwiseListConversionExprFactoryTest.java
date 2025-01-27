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

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.junit.jupiter.api.Test;
import rapier.compiler.core.ConversionExprFactory;

public class ElementwiseListConversionExprFactoryTest {
  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void givenFullyReifiedListTargetType_whenComputeConversionExpr_thenGetExpectedConversionExpr() {
    final Types types = mock(Types.class);

    final TypeMirror listType = mock(TypeMirror.class);
    when(listType.getKind()).thenReturn(TypeKind.DECLARED);
    when(listType.toString()).thenReturn("java.util.List");

    final TypeMirror targetTypeAsTypeMirror =
        mock(TypeMirror.class, withSettings().extraInterfaces(DeclaredType.class));
    when(targetTypeAsTypeMirror.getKind()).thenReturn(TypeKind.DECLARED);
    when(targetTypeAsTypeMirror.toString()).thenReturn("java.util.List<java.lang.Integer>");
    when(types.erasure(targetTypeAsTypeMirror)).thenReturn(listType);

    final TypeMirror targetTypeTypeArgument = mock(TypeMirror.class);
    when(targetTypeTypeArgument.getKind()).thenReturn(TypeKind.DECLARED);
    when(targetTypeTypeArgument.toString()).thenReturn("java.lang.Integer");

    final List<TypeMirror> targetTypeTypeArguments = new ArrayList<>();
    targetTypeTypeArguments.add(targetTypeTypeArgument);

    final DeclaredType targetTypeAsDeclaredType = (DeclaredType) targetTypeAsTypeMirror;
    when(targetTypeAsDeclaredType.getTypeArguments()).thenReturn((List) targetTypeTypeArguments);

    final ConversionExprFactory innerConversionExprFactory = mock(ConversionExprFactory.class);
    when(innerConversionExprFactory.generateConversionExpr(targetTypeTypeArgument, "element"))
        .thenReturn(Optional.of("Integer.valueOf(element)"));

    final ElementwiseListConversionExprFactory unit =
        new ElementwiseListConversionExprFactory(types, innerConversionExprFactory);

    final String conversionExpr =
        unit.generateConversionExpr(targetTypeAsTypeMirror, "value").orElse(null);

    assertEquals(
        "value.stream().map(element -> { try { return Integer.valueOf(element); } catch(RuntimeException e) { throw e; } catch(Exception e) { throw new RuntimeException(e); } }).collect(java.util.stream.Collectors.toList())",
        conversionExpr);
  }

  @Test
  public void givenRawListTargetType_whenComputeConversionExpr_thenGetExpectedConversionExpr() {
    final Types types = mock(Types.class);

    final TypeMirror targetTypeAsTypeMirror =
        mock(TypeMirror.class, withSettings().extraInterfaces(DeclaredType.class));
    when(targetTypeAsTypeMirror.getKind()).thenReturn(TypeKind.DECLARED);
    when(targetTypeAsTypeMirror.toString()).thenReturn("java.util.List");
    when(types.erasure(targetTypeAsTypeMirror)).thenReturn(targetTypeAsTypeMirror);

    final DeclaredType targetTypeAsDeclaredType = (DeclaredType) targetTypeAsTypeMirror;
    when(targetTypeAsDeclaredType.getTypeArguments()).thenReturn(emptyList());

    final ConversionExprFactory innerConversionExprFactory = mock(ConversionExprFactory.class);

    final ElementwiseListConversionExprFactory unit =
        new ElementwiseListConversionExprFactory(types, innerConversionExprFactory);

    final String conversionExpr =
        unit.generateConversionExpr(targetTypeAsTypeMirror, "value").orElse(null);

    assertEquals(null, conversionExpr);

    verifyNoInteractions(innerConversionExprFactory);
  }

  @Test
  public void givenNonListTargetType_whenComputeConversionExpr_thenGetNoConversionExpr() {
    final Types types = mock(Types.class);

    final TypeMirror mapType = mock(TypeMirror.class);
    when(mapType.getKind()).thenReturn(TypeKind.DECLARED);
    when(mapType.toString()).thenReturn("java.util.Map");

    final TypeMirror targetTypeAsTypeMirror =
        mock(TypeMirror.class, withSettings().extraInterfaces(DeclaredType.class));
    when(targetTypeAsTypeMirror.getKind()).thenReturn(TypeKind.DECLARED);
    when(targetTypeAsTypeMirror.toString())
        .thenReturn("java.util.Map<java.lang.String,java.lang.Integer>");
    when(types.erasure(targetTypeAsTypeMirror)).thenReturn(mapType);

    final ConversionExprFactory innerConversionExprFactory = mock(ConversionExprFactory.class);

    final ElementwiseListConversionExprFactory unit =
        new ElementwiseListConversionExprFactory(types, innerConversionExprFactory);

    final String conversionExpr =
        unit.generateConversionExpr(targetTypeAsTypeMirror, "value").orElse(null);

    assertEquals(null, conversionExpr);

    verifyNoInteractions(innerConversionExprFactory);
  }
}
