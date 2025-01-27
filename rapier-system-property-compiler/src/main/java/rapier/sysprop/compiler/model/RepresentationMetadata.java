/*-
 * =================================LICENSE_START==================================
 * rapier-system-property-compiler
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
package rapier.sysprop.compiler.model;

import java.util.Objects;

public class RepresentationMetadata {
  private final boolean nullable;

  public RepresentationMetadata(boolean nullable) {
    this.nullable = nullable;
  }

  public boolean isNullable() {
    return nullable;
  }

  @Override
  public int hashCode() {
    return Objects.hash(nullable);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    RepresentationMetadata other = (RepresentationMetadata) obj;
    return nullable == other.nullable;
  }

  @Override
  public String toString() {
    return "ParameterMetadata [nullable=" + nullable + "]";
  }
}
