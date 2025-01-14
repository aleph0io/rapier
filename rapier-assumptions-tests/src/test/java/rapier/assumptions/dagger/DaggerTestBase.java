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
package rapier.assumptions.dagger;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.joining;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import com.google.testing.compile.Compilation;
import rapier.compiler.core.util.Maven;

public abstract class DaggerTestBase {
  @SuppressWarnings("serial")
  private static class TempDir extends File implements AutoCloseable {
    public static TempDir createTempDirectory(String prefix) throws IOException {
      return new TempDir(Files.createTempDirectory(prefix).toFile());
    }

    private TempDir(File file) {
      super(file.getAbsolutePath());
    }

    @Override
    public void close() {
      delete();
    }
  }

  /**
   * Returns the annotation processors to use when compiling the source code. We will use any
   * annotation available via ServiceLoader, plus the Dagger processor.
   */
  protected Processor[] getAnnotationProcessors() {
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

    // Order matters here. Dagger needs to go after rapier.
    result.sort(new Comparator<Processor>() {
      @Override
      public int compare(Processor a, Processor b) {
        final int acat = category(a);
        final int bcat = category(b);

        if (acat != bcat)
          return acat - bcat;

        return a.getClass().getName().compareTo(b.getClass().getName());
      }

      private int category(Processor p) {
        final String processorClassName = p.getClass().getName();
        if (processorClassName.startsWith("rapier."))
          return 100;
        if (processorClassName.startsWith("dagger."))
          return 200;
        return 300;
      }
    });

    return result.toArray(Processor[]::new);
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

  protected List<File> getRunClasspath(Compilation compilation) throws IOException {
    List<File> result = new ArrayList<>();
    result.addAll(getCompileClasspath());

    // Add the compiled classes. Extract the compiled files into a temporary directory.
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

    final File tmpdirAsFile = tmpdir.toFile();
    result.add(tmpdirAsFile);
    tmpdirAsFile.deleteOnExit();

    return unmodifiableList(result);
  }


  protected String compileSourceCode(String... compilationUnitSourceCodes) throws IOException {
    return compileSourceCode(List.of(compilationUnitSourceCodes));
  }

  /**
   * Compiles a given source code string and returns any compilation errors.
   */
  protected String compileSourceCode(List<String> compilationUnitSourceCodes) throws IOException {
    try (TempDir tempDir = TempDir.createTempDirectory("dagger_test")) {
      return compileSourceCode(tempDir, compilationUnitSourceCodes);
    }
  }

  private static final Pattern COMPILATION_UNIT_NAME_PATTERN = Pattern.compile(
      "^public(?: abstract)? (?:class|interface) ([a-zA-Z_$][a-zA-Z0-9_$]*)", Pattern.MULTILINE);

  private static final Pattern PUBLIC_STATIC_VOID_MAIN_PATTERN =
      Pattern.compile("^    public static void main\\(String\\[\\] args\\) \\{", Pattern.MULTILINE);

  protected String compileAndRunSourceCode(String... compilationUnitSourceCodes)
      throws IOException {
    return compileAndRunSourceCode(List.of(compilationUnitSourceCodes));
  }

  /**
   * Compiles a given source code, runs the main method and returns the output from stdout.
   */
  protected String compileAndRunSourceCode(List<String> compilationUnitSourceCodes)
      throws IOException {
    return compileAndRunSourceCode(compilationUnitSourceCodes, DEFAULT_ANNOTATION_PROCESSORS);
  }


  /**
   * Compiles a given source code, runs the main method and returns the output from stdout.
   */
  protected String compileAndRunSourceCode(List<String> compilationUnitSourceCodes,
      List<String> annotationProcessors) throws IOException {
    return compileAndRunSourceCode(compilationUnitSourceCodes, annotationProcessors, emptyList());
  }

  /**
   * Compiles a given source code, runs the main method and returns the output from stdout.
   */
  protected String compileAndRunSourceCode(List<String> compilationUnitSourceCodes,
      List<String> annotationProcessors, List<File> additionalClasspathEntries) throws IOException {
    try (TempDir tempDir = TempDir.createTempDirectory("rapier_test")) {
      String errors = compileSourceCode(tempDir, compilationUnitSourceCodes, annotationProcessors,
          additionalClasspathEntries);
      if (!errors.isBlank()) {
        throw new IllegalArgumentException(
            "Compilation of invalid compilation units failed with errors: " + errors);
      }

      final String mainCompilationUnitName = compilationUnitSourceCodes.stream()
          .filter(sourceCode -> PUBLIC_STATIC_VOID_MAIN_PATTERN.matcher(sourceCode).find())
          .map(sourceCode -> {
            final Matcher classNameMatcher = COMPILATION_UNIT_NAME_PATTERN.matcher(sourceCode);
            if (!classNameMatcher.find())
              throw new IllegalArgumentException(
                  "Could not determine class name from compilation unit");
            return classNameMatcher.group(1);
          }).findFirst().orElseThrow(() -> {
            return new IllegalArgumentException(
                "compilationUnitSourceCodes must contain a main method");
          });

      try {
        // Dynamically load the compiled classes
        final List<URL> classpath = new ArrayList<>();
        for (File simulationClasspathEntry : simulationClasspath())
          classpath.add(simulationClasspathEntry.toURI().toURL());
        classpath.add(tempDir.toURI().toURL());
        if (additionalClasspathEntries != null)
          for (File additionalClasspathEntry : additionalClasspathEntries)
            classpath.add(additionalClasspathEntry.toURI().toURL());
        try (URLClassLoader classLoader = new URLClassLoader(classpath.toArray(URL[]::new),
            ClassLoader.getPlatformClassLoader())) {

          final Class<?> mainClass = classLoader.loadClass(mainCompilationUnitName);

          // Run the main method of the specified class
          final Method mainMethod = mainClass.getDeclaredMethod("main", String[].class);

          final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
          synchronized (System.class) {
            final PrintStream originalOut = System.out;
            try {
              System.setOut(new PrintStream(stdout));
              mainMethod.invoke(null, (Object) new String[] {});
            } finally {
              System.setOut(originalOut);
            }
          }

          return new String(stdout.toByteArray(), StandardCharsets.UTF_8);
        }
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
        throw new IllegalStateException(
            "Execution of invalid compilation units failed with exception", cause);
      } catch (Exception e) {
        throw new IllegalArgumentException(
            "Execution of invalid compilation units failed with exception", e);
      }
    }
  }

  protected static final String DAGGER_COMPONENT_ANNOTATION_PROCESSOR =
      "dagger.internal.codegen.ComponentProcessor";

  private static final List<String> DEFAULT_ANNOTATION_PROCESSORS =
      List.of(DAGGER_COMPONENT_ANNOTATION_PROCESSOR);

  /**
   * Compiles a given source code string and returns any compilation errors.
   * 
   * @param tempDir the temporary directory to store the generated source files
   * @param compilationUnitSourceCodes the source code strings to compile
   * @return any compilation errors
   * 
   * @throws IOException if an I/O error occurs
   */
  private String compileSourceCode(File tempDir, List<String> compilationUnitSourceCodes)
      throws IOException {
    return compileSourceCode(tempDir, compilationUnitSourceCodes, DEFAULT_ANNOTATION_PROCESSORS);
  }

  /**
   * Compiles a given source code string and returns any compilation errors.
   * 
   * @param tempDir the temporary directory to store the generated source files
   * @param compilationUnitSourceCodes the source code strings to compile
   * @return any compilation errors
   * 
   * @throws IOException if an I/O error occurs
   */
  private String compileSourceCode(File tempDir, List<String> compilationUnitSourceCodes,
      List<String> annotationProcessors) throws IOException {
    return compileSourceCode(tempDir, compilationUnitSourceCodes, annotationProcessors,
        emptyList());
  }

  /**
   * Compiles a given source code string and returns any compilation errors.
   * 
   * @param tempDir the temporary directory to store the generated source files
   * @param compilationUnitSourceCodes the source code strings to compile
   * @return any compilation errors
   * 
   * @throws IOException if an I/O error occurs
   */
  private String compileSourceCode(File tempDir, List<String> compilationUnitSourceCodes,
      List<String> annotationProcessors, List<File> additionalClasspathEntries) throws IOException {
    // Get the system Java compiler
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException(
          "JavaCompiler not available. Make sure JDK is used instead of JRE.");
    }

    // Write the source files to disk
    final List<File> sourceFiles = new ArrayList<>();
    for (final String compilationUnitSourceCode : compilationUnitSourceCodes) {
      final Matcher classNameMatcher =
          COMPILATION_UNIT_NAME_PATTERN.matcher(compilationUnitSourceCode);
      if (!classNameMatcher.find())
        throw new IllegalArgumentException("Could not determine class name from compilation unit");
      final String className = classNameMatcher.group(1);

      final File sourceFile = new File(tempDir, className + ".java");
      try (PrintWriter out = new PrintWriter(sourceFile)) {
        out.println(compilationUnitSourceCode);
      }

      sourceFiles.add(sourceFile);
    }

    // Set up diagnostic collector to capture compilation errors
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

    // Set up our classpath
    final List<File> classpathEntries = new ArrayList<>();
    classpathEntries.addAll(simulationClasspath());
    for (File additionalClasspathEntry : additionalClasspathEntries)
      classpathEntries.add(additionalClasspathEntry);
    final String classpath =
        classpathEntries.stream().map(File::getAbsolutePath).collect(joining(File.pathSeparator));

    // Configure annotation processors (include Dagger's processor)
    List<String> options = List.of("-cp", classpath, "-processor",
        String.join(",", annotationProcessors), "-s", tempDir.getAbsolutePath());

    // Compile the source file
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, null, null)) {
      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjects(sourceFiles.toArray(File[]::new));
      JavaCompiler.CompilationTask task =
          compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);

      final boolean success = task.call();
      if (success) {
        // This will be true if the compilation succeeded, or false otherwise
      }

      // Collect all errors
      String errors = diagnostics.getDiagnostics().stream()
          .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
          .map(d -> d.getMessage(Locale.getDefault())).collect(Collectors.joining("\n"));

      return errors;
    }
  }

  /**
   * The root directory of the current Maven module
   */
  private static final File MAVEN_PROJECT_BASEDIR =
      Optional.ofNullable(System.getProperty("maven.project.basedir")).map(File::new).orElseThrow(
          () -> new IllegalStateException("maven.project.basedir system property not set"));

  protected File resolveProjectFile(String path) throws FileNotFoundException {
    final File result = new File(MAVEN_PROJECT_BASEDIR, path);
    if (!result.exists())
      throw new FileNotFoundException(result.toString());
    return result;
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

  private List<File> simulationClasspath() throws FileNotFoundException {
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
}
