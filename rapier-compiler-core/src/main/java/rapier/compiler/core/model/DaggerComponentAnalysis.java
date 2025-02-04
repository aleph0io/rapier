/*-
 * =================================LICENSE_START==================================
 * rapier-compiler-core
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
package rapier.compiler.core.model;

import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.TypeElement;

public class DaggerComponentAnalysis {
  private final TypeElement componentType;
  private final Set<DaggerInjectionSite> injectionSites;

  public DaggerComponentAnalysis(TypeElement componentType, Set<DaggerInjectionSite> dependencies) {
    this.componentType = requireNonNull(componentType);
    this.injectionSites = unmodifiableSet(dependencies);
  }

  public TypeElement getComponentType() {
    return componentType;
  }

  public Set<DaggerInjectionSite> getInjectionSites() {
    return injectionSites;
  }

  @Override
  public int hashCode() {
    return Objects.hash(componentType, injectionSites);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    DaggerComponentAnalysis other = (DaggerComponentAnalysis) obj;
    return Objects.equals(componentType, other.componentType)
        && Objects.equals(injectionSites, other.injectionSites);
  }

  @Override
  public String toString() {
    return "DaggerComponentAnalysis [componentType=" + componentType + ", dependencies="
        + injectionSites + "]";
  }
}
