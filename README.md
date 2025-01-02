# rapier [![tests](https://github.com/sigpwned/rapier/actions/workflows/tests.yml/badge.svg)](https://github.com/sigpwned/rapier/actions/workflows/tests.yml)

A code-generation companion library for [Dagger](https://github.com/google/dagger).

## Features

This library generates Dagger Modules automatically from annotations on Component injection sites for common tasks. The following injection sites are supported:

* Component provision methods
* Module provide methods
* Class inject constructor parameters
* Class inject methods
* Class inject fields

## Quickstart

To load an environment variable automatically, ensure that the corresponding annotation processor runs *before* Dagger's annotation processor:

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
                    <artifactId>rapier-processor-environment-variable</artifactId>
                    <version>0.0.0-b0</version>
                </path>
                
                <!-- Dagger annotation processor -->
                <path>
                    <groupId>com.google.dagger</groupId>
                    <artifactId>dagger-compiler</artifactId>
                    <version>2.48</version>
                </path>
            </annotationProcessorPaths>
        </configuration>
    </plugin>


And then use the following component:

    @Component(modules = {RapierExampleComponentEnvironmentVariableModule.class})
    public interface ExampleComponent {
        /**
         * Get timeout in milliseconds
         */
        @EnvironmentVariable(value="TIMEOUT", defaultValue="30000")
        public long getTimeout();
    }
    
This will generate a new module, `RapierExampleComponentEnvironmentVariableModule`, that looks like this:

    @Module
    public class RapierExampleComponentEnvironmentVariableModule {
        private final Map<String, String> env;

        @Inject
        public RapierExampleComponentEnvironmentVariableModule() {
            this(System.getenv());
        }

        public RapierExampleComponentEnvironmentVariableModule(Map<String, String> env) {
            this.env = unmodifiableMap(env);
        }

        @Provides
        @EnvironmentVariable("TIMEOUT")
        public java.lang.Long provideEnvironmentVariableFooBarAsLong(@EnvironmentVariable(value="TIMEOUT", defaultValue="30000") String value) {
            java.lang.Long result= java.lang.Long.valueOf(value);
            if (result == null)
                throw new IllegalStateException("Environment variable TIMEOUT  as java.lang.Long not set");
            return result;
        }

        @Provides
        @EnvironmentVariable(value="TIMEOUT", defaultValue="30000")
        public String provideEnvironmentVariableFooBarAsString() {
            String result=env.get("TIMEOUT");
            return result != null ? resut : "30000";
        }
    }

Note that Rapier is smart enough to recognize that the default String representation is insufficient, and therefore generates an additional binding for Long.

Note that the `ExampleComponent` component includes the generated `RapierExampleComponentEnvironmentVariableModule` module, even though the latter is generated from the former.

The library has processors for the following parameter types:

* Java environment variables
* Java system properties
* AWS SSM Parameter Store Parameters

## Colophon

A rapier is a sword that was frequently used as a companion weapon to the dagger