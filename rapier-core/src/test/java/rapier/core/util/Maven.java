package rapier.core.util;

import java.io.File;

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
   */
  public static File findJarInLocalRepository(String groupId, String artifactId, String version) {
    // Convert groupId to directory path (e.g., org.apache.maven -> org/apache/maven)
    final String groupPath = groupId.replace('.', '/');

    // Construct the path to the JAR file
    final String jarPath = String.format("%s/%s/%s/%s/%s-%s.jar", LOCAL_REPO_PATH, groupPath,
        artifactId, version, artifactId, version);

    // Return the JAR file as a File object
    final File jarFile = new File(jarPath);

    if (!jarFile.exists())
      throw new IllegalArgumentException("JAR file does not exist: " + jarFile);

    return jarFile;
  }
}
