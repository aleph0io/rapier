/*-
 * =================================LICENSE_START==================================
 * rapier-processor-cli
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

import java.util.Objects;
import java.util.Optional;

public class FlagParameterMetadata {
  private final boolean required;

  /**
   * For positional bindings, this indicates whether the binding is a varargs "bucket." For named
   * and flag bindings, this indicates whether the binding is a list of values.
   */
  private final boolean list;

  /**
   * The description of the parameter for use in the help message
   */
  private final String helpDescription;

  public FlagParameterMetadata(boolean required, boolean list, String helpDescription) {
    this.required = required;
    this.list = list;
    this.helpDescription = helpDescription;
  }

  public boolean isRequired() {
    return required;
  }

  public boolean isList() {
    return list;
  }

  public Optional<String> getHelpDescription() {
    return Optional.ofNullable(helpDescription);
  }

  @Override
  public int hashCode() {
    return Objects.hash(helpDescription, list, required);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    FlagParameterMetadata other = (FlagParameterMetadata) obj;
    return Objects.equals(helpDescription, other.helpDescription) && list == other.list
        && required == other.required;
  }

  @Override
  public String toString() {
    return "BindingMetadata [required=" + required + ", list=" + list + ", helpDescription="
        + helpDescription + "]";
  }
}
