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
import java.util.regex.Pattern;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import dagger.Component;
import rapier.core.DaggerComponentAnalyzer;
import rapier.core.RapierProcessorBase;
import rapier.core.model.DaggerComponentAnalysis;
import rapier.core.model.DaggerInjectionSite;
import rapier.core.util.AnnotationProcessing;
import rapier.core.util.CaseFormat;
import rapier.core.util.Java;

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

  private static class AwsSsmStringParameterKey implements Comparable<AwsSsmStringParameterKey> {
    public static AwsSsmStringParameterKey fromDependency(DaggerInjectionSite dependency) {
      final AnnotationMirror qualifier = dependency.getQualifier().orElseThrow(() -> {
        return new IllegalArgumentException("Dependency must have qualifier");
      });

      if (!qualifier.getAnnotationType().toString()
          .equals(AwsSsmStringParameter.class.getCanonicalName())) {
        throw new IllegalArgumentException("Dependency qualifier must be @AwsSsmStringParameter");
      }

      final TypeMirror type = dependency.getProvidedType();
      final String name = extractAwsSsmStringParameterName(qualifier);
      final String defaultValue = extractAwsSsmStringParameterDefaultValue(qualifier);

      return new AwsSsmStringParameterKey(type, name, defaultValue);
    }

    private final TypeMirror type;
    private final String name;
    private final String defaultValue;

    public AwsSsmStringParameterKey(TypeMirror type, String name, String defaultValue) {
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

    private final Comparator<AwsSsmStringParameterKey> COMPARATOR =
        Comparator.comparing(AwsSsmStringParameterKey::getName)
            .thenComparing(Comparator.comparing(x -> x.getDefaultValue().orElse(null),
                Comparator.nullsFirst(Comparator.naturalOrder())))
            .thenComparing(x -> x.getType().toString());

    public int compareTo(AwsSsmStringParameterKey that) {
      return COMPARATOR.compare(this, that);
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
      AwsSsmStringParameterKey other = (AwsSsmStringParameterKey) obj;
      return Objects.equals(defaultValue, other.defaultValue) && Objects.equals(name, other.name)
          && Objects.equals(type, other.type);
    }

    @Override
    public String toString() {
      return "AwsSsmStringParameterKey [type=" + type + ", name=" + name + ", defaultValue="
          + defaultValue + "]";
    }
  }

  private static class BindingMetadata {
    private final boolean nullable;

    public BindingMetadata(boolean nullable) {
      this.nullable = nullable;
    }

    public boolean isNullable() {
      return nullable;
    }

    @Override
    public int hashCode() {
      return Objects.hash(nullable);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      BindingMetadata other = (BindingMetadata) obj;
      return nullable == other.nullable;
    }

    @Override
    public String toString() {
      return "BindingMetadata [nullable=" + nullable + "]";
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

    final Map<AwsSsmStringParameterKey, List<DaggerInjectionSite>> parameters = analysis
        .getDependencies().stream().filter(d -> d.getQualifier().isPresent())
        .filter(d -> getTypes().isSameType(d.getQualifier().get().getAnnotationType(),
            getElements().getTypeElement(AwsSsmStringParameter.class.getCanonicalName()).asType()))
        .collect(groupingBy(AwsSsmStringParameterKey::fromDependency, toList()));


    final SortedMap<AwsSsmStringParameterKey, BindingMetadata> parametersAndMetadata =
        new TreeMap<>();
    for (Map.Entry<AwsSsmStringParameterKey, List<DaggerInjectionSite>> e : parameters.entrySet()) {
      final AwsSsmStringParameterKey key = e.getKey();
      final List<DaggerInjectionSite> dependencies = e.getValue();

      final Set<Boolean> nullables =
          dependencies.stream().map(DaggerInjectionSite::isNullable).collect(toSet());
      if (nullables.size() > 1) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Conflicting nullability for environment variable: " + key);
        // TODO Print the conflicting dependencies, with element and annotation references
        continue;
      }

      final boolean nullable = nullables.iterator().next();

      parametersAndMetadata.put(key, new BindingMetadata(nullable));
    }

    final TypeMirror stringType = getElements().getTypeElement("java.lang.String").asType();

    // Make sure every environment variable has a string binding
    for (AwsSsmStringParameterKey key : parameters.keySet()) {
      final BindingMetadata metadata = parametersAndMetadata.get(key);
      if (metadata == null)
        continue;
      if (getTypes().isSameType(key.getType(), stringType))
        continue;

      final AwsSsmStringParameterKey keyAsString = new AwsSsmStringParameterKey(stringType,
          key.getName(), key.getDefaultValue().orElse(null));

      if (!parametersAndMetadata.containsKey(keyAsString)) {
        parametersAndMetadata.put(keyAsString, metadata);
      }
    }

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
        writer.println("import static java.util.Objects.requireNonNull;");
        writer.println();
        writer.println("import " + AwsSsmStringParameter.class.getName() + ";");
        writer.println("import dagger.Module;");
        writer.println("import dagger.Provides;");
        writer.println("import java.util.Map;");
        writer.println("import java.util.Optional;");
        writer.println("import javax.annotation.Nullable;");
        writer.println("import javax.inject.Inject;");
        writer.println("import software.amazon.awssdk.services.ssm.SsmClient;");
        writer.println(
            "import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;");
        writer.println("import software.amazon.awssdk.services.ssm.model.GetParameterRequest;");
        writer.println();
        writer.println("@Module");
        writer.println("public class " + moduleClassName + " {");
        writer.println("    private final SsmClient client;");
        writer.println();
        writer.println("    @Inject");
        writer.println("    public " + moduleClassName + "() {");
        writer.println("        this(SsmClient.create());");
        writer.println("    }");
        writer.println();
        writer.println("    public " + moduleClassName + "(SsmClient client) {");
        writer.println("        this.client = requireNonNull(client);");
        writer.println("    }");
        writer.println();
        for (Map.Entry<AwsSsmStringParameterKey, BindingMetadata> e : parametersAndMetadata
            .entrySet()) {
          final AwsSsmStringParameterKey key = e.getKey();
          final TypeMirror type = key.getType();
          final String name = key.getName();
          final String defaultValue = key.getDefaultValue().orElse(null);
          final BindingMetadata metadata = e.getValue();
          final boolean nullable = metadata.isNullable();

          final StringBuilder baseMethodName = new StringBuilder()
              .append("provideAwsSsmStringParameter").append(CaseFormat.UPPER_UNDERSCORE
                  .to(CaseFormat.UPPER_CAMEL, standardizeParameterName(name)));
          if (defaultValue != null) {
            baseMethodName.append("WithDefaultValue").append(stringSignature(defaultValue));
          }

          if (getTypes().isSameType(type, stringType)) {
            if (defaultValue != null) {
              writer.println("    @Provides");
              writer.println("    @AwsSsmStringParameter(value=\"" + name + "\", defaultValue=\""
                  + Java.escapeString(defaultValue) + "\")");
              writer.println("    public String " + baseMethodName + "AsString() {");
              writer.println("        try {");
              writer.println("            return client");
              writer.println("                .getParameter(b -> b.name(\"" + name + "\"))");
              writer.println("                .parameter()");
              writer.println("                .value();");
              writer.println("        } catch(ParameterNotFoundException e) {");
              writer.println("            return \"" + Java.escapeString(defaultValue) + "\";");
              writer.println("        }");
              writer.println("    }");
              writer.println();
            } else if (nullable) {
              writer.println("    @Provides");
              writer.println("    @Nullable");
              writer.println("    @AwsSsmStringParameter(\"" + name + "\")");
              writer.println("    public String " + baseMethodName + "AsString() {");
              writer.println("        try {");
              writer.println("            return client");
              writer.println("                .getParameter(b -> b.name(\"" + name + "\"))");
              writer.println("                .parameter()");
              writer.println("                .value();");
              writer.println("        } catch(ParameterNotFoundException e) {");
              writer.println("            return null;");
              writer.println("        }");
              writer.println("    }");
              writer.println();
              writer.println("    @Provides");
              writer.println("    @AwsSsmStringParameter(\"" + name + "\")");
              writer.println("    public Optional<String> " + baseMethodName
                  + "AsOptionalOfString(@Nullable @AwsSsmStringParameter(\"" + name
                  + "\") String value) {");
              writer.println("        return Optional.ofNullable(value);");
              writer.println("    }");
              writer.println();
            } else {
              writer.println("    @Provides");
              writer.println("    @AwsSsmStringParameter(\"" + name + "\")");
              writer.println("    public String " + baseMethodName + "AsString() {");
              writer.println("        try {");
              writer.println("            return client");
              writer.println("                .getParameter(b -> b.name(\"" + name + "\"))");
              writer.println("                .parameter()");
              writer.println("                .value();");
              writer.println("        } catch(ParameterNotFoundException e) {");
              writer.println("            throw new IllegalStateException(\"AWS SSM Parameter "
                  + name + " not set\");");
              writer.println("        }");
              writer.println("    }");
              writer.println();
            }
          } else {
            final String conversionExpr =
                generateConversionExpr(type, stringType, "value").orElse(null);
            if (conversionExpr == null) {
              getMessager().printMessage(Diagnostic.Kind.ERROR,
                  "Cannot convert " + type + " from " + stringType);
              continue;
            }

            final String typeSimpleName = getTypes().asElement(type).getSimpleName().toString();

            if (defaultValue != null) {
              writer.println("    @Provides");
              writer.println("    @AwsSsmStringParameter(value=\"" + name + "\", defaultValue=\""
                  + Java.escapeString(defaultValue) + "\")");
              writer.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@AwsSsmStringParameter(value=\"" + name + "\", defaultValue=\""
                  + Java.escapeString(defaultValue) + "\") String value) {");
              writer.println("        return " + conversionExpr + ";");
              writer.println("    }");
              writer.println();
            } else if (nullable) {
              writer.println("    @Provides");
              writer.println("    @Nullable");
              writer.println("    @AwsSsmStringParameter(\"" + name + "\")");
              writer.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@Nullable @AwsSsmStringParameter(\"" + name + "\") String value) {");
              writer.println("        return value != null ? " + conversionExpr + " : null;");
              writer.println("    }");
              writer.println();
              writer.println("    @Provides");
              writer.println("    @AwsSsmStringParameter(\"" + name + "\")");
              writer.println("    public Optional<" + type + "> " + baseMethodName + "AsOptionalOf"
                  + typeSimpleName + "(@AwsSsmStringParameter(\"" + name
                  + "\") Optional<String> o) {");
              writer.println("        return o.map(value -> " + conversionExpr + ");");
              writer.println("    }");
              writer.println();
            } else {
              writer.println("    @Provides");
              writer.println("    @AwsSsmStringParameter(\"" + name + "\")");
              writer.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@AwsSsmStringParameter(\"" + name + "\") String value) {");
              writer.println("        " + type + " result= " + conversionExpr + ";");
              writer.println("        if (result == null)");
              writer.println("            throw new IllegalStateException(\"AWS SSM parameter "
                  + name + " " + " as " + type + " not set\");");
              writer.println("        return result;");
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
   * @param targetType the target type to create
   * @param sourceType the source type to convert from
   * @param sourceValue the name of the variable containing the source value to convert from
   * @return the Java expression, or {@link Optional#empty()} if no conversion is possible
   * 
   */
  private Optional<String> generateConversionExpr(TypeMirror targetType, TypeMirror sourceType,
      String sourceValue) {
    // Get the TypeElement representing the declared type
    final TypeElement targetElement = (TypeElement) getTypes().asElement(targetType);

    // Iterate through the enclosed elements to find the "valueOf" method
    Optional<ExecutableElement> maybeValueOfMethod =
        AnnotationProcessing.findValueOfMethod(getTypes(), targetElement, sourceType);
    if (maybeValueOfMethod.isPresent()) {
      return Optional.of(targetType.toString() + ".valueOf(" + sourceValue + ")");
    }

    // Iterate through the enclosed elements to find the "fromString" method
    if (sourceType.toString().equals("java.lang.String")) {
      Optional<ExecutableElement> maybeFromStringMethod =
          AnnotationProcessing.findFromStringMethod(getTypes(), targetElement);
      if (maybeFromStringMethod.isPresent()) {
        return Optional.of(targetType.toString() + ".fromString(" + sourceValue + ")");
      }
    }

    // Iterate through the enclosed elements to find the single-argument constructor
    Optional<ExecutableElement> maybeConstructor =
        AnnotationProcessing.findSingleArgumentConstructor(getTypes(), targetElement, sourceType);
    if (maybeConstructor.isPresent()) {
      return Optional.of("new " + targetType.toString() + "(" + sourceValue + ")");
    }

    return Optional.empty();
  }

  private static String extractAwsSsmStringParameterName(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(AwsSsmStringParameter.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("value")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            return s;
          }
        }, null)).orElseThrow(() -> {
          return new AssertionError("No string value for @AwsSsmStringParameter");
        });
  }

  private static String extractAwsSsmStringParameterDefaultValue(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(AwsSsmStringParameter.class.getCanonicalName());
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

  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9]+");

  private static String standardizeParameterName(String name) {
    name = NON_ALPHANUMERIC.matcher(name).replaceAll("_").toUpperCase();

    int start = 0;
    while (start < name.length() && name.charAt(start) == '_') {
      start = start + 1;
    }

    int end = name.length();
    while (end > start && name.charAt(end - 1) == '_') {
      end = end - 1;
    }

    return name.substring(start, end).toUpperCase();
  }
}
