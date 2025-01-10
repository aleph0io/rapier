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
package rapier.sysprop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation that indicates an injection site should be populated with the value of the given
 * {@link System#getProperties() system property}. The value of the system property is read at
 * runtime and injected into the annotated element. If the system property is not set, the default
 * value is used instead, if given. If no default value is given, then the value is {@code null}.
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
 */
@javax.inject.Qualifier
@jakarta.inject.Qualifier
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
public @interface SystemProperty {
  public static final String DEFAULT_VALUE_NOT_SET = "__UNDEFINED__";

  /**
   * The name of the environment variable to read.
   */
  public String value();

  /**
   * The default value to use if the environment variable is not set. If not set, the default value
   * is {@link #DEFAULT_VALUE_NOT_SET}.
   */
  public String defaultValue() default DEFAULT_VALUE_NOT_SET;
}
