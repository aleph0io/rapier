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
package rapier.cli;

public enum CliFlagParameterValue {
  /**
   * No value, equivalent to {@code null}
   */
  NONE {
    @Override
    public Boolean toBoolean() {
      return null;
    }
  },

  /**
   * The value {@code true}, equivalent to {@code Boolean.TRUE}
   */
  TRUE {
    @Override
    public Boolean toBoolean() {
      return Boolean.TRUE;
    }
  },

  /**
   * The value {@code false}, equivalent to {@code Boolean.FALSE}
   */
  FALSE {
    @Override
    public Boolean toBoolean() {
      return Boolean.FALSE;
    }
  };

  public abstract Boolean toBoolean();
}
