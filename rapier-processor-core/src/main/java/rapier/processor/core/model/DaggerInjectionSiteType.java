/*-
 * =================================LICENSE_START==================================
 * rapier-processor-core
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
package rapier.processor.core.model;

public enum DaggerInjectionSiteType {
  // TODO component binding method? scan for inject?

  /**
   * The return value of a "provision method" in a {@link dagger.Component component}. The element
   * will be the method.
   */
  COMPONENT_PROVISION_METHOD_RESULT,

  /**
   * A parameter of a static method in a {@link dagger.Module module}. The element will be the
   * method parameter.
   */
  MODULE_STATIC_PROVIDES_METHOD_PARAMETER,

  /**
   * A parameter of an instance method in a {@link dagger.Module module}. The element will be the
   * method parameter.
   */
  MODULE_INSTANCE_PROVIDES_METHOD_PARAMETER,

  /**
   * A parameter in an {@code @Inject}-annotated constructor. The element will be the constructor
   * parameter.
   */
  INJECT_CONSTRUCTOR_PARAMETER,

  /**
   * A parameter in an {@code @Inject}-annotated instance method. The element will be the method.
   */
  INJECT_INSTANCE_METHOD,

  /**
   * An {@code Inject}-annotated instance field. The element will be the field.
   */
  INJECT_INSTANCE_FIELD;
}
