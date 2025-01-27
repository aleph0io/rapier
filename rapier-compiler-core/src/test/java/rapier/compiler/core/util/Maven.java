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
package rapier.compiler.core.util;

import java.io.File;
import java.io.FileNotFoundException;

public final class Maven {
  private Maven() {}

  private static final String LOCAL_REPO_PATH = System.getProperty("user.home") + "/.m2/repository";

  /**
   * Finds the JAR file for a given Maven artifact in the local repository. Makes no attempt to
   * download the artifact if it does not exist in the local cache. For this reason, this method
   * should generally only be used for artifacts that are present in the build, since the build will
   * guarantee that the artifact is present in the local repository.
   *
   * @param groupId The group ID of the artifact
   * @param artifactId The artifact ID of the artifact
   * @param version The version of the artifact
   * @return An Optional containing the JAR file if it exists, or an empty Optional otherwise
   * @throws FileNotFoundException if the JAR file does not exist
   */
  public static File findJarInLocalRepository(String groupId, String artifactId, String version)
      throws FileNotFoundException {
    // Convert groupId to directory path (e.g., org.apache.maven -> org/apache/maven)
    final String groupPath = groupId.replace('.', '/');

    // Construct the path to the JAR file
    final String jarPath = String.format("%s/%s/%s/%s/%s-%s.jar", LOCAL_REPO_PATH, groupPath,
        artifactId, version, artifactId, version);

    // Return the JAR file as a File object
    final File jarFile = new File(jarPath);

    if (!jarFile.exists())
      throw new FileNotFoundException(jarFile.getAbsolutePath());

    return jarFile;
  }
}
