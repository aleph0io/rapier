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

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import javax.inject.Provider;
import org.junit.jupiter.api.Test;

/**
 * Test assumptions about how {@link Provider} bindings work in Dagger.
 */
public class BoxingDaggerTest extends DaggerTestBase {
  /**
   * Verify that Dagger allows @Nullable int injection sites. Weird.
   */
  @Test
  public void givenNullableIntDependencyAndNullableIntegerBinding_whenCompile_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.annotation.Nullable;
        import javax.inject.Provider;

        @Component(modules = {ExampleModule.class})
        public interface ExampleComponent {
            @Nullable
            public int provisionInt();
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
            public Integer provideInteger() {
                return Integer.valueOf(42);
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode);

    assertTrue(errors.isBlank(), "Expected no errors, but errors were found.");
    
    // TODO After we refactor DaggerTestBase, verify no warnings here
  }

  /**
   * Verify that Dagger allows @Nullable int injection sites. Weird.
   */
  @Test
  public void givenNullableIntDependencyAndNonNullableIntegerBinding_whenCompile_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.annotation.Nullable;
        import javax.inject.Provider;

        @Component(modules = {ExampleModule.class})
        public interface ExampleComponent {
            @Nullable
            public int provisionInt();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleModule {
            @Provides
            public Integer provideInteger() {
                return Integer.valueOf(42);
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode);

    assertTrue(errors.isBlank(), "Expected no errors, but errors were found.");
  }

  /**
   * Verify that Dagger does not allow non-@Nullable int injection sites and @Nullable Integer
   * bindings.
   */
  @Test
  public void givenNonNullableIntDependencyAndNullableIntegerBinding_whenCompile_thenNullableError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.inject.Provider;

        @Component(modules = {ExampleModule.class})
        public interface ExampleComponent {
            public int provisionInt();
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
            public Integer provideInteger() {
                return Integer.valueOf(42);
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode);

    assertTrue(errors.contains("[Dagger/Nullable]"),
        "Expected a nullable error, but no nullable error was found.");
  }

  /**
   * Verify that Dagger fails with NPE for @Nullable int injection sites and @Nullable Integer when
   * null is returned, even thought Dagger allows this to compile.
   */
  @Test
  public void givenNullableIntDependencyAndNullableIntegerBinding_whenCompileAndRun_thenNullError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.inject.Provider;
        import javax.annotation.Nullable;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            @Nullable
            public int provisionInt();
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
            public Integer provideInteger() {
                return null;
            }
        }
        """;

    final String appSourceCode = """
        public class ExampleApp {
            public static void main(String[] args) {
                ExampleComponent c = DaggerExampleComponent.builder().build();
                System.out.println(c.provisionInt());
            }
        }
        """;

    assertThrowsExactly(NullPointerException.class,
        () -> compileAndRunSourceCode(componentSourceCode, moduleSourceCode, appSourceCode));
  }
}
