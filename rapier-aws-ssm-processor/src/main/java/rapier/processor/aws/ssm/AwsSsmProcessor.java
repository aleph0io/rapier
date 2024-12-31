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
package rapier.processor.aws.ssm;

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
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import dagger.Component;
import rapier.processor.core.DaggerComponentAnalyzer;
import rapier.processor.core.RapierProcessorBase;
import rapier.processor.core.model.DaggerComponentAnalysis;
import rapier.processor.core.model.Dependency;
import rapier.processor.core.util.AnnotationProcessing;
import rapier.processor.core.util.CaseFormat;
import rapier.processor.core.util.Java;

@SupportedAnnotationTypes("dagger.Component")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class AwsSsmProcessor extends RapierProcessorBase {
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

  public static enum AwsSsmParameterType {
    STRING("String", "AwsSsmStringParameter", "String"), STRING_LIST("StringList",
        "AwsSsmStringListParameter", "List<String>");

    private final String methodPart;
    private final String annotationName;
    private final String javaType;

    private AwsSsmParameterType(String methodPart, String annotationName, String javaType) {
      this.methodPart = requireNonNull(methodPart);
      this.annotationName = requireNonNull(annotationName);
      this.javaType = requireNonNull(javaType);
    }

    public String getMethodPart() {
      return methodPart;
    }

    public String getAnnotationName() {
      return annotationName;
    }

    public String getJavaType() {
      return javaType;
    }
  }

  private static class AwsSsmParameterKey {
    public static AwsSsmParameterKey fromDependency(Dependency dependency) {
      final AnnotationMirror qualifier = dependency.getQualifier().orElseThrow(() -> {
        return new IllegalArgumentException("Dependency must have qualifier");
      });

      AwsSsmParameterType parameterType;
      if (qualifier.getAnnotationType().toString().equals(AwsSsmStringParameter.class.getName())) {
        parameterType = AwsSsmParameterType.STRING;
      } else if (qualifier.getAnnotationType().toString()
          .equals(AwsSsmStringListParameter.class.getName())) {
        parameterType = AwsSsmParameterType.STRING_LIST;
      } else {
        throw new IllegalArgumentException(
            "Dependency qualifier must be @AwsSsmStringParameter or @AwsSsmStringListParameter");
      }

      final TypeMirror valueType = dependency.getType();
      final String name = extractParameterName(qualifier);
      final String defaultValue = extractEnvironmentVariableDefaultValue(qualifier);

      return new AwsSsmParameterKey(valueType, parameterType, name, defaultValue);
    }

    private final TypeMirror valueType;
    private final AwsSsmParameterType parameterType;
    private final String name;
    private final String defaultValue;

    public AwsSsmParameterKey(TypeMirror type, AwsSsmParameterType parameterType, String name,
        String defaultValue) {
      this.valueType = requireNonNull(type);
      this.parameterType = requireNonNull(parameterType);
      this.name = requireNonNull(name);
      this.defaultValue = defaultValue;
    }

    public TypeMirror getValueType() {
      return valueType;
    }

    public AwsSsmParameterType getParameterType() {
      return parameterType;
    }

    public String getName() {
      return name;
    }

    public Optional<String> getDefaultValue() {
      return Optional.ofNullable(defaultValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(defaultValue, name, parameterType, valueType);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      AwsSsmParameterKey other = (AwsSsmParameterKey) obj;
      return Objects.equals(defaultValue, other.defaultValue) && Objects.equals(name, other.name)
          && parameterType == other.parameterType && Objects.equals(valueType, other.valueType);
    }

    @Override
    public String toString() {
      return "AwsSsmParameterKey [valueType=" + valueType + ", parameterType=" + parameterType
          + ", name=" + name + ", defaultValue=" + defaultValue + "]";
    }
  }


  private static class AwsSsmParameterDefinition implements Comparable<AwsSsmParameterDefinition> {
    public static AwsSsmParameterDefinition fromKey(AwsSsmParameterKey key) {
      return new AwsSsmParameterDefinition(key.getParameterType(), key.getName(),
          key.getDefaultValue().orElse(null));
    }

    private final AwsSsmParameterType parameterType;
    private final String name;
    private final String defaultValue;

    public AwsSsmParameterDefinition(AwsSsmParameterType parameterType, String name,
        String defaultValue) {
      this.parameterType = requireNonNull(parameterType);
      this.name = requireNonNull(name);
      this.defaultValue = defaultValue;
    }

    public AwsSsmParameterType getParameterType() {
      return parameterType;
    }

    public String getName() {
      return name;
    }

    public Optional<String> getDefaultValue() {
      return Optional.ofNullable(defaultValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(defaultValue, name, parameterType);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      AwsSsmParameterDefinition other = (AwsSsmParameterDefinition) obj;
      return Objects.equals(defaultValue, other.defaultValue) && Objects.equals(name, other.name)
          && parameterType == other.parameterType;
    }

    @Override
    public String toString() {
      return "EnvironmentVariableDefinition [parameterType=" + parameterType + ", name=" + name
          + ", defaultValue=" + defaultValue + "]";
    }

    private static final Comparator<AwsSsmParameterDefinition> COMPARATOR =
        Comparator.comparing(AwsSsmParameterDefinition::getParameterType)
            .thenComparing(AwsSsmParameterDefinition::getName)
            .thenComparing(Comparator.comparing(d -> d.getDefaultValue().orElse(null),
                Comparator.nullsFirst(Comparator.naturalOrder())));

    @Override
    public int compareTo(AwsSsmParameterDefinition that) {
      return COMPARATOR.compare(this, that);
    }

  }

  private void processComponent(TypeElement component) {
    getMessager().printMessage(Diagnostic.Kind.NOTE,
        "Found @Component: " + component.getQualifiedName());

    final String componentPackageName =
        getElements().getPackageOf(component).getQualifiedName().toString();
    final String componentClassName = component.getSimpleName().toString();
    final String moduleClassName = "Rapier" + componentClassName + "AwsSsmModule";

    final DaggerComponentAnalysis analysis =
        new DaggerComponentAnalyzer(getProcessingEnv()).analyzeComponent(component);

    final List<AwsSsmParameterKey> environmentVariables = analysis.getDependencies().stream()
        .filter(d -> d.getQualifier().isPresent())
        .filter(d -> getTypes().isSameType(d.getQualifier().get().getAnnotationType(),
            getElements().getTypeElement(AwsSsmStringParameter.class.getCanonicalName()).asType())
            || getTypes().isSameType(d.getQualifier().get().getAnnotationType(), getElements()
                .getTypeElement(AwsSsmStringListParameter.class.getCanonicalName()).asType()))
        .map(AwsSsmParameterKey::fromDependency).collect(toList());

    final SortedMap<AwsSsmParameterDefinition, Set<TypeMirror>> environmentVariablesByDefinition =
        environmentVariables.stream().collect(groupingBy(AwsSsmParameterDefinition::fromKey,
            TreeMap::new, mapping(AwsSsmParameterKey::getValueType, toSet())));

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
        writer.println("import " + AwsSsmStringParameter.class.getName() + ";");
        writer.println("import " + AwsSsmStringListParameter.class.getName() + ";");
        writer.println("import dagger.Module;");
        writer.println("import dagger.Provides;");
        writer.println("import java.util.Map;");
        writer.println("import java.util.Optional;");
        writer.println("import javax.annotation.Nullable;");
        writer.println("import javax.inject.Inject;");
        writer.println("import javax.inject.Singleton;");
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
        for (Map.Entry<AwsSsmParameterDefinition, Set<TypeMirror>> e : environmentVariablesByDefinition
            .entrySet()) {
          final AwsSsmParameterDefinition definition = e.getKey();
          final AwsSsmParameterType parameterType = definition.getParameterType();
          final String name = definition.getName();
          final String defaultValue = definition.getDefaultValue().orElse(null);
          final List<TypeMirror> types = e.getValue().stream()
              .sorted(Comparator.comparing(t -> unpack(t).toString())).collect(toList());

          final StringBuilder baseMethodName = new StringBuilder().append("provide")
              .append(parameterType.getAnnotationName()).append("Parameter")
              .append(CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name));
          if (defaultValue != null) {
            baseMethodName.append("WithDefaultValue").append(stringSignature(defaultValue));
          }

          if (defaultValue != null) {
            writer.println("    // " + name + " " + parameterType.getMethodPart()
                + " default value " + defaultValue);
          } else {
            writer.println("    // " + name + " " + parameterType.getMethodPart());
          }

          final boolean nullable = (defaultValue == null);

          if (nullable) {
            writer.println("    @Provides");
            writer.println("    @Singleton");
            writer.println("    @Nullable");
            writer.println("    @" + parameterType.getAnnotationName() + "(\"" + name + "\")");
            writer.println(
                "    public " + parameterType.getJavaType() + " " + baseMethodName + "() {");
            writer.println("        return env.get(\"" + name + "\");");
            writer.println("    }");
          } else {
            writer.println("    @Provides");
            writer.println("    @Singleton");
            writer.println("    @" + parameterType.getAnnotationName() + "(value=\"" + name
                + "\", defaultValue=\"" + Java.escapeString(defaultValue) + "\")");
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
              writer.println("    @" + parameterType.getAnnotationName() + "(\"" + name + "\")");
              writer.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@Nullable @" + parameterType.getAnnotationName() + "(\"" + name
                  + "\") String value) {");
              writer.println("        return value != null ? " + expr + " : null;");
              writer.println("    }");
              writer.println();
            } else {
              writer.println("    @Provides");
              writer.println("    @" + parameterType.getAnnotationName() + "(value=\"" + name
                  + "\", defaultValue=\"" + Java.escapeString(defaultValue) + "\")");
              writer.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@" + parameterType.getAnnotationName() + "(value=\"" + name
                  + "\", defaultValue=\"" + Java.escapeString(defaultValue)
                  + "\") String value) {");
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

  /**
   * Returns a string representing a Java expression that creates an instance of the given type from
   * a string value. The expression should be of the form {@code Type.valueOf(value)} or
   * {@code Type.fromString(value)}.
   * 
   * @param type the type
   * @return the expression
   */
  private Optional<String> expr(TypeMirror type) {
    // Get the TypeElement representing the declared type
    final TypeElement typeElement = (TypeElement) getTypes().asElement(type);

    final TypeMirror stringType = getElements().getTypeElement("java.lang.String").asType();

    // Iterate through the enclosed elements to find the "valueOf" method
    Optional<ExecutableElement> maybeValueOfMethod =
        AnnotationProcessing.findValueOfMethod(getTypes(), typeElement, stringType);
    if (maybeValueOfMethod.isPresent()) {
      return Optional.of(type.toString() + ".valueOf(value)");
    }

    // Iterate through the enclosed elements to find the "fromString" method
    Optional<ExecutableElement> maybeFromStringMethod =
        AnnotationProcessing.findFromStringMethod(getTypes(), typeElement);
    if (maybeFromStringMethod.isPresent()) {
      return Optional.of(type.toString() + ".fromString(value)");
    }

    return Optional.empty();
  }

  private static String extractAwsSsmParameterName(AnnotationMirror annotation) {
    final String annotationTypeName = annotation.getAnnotationType().toString();

    assert annotationTypeName.equals(AwsSsmStringParameter.class.getName())
        || annotationTypeName.equals(AwsSsmStringListParameter.class.getName());

    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("value")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            return s;
          }
        }, null)).orElseThrow(() -> {
          return new AssertionError("No string value for @" + annotationTypeName);
        });
  }

  private static String extractAwsSsmStringParameterDefaultValue(AnnotationMirror annotation) {
    final String annotationTypeName = annotation.getAnnotationType().toString();

    assert annotationTypeName.equals(AwsSsmStringParameter.class.getName());

    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("defaultValue")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            if (s.equals(AwsSsmStringParameter.DEFAULT_VALUE_NOT_SET))
              return null;
            return s;
          }
        }, null)).orElse(null);
  }

  private static List<String> extractAwsSsmStringListParameterDefaultValue(
      AnnotationMirror annotation) {
    final String annotationTypeName = annotation.getAnnotationType().toString();

    assert annotationTypeName.equals(AwsSsmStringListParameter.class.getName());

    final AtomicBoolean set = new AtomicBoolean(true);
    final List<String> values = new ArrayList<>();
    annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("defaultValue")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<Void, Void>() {
          @Override
          public Void visitArray(List<? extends AnnotationValue> vals, Void p) {
            if (vals.size() == 1
                && vals.get(0).getValue().equals(AwsSsmStringListParameter.DEFAULT_VALUE_NOT_SET)) {
              set.set(false);
            } else {
              set.set(true);
              for (AnnotationValue val : vals) {
                val.accept(this, null);
              }
            }
            return null;
          }

          @Override
          public Void visitString(String s, Void p) {
            values.add(s);
            return null;
          }
        }, null)).orElse(null);

    return set.get() ? values : null;
  }
}
