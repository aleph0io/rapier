/*-
 * =================================LICENSE_START==================================
 * rapier-cli-compiler
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
package rapier.cli.compiler.model;

import static java.util.Objects.requireNonNull;
import java.util.Objects;
import java.util.Optional;

public class OptionParameterMetadata {
  private final boolean required;

  /**
   * The name of the parameter for use in the help message
   */
  private final String helpValueName;

  /**
   * The description of the parameter for use in the help message
   */
  private final String helpDescription;

  public OptionParameterMetadata(boolean required, String helpValueName, String helpDescription) {
    this.required = required;
    this.helpValueName = requireNonNull(helpValueName);
    this.helpDescription = helpDescription;
  }

  public boolean isRequired() {
    return required;
  }

  public String getHelpValueName() {
    return helpValueName;
  }

  public Optional<String> getHelpDescription() {
    return Optional.ofNullable(helpDescription);
  }

  @Override
  public int hashCode() {
    return Objects.hash(helpDescription, helpValueName, required);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    OptionParameterMetadata other = (OptionParameterMetadata) obj;
    return Objects.equals(helpDescription, other.helpDescription)
        && Objects.equals(helpValueName, other.helpValueName) && required == other.required;
  }

  @Override
  public String toString() {
    return "OptionParameterMetadata [required=" + required + ", helpValueName=" + helpValueName
        + ", helpDescription=" + helpDescription + "]";
  }
}
