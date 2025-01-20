
# rapier-aws-ssm

This module (and its sister module, rapier-aws-ssm-compiler) provides code generation for fetching string parameters from AWS Systems Manager (SSM) Parameter Store using [Dagger](https://dagger.dev/).

## Overview

To generate code to fetch AWS SSM string parameters in Dagger, use the `@AwsSsmStringParameter` annotation. For example:

    @Component(modules = {
        // Note that we refer to the generated module here, by name
        RapierExampleComponentAwsSsmModule.class
    })
    public interface ExampleComponent {
        /**
         * Get timeout in milliseconds from SSM Parameter Store parameter `/config/timeout`, or use the
         * default of 30000 if not present.
         */
        @AwsSsmStringParameter(value="/config/timeout", defaultValue="30000")
        public long timeout();
    }

The above `ExampleComponent` component would generate the `RapierExampleComponentAwsSsmModule` module referenced in the code. The generated code would look like this:

    @Module
    public class RapierExampleComponentAwsSsmModule {
        // NOTE: Code is left out for simplicity
    
        @Provides
        @AwsSsmStringParameter(value="/config/timeout", defaultValue="30000")
        public String provideAwsSsmParameterTimeoutAsString() {
            // Fetch parameter from AWS SSM or use default value
        }
        
        @Provides
        @AwsSsmStringParameter(value="/config/timeout", defaultValue="30000")
        public Long provideAwsSsmParameterTimeoutAsLong(
                @AwsSsmStringParameter(value="/config/timeout", defaultValue="30000") String value) {
            return Long.valueOf(value);
        }
    }

Note a few things about the generated code:

* It looks for the named AWS SSM Parameter Store string parameter `/config/timeout`, and provides that value if present, or else the default value of `"30000"` otherwise.
* It converts the `String` value to a `Long`, since that's what the user requested.

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
              <!-- Rapier AWS SSM annotation processor -->
              <path>
                <groupId>com.sigpwned</groupId>
                <artifactId>rapier-aws-ssm-compiler</artifactId>
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
            <artifactId>rapier-aws-ssm</artifactId>
            <version>${repier.version}</version>
        </dependency>
        <dependency>
            <groupId>com.sigpwned</groupId>
            <artifactId>rapier-aws-ssm-compiler</artifactId>
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

Next, define a component class that uses the `@AwsSsmStringParameter` annotation to create a dependency on an AWS SSM Parameter Store parameter. Names are allowed to have the characters `[-a-zA-Z0-9._/]`.

    @Component(modules = {
        // Note that we refer to the generated module here, by name
        RapierExampleComponentAwsSsmModule.class
    })
    public interface ExampleComponent {
        /**
         * Get timeout in milliseconds from SSM Parameter Store parameter `/config/timeout`, or use the
         * default of 30000 if not present.
         */
        @AwsSsmParameter(value="/config/timeout", defaultValue="30000")
        public long timeout();
    }
    
Rapier analyzes dependencies on the component level and generates a module for each component in the same package as the component. Therefore, the above will generate a new module, `RapierExampleComponentAwsSsmModule`, for `ExampleComponent` in the same package. Note that the example component references the generated module by name. Generating the module isn't useful if it isn't added to the component!

Now create and use the component in your code like any other Dagger component:

    public static void main(String[] args) {
        final ExampleComponent component = DaggerExampleComponent.builder()
            .build();
        System.out.println(component.timeout());
    }
    
## Provisioning sites

Rapier is designed to integrate with Dagger seamlessly and supports provisioning AWS SSM parameters in most Dagger-supported injection sites. For example, the below shows how `@AwsSsmStringParameter` can be used to provision configuration data in modules.

    @Component(modules = {
      ServerModule.class,
      RapierExampleComponentAwsSsmModule.class })
    public class ExampleComponent {
      public Server server();
    }

    @Module(includes = {DataStoreModule.class})
    public class ServerModule {
      @Provides
      public Server getServer(
          @AwsSsmStringParameter(value = "/config/server/port", defaultValue = "7070") int port,
          DataStore dataStore) {
        return new ExampleServer(port, dataStore);
      }
    }
    
    @Module
    public class DataStoreModule {
      @Provides
      public DataStore getDataStore(
          @AwsSsmStringParameter(value = "/config/database/host", defaultValue = "localhost") String host,
          @AwsSsmStringParameter(value = "/config/database/port", defaultValue = "5432") int port) {
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

### Required parameters

By default, parameters are required. This causes Rapier to generate code that will provide the value of the named parameter if present, or else throw an `IllegalStateException` during initialization otherwise.

    /**
     * This parameter is required. Rapier will provide the value of the parameter if
     * present, or else throw an IllegalStateException otherwise.
     */
    @AwsSsmStringParameter("/foo")
    public String foo();

### Optional parameters

If the value of a parameter is optional, then annotating the injection site with `@Nullable` will cause Rapier to generate code that will provide the value of the named parameter if present, or else `null` otherwise.

    /**
     * This parameter is optional. Rapier will provide the value of the parameter if
     * present, or else null otherwise.
     */
    @Nullable
    @AwsSsmStringParameter("/foo")
    public String foo();
    
### Defaulted parameters

Including a default value in the `@AwsSsmStringParameter` annotation will cause Rapier to generate code that will provide the value of the named parameter if present, or else the given default value otherwise. No `@Nullable` annotation is required.

    /**
     * This parameter is optional. Rapier will provide the value of the parameter if
     * present, or else "bar" otherwise.
     */
    @AwsSsmStringParameter(value="/foo", defaultValue="bar")
    public String foo();

## Effective requiredness

Rapier scans all the references to each unique parameter and determines if each is "effectively required." For example, in the following example:

    /**
     * This reference is required.
     */
    @AwsSsmStringParameter("/foo")
    public String fooRequired();
    
    /**
     * This reference has a default value, so is optional.
     */
    @AwsSsmStringParameter(value="/foo", defaultValue="bar")
    public String fooWithDefaultValue();
    
    /**
     * This reference is @Nullable, so is optional.
     */
    @Nullable
    @AwsSsmStringParameter("/foo")
    public Long fooOptional();
    

the `/foo` parameter is effectively required because one of the provision methods (`fooRequired`) requires it to be present, or else an `IllegalStateException` will be thrown. Since `/foo` is required in one place, it is logically required in *all* places. Rapier issues warnings when effectively required parameters are treated as optional. For example, the above code would generate the following warnings:

* `Effectively required AWS SSM string parameter /foo is treated as nullable`
* `Effectively required AWS SSM string parameter /foo has default value`

To address this warning, either:

* Make all required reference(s) optional by either adding `@Nullable` or adding a default value
* Make all optional reference(s) required by removing `@Nullable` and default values

## Type conversions

The AWS SSM module provides `String` values by default, but also supports a variety of built-in and extensible type conversions as well. For example, the following types are supported:

* Primitives (i.e., `byte`, `short`, `int`, `long`, `char`, `float`, `double`, `boolean`)
* Boxed primitives (i.e., `Byte`, `Short`, `Integer`, `Long`, `Character`, `Float`, `Double`, `Boolean`)
* Types `T` with a method `public static T fromString(String)`
* Types `T` with a method `public static T valueOf(String)`
* types `T` with a constructor `public T(String)`

For more information about type conversions, see [the Rapier wiki](https://github.com/aleph0io/rapier/wiki/Type-conversions).

AWS SSM string list parameters are not supported at this time.

## Name templating

The AWS SSM module supports simple environment variable and system property templating in the `@AwsSsmStringParameter(value)` field, which contains the name of the environment variable to provide. For example:

    /**
     * If the environment variable "STAGE" has the value "PROD", then this provisions
     * the parameter "/prod/foo". If the environment variable "STAGE" is not present,
     * then the generated code would throw an `IllegalStateException` on initialization.
     */
    @AwsSsmStringParameter("/${env.STAGE}/foo")
    public String foo();

The environment variable module supports the following syntax for name templates:

* `${env.NAME}` - Replace with the value of the `NAME` environment variable if present, or else throw `IllegalStateException`
* `${env.NAME:-default}` - Replace with the value of the `NAME` environment variable if present, or else use the value `"default"`
* `${sys.name}` - Replace with the value of the `name` system property if present, or else throw `IllegalStateException`
* `${sys.name:-default}` - Replace with the value of the `name` system property if present, or else use the value `"default"`

For more information about name templating, see [the Rapier wiki](https://github.com/aleph0io/rapier/wiki/Name-templating).

## Testing with Rapier

Rapier is designed with testing in mind.

### Customizing AWS SSM client

To customize the AWS SSM client (e.g., to use a LocalStack instance), pass a custom client to the generated module. For example:

    final ExampleComponent component = DaggerExampleComponent.builder()
        .rapierExampleComponentAwsSsmModule(
            SsmClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SSM))
                .credentialsProvider(StaticCredentialsProvider.create(
                     AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion())).build())
                .build())
        .build();
        
The given client will be used to communicate with the AWS SSM API.
        
### Providing custom environment variable and system property values

To cause Rapier to provide custom values for environment variables and system properties during a test, use the three-argument test constructor:

    final SsmClient client = SsmClient.builder()
        .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SSM))
        .credentialsProvider(StaticCredentialsProvider.create(
             AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
        .region(Region.of(localstack.getRegion())).build())
        .build();
    final Map<String, String> customEnvironmentVariables = Map.of(
        "FOO", "BAR",
        "ALPHA", "BRAVO");
    final Map<String, String> customSystemProperties = Map.of(
        "com.example.foo", "bar",
        "com.example.alpha", "bravo");
    final ExampleComponent component = DaggerExampleComponent.builder()
        .rapierExampleComponentAwsSsmModule(
            new RapierExampleComponentAwsSsmModule(client, env, sys)))
        .build();
        
The given client will be used to communicate with the AWS SSM API. The given environment variables and system properties will be used when expanding name templates.