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
package rapier.processor.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;



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
    try (TempDir tempDir = TempDir.createTempDirectory("dagger_test")) {
      String errors = compileSourceCode(tempDir, compilationUnitSourceCodes, annotationProcessors);
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
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {tempDir.toURI().toURL()},
            Thread.currentThread().getContextClassLoader())) {

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
    // Get the system Java compiler
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException(
          "JavaCompiler not available. Make sure JDK is used instead of JRE.");
    }

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

    // Configure annotation processors (include Dagger's processor)
    List<String> options = List.of("-processor", String.join(",", annotationProcessors), "-s",
        tempDir.getAbsolutePath());

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
}
