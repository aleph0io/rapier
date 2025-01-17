# rapier-environment-variable

This module (and its sister module, rapier-environment-variable-compiler) provide code generation for fetching environment variables using [Dagger](https://dagger.dev/).

## Overview

To generate code to fetch environment variables in Dagger, use the `@EnvironmentVariable` annotation. For example:

    @Component(modules = {
        // Note that we refer to the generated module here, by name
        RapierExampleComponentEnvironmentVariableModule.class
    })
    public interface ExampleComponent {
        /**
         * Get timeout in milliseconds from environment variable TIMEOUT, or use the
         * default of 30000 if not present
         */
        @EnvironmentVariable(value="TIMEOUT", defaultValue="30000")
        public long timeout();
    }

The above `ExampleComponent` component would generate the `RapierExampleComponentEnvironmentVariableModule` module referenced in the code. The generated code would look like this:

    @Module
    public class RapierExampleComponentEnvironmentVariableModule {
        // NOTE: Code is left out for simplicity
    
        @Provides
        @EnvironmentVariable(value="TIMEOUT", defaultValue="30000")
        public String provideEnvironmentVariableTimelineAsString() {
            final String name="TIMEOUT";
            final String value=env.get(name);
            if (value == null)
                return "30000";
            return value;
        }
        
        @Provides
        @EnvironmentVariable(value="TIMEOUT", defaultValue="30000")
        public java.lang.Long provideEnvironmentVariableTimelineAsLong(
                @EnvironmentVariable(value="TIMELINE", defaultValue="30000") String value) {
            return java.lang.Long.valueOf(value);
        }
    }

Note a few things about the generated code:

* It looks for the named `TIMEOUT` environment variable, and provides that value if present, or else the default value of `"30000"` otherwise
* It converts the `String` value to a `Long`, since that's what the user requested

## Quickstart

First, add Rapier and Dagger to your build. This involves adding dependencies, annotation processors, and a generated source folder. You can add as many Rapier dependencies and annotation processors to your build as you'd like, but all the Rapier annotation processors run before the Dagger annotation processor.

If using Maven, add this to your `pom.xml`:

    <properties>
      <rapier.version>0.0.0-b0</rapier.version>
      <dagger.version>2.52</dagger.version>
    </properties>
    
    <build>
      <plugins>
        <!-- Configuration compilation -->
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-compiler-plugin</artifactId>
          <configuration>
            <!-- Rapier requires at least Java 11, just like Dagger. -->
            <source>11</source>
            <target>11</target>
            <annotationProcessorPaths>
              <!-- Rapier environment variable annotation processor -->
              <path>
                <groupId>com.sigpwned</groupId>
                <artifactId>rapier-environment-variable-compiler</artifactId>
                <version>${rapier.version}</version>
              </path>
                    
              <!-- Dagger annotation processor -->
              <path>
                <groupId>com.google.dagger</groupId>
                <artifactId>dagger-compiler</artifactId>
                <version>${dagger.version}</version>
              </path>
            </annotationProcessorPaths>
          </configuration>
        </plugin>
        
        <!-- Add generated source code to build -->
        <plugin>
          <groupId>org.codehaus.mojo</groupId>
          <artifactId>build-helper-maven-plugin</artifactId>
          <executions>
            <execution>
              <id>add-generated-sources</id>
              <phase>generate-sources</phase>
              <goals>
                <goal>add-source</goal>
              </goals>
              <configuration>
                <sources>
                  <source>${project.build.directory}/generated-sources/annotations</source>
                </sources>
              </configuration>
            </execution>
          </executions>
        </plugin>
      </plugins>
    </build>
    
    <dependencies>
        <dependency>
            <groupId>com.sigpwned</groupId>
            <artifactId>rapier-environment-variable</artifactId>
            <version>${repier.version}</version>
        </dependency>
        <dependency>
            <groupId>com.sigpwned</groupId>
            <artifactId>rapier-environment-variable-compiler</artifactId>
            <version>${repier.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.google.dagger</groupId>
            <artifactId>dagger</artifactId>
            <version>${dagger.version}</version>
        </dependency>
        <dependency>
            <groupId>com.google.dagger</groupId>
            <artifactId>dagger-compiler</artifactId>
            <version>${dagger.version}</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

Next, define a component class that uses the `@EnvironmentVariable` annotation to create a dependency on an environment variable:

    @Component(modules = {
        // Note that we refer to the generated module here, by name
        RapierExampleComponentEnvironmentVariableModule.class
    })
    public interface ExampleComponent {
        /**
         * Get timeout in milliseconds from environment variable TIMEOUT, or use the
         * default of 30000 if not present
         */
        @EnvironmentVariable(value="TIMEOUT", defaultValue="30000")
        public long timeout();
    }
    
Rapier analyzes dependencies on the component level and generates a module for each component in the same package as the component. Therefore, the above will generate a new module, `RapierExampleComponentEnvironmentVariableModule`, for `ExampleComponent` in the same package. Note that the example component references the generated module by name. Generating the module isn't useful if it isn't added to the component!

Now create and use the component in your code like any other Dagger component:

    public static void main(String[] args) {
        final ExampleComponent component = DaggerExampleComponent.builder()
            .build();
        System.out.println(component.timeout());
    }

## Provisioning sites

Rapier is designed to integrate with Dagger seamlessly and supports provisioning environment variables in most Dagger-supported injection sites. For example, the below shows how `@EnvironmentVariable` can be used to provision configuration data in modules.

    @Component(modules = {
      ServerModule.class,
      RapierExampleComponentEnvironmentVariableModule.class })
    public class ExampleComponent {
      public Server server();
    }

    @Module(includes = {DataStoreModule.class})
    public class ServerModule {
      @Provides
      public Server getServer(
          @EnvironmentVariable(value = "SERVER_PORT", defaultValue = "7070") int port,
          DataStore dataStore) {
        return new ExampleServer(port, dataStore);
      }
    }
    
    @Module
    public class DataStoreModule {
      @Provides
      public DataStore getDataStore(
          @EnvironmentVariable(value = "DATABASE_HOST", defaultValue = "localhost") String host,
          @EnvironmentVariable(value = "DATABASE_PORT", defaultValue = "5432") int port) {
        return new ExampleDataStore(host, port);
      }
    }

Rapier supports the following Dagger features for environment variable discovery:

* Component-module dependencies (i.e., `@Component(modules)`)
* Component-component dependencies (i.e., `@Component(dependencies)`)
* Module-module dependencies (i.e., `@Module(includes)`)
* Component provision methods
* Module provide methods (i.e., `@Provider` methods without parameters)
* Module factory methods (i.e., `@Provider` methods with parameters)
* JSR 330-style `@Inject` constructors
* JSR 330-style `@Inject` methods
* JSR 330-style `@Inject` fields
* `Lazy<T>`
* `Provider<T>`

For more information about Rapier's injection site discovery, see [the Rapier wiki](https://github.com/aleph0io/rapier/wiki/Injection-site-discovery).

## Customizing the code

The environment variable module allows a few ways to customize the generated code:

### Required environment variables

By default, environment variables are required. This causes Rapier to generate code that will provide the value of the named environment variable if present, or else throw an `IllegalStateException` during initialization otherwise.

    /**
     * This environment variable is required. Rapier will provide the value of the
     * environment variable if present, or else throw an IllegalStateException
     * otherwise.
     */
    @EnvironmentVariable("FOO")
    public String foo();

### Optional environment variables

If the value of an environment variable is optional, then annotating the injection site with `@Nullable` will cause Rapier to generate code that will provide the value of the named environment variable if present, or else `null` otherwise.

    /**
     * This environment variable is optional. Rapier will provide the value of the
     * environment variable if present, or else null otherwise.
     */
    @Nullable
    @EnvironmentVariable("FOO")
    public String foo();
    
### Defaulted environment variables

Including a default value in the `@EnvironmentVariable` annotation will cause Rapier to generate code that will provide the value of the named environment variable if present, or else the given default value otherwise. No `@Nullable` annotation is required.

    /**
     * This environment variable is optional. Rapier will provide the value of the
     * environment variable if present, or else "BAR" otherwise.
     */
    @EnvironmentVariable(value="FOO", defaultValue="BAR")
    public String foo();

## Effective requiredness

Rapier scans all the references to each unique environment variable and determines if each is "effectively required." For example, in the following example:

    /**
     * This reference is required.
     */
    @EnvironmentVariable("FOO")
    public String fooRequired();
    
    /**
     * This reference has a default value, so is optional.
     */
    @EnvironmentVariable(value="FOO", defaultValue="BAR")
    public String fooWithDefaultValue();
    
    /**
     * This reference is @Nullable, so is optional.
     */
    @Nullable
    @EnvironmentVariable("FOO")
    public Long fooOptional();
    

the `FOO` environment variable is effectively required because one of the provision methods (`fooRequired`) requires it to be present, or else an `IllegalStateException` will be thrown. Since `FOO` is required in one place, it is logically required in *all* places. Rapier issues warnings when effectively required parameters are treated as optional. For example, the above code would generate the following warnings:

* `Effectively required environment variable FOO is treated as nullable`
* `Effectively required environment variable FOO has default value`

To address this warning, either:

* Make all required reference(s) optional by either adding `@Nullable` or adding a default value
* Make all optional reference(s) required by removing `@Nullable` and default values

## Type conversions

The environment variable module provides `String` values by default, but also supports a variety of built-in and extensible type conversions as well. For example, the following types are supported:

* Primitives (i.e., `byte`, `short`, `int`, `long`, `char`, `float`, `double`, `boolean`)
* Boxed primitives (i.e., `Byte`, `Short`, `Integer`, `Long`, `Character`, `Float`, `Double`, `Boolean`)
* Types `T` with a method `public static T fromString(String)`
* Types `T` with a method `public static T valueOf(String)`
* types `T` with a constructor `public T(String)`

For more information about type conversions, see [the Rapier wiki](https://github.com/aleph0io/rapier/wiki/Type-conversions).

## Name templating

The environment variable module supports simple environment variable and system property templating in the `@EnvironmentVariable(value)` field, which contains the name of the environment variable to provide. For example:

    /**
     * If the environment variable "STAGE" has the value "PROD", then this provisions
     * the environment variable "FOO_PROD". If the environment variable "STAGE" is not
     * present, then the generated code would throw an `IllegalStateException` on
     * initialization.
     */
    @EnvironmentVariable("FOO_${env.STAGE}")
    public String foo();

The environment variable module supports the following syntax for name templates:

* `${env.NAME}` - Replace with the value of the `NAME` environment variable if present, or else throw `IllegalStateException`
* `${env.NAME:-default}` - Replace with the value of the `NAME` environment variable if present, or else use the value `"default"`
* `${sys.name}` - Replace with the value of the `name` system property if present, or else throw `IllegalStateException`
* `${sys.name:-default}` - Replace with the value of the `name` system property if present, or else use the value `"default"`

For more information about name templating, see [the Rapier wiki](https://github.com/aleph0io/rapier/wiki/Name-templating).

## Testing with Rapier

Rapier is designed with testing in mind.

### Providing custom environment variable values

To cause Rapier to provide custom environment variables during a test, use the single-argument test constructor:

    final ExampleComponent component = DaggerExampleComponent.builder()
        .rapierExampleComponentEnvironmentVariableModule(
            new RapierExampleComponentEnvironmentVariableModule(Map.of(
                "FOO", "BAR",
                "ALPHA", "BRAVO")))
        .build();
        
The above component will use the given values for environment variables (as opposed to `System.getenv()`) when expanding name templates and providing environment variable values.
        
### Providing custom system property values

To cause Rapier to provide custom values for environment variables *and* system properties during a test, use the double-argument test constructor:

    final Map<String, String> customEnvironmentVariables = Map.of(
        "FOO", "BAR",
        "ALPHA", "BRAVO");
    final Map<String, String> customSystemProperties = Map.of(
        "com.example.foo", "bar",
        "com.example.alpha", "bravo");
    final ExampleComponent component = DaggerExampleComponent.builder()
        .rapierExampleComponentEnvironmentVariableModule(
            new RapierExampleComponentEnvironmentVariableModule(
                customEnvironmentVariables,
                customSystemProperties)))
        .build();
        
The given environment variables will be used when expanding name templates and providing environment variable values. The given system properties will only be used when expanding name templates.
