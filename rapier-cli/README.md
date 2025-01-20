# rapier-cli

This module (and its sister module, rapier-cli-compiler) provide code generation for parsing CLI arguments using [Dagger](https://dagger.dev/).

## Overview

To generate code to parse CLI arguments in Dagger, use the `@CliPositionalParameter`, `@CliOptionParameter`, and `@CliFlagParameter` annotations. For example:

    /**
     * CLI arguments for an imaginary CLI tool to fetch the contents of a URL.
     */
    @Component(modules = {
        // Note that we refer to the generated module here, by name
        RapierExampleComponentCliModule.class
    })
    public interface ExampleComponent {
        /**
         * The timeout for fetching the URL
         */
        @CliOptionParameter(shortName='t', longName="timeout", defaultValue="30000")
        public long timeout();
        
        @CliFlagParameter(
            positiveShortName='f', positiveLongName="follow-redirects",
            negativeShortName='F', negativeLongName="no-follow-redirects",
            defaultValue=CliFlagParameterValue.TRUE)
        public boolean followRedirects();
    
        /**
         * The URL to fetch
         */
        @CliPositionalParameter(0)
        public URL url();
    }

The above `ExampleComponent` component would generate the `RapierExampleComponentCliModule` module referenced in the code. The generated code would look like this:

    @Module
    public class RapierExampleComponentCliModule {
        // NOTE: Code is left out for simplicity
        
        public RapierExampleComponentCliModule(String[] args) {
            // CLI argument parsing logic...
        }
        
        @Provides
        @CliFlagParameter(
            positiveShortName='f', positiveLongName="follow-redirects",
            negativeShortName='F', negativeLongName="no-follow-redirects",
            defaultValue=CliFlagParameterValue.TRUE)
        public boolean provideFollowRedirectsAsString() {
            // Return parsed value...
        }
        
        
        @Provides
        @CliFlagParameter(
            positiveShortName='f', positiveLongName="follow-redirects",
            negativeShortName='F', negativeLongName="no-follow-redirects",
            defaultValue=CliFlagParameterValue.TRUE)
        public boolean provideFollowRedirectsAsBoolean(
                @CliFlagParameter(
                    positiveShortName='f', positiveLongName="follow-redirects",
                    negativeShortName='F', negativeLongName="no-follow-redirects",
                    defaultValue=CliFlagParameterValue.TRUE)
                String value) {
            return Boolean.valueOf(value);
        }
    
        @Provides
        @CliOptionParameter(shortName='t', longName="timeout", defaultValue="30000")
        public String provideTimeoutAsString() {
            // Return parsed value
        }
        
        @Provides
        @CliOptionParameter(shortName='t', longName="timeout", defaultValue="30000")
        public Long provideTimeoutAsLong(
                @CliOptionParameter(shortName='t', longName="timeout", defaultValue="30000")
                String value) {
            return Long.valueOf(value);
        }
    
        @Provides
        @CliPositionalParameter(0)
        public String provideUrlAsString() {
            // Return parsed value
        }
        
        @Provides
        @CliPositionalParameter(0)
        public Long provideUrlAsUrl(
                @CliPositionalParameter(0)
                String value) {
            try {
                return new URL(value);
            } catch(RuntimeException e) {
                throw e;
            } catch(Exception e) {
                throw new IllegalArgumentException("Positional parameter 0 has invalid value", e);
            }
        }
    }

The configured component would accept syntax of the form:

    [-f | --follow-redirects | -F | --no-follow-redirects] [-t <millis> | --timeout <millis>] <url>

Note a few things about the generated code:

* The module takes CLI arguments, so the module must be provided to the component builder explicitly
* It parses the CLI arguments according to the syntax defined by the annotations
* It provides domain-specific types as declared, converting them automatically

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
              <!-- Rapier CLI annotation processor -->
              <path>
                <groupId>com.sigpwned</groupId>
                <artifactId>rapier-cli-compiler</artifactId>
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
            <artifactId>rapier-cli</artifactId>
            <version>${repier.version}</version>
        </dependency>
        <dependency>
            <groupId>com.sigpwned</groupId>
            <artifactId>rapier-cli-compiler</artifactId>
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

Next, define a component class that uses the `@CliPositionalParameter`, `@CliOptionParameter`, and `@CliFlagParameter` annotations to create a dependency on CLI arguments:

    /**
     * CLI arguments for an imaginary CLI tool to fetch the contents of a URL.
     */
    @Component(modules = {
        // Note that we refer to the generated module here, by name
        RapierExampleComponentCliModule.class
    })
    public interface ExampleComponent {
        /**
         * The timeout for fetching the URL
         */
        @CliOptionParameter(shortName='t', longName="timeout", defaultValue="30000")
        public long timeout();
        
        @CliFlagParameter(
            positiveShortName='f', positiveLongName="follow-redirects",
            negativeShortName='F', negativeLongName="no-follow-redirects",
            defaultValue=CliFlagParameterValue.TRUE)
        public boolean followRedirects();
    
        /**
         * The URL to fetch
         */
        @CliPositionalParameter(0)
        public URL url();
    }
    
Position parameter indexes must start at 0 and increase consecutively. Short names for option and flag parameters must be single characters in `[a-zA-Z0-9]`. Long names for option and flag parameters must be non-empty strings with characters in `[-a-zA-Z0-9_]`.

Rapier analyzes dependencies on the component level and generates a module for each component in the same package as the component. Therefore, the above will generate a new module, `RapierExampleComponentCliModule`, for `ExampleComponent` in the same package. Note that the example component references the generated module by name. Generating the module isn't useful if it isn't added to the component!

Now create and use the component in your code like any other Dagger component:

    public static void main(String[] args) {
        final ExampleComponent component = DaggerExampleComponent.builder()
            // Note that we have to provide the module with args
            .rapierExampleComponentCliModule(
                new RapierExampleComponentCliModule(args))
            .build();
        System.out.println(component.timeout());
    }

## Provisioning sites

Rapier is designed to integrate with Dagger seamlessly and supports provisioning CLI arguments in most Dagger-supported injection sites. For example, the below shows how `@CliPositionalParameter`, `@CliOptionParameter`, and `@CliFlagParameter` can be used to provision configuration data in modules.

    @Component(modules = {
      ServerModule.class,
      RapierExampleComponentCliModule.class })
    public interface ExampleComponent {
      public Server server();
    }

    @Module(includes = {DataStoreModule.class})
    public class ServerModule {
      @Provides
      public Server getServer(
          @CliOptionParameter(longName="serverPort", defaultValue="7070") int port,
          DataStore dataStore) {
        return new ExampleServer(port, dataStore);
      }
    }
    
    @Module
    public class DataStoreModule {
      @Provides
      public DataStore getDataStore(
          @CliOptionParameter(longName="databaseHost", defaultValue="localhost") String host,
          @CliOptionParameter(longName="databasePort", defaultValue="5432") int port) {
        return new ExampleDataStore(host, port);
      }
    }

Rapier supports the following Dagger features for CLI parameter discovery:

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

## Modeling CLI syntax

The CLI module supports three kinds of CLI parmaeters:

* Flag parameters -- boolean-valued options that determine their value from their presence or absence (e.g., `-x`, `--xray`)
* Option parameters -- string-valued options that are given values explicitly (e.g., `-x <value>`, `--xray <value>`)
* Positional parameters -- string-valued parameters that are given values explicitly based on their position on the command line

When parsing CLI arguments, Rapier validates that all required arguments are present. In order for this to be possible, all parameters of all types must be treated consistently as either required or optional across all injection sites, or else a compiler error is issued. Otherwise, it is not clear whether or not each parameter is required. See "effective requiredness" for more information.

### Positional parameters

Positional parameters use the `@CliPositionalParameter` annotation. They are identified by their position, with `@CliPositionalParameter(0)` being the first positional argument.

Positional parameters must be defined in order starting from zero without gaps.

All positional parameters must be scalar-valued, except for the last one, which may be list-valued. If the last parameter is list-valued, then it is called a "varargs" parameter, and it receives all positional arguments with position greater than or equal to its position. Whether or not a parameter is varargs is determined entirely from its position and type; there is no additional annotation required.

There can be at most one varargs parameter.

### Option parameters

Option parameters use the `@CliOptionParameter` annotation. They are identified by the combination of their short and long names. A short name is a single character given as `@CliOptionParameter(shortName='x')` in the code and encoded as `-x` on the command line. A long name is a string given as `@CliOptionParameter(longName="xray")` and encoded as `--xray` on the command line. Both may be given, as in `@CliOptionParameter(shortName='x', longName="xray")`. At least one must be given, or a compile error is issued.

Each unique short name must only ever be used with the same long name, and vice versa. Any short name or long name used on options must not be used in any other context, i.e., flag parameters. Otherwise, a compile error is issued.

All option parameters are allowed to be required or optional as required without constraint. One option parameter's requiredness does not affect the validity of any other option parameter's requiredness. However, they must be treated as either required or optional consistently across all injection sites, or else a compile error is issued.

An option parameter's listness is flexible. The same option parameter can be treated as a list and scalar value at different injection sites without error. When a list is treated as a scalar, then the last value is used.

Option parameters are given on the command line followed by a value, as in `-x value`, `--xray value`, or `--xray=value`.

### Flag parameters

Flag parameters use the `@CliFlagParameter` annotation. They are identified by the combination of their position short name, positive long name, negative short name, and negative long name. A positive short name is a single character given as `@CliFlagParameter(positiveShortName='x')` in the code, given as `-x` on the command line, and indicates a value of `true` for the parameter. A positive long name is a string given as `@CliFlagParameter(positiveLongName="xray")` in the code, given as `--xray` on the command line, and indicates a value of `true` for the parameter. Negative short and long names work the same way, except that they are given as `@CliFlagParameter(negativeShortName='x')` and `@CliFlagParameter(negativeLongName='x')` in the code, and indicate a value of `false` for the parameter.

Each unique short name must only ever be used with the same short and long names, and vice versa. Any short name or long name used on flags must not be used in any other context, i.e., option parameters. Otherwise, a compile error is issued.

All flag parameters are allowed to be required or optional as required without constraint. One flag parameter's requiredness does not affect the validity of any other flag parameter's requiredness. However, they must be treated as either required or optional consistently across all injection sites, or else a compile error is issued.

A flag parameter's listness is flexible. The same flag parameter can be treated as a list and scalar value at different injection sites without error. When a list is treated as a scalar, then the last value is used.

Option parameters are given on the command line as a standalone switch, as in `-x` or `--xray`. Multiple flag short names may also be given together in "batch" syntax, where `-xyz` is equivalent to `-x -y -z`.

## Standard help and version

Rapier automatically adds standard help (`--help`, `-h`) and version (`--version`, `-V`) flags, along with corresponding messages, to your CLI application by default. Here’s an example:

    @CliCommand
    @CliCommandHelp(
      name = "server",
      version = "0.0.1",
      description = "A simple server application",
      provideStandardHelp = true,
      provideStandardVersion = true)
    @Component(modules = {
      ServerModule.class,
      RapierExampleComponentCliModule.class })
    public interface ExampleComponent {
      public Server server();
    }

    @Module(includes = {DataStoreModule.class})
    public class ServerModule {
      @Provides
      public Server getServer(
          @CliOptionParameterHelp(
            valueName="port", 
            description="The port the server should listen on")
          @CliOptionParameter(longName="serverPort", defaultValue="7070") int port,
          DataStore dataStore) {
        return new ExampleServer(port, dataStore);
      }
    }
    
    @Module
    public class DataStoreModule {
      @Provides
      public DataStore getDataStore(
          @CliOptionParameterHelp(
            valueName="host", 
            description="The host for the database")
          @CliOptionParameter(longName="databaseHost", defaultValue="localhost") String host,
          @CliOptionParameterHelp(
            valueName="port", 
            description="The port for the database")
          @CliOptionParameter(longName="databasePort", defaultValue="5432") int port) {
        return new ExampleDataStore(host, port);
      }
    }

Each Rapier CLI annotation (`@CliCommand`, `@CliPositionalParameter`, `@CliOptionParameter`, and `@CliFlagParameter`) has its own corresponding help documentation annotation (`@CliCommandHelp`, `@CliPositionalParameterHelp`, `@CliOptionParameterHelp`, and `@CliFlagParameterHelp`, respectively). These help documentation annotations provide the information used in the standard help and version messages Rapier provides.

If the above application were built and packaged as server.jar, then the following commands would demonstrate the built-in help and version handling.

    $ java -jar server.jar --help
    Usage: server [OPTIONS]

    Description: A simple server application

    Option parameters:
      --databaseHost <host>
                        The host for the database
      --databasePort <port>
                        The port for the database
      -h, --help        Print this help message and exit
      --serverPort <port>
                        The port the server should listen on
      -V, --version     Print a version message and exit    

    $ java -jar server.jar --version
    server version 0.0.1
    
To disable the standard help and/or standard version flags, simply use `@CliCommandHelp(provideStandardHelp=false)` or `@CliCommandHelp(provideStandardVersion=false)`, repsectively. Both are enabled by default.

Only one injection site needs to be annotated with help information for the given parameter to be documented in the standard help message. While users can organize this help information however they like, one approach to simplify and centralize parameter documentation is to define a separate documentation component. Remember, Dagger allows users to provision a given value as many times as they like, so there is no issue with duplicate provisioning. 

    @CliCommand
    @CliCommandHelp(
      name = "server",
      version = "0.0.1",
      description = "A simple server application")
    @Component(
      modules = {
        ServerModule.class,
        RapierExampleComponentCliModule.class
      },
      dependencies = ExampleComponentCliDocumentation.class)
    public interface ExampleComponent {
      public Server server();
    }
    
    @Component
    public interface ExampleComponentCliDocumentation {
      @CliOptionParameterHelp(
        valueName="port", 
        description="The port the server should listen on")
      @CliOptionParameter(longName="serverPort", defaultValue="7070")
      public int serverPortHelp();
      
      @CliOptionParameterHelp(
        valueName="host", 
        description="The host for the database")
      @CliOptionParameter(longName="databaseHost", defaultValue="localhost")
      public String databaseHostHelp();
      
      @CliOptionParameterHelp(
        valueName="port", 
        description="The port for the database")
      @CliOptionParameter(longName="databasePort", defaultValue="5432")
      public int databasePortHelp();
    }

    @Module(includes = {DataStoreModule.class})
    public class ServerModule {
      @Provides
      public Server getServer(
          @CliOptionParameter(longName="serverPort", defaultValue="7070") int port,
          DataStore dataStore) {
        return new ExampleServer(port, dataStore);
      }
    }
    
    @Module
    public class DataStoreModule {
      @Provides
      public DataStore getDataStore(
          @CliOptionParameter(longName="databaseHost", defaultValue="localhost") String host,
          @CliOptionParameter(longName="databasePort", defaultValue="5432") int port) {
        return new ExampleDataStore(host, port);
      }
    }

In this approach, a separate component (`ExampleComponentCliDocumentation`) consolidates all parameter help annotations. The main component (`ExampleComponent`) depends on the documentation component, simplifying updates and ensuring clear separation of concerns.

## Customizing the code

The CLI module allows a few ways to customize the generated code.

### Required parameters

By default, CLI parameters are required. This causes Rapier to generate code that will provide the value of the parameter if present, or else throw an `IllegalStateException` during initialization otherwise.

    /**
     * This CLI parameter is required. Rapier will provide the value of the parameter
     * if present, or else throw an IllegalStateException otherwise.
     */
    @CliOptionParameter(longName="foo")
    public String foo();

### Optional parameters

If a CLI parameter should be optional, then annotating the injection site with `@Nullable` will cause Rapier to generate code that will provide the value of the parameter if present, or else `null` otherwise.

    /**
     * This CLI parameter is optional. Rapier will provide the value of the parameter
     * if present, or else null otherwise.
     */
    @Nullable
    @CliOptionParameter(longName="foo")
    public String foo();
    
For list-valued parameters, an empty list is given instead of `null`.
    
### Defaulted parameters

If a CLI parameter should have a default value that can be changed via an explicit override, then including a `deafultValue` will cause Rapier to generate code that will provide the value of the parameter if present, or else the given default value otherwise. No `@Nullable` annotation is required.

    /**
     * This CLI parameter is optional. Rapier will provide the value of the parameter
     * if present, or else "bar" otherwise.
     */
    @CliOptionParameter(longName="foo", defaultValue="bar")
    public String foo();
    
For scalar-valued parameters, the default value is the given string value converted to the parameter's type. For list-valued parameters, the default value is a list containing the given string value converted to the parameter's element type.

## Effective requiredness

Rapier scans all references to each unique positional, option, and flag parameter to determine if each is "effectively required." For example, in the following example:

    /**
     * This reference is required.
     */
    @CliOptionParameter(longName="foo")
    public String optionFooRequired();
    
    /**
     * This reference has a default value, so is optional.
     */
    @CliOptionParameter(longName="foo", defaultValue="foobar")
    public String optionFooWithDefaultValue();
    
    /**
     * This reference is @Nullable, so is optional.
     */
    @Nullable
    @CliOptionParameter(longName="foo")
    public Long optionFooOptional();
    

...the option parameter `foo` is effectively required because one of the provision methods (`optionFooRequired`) requires it to be present, or else an `IllegalStateException` will be thrown. Since option parameter `foo` is required in one place, it is logically required in *all* places.

The CLI module enforces that the requiredness of each parameter at all injection sites match the parameter's effective requiredness. If there is a mismatch, a compile error is issued. This ensures that Rapier can accurately determine which parameters are required and handle their parsing correctly.

## Type conversions

### Positional parameters

The CLI module annotation `@CliPositionalParameter` provides `String` values by default, but also supports a variety of built-in and extensible type conversions as well. For example, the following types are supported:

* Primitives (i.e., `byte`, `short`, `int`, `long`, `char`, `float`, `double`, `boolean`)
* Boxed primitives (i.e., `Byte`, `Short`, `Integer`, `Long`, `Character`, `Float`, `Double`, `Boolean`)
* Types `T` with a method `public static T fromString(String)`
* Types `T` with a method `public static T valueOf(String)`
* Types `T` with a constructor `public T(String)`

In addition, for varargs positional parameters, the following types are supported:

* List types `java.util.List<T>` for any supported reference type `T` above
* Types `T` with a method `public static T valueOf(List<String>)`
* Types `T` with a constructor `public T(List<String>)`

### Option parameters

The CLI module annotation `@CliOptionParameter` provides `String` values by default, but also supports a variety of built-in and extensible type conversions as well. For example, the following types are supported:

* Scalar-valued
    * Primitives (i.e., `byte`, `short`, `int`, `long`, `char`, `float`, `double`, `boolean`)
    * Boxed primitives (i.e., `Byte`, `Short`, `Integer`, `Long`, `Character`, `Float`, `Double`, `Boolean`)
    * Types `T` with a method `public static T fromString(String)`
    * Types `T` with a method `public static T valueOf(String)`
    * Types `T` with a constructor `public T(String)`
* List-valued
    * List types `java.util.List<T>` for any supported reference scalar type `T`
    * Types `T` with a method `public static T valueOf(List<String>)`
    * Types `T` with a constructor `public T(List<String>)`

The list type does not have any special semantics for option parameters.

The same option parameter can be treated as a single value and a list without error.

### Flag parameters

The CLI module annotation `@CliFlagParameter` provides `Boolean` values by default, but also supports a variety of built-in and extensible type conversions as well. For example, the following types are supported:

* Scalar-valued
    * `boolean`
    * Types `T` with a method `public static T valueOf(Boolean)`
    * Types `T` with a constructor `public T(Boolean)`
* List-valued
    * List types `java.util.List<T>` for any supported reference scalar type `T`
    * Types `T` with a method `public static T valueOf(List<Boolean>)`
    * Types `T` with a method `public T(List<Boolean>)`

## Name templating

The CLI module does not support name templating. CLI syntax should not change based on environment.

## Testing with Rapier

Rapier is designed with testing in mind. 

To test with the CLI module, simply provide custom argument arrays to the module.
