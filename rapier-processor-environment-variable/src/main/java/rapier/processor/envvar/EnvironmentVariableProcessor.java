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
import rapier.processor.envvar.model.RepresentationMetadata;
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

    // Collect all injection sites by parameter. This should include all injection sites for the
    // annotation being analyzed.
    final Map<ParameterKey, List<DaggerInjectionSite>> injectionSitesByParameter =
        relevantInjectionSites.stream()
            .map(dis -> Map.entry(ParameterKey.fromInjectionSite(dis), dis))
            .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));

    // Compute per-parameter metadata. Remember, a "parameter" is the logical parameter here, so
    // all injection sites that share the same input.
    final Map<ParameterKey, ParameterMetadata> metadataForParameters =
        injectionSitesByParameter.entrySet().stream().map(e -> {
          final ParameterKey key = e.getKey();
          final List<DaggerInjectionSite> injectionSites = e.getValue();

          final Map<Boolean, List<DaggerInjectionSite>> injectionSitesByRequired = injectionSites
              .stream()
              .collect(Collectors.groupingBy(
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

          return Map.entry(key, new ParameterMetadata(required));
        }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    // Collect all injection sites by representation.
    final SortedMap<RepresentationKey, List<DaggerInjectionSite>> injectionSitesByRepresentation =
        relevantInjectionSites.stream()
            .map(dis -> Map.entry(RepresentationKey.fromInjectionSite(dis), dis)).collect(
                groupingBy(Map.Entry::getKey, () -> new TreeMap<>(INJECTION_SITE_KEY_COMPARATOR),
                    mapping(Map.Entry::getValue, toCollection(ArrayList::new))));

    final Map<RepresentationKey, RepresentationMetadata> metadataForRepresentations =
        injectionSitesByRepresentation.entrySet().stream().map(e -> {
          final RepresentationKey key = e.getKey();
          final List<DaggerInjectionSite> injectionSites = e.getValue();

          final Map<Boolean, List<DaggerInjectionSite>> injectionSitesByNullable = injectionSites
              .stream().collect(Collectors.groupingBy(dis -> dis.isNullable(), toList()));
          if (injectionSitesByNullable.size() > 1) {
            // If there is more than one opinion about nullability across all injection sites, then
            // on face, we should generate a warning because the user is expressing differing
            // opinions about whether or not this parameter is nullable. However, we don't because:
            //
            // - If there are different opinions about nullability at the representation level, then
            // there are also different opinions about requiredness at the parameter level, too.
            // We have already warned about those, so this would be redundant.
            // - If there is a material problem with nullability, then Dagger will fail the compile
            // anyway, so let Dagger handle it.
            //
            // One day we may want to print BETTER error messages than Dagger, but leave it for now.
          }

          // The parameter is nullable if and only if all injection sites are nullable
          final boolean nullable =
              injectionSitesByNullable.keySet().stream().allMatch(b -> b == true);

          return Map.entry(key, new RepresentationMetadata(nullable));
        }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    // Make sure every representation has an equivalent representation with a string type. This is
    // a requirement because of the way we generate the module code.
    for (Map.Entry<RepresentationKey, List<DaggerInjectionSite>> e : injectionSitesByRepresentation
        .entrySet()) {
      final RepresentationKey representation = e.getKey();

      final ParameterKey parameter = ParameterKey.fromRepresentationKey(representation);
      final ParameterMetadata parameterMetadata = metadataForParameters.get(parameter);
      final boolean parameterIsRequired = parameterMetadata.isRequired();

      final List<DaggerInjectionSite> injectionSites = e.getValue();

      for (DaggerInjectionSite injectionSite : injectionSites) {
        final boolean injectionSiteIsNullable = injectionSite.isNullable();
        final boolean injectionSiteHasDefaultValue = representation.getDefaultValue().isPresent();

        if (parameterIsRequired && injectionSiteIsNullable) {
          getMessager().printMessage(
              Diagnostic.Kind.WARNING, "Effectively required environment variable "
                  + representation.getName() + " is treated as nullable",
              injectionSite.getElement());
        }

        if (parameterIsRequired && injectionSiteHasDefaultValue) {
          getMessager()
              .printMessage(
                  Diagnostic.Kind.WARNING, "Effectively required environment variable "
                      + representation.getName() + " has default value",
                  injectionSite.getElement());
        }
      }
    }

    // Make sure every representation has an equivalent representation with a string type. This is
    // a requirement because of the way we generate the module code.
    for (Map.Entry<RepresentationKey, List<DaggerInjectionSite>> e : MoreSets
        .copyOf(injectionSitesByRepresentation.entrySet())) {
      final RepresentationKey representation = e.getKey();

      if (getTypes().isSameType(representation.getType(), getStringType()))
        continue;

      final RepresentationKey representationAsString = toStringRepresentation(representation);

      if (!injectionSitesByRepresentation.containsKey(representationAsString)) {
        // Our code generation strategy involves injecting the environment variable as a string, and
        // then converting it to the desired type. Since there is no injection site for the string
        // representation, we will create one here. Since this is a new representation, we will also
        // create a metadata entry for it. However, is this new representation nullable?
        //
        // If the underlying parameter is required, then the new string representation is required,
        // too, and therefore not nullable.
        //
        // If the underlying parameter is not required, but the representation has a default value,
        // then the new string representation is not nullable, since the default value will be used
        // if the environment variable is not set.
        //
        // If the underlying parameter is not required, and the representation does not have a
        // default value, then the new string representation is nullable.
        final ParameterKey parameter = ParameterKey.fromRepresentationKey(representation);
        final ParameterMetadata parameterMetadata = metadataForParameters.get(parameter);
        final boolean parameterIsRequired = parameterMetadata.isRequired();
        final boolean representationHasDefaultValue = representation.getDefaultValue().isPresent();
        final boolean representationIsNullable =
            !parameterIsRequired && !representationHasDefaultValue;

        injectionSitesByRepresentation.put(representationAsString, emptyList());
        metadataForRepresentations.put(representationAsString,
            new RepresentationMetadata(representationIsNullable));
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
        for (RepresentationKey representation : injectionSitesByRepresentation.keySet()) {
          final TypeMirror type = representation.getType();
          final String name = representation.getName();
          final String representationDefaultValue = representation.getDefaultValue().orElse(null);
          final RepresentationMetadata representationMetadata =
              metadataForRepresentations.get(representation);
          final boolean representationIsNullable = representationMetadata.isNullable();
          final ParameterKey parameter = ParameterKey.fromRepresentationKey(representation);
          final ParameterMetadata parameterMetadata = metadataForParameters.get(parameter);
          final boolean parameterIsRequired = parameterMetadata.isRequired();

          final StringBuilder baseMethodName =
              new StringBuilder().append("provideEnvironmentVariable")
                  .append(CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name));
          if (representationDefaultValue != null) {
            baseMethodName.append("WithDefaultValue")
                .append(stringSignature(representationDefaultValue));
          }

          if (getTypes().isSameType(type, getStringType())) {
            if (representationDefaultValue != null) {
              writer.println("    @Provides");
              writer.println("    @EnvironmentVariable(value=\"" + name + "\", defaultValue=\""
                  + Java.escapeString(representationDefaultValue) + "\")");
              writer.println("    public String " + baseMethodName + "AsString() {");
              writer.println("        return Optional.ofNullable(env.get(\"" + name
                  + "\")).orElse(\"" + Java.escapeString(representationDefaultValue) + "\");");
              writer.println("    }");
              writer.println();
            } else if (representationIsNullable && !parameterIsRequired) {
              writer.println("    @Provides");
              writer.println("    @Nullable");
              writer.println("    @EnvironmentVariable(\"" + name + "\")");
              writer.println("    public String " + baseMethodName + "AsString() {");
              writer.println("        return env.get(\"" + name + "\");");
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

            if (representationIsNullable) {
              writer.println("    @Provides");
              writer.println("    @EnvironmentVariable(\"" + name + "\")");
              writer.println("    public Optional<String> " + baseMethodName
                  + "AsOptionalOfString(@Nullable @EnvironmentVariable(\"" + name
                  + "\") String value) {");
              writer.println("        return Optional.ofNullable(value);");
              writer.println("    }");
              writer.println();
            }
          } else {
            final String conversionExpr =
                getConverter().generateConversionExpr(type, getStringType(), "value").orElse(null);
            if (conversionExpr == null) {
              getMessager().printMessage(Diagnostic.Kind.ERROR,
                  "Cannot convert " + type + " from " + getStringType());
              continue;
            }

            final String typeSimpleName = getTypes().asElement(type).getSimpleName().toString();

            if (representationDefaultValue != null) {
              // We don't need to check nullability here because the default value "protects" us
              // from any possible null values.
              writer.println("    @Provides");
              writer.println("    @EnvironmentVariable(value=\"" + name + "\", defaultValue=\""
                  + Java.escapeString(representationDefaultValue) + "\")");
              writer.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@EnvironmentVariable(value=\"" + name + "\", defaultValue=\""
                  + Java.escapeString(representationDefaultValue) + "\") String value) {");
              writer.println("        return " + conversionExpr + ";");
              writer.println("    }");
              writer.println();
            } else if (representationIsNullable && !parameterIsRequired) {
              writer.println("    @Provides");
              writer.println("    @Nullable");
              writer.println("    @EnvironmentVariable(\"" + name + "\")");
              writer.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@Nullable @EnvironmentVariable(\"" + name + "\") String value) {");
              writer.println("        return value != null ? " + conversionExpr + " : null;");
              writer.println("    }");
              writer.println();
            } else {
              writer.println("    @Provides");
              writer.println("    @EnvironmentVariable(\"" + name + "\")");
              writer.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                  + "(@EnvironmentVariable(\"" + name + "\") String value) {");
              writer.println("        return " + conversionExpr + ";");
              writer.println("    }");
              writer.println();
            }

            if (representationIsNullable) {
              writer.println("    @Provides");
              writer.println("    @EnvironmentVariable(\"" + name + "\")");
              writer.println("    public Optional<" + type + "> " + baseMethodName + "AsOptionalOf"
                  + typeSimpleName + "(@EnvironmentVariable(\"" + name
                  + "\") Optional<String> o) {");
              writer.println("        return o.map(value -> " + conversionExpr + ");");
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

  private RepresentationKey toStringRepresentation(RepresentationKey key) {
    return new RepresentationKey(getStringType(), key.getName(),
        key.getDefaultValue().orElse(null));
  }

  private transient TypeMirror stringType;

  private TypeMirror getStringType() {
    if (stringType == null) {
      stringType = getElements().getTypeElement("java.lang.String").asType();
    }
    return stringType;
  }

  private ConversionExprFactory getConverter() {
    return converter;
  }
}
