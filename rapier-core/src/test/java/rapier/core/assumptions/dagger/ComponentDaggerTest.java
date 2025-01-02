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
package rapier.core.assumptions.dagger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import dagger.Component;
import rapier.core.DaggerTestBase;

/**
 * Test assumptions about how {@link Component @Component}-annotated classes work
 */
public class ComponentDaggerTest extends DaggerTestBase {
  /**
   * Confirm that the only valid method types are: provision methods, members injection methods, and
   * subcomponent factory methods.
   */
  @Test
  public void givenCarefullyCraftedComponent_whenCompile_thenGetExpectedListOfMethodTypes()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public interface ExampleComponent {
            public String example(String foo, int bar);
        }
        """;

    // This method isn't a valid provision method, members injection method or subcomponent factory
    // method. Dagger cannot implement this method
    final String errors = compileSourceCode(componentSourceCode);

    assertEquals(
        "This method isn't a valid provision method, members injection method or subcomponent factory method. Dagger cannot implement this method",
        errors);
  }

  /**
   * Confirm that empty components are valid
   */
  @Test
  public void givenEmptyInterfaceComponent_whenCompiled_thenNoError() throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public interface ExampleComponent {
        }
        """;

    final String errors = compileSourceCode(componentSourceCode);

    assertTrue(errors.isBlank(), "Expected no errors, but errors were found.");
  }

  /**
   * Confirm that void-valued methods are not allowed in components.
   */
  @Test
  public void givenInterfaceComponentWithVoidValuedProvisionMethod_whenCompiled_thenIllegalArgumentException()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public interface ExampleComponent {
            public void example();
        }
        """;

    // This is an odd presentation. I would have expected the errors to come back as diagnostics,
    // but Dagger throws an uncaught exception instead. Whatever.
    final RuntimeException exception = assertThrowsExactly(RuntimeException.class, () -> {
      compileSourceCode(componentSourceCode);
    });

    final IllegalArgumentException cause = (IllegalArgumentException) exception.getCause();

    assertEquals("component method cannot be void: example()", cause.getMessage());
  }

  /**
   * Confirm that member injection methods do not create a dependency.
   */
  @Test
  public void givenInterfaceComponentWithMemberInjectionMethod_whenCompiled_thenIllegalArgumentException()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public interface ExampleComponent {
            public void exampleMemberInjectionMethod(String member);
        }
        """;

    // This is an odd presentation. I would have expected the errors to come back as diagnostics,
    // but Dagger throws an uncaught exception instead. Whatever.
    final String errors = compileSourceCode(componentSourceCode);

    assertTrue(errors.isBlank(), "Expected no errors, but errors were found.");
  }

  /**
   * Confirm that provision methods in an interface DO create a dependency.
   */
  @Test
  public void givenInterfaceComponentWithUnresolvedDependency_whenCompiled_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public interface ExampleComponent {
            String getMissingBinding();
        }
        """;

    final String errors = compileSourceCode(componentSourceCode);

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a MissingBinding error but it was not found.");
  }

  /**
   * Confirm that default methods in an interface DO NOT create a dependency.
   */
  @Test
  public void givenInterfaceComponentWithDefaultMethod_whenCompiled_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public interface ExampleComponent {
            default String defaultBindingTest() {
                return "default";
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode);

    assertTrue(errors.isBlank(), "Expected no errors, but errors were found.");
  }

  /**
   * Confirm that static methods in an interface DO NOT create a dependency.
   */
  @Test
  public void givenInterfaceComponentWithStaticMethod_whenCompiled_thenNoError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public interface ExampleComponent {
            public static String defaultBindingTest() {
                return "default";
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode);

    assertTrue(errors.isBlank(), "Expected no errors, but errors were found.");
  }

  /**
   * Confirm that instance methods in parent interfaces DO create a dependency.
   */
  @Test
  public void givenInterfaceComponentWithUnresolvedDependencyInParentInterface_whenCompiled_thenMissingBindingError()
      throws IOException {
    final String parentSourceCode = """
        public interface ExampleParent {
            public String getMissingBinding();
        }
        """;

    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public interface ExampleComponent extends ExampleParent{
        }
        """;

    final String errors = compileSourceCode(parentSourceCode, componentSourceCode);

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a MissingBinding error but it was not found.");
  }

  /**
   * Confirm that abstract class methods DO create a dependency.
   */
  @Test
  public void givenAbstractClassComponentWithUnresolvedDependency_whenCompiled_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public abstract class ExampleComponent {
            public abstract String getMissingBinding();
        }
        """;

    final String errors = compileSourceCode(componentSourceCode);

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a MissingBinding error but it was not found.");
  }

  /**
   * Confirm that non-default constructors fail to compile
   */
  @Test
  public void givenAbstractClassComponentWithoutDefaultConstructor_whenCompiled_thenConstructorError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public abstract class ExampleComponent {
            public ExampleComponent(String foo) {}
        }
        """;

    final String errors = compileSourceCode(componentSourceCode);

    assertTrue(errors.contains(
        "constructor ExampleComponent in class ExampleComponent cannot be applied to given types"),
        "Expected a constructor error but it was not found.");
  }

  /**
   * Confirm that non-default @Inject constructors fail to compile
   */
  @Test
  public void givenAbstractClassComponentWithInjectConstructor_whenCompiled_thenConstructorError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;
        import javax.inject.Inject;

        @Component
        public abstract class ExampleComponent {
            @Inject
            public ExampleComponent(String foo) {}
        }
        """;

    final String errors = compileSourceCode(componentSourceCode);

    assertTrue(errors.contains(
        // Ooh, judgy!
        "@Inject is nonsense on the constructor of an abstract class"),
        "Expected a constructor error but it was not found.");
  }

  /**
   * Confirm that abstract class methods in parents DO create a dependency.
   */
  @Test
  public void givenAbstractClassComponentWithUnresolvedDependencyInParent_whenCompiled_thenMissingBindingError()
      throws IOException {
    final String parentSourceCode = """
        import dagger.Component;

        public abstract class ExampleParent {
            public abstract String getMissingBinding();
        }
        """;

    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public abstract class ExampleComponent extends ExampleParent {
        }
        """;

    final String errors = compileSourceCode(parentSourceCode, componentSourceCode);

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a MissingBinding error but it was not found.");
  }

  /**
   * Confirm that creating a component from a non-abstract class fails to compile.
   */
  @Test
  public void givenClassComponent_whenCompiled_thenFails() throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public class ExampleComponent {
        }
        """;

    final String errors = compileSourceCode(componentSourceCode);

    assertTrue(errors.contains("@Component may only be applied to an interface or abstract class"),
        "Expected a component error but it was not found.");
  }
}
