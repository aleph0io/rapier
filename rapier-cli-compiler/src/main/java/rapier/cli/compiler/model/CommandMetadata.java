/*-
 * =================================LICENSE_START==================================
 * rapier-cli-compiler
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
package rapier.cli.compiler.model;

import static java.util.Objects.requireNonNull;
import java.util.Objects;
import java.util.Optional;

public class CommandMetadata {
  private final String name;

  private final String version;

  private final String description;

  private final boolean provideStandardHelp;

  private final boolean provideStandardVersion;

  public CommandMetadata(String name, String version, String description,
      Boolean provideStandardHelp, Boolean provideStandardVersion) {
    this.name = requireNonNull(name);
    this.version = requireNonNull(version);
    this.description = description;
    this.provideStandardHelp = provideStandardHelp;
    this.provideStandardVersion = provideStandardVersion;
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }

  public Optional<String> getDescription() {
    return Optional.ofNullable(description);
  }

  public boolean isProvideStandardHelp() {
    return provideStandardHelp;
  }

  public boolean isProvideStandardVersion() {
    return provideStandardVersion;
  }

  @Override
  public String toString() {
    return "CommandHelp [name=" + name + ", version=" + version + ", description=" + description
        + ", provideStandardHelp=" + provideStandardHelp + ", provideStandardVersion="
        + provideStandardVersion + "]";
  }

  @Override
  public int hashCode() {
    return Objects.hash(description, name, provideStandardHelp, provideStandardVersion, version);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    CommandMetadata other = (CommandMetadata) obj;
    return Objects.equals(description, other.description) && Objects.equals(name, other.name)
        && provideStandardHelp == other.provideStandardHelp
        && provideStandardVersion == other.provideStandardVersion
        && Objects.equals(version, other.version);
  }
}
