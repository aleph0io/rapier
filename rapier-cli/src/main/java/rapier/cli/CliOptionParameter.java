/*-
 * =================================LICENSE_START==================================
 * rapier-cli
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
package rapier.cli;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates an injection site should be populated with the value of the given option parameter in
 * the program arguments. If a default value is given, then the option parameter is optional, and
 * the given default value is used if it is not provided. If the default value is not set, and the
 * injection site is {@code Nullable}, then the option parameter is optional, and {@code null} is
 * used if it is not provided. Otherwise, the option parameter is required.
 * 
 * <p>
 * The annotated element must be one of the following types:
 * 
 * <ul>
 * <li>{@link String}</li>
 * <li>Any primitive type</li>
 * <li>Any boxed primitive type</li>
 * <li>Any class or interface {@code T} with a method {@code public static T valueOf(String)}</li>
 * <li>Any class or interface {@code T} with a method
 * {@code public static T fromString(String)}</li>
 * <li>Any class or interface {@code T} with a constructor {@code public T(String)}</li>
 * </ul>
 * 
 * <p>
 * Any or all of {@link #shortName()} and {@link #longName()} may be set, but at least one must be
 * set.
 * 
 */
@javax.inject.Qualifier
@jakarta.inject.Qualifier
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
public @interface CliOptionParameter {
  public static final String DEFAULT_VALUE_NOT_SET = "__UNDEFINED__";

  /**
   * The short name (e.g., {@code -x}, {@code -y}) of the option parameter. If not set, then the
   * {@link #longName() long name} must be set, and the option parameter is only accessible by the
   * long name. The allowed characters are {@code [a-zA-Z0-9]}.
   */
  public char shortName() default '\0';

  /**
   * The long name (e.g., {@code --foo}, {@code --bar}) of the option parameter. If not set, then
   * the {@link #shortName() short name} must be set, and the option parameter is only accessible by
   * the short name. The allowed characters are {@code [-a-zA-Z0-9_]}.
   */
  public String longName() default "";

  /**
   * The optional default value to use if the option parameter is not provided.
   */
  public String defaultValue() default DEFAULT_VALUE_NOT_SET;
}
