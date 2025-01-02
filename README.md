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

To load an environment variable automatically, use this code:

    @Component(modules = {RapierExampleComponentEnvironmentVariableModule.class})
    public interface ExampleComponent {
        /**
         * Get timeout in milliseconds
         */
        @EnvironmentVariable(value="TIMEOUT", defaultValue="30000")
        public long getTimeout();
    }
    
This component will generate a new module, `RapierExampleComponentEnvironmentVariableModule`, that looks like this:

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
                public java.lang.Integer provideEnvironmentVariableFooBarAsInteger(@EnvironmentVariable("FOO_BAR") String value) {
                    java.lang.Integer result= java.lang.Integer.valueOf(value);
                    if (result == null)
                        throw new IllegalStateException("Environment variable FOO_BAR  as java.lang.Integer not set");
                    return result;
                }

                @Provides
                @EnvironmentVariable("TIMEOUT")
                public String provideEnvironmentVariableFooBarAsString() {
                    String result=env.get("TIMEOUT");
                    if (result == null)
                        throw new IllegalStateException("Environment variable TIMEOUT not set");
                    return result;
                }

            }



The library supports the following Bash substitution features:

* Parameter expansion
* String globbing
* Indirection (e.g., `${!parameter}`)

The library does not currently support the following Bash substitution features, but may in the future:

* Arithmetic expansion
* Quoting
* Arrays
* Namerefs
* Extglob

The library does not currently support the following Bash substitution features, and never will:

* Tilde expansion, since it accesses system environment facts
* Command substitution, since it creates new processes
* File globbing, since it access the file system

## Supported substitutions

* `${parameter}`
* `${parameter:-word}`
* `${parameter:+word}`
* `${parameter:offset}`
* `${parameter:offset:length}`
* `${parameter#word}`
* `${parameter##word}`
* `${parameter%word}`
* `${parameter%%word}`
* `${parameter/pattern/string}`
* `${parameter//pattern/string}`
* `${parameter/#pattern/string}`
* `${parameter/%pattern/string}`
* `${parameter^pattern}`
* `${parameter^^pattern}`
* `${parameter,pattern}`
* `${parameter,,pattern}`
* `${parameter@operator}`, for operators `UuL`
* `${!parameter}` (indirection, for all of the above)

## Quick start

To perform substitution using environment variables, use:

    BashSubstitution.substitute(System.getenv(), "This is my ${ADJECTIVE} template.");

To perform substitution using system properties, use:

    BashSubstitution.substitute(System.getProperties(), "This is my ${ADJECTIVE} template.");

To perform substitution using custom variables, use:

    Map<String, String> customVariables=new HashMap<>();
    customVariables.put("ADJECTIVE", "fancy");
    BashSubstitution.substitute(customVariables, "This is my ${ADJECTIVE} template.");

## Advanced usage

Users can create an instance to perform many substitutions with a given set of variables and use it like this:

    BashSubstitutor substitutor=new BashSubstitutor(System.getenv());
    String substitution = substitutor.substitute("This is my ${ADJECTIVE} template.");

Users can translate a Bash (string) globbing expression using:

    // Use true for greedy globbing, or false for non-greedy globbing
    Pattern p = StringGlobbing.toJavaPattern(globExpression, false);

### Disabling syntax

By default, the `BashSubstitutor` class supports all the enumerated substitutions. If users want not to support some specific syntax, then they can simply create a new subclass overriding a specific expression type. For example:

    // Don't support the ${parameter#word} syntax
    BashSubstitutor substitutor = new BashSubstitutor(variables) {
        @Override
        protected CharSequence handleHashExpr(CharSequence name, CharSequence pattern) {
            throw new UnsupportedOperationException("syntax not supported");
        }
    };
