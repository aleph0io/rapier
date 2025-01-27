/*-
 * =================================LICENSE_START==================================
 * rapier-example-server
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
package rapier.example.server.dagger;

import dagger.Module;
import dagger.Provides;
import rapier.envvar.EnvironmentVariable;
import rapier.example.server.DataStore;
import rapier.example.server.datastore.impl.MockDataStore;
import rapier.sysprop.SystemProperty;

@Module
public class DataStoreModule {
  /**
   * Provides a DataStore implementation.
   * 
   * @param host the database host. Since this is an example, we only use a mock database, so this
   *        value is purely for example. Note the default value, so the environment variable is not
   *        required.
   * @param port the database port. Since this is an example, we only use a mock database, so this
   *        value is purely for example. Note the default value, so the environment variable is not
   *        required.
   * @param username the database username. Since this is an exmaple, we only use a mock database,
   *        so this value is purely for example. Note that this value is required, so the
   *        environment variable must be set, or the generated Rapier module will throw an exception
   *        on creation.
   * @param password the database password. Since this is an example, we only use a mock database,
   *        so this value is purely for example. Note that this value is required, so the system
   *        property must be set, or the generated Rapier module will throw an exception on
   *        creation. Being a secret, users should probably prefer another method of setting this
   *        value, such as Rapier's AWS SSM integration.
   * 
   * @return a DataStore implementation
   */
  @Provides
  public DataStore getDataStore(
      @EnvironmentVariable(value = "DATABASE_HOST", defaultValue = "localhost") String host,
      @EnvironmentVariable(value = "DATABASE_PORT", defaultValue = "5432") int port,
      @EnvironmentVariable(value = "DATABASE_USER") String username,
      @SystemProperty(value = "database.password") String password) {
    return new MockDataStore(host, port, username, password);
  }
}
