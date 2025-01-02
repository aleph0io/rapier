/*-
 * =================================LICENSE_START==================================
 * rapier-core
 * ====================================SECTION=====================================
 * Copyright (C) 2024 Andy Boothe
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
package rapier.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class CaseFormatTest {
  /**
   * Tests the conversion between all case formats.
   */
  @Test
  public void test() {
    final Map<CaseFormat, String> tests = new EnumMap<>(CaseFormat.class);
    tests.put(CaseFormat.LOWER_CAMEL, "thisIsATest");
    tests.put(CaseFormat.LOWER_HYPHEN, "this-is-a-test");
    tests.put(CaseFormat.LOWER_UNDERSCORE, "this_is_a_test");
    tests.put(CaseFormat.UPPER_CAMEL, "ThisIsATest");
    tests.put(CaseFormat.UPPER_UNDERSCORE, "THIS_IS_A_TEST");

    for (CaseFormat from : CaseFormat.values()) {
      final String source = tests.get(from);
      for (CaseFormat to : CaseFormat.values()) {
        final String target = tests.get(to);
        assertEquals(from.name() + " " + to.name() + " " + target,
            from.name() + " " + to.name() + " " + from.to(to, source));
      }
    }
  }
}
