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
package rapier.envvar.compiler;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static java.util.Collections.unmodifiableList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import rapier.core.DaggerTestBase;

public class EnvironmentVariableProcessorTest extends DaggerTestBase {
  @Override
  protected List<File> getCompileClasspath() throws FileNotFoundException {
    List<File> result = new ArrayList<>();
    result.addAll(super.getCompileClasspath());
    result.add(resolveProjectFile("../rapier-environment-variable/target/classes"));
    return unmodifiableList(result);
  }

  @Test
  public void givenSimpleComponentWithEnvironmentVariableWithoutDefaultValue_whenCompile_thenExpectedtModuleIsGenerated()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source =
        JavaFileObjects.forSourceString("com.example.ExampleComponent", """
            package com.example;

            @dagger.Component(modules = {RapierExampleComponentEnvironmentVariableModule.class})
            public interface ExampleComponent {
                @rapier.envvar.EnvironmentVariable("FOO_BAR")
                public Integer provisionFooBarAsInt();
            }
            """);

    // Run the annotation processor
    final Compilation compilation = doCompile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("com.example.RapierExampleComponentEnvironmentVariableModule")
        .hasSourceEquivalentTo(JavaFileObjects.forSourceString(
            "com.example.RapierExampleComponentEnvironmentVariableModule",
            """
                package com.example;

                import static java.util.Collections.unmodifiableMap;

                import rapier.envvar.EnvironmentVariable;
                import dagger.Module;
                import dagger.Provides;
                import java.util.Map;
                import java.util.Optional;
                import javax.annotation.Nullable;
                import javax.inject.Inject;

                @Module
                public class RapierExampleComponentEnvironmentVariableModule {
                    private final Map<String, String> env;

                    @Inject
                    public RapierExampleComponentEnvironmentVariableModule() {
                        this(System.getenv());
                    }

                    public RapierExampleComponentEnvironmentVariableModule(Map<String, String> env) {
                        this.env = unmodifiableMap(env);
                    }

                    @Provides
                    @EnvironmentVariable("FOO_BAR")
                    public java.lang.Integer provideEnvironmentVariableFooBarAsInteger(@EnvironmentVariable("FOO_BAR") String value) {
                        java.lang.Integer result = java.lang.Integer.valueOf(value);
                        if (result == null)
                            throw new IllegalStateException("Environment variable FOO_BAR representation java.lang.Integer not set");
                        return result;
                    }

                    @Provides
                    @EnvironmentVariable("FOO_BAR")
                    public String provideEnvironmentVariableFooBarAsString() {
                        String result=env.get("FOO_BAR");
                        if (result == null)
                            throw new IllegalStateException("Environment variable FOO_BAR not set");
                        return result;
                    }

                }

                """));
  }

  private Compilation doCompile(JavaFileObject... source) throws IOException {
    return Compiler.javac().withClasspath(getCompileClasspath())
        .withProcessors(getAnnotationProcessors()).compile(source);
  }

  private String doRun(Compilation compilation) throws IOException {
    final List<File> classpathAsFiles = getRunClasspath(compilation);

    final List<URL> classpathAsUrls = new ArrayList<>();
    for (File file : classpathAsFiles)
      classpathAsUrls.add(file.toURI().toURL());

    final List<JavaFileObject> mains = new ArrayList<>();
    compilation.sourceFiles().stream().filter(EnvironmentVariableProcessorTest::containsMainMethod)
        .forEach(mains::add);
    compilation.generatedSourceFiles().stream()
        .filter(EnvironmentVariableProcessorTest::containsMainMethod).forEach(mains::add);
    if (mains.isEmpty())
      throw new IllegalArgumentException("No main method found");
    if (mains.size() > 1)
      throw new IllegalArgumentException("Multiple main methods found");
    final JavaFileObject main = mains.get(0);

    try (URLClassLoader classLoader = new URLClassLoader(classpathAsUrls.toArray(URL[]::new),
        ClassLoader.getPlatformClassLoader())) {

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
          mainMethod.invoke(null, (Object) new String[] {});
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
  private static final Pattern CLASS_DECLARATION_PATTERN =
      Pattern.compile("^public\\s+(?:class|interface)\\s+(\\S+)\\s*\\{", Pattern.MULTILINE);

  private static JavaFileObject prepareSourceFile(String sourceCode) {
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


  @Test
  public void givenSimpleComponentWithEnvironmentVariableWithDefaultValue_whenCompile_thenExpectedtModuleIsGenerated() {
    // Define the source file to test
    final JavaFileObject source =
        JavaFileObjects.forSourceString("com.example.ExampleComponent", """
            package com.example;

            @dagger.Component
            public interface ExampleComponent {
                @rapier.envvar.EnvironmentVariable(value="FOO_BAR", defaultValue="42")
                public Integer provisionFooBarAsInt();
            }
            """);

    // Run the annotation processor
    final Compilation compilation =
        Compiler.javac().withProcessors(new EnvironmentVariableProcessor()).compile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    assertThat(compilation)
        .generatedSourceFile("com.example.RapierExampleComponentEnvironmentVariableModule")
        .hasSourceEquivalentTo(JavaFileObjects.forSourceString(
            "com.example.RapierExampleComponentEnvironmentVariableModule",
            """
                package com.example;

                import static java.util.Collections.unmodifiableMap;

                import rapier.envvar.EnvironmentVariable;
                import dagger.Module;
                import dagger.Provides;
                import java.util.Map;
                import java.util.Optional;
                import javax.annotation.Nullable;
                import javax.inject.Inject;

                @Module
                public class RapierExampleComponentEnvironmentVariableModule {
                    private final Map<String, String> env;

                    @Inject
                    public RapierExampleComponentEnvironmentVariableModule() {
                        this(System.getenv());
                    }

                    public RapierExampleComponentEnvironmentVariableModule(Map<String, String> env) {
                        this.env = unmodifiableMap(env);
                    }

                    @Provides
                    @EnvironmentVariable(value="FOO_BAR", defaultValue="42")
                    public java.lang.Integer provideEnvironmentVariableFooBarWithDefaultValue92cfcebAsInteger(@EnvironmentVariable(value="FOO_BAR", defaultValue="42") String value) {
                        return java.lang.Integer.valueOf(value);
                    }

                    @Provides
                    @EnvironmentVariable(value="FOO_BAR", defaultValue="42")
                    public String provideEnvironmentVariableFooBarWithDefaultValue92cfcebAsString() {
                        return Optional.ofNullable(env.get("FOO_BAR")).orElse("42");
                    }

                }
                """));
  }

  @Test
  public void givenSimpleComponentWithEnvironmentVariableWithGivenValue_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    // Define the source file to test
    final String componentSource = """
        @dagger.Component(modules={RapierExampleComponentEnvironmentVariableModule.class})
        public interface ExampleComponent {
            @javax.annotation.Nullable
            @rapier.envvar.EnvironmentVariable("FOO_BAR")
            public Integer provisionFooBarAsInt();
        }
        """;

    final String appSource =
        """
            import java.util.Map;

            public class App {
                public static void main(String[] args) {
                    ExampleComponent component = DaggerExampleComponent.builder()
                        .rapierExampleComponentEnvironmentVariableModule(new RapierExampleComponentEnvironmentVariableModule(Map.of("FOO_BAR", "42")))
                        .build();
                    System.out.println(component.provisionFooBarAsInt());
                }
            }
            """;

    final String output = compileAndRunSourceCode(List.of(componentSource, appSource),
        List.of("rapier.envvar.compiler.EnvironmentVariableProcessor",
            DAGGER_COMPONENT_ANNOTATION_PROCESSOR),
        List.of(resolveProjectFile("../rapier-environment-variable/target/classes"))).trim();

    assertEquals("42", output);
  }

  @Test
  public void givenSimpleComponentWithEnvironmentVariableWithDefaultValue_whenCompileAndRun_thenExpectedtOutput()
      throws IOException {
    final JavaFileObject componentSource = prepareSourceFile("""
        @dagger.Component(modules={RapierExampleComponentEnvironmentVariableModule.class})
        public interface ExampleComponent {
            @rapier.envvar.EnvironmentVariable(value="FOO_BAR", defaultValue="43")
            public Integer provisionFooBarAsInt();
        }
        """);

    final JavaFileObject appSource = prepareSourceFile("""
        import java.util.Map;

        public class App {
            public static void main(String[] args) {
                ExampleComponent component = DaggerExampleComponent.builder()
                    .rapierExampleComponentEnvironmentVariableModule(
                        new RapierExampleComponentEnvironmentVariableModule(Map.of()))
                    .build();
                System.out.println(component.provisionFooBarAsInt());
            }
        }
        """);

    Compilation compilation = doCompile(componentSource, appSource);

    assertThat(compilation).succeeded();

    final String output = doRun(compilation).trim();

    assertEquals("43", output);
  }

  @Test
  public void givenComponentWithInconsistentEnvironmentVariableParameterRequirednessFromNullable_whenCompile_thenCompileWarning()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source =
        JavaFileObjects.forSourceString("com.example.ExampleComponent", """
            package com.example;

            @dagger.Component(modules={RapierExampleComponentEnvironmentVariableModule.class})
            public interface ExampleComponent {
                @javax.annotation.Nullable
                @rapier.envvar.EnvironmentVariable(value="FOO_BAR")
                public Integer provisionFooBarAsInt();

                @rapier.envvar.EnvironmentVariable(value="FOO_BAR")
                public String provisionFooBarAsString();
            }
            """);

    // Run the annotation processor
    final Compilation compilation =
        Compiler.javac().withProcessors(new EnvironmentVariableProcessor()).compile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    assertTrue(
        compilation.warnings().stream().anyMatch(e -> e.getMessage(Locale.getDefault()).equals(
            "Conflicting requiredness for environment variable FOO_BAR, will be treated as required")));
    assertTrue(compilation.warnings().stream().anyMatch(e -> e.getMessage(Locale.getDefault())
        .equals("Effectively required environment variable FOO_BAR is treated as nullable")));
  }

  @Test
  public void givenComponentWithInconsistentEnvironmentVariableParameterRequirednessFromDefaultValue_whenCompile_thenCompileWarning()
      throws IOException {
    // Define the source file to test
    final JavaFileObject source =
        JavaFileObjects.forSourceString("com.example.ExampleComponent", """
            package com.example;

            @dagger.Component(modules={RapierExampleComponentEnvironmentVariableModule.class})
            public interface ExampleComponent {
                @rapier.envvar.EnvironmentVariable(value="FOO_BAR")
                public Integer provisionFooBarAsInt();

                @rapier.envvar.EnvironmentVariable(value="FOO_BAR", defaultValue="42")
                public String provisionFooBarAsString();
            }
            """);

    // Run the annotation processor
    final Compilation compilation =
        Compiler.javac().withProcessors(new EnvironmentVariableProcessor()).compile(source);

    // Assert the compilation succeeded
    assertThat(compilation).succeeded();

    assertTrue(
        compilation.warnings().stream().anyMatch(e -> e.getMessage(Locale.getDefault()).equals(
            "Conflicting requiredness for environment variable FOO_BAR, will be treated as required")));
    assertTrue(compilation.warnings().stream().anyMatch(e -> e.getMessage(Locale.getDefault())
        .equals("Effectively required environment variable FOO_BAR has default value")));
  }
}
