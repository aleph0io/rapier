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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
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
import rapier.core.model.DaggerComponentAnalysis;
import rapier.core.model.DaggerInjectionSite;
import rapier.core.util.CaseFormat;
import rapier.core.util.Java;
import rapier.core.util.MoreSets;
import rapier.processor.envvar.model.ParameterKey;
import rapier.processor.envvar.model.ParameterMetadata;
import rapier.processor.envvar.model.RepresentationKey;
import rapier.processor.envvar.util.EnvironmentVariables;

@SupportedAnnotationTypes("dagger.Component")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class EnvironmentVariableProcessor extends RapierProcessorBase {
  private ConversionExprFactory converter;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    converter = new ConversionExprFactoryChain(new ValueOfConversionExprFactory(getTypes()),
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

  private static final Comparator<RepresentationKey> INJECTION_SITE_KEY_COMPARATOR =
      Comparator.comparing(RepresentationKey::getName)
          .thenComparing(Comparator.comparing(x -> x.getDefaultValue().orElse(null),
              Comparator.nullsFirst(Comparator.naturalOrder())))
          .thenComparing(x -> x.getType().toString());



  private void processComponent(TypeElement component) {
    getMessager().printMessage(Diagnostic.Kind.NOTE,
        "Found @Component: " + component.getQualifiedName());

    final String componentPackageName =
        getElements().getPackageOf(component).getQualifiedName().toString();
    final String componentClassName = component.getSimpleName().toString();
    final String moduleClassName = "Rapier" + componentClassName + "EnvironmentVariableModule";

    // This is the annotation that we are looking for.
    final TypeMirror relevantQualifierType =
        getElements().getTypeElement(EnvironmentVariable.class.getName()).asType();

    // Analyze the component to find all relevant injection sites.
    final DaggerComponentAnalysis analysis =
        new DaggerComponentAnalyzer(getProcessingEnv()).analyzeComponent(component);
    final List<DaggerInjectionSite> relevantInjectionSites = analysis.getDependencies().stream()
        .filter(d -> d.getQualifier().isPresent()).filter(d -> getTypes()
            .isSameType(d.getQualifier().orElseThrow().getAnnotationType(), relevantQualifierType))
        .collect(toList());

    // Collect all injection sites by parameter. This should include all injection sites.
    final Map<ParameterKey, List<DaggerInjectionSite>> injectionSitesByParameter =
        relevantInjectionSites.stream()
            .map(dis -> Map.entry(ParameterKey.fromInjectionSite(dis), dis))
            .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));

    // Compute per-parameter metadata.
    final Map<ParameterKey, ParameterMetadata> parameterMetadata =
        injectionSitesByParameter.entrySet().stream().map(e -> {
          final ParameterKey key = e.getKey();
          final List<DaggerInjectionSite> injectionSites = e.getValue();

          final Map<Boolean, List<DaggerInjectionSite>> injectionSitesByNullable =
              injectionSites.stream()
                  .collect(Collectors.partitioningBy(DaggerInjectionSite::isNullable, toList()));
          if (injectionSitesByNullable.size() > 1) {
            // If there is more than one opinion about nullability across all injection sites, then
            // we should generate a warning because the user is expressing differing opinions about
            // whether or not this parameter is nullable.
            getMessager().printMessage(Diagnostic.Kind.WARNING,
                "Conflicting nullability for environment variable " + key.getName()
                    + ", will be treated as non-nullable");
            // TODO Print the conflicting dependencies, with element and annotation references
          }

          // The parameter is only actually nullable if all of its injection sites are nullable.
          // If any injection site is not nullable, then the parameter writ large cannot be null,
          // so the parameter is not nullable.
          final boolean nullable =
              injectionSitesByNullable.keySet().stream().noneMatch(b -> b == false);

          final Map<Boolean, List<DaggerInjectionSite>> injectionSitesByRequired = injectionSites
              .stream()
              .collect(Collectors.partitioningBy(
                  dis -> EnvironmentVariables.isRequired(dis.isNullable(), EnvironmentVariables
                      .extractEnvironmentVariableDefaultValue(dis.getQualifier().orElseThrow())),
                  toList()));
          if (injectionSitesByRequired.size() > 1) {
            // If there is more than one opinion about requiredness across all injection sites, then
            // we should generate a warning because the user is expressing differing opinions about
            // whether or not this parameter is required.
            //
            // Remember, a parameter is required if it is not nullable and does not have a default
            // value. If a parameter is required at any injection site, then it is de-facto required
            // at all injection sites for the application to function correctly.
            getMessager().printMessage(Diagnostic.Kind.WARNING,
                "Conflicting requiredness for environment variable " + key.getName()
                    + ", will be treated as required");
            // TODO Print the conflicting dependencies, with element and annotation references
          }

          // The parameter is required if any injection site is required.
          final boolean required =
              injectionSitesByRequired.keySet().stream().anyMatch(b -> b == true);

          return Map.entry(key, new ParameterMetadata(required, nullable));
        }).filter(Objects::nonNull).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    // Collect all injection sites by representation.
    final SortedMap<RepresentationKey, List<DaggerInjectionSite>> injectionSitesByRepresentation =
        relevantInjectionSites.stream()
            .map(dis -> Map.entry(RepresentationKey.fromInjectionSite(dis), dis)).collect(
                groupingBy(Map.Entry::getKey, () -> new TreeMap<>(INJECTION_SITE_KEY_COMPARATOR),
                    mapping(Map.Entry::getValue, toCollection(ArrayList::new))));

    // Make sure every representation has an equivalent representation with a string type. This is
    // a requirement because of the way we generate the module code.
    for (Map.Entry<RepresentationKey, List<DaggerInjectionSite>> e : injectionSitesByRepresentation
        .entrySet()) {
      final RepresentationKey representation = e.getKey();
      final List<DaggerInjectionSite> injectionSites = e.getValue();
      final ParameterKey parameter = ParameterKey.fromRepresentationKey(representation);
      final ParameterMetadata metadata = parameterMetadata.get(parameter);
      final boolean parameterIsNullable = metadata.isNullable();
      final boolean parameterIsRequired = metadata.isRequired();

      for (DaggerInjectionSite injectionSite : injectionSites) {
        final boolean injectionSiteIsNullable = injectionSite.isNullable();
        final boolean injectionSiteIsRequired = EnvironmentVariables
            .isRequired(injectionSiteIsNullable, representation.getDefaultValue().orElse(null));

        // Are we nullability-compatible?
        if (injectionSiteIsNullable && !parameterIsNullable) {
          getMessager().printMessage(
              Diagnostic.Kind.WARNING, "Effectively non-nullable environment variable "
                  + representation.getName() + " is treated as nullable",
              injectionSite.getElement());
        }
        if (!injectionSiteIsNullable && parameterIsNullable) {
          // This should never happen, per the above logic.
          // TODO How should we best handle this?
          throw new AssertionError("Found non-nullable injection site for nullable parameter");
        }

        // Are we requiredness-compatible?
        if (!injectionSiteIsRequired && parameterIsRequired) {
          getMessager().printMessage(
              Diagnostic.Kind.WARNING, "Effectively required environment variable "
                  + representation.getName() + " is treated as non-required",
              injectionSite.getElement());
        }
        if (injectionSiteIsRequired && !parameterIsRequired) {
          // This should never happen, per the above logic.
          // TODO How should we best handle this?
          throw new AssertionError("Found required injection site for non-required parameter");
        }
      }
    }

    // Make sure every representation has an equivalent representation with a string type. This is
    // a requirement because of the way we generate the module code.
    final TypeMirror stringType = getElements().getTypeElement("java.lang.String").asType();
    for (Map.Entry<RepresentationKey, List<DaggerInjectionSite>> e : MoreSets
        .copyOf(injectionSitesByRepresentation.entrySet())) {
      final RepresentationKey representation = e.getKey();

      if (getTypes().isSameType(representation.getType(), stringType))
        continue;

      final RepresentationKey representationAsString = new RepresentationKey(stringType,
          representation.getName(), representation.getDefaultValue().orElse(null));

      if (!injectionSitesByRepresentation.containsKey(representationAsString)) {
        injectionSitesByRepresentation.put(representationAsString, emptyList());
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
        for (RepresentationKey key : injectionSitesByRepresentation.keySet()) {
          final TypeMirror type = key.getType();
          final String name = key.getName();
          final String defaultValue = key.getDefaultValue().orElse(null);
          final ParameterMetadata metadata =
              parameterMetadata.get(ParameterKey.fromRepresentationKey(key));
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
                getConverter().generateConversionExpr(type, stringType, "value").orElse(null);
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

  private ConversionExprFactory getConverter() {
    return converter;
  }
}
