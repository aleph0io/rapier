/*-
 * =================================LICENSE_START==================================
 * rapier-example-cli
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
package rapier.example.cli;

import dagger.Component;

/**
 * A Dagger component that provides a {@link Greeter} instance. Note that it includes both
 * {@link GreeterModule} and {@link RapierGreeterComponentCliModule} modules, where
 * {@link RapierGreeterComponentCliModule} is a generated module that provides the CLI parameters
 * for the {@link Greeter} instance.
 */
@Component(modules = {GreeterModule.class, RapierGreeterComponentCliModule.class})
public interface GreeterComponent {
  public Greeter greeter();
}
