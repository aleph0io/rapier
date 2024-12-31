/*-
 * =================================LICENSE_START==================================
 * rapier-processor-core
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
package rapier.processor.core.util;

/**
 * Utility methods for working with Java code.
 */
public final class Java {
  private Java() {}

  /**
   * Escapes a string for use in a Java string literal.
   * 
   * @param s the string to escape
   * @return the escaped string
   * 
   * @throws NullPointerException if {@code s} is {@code null}
   */
  public static String escapeString(String s) {
    if (s == null)
      throw new NullPointerException();
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
