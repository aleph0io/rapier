package com.sigpwned.rapier.core.util;

public final class Hex {
  private Hex() {}

  /**
   * Encode the given byte array as a lowercase hex string.
   * 
   * @param bytes the bytes to encode
   * @return the encoded hex string
   */
  public static String encode(byte[] bytes) {
    if (bytes == null)
      throw new NullPointerException();

    final StringBuilder result = new StringBuilder();
    for (byte b : bytes) {
      result.append(String.format("%02x", b));
    }

    return result.toString();
  }
}
