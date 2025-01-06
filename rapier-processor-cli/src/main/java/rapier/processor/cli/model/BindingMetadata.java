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
package rapier.processor.cli.model;

import java.util.Objects;

public class BindingMetadata {
  private final boolean required;

  /**
   * For positional bindings, this indicates whether the binding is a varargs "bucket." For named
   * and flag bindings, this indicates whether the binding is a list of values.
   */
  private final boolean list;

  public BindingMetadata(boolean nullable, boolean list) {
    this.required = nullable;
    this.list = list;
  }

  public boolean isRequired() {
    return required;
  }

  public boolean isList() {
    return list;
  }

  @Override
  public int hashCode() {
    return Objects.hash(list, required);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    BindingMetadata other = (BindingMetadata) obj;
    return list == other.list && required == other.required;
  }

  @Override
  public String toString() {
    return "NamedBindingMetadata [required=" + required + ", list=" + list + "]";
  }
}
