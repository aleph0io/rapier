package rapier.aws.ssm.compiler.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashSet;
import java.util.Set;

public final class Interfaces {
  private Interfaces() {}

  @FunctionalInterface
  public static interface MethodStubGenerator {
    /**
     * Generates a method stub for the given method. The generated code should be written to the
     * provided {@link PrintWriter}. This does not include the method signature.
     * 
     * @param out the {@link PrintWriter} to write the generated code to
     * @param method the {@link Method} to generate a stub for
     */
    public void generateMethodStub(PrintWriter out, Method method);
  }

  public static String generateInterfaceImplementation(Class<?> interfaceClass,
      MethodStubGenerator methodStubGenerator) {
    if (!interfaceClass.isInterface()) {
      throw new IllegalArgumentException(
          "Provided class is not an interface: " + interfaceClass.getName());
    }

    final String className = "Generated" + interfaceClass.getSimpleName();

    final StringWriter sourceCode = new StringWriter();
    final PrintWriter out = new PrintWriter(sourceCode);

    // Generate package statement (optional, here we assume no package for simplicity)
    out.println("// Auto-generated implementation of " + interfaceClass.getName());

    // Generate class declaration
    StringBuilder declarationFirstLine = new StringBuilder();
    declarationFirstLine.append("public class " + className);

    // Add generic type parameters if present
    TypeVariable<?>[] typeParameters = interfaceClass.getTypeParameters();
    if (typeParameters.length > 0) {
      declarationFirstLine.append("<");
      for (int i = 0; i < typeParameters.length; i++) {
        if (i > 0)
          declarationFirstLine.append(", ");
        declarationFirstLine.append(typeParameters[i].getName());
        Type[] bounds = typeParameters[i].getBounds();
        if (bounds.length > 0 && !bounds[0].equals(Object.class)) {
          declarationFirstLine.append(" extends ").append(bounds[0].getTypeName());
        }
      }
      declarationFirstLine.append(">");
    }
    out.println(declarationFirstLine);

    StringBuilder declarationSecondLine = new StringBuilder();
    declarationSecondLine.append("    implements ").append(interfaceClass.getName());

    // Add generic type arguments if the interface is parameterized
    if (typeParameters.length > 0) {
      declarationSecondLine.append("<");
      for (int i = 0; i < typeParameters.length; i++) {
        if (i > 0)
          declarationSecondLine.append(", ");
        declarationSecondLine.append(typeParameters[i].getName());
      }
      declarationSecondLine.append(">");
    }

    declarationSecondLine.append(" {");
    out.println(declarationSecondLine);

    // Collect methods from the interface and all its super-interfaces
    Set<Method> methods = collectAbstractMethods(interfaceClass);

    // Generate method stubs
    for (Method method : methods) {
      generateMethodStub(out, method, methodStubGenerator);
    }

    // Close class definition
    out.println("}");

    return sourceCode.toString();
  }

  private static Set<Method> collectAbstractMethods(Class<?> interfaceClass) {
    Set<Method> methods = new HashSet<>();

    // Traverse the interface hierarchy
    collectMethodsRecursive(interfaceClass, methods);

    return methods;
  }

  private static void collectMethodsRecursive(Class<?> interfaceClass, Set<Method> methods) {
    if (!interfaceClass.isInterface()) {
      return;
    }

    // Add abstract methods that are not default or already overridden
    for (Method method : interfaceClass.getDeclaredMethods()) {
      if (Modifier.isAbstract(method.getModifiers())) {
        methods.add(method);
      }
    }

    // Recurse into super-interfaces
    for (Class<?> superInterface : interfaceClass.getInterfaces()) {
      collectMethodsRecursive(superInterface, methods);
    }
  }

  private static void generateMethodStub(PrintWriter out, Method method,
      MethodStubGenerator methodStubGenerator) {
    // Method signature
    out.println("    @Override\n");

    StringBuilder sourceCode = new StringBuilder();
    sourceCode.append("    public ").append(getTypeName(method.getGenericReturnType())).append(" ")
        .append(method.getName()).append("(");

    // Parameters
    Parameter[] parameters = method.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0)
        sourceCode.append(", ");
      sourceCode.append(getTypeName(parameters[i].getParameterizedType())).append(" param")
          .append(i);
    }
    sourceCode.append(")");

    // Exceptions
    Class<?>[] exceptionTypes = method.getExceptionTypes();
    if (exceptionTypes.length > 0) {
      sourceCode.append(" throws ");
      for (int i = 0; i < exceptionTypes.length; i++) {
        if (i > 0)
          sourceCode.append(", ");
        sourceCode.append(exceptionTypes[i].getTypeName());
      }
    }

    // Method body
    sourceCode.append(" {");

    out.println(sourceCode);

    methodStubGenerator.generateMethodStub(out, method);

    out.println("    }");
    out.println();
  }

  private static String getTypeName(Type type) {
    // Convert generic type to string representation
    return type.getTypeName().replace('$', '.'); // Handle nested class naming
  }
}
