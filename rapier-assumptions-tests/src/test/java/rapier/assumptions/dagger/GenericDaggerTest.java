/*-
 * =================================LICENSE_START==================================
 * rapier-processor-core
 * ====================================SECTION=====================================
 * Copyright (C) 2024 - 2025 Andy Boothe
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
import org.junit.jupiter.api.Test;

public class GenericDaggerTest extends DaggerTestBase {
  /**
   * Confirm that creating a component from a generic interface succeeds
   */
  @Test
  public void givenGenericInterfaceComponent_whenCompiled_thenNoError() throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public interface ExampleComponent<T> {
        }
        """;

    final String errors = compileSourceCode(componentSourceCode);

    assertTrue(errors.isBlank(), "Expected no errors, but errors were found");
  }

  /**
   * Confirm that creating a component from a generic interface with a provision method for the
   * generic type parameter gets a missing binding error.
   */
  @Test
  public void givenGenericInterfaceComponentWithUnboundDependency_whenCompiled_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public interface ExampleComponent<T> {
            public T provisionT();
        }
        """;

    final String errors = compileSourceCode(componentSourceCode);

    // This one is surprising. I don't even know how one could provision this. It wouldn't work
    // through modules.
    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a MissingBinding error but it was not found.");
  }

  /**
   * Confirm that generic modules are not allowed
   */
  @Test
  public void givenGenericModule_whenCompiled_thenCompileError() throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.annotation.Nullable;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent<T> {
            @Nullable
            public T provisionT();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import javax.annotation.Nullable;

        @Module
        public class ExampleModule<T> {
            @Provides
            @Nullable
            public T provideT() {
                return null;
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode);

    // This one is surprising. I don't even know how one could provision this. It wouldn't work
    // through modules.
    assertTrue(errors.contains("Modules with type parameters must be abstract"),
        "Expected a compile error but it was not found.");
    assertTrue(errors.contains("ExampleModule is listed as a module, but has type parameters"),
        "Expected a compile error but it was not found.");
  }

  /**
   * Confirm that a component that provisions a wildcard lower bound can be satisfied by a module
   * that provides a wildcard lower bound.
   */
  @Test
  public void givenComponentProvisioningWildcardLowerBoundAndModuleProvidingWildcardLowerBound_whenCompileAndRun_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import java.util.List;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent<T> {
            public List<? extends String> provisionListOfString();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import java.util.ArrayList;
        import java.util.List;

        @Module
        public class ExampleModule {
            @Provides
            public List<? extends String> provideListOfString() {
                List<String> list = new ArrayList<>();
                list.add("example");
                return list;
            }
        }
        """;

    final String appSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import java.util.ArrayList;
        import java.util.List;

        public class App {
            public static void main(String[] args) {
                ExampleComponent component = DaggerExampleComponent.builder().build();
                System.out.println(component.provisionListOfString());
            }
        }
        """;

    final String output =
        compileAndRunSourceCode(componentSourceCode, moduleSourceCode, appSourceCode).trim();

    assertEquals("[example]", output);
  }

  /**
   * Confirm that a component that provisions a wildcard lower bound cannot be satisfied by a module
   * that provides a wildcard upper bound.
   */
  @Test
  public void givenComponentProvisioningWildcardLowerBoundAndModuleProvidingWildcardUpperBound_whenCompile_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import java.util.List;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent<T> {
            public List<? extends String> provisionListOfString();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import java.util.ArrayList;
        import java.util.List;

        @Module
        public class ExampleModule {
            @Provides
            public List<? super String> provideListOfString() {
                List<String> list = new ArrayList<>();
                list.add("example");
                return list;
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode).trim();

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a missing binding error, but no missing binding error was found.");
  }

  /**
   * Confirm that a component that provisions a wildcard upper bound can be satisfied by a module
   * that provides a wildcard upper bound.
   */
  @Test
  public void givenComponentProvisioningWildcardUpperBoundAndModuleProvidingWildcardUpperBound_whenCompileAndRun_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import java.util.List;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent<T> {
            public List<? super String> provisionListOfString();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import java.util.ArrayList;
        import java.util.List;

        @Module
        public class ExampleModule {
            @Provides
            public List<? super String> provideListOfString() {
                List<String> list = new ArrayList<>();
                list.add("example");
                return list;
            }
        }
        """;

    final String appSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import java.util.ArrayList;
        import java.util.List;

        public class App {
            public static void main(String[] args) {
                ExampleComponent component = DaggerExampleComponent.builder().build();
                System.out.println(component.provisionListOfString());
            }
        }
        """;

    final String output =
        compileAndRunSourceCode(componentSourceCode, moduleSourceCode, appSourceCode).trim();

    assertEquals("[example]", output);
  }

  /**
   * Confirm that a component that provisions a wildcard lower bound cannot be satisfied by a module
   * that provides a wildcard upper bound.
   */
  @Test
  public void givenComponentProvisioningWildcardUpperBoundAndModuleProvidingWildcardLowerBound_whenCompile_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import java.util.List;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent<T> {
            public List<? super String> provisionListOfString();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import java.util.ArrayList;
        import java.util.List;

        @Module
        public class ExampleModule {
            @Provides
            public List<? extends String> provideListOfString() {
                List<String> list = new ArrayList<>();
                list.add("example");
                return list;
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode).trim();

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a missing binding error, but no missing binding error was found.");
  }

  /**
   * Confirm that a component that provisions a wildcard upper bound cannot be satisfied by a module
   * that provides an exact type.
   */
  @Test
  public void givenComponentProvisioningWildcardLowerBoundAndModuleProvidingString_whenCompile_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import java.util.List;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent<T> {
            public List<? extends String> provisionListOfString();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import java.util.ArrayList;
        import java.util.List;

        @Module
        public class ExampleModule {
            @Provides
            public List<String> provideListOfString() {
                List<String> list = new ArrayList<>();
                list.add("example");
                return list;
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode).trim();

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a missing binding error, but no missing binding error was found.");
  }

  /**
   * Confirm that a component that provisions a wildcard upper bound cannot be satisfied by a module
   * that provides an exact type.
   */
  @Test
  public void givenComponentProvisioningWildcardUpperBoundAndModuleProvidingString_whenCompile_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import java.util.List;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent<T> {
            public List<? super String> provisionListOfString();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import java.util.ArrayList;
        import java.util.List;

        @Module
        public class ExampleModule {
            @Provides
            public List<String> provideListOfString() {
                List<String> list = new ArrayList<>();
                list.add("example");
                return list;
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode).trim();

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a missing binding error, but no missing binding error was found.");
  }

  /**
   * Confirm that a component that provisions a wildcard upper bound cannot be satisfied by a module
   * that provides an exact type.
   */
  @Test
  public void givenComponentProvisioningStringAndModuleProvidingWildcardUpperBound_whenCompile_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import java.util.List;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent<T> {
            public List<String> provisionListOfString();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import java.util.ArrayList;
        import java.util.List;

        @Module
        public class ExampleModule {
            @Provides
            public List<? extends String> provideListOfString() {
                List<String> list = new ArrayList<>();
                list.add("example");
                return list;
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode).trim();

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a missing binding error, but no missing binding error was found.");
  }

  /**
   * Confirm that a component that provisions a wildcard upper bound cannot be satisfied by a module
   * that provides an exact type.
   */
  @Test
  public void givenComponentProvisioningStringAndModuleProvidingWildcardLowerBound_whenCompile_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import java.util.List;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent<T> {
            public List<String> provisionListOfString();
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;
        import java.util.ArrayList;
        import java.util.List;

        @Module
        public class ExampleModule {
            @Provides
            public List<? super String> provideListOfString() {
                List<String> list = new ArrayList<>();
                list.add("example");
                return list;
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, moduleSourceCode).trim();

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a missing binding error, but no missing binding error was found.");
  }
}
