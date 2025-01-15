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

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import dagger.Component;

/**
 * Test assumptions about how {@link Component @Component} dependencies classes work
 */
public class ComponentDependencyDaggerTest extends DaggerTestBase {
  /**
   * Confirm that components with dependencies compile as expected
   */
  @Test
  public void givenClassComponentWithDependency_whenCompiled_thenSucceeds() throws IOException {
    final String alphaComponentSourceCode = """
        import dagger.Component;

        @Component(
            dependencies = BravoComponent.class)
        public interface AlphaComponent {
        }
        """;

    final String bravoComponentSourceCode = """
        import dagger.Component;

        @Component
        public interface BravoComponent {
        }
        """;

    final String errors = compileSourceCode(alphaComponentSourceCode, bravoComponentSourceCode);

    assertTrue(errors.isEmpty(), "Expected no errors, but errors were found.");
  }

  /**
   * Confirm that a component dependency cannot be a class
   */
  @Test
  public void givenClassComponentWithNonComponentClassDependency_whenCompiled_thenFails()
      throws IOException {
    final String alphaComponentSourceCode = """
        import dagger.Component;

        @Component(
            dependencies = BravoComponent.class)
        public interface AlphaComponent {
        }
        """;

    final String bravoComponentSourceCode = """
        import dagger.Provides;
        
        public interface BravoComponent {
            public String provisionString();
        }
        """;

    final String errors = compileSourceCode(alphaComponentSourceCode, bravoComponentSourceCode);
    
    System.err.println(errors);

    assertTrue(errors.isEmpty(), "Expected no errors, but errors were found.");
  }

  /**
   * Confirm that components with circular dependencies are an error
   */
  @Test
  public void givenClassComponentWithCircularDependency_whenCompiled_thenFails()
      throws IOException {
    final String alphaComponentSourceCode = """
        import dagger.Component;

        @Component(
            dependencies = BravoComponent.class)
        public interface AlphaComponent {
        }
        """;

    final String bravoComponentSourceCode = """
        import dagger.Component;

        @Component(
            dependencies = AlphaComponent.class)
        public interface BravoComponent {
        }
        """;

    final String errors = compileSourceCode(alphaComponentSourceCode, bravoComponentSourceCode);

    assertTrue(errors.contains("AlphaComponent contains a cycle in its component dependencies"),
        "Expected circular dependency error for AlphaComponent, but none was found");
    assertTrue(errors.contains("BravoComponent contains a cycle in its component dependencies"),
        "Expected circular dependency error for BravoComponent, but none was found");
  }

  /**
   * Confirm that a component with multiple dependencies having duplicate bindings which match a
   * dependency in the top-level component fail to compile with a duplicate bindings error.
   */
  @Test
  public void givenComponentWithDuplicateBindingsInComponentDependenciesAndMatchingDependency_whenCompiled_thenFails()
      throws IOException {
    final String alphaComponentSourceCode = """
        import dagger.Component;

        @Component(
            dependencies = {
                BravoComponent.class,
                CharlieComponent.class
            })
        public interface AlphaComponent {
            public String provisionString();
        }
        """;

    final String bravoComponentSourceCode = """
        import dagger.Component;

        @Component(modules=BravoModule.class)
        public interface BravoComponent {
            public String provisionStringBravo();
        }
        """;

    final String bravoModuleSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class BravoModule {
            @Provides
            public String provideStringBravo() {
                return "bravo";
            }
        }
        """;

    final String charlieComponentSourceCode = """
        import dagger.Component;

        @Component(modules=CharlieModule.class)
        public interface CharlieComponent {
            public String provisionStringCharlie();
        }
        """;


    final String charlieModuleSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class CharlieModule {
            @Provides
            public String provideStringCharlie() {
                return "charlie";
            }
        }
        """;

    final String errors = compileSourceCode(alphaComponentSourceCode, bravoComponentSourceCode,
        bravoModuleSourceCode, charlieComponentSourceCode, charlieModuleSourceCode);

    assertTrue(errors.contains("[Dagger/DuplicateBindings]"),
        "Expected duplicate bindings error, but none was found");
  }

  /**
   * Confirm that a component with multiple dependencies having duplicate bindings which DO NOT
   * match a dependency in the top-level component compile successfully. In other words, component
   * dependencies can have duplicate bindings as long as the component does not provision against
   * the duplicate bindings.
   */
  @Test
  public void givenComponentWithDuplicateBindingsInComponentDependenciesAndNoMatchingDependency_whenCompiled_thenFails()
      throws IOException {
    final String alphaComponentSourceCode = """
        import dagger.Component;

        @Component(
            dependencies = {
                BravoComponent.class,
                CharlieComponent.class
            })
        public interface AlphaComponent {
            // We succeed if we don't provision against the duplicate bindings
            // public String provisionString();
        }
        """;

    final String bravoComponentSourceCode = """
        import dagger.Component;

        @Component(modules=BravoModule.class)
        public interface BravoComponent {
            public String provisionStringBravo();
        }
        """;

    final String bravoModuleSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class BravoModule {
            @Provides
            public String provideStringBravo() {
                return "bravo";
            }
        }
        """;

    final String charlieComponentSourceCode = """
        import dagger.Component;

        @Component(modules=CharlieModule.class)
        public interface CharlieComponent {
            public String provisionStringCharlie();
        }
        """;


    final String charlieModuleSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class CharlieModule {
            @Provides
            public String provideStringCharlie() {
                return "charlie";
            }
        }
        """;

    final String errors = compileSourceCode(alphaComponentSourceCode, bravoComponentSourceCode,
        bravoModuleSourceCode, charlieComponentSourceCode, charlieModuleSourceCode);

    assertTrue(errors.isEmpty(), "Expected no errors, but errors were found");
  }
}
