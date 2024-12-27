package com.sigpwned.rapier.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@javax.inject.Qualifier
@jakarta.inject.Qualifier
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
public @interface EnvironmentVariable {
  public String value();
}
