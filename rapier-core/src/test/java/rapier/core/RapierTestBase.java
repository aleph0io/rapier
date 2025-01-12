/*-
 * =================================LICENSE_START==================================
 * rapier-core
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
package rapier.core;

import static java.util.Collections.unmodifiableList;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.regex.Pattern;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import rapier.core.util.Maven;

/**
 * Base class for Rapier tests.
 * 
 * <p>
 * Any {@code public} methods are for use in test classes. Any {@code protected} methods are for
 * override in subclasses. Any {@code private} methods are for internal use only, and are not
 * visible to test classes anyway.
 */
public abstract class RapierTestBase {
  public Compilation doCompile(JavaFileObject... source) throws IOException {
    return Compiler.javac().withClasspath(getCompileClasspath())
        .withProcessors(getAnnotationProcessors()).compile(source);
  }

  public String doRun(Compilation compilation, String... args) throws IOException {
    final List<File> classpathAsFiles = getRunClasspath(compilation);

    final List<URL> classpathAsUrls = new ArrayList<>();
    for (File file : classpathAsFiles)
      classpathAsUrls.add(file.toURI().toURL());

    final List<JavaFileObject> mains = new ArrayList<>();
    compilation.sourceFiles().stream().filter(RapierTestBase::containsMainMethod)
        .forEach(mains::add);
    compilation.generatedSourceFiles().stream().filter(RapierTestBase::containsMainMethod)
        .forEach(mains::add);
    if (mains.isEmpty())
      throw new IllegalArgumentException("No main method found");
    if (mains.size() > 1)
      throw new IllegalArgumentException("Multiple main methods found");
    final JavaFileObject main = mains.get(0);

    try (URLClassLoader classLoader =
        new URLClassLoader(classpathAsUrls.toArray(URL[]::new), getRunParentClassLoader())) {

      final Class<?> mainClass = classLoader.loadClass(toQualifiedClassName(main));

      // Run the main method of the specified class
      final Method mainMethod = mainClass.getDeclaredMethod("main", String[].class);

      final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
      final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
      synchronized (System.class) {
        final PrintStream stdout0 = System.out;
        final PrintStream stderr0 = System.err;
        try {
          System.setOut(new PrintStream(stdout));
          System.setErr(new PrintStream(stderr));
          mainMethod.invoke(null, (Object) args);
        } finally {
          System.setOut(stdout0);
          System.setErr(stderr0);
        }
      }

      final byte[] stdoutBytes = stdout.toByteArray();
      final byte[] stderrBytes = stderr.toByteArray();

      if (stderrBytes.length > 0)
        System.err.write(stderrBytes);

      return new String(stdoutBytes, StandardCharsets.UTF_8);
    } catch (RuntimeException e) {
      throw e;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException)
        throw (RuntimeException) cause;
      if (cause instanceof IOException)
        throw new UncheckedIOException((IOException) cause);
      throw new IllegalArgumentException(
          "Execution of invalid compilation units failed with exception", cause);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Execution of invalid compilation units failed with exception", e);
    }
  }

  /**
   * Check if a JavaFileObject contains a main method. A main method is considered to be present if
   * the file contains the literal string {@code "public static void main(String[] args)"}.
   * 
   * @param file the file to check
   * @return {@code true} if the file contains a main method, {@code false} otherwise
   * @throws UncheckedIOException if an IOException occurs while reading the file
   */
  private static boolean containsMainMethod(JavaFileObject file) {
    try {
      return file.getCharContent(true).toString()
          .contains("public static void main(String[] args)");
    } catch (IOException e) {
      // This really ought never to happen, since it's all in memory.
      throw new UncheckedIOException("Failed to read JavaFileObject contents", e);
    }
  }

  /**
   * Extracts the qualified Java class name from a JavaFileObject.
   *
   * @param file the JavaFileObject representing the Java source code
   * @return the qualified class name
   */
  private static String toQualifiedClassName(JavaFileObject file) {
    // Get the name of the JavaFileObject
    String fileName = file.getName();

    // Remove directories or prefixes before the package
    // Example: "/com/example/HelloWorld.java"
    // becomes "com/example/HelloWorld.java"
    if (fileName.startsWith("/")) {
      fileName = fileName.substring(1);
    }

    // Remove the ".java" extension
    if (fileName.endsWith(".java")) {
      fileName = fileName.substring(0, fileName.length() - ".java".length());
    }

    // Replace '/' with '.' to form package and class hierarchy
    fileName = fileName.replace("/", ".");

    return fileName;
  }

  private static final Pattern PACKAGE_DECLARATION_PATTERN =
      Pattern.compile("^package\\s+(\\S+)\\s*;", Pattern.MULTILINE);
  private static final Pattern CLASS_DECLARATION_PATTERN = Pattern.compile(
      "^public\\s+(?:class|interface)\\s+(\\S+)\\s*(?:extends\\s+\\S+)?\\s*(?:implements\\s+\\S+(?:\\s*,\\s*\\S+)*)?\\s*\\{",
      Pattern.MULTILINE);

  public JavaFileObject prepareSourceFile(String sourceCode) {
    final String packageName = PACKAGE_DECLARATION_PATTERN.matcher(sourceCode).results().findFirst()
        .map(m -> m.group(1)).orElse(null);

    final String simpleClassName = CLASS_DECLARATION_PATTERN.matcher(sourceCode).results()
        .findFirst().map(m -> m.group(1)).orElse(null);
    if (simpleClassName == null)
      throw new IllegalArgumentException("Failed to detect class name");

    final String qualifiedClassName =
        packageName != null ? packageName + "." + simpleClassName : simpleClassName;

    return JavaFileObjects.forSourceString(qualifiedClassName, sourceCode);
  }

  /**
   * The root directory of the current Maven module
   */
  private static final File MAVEN_PROJECT_BASEDIR =
      Optional.ofNullable(System.getProperty("maven.project.basedir")).map(File::new).orElseThrow(
          () -> new IllegalStateException("maven.project.basedir system property not set"));

  public File resolveProjectFile(String path) throws FileNotFoundException {
    final File result = new File(MAVEN_PROJECT_BASEDIR, path);
    if (!result.exists())
      throw new FileNotFoundException(result.toString());
    return result;
  }

  protected List<File> getRunClasspath(Compilation compilation) throws IOException {
    List<File> result = new ArrayList<>();

    // We need everything on the compile classpath
    result.addAll(getCompileClasspath());

    // Extract the compiled files into a temporary directory.
    final Path tmpdir = Files.createTempDirectory("test");
    for (JavaFileObject file : compilation.generatedFiles()) {
      if (file.getKind() == JavaFileObject.Kind.CLASS) {
        final String originalClassFileName = file.getName();

        String sanitizedClassFileName = originalClassFileName;
        sanitizedClassFileName = sanitizedClassFileName.replace("/", File.separator);
        if (sanitizedClassFileName.startsWith(File.separator))
          sanitizedClassFileName = sanitizedClassFileName.substring(1);
        if (sanitizedClassFileName.startsWith("CLASS_OUTPUT" + File.separator))
          sanitizedClassFileName = sanitizedClassFileName.substring("CLASS_OUTPUT".length() + 1,
              sanitizedClassFileName.length());

        final Path tmpdirClassFile = tmpdir.resolve(sanitizedClassFileName);

        Files.createDirectories(tmpdirClassFile.getParent());

        try (InputStream in = file.openInputStream()) {
          Files.copy(in, tmpdirClassFile);
        }
      }
    }

    // We also want the compiled classes to actually run the application
    final File tmpdirAsFile = tmpdir.toFile();
    result.add(tmpdirAsFile);
    tmpdirAsFile.deleteOnExit();

    return unmodifiableList(result);
  }

  /**
   * The {@link ClassLoader} to use as the parent of the class loader used to run the application.
   */
  protected ClassLoader getRunParentClassLoader() {
    // By default, we want to use the bootstrap class loader.
    return ClassLoader.getPlatformClassLoader();
  }

  protected List<File> getCompileClasspath() throws FileNotFoundException {
    final File daggerJar =
        Maven.findJarInLocalRepository("com.google.dagger", "dagger", DAGGER_VERSION);
    final File javaxInjectJar =
        Maven.findJarInLocalRepository("javax.inject", "javax.inject", JAVAX_INJECT_VERSION);
    final File jakartaInjectApiJar = Maven.findJarInLocalRepository("jakarta.inject",
        "jakarta.inject-api", JAKARTA_INJECT_API_VERSION);
    final File jsr305Jar =
        Maven.findJarInLocalRepository("com.google.code.findbugs", "jsr305", JSR_305_VERSION);
    return List.of(daggerJar, javaxInjectJar, jakartaInjectApiJar, jsr305Jar);
  }

  /**
   * The Dagger version to use for compiling test code. This should be passed by
   * maven-surefire-plugin using the exact dagger version from the POM. See the root POM for the
   * specific details of the setup.
   */
  private static final String DAGGER_VERSION =
      Optional.ofNullable(System.getProperty("maven.dagger.version")).orElseThrow(
          () -> new IllegalStateException("maven.dagger.version system property not set"));

  /**
   * The javax.inject version to use for compiling test code. This should be passed by
   * maven-surefire-plugin using the exact javax.inject version from the POM. See the root POM for
   * the specific details of the setup.
   */
  private static final String JAVAX_INJECT_VERSION =
      Optional.ofNullable(System.getProperty("maven.javax.inject.version")).orElseThrow(
          () -> new IllegalStateException("maven.javax.inject.version system property not set"));

  /**
   * The Jakarta Inject API version to use for compiling test code. This should be passed by
   * maven-surefire-plugin using the exact Jakarta Inject API version from the POM. See the root POM
   * for the specific details
   */
  private static final String JAKARTA_INJECT_API_VERSION =
      Optional.ofNullable(System.getProperty("maven.jakarta.inject-api.version"))
          .orElseThrow(() -> new IllegalStateException(
              "maven.jakarta.inject-api.version system property not set"));

  private static final String JSR_305_VERSION =
      Optional.ofNullable(System.getProperty("maven.jsr305.version")).orElseThrow(
          () -> new IllegalStateException("maven.jsr305.version system property not set"));

  /**
   * Returns the annotation processors to use when compiling the source code. We will use any
   * annotation available via ServiceLoader, plus the Dagger processor.
   */
  protected List<Processor> getAnnotationProcessors() {
    final List<Processor> result = new ArrayList<>();
    for (Processor processor : ServiceLoader.load(Processor.class))
      result.add(processor);

    // We only want to run dagger and rapier processors.
    final Iterator<Processor> iterator = result.iterator();
    while (iterator.hasNext()) {
      final Processor processor = iterator.next();
      final String processorClassName = processor.getClass().getName();

      // Skip any annotation processors we don't recognize
      if (processorClassName.startsWith("dagger.")) {
        // This is a dagger processor. Let's keep it, obviously.
      } else if (processorClassName.startsWith("rapier.")) {
        // This is a rapier processor. Let's keep it, obviously.
      } else if (processorClassName.startsWith("com.google.")) {
        // We want to see if rapier and dagger work on a standalone basis.
        // We recognize and trust these, but let's skip them.
        iterator.remove();
      } else {
        // What are you...?
        System.err
            .println("WARNING: Skipping unrecognized annotation processor: " + processorClassName);
        iterator.remove();
      }
    }

    return unmodifiableList(result);
  }
}
