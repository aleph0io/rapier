/*-
 * =================================LICENSE_START==================================
 * rapier-cli
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
package rapier.cli;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface CliCommandHelp {
  public static final String DEFAULT_VERSION = "0.0.0";

  /**
   * The name used in the help message.
   */
  public String name();

  /**
   * The version used in the help message.
   */
  public String version() default DEFAULT_VERSION;

  /**
   * Print a standard help message and exit with a nonzero exit code on syntax errors or in response
   * to the flags {@code --help} or {@code -h}.
   */
  public boolean provideStandardHelp() default true;

  /**
   * Print a standard version message and exit with a nonzero exit code in response to the flags
   * {@code --version} or {@code -v}.
   */
  public boolean provideStandardVersion() default true;

  /**
   * The description used in the help message.
   */
  public String description() default "";

  /**
   * Indicates that testing code should be emitted, not production code. For internal use only.
   */
  public boolean testing() default false;
}
