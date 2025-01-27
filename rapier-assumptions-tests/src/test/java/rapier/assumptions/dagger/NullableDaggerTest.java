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
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Test assumptions about how {@link Nullable} works in Dagger
 */
public class NullableDaggerTest extends DaggerTestBase {
  /**
   * Verify that Dagger supports binding a @Nullable String to a @Nullable String dependency.
   */
  @Test
  public void givenNullableStringDependencyAndNullableStringBinding_whenCompileAndRun_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.annotation.Nullable;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            @Nullable
            public String string();
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
                System.out.println(c.string());
            }
        }
        """;

    final String output =
        compileAndRunSourceCode(componentSourceCode, moduleSourceCode, appSourceCode).trim();

    assertEquals("null", output);
  }

  /**
   * Verify that Dagger supports binding a non-@Nullable String to a @Nullable String dependency.
   */
  @Test
  public void givenNullableStringDependencyAndNonNullableStringBinding_whenCompileAndRun_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.annotation.Nullable;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            @Nullable
            public String string();
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
                return "example";
            }
        }
        """;

    final String appSourceCode = """
        public class ExampleApp {
            public static void main(String[] args) {
                ExampleComponent c = DaggerExampleComponent.builder().build();
                System.out.println(c.string());
            }
        }
        """;

    final String output =
        compileAndRunSourceCode(componentSourceCode, moduleSourceCode, appSourceCode).trim();

    assertEquals("example", output);
  }

  /**
   * Verify that Dagger DOES NOT support binding a @Nullable String to a non-@Nullable String
   * dependency.
   */
  @Test
  public void givenNonNullableStringDependencyAndNullableStringBinding_whenCompile_thenNullableError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.annotation.Nullable;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            public String string();
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
                return "example";
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode).trim();

    assertTrue(errors.contains("[Dagger/Nullable]"),
        "Expected a nullable error, but no nullable error was found.");
  }

  /**
   * Verify that Dagger DOES NOT support Provider<@Nullable String>
   */
  @Test
  public void givenProviderOfNullableStringDependency_whenCompile_thenCompileError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.annotation.Nullable;
        import javax.inject.Provider;

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
   * Verify that Dagger DOES NOT support Lazy<@Nullable String>
   */
  @Test
  public void givenLazyOfNullableStringDependency_whenCompile_thenCompileError()
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
