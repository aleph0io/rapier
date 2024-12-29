package com.sigpwned.rapier.core.model;

import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.TypeElement;

public class DaggerComponentAnalysis {
  private final TypeElement componentType;
  private final Set<Dependency> dependencies;

  public DaggerComponentAnalysis(TypeElement componentType, Set<Dependency> dependencies) {
    this.componentType = requireNonNull(componentType);
    this.dependencies = unmodifiableSet(dependencies);
  }

  public TypeElement getComponentType() {
    return componentType;
  }

  public Set<Dependency> getDependencies() {
    return dependencies;
  }

  @Override
  public int hashCode() {
    return Objects.hash(componentType, dependencies);
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
        && Objects.equals(dependencies, other.dependencies);
  }

  @Override
  public String toString() {
    return "DaggerComponentAnalysis [componentType=" + componentType + ", dependencies="
        + dependencies + "]";
  }
}
