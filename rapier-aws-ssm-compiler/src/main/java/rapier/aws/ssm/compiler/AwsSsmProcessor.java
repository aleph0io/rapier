/*-
 * =================================LICENSE_START==================================
 * rapier-aws-ssm-compiler
 * ====================================SECTION=====================================
 * Copyright (C) 2024 - 2025 aleph0
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
package rapier.aws.ssm.compiler;

import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.time.OffsetDateTime;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
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
import rapier.aws.ssm.AwsSsmParameter;
import rapier.aws.ssm.compiler.model.ParameterKey;
import rapier.aws.ssm.compiler.model.ParameterMetadata;
import rapier.aws.ssm.compiler.model.RepresentationKey;
import rapier.aws.ssm.compiler.util.SystemProperties;
import rapier.compiler.core.ConversionExprFactory;
import rapier.compiler.core.DaggerComponentAnalyzer;
import rapier.compiler.core.RapierProcessorBase;
import rapier.compiler.core.TemplateParser;
import rapier.compiler.core.model.DaggerInjectionSite;
import rapier.compiler.core.util.AnnotationProcessing;
import rapier.compiler.core.util.CaseFormat;
import rapier.compiler.core.util.ConversionExprFactories;
import rapier.compiler.core.util.Java;
import rapier.compiler.core.util.RapierInfo;

@SupportedAnnotationTypes("dagger.Component")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class AwsSsmProcessor extends RapierProcessorBase {
  private ConversionExprFactory converter;

  private String version = RapierInfo.VERSION;

  /* default */ void setVersion(String version) {
    if (version == null)
      throw new NullPointerException();
    this.version = version;
  }

  private OffsetDateTime date = OffsetDateTime.now();

  /* default */ void setDate(OffsetDateTime date) {
    if (date == null)
      throw new NullPointerException();
    this.date = date;
  }

  private String url = RapierInfo.URL;

  /* default */ void setUrl(String url) {
    if (url == null)
      throw new NullPointerException();
    this.url = url;
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    converter = ConversionExprFactories.standardAmbiguousFromStringFactory(getProcessingEnv());
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
    // annotation, @SystemProperty.
    final List<DaggerInjectionSite> injectionSites = computeRelevantInjectionSites(component);

    // Compute per-parameter metadata. A "parameter" is the logical parameter here, so all injection
    // sites that share the same input.
    final ParameterMetadataService parameterMetadataService =
        createParameterMetadataService(injectionSites);

    // Emit warnings for any injection sites that are effectively required but are treated as
    // nullable or have a default value.
    emitCompilerWarnings(injectionSites, parameterMetadataService);

    // Determine all the various and sundry representations to generate bindings for
    final Set<RepresentationKey> representations = computeRepresentationsToGenerate(injectionSites);

    final String componentPackageName =
        getElements().getPackageOf(component).getQualifiedName().toString();
    final String componentClassName = component.getSimpleName().toString();
    final String moduleClassName = "Rapier" + componentClassName + "AwsSsmModule";

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
        .filter(d -> {
          final ParameterKey parameter = ParameterKey.fromInjectionSite(d);
          if (!isValidAwsSsmParameterNameTemplate(parameter.getName())) {
            getMessager().printMessage(Diagnostic.Kind.ERROR,
                "Invalid AWS SSM parameter name template", d.getElement());
            return false;
          }
          if (awsSsmParameterNameStartsWithAws(parameter.getName())) {
            getMessager().printMessage(Diagnostic.Kind.ERROR,
                "AWS SSM parameter name must not start with \"aws\"", d.getElement());
            return false;
          }
          if (awsSsmParameterNameStartsWithSsm(parameter.getName())) {
            getMessager().printMessage(Diagnostic.Kind.ERROR,
                "AWS SSM parameter name must not start with \"ssm\"", d.getElement());
            return false;
          }
          return true;
        }).collect(toList());
  }

  private boolean isValidAwsSsmParameterNameTemplate(String template) {
    final AtomicBoolean result = new AtomicBoolean(true);
    try {
      new TemplateParser().parse(template, new TemplateParser.ParseHandler() {
        @Override
        public void onText(int index, String text) {
          if (!text.chars().allMatch(AwsSsmProcessor.this::isValidAwsSsmParameterNameChar)) {
            result.set(false);
          }
        }

        @Override
        public void onVariableExpression(int index, String variableName) {}

        @Override
        public void onVariableExpressionWithDefaultValue(int index, String variableName,
            String defaultValue) {}
      });
    } catch (TemplateParser.TemplateSyntaxException e) {
      result.set(false);
    }
    return result.get();
  }

  private boolean isValidAwsSsmParameterNameChar(int ch) {
    return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
        || ch == '_' || ch == '-' || ch == '.' || ch == '/';
  }

  private static final Pattern STARTS_WITH_AWS =
      Pattern.compile("^/?aws", Pattern.CASE_INSENSITIVE);

  private boolean awsSsmParameterNameStartsWithAws(String template) {
    if (STARTS_WITH_AWS.matcher(template).find())
      return true;
    return false;
  }

  private static final Pattern STARTS_WITH_SSM =
      Pattern.compile("^/?ssm", Pattern.CASE_INSENSITIVE);

  private boolean awsSsmParameterNameStartsWithSsm(String template) {
    if (STARTS_WITH_SSM.matcher(template).find())
      return true;
    return false;
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
          parameterInjectionSites.stream()
              .collect(groupingBy(
                  dis -> SystemProperties.isRequired(dis.isNullable(),
                      RepresentationKey.fromInjectionSite(dis).getDefaultValue().orElse(null)),
                  toList()));

      // Check for conflicting requiredness
      if (injectionSitesByRequired.size() > 1) {
        getMessager().printMessage(Diagnostic.Kind.WARNING,
            "Conflicting requiredness for AWS SSM string parameter " + key.getName()
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
   * realities, e.g., treating a required AWS SSM string parameter as nullable.
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
              Diagnostic.Kind.WARNING, "Effectively required AWS SSM string parameter "
                  + representation.getName() + " is treated as nullable",
              injectionSite.getElement());
        }

        if (parameterIsRequired && injectionSiteHasDefaultValue) {
          getMessager()
              .printMessage(
                  Diagnostic.Kind.WARNING, "Effectively required AWS SSM string parameter "
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
      writer.println("import static java.util.stream.Collectors.toMap;");
      writer.println("import static java.util.Objects.requireNonNull;");
      writer.println();
      writer.println("import " + AwsSsmParameter.class.getName() + ";");
      writer.println("import dagger.Module;");
      writer.println("import dagger.Provides;");
      writer.println("import java.io.IOException;");
      writer.println("import java.io.UncheckedIOException;");
      writer.println("import java.util.Map;");
      writer.println("import java.util.Optional;");
      writer.println("import java.util.Properties;");
      writer.println("import javax.annotation.Nullable;");
      writer.println("import javax.annotation.processing.Generated;");
      writer.println("import javax.inject.Inject;");
      writer.println("import rapier.internal.RapierGenerated;");
      writer.println("import software.amazon.awssdk.services.ssm.SsmClient;");
      writer
          .println("import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;");
      writer.println("import software.amazon.awssdk.services.ssm.model.GetParameterRequest;");
      writer.println();
      writer.println("@Module");
      writer.println("@RapierGenerated");
      writer.println("@Generated(");
      writer.println(
          "    value = \"" + AwsSsmProcessor.class.getCanonicalName() + "@" + version + "\",");
      writer.println("    comments = \"" + Java.escapeString(url) + "\",");
      writer.println("    date = \"" + Java.escapeString(date.toInstant().toString()) + "\")");
      writer.println("public class " + moduleClassName + " {");
      writer.println("    private final SsmClient client;");
      writer.println("    private final Map<String, String> env;");
      writer.println("    private final Map<String, String> sys;");
      writer.println();
      writer.println("    @Inject");
      writer.println("    public " + moduleClassName + "() {");
      writer.println("        this(SsmClient.create());");
      writer.println("    }");
      writer.println();
      writer.println("    public " + moduleClassName + "(SsmClient client) {");
      writer.println("        this(client, System.getenv(), System.getProperties());");
      writer.println("    }");
      writer.println();
      writer.println("    public " + moduleClassName
          + "(SsmClient client, Map<String, String> env, Properties sys) {");
      writer.println("        this(client, env, sys.entrySet().stream()");
      writer.println("            .collect(toMap(");
      writer.println("                e -> e.getKey().toString(),");
      writer.println("                e -> e.getValue().toString())));");
      writer.println("    }");
      writer.println();
      writer.println("    public " + moduleClassName
          + "(SsmClient client, Map<String, String> env, Map<String, String> sys) {");
      writer.println("        this.client = requireNonNull(client);");
      writer.println("        this.env = unmodifiableMap(requireNonNull(env));");
      writer.println("        this.sys = unmodifiableMap(requireNonNull(sys));");
      writer.println("    }");
      writer.println();
      for (RepresentationKey representation : representationsInOrder) {
        final TypeMirror representationType = representation.getType();
        final String name = representation.getName();
        final String nameExpr = this.compileTemplate(name, "env", "sys");
        final String representationDefaultValue = representation.getDefaultValue().orElse(null);
        final ParameterKey parameter = ParameterKey.fromRepresentationKey(representation);
        final ParameterMetadata parameterMetadata =
            metadataForParameters.getParameterMetadata(parameter);
        final boolean parameterIsRequired = parameterMetadata.isRequired();

        final StringBuilder baseMethodName = new StringBuilder()
            .append("provideAwsSsmParameter").append(CaseFormat.UPPER_UNDERSCORE
                .to(CaseFormat.UPPER_CAMEL, standardizeAwsSsmParameterName(name)));
        if (representationDefaultValue != null) {
          baseMethodName.append("WithDefaultValue")
              .append(stringSignature(representationDefaultValue));
        }

        final String representationAnnotation;
        if (representationDefaultValue != null) {
          representationAnnotation = "@AwsSsmParameter(value=\"" + name
              + "\", defaultValue=\"" + Java.escapeString(representationDefaultValue) + "\")";
        } else {
          representationAnnotation = "@AwsSsmParameter(\"" + name + "\")";
        }

        final boolean representationIsNullable =
            representationDefaultValue == null && !parameterIsRequired;

        final String nullableAnnotation = representationIsNullable ? "@Nullable" : "";

        if (getTypes().isSameType(representationType, getStringType())) {
          writer.println("    " + nullableAnnotation);
          writer.println("    @Provides");
          writer.println("    " + representationAnnotation);
          writer.println("    public String " + baseMethodName + "AsString() {");
          writer.println("        final String name=" + nameExpr + ";");
          writer.println("        String value;");
          writer.println("        try {");
          writer.println("            value = client");
          writer.println("                .getParameter(b -> b.name(name))");
          writer.println("                .parameter()");
          writer.println("                .value();");
          writer.println("        } catch(ParameterNotFoundException e) {");
          if (representationDefaultValue != null) {
            writer.println(
                "            value = \"" + Java.escapeString(representationDefaultValue) + "\";");
          } else if (parameterIsRequired) {
            writer.println(
                "            throw new IllegalStateException(\"AWS SSM Parameter \" + name + \" not set\");");
          } else {
            writer.println("            value = null;");
          }
          writer.println("        } catch(Exception e) {");
          writer.println("            throw new UncheckedIOException(");
          writer.println("                \"Failed to retrieve AWS SSM Parameter \" + name,");
          writer.println("                new IOException(");
          writer.println(
              "                    \"Failed to retrieve AWS SSM Parameter \" + name, e));");
          writer.println("        }");
          writer.println("        return value;");
          writer.println("    }");
          writer.println();

          if (representationIsNullable) {
            writer.println("    @Provides");
            writer.println("    " + representationAnnotation);
            writer.println("    public Optional<String> " + baseMethodName + "AsOptionalOfString("
                + representationAnnotation + " String value) {");
            writer.println("        return Optional.ofNullable(value);");
            writer.println("    }");
            writer.println();
          }
        } else {
          final String conversionExpr =
              getConverter().generateConversionExpr(representationType, "value").orElse(null);
          if (conversionExpr == null) {
            getMessager().printMessage(Diagnostic.Kind.ERROR,
                "Cannot convert " + representationType + " from " + getStringType());
            continue;
          }

          final String typeSimpleName = getSimpleTypeName(representationType);

          writer.println("    " + nullableAnnotation);
          writer.println("    @Provides");
          writer.println("    " + representationAnnotation);
          writer.println(
              "    public " + representationType + " " + baseMethodName + "As" + typeSimpleName
                  + "(" + nullableAnnotation + " " + representationAnnotation + " String value) {");
          if (representationIsNullable == true) {
            writer.println("        if(value == null)");
            writer.println("            return null;");
          }
          writer.println("        final " + representationType + " result;");
          writer.println("        try {");
          writer.println("            result = " + conversionExpr + ";");
          writer.println("        } catch (Exception e) {");
          writer.println("            final String name=" + nameExpr + ";");
          writer.println("            throw new IllegalArgumentException(");
          writer.println("                \"Environment variable \" + name + \" representation "
              + representationType + " argument not valid\", e);");
          writer.println("        }");
          if (representationIsNullable == false) {
            writer.println("        if (result == null) {");
            writer.println("            final String name=" + nameExpr + ";");
            writer.println(
                "            throw new IllegalStateException(\"Environment variable \" + name + \" representation "
                    + representationType + " not set\");");
            writer.println("        }");
          }
          writer.println("        return result;");
          writer.println("    }");
          writer.println();

          if (representationIsNullable) {
            writer.println("    @Provides");
            writer.println("    " + representationAnnotation);
            writer.println("    public Optional<" + representationType + "> " + baseMethodName
                + "AsOptionalOf" + typeSimpleName + "(" + representationAnnotation + " "
                + representationType + " value) {");
            writer.println("        return Optional.ofNullable(value);");
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
      qualifierType = getElements().getTypeElement(AwsSsmParameter.class.getName()).asType();
    }
    return qualifierType;
  }

  private ConversionExprFactory getConverter() {
    return converter;
  }

  private static final Pattern NONALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9]+");
  private static final Pattern UNDERSCORES = Pattern.compile("_+");

  private String standardizeAwsSsmParameterName(String name) {
    final String original = name;

    name = NONALPHANUMERIC.matcher(name).replaceAll("_").toUpperCase();
    name = UNDERSCORES.matcher(name).replaceAll("_");

    if (name.startsWith("_"))
      name = name.substring(1, name.length());
    if (name.endsWith("_"))
      name = name.substring(0, name.length() - 1);

    if (name.isEmpty())
      return stringSignature(original);

    return name.toUpperCase();
  }
}
