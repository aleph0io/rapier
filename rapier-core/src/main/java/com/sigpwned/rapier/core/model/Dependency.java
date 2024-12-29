package com.sigpwned.rapier.core.model;

import static java.util.Objects.requireNonNull;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;

public class Dependency {
  private final TypeMirror type;
  private final AnnotationMirror qualifier;

  public Dependency(TypeMirror type, AnnotationMirror qualifier) {
    this.type = requireNonNull(type);
    this.qualifier = qualifier;
  }

  public TypeMirror getType() {
    return type;
  }

  public Optional<AnnotationMirror> getQualifier() {
    return Optional.ofNullable(qualifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(qualifier, type);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Dependency other = (Dependency) obj;
    return Objects.equals(qualifier, other.qualifier) && Objects.equals(type, other.type);
  }

  @Override
  public String toString() {
    return "Dependency [type=" + type + ", qualifier=" + qualifier + "]";
  }
}
