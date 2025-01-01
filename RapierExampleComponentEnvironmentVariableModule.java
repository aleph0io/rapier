import static java.util.Collections.unmodifiableMap;

import rapier.processor.envvar.EnvironmentVariable;
import dagger.Module;
import dagger.Provides;
import dagger.Reusable;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;

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
    @Nullable
    @EnvironmentVariable("FOO_BAR")
    public java.lang.Integer provideEnvironmentVariableFooBarAsInteger(@Nullable @EnvironmentVariable("FOO_BAR") String value) {
        return value != null ? java.lang.Integer.valueOf(value) : null;
    }

    @Provides
    @EnvironmentVariable("FOO_BAR")
    public Optional<java.lang.Integer> provideEnvironmentVariableFooBarAsOptionalOfInteger(@EnvironmentVariable("FOO_BAR") Optional<String> o) {
        return o.map(value -> java.lang.Integer.valueOf(value));
    }

    @Provides
    @Nullable
    @EnvironmentVariable("FOO_BAR")
    public String provideEnvironmentVariableFooBarAsString() {
        return env.get("FOO_BAR");
    }

    @Provides
    @EnvironmentVariable("FOO_BAR")
    public Optional<String> provideEnvironmentVariableFooBarAsOptionalOfString(@Nullable @EnvironmentVariable("FOO_BAR") value) {
        return Optional.ofNullable(value);
    }

}
