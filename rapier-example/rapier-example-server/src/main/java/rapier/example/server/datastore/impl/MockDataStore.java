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
package rapier.example.server.datastore.impl;

import java.util.List;
import rapier.example.server.DataStore;

/**
 * A toy implementation of a data store that lists user names.
 */
@SuppressWarnings("unused")
public class MockDataStore implements DataStore {
  private final String host;
  private final int port;
  private final String username;
  private final String password;

  public MockDataStore(String host, int port, String username, String password) {
    this.port = port;
    this.host = host;
    this.username = username;
    this.password = password;
  }

  @Override
  public List<String> listUserNames() {
    return List.of("John Doe", "Jane Doe");
  }
}
