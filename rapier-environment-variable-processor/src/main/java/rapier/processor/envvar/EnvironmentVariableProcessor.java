/*-
 * =================================LICENSE_START==================================
 * rapier-core
 * ====================================SECTION=====================================
 * Copyright (C) 2024 Andy Boothe
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
package rapier.processor.envvar;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import dagger.Component;
import rapier.processor.core.DaggerComponentAnalyzer;
import rapier.processor.core.model.DaggerComponentAnalysis;
import rapier.processor.core.model.Dependency;
import rapier.processor.core.util.CaseFormat;
import rapier.processor.core.util.Hashing;
import rapier.processor.core.util.Hex;
import rapier.processor.core.util.Java;

@SupportedAnnotationTypes("dagger.Component")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class EnvironmentVariableProcessor extends AbstractProcessor {
  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    final Set<? extends Element> annotatedElements =
        roundEnv.getElementsAnnotatedWith(Component.class);

    final List<TypeElement> components = new ArrayList<>();
    for (Element element : annotatedElements) {
      if (element.getKind() != ElementKind.INTERFACE && element.getKind() != ElementKind.CLASS)
        continue;
      TypeElement typeElement = (TypeElement) element;
      components.add(typeElement);
    }

    for (TypeElement component : components) {
      processComponent(component);
    }

    // We never "claim" this annotation. Dagger has to process it, too. If we set true here, then
    // component implementations are not generated.
    return false;
  }

  private static class EnvironmentVariableKey {
    public static EnvironmentVariableKey fromDependency(Dependency dependency) {
      final AnnotationMirror qualifier = dependency.getQualifier().orElseThrow(() -> {
        return new IllegalArgumentException("Dependency must have qualifier");
      });

      if (!qualifier.getAnnotationType().toString()
          .equals(EnvironmentVariable.class.getCanonicalName())) {
        throw new IllegalArgumentException("Dependency qualifier must be @EnvironmentVariable");
      }

      final TypeMirror type = dependency.getType();
      final String name = extractEnvironmentVariable(qualifier);
      final String defaultValue = extractDefaultValue(qualifier);

      return new EnvironmentVariableKey(type, name, defaultValue);
    }

    private final TypeMirror type;
    private final String name;
    private final String defaultValue;

    public EnvironmentVariableKey(TypeMirror type, String name, String defaultValue) {
      this.type = requireNonNull(type);
      this.name = requireNonNull(name);
      this.defaultValue = defaultValue;
    }

    public TypeMirror getType() {
      return type;
    }

    public String getName() {
      return name;
    }

    public Optional<String> getDefaultValue() {
      return Optional.ofNullable(defaultValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(defaultValue, name, type);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      EnvironmentVariableKey other = (EnvironmentVariableKey) obj;
      return Objects.equals(defaultValue, other.defaultValue) && Objects.equals(name, other.name)
          && Objects.equals(type, other.type);
    }

    @Override
    public String toString() {
      return "EnvironmentVariableKey [type=" + type + ", name=" + name + ", defaultValue="
          + defaultValue + "]";
    }
  }


  private static class EnvironmentVariableDefinition
      implements Comparable<EnvironmentVariableDefinition> {
    public static EnvironmentVariableDefinition fromKey(EnvironmentVariableKey key) {
      return new EnvironmentVariableDefinition(key.getName(), key.getDefaultValue().orElse(null));
    }

    private final String name;
    private final String defaultValue;

    public EnvironmentVariableDefinition(String name, String defaultValue) {
      this.name = requireNonNull(name);
      this.defaultValue = defaultValue;
    }

    public String getName() {
      return name;
    }

    public Optional<String> getDefaultValue() {
      return Optional.ofNullable(defaultValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(defaultValue, name);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      EnvironmentVariableKey other = (EnvironmentVariableKey) obj;
      return Objects.equals(defaultValue, other.defaultValue) && Objects.equals(name, other.name);
    }

    @Override
    public String toString() {
      return "EnvironmentVariableKey [name=" + name + ", defaultValue=" + defaultValue + "]";
    }

    private static final Comparator<EnvironmentVariableDefinition> COMPARATOR =
        Comparator.comparing(EnvironmentVariableDefinition::getName)
            .thenComparing(Comparator.comparing(d -> d.getDefaultValue().orElse(null),
                Comparator.nullsFirst(Comparator.naturalOrder())));

    @Override
    public int compareTo(EnvironmentVariableDefinition that) {
      return COMPARATOR.compare(this, that);
    }
  }

  private void processComponent(TypeElement component) {
    getMessager().printMessage(Diagnostic.Kind.NOTE,
        "Found @Component: " + component.getQualifiedName());

    final String componentPackageName =
        getElements().getPackageOf(component).getQualifiedName().toString();
    final String componentClassName = component.getSimpleName().toString();
    final String moduleClassName = "Rapier" + componentClassName + "EnvironmentVariableModule";

    final DaggerComponentAnalysis analysis =
        new DaggerComponentAnalyzer(getProcessingEnv()).analyzeComponent(component);

    final List<EnvironmentVariableKey> environmentVariables =
        analysis
            .getDependencies().stream().filter(
                d -> d.getQualifier().isPresent())
            .filter(
                d -> getTypes()
                    .isSameType(d.getQualifier().get().getAnnotationType(),
                        getElements().getTypeElement(EnvironmentVariable.class.getCanonicalName())
                            .asType()))
            .map(EnvironmentVariableKey::fromDependency).collect(toList());

    final SortedMap<EnvironmentVariableDefinition, Set<TypeMirror>> environmentVariablesByDefinition =
        environmentVariables.stream().collect(groupingBy(EnvironmentVariableDefinition::fromKey,
            TreeMap::new, mapping(EnvironmentVariableKey::getType, toSet())));

    try {
      // TODO Is this the right set of elements?
      final Element[] dependentElements = new Element[] {component};
      final JavaFileObject o =
          getFiler().createSourceFile(componentPackageName.equals("") ? moduleClassName
              : componentPackageName + "." + moduleClassName, dependentElements);
      try (final PrintWriter writer = new PrintWriter(o.openWriter())) {
        if (!componentPackageName.equals("")) {
          writer.println("package " + componentPackageName + ";");
          writer.println();
        }
        writer.println("import static java.util.Collections.unmodifiableMap;");
        writer.println();
        writer.println("import " + EnvironmentVariable.class.getName() + ";");
        writer.println("import dagger.Module;");
        writer.println("import dagger.Provides;");
        writer.println("import dagger.Reusable;");
        writer.println("import java.util.Map;");
        writer.println("import java.util.Optional;");
        writer.println("import javax.annotation.Nullable;");
        writer.println("import javax.inject.Inject;");
        writer.println();
        writer.println("@Module");
        writer.println("public class " + moduleClassName + " {");
        writer.println("    private final Map<String, String> env;");
        writer.println();
        writer.println("    @Inject");
        writer.println("    public " + moduleClassName + "() {");
        writer.println("        this(System.getenv());");
        writer.println("    }");
        writer.println();
        writer.println("    public " + moduleClassName + "(Map<String, String> env) {");
        writer.println("        this.env = unmodifiableMap(env);");
        writer.println("    }");
        writer.println();
        for (Map.Entry<EnvironmentVariableDefinition, Set<TypeMirror>> e : environmentVariablesByDefinition
            .entrySet()) {
          final EnvironmentVariableDefinition definition = e.getKey();
          final String name = definition.getName();
          final String defaultValue = definition.getDefaultValue().orElse(null);
          final List<TypeMirror> types = e.getValue().stream()
              .sorted(Comparator.comparing(t -> convertType(t).toString())).collect(toList());

          final StringBuilder baseMethodName =
              new StringBuilder().append("provideEnvironmentVariable")
                  .append(CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name));
          if (defaultValue != null) {
            baseMethodName.append("WithDefaultValue").append(defaultValueSignature(defaultValue));
          }

          if (defaultValue != null) {
            writer.println("    // " + name + " default value " + defaultValue);
          } else {
            writer.println("    // " + name);
          }

          final boolean nullable = (defaultValue == null);

          if (nullable) {
            writer.println("    @Provides");
            writer.println("    @Reusable");
            writer.println("    @Nullable");
            writer.println("    @EnvironmentVariable(\"" + name + "\")");
            writer.println("    public String " + baseMethodName + "() {");
            writer.println("        return env.get(\"" + name + "\");");
            writer.println("    }");
          } else {
            writer.println("    @Provides");
            writer.println("    @Reusable");
            writer.println("    @EnvironmentVariable(value=\"" + name + "\", defaultValue=\""
                + Java.escapeString(defaultValue) + "\")");
            writer.println("    public String " + baseMethodName + "() {");
            writer.println("        return Optional.ofNullable(env.get(\"" + name + "\")).orElse(\""
                + defaultValue.replace("\"", "\\\"") + "\");");
            writer.println("    }");
          }
          writer.println();

          for (TypeMirror type : types) {
            if (type.toString().equals("java.lang.String")) {
              // Skip the String type since we've already provided it
              continue;
            }

            final String expr = expr(type).orElse(null);
            if (expr == null) {
              getMessager().printMessage(Diagnostic.Kind.ERROR,
                  "Cannot convert " + type + " from a string");
              continue;
            }

            final String typeSimpleName = getTypes().asElement(type).getSimpleName().toString();

            if (nullable) {
              writer.println("    @Provides");
              writer.println("    @Nullable");
              writer.println("    @EnvironmentVariable(\"" + name + "\")");
              writer.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@Nullable @EnvironmentVariable(\"" + name + "\") String value) {");
              writer.println("        return value != null ? " + expr + " : null;");
              writer.println("    }");
              writer.println();
            } else {
              writer.println("    @Provides");
              writer.println("    @EnvironmentVariable(value=\"" + name + "\", defaultValue=\""
                  + Java.escapeString(defaultValue) + "\")");
              writer.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@EnvironmentVariable(value=\"" + name + "\", defaultValue=\""
                  + Java.escapeString(defaultValue) + "\") String value) {");
              writer.println("        return " + expr + ";");
              writer.println("    }");
              writer.println();
            }
          }
        }
        writer.println("}");
      }
    } catch (IOException e) {
      e.printStackTrace();
      getMessager().printMessage(Diagnostic.Kind.ERROR,
          "Failed to create source file: " + e.getMessage());
    }
  }

  private Optional<String> expr(TypeMirror type) {
    // Get the TypeElement representing the declared type
    TypeElement typeElement = (TypeElement) getTypes().asElement(type);

    // Iterate through the enclosed elements to find the "valueOf" method
    Optional<ExecutableElement> maybeValueOfMethod = findValueOfMethod(typeElement);
    if (maybeValueOfMethod.isPresent()) {
      return Optional.of(type.toString() + ".valueOf(value)");
    }

    // Iterate through the enclosed elements to find the "fromString" method
    Optional<ExecutableElement> maybeFromStringMethod = findFromStringMethod(typeElement);
    if (maybeFromStringMethod.isPresent()) {
      return Optional.of(type.toString() + ".fromString(value)");
    }

    // Iterate through the enclosed elements to find the constructor that takes a string
    Optional<ExecutableElement> maybeStringConstructor = findStringConstructor(typeElement);
    if (maybeStringConstructor.isPresent()) {
      return Optional.of("new " + type.toString() + "(value)");
    }

    return Optional.empty();
  }

  private Optional<ExecutableElement> findValueOfMethod(TypeElement typeElement) {
    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.METHOD)
        continue;
      final ExecutableElement method = (ExecutableElement) enclosed;
      if (method.getSimpleName().contentEquals("valueOf")
          && method.getModifiers().contains(Modifier.PUBLIC)
          && method.getModifiers().contains(Modifier.STATIC) && method.getParameters().size() == 1
          && getTypes().isSameType(method.getParameters().get(0).asType(),
              getElements().getTypeElement("java.lang.String").asType())
          && getTypes().isSameType(method.getReturnType(), typeElement.asType())) {
        return Optional.of(method);
      }
    }
    return Optional.empty();

  }

  private Optional<ExecutableElement> findFromStringMethod(TypeElement typeElement) {
    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.METHOD)
        continue;
      final ExecutableElement method = (ExecutableElement) enclosed;
      if (method.getSimpleName().contentEquals("fromString")
          && method.getModifiers().contains(Modifier.PUBLIC)
          && method.getModifiers().contains(Modifier.STATIC) && method.getParameters().size() == 1
          && getTypes().isSameType(method.getParameters().get(0).asType(),
              getElements().getTypeElement("java.lang.String").asType())
          && getTypes().isSameType(method.getReturnType(), typeElement.asType())) {
        return Optional.of(method);
      }
    }
    return Optional.empty();

  }

  private Optional<ExecutableElement> findStringConstructor(TypeElement typeElement) {
    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.CONSTRUCTOR)
        continue;
      final ExecutableElement method = (ExecutableElement) enclosed;
      if (method.getModifiers().contains(Modifier.PUBLIC) && method.getParameters().size() == 1
          && getTypes().isSameType(method.getParameters().get(0).asType(),
              getElements().getTypeElement("java.lang.String").asType())) {
        return Optional.of(method);
      }
    }
    return Optional.empty();
  }

  private TypeMirror convertType(TypeMirror type) {
    if (type.getKind() == TypeKind.BYTE) {
      return getElements().getTypeElement(Byte.class.getCanonicalName()).asType();
    } else if (type.getKind() == TypeKind.SHORT) {
      return getElements().getTypeElement(Short.class.getCanonicalName()).asType();
    } else if (type.getKind() == TypeKind.INT) {
      return getElements().getTypeElement(Integer.class.getCanonicalName()).asType();
    } else if (type.getKind() == TypeKind.LONG) {
      return getElements().getTypeElement(Long.class.getCanonicalName()).asType();
    } else if (type.getKind() == TypeKind.FLOAT) {
      return getElements().getTypeElement(Float.class.getCanonicalName()).asType();
    } else if (type.getKind() == TypeKind.DOUBLE) {
      return getElements().getTypeElement(Double.class.getCanonicalName()).asType();
    } else if (type.getKind() == TypeKind.BOOLEAN) {
      return getElements().getTypeElement(Boolean.class.getCanonicalName()).asType();
    } else {
      return type;
    }
  }

  private static String extractEnvironmentVariable(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(EnvironmentVariable.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("value")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            return s;
          }
        }, null)).orElseThrow(() -> {
          return new AssertionError("No string value for @EnvironmentVariable");
        });
  }

  private static String extractDefaultValue(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(EnvironmentVariable.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("defaultValue")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            if (s.equals(EnvironmentVariable.DEFAULT_VALUE_NOT_SET))
              return null;
            return s;
          }
        }, null)).orElse(null);
  }

  private ProcessingEnvironment getProcessingEnv() {
    return processingEnv;
  }

  private Elements getElements() {
    return getProcessingEnv().getElementUtils();
  }

  private Types getTypes() {
    return getProcessingEnv().getTypeUtils();
  }

  private Filer getFiler() {
    return getProcessingEnv().getFiler();
  }

  private Messager getMessager() {
    return getProcessingEnv().getMessager();
  }

  private static String defaultValueSignature(String defaultValue) {
    if (defaultValue == null)
      throw new NullPointerException();
    final byte[] digest = Hashing.md5(defaultValue);
    final String hex = Hex.encode(digest);
    // Use the first 7 characters of the hash as the signature. If it's good enough for git, it's
    // good enough for us!
    return hex.substring(0, 7);
  }
}
