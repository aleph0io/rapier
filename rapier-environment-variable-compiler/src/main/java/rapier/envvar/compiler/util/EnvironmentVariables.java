/*-
 * =================================LICENSE_START==================================
 * rapier-environment-variable-compiler
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
package rapier.envvar.compiler.util;

public final class EnvironmentVariables {
  private EnvironmentVariables() {}

  /**
   * An injection site is logically required if it is not nullable and has no default value. if it
   * were nullable, then it would not be required because the application is prepared to handle a
   * {@code null} value for the parameter. If the parameter has a default value, then it is not
   * required because the application will never observe {@code null} for the parameter because its
   * default value will given instead.
   * 
   * @param nullable {@code true} if the parameter is nullable, {@code false} otherwise. Nullability
   *        is a Dagger-level concept.
   * @param defaultValue the default value for the parameter, or {@code null} if there is no default
   *        value. Default values are a Rapier-level concept.
   * @return {@code true} if the parameter is logically required at the injection site,
   *         {@code false} otherwise
   */
  public static boolean isRequired(boolean nullable, String defaultValue) {
    return !nullable && defaultValue == null;
  }
}
