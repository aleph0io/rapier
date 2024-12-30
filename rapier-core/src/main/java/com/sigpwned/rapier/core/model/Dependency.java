package com.sigpwned.rapier.core.model;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;

public class Dependency {
  private final TypeMirror type;

  /**
   * The qualifier annotation for this dependency, if any.
   */
  private final AnnotationMirror qualifier;

  /**
   * All annotations on this dependency. This includes the qualifier annotation, if any.
   */
  private final List<AnnotationMirror> annotations;

  public Dependency(TypeMirror type, AnnotationMirror qualifier,
      List<AnnotationMirror> annotations) {
    this.type = requireNonNull(type);
    this.qualifier = qualifier;
    this.annotations = unmodifiableList(annotations);
  }

  public TypeMirror getType() {
    return type;
  }

  public Optional<AnnotationMirror> getQualifier() {
    return Optional.ofNullable(qualifier);
  }

  public List<AnnotationMirror> getAnnotations() {
    return annotations;
  }

  @Override
  public int hashCode() {
    return Objects.hash(annotations, qualifier, type);
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
    return Objects.equals(annotations, other.annotations)
        && Objects.equals(qualifier, other.qualifier) && Objects.equals(type, other.type);
  }

  @Override
  public String toString() {
    return "Dependency [type=" + type + ", qualifier=" + qualifier + ", annotations=" + annotations
        + "]";
  }
}
