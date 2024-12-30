package com.sigpwned.rapier.core.util;

/**
 * Utility methods for working with Java code.
 */
public final class Java {
  private Java() {}

  /**
   * Escapes a string for use in a Java string literal.
   * 
   * @param s the string to escape
   * @return the escaped string
   * 
   * @throws NullPointerException if {@code s} is {@code null}
   */
  public static String escapeString(String s) {
    if (s == null)
      throw new NullPointerException();
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
