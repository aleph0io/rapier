# rapier [![tests](https://github.com/sigpwned/rapier/actions/workflows/tests.yml/badge.svg)](https://github.com/sigpwned/rapier/actions/workflows/tests.yml) [![Maven Central Version](https://img.shields.io/maven-central/v/io.aleph0/rapier)](https://central.sonatype.com/artifact/io.aleph0/rapier)  [![javadoc](https://javadoc.io/badge2/io.aleph0/rapier/javadoc.svg)](https://javadoc.io/doc/io.aleph0/rapier) 

A code-generation companion library for [Dagger](https://github.com/google/dagger) focused on eliminating boilerplate code when pulling configuration data from common configuration sources.

## Rapier Features

Rapier generates Dagger modules automatically from annotations on Dagger injection sites.

For example, given the following component:

    @Component(modules = {RapierExampleComponentEnvironmentVariableModule.class})
    public interface ExampleComponent {
        /**
         * Get timeout in milliseconds from environment variable TIMEOUT, or use the
         * default of 30000 if not present
         */
        @EnvironmentVariable(value="TIMEOUT", defaultValue="30000")
        public long getTimeout();
    }
    
...Rapier would automatically generate the named `RapierExampleComponentEnvironmentVariableModule` module class to provide the logic for retrieving the `TIMEOUT` environment variable, using the default value of `30000` if the environment variable is not set, and convert it to `long`.

## Quickstart

In order for Rapier to generate code for a configuration source (e.g., environment variables), the associated Rapier annotation processor has to run during the build. Because Rapier generates code for Dagger to consume, all of Rapier's annotation processors must run before Dagger's annotation processor. This involves setting up annotation processor execution order manually in your build.

For example, to use Rapier's environment variable integration in your Maven build, add the following configuration in your `pom.xml`:

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
            <!-- Rapier requires Java 11+, just like Dagger -->
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
        public long getTimeout();
    }
    
This will generate a new module, `RapierExampleComponentEnvironmentVariableModule`, for `ExampleComponent` in the same package. (Note that the example component references the generated module by name.) The generated module will look like this.

    @Module
    public class RapierExampleComponentEnvironmentVariableModule {
        private final Map<String, String> env;
        
        /**
         * Default constructor, used by Dagger automatically if no module instance given
         */
        public RapierExampleComponentEnvironmentVariableModule() {
            this(System.getenv());
        }
        
        /**
         * Test constructor, allows users to provide custom environment variables during
         * test.
         *
         * @param env custom environment variables for test
         */
        public RapierExampleComponentEnvironmentVariableModule(Map<String, String> env) {
            this.env = unmodifiableMap(env);
        }
        
        // Additional code left out for simplicity
    
        @Provides
        @EnvironmentVariable(value="TIMELINE", defaultValue="30000")
        public String provideEnvironmentVariableTimelineAsString() {
            final String name="TIMELINE";
            final String value=env.get(name);
            if (value == null)
                return "30000";
            return value;
        }
        
        @Provides
        @EnvironmentVariable(value="TIMELINE", defaultValue="30000")
        public java.lang.Long provideEnvironmentVariableTimelineAsLong(
                @EnvironmentVariable(value="TIMELINE", defaultValue="30000") String value) {
            return java.lang.Long.valueOf(value);
        }
    }

Note that the generated module has a test constructor, allowing users to provide custom environment variables during testing.

## Existing Integrations

Rapier has integrations with the following configuration sources:

* Java environment variables (`@EnvironmentVariable`)
* Java system properties (`@SystemProperty`)
* AWS SSM Parameter Store Parameters (`@AwsSsmStringParameter`)
* CLI arguments (`@CliPositionalParameter`, `@CliOptionParameter`, `@CliFlagParameter`)

Each integration has two modules in this repository: a "compile" dependency to be added to the project as a dependency that contains annotations, and a "provided" dependency to add to the build that contains the annotation processor to generate the appropriate code. For example, for environment variables, the modules are, respectively:

* `rapier-environment-variable`
* `rapier-environment-variable-compiler`

Information about how to use each provider is in each module's "compile" dependency.

## Supported Dagger Features

Rapier automatically detects and generates providers for injection sites based on the following core Dagger features:

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

Rapier does *not* currently support the following core Dagger features. It may or may not work with these Dagger features.

* [Subcomponents](https://dagger.dev/dev-guide/subcomponents.html)
* [Assisted injection](https://dagger.dev/dev-guide/assisted-injection)
* Module `@Bind` methods
* `Provider<Lazy<T>>`
* `MembersInjector`
* `@BindsOptionalOf`
* `@BindsInstance`

Rapier also does not currently support the following Dagger add-on features. It may or may not work with these Dagger features.

* [Producers](https://dagger.dev/dev-guide/producers)

## Advanced Usage

### Data Type Conversion

As part of eliminating boilerplate code, each Rapier integration provides a set of standard data type conversion. For instance, in this example, the environment variable module generates code to convert the native `String` to a `long`:

    @Component(modules = {RapierExampleComponentEnvironmentVariableModule.class})
    public interface ExampleComponent {
        /**
         * Get timeout in milliseconds from environment variable TIMEOUT, or use the
         * default of 30000 if not present
         */
        @EnvironmentVariable(value="TIMEOUT", defaultValue="30000")
        public long getTimeout();
    }

#### String-typed Configuration Sources

For `String`-typed configuration sources, the following conversions are supported:

* Primitive types (`byte`, `short`, `int`, `long`, `char`, `float`, `double`, `boolean`)
* Boxed primitive types (`Byte`, `Short`, `Integer`, `Long`, `Character`, `Float`, `Double`, `Boolean`)
* Custom conversions for other types `T` via:
    * For type `T`, uses `public static T fromString(String)`
    * For type `T`, uses `public static T valueOf(String)`
    * For type `T`, uses `public T(String)`

So, to allow Rapier to convert from `String` to another type `T` automatically, simply add a matching `fromString` method, a matching `valueOf` method, or a matching single-argument constructor. Of course, users are still free simply to add a factory method in another module, too.


#### Other Configuration Sources

For configuration sources of another type `C` (e.g., CLI flags are `Boolean`-valued), the following conversions are supported:

* `String` using `public String toString()`
* Custom conversions for other types `T` via:
    * For type `T`, uses `public static T valueOf(C)`
    * For type `T`, uses `public T(C)`
    
Similarly, to allow Rapier to convert from `C` to another type `T` automatically, simply add a matching `valueOf` method, or a matching single-argument constructor. Of course, users are still free simply to add a factory method in another module here, too.

#### Additional Information

Specific details about the type conversions each module supports appear in their respective READMEs.

### Templating

Rapier supports using environment variables and system properties to specify configuration coordinates, e.g., environment variable names. For example, when using the AWS SSM integration, it might be useful to use the current deployment stage to load different configuration data.

    @Component(modules = RapierExampleComponentAwsSsmModule.class)
    public interface ExampleComponent {
        @AwsSsmStringParameter("${env.STAGE}.databaseHost")
        public String databaseHostname();
    }
    
The above `ExampleComponent` uses the environment variable `STAGE` to choose which parameter to load at runtime.

In this usage, the `STAGE` variable must be present, or Rapier will throw an `IllegalStateException` during initialization. However, users can also provide a default value to use if the environment variable is missing:

    @Component(modules = RapierExampleComponentAwsSsmModule.class)
    public interface ExampleComponent {
        @AwsSsmStringParameter("${env.STAGE:-test}.databaseHost")
        public String databaseHostname();
    }

In this usage, Rapier will use the value of the `STAGE` variable if it is present, or `"test"` otherwise.

Rapier supports the following syntax for name templates:

* `${env.NAME}` - Replace with the value of the `NAME` environment variable if present, or else throw `IllegalStateException`
* `${env.NAME:-default}` - Replace with the value of the `NAME` environment variable if present, or else use the value `"default"`
* `${sys.name}` - Replace with the value of the `name` system property if present, or else throw `IllegalStateException`
* `${sys.name:-default}` - Replace with the value of the `name` system property if present, or else use the value `"default"`

The following integration attributes support templating:

* `@EnvironmentVariable(value)`
* `@SystemProperty(value)`
* `@AwsSsmStringParameter(value)`

Note that no integration supports templates for default values at this time.

The above integrations all support test constructors that allow users to specify custom environment variables and system properties for templates during testing.

### Testing Using Rapier

Rapier was designed to allow the easy test of applications built with it. All Rapier modules generate code that includes features to allow the user to control the data Rapier returns for testing purposes. For example, the environment variable module generates module code like this:

    @Module
    public class RapierExampleComponentEnvironmentVariableModule {
        /**
         * Test constructor, allows users to provide custom environment variables for
         * use during test and templating.
         *
         * @param env custom environment variables for test and templating
         */
        public RapierExampleComponentEnvironmentVariableModule(Map<String, String> env) {
            // implementation
        }
    
        /**
         * Test constructor, allows users to provide custom environment variables for
         * use during test and templating, and custom system properties for use during
         * templating.
         *
         * @param env custom environment variables for test and templating
         * @param sys custom system properties for templating
         */
        public RapierExampleComponentEnvironmentVariableModule(
                Map<String, String> env,
                Properties sys) {
            // implementation
        }
    }
    
This allows users to provide custom values for use in tests when retrieving
configuration data and evaluating name templates. For example:



## Regarding Beta Versioning

The Rapier library is currently in a beta phase. This does not imply that the library is unstable or unreliable; in fact, the authors are successfully using Rapier in production already. However, as a beta release, it is subject to potential non-backwards-compatible changes in future versions without prior notice.

While the primary user-facing interface of Rapier is expected to remain relatively stable, certain components are still under active development and may evolve more frequently. Specifically:

* List Handling: Particularly with respect to default values.

## Examples

See the rapier-exmaples module for examples of usage in real-world scenarios.

## Colophon

A [rapier](https://en.wikipedia.org/wiki/Rapier) is a sword that frequently used a [dagger](https://en.wikipedia.org/wiki/Parrying_dagger) as a [companion weapon](https://en.wikipedia.org/wiki/Companion_weapon).