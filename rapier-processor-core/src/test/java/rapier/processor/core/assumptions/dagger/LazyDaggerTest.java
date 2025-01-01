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
package rapier.processor.core.assumptions.dagger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import dagger.Lazy;
import rapier.processor.core.DaggerTestBase;

/**
 * Test assumptions about how {@link Lazy} bindings work in Dagger.
 */
public class LazyDaggerTest extends DaggerTestBase {
  /**
   * Verify that Dagger provides a Lazy<String> binding when a module provides a String binding.
   */
  @Test
  public void givenLazyOfStringDependencyAndStringBinding_whenCompile_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import dagger.Lazy;

        @Component(modules = {ExampleModule.class})
        public interface ExampleComponent {
            public Lazy<String> example();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleModule {
            @Provides
            public String example() {
                return "example";
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode);

    assertTrue(errors.isBlank(), "Expected no errors, but errors were found.");
  }

  /**
   * Verify that Dagger provides a @Named("example") Lazy<String> binding when a module provides
   * a @Named("example") String binding.
   */
  @Test
  public void givenQualifiedLazyOfStringDependencyAndQualifiedStringBinding_whenCompile_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import dagger.Lazy;
        import javax.inject.Named;

        @Component(modules = {ExampleModule.class})
        public interface ExampleComponent {
            @Named("example")
            public Lazy<String> example();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import javax.inject.Named;

        @Module
        public class ExampleModule {
            @Provides
            @Named("example")
            public String example() {
                return "example";
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode);

    assertTrue(errors.isBlank(), "Expected no errors, but errors were found.");
  }

  /**
   * Verify that Dagger does not allow qualifier annotations on Lazy type parameters.
   */
  @Test
  public void givenLazyOfQualifiedStringDependency_whenCompile_thenCompileError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import dagger.Lazy;
        import javax.inject.Named;

        @Component
        public interface ExampleComponent {
            public Lazy<@Named("example") String> example();
        }
        """;

    final String errors = compileSourceCode(componentSourceCode).trim();

    assertEquals("annotation @javax.inject.Named not applicable in this type context", errors);
  }

  /**
   * Verify that Dagger does not provide a @Named("example") Lazy<String> binding when a module
   * provides only an unqualified String.
   */
  @Test
  public void givenQualifiedLazyOfStringDependencyAndUnqualifiedStringBinding_whenCompile_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import dagger.Lazy;
        import javax.inject.Named;

        @Component(modules = {ExampleModule.class})
        public interface ExampleComponent {
            @Named("example")
            public Lazy<String> example();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import javax.inject.Named;

        @Module
        public class ExampleModule {
            @Provides
            public String example() {
                return "example";
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode);

    // This produces an error because the Lazy is for a qualified type, but the module provides
    // only an unqualified type.
    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a missing binding error, but no missing binding error was found.");
  }

  /**
   * Verify that Dagger provides a @Nullable Lazy<String> binding when a module provides a
   * 
   * @Nullable String binding.
   */
  @Test
  public void givenNullableLazyOfStringDependencyAndNullableStringBinding_whenCompileAndRun_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import dagger.Lazy;
        import javax.annotation.Nullable;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            @Nullable
            public Lazy<String> string();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import javax.annotation.Nullable;

        @Module
        public class ExampleModule {
            @Provides
            @Nullable
            public String provideString() {
                return null;
            }
        }
        """;

    final String appSourceCode = """
        public class ExampleApp {
            public static void main(String[] args) {
                ExampleComponent c = DaggerExampleComponent.builder().build();
                System.out.println(c.string().get());
            }
        }
        """;

    final String output =
        compileAndRunSourceCode(componentSourceCode, moduleSourceCode, appSourceCode).trim();

    assertEquals("null", output);
  }

  /**
   * Verify that Dagger provides a @Nullable Lazy<String> binding when a module provides a
   * non-@Nullable String binding.
   */
  @Test
  public void givenNullableLazyOfStringDependencyAndNonNullableStringBinding_whenCompileAndRun_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import dagger.Lazy;
        import javax.annotation.Nullable;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            @Nullable
            public Lazy<String> string();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import javax.annotation.Nullable;

        @Module
        public class ExampleModule {
            @Provides
            public String provideString() {
                return "hello";
            }
        }
        """;

    final String appSourceCode = """
        public class ExampleApp {
            public static void main(String[] args) {
                ExampleComponent c = DaggerExampleComponent.builder().build();
                System.out.println(c.string().get());
            }
        }
        """;

    final String output =
        compileAndRunSourceCode(componentSourceCode, moduleSourceCode, appSourceCode).trim();

    assertEquals("hello", output);
  }

  /**
   * Verify that Dagger provides a non-@Nullable Lazy<String> binding when a module provides
   * a @Nullable String binding.
   */
  @Test
  public void givenNonNullableLazyOfStringDependencyAndNullableStringBinding_whenCompile_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import dagger.Lazy;
        import javax.annotation.Nullable;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            public Lazy<String> string();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import javax.annotation.Nullable;

        @Module
        public class ExampleModule {
            @Provides
            @Nullable
            public String provideString() {
                return null;
            }
        }
        """;

    final String appSourceCode = """
        public class ExampleApp {
            public static void main(String[] args) {
                ExampleComponent c = DaggerExampleComponent.builder().build();
                System.out.println(c.string().get());
            }
        }
        """;

    final String output =
        compileAndRunSourceCode(componentSourceCode, moduleSourceCode, appSourceCode).trim();

    assertEquals("null", output);
  }

  /**
   * Verify that Dagger does not support @Nullable annotations on Lazy type parameters.
   */
  @Test
  public void givenLazyOfNullableStringDependencyAndNullableStringBinding_whenCompileAndRun_thenCompileError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import dagger.Lazy;
        import javax.annotation.Nullable;

        @Component
        public interface ExampleComponent {
            public Lazy<@Nullable String> string();
        }
        """;

    final String errors = compileSourceCode(componentSourceCode).trim();

    assertEquals("annotation @javax.annotation.Nullable not applicable in this type context",
        errors);
  }
}
