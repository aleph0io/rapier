package com.sigpwned.rapier.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Hashing {
  private Hashing() {}

  public static byte[] md5(String s) {
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      // This should never happen. The spec requires MD5 to be available.
      throw new AssertionError("MD5 not available", e);
    }
    return digest.digest(s.getBytes(StandardCharsets.UTF_8));
  }
}
