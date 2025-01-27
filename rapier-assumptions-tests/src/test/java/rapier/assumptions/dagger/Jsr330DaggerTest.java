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
import org.junit.jupiter.api.Test;

/**
 * Test assumptions about how Dagger uses JSR-330 annotations
 */
public class Jsr330DaggerTest extends DaggerTestBase {
  /**
   * Confirm that Dagger will generate a MissingBinding error when a dependency has a default
   * constructor without an @Inject annotation and no @Provides method
   */
  @Test
  public void givenComponentWithDependencyOfDefaultConstructorWithoutInjectAnnotation_whenCompiled_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public interface ExampleComponent {
            Foo getFoo();
        }
        """;

    final String fooSourceCode = """
        public class Foo {
            /**
             * Default constructor without an @Inject annotation
             */
            public Foo() {
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, fooSourceCode).trim();

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a MissingBinding error but it was not found.");
  }

  /**
   * Confirm that Dagger will succeed when a dependency has a default constructor with an @Inject
   * annotation and no @Provides method
   */
  @Test
  public void givenComponentWithDependencyOfDefaultConstructorWithInjectAnnotation_whenCompiled_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component
        public interface ExampleComponent {
            Foo getFoo();
        }
        """;

    final String fooSourceCode = """
        import javax.inject.Inject;

        public class Foo {
            /**
             * Default constructor with an @Inject annotation
             */
            @Inject
            public Foo() {
                // default constructor
            }
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, fooSourceCode).trim();

    assertTrue(errors.isBlank(), "Expected no errors, but errors were found.");
  }

  /**
   * Confirm that Dagger will succeed when a dependency has a default constructor with an @Inject
   * annotation and no @Provides method, and will prioritize the module provider
   */
  @Test
  public void givenComponentWithInjectProviderAndModuleProvider_whenCompiledAndRun_thenModuleProviderIsUsed()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            Foo getFoo();
        }
        """;

    final String fooSourceCode = """
        import javax.inject.Inject;

        public class Foo {
            public final String value;

            /**
             * Default constructor with an @Inject annotation
             */
            @Inject
            public Foo() {
                this("constructor");
            }

            public Foo(String value) {
                this.value = value;
            }
        }
        """;

    final String moduleSourceCode = """
        import dagger.Module;
        import dagger.Provides;

        @Module
        public class ExampleModule {
            @Provides
            public Foo provideFoo() {
                return new Foo("module");
            }
        }
        """;

    final String appSourceCode = """
        public class ExampleApp {
            public static void main(String[] args) {
                ExampleComponent c = DaggerExampleComponent.builder().build();
                System.out.println(c.getFoo().value);
            }
        }
        """;

    final String output =
        compileAndRunSourceCode(componentSourceCode, fooSourceCode, moduleSourceCode, appSourceCode)
            .trim();

    assertEquals("module", output);
  }

  /**
   * Confirm that Dagger DOES NOT support injection into private fields, methods, or constructors
   */
  @Test
  public void givenComponentWithPrivateInjectionSites_whenCompile_thenPrivateInjectionSiteErrors()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            Foo getFoo();
        }
        """;

    final String fooSourceCode = """
        import javax.inject.Inject;

        public class Foo {
            @Inject
            private String directField;

            private String constructorField;

            private String methodField;

            /**
             * Default constructor with an @Inject annotation
             */
            @Inject
            private Foo(String value) {
                this.constructorField = value;
            }

            @Inject
            private void setMethodField(String value) {
                this.methodField = value;
            }

            public String getDirectField() {
                return directField;
            }

            public String getConstructorField() {
                return constructorField;
            }

            public String getMethodField() {
                return methodField;
            }
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
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, fooSourceCode, moduleSourceCode);

    assertTrue(errors.contains("Dagger does not support injection into private fields"),
        "Expected a private field injection error but it was not found.");
    assertTrue(errors.contains("Dagger does not support injection into private methods"),
        "Expected a private method injection error but it was not found.");
    assertTrue(errors.contains("Dagger does not support injection into private constructors"),
        "Expected a private constructor injection error but it was not found.");
  }

  /**
   * Confirm that Dagger DOES support injection into package fields, methods, or constructors
   */
  @Test
  public void givenComponentWithPackageInjectionSites_whenCompile_thenInjectionSitesArePopulated()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            Foo getFoo();
        }
        """;

    final String fooSourceCode = """
        import javax.inject.Inject;

        public class Foo {
            @Inject
            /* package */ String directField;

            private String constructorField;

            private String methodField;

            /**
             * Default constructor with an @Inject annotation
             */
            @Inject
            /* package */ Foo(String value) {
                this.constructorField = value;
            }

            @Inject
            /* package */ void setMethodField(String value) {
                this.methodField = value;
            }

            public String getDirectField() {
                return directField;
            }

            public String getConstructorField() {
                return constructorField;
            }

            public String getMethodField() {
                return methodField;
            }
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
        }
        """;

    final String appSourceCode =
        """
            public class ExampleApp {
                public static void main(String[] args) {
                    ExampleComponent c = DaggerExampleComponent.builder().build();
                    System.out.println(c.getFoo().getDirectField() + "." + c.getFoo().getConstructorField() + "." + c.getFoo().getMethodField());
                }
            }
            """;

    final String output =
        compileAndRunSourceCode(componentSourceCode, fooSourceCode, moduleSourceCode, appSourceCode)
            .trim();

    assertEquals("value.value.value", output);
  }

  /**
   * Confirm that Dagger DOES support injection into protected fields, methods, or constructors
   */
  @Test
  public void givenComponentWithProtectedInjectionSites_whenCompile_thenInjectionSitesArePopulated()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            Foo getFoo();
        }
        """;

    final String fooSourceCode = """
        import javax.inject.Inject;

        public class Foo {
            @Inject
            protected String directField;

            private String constructorField;

            private String methodField;

            /**
             * Default constructor with an @Inject annotation
             */
            @Inject
            protected Foo(String value) {
                this.constructorField = value;
            }

            @Inject
            protected void setMethodField(String value) {
                this.methodField = value;
            }

            public String getDirectField() {
                return directField;
            }

            public String getConstructorField() {
                return constructorField;
            }

            public String getMethodField() {
                return methodField;
            }
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
        }
        """;

    final String appSourceCode =
        """
            public class ExampleApp {
                public static void main(String[] args) {
                    ExampleComponent c = DaggerExampleComponent.builder().build();
                    System.out.println(c.getFoo().getDirectField() + "." + c.getFoo().getConstructorField() + "." + c.getFoo().getMethodField());
                }
            }
            """;

    final String output =
        compileAndRunSourceCode(componentSourceCode, fooSourceCode, moduleSourceCode, appSourceCode)
            .trim();

    assertEquals("value.value.value", output);
  }

  /**
   * Confirm that Dagger DOES support injection into protected fields, methods, or constructors
   */
  @Test
  public void givenComponentWithPublicInjectionSites_whenCompile_thenInjectionSitesArePopulated()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            Foo getFoo();
        }
        """;

    final String fooSourceCode = """
        import javax.inject.Inject;

        public class Foo {
            @Inject
            public String directField;

            private String constructorField;

            private String methodField;

            /**
             * Default constructor with an @Inject annotation
             */
            @Inject
            public Foo(String value) {
                this.constructorField = value;
            }

            @Inject
            public void setMethodField(String value) {
                this.methodField = value;
            }

            public String getDirectField() {
                return directField;
            }

            public String getConstructorField() {
                return constructorField;
            }

            public String getMethodField() {
                return methodField;
            }
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
        }
        """;

    final String appSourceCode =
        """
            public class ExampleApp {
                public static void main(String[] args) {
                    ExampleComponent c = DaggerExampleComponent.builder().build();
                    System.out.println(c.getFoo().getDirectField() + "." + c.getFoo().getConstructorField() + "." + c.getFoo().getMethodField());
                }
            }
            """;

    final String output =
        compileAndRunSourceCode(componentSourceCode, fooSourceCode, moduleSourceCode, appSourceCode)
            .trim();

    assertEquals("value.value.value", output);
  }

  /**
   * Confirm that Dagger DOES NOT support injection into static fields
   */
  @Test
  public void givenComponentWithStaticFieldInjectionSite_whenCompile_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            Foo getFoo();
        }
        """;

    final String fooSourceCode = """
        import javax.inject.Inject;

        public class Foo {
            @Inject
            public static String staticField;

            /**
             * Default constructor with an @Inject annotation
             */
            @Inject
            public Foo() {
            }
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
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, fooSourceCode, moduleSourceCode);

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a MissingBinding error but it was not found.");
  }


  /**
   * Confirm that Dagger DOES NOT support injection into static methods
   */
  @Test
  public void givenComponentWithStaticMethodInjectionSite_whenCompile_thenMissingBindingError()
      throws IOException {
    final String componentSourceCode = """
        import dagger.Component;

        @Component(modules={ExampleModule.class})
        public interface ExampleComponent {
            Foo getFoo();
        }
        """;

    final String fooSourceCode = """
        import javax.inject.Inject;

        public class Foo {
            private static String staticMethod;
            
            @Inject
            public static void setStaticMethod(String value) {
                staticMethod = value;
            }
            
            public static String getStaticMethod() {
                return staticMethod;
            }

            /**
             * Default constructor with an @Inject annotation
             */
            @Inject
            public Foo() {
            }
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
        }
        """;

    final String errors = compileSourceCode(componentSourceCode, fooSourceCode, moduleSourceCode);

    assertTrue(errors.contains("[Dagger/MissingBinding]"),
        "Expected a MissingBinding error but it was not found.");
  }
}
