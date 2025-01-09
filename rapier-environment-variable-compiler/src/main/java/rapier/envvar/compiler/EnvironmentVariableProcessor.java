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
package rapier.envvar.compiler;

import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
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
import rapier.core.model.DaggerInjectionSite;
import rapier.core.util.AnnotationProcessing;
import rapier.core.util.CaseFormat;
import rapier.core.util.Java;
import rapier.envvar.EnvironmentVariable;
import rapier.envvar.compiler.model.ParameterKey;
import rapier.envvar.compiler.model.ParameterMetadata;
import rapier.envvar.compiler.model.RepresentationKey;
import rapier.envvar.compiler.util.EnvironmentVariables;

@SupportedAnnotationTypes("dagger.Component")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class EnvironmentVariableProcessor extends RapierProcessorBase {
  private ConversionExprFactory converter;
  
  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    converter = new ConversionExprFactoryChain(
        new ValueOfConversionExprFactory(getTypes(), getStringType()),
        new FromStringConversionExprFactory(getTypes()),
        new SingleArgumentConstructorConversionExprFactory(getTypes(), getStringType()));
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

    // Analyze the component to find all injection sites decorated with this processor's qualifier
    // annotation, @EnvironmentVariable.
    final List<DaggerInjectionSite> injectionSites = computeRelevantInjectionSites(component);

    // Compute per-parameter metadata. A "parameter" is the logical parameter here, so all injection
    // sites that share the same input.
    final ParameterMetadataService parameterMetadataService =
        createParameterMetadataService(injectionSites);

    // Emit warnings for any injection sites that are effectively required but are treated as
    // nullable or have a default value.
    emitCompilerWarnings(injectionSites, parameterMetadataService);

    // Determine all the various and sundry representations to generate bindings for
    final Set<RepresentationKey> representations =
        this.computeRepresentationsToGenerate(injectionSites);

    final String componentPackageName =
        getElements().getPackageOf(component).getQualifiedName().toString();
    final String componentClassName = component.getSimpleName().toString();
    final String moduleClassName = "Rapier" + componentClassName + "EnvironmentVariableModule";

    final String sourceCode = generateSourceCode(componentPackageName, moduleClassName,
        representations, parameterMetadataService);

    // Write the generated source code to a file.
    try {
      // TODO Is component the right set of dependent elements?
      writeSourceCode(
          AnnotationProcessing.qualifiedClassName(componentPackageName, moduleClassName),
          sourceCode, component);
    } catch (IOException e) {
      e.printStackTrace();
      getMessager().printMessage(Diagnostic.Kind.ERROR,
          "Failed to create source file: " + e.getMessage());
    }
  }

  /**
   * Analyzes the given component and returns all relevant injection sites. An injection site is
   * considered relevant if it has a qualifier annotation that matches the expected qualifier
   * annotation for this processor.
   * 
   * @param component the component to analyze
   * @return all relevant injection sites
   */
  private List<DaggerInjectionSite> computeRelevantInjectionSites(TypeElement component) {
    return new DaggerComponentAnalyzer(getProcessingEnv())
        .analyzeComponent(component).getInjectionSites().stream()
        .filter(d -> d.getQualifier().isPresent()).filter(d -> getTypes()
            .isSameType(d.getQualifier().orElseThrow().getAnnotationType(), getQualifierType()))
        .collect(toList());
  }

  @FunctionalInterface
  public static interface ParameterMetadataService {
    public ParameterMetadata getParameterMetadata(ParameterKey key);
  }

  private ParameterMetadataService createParameterMetadataService(
      List<DaggerInjectionSite> injectionSites) {
    final Map<ParameterKey, List<DaggerInjectionSite>> injectionSitesByParameter =
        injectionSites.stream().map(dis -> Map.entry(ParameterKey.fromInjectionSite(dis), dis))
            .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));

    final Map<ParameterKey, ParameterMetadata> metadataForParameters = new HashMap<>();
    for (Map.Entry<ParameterKey, List<DaggerInjectionSite>> entry : injectionSitesByParameter
        .entrySet()) {
      final ParameterKey key = entry.getKey();
      final List<DaggerInjectionSite> parameterInjectionSites = entry.getValue();

      // Group injection sites by requiredness
      final Map<Boolean, List<DaggerInjectionSite>> injectionSitesByRequired =
          parameterInjectionSites.stream().collect(Collectors.groupingBy(
              dis -> EnvironmentVariables.isRequired(dis.isNullable(),
                  EnvironmentVariables
                      .extractEnvironmentVariableDefaultValue(dis.getQualifier().orElseThrow())),
              toList()));

      // Check for conflicting requiredness
      if (injectionSitesByRequired.size() > 1) {
        getMessager().printMessage(Diagnostic.Kind.WARNING,
            "Conflicting requiredness for environment variable " + key.getName()
                + ", will be treated as required");
        // TODO Print the conflicting dependencies, with element and annotation references
      }

      // Determine if the parameter is required
      final boolean required = injectionSitesByRequired.keySet().stream().anyMatch(b -> b == true);

      metadataForParameters.put(key, new ParameterMetadata(required));
    }

    return metadataForParameters::get;
  }

  /**
   * Prints compiler warnings for injection sites with local constraints that do not match global
   * realities, e.g., treating a required environment variable as nullable.
   * 
   * @param injectionSitesByRepresentation the injection sites by representation
   */
  private void emitCompilerWarnings(List<DaggerInjectionSite> injectionSites,
      ParameterMetadataService parameterMetadataService) {
    final SortedMap<RepresentationKey, List<DaggerInjectionSite>> injectionSitesByRepresentation =
        injectionSites.stream().map(dis -> Map.entry(RepresentationKey.fromInjectionSite(dis), dis))
            .collect(
                groupingBy(Map.Entry::getKey, () -> new TreeMap<>(REPRESENTATION_KEY_COMPARATOR),
                    mapping(Map.Entry::getValue, toCollection(ArrayList::new))));

    for (Map.Entry<RepresentationKey, List<DaggerInjectionSite>> e : injectionSitesByRepresentation
        .entrySet()) {
      final RepresentationKey representation = e.getKey();
      final List<DaggerInjectionSite> representationInjectionSites = e.getValue();

      final ParameterKey parameter = ParameterKey.fromRepresentationKey(representation);
      final ParameterMetadata parameterMetadata =
          parameterMetadataService.getParameterMetadata(parameter);
      final boolean parameterIsRequired = parameterMetadata.isRequired();

      for (DaggerInjectionSite injectionSite : representationInjectionSites) {
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
  }

  /**
   * Determine the unique set of representations that need to be generated. This is the union of all
   * representations in the given injection sites, plus any additional representations that are
   * required to support the conversion of the representation to code, e.g., a string
   * representation.
   * 
   * @param injectionSites the injection sites
   * @return the set of representations to generate
   */
  private Set<RepresentationKey> computeRepresentationsToGenerate(
      List<DaggerInjectionSite> injectionSites) {
    final Set<RepresentationKey> representations = injectionSites.stream()
        .map(dis -> RepresentationKey.fromInjectionSite(dis)).collect(toCollection(HashSet::new));

    for (RepresentationKey representation : Set.copyOf(representations)) {
      if (getTypes().isSameType(representation.getType(), getStringType()))
        continue;

      final RepresentationKey representationAsString = toStringRepresentation(representation);

      if (!representations.contains(representationAsString))
        representations.add(representationAsString);
    }

    return unmodifiableSet(representations);
  }

  private static final Comparator<RepresentationKey> REPRESENTATION_KEY_COMPARATOR =
      Comparator.comparing(RepresentationKey::getName)
          .thenComparing(Comparator.comparing(x -> x.getDefaultValue().orElse(null),
              Comparator.nullsFirst(Comparator.naturalOrder())))
          .thenComparing(x -> x.getType().toString());

  /**
   * Generates the source code for the given representations.
   * 
   * @param component
   * @param representations
   * @param metadataForParameters
   * @throws IOException
   */
  private String generateSourceCode(String componentPackageName, String moduleClassName,
      Set<RepresentationKey> representations, ParameterMetadataService metadataForParameters) {
    final StringWriter result = new StringWriter();

    final SortedSet<RepresentationKey> representationsInOrder =
        new TreeSet<>(REPRESENTATION_KEY_COMPARATOR);
    representationsInOrder.addAll(representations);

    try (final PrintWriter writer = new PrintWriter(result)) {
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
      for (RepresentationKey representation : representationsInOrder) {
        final TypeMirror type = representation.getType();
        final String name = representation.getName();
        final String representationDefaultValue = representation.getDefaultValue().orElse(null);
        final ParameterKey parameter = ParameterKey.fromRepresentationKey(representation);
        final ParameterMetadata parameterMetadata =
            metadataForParameters.getParameterMetadata(parameter);
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
            writer.println("        return Optional.ofNullable(env.get(\"" + name + "\")).orElse(\""
                + Java.escapeString(representationDefaultValue) + "\");");
            writer.println("    }");
            writer.println();
          } else if (parameterIsRequired) {
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
          } else {
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
          }
        } else {
          final String conversionExpr =
              getConverter().generateConversionExpr(type, "value").orElse(null);
          if (conversionExpr == null) {
            getMessager().printMessage(Diagnostic.Kind.ERROR,
                "Cannot convert " + type + " from " + getStringType());
            continue;
          }

          final String typeSimpleName = getSimpleTypeName(type);

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
          } else if (parameterIsRequired) {
            writer.println("    @Provides");
            writer.println("    @EnvironmentVariable(\"" + name + "\")");
            writer.println("    public " + type + " " + baseMethodName + "As" + typeSimpleName
                + "(@EnvironmentVariable(\"" + name + "\") String value) {");
            writer.println("        " + type + " result = " + conversionExpr + ";");
            writer.println("        if (result == null)");
            writer.println("            throw new IllegalStateException(\"Environment variable "
                + name + " representation " + type + " not set\");");
            writer.println("        return result;");
            writer.println("    }");
            writer.println();
          } else {
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
                + typeSimpleName + "(@EnvironmentVariable(\"" + name + "\") Optional<String> o) {");
            writer.println("        return o.map(value -> " + conversionExpr + ");");
            writer.println("    }");
            writer.println();
          }
        }
      }
      writer.println("}");
    }

    return result.toString();
  }

  /**
   * Writes the given source code to a file. The file is created in the same package as the
   * component, with the given class name.
   * 
   * @param qualifiedClassName the qualified class name
   * @param sourceCode the source code
   * 
   * @throws IOException if an I/O error occurs
   */
  private void writeSourceCode(final String qualifiedClassName, final String sourceCode,
      Element... dependentElements) throws IOException {
    // TODO Is this the right set of elements?
    final JavaFileObject jfo = getFiler().createSourceFile(qualifiedClassName, dependentElements);
    try (Writer w = jfo.openWriter()) {
      w.write(sourceCode);
    }
  }

  private RepresentationKey toStringRepresentation(RepresentationKey key) {
    return new RepresentationKey(getStringType(), key.getName(),
        key.getDefaultValue().orElse(null));
  }

  private transient TypeMirror qualifierType;

  private TypeMirror getQualifierType() {
    if (qualifierType == null) {
      qualifierType = getElements().getTypeElement(EnvironmentVariable.class.getName()).asType();
    }
    return qualifierType;
  }

  private ConversionExprFactory getConverter() {
    return converter;
  }
}
