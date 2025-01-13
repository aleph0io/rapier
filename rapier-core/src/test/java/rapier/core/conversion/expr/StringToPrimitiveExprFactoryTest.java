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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.junit.jupiter.api.Test;

public class StringToPrimitiveExprFactoryTest {
  @Test
  public void givenByteTargetType_whenComputeConversionExpr_thenGetExpectedConversionExpr() {
    final Types types = mock(Types.class);

    final TypeMirror targetType = mock(TypeMirror.class);
    when(targetType.getKind()).thenReturn(TypeKind.BYTE);

    final StringToPrimitiveConversionExprFactory unit =
        new StringToPrimitiveConversionExprFactory(types);

    final String conversionExpr = unit.generateConversionExpr(targetType, "value").orElse(null);

    assertEquals("Byte.parseByte(value)", conversionExpr);
  }

  @Test
  public void givenShortTargetType_whenComputeConversionExpr_thenGetExpectedConversionExpr() {
    final Types types = mock(Types.class);

    final TypeMirror targetType = mock(TypeMirror.class);
    when(targetType.getKind()).thenReturn(TypeKind.SHORT);

    final StringToPrimitiveConversionExprFactory unit =
        new StringToPrimitiveConversionExprFactory(types);

    final String conversionExpr = unit.generateConversionExpr(targetType, "value").orElse(null);

    assertEquals("Short.parseShort(value)", conversionExpr);
  }

  @Test
  public void givenIntTargetType_whenComputeConversionExpr_thenGetExpectedConversionExpr() {
    final Types types = mock(Types.class);

    final TypeMirror targetType = mock(TypeMirror.class);
    when(targetType.getKind()).thenReturn(TypeKind.INT);

    final StringToPrimitiveConversionExprFactory unit =
        new StringToPrimitiveConversionExprFactory(types);

    final String conversionExpr = unit.generateConversionExpr(targetType, "value").orElse(null);

    assertEquals("Integer.parseInt(value)", conversionExpr);
  }

  @Test
  public void givenLongTargetType_whenComputeConversionExpr_thenGetExpectedConversionExpr() {
    final Types types = mock(Types.class);

    final TypeMirror targetType = mock(TypeMirror.class);
    when(targetType.getKind()).thenReturn(TypeKind.LONG);

    final StringToPrimitiveConversionExprFactory unit =
        new StringToPrimitiveConversionExprFactory(types);

    final String conversionExpr = unit.generateConversionExpr(targetType, "value").orElse(null);

    assertEquals("Long.parseLong(value)", conversionExpr);
  }

  @Test
  public void givenCharTargetType_whenComputeConversionExpr_thenGetExpectedConversionExpr() {
    final Types types = mock(Types.class);

    final TypeMirror targetType = mock(TypeMirror.class);
    when(targetType.getKind()).thenReturn(TypeKind.CHAR);

    final StringToPrimitiveConversionExprFactory unit =
        new StringToPrimitiveConversionExprFactory(types);

    final String conversionExpr = unit.generateConversionExpr(targetType, "value").orElse(null);

    assertEquals(
        "Optional.of(value).map(s -> s.isEmpty() ? null : s.charAt(0)).orElseThrow(() -> new IllegalStateException(\"Cannot convert empty string to char\"))",
        conversionExpr);
  }

  @Test
  public void givenFloatTargetType_whenComputeConversionExpr_thenGetExpectedConversionExpr() {
    final Types types = mock(Types.class);

    final TypeMirror targetType = mock(TypeMirror.class);
    when(targetType.getKind()).thenReturn(TypeKind.FLOAT);

    final StringToPrimitiveConversionExprFactory unit =
        new StringToPrimitiveConversionExprFactory(types);

    final String conversionExpr = unit.generateConversionExpr(targetType, "value").orElse(null);

    assertEquals("Float.parseFloat(value)", conversionExpr);
  }

  @Test
  public void givenDoubleTargetType_whenComputeConversionExpr_thenGetExpectedConversionExpr() {
    final Types types = mock(Types.class);

    final TypeMirror targetType = mock(TypeMirror.class);
    when(targetType.getKind()).thenReturn(TypeKind.DOUBLE);

    final StringToPrimitiveConversionExprFactory unit =
        new StringToPrimitiveConversionExprFactory(types);

    final String conversionExpr = unit.generateConversionExpr(targetType, "value").orElse(null);

    assertEquals("Double.parseDouble(value)", conversionExpr);
  }

  @Test
  public void givenBooleanTargetType_whenComputeConversionExpr_thenGetExpectedConversionExpr() {
    final Types types = mock(Types.class);

    final TypeMirror targetType = mock(TypeMirror.class);
    when(targetType.getKind()).thenReturn(TypeKind.BOOLEAN);

    final StringToPrimitiveConversionExprFactory unit =
        new StringToPrimitiveConversionExprFactory(types);

    final String conversionExpr = unit.generateConversionExpr(targetType, "value").orElse(null);

    assertEquals("Boolean.parseBoolean(value)", conversionExpr);
  }
  
  @Test
  public void givenNonPrimitiveTargetType_whenComputeConversionExpr_thenGetExpectedConversionExpr() {
    final Types types = mock(Types.class);

    final TypeMirror targetType = mock(TypeMirror.class);
    when(targetType.getKind()).thenReturn(TypeKind.DECLARED);

    final StringToPrimitiveConversionExprFactory unit =
        new StringToPrimitiveConversionExprFactory(types);

    final String conversionExpr = unit.generateConversionExpr(targetType, "value").orElse(null);

    assertEquals(null, conversionExpr);
  }  
}
