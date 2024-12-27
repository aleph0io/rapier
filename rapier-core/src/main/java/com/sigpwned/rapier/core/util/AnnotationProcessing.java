package com.sigpwned.rapier.core.util;

import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;

public final class AnnotationProcessing {
  private AnnotationProcessing() {}

  public static Optional<AnnotationMirror> findFirstAnnotationByQualifiedName(Element element,
      String qualifiedName) {
    if (element == null)
      throw new NullPointerException();
    if (qualifiedName == null)
      throw new NullPointerException();

    for (AnnotationMirror annotation : element.getAnnotationMirrors())
      if (annotation.getAnnotationType().toString().equals(qualifiedName))
        return Optional.of(annotation);

    return Optional.empty();
  }

  public static boolean isDefaultConstructor(ExecutableElement constructor) {
    if (constructor == null)
      throw new NullPointerException();

    assert constructor.getKind() == ElementKind.CONSTRUCTOR;

    return constructor.getParameters().isEmpty();
  }
}
