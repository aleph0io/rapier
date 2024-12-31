/*-
 * =================================LICENSE_START==================================
 * rapier-processor-core
 * ====================================SECTION=====================================
 * Copyright (C) 2024 Andy Boothe
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
package rapier.processor.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

public final class Hashing {
  private Hashing() {}

  /**
   * Returns the MD5 hash of the given string.
   * 
   * @param s the string
   * @return the hash
   */
  public static byte[] md5(String s) {
    return findDigest("MD5").orElseThrow(() -> {
      // This should never happen. The spec requires SHA-1 to be available.
      return new AssertionError("MD5 not available");
    }).digest(s.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Returns the SHA-1 hash of the given string.
   * 
   * @param s the string
   * @return the hash
   */
  public static byte[] sha1(String s) {
    return findDigest("SHA-1").orElseThrow(() -> {
      // This should never happen. The spec requires SHA-1 to be available.
      return new AssertionError("SHA-1 not available");
    }).digest(s.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Returns a MessageDigest for the given algorithm, e.g., {@code "MD5"}, {@code "SHA-1"}
   * 
   * @param algorithm the name of the algorithm requested.
   * @return the algorithm
   * 
   * @throws IllegalArgumentException if the algorithm is not available
   */
  private static Optional<MessageDigest> findDigest(String algorithm) {
    try {
      return Optional.of(MessageDigest.getInstance(algorithm));
    } catch (NoSuchAlgorithmException e) {
      return Optional.empty();
    }
  }
}
