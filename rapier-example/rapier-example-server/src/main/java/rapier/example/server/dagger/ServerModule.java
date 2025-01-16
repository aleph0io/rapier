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

import dagger.Module;
import dagger.Provides;
import rapier.envvar.EnvironmentVariable;
import rapier.example.server.DataStore;
import rapier.example.server.Server;

@Module(includes = {DataStoreModule.class})
public class ServerModule {
  /**
   * Provides the server instance.
   * 
   * @param port the port to listen on. Note the default value, which means that the environment
   *        variable is optional.
   * @param dataStore the data store to use, provided by the {@link DataStoreModule}
   * @return the server instance
   */
  @Provides
  public Server getServer(
      @EnvironmentVariable(value = "SERVER_PORT", defaultValue = "7070") int port,
      DataStore dataStore) {
    return new Server(port, dataStore);
  }
}
