package com.sigpwned.rapier.core.util;

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
