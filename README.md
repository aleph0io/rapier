In Dagger, dependencies can only be created via:

1. Provision methods in a component.
2. Method parameters in modules (@Provides, @Binds).
3. @Inject-annotated constructors from 1 and 2.
4. @Inject-annotated fields from 1 and 2.
5. @Inject-annotated instance methods from 1 and 2.
6. @Inject-annotated static methods from 1 and 2.

Dependencies are NOT created by:

1. Module constructor parameters, even if @Inject-annotated
2. Component constructor parameters for abstract classes, even if @Inject-annotated