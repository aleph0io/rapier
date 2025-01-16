/*-
 * =================================LICENSE_START==================================
 * rapier-processor-environment-variable
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
package rapier.sysprop.compiler.model;

import java.util.Objects;

public class ParameterMetadata {
  private final boolean required;

  public ParameterMetadata(boolean required) {
    this.required = required;
  }

  public boolean isRequired() {
    return required;
  }

  @Override
  public int hashCode() {
    return Objects.hash(required);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ParameterMetadata other = (ParameterMetadata) obj;
    return required == other.required;
  }

  @Override
  public String toString() {
    return "ParameterMetadata [required=" + required + "]";
  }
}
