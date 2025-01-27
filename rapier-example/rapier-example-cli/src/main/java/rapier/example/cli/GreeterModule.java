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

import dagger.Module;
import dagger.Provides;
import rapier.cli.CliOptionParameter;
import rapier.cli.CliOptionParameterHelp;
import rapier.cli.CliPositionalParameter;

/**
 * A Dagger module that provides a {@link Greeter} object from CLI input. 
 */
@Module
public class GreeterModule {
  @Provides
  public Greeter getGreeter(
      @CliOptionParameterHelp(valueName = "name",
          description = "The name of the user to greet") @CliPositionalParameter(0) String name,
      @CliOptionParameterHelp(valueName = "greeting",
          description = "The greeting to use to greet the user, e.g., 'Hello'") @CliOptionParameter(
              shortName = 'g', longName = "greeting", defaultValue = "Hello") String greeting) {
    return new Greeter(greeting, name);
  }
}
