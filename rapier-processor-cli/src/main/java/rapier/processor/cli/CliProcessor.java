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
import java.io.StringWriter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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
import rapier.core.ConversionExprFactory;
import rapier.core.DaggerComponentAnalyzer;
import rapier.core.RapierProcessorBase;
import rapier.core.conversion.expr.ConversionExprFactoryChain;
import rapier.core.conversion.expr.FromStringConversionExprFactory;
import rapier.core.conversion.expr.SingleArgumentConstructorConversionExprFactory;
import rapier.core.conversion.expr.ValueOfConversionExprFactory;
import rapier.core.model.DaggerInjectionSite;
import rapier.core.util.AnnotationProcessing;
import rapier.core.util.CaseFormat;
import rapier.core.util.Java;
import rapier.core.util.MoreSets;
import rapier.processor.cli.model.BindingMetadata;
import rapier.processor.cli.model.FlagKey;
import rapier.processor.cli.model.NamedKey;
import rapier.processor.cli.model.OptionParameterKey;
import rapier.processor.cli.model.PositionalParameterKey;
import rapier.processor.cli.model.PositionalRepresentationKey;
import rapier.processor.cli.util.CliProcessing;

@SupportedAnnotationTypes("dagger.Component")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class CliProcessor extends RapierProcessorBase {
  private ConversionExprFactory stringConverter;
  private ConversionExprFactory listOfStringConverter;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    stringConverter = new ConversionExprFactoryChain(new ValueOfConversionExprFactory(getTypes()),
        new FromStringConversionExprFactory(getTypes()),
        new SingleArgumentConstructorConversionExprFactory(getTypes()));

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

    final List<DaggerInjectionSite> qualifiedInjectionSites =
        new DaggerComponentAnalyzer(getProcessingEnv()).analyzeComponent(component)
            .getInjectionSites().stream().filter(d -> d.getQualifier().isPresent())
            .collect(toList());

    final Map<PositionalRepresentationKey, List<DaggerInjectionSite>> positionals =
        qualifiedInjectionSites.stream().filter(d -> getTypes().isSameType(
            d.getQualifier().get().getAnnotationType(),
            getElements().getTypeElement(PositionalCliParameter.class.getCanonicalName()).asType()))
            .map(d -> {
              try {
                return Map.entry(PositionalRepresentationKey.fromInjectionSite(d), d);
              } catch (IllegalArgumentException e) {
                getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), d.getElement());
                return null;
              }
            }).filter(Objects::nonNull)
            .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));

    final Map<NamedKey, List<DaggerInjectionSite>> nameds = qualifiedInjectionSites.stream()
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

    final Map<FlagKey, List<DaggerInjectionSite>> flags = qualifiedInjectionSites.stream()
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
    final Set<Integer> positions = positionals.keySet().stream()
        .map(PositionalRepresentationKey::getPosition).collect(toSet());
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

    final Map<PositionalParameterKey, List<DaggerInjectionSite>> injectionSitesByPositionalParameter =
        qualifiedInjectionSites.stream()
            .map(dis -> Map.entry(PositionalParameterKey.fromInjectionSite(dis), dis))
            .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));

    final Map<PositionalParameterKey, BindingMetadata> positionalMetadata = new HashMap<>();
    for (Map.Entry<PositionalParameterKey, List<DaggerInjectionSite>> e : injectionSitesByPositionalParameter
        .entrySet()) {
      final PositionalParameterKey key = e.getKey();
      final List<DaggerInjectionSite> injectionSites = e.getValue();

      final Map<Boolean, List<DaggerInjectionSite>> injectionSitesByRequired =
          injectionSites.stream()
              .collect(Collectors.groupingBy(
                  dis -> CliProcessing.isRequired(dis.isNullable(),
                      PositionalRepresentationKey.fromInjectionSite(dis).getDefaultValue()),
                  toList()));
      if (injectionSitesByRequired.size() > 1) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Conflicting requiredness for positional parameter " + key.getPosition()
                + ", will be treated as required");
        // TODO Print examples of each requiredness
      }

      final boolean list = lists.iterator().next();

      // TODO How do we determine list-ness?
      // First, if any of the injection sites are List<String>, then the parameter is a list
      // Next, if any of the injection sites are List<X>, where there is a conversion from String to
      // X, that is a list.
      // Next, if any of the injection sites are X, where there is a conversion from List<String> to
      // X, that is a list.
      // But if there is also a conversion from String to X, then it is an error due to ambiguity.

      positionalMetadata.put(new PositionalParameterKey(key.getPosition()),
          new BindingMetadata(nullable, list));
    }

    final Map<EnvironmentVariableKey, List<DaggerInjectionSite>> environmentVariables = analysis
        .getInjectionSites().stream()
        .filter(d -> getTypes().isSameType(d.getQualifier().get().getAnnotationType(),
            getElements().getTypeElement(EnvironmentVariable.class.getCanonicalName()).asType()))
        .collect(groupingBy(EnvironmentVariableKey::fromInjectionSite, toList()));


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
          out.println("package " + componentPackageName + ";");
          out.println();
        }
        out.println("import static java.util.Collections.unmodifiableMap;");
        out.println();
        out.println("import " + EnvironmentVariable.class.getName() + ";");
        out.println("import dagger.Module;");
        out.println("import dagger.Provides;");
        out.println("import java.util.Map;");
        out.println("import java.util.Optional;");
        out.println("import javax.annotation.Nullable;");
        out.println("import javax.inject.Inject;");
        out.println();
        out.println("@Module");
        out.println("public class " + moduleClassName + " {");
        out.println("    private final Map<String, String> env;");
        out.println();
        out.println("    @Inject");
        out.println("    public " + moduleClassName + "() {");
        out.println("        this(System.getenv());");
        out.println("    }");
        out.println();
        out.println("    public " + moduleClassName + "(Map<String, String> env) {");
        out.println("        this.env = unmodifiableMap(env);");
        out.println("    }");
        out.println();
        for (Map.Entry<EnvironmentVariableKey, PositionalBindingMetadata> e : environmentVariablesAndMetadata
            .entrySet()) {
          final EnvironmentVariableKey key = e.getKey();
          final TypeMirror type = key.getType();
          final String name = key.getName();
          final String defaultValue = key.getDefaultValue().orElse(null);
          final PositionalBindingMetadata metadata = e.getValue();
          final boolean nullable = metadata.isRequired();

          final StringBuilder baseMethodName =
              new StringBuilder().append("provideEnvironmentVariable")
                  .append(CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name));
          if (defaultValue != null) {
            baseMethodName.append("WithDefaultValue").append(stringSignature(defaultValue));
          }

          if (getTypes().isSameType(type, stringType)) {
            if (defaultValue != null) {
              out.println("    @Provides");
              out.println("    @EnvironmentVariable(value=\"" + name + "\", defaultValue=\""
                  + Java.escapeString(defaultValue) + "\")");
              out.println("    public String " + baseMethodName + "AsString() {");
              out.println("        return Optional.ofNullable(env.get(\"" + name + "\")).orElse(\""
                  + Java.escapeString(defaultValue) + "\");");
              out.println("    }");
              out.println();
            } else if (nullable) {
              out.println("    @Provides");
              out.println("    @Nullable");
              out.println("    @EnvironmentVariable(\"" + name + "\")");
              out.println("    public String " + baseMethodName + "AsString() {");
              out.println("        return env.get(\"" + name + "\");");
              out.println("    }");
              out.println();
              out.println("    @Provides");
              out.println("    @EnvironmentVariable(\"" + name + "\")");
              out.println("    public Optional<String> " + baseMethodName
                  + "AsOptionalOfString(@Nullable @EnvironmentVariable(\"" + name
                  + "\") String value) {");
              out.println("        return Optional.ofNullable(value);");
              out.println("    }");
              out.println();
            } else {
              out.println("    @Provides");
              out.println("    @EnvironmentVariable(\"" + name + "\")");
              out.println("    public String " + baseMethodName + "AsString() {");
              out.println("        String result=env.get(\"" + name + "\");");
              out.println("        if (result == null)");
              out.println("            throw new IllegalStateException(\"Environment variable "
                  + name + " not set\");");
              out.println("        return result;");
              out.println("    }");
              out.println();
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
              out.println("    @Provides");
              out.println("    @EnvironmentVariable(value=\"" + name + "\", defaultValue=\""
                  + Java.escapeString(defaultValue) + "\")");
              out.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@EnvironmentVariable(value=\"" + name + "\", defaultValue=\""
                  + Java.escapeString(defaultValue) + "\") String value) {");
              out.println("        return " + conversionExpr + ";");
              out.println("    }");
              out.println();
            } else if (nullable) {
              out.println("    @Provides");
              out.println("    @Nullable");
              out.println("    @EnvironmentVariable(\"" + name + "\")");
              out.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@Nullable @EnvironmentVariable(\"" + name + "\") String value) {");
              out.println("        return value != null ? " + conversionExpr + " : null;");
              out.println("    }");
              out.println();
              out.println("    @Provides");
              out.println("    @EnvironmentVariable(\"" + name + "\")");
              out.println("    public Optional<" + type + "> " + baseMethodName + "AsOptionalOf"
                  + typeSimpleName + "(@EnvironmentVariable(\"" + name
                  + "\") Optional<String> o) {");
              out.println("        return o.map(value -> " + conversionExpr + ");");
              out.println("    }");
              out.println();
            } else {
              out.println("    @Provides");
              out.println("    @EnvironmentVariable(\"" + name + "\")");
              out.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@EnvironmentVariable(\"" + name + "\") String value) {");
              out.println("        " + type + " result= " + conversionExpr + ";");
              out.println("        if (result == null)");
              out.println("            throw new IllegalStateException(\"Environment variable "
                  + name + " " + " as " + type + " not set\");");
              out.println("        return result;");
              out.println("    }");
              out.println();
            }
          }
        }
        out.println("}");
      }
    } catch (IOException e) {
      e.printStackTrace();
      getMessager().printMessage(Diagnostic.Kind.ERROR,
          "Failed to create source file: " + e.getMessage());
    }
  }

  private static final Comparator<PositionalParameterKey> POSITIONAL_PARAMETER_KEY_COMPARATOR =
      Comparator.comparingInt(PositionalParameterKey::getPosition);

  private static final Comparator<OptionParameterKey> OPTION_PARAMETER_KEY_COMPARATOR = Comparator
      .<OptionParameterKey, String>comparing(opk -> opk.getLongName().orElse(null),
          Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparing(opk -> opk.getShortName().orElse(null),
          Comparator.nullsLast(Comparator.naturalOrder()));

  private String generateModuleSource(String packageName, String moduleClassName,
      List<DaggerInjectionSite> injectionSites,
      PositionalParameterMetadataService positionalMetadataService) {

    final SortedMap<PositionalParameterKey, List<PositionalRepresentationKey>> positionalRepresentationsByParameter =
        injectionSites.stream().flatMap(dis -> {
          try {
            return Stream.of(entry(PositionalParameterKey.fromInjectionSite(dis),
                PositionalRepresentationKey.fromInjectionSite(dis)));
          } catch (IllegalArgumentException e) {
            return Stream.empty();
          }
        }).collect(
            groupingBy(Map.Entry::getKey, () -> new TreeMap<>(POSITIONAL_PARAMETER_KEY_COMPARATOR),
                mapping(Map.Entry::getValue, toList())));

    final SortedMap<OptionParameterKey, List<PositionalRepresentationKey>> optionRepresentationsByParameter =
        injectionSites.stream().flatMap(dis -> {
          try {
            return Stream.of(entry(OptionParameterKey.fromInjectionSite(dis),
                PositionalRepresentationKey.fromInjectionSite(dis)));
          } catch (IllegalArgumentException e) {
            return Stream.empty();
          }
        }).collect(
            groupingBy(Map.Entry::getKey, () -> new TreeMap<>(OPTION_PARAMETER_KEY_COMPARATOR),
                mapping(Map.Entry::getValue, toList())));

    final StringWriter result = new StringWriter();
    try (PrintWriter out = new PrintWriter(result)) {
      out.println("package " + packageName + ";");
      out.println();
      out.println("import static java.util.Arrays.asList;");
      out.println("import static java.util.Collections.emptyList;");
      out.println();
      out.println("import dagger.Module;");
      out.println("import dagger.Provides;");
      out.println("import java.util.List;");
      out.println("import java.util.Optional;");
      out.println("import javax.annotation.Nullable;");
      out.println();
      out.println("@Module");
      out.println("public class " + moduleClassName + " {");
      out.println();

      // Generate instance fields for each positional parameter representation
      for (PositionalParameterKey ppk : positionalRepresentationsByParameter.keySet()) {
        final int position = ppk.getPosition();
        final BindingMetadata m =
            positionalMetadataService.getPositionalParameterMetadata(position);
        final boolean parameterIsRequired = m.isRequired();
        final boolean parameterIsList = m.isList();
        emitPositionalParameterInstanceFieldDeclaration(out, position, parameterIsRequired,
            parameterIsList);
      }

      out.println("    public " + moduleClassName + "(String[]) {");
      out.println("        this(asList(args));");
      out.println("    }");
      out.println();


      out.println("    public " + moduleClassName + "(List<String> args) {");
      out.println("        final JustArgs.ParsedArgs parsed = JustArgs.parseArgs(args);");
      out.println();

      // Generate the initialization of each positional parameter representation
      for (PositionalParameterKey ppk : positionalRepresentationsByParameter.keySet()) {
        final int position = ppk.getPosition();
        final BindingMetadata m =
            positionalMetadataService.getPositionalParameterMetadata(position);
        final boolean parameterIsRequired = m.isRequired();
        final boolean parameterIsList = m.isList();
        emitPositionalParameterInstanceFieldInitClause(out, position, parameterIsRequired,
            parameterIsList);
      }

      out.println("    }");
      out.println();

      // Generate the binding methods for each positional parameter representation
      for (Map.Entry<PositionalParameterKey, List<PositionalRepresentationKey>> e : positionalRepresentationsByParameter
          .entrySet()) {
        final PositionalParameterKey parameter = e.getKey();
        final BindingMetadata metadata =
            positionalMetadataService.getPositionalParameterMetadata(parameter.getPosition());
        final List<PositionalRepresentationKey> representations = e.getValue();
        final int position = parameter.getPosition();
        final boolean parameterIsRequired = metadata.isRequired();
        final boolean parameterIsList = metadata.isList();
        for (PositionalRepresentationKey representation : representations) {
          final TypeMirror representationType = representation.getType();
          final String representationDefaultValue = representation.getDefaultValue().orElse(null);
          emitPositionalParameterRepresentationBindingMethods(out, position, parameterIsRequired,
              parameterIsList, representationType, representationDefaultValue);
        }
      }

      out.println("}");
    }

    return result.toString();
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // POSITIONAL PARAMETER CODE GENERATION //////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Emits the instance field declaration for a positional parameter representation.
   * 
   * @param out the PrintWriter to write to
   * @param position the position of the positional parameter
   * @param positionalMetadataService the positional metadata service
   */
  private void emitPositionalParameterInstanceFieldDeclaration(PrintWriter out, int position,
      boolean parameterIsRequired, boolean parameterIsList) {
    final String fieldName = positionalParameterInstanceFieldName(position);
    out.println("    /**");
    out.println("     * Position " + position);
    out.println("     */");
    if (parameterIsList) {
      out.println("    private final List<String> " + fieldName + ";");
    } else {
      out.println("    private final String " + fieldName + ";");
    }
    out.println();
  }

  /**
   * Emits the clause that initializes a positional parameter field.
   * 
   * @param out the PrintWriter to write to
   * @param position the position of the positional parameter
   * @param parameterIsRequired whether the parameter is required
   * @param parameterIsList whether the parameter is a list
   */
  private void emitPositionalParameterInstanceFieldInitClause(PrintWriter out, int position,
      boolean parameterIsRequired, boolean parameterIsList) {
    final String fieldName = positionalParameterInstanceFieldName(position);

    final String extractValueExpr;
    if (parameterIsList) {
      extractValueExpr = "parsed.getArgs().subList(" + position + ", parsed.getArgs().size())";
    } else {
      extractValueExpr = "parsed.getArgs().get(" + position + ")";
    }

    final String defaultValueExpr = "null";

    out.println("        if(parsed.getArgs().size() > " + position + ") {");
    out.println("            this." + fieldName + " = " + extractValueExpr + ";");
    out.println("        } else {");
    if (parameterIsRequired) {
      out.println("            throw new IllegalArgumentException(");
      out.println("                \"Missing required positional parameter " + position + "\");");
    } else {
      out.println("            this." + fieldName + " = " + defaultValueExpr + ";");
    }
    out.println("        }");
    out.println();
  }

  /**
   * Emits the binding methods for a positional parameter representation.
   * 
   * @param out the PrintWriter to write to
   * @param position the position of the positional parameter
   * @param parameterIsRequired whether the parameter is required
   * @param parameterIsList whether the parameter is a list
   * @param representationType the type of the representation, e.g., String
   * @param representationDefaultValue the default value of the representation, if any
   */
  private void emitPositionalParameterRepresentationBindingMethods(PrintWriter out, int position,
      boolean parameterIsRequired, boolean parameterIsList, TypeMirror representationType,
      String representationDefaultValue) {
    final String fieldName = positionalParameterInstanceFieldName(position);

    final StringBuilder baseMethodName =
        new StringBuilder().append("providePositional").append(position);
    if (representationDefaultValue != null) {
      baseMethodName.append("WithDefaultValue").append(stringSignature(representationDefaultValue));
    }

    if (parameterIsList == true
        && getTypes().isSameType(representationType, getListOfStringType())) {
      // This is a varargs parameter, and we're generating the "default" binding.
      if (representationDefaultValue != null) {
        final String defaultValueExpr = "\"" + Java.escapeString(representationDefaultValue) + "\"";
        out.println("    @Provides");
        out.println("    @CliPositionalParameter(value=" + position + ", defaultValue="
            + defaultValueExpr + ")");
        out.println("    public List<String> " + baseMethodName + "AsListOfString() {");
        out.println("        if(" + fieldName + " == null)");
        out.println("            return singletonList(" + defaultValueExpr + ");");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else if (parameterIsRequired) {
        out.println("    @Provides");
        out.println("    @CliPositionalParameter(" + position + ")");
        out.println("    public List<String> " + baseMethodName + "AsListOfString() {");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else {
        // We never generate a nullable list of strings. We just use empty list.
        out.println("    @Provides");
        out.println("    @CliPositionalParameter(" + position + ")");
        out.println("    public List<String> " + baseMethodName + "AsListOfString() {");
        out.println("        if(" + fieldName + " == null)");
        out.println("            return emptyList();");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
        out.println("    @Provides");
        out.println("    @CliPositionalParameter(" + position + ")");
        out.println("    public Optional<List<String>> " + baseMethodName
            + "AsOptionalOfString(@CliPositionalParameter(" + position + ") List<String> value) {");
        out.println("        return Optional.of(value);");
        out.println("    }");
        out.println();
      }
    } else if (parameterIsList == false
        && getTypes().isSameType(representationType, getStringType())) {
      // This is a single positional parameter, and we are generating the "default" binding
      if (representationDefaultValue != null) {
        final String defaultValueExpr = "\"" + Java.escapeString(representationDefaultValue) + "\"";
        out.println("    @Provides");
        out.println("    @CliPositionalParameter(value=" + position + ", defaultValue="
            + defaultValueExpr + ")");
        out.println("    public String " + baseMethodName + "AsString() {");
        out.println("        if(" + fieldName + " == null)");
        out.println("            return " + defaultValueExpr + ";");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else if (parameterIsRequired) {
        out.println("    @Provides");
        out.println("    @CliPositionalParameter(" + position + ")");
        out.println("    public String " + baseMethodName + "AsString() {");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else {
        out.println("    @Provides");
        out.println("    @Nullable");
        out.println("    @CliPositionalParameter(" + position + ")");
        out.println("    public String " + baseMethodName + "AsString() {");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
        out.println("    @Provides");
        out.println("    @CliPositionalParameter(" + position + ")");
        out.println("    public Optional<String> " + baseMethodName
            + "AsOptionalOfString(@Nullable @CliPositionalParameter(" + position
            + ") String value) {");
        out.println("        return Optional.ofNullable(value);");
        out.println("    }");
        out.println();
      }
    } else {
      final String typeSimpleName = getSimpleTypeName(representationType);
      if (parameterIsList) {
        final String conversionExpr = getConverter()
            .generateConversionExpr(representationType, getStringType(), "value").orElse(null);
        if (conversionExpr == null) {
          getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Cannot convert " + representationType + " from " + getStringType());
          continue;
        }

        if (representationDefaultValue != null) {
          // We don't need to check nullability here because the default value "protects" us
          // from any possible null values.
          final String defaultValueExpr =
              "\"" + Java.escapeString(representationDefaultValue) + "\"";
          out.println("    @Provides");
          out.println("    @CliPositionalParameter(value=" + position + ", defaultValue="
              + defaultValueExpr + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@CliPositionalParameter(value=" + position + ", defaultValue="
              + defaultValueExpr + " ) List<String> value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else if (parameterIsRequired) {
          // We don't need to check for null here because we already did in the constructor
          out.println("    @Provides");
          out.println("    @CliPositionalParameter(" + position + ")");
          out.println(
              "    public " + representationType + " " + baseMethodName + "As" + typeSimpleName
                  + "(@CliPositionalParameter(" + position + ") List<String> value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else {
          // We never generate a nullable list of strings. We just use empty list.
          out.println("    @Provides");
          out.println("    @CliPositionalParameter(" + position + ")");
          out.println(
              "    public " + representationType + " " + baseMethodName + "As" + typeSimpleName
                  + "(@CliPositionalParameter(" + position + ") List<String> value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
          out.println("    @Provides");
          out.println("    @CliPositionalParameter(" + position + ")");
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(@CliPositionalParameter(" + position
              + ") Optional<List<String>> o) {");
          out.println("        return o.map(value -> " + conversionExpr + ");");
          out.println("    }");
          out.println();
        }
      } else {
        final String conversionExpr = getConverter()
            .generateConversionExpr(representationType, getStringType(), "value").orElse(null);
        if (conversionExpr == null) {
          getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Cannot convert " + representationType + " from " + getStringType());
          continue;
        }

        if (representationDefaultValue != null) {
          // We don't need to check nullability here because the default value "protects" us
          // from any possible null values.
          final String defaultValueExpr =
              "\"" + Java.escapeString(representationDefaultValue) + "\"";
          out.println("    @Provides");
          out.println("    @CliPositionalParameter(value=" + position + ", defaultValue="
              + defaultValueExpr + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@CliPositionalParameter(value=" + position + ", defaultValue="
              + defaultValueExpr + " ) String value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else if (parameterIsRequired) {
          // We don't need to check for null here because we already did in the constructor
          out.println("    @Provides");
          out.println("    @CliPositionalParameter(" + position + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@CliPositionalParameter(" + position + ") String value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else {
          out.println("    @Provides");
          out.println("    @Nullable");
          out.println("    @CliPositionalParameter(" + position + ")");
          out.println(
              "    public " + representationType + " " + baseMethodName + "As" + typeSimpleName
                  + "(@Nullable @CliPositionalParameter(" + position + ") String value) {");
          out.println("        if(value == null)");
          out.println("            return null;");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
          out.println("    @Provides");
          out.println("    @CliPositionalParameter(" + position + ")");
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(@CliPositionalParameter(" + position
              + ") Optional<String> o) {");
          out.println("        return o.map(value -> " + conversionExpr + ");");
          out.println("    }");
          out.println();
        }
      }
    }
  }

  private String positionalParameterInstanceFieldName(int position) {
    return "positional" + position;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // OPTION PARAMETER CODE GENERATION //////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Emits the instance field declaration for a option parameter representation.
   * 
   * @param out the PrintWriter to write to
   * @param position the position of the positional parameter
   * @param positionalMetadataService the positional metadata service
   */
  private void emitOptionParameterInstanceFieldDeclaration(PrintWriter out, Character shortName,
      String longName, boolean parameterIsRequired, boolean parameterIsList) {
    final String fieldName = optionParameterInstanceFieldName(shortName, longName);
    out.println("    /**");
    out.println("     * Option parameter");
    if (shortName != null) {
      out.println("     * shortName " + shortName);
    }
    if (longName != null) {
      out.println("     * longName " + longName);
    }
    out.println("     */");
    if (parameterIsList) {
      out.println("    private final List<String> " + fieldName + ";");
    } else {
      out.println("    private final String " + fieldName + ";");
    }
    out.println();
  }


  /**
   * Emits the instance field declaration for a option parameter representation.
   * 
   * @param out the PrintWriter to write to
   * @param position the position of the positional parameter
   * @param positionalMetadataService the positional metadata service
   */
  private void emitOptionParameterInstanceFieldInitPreparation(PrintWriter out,
      List<OptionParameterKey> parameters) {
    for (OptionParameterKey parameter : parameters) {
      final Character shortName = parameter.getShortName().orElse(null);
      if (shortName == null)
        continue;
      final String parameterSignature =
          optionParameterSignature(shortName, parameter.getLongName().orElse(null));
      out.println(
          "        optionShortNames.put(\"" + shortName + "\", \"" + parameterSignature + "\");");
    }
    for (OptionParameterKey parameter : parameters) {
      final String longName = parameter.getLongName().orElse(null);
      if (longName == null)
        continue;
      final String parameterSignature =
          optionParameterSignature(parameter.getShortName().orElse(null), longName);
      out.println(
          "        optionLongNames.put(\"" + longName + "\", \"" + parameterSignature + "\");");
    }
    out.println();
  }


  /**
   * Emits the clause that initializes an option parameter field.
   * 
   * @param out the PrintWriter to write to
   * @param shortName the short name of the option parameter
   * @param longName the long name of the option parameter
   * @param parameterIsRequired whether the parameter is required
   * @param parameterIsList whether the parameter is a list
   */
  private void emitOptionParameterInstanceFieldInitClause(PrintWriter out, Character shortName,
      String longName, boolean parameterIsRequired, boolean parameterIsList) {
    final String fieldName = optionParameterInstanceFieldName(shortName, longName);
    final String signature = optionParameterSignature(shortName, longName);
    final String userFacingString = optionParameterUserFacingString(shortName, longName);

    out.println("        if(parsed.getOptions().containsKey(\"" + signature + "\")) {");
    out.println("            List<String> " + fieldName + " = parsed.getOptions().get(\""
        + signature + "\");");
    if (parameterIsList) {
      out.println("            this." + fieldName + " = unmodifiableList(" + fieldName + "));");
    } else {
      out.println("            this." + fieldName + " = " + fieldName + ".get(" + fieldName
          + ".size()-1);");
    }
    out.println("        } else {");
    if (parameterIsRequired) {
      out.println("            throw new IllegalArgumentException(");
      out.println(
          "                \"Missing required option parameter " + userFacingString + "\");");
    }
    out.println("            this." + fieldName + " = null;");
    out.println("        }");
    out.println();
  }


  /**
   * Emits the binding methods for a positional parameter representation.
   * 
   * @param out the PrintWriter to write to
   * @param position the position of the positional parameter
   * @param parameterIsRequired whether the parameter is required
   * @param parameterIsList whether the parameter is a list
   * @param representationType the type of the representation, e.g., String
   * @param representationDefaultValue the default value of the representation, if any
   */
  private void emitOptionParameterRepresentationBindingMethods(PrintWriter out, Character shortName,
      String longName, boolean parameterIsRequired, boolean parameterIsList,
      TypeMirror representationType, String representationDefaultValue) {
    final String fieldName = optionParameterInstanceFieldName(shortName, longName);
    final String signature = optionParameterSignature(shortName, longName);
    final String shortNameClause = shortName != null ? "shortName='" + shortName + "'" : null;
    final String longNameClause = longName != null ? "longName=\"" + longName + "\"" : null;
    final String nameClauses = Stream.of(shortNameClause, longNameClause).filter(Objects::nonNull)
        .collect(Collectors.joining(", "));

    final StringBuilder baseMethodName =
        new StringBuilder().append("provideOption").append(signature);
    if (representationDefaultValue != null) {
      baseMethodName.append("WithDefaultValue").append(stringSignature(representationDefaultValue));
    }

    if (parameterIsList == true
        && getTypes().isSameType(representationType, getListOfStringType())) {
      // This is a varargs parameter, and we're generating the "default" binding.
      if (representationDefaultValue != null) {
        final String defaultValueExpr = "\"" + Java.escapeString(representationDefaultValue) + "\"";
        out.println("    @Provides");
        out.println(
            "    @CliOptionParameter(" + nameClauses + ", defaultValue=" + defaultValueExpr + ")");
        out.println("    public List<String> " + baseMethodName + "AsListOfString() {");
        out.println("        if(" + fieldName + " == null)");
        out.println("            return singletonList(" + defaultValueExpr + ");");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else if (parameterIsRequired) {
        // We don't need to check for null here because we already did in the constructor
        out.println("    @Provides");
        out.println("    @CliOptionParameter(" + nameClauses + ")");
        out.println("    public List<String> " + baseMethodName + "AsListOfString() {");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else {
        // We never generate a nullable list of strings. We just use empty list.
        out.println("    @Provides");
        out.println("    @CliOptionParameter(" + nameClauses + ")");
        out.println("    public List<String> " + baseMethodName + "AsListOfString() {");
        out.println("        if(" + fieldName + " == null)");
        out.println("            return emptyList();");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
        out.println("    @Provides");
        out.println("    @CliOptionParameter(" + nameClauses + ")");
        out.println("    public Optional<List<String>> " + baseMethodName
            + "AsOptionalOfString(@CliOptionParameter(" + nameClauses + ") List<String> value) {");
        out.println("        return Optional.of(value);");
        out.println("    }");
        out.println();
      }
    } else if (parameterIsList == false
        && getTypes().isSameType(representationType, getStringType())) {
      // This is a single positional parameter, and we are generating the "default" binding
      if (representationDefaultValue != null) {
        final String defaultValueExpr = "\"" + Java.escapeString(representationDefaultValue) + "\"";
        out.println("    @Provides");
        out.println(
            "    @CliOptionParameter(" + nameClauses + ", defaultValue=" + defaultValueExpr + ")");
        out.println("    public String " + baseMethodName + "AsString() {");
        out.println("        if(" + fieldName + " == null)");
        out.println("            return " + defaultValueExpr + ";");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else if (parameterIsRequired) {
        out.println("    @Provides");
        out.println("    @CliOptionParameter(" + nameClauses + ")");
        out.println("    public String " + baseMethodName + "AsString() {");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else {
        out.println("    @Provides");
        out.println("    @Nullable");
        out.println("    @CliOptionParameter(" + nameClauses + ")");
        out.println("    public String " + baseMethodName + "AsString() {");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
        out.println("    @Provides");
        out.println("    @CliOptionParameter(" + nameClauses + ")");
        out.println("    public Optional<String> " + baseMethodName
            + "AsOptionalOfString(@Nullable @CliPositionalParameter(" + nameClauses
            + ") String value) {");
        out.println("        return Optional.ofNullable(value);");
        out.println("    }");
        out.println();
      }
    } else {
      final String typeSimpleName = getSimpleTypeName(representationType);
      if (parameterIsList) {
        final String conversionExpr = getConverter()
            .generateConversionExpr(representationType, getStringType(), "value").orElse(null);
        if (conversionExpr == null) {
          getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Cannot convert " + representationType + " from " + getStringType());
          continue;
        }

        if (representationDefaultValue != null) {
          // We don't need to check nullability here because the default value "protects" us
          // from any possible null values.
          final String defaultValueExpr =
              "\"" + Java.escapeString(representationDefaultValue) + "\"";
          out.println("    @Provides");
          out.println("    @CliPositionalParameter(value=" + position + ", defaultValue="
              + defaultValueExpr + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@CliPositionalParameter(value=" + position + ", defaultValue="
              + defaultValueExpr + " ) List<String> value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else if (parameterIsRequired) {
          // We don't need to check for null here because we already did in the constructor
          out.println("    @Provides");
          out.println("    @CliPositionalParameter(" + position + ")");
          out.println(
              "    public " + representationType + " " + baseMethodName + "As" + typeSimpleName
                  + "(@CliPositionalParameter(" + position + ") List<String> value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else {
          // We never generate a nullable list of strings. We just use empty list.
          out.println("    @Provides");
          out.println("    @CliPositionalParameter(" + position + ")");
          out.println(
              "    public " + representationType + " " + baseMethodName + "As" + typeSimpleName
                  + "(@CliPositionalParameter(" + position + ") List<String> value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
          out.println("    @Provides");
          out.println("    @CliPositionalParameter(" + position + ")");
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(@CliPositionalParameter(" + position
              + ") Optional<List<String>> o) {");
          out.println("        return o.map(value -> " + conversionExpr + ");");
          out.println("    }");
          out.println();
        }
      } else {
        final String conversionExpr = getConverter()
            .generateConversionExpr(representationType, getStringType(), "value").orElse(null);
        if (conversionExpr == null) {
          getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Cannot convert " + representationType + " from " + getStringType());
          continue;
        }

        if (representationDefaultValue != null) {
          // We don't need to check nullability here because the default value "protects" us
          // from any possible null values.
          final String defaultValueExpr =
              "\"" + Java.escapeString(representationDefaultValue) + "\"";
          out.println("    @Provides");
          out.println("    @CliPositionalParameter(value=" + position + ", defaultValue="
              + defaultValueExpr + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@CliPositionalParameter(value=" + position + ", defaultValue="
              + defaultValueExpr + " ) String value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else if (parameterIsRequired) {
          // We don't need to check for null here because we already did in the constructor
          out.println("    @Provides");
          out.println("    @CliPositionalParameter(" + position + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@CliPositionalParameter(" + position + ") String value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else {
          out.println("    @Provides");
          out.println("    @Nullable");
          out.println("    @CliPositionalParameter(" + position + ")");
          out.println(
              "    public " + representationType + " " + baseMethodName + "As" + typeSimpleName
                  + "(@Nullable @CliPositionalParameter(" + position + ") String value) {");
          out.println("        if(value == null)");
          out.println("            return null;");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
          out.println("    @Provides");
          out.println("    @CliPositionalParameter(" + position + ")");
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(@CliPositionalParameter(" + position
              + ") Optional<String> o) {");
          out.println("        return o.map(value -> " + conversionExpr + ");");
          out.println("    }");
          out.println();
        }
      }
    }
  }

  private String optionParameterInstanceFieldName(Character shortName, String longName) {
    return "option" + optionParameterSignature(shortName, longName);
  }

  private String optionParameterUserFacingString(Character shortName, String longName) {
    if (shortName != null && longName != null) {
      return "-" + shortName + " / --" + longName;
    }
    if (shortName != null) {
      return "-" + shortName;
    }
    if (longName != null) {
      return "--" + longName;
    }
    throw new IllegalArgumentException("shortName and longName cannot both be null");
  }

  private String optionParameterSignature(Character shortName, String longName) {
    final String[] parts = new String[2];
    parts[0] = shortName == null ? "" : shortName.toString();
    parts[1] = longName == null ? "" : longName;
    return String.join("/", parts);
  }


  private void foo(Map<PositionalParameterKey, BindingMetadata> positionals) {
    final int count = positionals.size();

    boolean seenOptionalParameter = false;
    boolean seenListParameter = false;
    for (int i = 0; i < count; i++) {
      final PositionalParameterKey parameter = new PositionalParameterKey(i);
      final BindingMetadata metadata = positionals.get(parameter);

      if (seenOptionalParameter == false && !metadata.isRequired()) {
        seenOptionalParameter = true;
      } else if (seenOptionalParameter == true && metadata.isRequired()) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Required positional parameter after optional parameter");
      }

      if (seenListParameter == false && metadata.isList()) {
        seenListParameter = true;
      } else if (seenListParameter == true) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Varargs positional parameter must be last parameter");
      }
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

  private transient TypeMirror listOfStringType;

  private TypeMirror getListOfStringType() {
    if (listOfStringType == null) {
      listOfStringType = getTypes().getDeclaredType(getElements().getTypeElement("java.util.List"),
          getStringType());
    }
    return listOfStringType;
  }

  private static <K, V> Map.Entry<K, V> entry(K key, V value) {
    return new AbstractMap.SimpleImmutableEntry<>(key, value);
  }
}
