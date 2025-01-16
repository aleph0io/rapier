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
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Test assumptions about how {@link dagger.Module Module}-annotated classes work
 */
public class ModuleDaggerTest extends DaggerTestBase {
  /**
   * Confirm that modules and components work together as expected.
   */
  @Test
  public void givenComponentAndModule_whenCompiled_thenNoError() throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            String getRequiredBinding();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleModule {
            @Provides
            public String provideRequiredBinding() {
                return "value";
            }
        }
        """;


    final String appSourceCode = """
        public class ExampleApp {
            public static void main(String[] args) {
                ExampleComponent c = DaggerExampleComponent.builder().build();
                System.out.println(c.getRequiredBinding());
            }
        }
        """;

    final String output =
        compileAndRunSourceCode(componentSourceCode, moduleSourceCode, appSourceCode).trim();

    assertEquals(output, "value");
  }

  /**
   * Confirm that module parent classes provider methods are included in the dependency graph
   */
  @Test
  public void givenComponentAndModuleWithParent_whenCompiled_thenNoError() throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            String getRequiredBinding();
        }
        """;

    final String parentSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleParent {
            @Provides
            public String provideRequiredBinding() {
                return "value";
            }
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleModule extends ExampleParent {
        }
        """;

    final String appSourceCode = """
        public class ExampleApp {
            public static void main(String[] args) {
                ExampleComponent c = DaggerExampleComponent.builder().build();
                System.out.println(c.getRequiredBinding());
            }
        }
        """;

    final String output = compileAndRunSourceCode(componentSourceCode, parentSourceCode,
        moduleSourceCode, appSourceCode).trim();

    assertEquals(output, "value");
  }

  /**
   * Confirm that multiple "copies" of a parent module is an error, due to duplicate binding errors
   */
  @Test
  public void givenComponentAndTwoModuleWithSameParent_whenCompiled_thenDuplicateBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModuleA.class, ExampleModuleB.class})
        public interface ExampleComponent {
            String getRequiredBinding();
        }
        """;

    final String parentSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleParent {
            @Provides
            public String provideRequiredBinding() {
                return "value";
            }
        }
        """;

    final String moduleASourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleModuleA extends ExampleParent {
        }
        """;

    final String moduleBSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleModuleB extends ExampleParent {
        }
        """;

    final String appSourceCode = """
        public class ExampleApp {
            public static void main(String[] args) {
                ExampleComponent c = DaggerExampleComponent.builder().build();
                System.out.println(c.getRequiredBinding());
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, parentSourceCode,
        moduleASourceCode, moduleBSourceCode, appSourceCode).trim();

    assertTrue(errors.contains("[Dagger/DuplicateBindings]"),
        "Expected a DuplicateBinding error but it was not found.");

  }

  /**
   * Confirm that parameters to module provider methods introduce new dependencies.
   */
  @Test
  public void givenComponentAndModuleWithProviderMethodParameter_whenCompiled_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            String getRequiredBinding();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleModule {
            @Provides
            public String provideRequiredBinding(int missingBinding) {
                return "value";
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode);

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a MissingBinding error but it was not found.");
  }

  /**
   * Confirm that modules can provide method parameters for other modules
   */
  @Test
  public void givenComponentAndModuleAWithProviderMethodParameterAndModuleBWithProviderForMethodParameter_whenCompiled_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModuleA.class, ExampleModuleB.class})
        public interface ExampleComponent {
            String getRequiredBinding();
        }
        """;

    final String moduleASourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleModuleA {
            @Provides
            public String provideRequiredStringBinding(int requiredIntBinding) {
                return "value";
            }
        }
        """;

    final String moduleBSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleModuleB {
            @Provides
            public int provideRequiredIntBinding() {
                return 0;
            }
        }
        """;

    final String errors =
        compileSourceCode(componentSourceCode, moduleASourceCode, moduleBSourceCode);

    assertTrue(errors.isEmpty(), "Expected no errors, but errors were found.");
  }

  /**
   * Confirm that parameters in module constructors do not introduce new required dependencies
   */
  @Test
  public void givenComponentAndModuleWithConstructorParameter_whenCompiledAndRun_thenModuleNotSetRuntimeError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            String getRequiredBinding();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleModule {
            public ExampleModule(int missingBinding) {
            }

            @Provides
            public String provideRequiredBinding() {
                return "value";
            }
        }
        """;

    final String appSourceCode = """
        public class ExampleApp {
            public static void main(String[] args) {
                // Attempt to create the DaggerExampleComponent without providing ExampleModule
                DaggerExampleComponent.builder().build();
            }
        }
        """;

    final IllegalStateException problem = assertThrowsExactly(IllegalStateException.class, () -> {
      compileAndRunSourceCode(componentSourceCode, moduleSourceCode, appSourceCode);
    });

    assertEquals("ExampleModule must be set", problem.getMessage());
  }

  /**
   * Confirm that one module cannot provide constructor parameters for another module
   */
  @Test
  public void givenComponentAndModuleAWithConstructorParameterAndModuleBWithProviderForConstructorParameter_whenCompiledAndRun_thenModuleNotSetRuntimeError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModuleA.class, ExampleModuleB.class})
        public interface ExampleComponent {
            String getRequiredBinding();
        }
        """;

    final String moduleASourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import javax.inject.Inject;

        @Module
        public class ExampleModuleA {
            @Inject
            public ExampleModuleA(int requiredIntBinding) {
            }

            @Provides
            public String provideRequiredStringBinding() {
                return "value";
            }
        }
        """;

    final String moduleBSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleModuleB {
            @Provides
            public int provideRequiredIntBinding() {
                return 0;
            }
        }
        """;

    final String appSourceCode = """
        public class ExampleApp {
            public static void main(String[] args) {
                // Attempt to create the DaggerExampleComponent without providing ExampleModule
                DaggerExampleComponent.builder().build();
            }
        }
        """;

    final IllegalStateException problem = assertThrowsExactly(IllegalStateException.class, () -> {
      compileAndRunSourceCode(componentSourceCode, moduleASourceCode, moduleBSourceCode,
          appSourceCode);
    });

    assertEquals("ExampleModuleA must be set", problem.getMessage());
  }

  /**
   * Confirm that Dagger scans @Provides method parameters for JSR-330 annotations
   */
  @Test
  public void givenProvidesMethodWithUnresolvableParameter_whenCompiled_thenError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            String getRequiredBinding();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleModule {
            @Provides
            public String provideRequiredBinding(UnresolvableDependency dependency) {
                return dependency.toString();
            }
        }
        """;

    final String unresolvableDependencySourceCode = """
        import javax.inject.Inject;

        public class UnresolvableDependency {
            @Inject
            public UnresolvableDependency() {}
        }
        """;

    final String errors =
        compileSourceCode(componentSourceCode, moduleSourceCode, unresolvableDependencySourceCode)
            .trim();

    assertTrue(errors.isBlank(), "Expected no errors, but errors were found.");
  }

  /**
   * Confirm that Dagger performs JSR-330 injection on @Provides method parameters
   */
  @Test
  public void givenProvidesMethodWithParameterThatRequiresJsr330Injection_whenCompileAndRun_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            BravoDependency getRequiredBinding();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleModule {
            @Provides
            public String provideString() {
                return "value";
            }

            @Provides
            public BravoDependency provideRequiredBinding(AlphaDependency alpha) {
                return new BravoDependency(alpha);
            }
        }
        """;

    final String alphaDependencySourceCode = """
        import javax.inject.Inject;

        public class AlphaDependency {
            public final String value;

            @Inject
            public AlphaDependency(String value) {
                this.value = value;
            }
        }
        """;

    final String bravoDependencySourceCode = """
        public class BravoDependency {
            public final AlphaDependency alpha;

            public BravoDependency(AlphaDependency alpha) {
                this.alpha = alpha;
            }
        }
        """;

    final String appSourceCode = """
        public class ExampleApp {
            public static void main(String[] args) {
                // Attempt to create the DaggerExampleComponent without providing ExampleModule
                ExampleComponent component=DaggerExampleComponent.builder().build();
                BravoDependency bravo = component.getRequiredBinding();
                System.out.println(bravo.alpha.value);
            }
        }
        """;


    final String output = compileAndRunSourceCode(componentSourceCode, moduleSourceCode,
        alphaDependencySourceCode, bravoDependencySourceCode, appSourceCode).trim();

    assertEquals(output, "value");
  }
}
