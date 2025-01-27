/*-
 * =================================LICENSE_START==================================
 * rapier-assumptions-tests
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
package rapier.assumptions.dagger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import javax.inject.Provider;
import org.junit.jupiter.api.Test;

/**
 * Test assumptions about how {@link Provider} bindings work in Dagger.
 */
public class OptionalDaggerTest extends DaggerTestBase {
  /**
   * Verify that Dagger does not provide Optional<String> bindings when a module provides only a
   * String binding.
   */
  @Test
  public void givenOptionalOfStringDependencyAndStringBinding_whenCompile_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import java.util.Optional;

        @Component(modules = {ExampleModule.class})
        public interface ExampleComponent {
            public Optional<String> example();
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

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a missing binding error, but no missing binding error was found.");
  }

  /**
   * Verify that Dagger does not provide Optional<String> bindings when a module provides only a
   * String binding.
   */
  @Test
  public void givenOptionalOfStringDependencyAndNullableStringBinding_whenCompile_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import java.util.Optional;

        @Component(modules = {ExampleModule.class})
        public interface ExampleComponent {
            public Optional<String> example();
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
            public String example() {
                return "example";
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode);

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a missing binding error, but no missing binding error was found.");
  }

  /**
   * Verify that Dagger provides a @Nullable Provider<String> binding when a module provides a
   * 
   * @Nullable String binding.
   */
  @Test
  public void givenOptionalOfStringDependencyAndOptionalOfStringBinding_whenCompileAndRun_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import java.util.Optional;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            public Optional<String> string();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import java.util.Optional;
        import javax.annotation.Nullable;

        @Module
        public class ExampleModule {
            @Provides
            public Optional<String> provideString() {
                return Optional.of("example");
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

    assertEquals("example", output);
  }
}
