/*-
 * =================================LICENSE_START==================================
 * rapier-example-server
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
package rapier.example.server.dagger;

import dagger.Component;
import rapier.example.server.Server;

/**
 * The Dagger component for the server. This component is responsible for providing the server
 * instance.
 * 
 * <p>
 * Note that we use the {@link RapierServerComponentEnvironmentVariableModule} and
 * {@link RapierServerComponentSystemPropertyModule}. These modules are generated by the Rapier
 * processor, which scans this component for all required environment variables and system
 * properties, and generates these modules to provide the values for those environment variables and
 * system properties.
 */
@Component(modules = {RapierServerComponentEnvironmentVariableModule.class,
    RapierServerComponentSystemPropertyModule.class, ServerModule.class})
public interface ServerComponent {
  public Server server();
}
