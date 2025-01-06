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
package rapier.processor.cli;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.IntStream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
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
import rapier.core.util.MoreSets;
import rapier.processor.cli.model.FlagKey;
import rapier.processor.cli.model.NamedKey;
import rapier.processor.cli.model.PositionalKey;

@SupportedAnnotationTypes("dagger.Component")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class CliProcessor extends RapierProcessorBase {
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

  private void processComponent(TypeElement component) {
    getMessager().printMessage(Diagnostic.Kind.NOTE,
        "Found @Component: " + component.getQualifiedName());

    final String componentPackageName =
        getElements().getPackageOf(component).getQualifiedName().toString();
    final String componentClassName = component.getSimpleName().toString();
    final String moduleClassName = "Rapier" + componentClassName + "EnvironmentVariableModule";

    final DaggerComponentAnalysis analysis =
        new DaggerComponentAnalyzer(getProcessingEnv()).analyzeComponent(component);

    final Map<PositionalKey, List<DaggerInjectionSite>> positionals = analysis.getInjectionSites()
        .stream().filter(d -> d.getQualifier().isPresent())
        .filter(d -> getTypes().isSameType(d.getQualifier().get().getAnnotationType(),
            getElements().getTypeElement(PositionalCliParameter.class.getCanonicalName()).asType()))
        .map(d -> {
          try {
            return Map.entry(PositionalKey.fromDependency(d), d);
          } catch (IllegalArgumentException e) {
            getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), d.getElement());
            return null;
          }
        }).filter(Objects::nonNull)
        .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));

    final Map<NamedKey, List<DaggerInjectionSite>> nameds =
        analysis.getInjectionSites().stream().filter(d -> d.getQualifier().isPresent())
            .filter(d -> getTypes().isSameType(d.getQualifier().get().getAnnotationType(),
                getElements().getTypeElement(NamedCliParameter.class.getCanonicalName()).asType()))
            .map(d -> {
              try {
                return Map.entry(NamedKey.fromDependency(d), d);
              } catch (IllegalArgumentException e) {
                getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), d.getElement());
                return null;
              }
            }).filter(Objects::nonNull)
            .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));

    final Map<FlagKey, List<DaggerInjectionSite>> flags =
        analysis.getInjectionSites().stream().filter(d -> d.getQualifier().isPresent())
            .filter(d -> getTypes().isSameType(d.getQualifier().get().getAnnotationType(),
                getElements().getTypeElement(FlagCliParameter.class.getCanonicalName()).asType()))
            .map(d -> {
              try {
                return Map.entry(FlagKey.fromDependency(d), d);
              } catch (IllegalArgumentException e) {
                getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), d.getElement());
                return null;
              }
            }).filter(Objects::nonNull)
            .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));

    // Make sure we have all the expected positional parameters
    final Set<Integer> positions =
        positionals.keySet().stream().map(PositionalKey::getPosition).collect(toSet());
    final Set<Integer> expectedPositions =
        IntStream.range(0, positions.size()).boxed().collect(toSet());
    if (!positions.equals(expectedPositions)) {
      final Set<Integer> missingPositions = MoreSets.difference(expectedPositions, positions);
      if (!missingPositions.isEmpty())
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Missing positional parameters: " + MoreSets.difference(expectedPositions, positions));
      final Set<Integer> extraPositions = MoreSets.difference(positions, expectedPositions);
      if (!extraPositions.isEmpty())
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Extra positional parameters: " + MoreSets.difference(positions, expectedPositions));
    }

    // Make sure that each named short parameter is only ever used with the same long parameter
    final Map<String, Set<String>> namedLongNamesByShortNames =
        nameds.keySet().stream().filter(n -> n.getShortName().isPresent()).collect(groupingBy(
            n -> n.getShortName().get(), mapping(n -> n.getLongName().orElse(""), toSet())));
    for (Map.Entry<String, Set<String>> e : namedLongNamesByShortNames.entrySet()) {
      final String shortName = e.getKey();
      final Set<String> longNamesForShortName = e.getValue();
      if (longNamesForShortName.size() > 1) {
        // TODO Print exmaples of each conflicting long name
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Conflicting long names for short name " + shortName + ": " + e.getKey());
      }
    }

    // Make sure that each named long parameter is only ever used with the same short parameter
    final Map<String, Set<String>> namedShortNamesByLongNames =
        nameds.keySet().stream().filter(n -> n.getLongName().isPresent()).collect(groupingBy(
            n -> n.getLongName().get(), mapping(n -> n.getShortName().orElse(""), toSet())));
    for (Map.Entry<String, Set<String>> e : namedShortNamesByLongNames.entrySet()) {
      final String longName = e.getKey();
      final Set<String> shortNamesForLongName = e.getValue();
      if (shortNamesForLongName.size() > 1) {
        // TODO Print exmaples of each conflicting short name
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Conflicting short names for long name " + longName + ": " + e.getKey());
      }
    }

    // Make sure that each flag short parameter is only ever used with the same long parameter
    final Map<String, Set<String>> flagLongNamesByShortNames =
        flags.keySet().stream().filter(n -> n.getShortName().isPresent()).collect(groupingBy(
            n -> n.getShortName().get(), mapping(n -> n.getLongName().orElse(""), toSet())));
    for (Map.Entry<String, Set<String>> e : flagLongNamesByShortNames.entrySet()) {
      final String shortName = e.getKey();
      final Set<String> longNamesForShortName = e.getValue();
      if (longNamesForShortName.size() > 1) {
        // TODO Print exmaples of each conflicting long name
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Conflicting long names for short name " + shortName + ": " + e.getKey());
      }
    }

    // Make sure that each flag long parameter is only ever used with the same short parameter
    final Map<String, Set<String>> flagShortNamesByLongNames =
        flags.keySet().stream().filter(n -> n.getLongName().isPresent()).collect(groupingBy(
            n -> n.getLongName().get(), mapping(n -> n.getShortName().orElse(""), toSet())));
    for (Map.Entry<String, Set<String>> e : flagShortNamesByLongNames.entrySet()) {
      final String longName = e.getKey();
      final Set<String> shortNamesForLongName = e.getValue();
      if (shortNamesForLongName.size() > 1) {
        // TODO Print exmaples of each conflicting short name
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Conflicting short names for long name " + longName + ": " + e.getKey());
      }
    }

    // Make sure that each shortName is used either only by named or flag parameters
    if (!MoreSets
        .intersection(namedLongNamesByShortNames.keySet(), flagLongNamesByShortNames.keySet())
        .isEmpty()) {
      // TODO Print exmaples of each conflicting short name
      getMessager().printMessage(Diagnostic.Kind.ERROR,
          "Conflicting named and flag parameter shortName values: " + MoreSets.intersection(
              namedLongNamesByShortNames.keySet(), flagLongNamesByShortNames.keySet()));
    }

    // Make sure that each longName is used either only by named or flag parameters
    if (!MoreSets
        .intersection(namedShortNamesByLongNames.keySet(), flagShortNamesByLongNames.keySet())
        .isEmpty()) {
      // TODO Print exmaples of each conflicting short name
      getMessager().printMessage(Diagnostic.Kind.ERROR,
          "Conflicting named and flag parameter longName values: " + MoreSets.intersection(
              namedShortNamesByLongNames.keySet(), flagShortNamesByLongNames.keySet()));
    }

    final Map<EnvironmentVariableKey, List<DaggerInjectionSite>> environmentVariables = analysis
        .getInjectionSites().stream().filter(d -> d.getQualifier().isPresent())
        .filter(d -> getTypes().isSameType(d.getQualifier().get().getAnnotationType(),
            getElements().getTypeElement(EnvironmentVariable.class.getCanonicalName()).asType()))
        .collect(groupingBy(EnvironmentVariableKey::fromDependency, toList()));


    final SortedMap<EnvironmentVariableKey, PositionalBindingMetadata> environmentVariablesAndMetadata =
        new TreeMap<>();
    for (Map.Entry<EnvironmentVariableKey, List<DaggerInjectionSite>> e : environmentVariables
        .entrySet()) {
      final EnvironmentVariableKey key = e.getKey();
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

      environmentVariablesAndMetadata.put(key, new PositionalBindingMetadata(nullable));
    }

    final TypeMirror stringType = getElements().getTypeElement("java.lang.String").asType();

    // Make sure every environment variable has a string binding
    for (EnvironmentVariableKey key : environmentVariables.keySet()) {
      final PositionalBindingMetadata metadata = environmentVariablesAndMetadata.get(key);
      if (metadata == null)
        continue;
      if (getTypes().isSameType(key.getType(), stringType))
        continue;

      final EnvironmentVariableKey keyAsString =
          new EnvironmentVariableKey(stringType, key.getName(), key.getDefaultValue().orElse(null));

      if (!environmentVariablesAndMetadata.containsKey(keyAsString)) {
        environmentVariablesAndMetadata.put(keyAsString, metadata);
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
        writer.println("import static java.util.Collections.unmodifiableMap;");
        writer.println();
        writer.println("import " + EnvironmentVariable.class.getName() + ";");
        writer.println("import dagger.Module;");
        writer.println("import dagger.Provides;");
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
        for (Map.Entry<EnvironmentVariableKey, PositionalBindingMetadata> e : environmentVariablesAndMetadata
            .entrySet()) {
          final EnvironmentVariableKey key = e.getKey();
          final TypeMirror type = key.getType();
          final String name = key.getName();
          final String defaultValue = key.getDefaultValue().orElse(null);
          final PositionalBindingMetadata metadata = e.getValue();
          final boolean nullable = metadata.isNullable();

          final StringBuilder baseMethodName =
              new StringBuilder().append("provideEnvironmentVariable")
                  .append(CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name));
          if (defaultValue != null) {
            baseMethodName.append("WithDefaultValue").append(stringSignature(defaultValue));
          }

          if (getTypes().isSameType(type, stringType)) {
            if (defaultValue != null) {
              writer.println("    @Provides");
              writer.println("    @EnvironmentVariable(value=\"" + name + "\", defaultValue=\""
                  + Java.escapeString(defaultValue) + "\")");
              writer.println("    public String " + baseMethodName + "AsString() {");
              writer.println("        return Optional.ofNullable(env.get(\"" + name
                  + "\")).orElse(\"" + Java.escapeString(defaultValue) + "\");");
              writer.println("    }");
              writer.println();
            } else if (nullable) {
              writer.println("    @Provides");
              writer.println("    @Nullable");
              writer.println("    @EnvironmentVariable(\"" + name + "\")");
              writer.println("    public String " + baseMethodName + "AsString() {");
              writer.println("        return env.get(\"" + name + "\");");
              writer.println("    }");
              writer.println();
              writer.println("    @Provides");
              writer.println("    @EnvironmentVariable(\"" + name + "\")");
              writer.println("    public Optional<String> " + baseMethodName
                  + "AsOptionalOfString(@Nullable @EnvironmentVariable(\"" + name
                  + "\") String value) {");
              writer.println("        return Optional.ofNullable(value);");
              writer.println("    }");
              writer.println();
            } else {
              writer.println("    @Provides");
              writer.println("    @EnvironmentVariable(\"" + name + "\")");
              writer.println("    public String " + baseMethodName + "AsString() {");
              writer.println("        String result=env.get(\"" + name + "\");");
              writer.println("        if (result == null)");
              writer.println("            throw new IllegalStateException(\"Environment variable "
                  + name + " not set\");");
              writer.println("        return result;");
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
              writer.println("    @EnvironmentVariable(value=\"" + name + "\", defaultValue=\""
                  + Java.escapeString(defaultValue) + "\")");
              writer.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@EnvironmentVariable(value=\"" + name + "\", defaultValue=\""
                  + Java.escapeString(defaultValue) + "\") String value) {");
              writer.println("        return " + conversionExpr + ";");
              writer.println("    }");
              writer.println();
            } else if (nullable) {
              writer.println("    @Provides");
              writer.println("    @Nullable");
              writer.println("    @EnvironmentVariable(\"" + name + "\")");
              writer.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@Nullable @EnvironmentVariable(\"" + name + "\") String value) {");
              writer.println("        return value != null ? " + conversionExpr + " : null;");
              writer.println("    }");
              writer.println();
              writer.println("    @Provides");
              writer.println("    @EnvironmentVariable(\"" + name + "\")");
              writer.println("    public Optional<" + type + "> " + baseMethodName + "AsOptionalOf"
                  + typeSimpleName + "(@EnvironmentVariable(\"" + name
                  + "\") Optional<String> o) {");
              writer.println("        return o.map(value -> " + conversionExpr + ");");
              writer.println("    }");
              writer.println();
            } else {
              writer.println("    @Provides");
              writer.println("    @EnvironmentVariable(\"" + name + "\")");
              writer.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@EnvironmentVariable(\"" + name + "\") String value) {");
              writer.println("        " + type + " result= " + conversionExpr + ";");
              writer.println("        if (result == null)");
              writer.println("            throw new IllegalStateException(\"Environment variable "
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
}
