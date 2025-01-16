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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import javax.inject.Provider;
import org.junit.jupiter.api.Test;

/**
 * Test assumptions about how {@link Provider} bindings work in Dagger.
 */
public class ProviderDaggerTest extends DaggerTestBase {
  /**
   * Verify that Dagger provides a Provider<String> binding when a module provides a String binding.
   */
  @Test
  public void givenProviderOfStringDependencyAndStringBinding_whenCompile_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.inject.Provider;

        @Component(modules = {ExampleModule.class})
        public interface ExampleComponent {
            public Provider<String> example();
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
   * Verify that Dagger provides a @Named("example") Provider<String> binding when a module provides
   * a @Named("example") String binding.
   */
  @Test
  public void givenQualifiedProviderOfStringDependencyAndQualifiedStringBinding_whenCompile_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.inject.Provider;
        import javax.inject.Named;

        @Component(modules = {ExampleModule.class})
        public interface ExampleComponent {
            @Named("example")
            public Provider<String> example();
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
   * Verify that Dagger does not allow qualifier annotations on Provider type parameters.
   */
  @Test
  public void givenProviderOfQualifiedStringDependency_whenCompile_thenCompileError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.inject.Provider;
        import javax.inject.Named;

        @Component
        public interface ExampleComponent {
            public Provider<@Named("example") String> example();
        }
        """;

    final String errors = compileSourceCode(componentSourceCode).trim();

    assertEquals("annotation @javax.inject.Named not applicable in this type context", errors);
  }

  /**
   * Verify that Dagger does not provide a @Named("example") Provider<String> binding when a module
   * provides only an unqualified String.
   */
  @Test
  public void givenQualifiedProviderOfStringDependencyAndUnqualifiedStringBinding_whenCompile_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.inject.Provider;
        import javax.inject.Named;

        @Component(modules = {ExampleModule.class})
        public interface ExampleComponent {
            @Named("example")
            public Provider<String> example();
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

    // This produces an error because the Provider is for a qualified type, but the module provides
    // only an unqualified type.
    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a missing binding error, but no missing binding error was found.");
  }

  /**
   * Verify that Dagger provides a @Nullable Provider<String> binding when a module provides a
   * 
   * @Nullable String binding.
   */
  @Test
  public void givenNullableProviderOfStringDependencyAndNullableStringBinding_whenCompileAndRun_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.inject.Provider;
        import javax.annotation.Nullable;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            @Nullable
            public Provider<String> string();
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
   * Verify that Dagger provides a @Nullable Provider<String> binding when a module provides a
   * non-@Nullable String binding.
   */
  @Test
  public void givenNullableProviderOfStringDependencyAndNonNullableStringBinding_whenCompileAndRun_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.inject.Provider;
        import javax.annotation.Nullable;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            @Nullable
            public Provider<String> string();
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
   * Verify that Dagger provides a non-@Nullable Provider<String> binding when a module provides
   * a @Nullable String binding.
   */
  @Test
  public void givenNonNullableProviderOfStringDependencyAndNullableStringBinding_whenCompile_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.inject.Provider;
        import javax.annotation.Nullable;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            public Provider<String> string();
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
   * Verify that Dagger does not support @Nullable annotations on Provider type parameters.
   */
  @Test
  public void givenProviderOfNullableStringDependencyAndNullableStringBinding_whenCompileAndRun_thenCompileError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.inject.Provider;
        import javax.annotation.Nullable;

        @Component
        public interface ExampleComponent {
            public Provider<@Nullable String> string();
        }
        """;

    final String errors = compileSourceCode(componentSourceCode).trim();

    assertEquals("annotation @javax.annotation.Nullable not applicable in this type context",
        errors);
  }

  /**
   * Verify that Dagger provides a Provider<? extends String> binding when a module provides a
   * String binding.
   */
  @Test
  public void givenProviderOfWildcardDependency_whenCompile_thenCompileError() throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.inject.Provider;

        @Component
        public interface ExampleComponent {
            public Provider<?> example();
        }
        """;

    final String errors = compileSourceCode(componentSourceCode).trim();

    assertEquals(
        "Dagger does not support injecting Provider<T>, Lazy<T>, Producer<T>, or Produced<T> when T is a wildcard type such as ?",
        errors);
  }

  /**
   * Verify that Dagger provides a Provider<? extends String> binding when a module provides a
   * String binding.
   */
  @Test
  public void givenProviderOfStringUpperBoundDependency_whenCompile_thenCompileError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.inject.Provider;

        @Component
        public interface ExampleComponent {
            public Provider<? extends String> example();
        }
        """;

    final String errors = compileSourceCode(componentSourceCode).trim();

    assertEquals(
        "Dagger does not support injecting Provider<T>, Lazy<T>, Producer<T>, or Produced<T> when T is a wildcard type such as ? extends java.lang.String",
        errors);
  }

  /**
   * Verify that Dagger provides a Provider<? super String> binding when a module provides a String
   * binding.
   */
  @Test
  public void givenProviderOfStringLowerBoundDependency_whenCompile_thenCompileError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.inject.Provider;

        @Component
        public interface ExampleComponent {
            public Provider<? super String> example();
        }
        """;

    final String errors = compileSourceCode(componentSourceCode).trim();

    assertEquals(
        "Dagger does not support injecting Provider<T>, Lazy<T>, Producer<T>, or Produced<T> when T is a wildcard type such as ? super java.lang.String",
        errors);
  }
}
