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
package rapier.compiler.core.model;

import java.util.Optional;
import javax.inject.Provider;
import dagger.Lazy;

public enum DaggerProvisionStyle {
  /**
   * The provisioned type is a primitive type (e.g., {@code int}, {@code long}, etc.). The binding
   * type will be the corresponding boxed type. The qualifier must appear on element. The binding is
   * implicitly not nullable. It is an error to mark a primitive as nullable.
   */
  PRIMITIVE,

  /**
   * The provisioned type is an {@link Optional Optional&lt;T&gt;}. The binding type will be the
   * type parameter. The qualifier must appear on the element, or on the type parameter. The binding
   * is implicitly nullable. It is redundant, but not an error, to mark an optional as nullable.
   */
  OPTIONAL,

  /**
   * The provisioned type is a {@link Provider Provider&lt;T&gt;}. The binding type will be the type
   * parameter. The qualifier must appear on the element, or on the type parameter. The binding is
   * considered nullable if either the element is annotated {@code @Nullable} or the type parameter
   * is annotated {@code @Nullable}.
   * 
   */
  PROVIDER,

  /**
   * The provisioned type is a {@link Lazy Lazy&lt;T&gt;}. The binding type will be the type
   * parameter. The qualifier must appear on the element, or on the type parameter. The binding is
   * considered nullable if either the element is annotated {@code @Nullable} or the type parameter
   * is annotated {@code @Nullable}.
   */
  LAZY,

  /**
   * The provisioned type is none of the above. The binding type will be the same as the provisioned
   * type. The qualifier must appear on the element. The binding is considered nullable if the
   * element is annotated {@code @Nullable}.
   */
  VERBATIM;
}
