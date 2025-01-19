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
package rapier.cli.compiler;

import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import rapier.cli.CliCommandHelp;
import rapier.cli.CliFlagParameter;
import rapier.cli.CliOptionParameter;
import rapier.cli.CliPositionalParameter;
import rapier.cli.compiler.model.CommandHelp;
import rapier.cli.compiler.model.CommandMetadata;
import rapier.cli.compiler.model.FlagParameterHelp;
import rapier.cli.compiler.model.FlagParameterKey;
import rapier.cli.compiler.model.FlagParameterMetadata;
import rapier.cli.compiler.model.FlagRepresentationKey;
import rapier.cli.compiler.model.OptionParameterHelp;
import rapier.cli.compiler.model.OptionParameterKey;
import rapier.cli.compiler.model.OptionParameterMetadata;
import rapier.cli.compiler.model.OptionRepresentationKey;
import rapier.cli.compiler.model.PositionalParameterHelp;
import rapier.cli.compiler.model.PositionalParameterKey;
import rapier.cli.compiler.model.PositionalParameterMetadata;
import rapier.cli.compiler.model.PositionalRepresentationKey;
import rapier.cli.compiler.util.CliProcessing;
import rapier.cli.thirdparty.com.sigpwned.just.args.JustArgs;
import rapier.compiler.core.ConversionExprFactory;
import rapier.compiler.core.DaggerComponentAnalyzer;
import rapier.compiler.core.RapierProcessorBase;
import rapier.compiler.core.conversion.expr.BooleanToPrimitiveConversionExprFactory;
import rapier.compiler.core.conversion.expr.BooleanToStringConversionExprFactory;
import rapier.compiler.core.conversion.expr.ConversionExprFactoryChain;
import rapier.compiler.core.conversion.expr.ElementwiseListConversionExprFactory;
import rapier.compiler.core.conversion.expr.IdentityConversionExprFactory;
import rapier.compiler.core.conversion.expr.SingleArgumentConstructorConversionExprFactory;
import rapier.compiler.core.conversion.expr.ValueOfConversionExprFactory;
import rapier.compiler.core.model.DaggerInjectionSite;
import rapier.compiler.core.util.ConversionExprFactories;
import rapier.compiler.core.util.Java;
import rapier.compiler.core.util.RapierInfo;

@SupportedAnnotationTypes("dagger.Component")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class CliProcessor extends RapierProcessorBase {
  private ConversionExprFactory stringConverter;
  private ConversionExprFactory listOfStringConverter;
  private ConversionExprFactory booleanConverter;
  private ConversionExprFactory listOfBooleanConverter;

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

    stringConverter =
        ConversionExprFactories.standardAmbiguousFromStringFactory(getProcessingEnv());

    listOfStringConverter =
        ConversionExprFactories.standardAmbiguousFromListOfStringFactory(getProcessingEnv());

    booleanConverter = new ConversionExprFactoryChain(
        new IdentityConversionExprFactory(getTypes(), getBooleanType()),
        new BooleanToPrimitiveConversionExprFactory(getTypes()),
        new BooleanToStringConversionExprFactory(getTypes()),
        new ValueOfConversionExprFactory(getTypes(), getBooleanType()),
        new SingleArgumentConstructorConversionExprFactory(getTypes(), getBooleanType()));

    listOfBooleanConverter = new ConversionExprFactoryChain(
        new IdentityConversionExprFactory(getTypes(), getListOfBooleanType()),
        new ValueOfConversionExprFactory(getTypes(), getListOfBooleanType()),
        new SingleArgumentConstructorConversionExprFactory(getTypes(), getListOfBooleanType()),
        new ElementwiseListConversionExprFactory(getTypes(), booleanConverter));
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

    final String componentQualifiedName = component.getQualifiedName().toString();
    final String componentPackageName =
        getElements().getPackageOf(component).getQualifiedName().toString();
    final String componentClassName = component.getSimpleName().toString();
    final String moduleClassName = "Rapier" + componentClassName + "CliModule";

    final CommandHelp commandHelp = component.getAnnotationMirrors().stream()
        .flatMap(am -> CommandHelp.fromAnnotationMirror(am).stream()).findFirst().orElse(null);
    final CommandMetadata commandMetadata = Optional.ofNullable(commandHelp)
        .map(h -> new CommandMetadata(h.getName(), h.getVersion(), h.getDescription().orElse(null),
            h.isProvideStandardHelp(), h.isProvideStandardVersion(), h.isTesting()))
        .orElseGet(() -> new CommandMetadata("command", CliCommandHelp.DEFAULT_VERSION, null, true,
            true, false));

    final List<DaggerInjectionSite> qualifiedInjectionSites =
        new DaggerComponentAnalyzer(getProcessingEnv()).analyzeComponent(component)
            .getInjectionSites().stream().filter(d -> d.getQualifier().isPresent())
            .collect(toList());

    final SortedMap<PositionalParameterKey, List<DaggerInjectionSite>> positionalInjectionSites =
        extractPositionalParameters(qualifiedInjectionSites);

    final PositionalParameterMetadataService positionalMetadataService =
        createPositionalParameterMetadataService(positionalInjectionSites);

    final SortedMap<OptionParameterKey, List<DaggerInjectionSite>> optionInjectionSites =
        extractOptionParameters(qualifiedInjectionSites);

    final OptionParameterMetadataService optionMetadataService =
        createOptionParameterMetadataService(optionInjectionSites);

    final SortedMap<FlagParameterKey, List<DaggerInjectionSite>> flagInjectionSites =
        extractFlagParameters(qualifiedInjectionSites);

    final FlagParameterMetadataService flagMetadataService =
        createFlagParameterMetadataService(flagInjectionSites);

    if (positionalMetadataService == null || optionMetadataService == null
        || flagMetadataService == null) {
      getMessager().printMessage(Diagnostic.Kind.NOTE, "Analysis of component "
          + componentQualifiedName + " failed, will not generate " + moduleClassName);
      return;
    }

    final boolean injectionSitesAreValid =
        validateInjectionSites(component, positionalInjectionSites, positionalMetadataService,
            optionInjectionSites, optionMetadataService, flagInjectionSites, flagMetadataService);

    final boolean standardHelpIsValid = validateStandardHelp(component, commandMetadata,
        optionInjectionSites, optionMetadataService, flagInjectionSites, flagMetadataService);

    final boolean standardVersionIsValid = validateStandardVersion(component, commandMetadata,
        optionInjectionSites, optionMetadataService, flagInjectionSites, flagMetadataService);

    if (injectionSitesAreValid == false || standardHelpIsValid == false
        || standardVersionIsValid == false) {
      getMessager().printMessage(Diagnostic.Kind.NOTE, "Component " + componentQualifiedName
          + " has semantic errors, will not generate " + moduleClassName);
      return;
    }

    final String moduleSource = generateModuleSource(componentPackageName, moduleClassName,
        commandMetadata, positionalInjectionSites, positionalMetadataService, optionInjectionSites,
        optionMetadataService, flagInjectionSites, flagMetadataService);


    try {
      // TODO Is this the right set of elements?
      final Element[] dependentElements = new Element[] {component};
      final JavaFileObject moduleSourceFile = getFiler().createSourceFile(
          Java.qualifiedClassName(componentPackageName, moduleClassName), dependentElements);
      try (final Writer writer = moduleSourceFile.openWriter()) {
        writer.write(moduleSource);
      }
    } catch (IOException e) {
      e.printStackTrace();
      getMessager().printMessage(Diagnostic.Kind.ERROR,
          "Failed to create source file: " + e.getMessage());
    }
  }


  private boolean validateInjectionSites(TypeElement component,
      SortedMap<PositionalParameterKey, List<DaggerInjectionSite>> positionalInjecionSites,
      PositionalParameterMetadataService positionalMetadataService,
      SortedMap<OptionParameterKey, List<DaggerInjectionSite>> optionInjectionSites,
      OptionParameterMetadataService optionMetadataService,
      SortedMap<FlagParameterKey, List<DaggerInjectionSite>> flagInjectionSites,
      FlagParameterMetadataService flagMetadataService) {
    final boolean positionalAreValids =
        validatePositionalInjectionSites(positionalInjecionSites, positionalMetadataService);

    final boolean switchNamesAreValid = validateOptionAndFlagSwitchNames(optionInjectionSites,
        optionMetadataService, flagInjectionSites, flagMetadataService);

    return positionalAreValids && switchNamesAreValid;
  }

  private static final String STANDARD_HELP_LONG_NAME = "help";

  private static final Character STANDARD_HELP_SHORT_NAME = 'h';

  private boolean validateStandardHelp(TypeElement component, CommandMetadata commandHelp,
      SortedMap<OptionParameterKey, List<DaggerInjectionSite>> optionInjectionSites,
      OptionParameterMetadataService optionMetadataService,
      SortedMap<FlagParameterKey, List<DaggerInjectionSite>> flagInjectionSites,
      FlagParameterMetadataService flagMetadataService) {
    if (!commandHelp.isProvideStandardHelp())
      return true;

    boolean result = true;

    if (optionInjectionSites.keySet().stream()
        .anyMatch(p -> Objects.equals(STANDARD_HELP_SHORT_NAME, p.getShortName().orElse(null)))) {
      getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot use short name '-"
          + STANDARD_HELP_SHORT_NAME + "' for option when standard help is enabled");
      // TODO Print example standard help
      result = false;
    }

    if (optionInjectionSites.keySet().stream()
        .anyMatch(p -> Objects.equals(STANDARD_HELP_LONG_NAME, p.getLongName().orElse(null)))) {
      getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot use long name '--"
          + STANDARD_HELP_LONG_NAME + "' for option when standard help is enabled");
      // TODO Print example standard help
      result = false;
    }

    if (flagInjectionSites.keySet().stream().anyMatch(
        p -> Objects.equals(STANDARD_HELP_SHORT_NAME, p.getPositiveShortName().orElse(null)))) {
      getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot use short name '-"
          + STANDARD_HELP_SHORT_NAME + "' for flag when standard help is enabled");
      // TODO Print example standard help
      result = false;
    }

    if (flagInjectionSites.keySet().stream().anyMatch(
        p -> Objects.equals(STANDARD_HELP_LONG_NAME, p.getPositiveLongName().orElse(null)))) {
      getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot use long name '--"
          + STANDARD_HELP_LONG_NAME + "' for flag when standard help is enabled");
      // TODO Print example standard help
      result = false;
    }

    if (flagInjectionSites.keySet().stream().anyMatch(
        p -> Objects.equals(STANDARD_HELP_SHORT_NAME, p.getNegativeShortName().orElse(null)))) {
      getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot use short name '-"
          + STANDARD_HELP_SHORT_NAME + "' for flag when standard help is enabled");
      // TODO Print example standard help
      result = false;
    }

    if (flagInjectionSites.keySet().stream().anyMatch(
        p -> Objects.equals(STANDARD_HELP_LONG_NAME, p.getNegativeLongName().orElse(null)))) {
      getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot use long name '--"
          + STANDARD_HELP_LONG_NAME + "' for flag when standard help is enabled");
      // TODO Print example standard help
      result = false;
    }

    return result;
  }

  private static final String STANDARD_VERSION_LONG_NAME = "version";

  private static final Character STANDARD_VERSION_SHORT_NAME = 'V';

  private boolean validateStandardVersion(TypeElement component, CommandMetadata commandMetadata,
      SortedMap<OptionParameterKey, List<DaggerInjectionSite>> optionInjectionSites,
      OptionParameterMetadataService optionMetadataService,
      SortedMap<FlagParameterKey, List<DaggerInjectionSite>> flagInjectionSites,
      FlagParameterMetadataService flagMetadataService) {
    if (!commandMetadata.isProvideStandardVersion())
      return true;

    boolean result = true;

    if (optionInjectionSites.keySet().stream().anyMatch(
        p -> Objects.equals(STANDARD_VERSION_SHORT_NAME, p.getShortName().orElse(null)))) {
      getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot use short name '-"
          + STANDARD_VERSION_SHORT_NAME + "' for option when standard version is enabled");
      // TODO Print example standard version
      result = false;
    }

    if (optionInjectionSites.keySet().stream()
        .anyMatch(p -> Objects.equals(STANDARD_VERSION_LONG_NAME, p.getLongName().orElse(null)))) {
      getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot use long name '--"
          + STANDARD_VERSION_LONG_NAME + "' for option when standard version is enabled");
      // TODO Print example standard version
      result = false;
    }

    if (flagInjectionSites.keySet().stream().anyMatch(
        p -> Objects.equals(STANDARD_VERSION_SHORT_NAME, p.getPositiveShortName().orElse(null)))) {
      getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot use short name '-"
          + STANDARD_VERSION_SHORT_NAME + "' for flag when standard version is enabled");
      // TODO Print example standard version
      result = false;
    }

    if (flagInjectionSites.keySet().stream().anyMatch(
        p -> Objects.equals(STANDARD_VERSION_LONG_NAME, p.getPositiveLongName().orElse(null)))) {
      getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot use long name '--"
          + STANDARD_VERSION_LONG_NAME + "' for flag when standard version is enabled");
      // TODO Print example standard version
      result = false;
    }

    if (flagInjectionSites.keySet().stream().anyMatch(
        p -> Objects.equals(STANDARD_VERSION_SHORT_NAME, p.getNegativeShortName().orElse(null)))) {
      getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot use short name '-"
          + STANDARD_VERSION_SHORT_NAME + "' for flag when standard version is enabled");
      // TODO Print example standard version
      result = false;
    }

    if (flagInjectionSites.keySet().stream().anyMatch(
        p -> Objects.equals(STANDARD_VERSION_LONG_NAME, p.getNegativeLongName().orElse(null)))) {
      getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot use long name '--"
          + STANDARD_VERSION_LONG_NAME + "' for flag when standard version is enabled");
      // TODO Print example standard version
      result = false;
    }

    return result;
  }

  private boolean validatePositionalInjectionSites(
      SortedMap<PositionalParameterKey, List<DaggerInjectionSite>> positionalInjecionSites,
      PositionalParameterMetadataService positionalMetadataService) {
    boolean valid = true;

    boolean seenOptional = false;
    boolean seenList = false;
    final int size = positionalInjecionSites.size();
    for (int position = 0; position < size; position++) {
      final boolean isLastParameter = position == size - 1;

      final List<DaggerInjectionSite> positionInjectionSites =
          positionalInjecionSites.get(PositionalParameterKey.forPosition(position));
      if (positionInjectionSites == null) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Missing required positional parameter: " + position);
        valid = false;
        continue;
      }

      final PositionalParameterMetadata metadata =
          positionalMetadataService.getPositionalParameterMetadata(position);

      final boolean parameterIsRequired = metadata.isRequired();
      if (seenOptional && parameterIsRequired) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Required positional parameter " + position + " follows optional parameter");
        // TODO Print example optional
        valid = false;
      } else if (!seenOptional && !parameterIsRequired) {
        seenOptional = true;
      }

      final boolean parameterIsList = metadata.isList();

      if (parameterIsList && !isLastParameter) {
        getMessager().printMessage(Diagnostic.Kind.ERROR, "Positional parameter " + position
            + " appears to be varargs parameter, but is not last positional parameter");
        // TODO Print example list
        valid = false;
      }

      if (seenList) {
        getMessager().printMessage(Diagnostic.Kind.ERROR, "Positional parameter " + position
            + " follows varargs parameter, which should be last positional parameter");
        // TODO Print example optional
        valid = false;
      }

      if (parameterIsList) {
        seenList = true;
      }
    }

    return valid;
  }

  private static class NameKey {
    public static NameKey fromOptionParameterKey(OptionParameterKey key) {
      return new NameKey(key.getShortName().map(Set::of).orElseGet(Set::of),
          key.getLongName().map(Set::of).orElseGet(Set::of));
    }

    public static NameKey fromFlagParameterKey(FlagParameterKey key) {
      final Set<Character> shortNames =
          Stream.concat(key.getPositiveShortName().stream(), key.getNegativeShortName().stream())
              .collect(toSet());
      final Set<String> longNames =
          Stream.concat(key.getPositiveLongName().stream(), key.getNegativeLongName().stream())
              .collect(toSet());
      return new NameKey(shortNames, longNames);
    }

    private final Set<Character> shortNames;
    private final Set<String> longNames;

    public NameKey(Set<Character> shortNames, Set<String> longNames) {
      this.shortNames = unmodifiableSet(shortNames);
      this.longNames = unmodifiableSet(longNames);
    }

    public Set<Character> getShortNames() {
      return shortNames;
    }

    public Set<String> getLongNames() {
      return longNames;
    }

    @Override
    public int hashCode() {
      return Objects.hash(longNames, shortNames);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      NameKey other = (NameKey) obj;
      return Objects.equals(longNames, other.longNames)
          && Objects.equals(shortNames, other.shortNames);
    }

    @Override
    public String toString() {
      return "NameKey [shortNames=" + shortNames + ", longNames=" + longNames + "]";
    }
  }

  private boolean validateOptionAndFlagSwitchNames(
      Map<OptionParameterKey, List<DaggerInjectionSite>> optionInjectionSites,
      OptionParameterMetadataService optionMetadataService,
      Map<FlagParameterKey, List<DaggerInjectionSite>> flagInjectionSites,
      FlagParameterMetadataService flagMetadataService) {
    final Map<NameKey, List<DaggerInjectionSite>> injectionSitesByNameKey = Stream
        .concat(
            optionInjectionSites.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                    .map(dis -> Map.entry(NameKey.fromOptionParameterKey(e.getKey()), dis))),
            flagInjectionSites.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                    .map(dis -> Map.entry(NameKey.fromFlagParameterKey(e.getKey()), dis))))
        .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));

    final Map<Character, Set<NameKey>> nameKeysByShortName = injectionSitesByNameKey.keySet()
        .stream().flatMap(nk -> nk.getShortNames().stream().map(sn -> Map.entry(sn, nk)))
        .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toSet())));

    final Map<String, Set<NameKey>> nameKeysByLongtName = injectionSitesByNameKey.keySet().stream()
        .flatMap(nk -> nk.getLongNames().stream().map(sn -> Map.entry(sn, nk)))
        .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toSet())));

    boolean valid = true;

    for (Map.Entry<Character, Set<NameKey>> e : nameKeysByShortName.entrySet()) {
      final Character shortName = e.getKey();
      final Set<NameKey> nameKeys = e.getValue();
      if (nameKeys.size() > 1) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Short name " + shortName + " is not consistently used with the same name set");
        // TODO Print examples of each conflicting name set
        valid = false;
      }
    }

    for (Map.Entry<String, Set<NameKey>> e : nameKeysByLongtName.entrySet()) {
      final String longName = e.getKey();
      final Set<NameKey> nameKeys = e.getValue();
      if (nameKeys.size() > 1) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Long name " + longName + " is not consistently used with the same name set");
        // TODO Print examples of each conflicting name set
        valid = false;
      }
    }

    return valid;
  }

  private static final Comparator<PositionalParameterKey> POSITIONAL_PARAMETER_KEY_COMPARATOR =
      Comparator.comparingInt(PositionalParameterKey::getPosition);

  private SortedMap<PositionalParameterKey, List<DaggerInjectionSite>> extractPositionalParameters(
      List<DaggerInjectionSite> injectionSites) {
    return injectionSites.stream().filter(d -> d.getQualifier().isPresent())
        .filter(d -> getTypes().isSameType(d.getQualifier().get().getAnnotationType(),
            getElements().getTypeElement(CliPositionalParameter.class.getCanonicalName()).asType()))
        .map(d -> {
          try {
            return Map.entry(PositionalParameterKey.fromInjectionSite(d), d);
          } catch (IllegalArgumentException e) {
            getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), d.getElement());
            return null;
          }
        }).filter(Objects::nonNull).collect(
            groupingBy(Map.Entry::getKey, () -> new TreeMap<>(POSITIONAL_PARAMETER_KEY_COMPARATOR),
                mapping(Map.Entry::getValue, toList())));
  }

  private PositionalParameterMetadataService createPositionalParameterMetadataService(
      Map<PositionalParameterKey, List<DaggerInjectionSite>> positionalInjectionSites) {
    boolean valid = true;
    final Map<PositionalParameterKey, PositionalParameterMetadata> metadataByPositionalParameter =
        new HashMap<>();
    for (Map.Entry<PositionalParameterKey, List<DaggerInjectionSite>> entry : positionalInjectionSites
        .entrySet()) {
      final PositionalParameterKey pk = entry.getKey();
      final List<DaggerInjectionSite> injectionSites = entry.getValue();
      final List<Map.Entry<PositionalRepresentationKey, DaggerInjectionSite>> rkes = injectionSites
          .stream().map(dis -> Map.entry(PositionalRepresentationKey.fromInjectionSite(dis), dis))
          .collect(toList());

      final List<Boolean> stringiness = rkes.stream()
          .map(prke -> getStringConverter()
              .generateConversionExpr(prke.getValue().getProvisionedType(), "_").isPresent())
          .collect(toList());
      final boolean allStringy = stringiness.stream().allMatch(b -> b);
      final boolean anyStringy = stringiness.stream().anyMatch(b -> b);
      final List<Boolean> listiness = rkes.stream()
          .map(prke -> getListOfStringConverter()
              .generateConversionExpr(prke.getValue().getProvisionedType(), "_").isPresent())
          .collect(toList());
      final boolean allListy = listiness.stream().allMatch(b -> b);
      final boolean anyListy = listiness.stream().anyMatch(b -> b);
      if (allStringy && allListy) {
        getMessager().printMessage(Diagnostic.Kind.ERROR, "Positional parameter " + pk.getPosition()
            + " is ambiguously convertible to String and List<String>");
        // TODO Print example ambiguous
        valid = false;
        continue;
      }
      if (!allStringy && !allListy) {
        if (anyStringy && anyListy) {
          getMessager().printMessage(Diagnostic.Kind.ERROR, "Positional parameter "
              + pk.getPosition()
              + " can partially be converted to String and List<String>, but cannot completely be converted to either");
          // TODO Print example no conversion
          valid = false;
          continue;
        } else {
          getMessager().printMessage(Diagnostic.Kind.ERROR, "Positional parameter "
              + pk.getPosition() + " cannot completely be converted to String or List<String>");
          // TODO Print example no conversion
          valid = false;
          continue;
        }
      }

      // We consider lists to be varargs
      final boolean varargs = allListy;

      final Map<Boolean, List<DaggerInjectionSite>> injectionSitesByRequired =
          rkes.stream().collect(Collectors.groupingBy(prke -> {
            // A varargs is always optional
            final boolean isConventionallyRequired = CliProcessing.isRequired(
                prke.getValue().isNullable(), prke.getKey().getDefaultValue().orElse(null));
            return isConventionallyRequired && !varargs;
          }, mapping(Map.Entry::getValue, toList())));

      if (injectionSitesByRequired.size() > 1) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Conflicting requiredness for positional parameter " + pk.getPosition());
        // TODO Print example required
        valid = false;
        continue;
      }

      final boolean required = injectionSitesByRequired.keySet().stream().anyMatch(b -> b == true);

      final List<PositionalParameterHelp> helps = injectionSites.stream()
          .flatMap(dis -> PositionalParameterHelp.fromInjectionSite(dis).stream()).distinct()
          .sorted(Comparator
              .<PositionalParameterHelp, String>comparing(PositionalParameterHelp::getName)
              .thenComparing(h -> h.getDescription().orElse(null),
                  Comparator.nullsLast(Comparator.naturalOrder())))
          .collect(toList());
      if (helps.size() > 1) {
        getMessager().printMessage(Diagnostic.Kind.WARNING,
            "Multiple help for positional parameter " + pk.getPosition()
                + ", help message is not deterministic");
        // TODO Print example conflicting help
      }
      final Optional<PositionalParameterHelp> maybeHelp =
          helps.isEmpty() ? Optional.empty() : Optional.of(helps.get(0));

      metadataByPositionalParameter.put(pk,
          new PositionalParameterMetadata(required, allListy,
              maybeHelp.map(PositionalParameterHelp::getName).orElse("value"),
              maybeHelp.flatMap(PositionalParameterHelp::getDescription).orElse(null)));
    }

    if (valid == false)
      return null;

    return position -> metadataByPositionalParameter
        .get(PositionalParameterKey.forPosition(position));
  }

  private static final Comparator<OptionParameterKey> OPTION_PARAMETER_KEY_COMPARATOR = Comparator
      .<OptionParameterKey, String>comparing(opk -> opk.getLongName().orElse(null),
          Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparing(opk -> opk.getShortName().orElse(null),
          Comparator.nullsLast(Comparator.naturalOrder()));

  private SortedMap<OptionParameterKey, List<DaggerInjectionSite>> extractOptionParameters(
      List<DaggerInjectionSite> injectionSites) {
    return injectionSites.stream().filter(d -> d.getQualifier().isPresent())
        .filter(d -> getTypes().isSameType(d.getQualifier().get().getAnnotationType(),
            getElements().getTypeElement(CliOptionParameter.class.getCanonicalName()).asType()))
        .map(d -> {
          try {
            return Map.entry(OptionParameterKey.fromInjectionSite(d), d);
          } catch (IllegalArgumentException e) {
            getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), d.getElement());
            return null;
          }
        }).filter(Objects::nonNull)
        .collect(groupingBy(Map.Entry::getKey, () -> new TreeMap<>(OPTION_PARAMETER_KEY_COMPARATOR),
            mapping(Map.Entry::getValue, toList())));
  }

  private OptionParameterMetadataService createOptionParameterMetadataService(
      Map<OptionParameterKey, List<DaggerInjectionSite>> optionInjectionSites) {
    boolean valid = true;
    final Map<OptionParameterKey, OptionParameterMetadata> metadataByOptionParameter =
        new HashMap<>();
    for (Map.Entry<OptionParameterKey, List<DaggerInjectionSite>> entry : optionInjectionSites
        .entrySet()) {
      final OptionParameterKey pk = entry.getKey();
      final List<DaggerInjectionSite> injectionSites = entry.getValue();
      final List<Map.Entry<OptionRepresentationKey, DaggerInjectionSite>> rkes = injectionSites
          .stream().map(dis -> Map.entry(OptionRepresentationKey.fromInjectionSite(dis), dis))
          .collect(toList());

      final Map<Boolean, List<DaggerInjectionSite>> injectionSitesByRequired = rkes.stream()
          .collect(Collectors.groupingBy(
              prke -> CliProcessing.isRequired(prke.getValue().isNullable(),
                  prke.getKey().getDefaultValue().orElse(null)),
              mapping(Map.Entry::getValue, toList())));

      if (injectionSitesByRequired.size() > 1) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Conflicting requiredness for option parameter " + optionParameterUserFacingString(
                pk.getShortName().orElse(null), pk.getLongName().orElse(null)));
        // TODO Print example required
        valid = false;
        continue;
      }

      final boolean required = injectionSitesByRequired.keySet().stream().anyMatch(b -> b == true);

      final List<OptionParameterHelp> helps = injectionSites.stream()
          .flatMap(dis -> OptionParameterHelp.fromInjectionSite(dis).stream()).distinct()
          .sorted(Comparator.<OptionParameterHelp, String>comparing(h -> h.getValueName())
              .thenComparing(h -> h.getDescription().orElse(null),
                  Comparator.nullsLast(Comparator.naturalOrder())))
          .collect(toList());
      if (helps.size() > 1) {
        getMessager().printMessage(Diagnostic.Kind.WARNING,
            "Multiple help for option parameter "
                + optionParameterUserFacingString(pk.getShortName().orElse(null),
                    pk.getLongName().orElse(null))
                + ", help message is not deterministic");
        // TODO Print example conflicting help
      }
      final Optional<OptionParameterHelp> maybeHelp =
          helps.isEmpty() ? Optional.empty() : Optional.of(helps.get(0));

      metadataByOptionParameter.put(pk,
          new OptionParameterMetadata(required,
              maybeHelp.map(OptionParameterHelp::getValueName).orElse("value"),
              maybeHelp.flatMap(OptionParameterHelp::getDescription).orElse(null)));
    }

    if (valid == false)
      return null;

    return (shortName, longName) -> metadataByOptionParameter
        .get(new OptionParameterKey(shortName, longName));
  }

  private static final Comparator<FlagParameterKey> FLAG_PARAMETER_KEY_COMPARATOR = Comparator
      .<FlagParameterKey, String>comparing(opk -> opk.getPositiveLongName().orElse(null),
          Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparing(opk -> opk.getPositiveShortName().orElse(null),
          Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparing(opk -> opk.getNegativeLongName().orElse(null),
          Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparing(opk -> opk.getNegativeShortName().orElse(null),
          Comparator.nullsLast(Comparator.naturalOrder()));

  private SortedMap<FlagParameterKey, List<DaggerInjectionSite>> extractFlagParameters(
      List<DaggerInjectionSite> injectionSites) {
    return injectionSites.stream().filter(d -> d.getQualifier().isPresent())
        .filter(d -> getTypes().isSameType(d.getQualifier().get().getAnnotationType(),
            getElements().getTypeElement(CliFlagParameter.class.getCanonicalName()).asType()))
        .map(d -> {
          try {
            return Map.entry(FlagParameterKey.fromInjectionSite(d), d);
          } catch (IllegalArgumentException e) {
            getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), d.getElement());
            return null;
          }
        }).filter(Objects::nonNull)
        .collect(groupingBy(Map.Entry::getKey, () -> new TreeMap<>(FLAG_PARAMETER_KEY_COMPARATOR),
            mapping(Map.Entry::getValue, toList())));
  }

  private FlagParameterMetadataService createFlagParameterMetadataService(
      Map<FlagParameterKey, List<DaggerInjectionSite>> flagInjectionSites) {
    boolean valid = true;
    final Map<FlagParameterKey, FlagParameterMetadata> metadataByFlagParameter = new HashMap<>();
    for (Map.Entry<FlagParameterKey, List<DaggerInjectionSite>> entry : flagInjectionSites
        .entrySet()) {
      final FlagParameterKey pk = entry.getKey();
      final List<DaggerInjectionSite> injectionSites = entry.getValue();
      final List<Map.Entry<FlagRepresentationKey, DaggerInjectionSite>> rkes = injectionSites
          .stream().map(dis -> Map.entry(FlagRepresentationKey.fromInjectionSite(dis), dis))
          .collect(toList());

      final Map<Boolean, List<DaggerInjectionSite>> injectionSitesByRequired = rkes.stream()
          .collect(Collectors.groupingBy(
              rke -> CliProcessing.isRequired(rke.getValue().isNullable(),
                  rke.getKey().getDefaultValue().orElse(null)),
              mapping(Map.Entry::getValue, toList())));

      if (injectionSitesByRequired.size() > 1) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Conflicting requiredness for flag parameter " + flagParameterUserFacingString(
                pk.getPositiveShortName().orElse(null), pk.getPositiveLongName().orElse(null),
                pk.getNegativeShortName().orElse(null), pk.getNegativeLongName().orElse(null)));
        // TODO Print example required
        valid = false;
        continue;
      }

      final boolean required = injectionSitesByRequired.keySet().stream().anyMatch(b -> b == true);

      final List<FlagParameterHelp> helps =
          injectionSites.stream().flatMap(dis -> FlagParameterHelp.fromInjectionSite(dis).stream())
              .distinct()
              .sorted(Comparator.<FlagParameterHelp, String>comparing(
                  h -> h.getDescription().orElse(null),
                  Comparator.nullsLast(Comparator.naturalOrder())))
              .collect(toList());
      if (helps.size() > 1) {
        getMessager()
            .printMessage(Diagnostic.Kind.WARNING,
                "Multiple help for flag parameter " + flagParameterUserFacingString(
                    pk.getPositiveShortName().orElse(null), pk.getPositiveLongName().orElse(null),
                    pk.getNegativeShortName().orElse(null), pk.getNegativeLongName().orElse(null))
                    + ", help message is not deterministic");
        // TODO Print example conflicting help
      }
      final Optional<FlagParameterHelp> maybeHelp =
          helps.isEmpty() ? Optional.empty() : Optional.of(helps.get(0));

      metadataByFlagParameter.put(pk, new FlagParameterMetadata(required,
          maybeHelp.flatMap(FlagParameterHelp::getDescription).orElse(null)));
    }

    if (valid == false)
      return null;

    return (positiveShortName, positiveLongName, negativeShortName,
        negativeLongName) -> metadataByFlagParameter.get(new FlagParameterKey(positiveShortName,
            positiveLongName, negativeShortName, negativeLongName));
  }

  private static final Comparator<PositionalRepresentationKey> POSITIONAL_REPRESENTATION_KEY_COMPARATOR =
      Comparator.<PositionalRepresentationKey>comparingInt(r -> r.getPosition())
          .thenComparing(r -> r.getType().toString())
          .thenComparing(r -> r.getDefaultValue().orElse(null),
              Comparator.nullsFirst(Comparator.naturalOrder()));

  private static final Comparator<OptionRepresentationKey> OPTION_REPRESENTATION_KEY_COMPARATOR =
      Comparator
          .<OptionRepresentationKey, Character>comparing(r -> r.getShortName().orElse(null),
              Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(r -> r.getLongName().orElse(null),
              Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(r -> r.getType().toString())
          .thenComparing(r -> r.getDefaultValue().orElse(null),
              Comparator.nullsFirst(Comparator.naturalOrder()));

  private static final Comparator<FlagRepresentationKey> FLAG_REPRESENTATION_KEY_COMPARATOR =
      Comparator
          .<FlagRepresentationKey, Character>comparing(r -> r.getPositiveShortName().orElse(null),
              Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(r -> r.getPositiveLongName().orElse(null),
              Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(r -> r.getNegativeShortName().orElse(null),
              Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(r -> r.getNegativeLongName().orElse(null),
              Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(r -> r.getType().toString())
          .thenComparing(r -> r.getDefaultValue().orElse(null),
              Comparator.nullsFirst(Comparator.naturalOrder()));

  private String generateModuleSource(String packageName, String moduleClassName,
      CommandMetadata commandMetadata,
      SortedMap<PositionalParameterKey, List<DaggerInjectionSite>> positionalInjectionSites,
      PositionalParameterMetadataService positionalMetadataService,
      SortedMap<OptionParameterKey, List<DaggerInjectionSite>> optionInjectionSites,
      OptionParameterMetadataService optionMetadataService,
      SortedMap<FlagParameterKey, List<DaggerInjectionSite>> flagInjectionSites,
      FlagParameterMetadataService flagMetadataService) {

    final SortedMap<PositionalParameterKey, Collection<PositionalRepresentationKey>> positionalRepresentationsByParameter =
        positionalInjectionSites.entrySet().stream()
            .flatMap(e -> e.getValue().stream()
                .map(dis -> Map.entry(PositionalParameterKey.fromInjectionSite(dis),
                    PositionalRepresentationKey.fromInjectionSite(dis))))
            .collect(groupingBy(Map.Entry::getKey,
                () -> new TreeMap<>(POSITIONAL_PARAMETER_KEY_COMPARATOR),
                mapping(Map.Entry::getValue,
                    toCollection(() -> new TreeSet<>(POSITIONAL_REPRESENTATION_KEY_COMPARATOR)))));
    for (Map.Entry<PositionalParameterKey, Collection<PositionalRepresentationKey>> entry : positionalRepresentationsByParameter
        .entrySet()) {
      final PositionalParameterKey parameter = entry.getKey();
      final PositionalParameterMetadata metadata =
          positionalMetadataService.getPositionalParameterMetadata(parameter.getPosition());
      final Collection<PositionalRepresentationKey> representations = entry.getValue();
      for (PositionalRepresentationKey representation : List.copyOf(representations)) {
        if (metadata.isList()) {
          representations.add(toListOfStringRepresentation(representation));
        } else {
          representations.add(toStringRepresentation(representation));
        }
      }
    }

    final SortedMap<OptionParameterKey, Collection<OptionRepresentationKey>> optionRepresentationsByParameter =
        optionInjectionSites.entrySet().stream()
            .flatMap(e -> e.getValue().stream()
                .map(dis -> Map.entry(e.getKey(), OptionRepresentationKey.fromInjectionSite(dis))))
            .collect(groupingBy(Map.Entry::getKey,
                () -> new TreeMap<>(OPTION_PARAMETER_KEY_COMPARATOR), mapping(Map.Entry::getValue,
                    toCollection(() -> new TreeSet<>(OPTION_REPRESENTATION_KEY_COMPARATOR)))));
    for (Map.Entry<OptionParameterKey, ? extends Collection<OptionRepresentationKey>> entry : optionRepresentationsByParameter
        .entrySet()) {
      final Collection<OptionRepresentationKey> representations = entry.getValue();
      for (OptionRepresentationKey representation : List.copyOf(representations)) {
        representations.add(toStringRepresentation(representation));
        representations.add(toListOfStringRepresentation(representation));
      }
    }

    final SortedMap<FlagParameterKey, Collection<FlagRepresentationKey>> flagRepresentationsByParameter =
        flagInjectionSites.entrySet().stream()
            .flatMap(e -> e.getValue().stream()
                .map(dis -> Map.entry(e.getKey(), FlagRepresentationKey.fromInjectionSite(dis))))
            .collect(groupingBy(Map.Entry::getKey,
                () -> new TreeMap<>(FLAG_PARAMETER_KEY_COMPARATOR), mapping(Map.Entry::getValue,
                    toCollection(() -> new TreeSet<>(FLAG_REPRESENTATION_KEY_COMPARATOR)))));
    for (Map.Entry<FlagParameterKey, ? extends Collection<FlagRepresentationKey>> entry : flagRepresentationsByParameter
        .entrySet()) {
      final Collection<FlagRepresentationKey> representations = entry.getValue();
      for (FlagRepresentationKey representation : List.copyOf(representations)) {
        representations.add(toBooleanRepresentation(representation));
        representations.add(toListOfBooleanRepresentation(representation));
      }
    }

    final StringWriter result = new StringWriter();
    try (PrintWriter out = new PrintWriter(result)) {
      out.println("package " + packageName + ";");
      out.println();
      out.println("import static java.util.Arrays.asList;");
      out.println("import static java.util.Collections.emptyList;");
      out.println("import static java.util.Collections.singletonList;");
      out.println("import static java.util.Collections.unmodifiableList;");
      out.println();
      out.println("import dagger.Module;");
      out.println("import dagger.Provides;");
      out.println("import java.util.HashMap;");
      out.println("import java.util.List;");
      out.println("import java.util.Map;");
      out.println("import java.util.Optional;");
      out.println("import javax.annotation.Nullable;");
      out.println("import javax.annotation.processing.Generated;");
      out.println("import rapier.internal.RapierGenerated;");
      out.println("import rapier.cli.CliSyntaxException;");
      out.println("import rapier.cli.CliFlagParameter;");
      out.println("import rapier.cli.CliOptionParameter;");
      out.println("import rapier.cli.CliPositionalParameter;");
      out.println("import " + JustArgs.class.getName() + ";");
      out.println();
      out.println("@Module");
      out.println("@RapierGenerated");
      out.println("@Generated(");
      out.println("    value = \"" + CliProcessor.class.getName() + "@" + version + "\",");
      out.println("    comments = \"" + Java.escapeString(url) + "\",");
      out.println("    date = \"" + date.toInstant().toString() + "\")");
      out.println("public class " + moduleClassName + " {");
      out.println();

      if (commandMetadata.isTesting()) {
        // If testing is enabled, generate an exception class to use instead of exiting. It's hard
        // to test your library if your tests just exit the program running the tests!
        out.println("    @SuppressWarnings(\"serial\")");
        out.println("    public static class ExitException extends RuntimeException {");
        out.println("        private final int status;");
        out.println("        public ExitException(int status) {");
        out.println("            this.status = status;");
        out.println("        }");
        out.println("        public int getStatus() { return status; }");
        out.println("    }");
        out.println();
      }

      // Generate instance fields for each positional parameter representation
      out.println("    // Positional parameters");
      for (PositionalParameterKey ppk : positionalRepresentationsByParameter.keySet()) {
        final int position = ppk.getPosition();
        final PositionalParameterMetadata metadata =
            positionalMetadataService.getPositionalParameterMetadata(position);
        final boolean parameterIsRequired = metadata.isRequired();
        final boolean parameterIsList = metadata.isList();
        emitPositionalParameterInstanceFieldDeclaration(out, position, parameterIsRequired,
            parameterIsList);
      }
      out.println();

      // Generate instance fields for each option parameter representation
      out.println("    // Option parameters");
      for (OptionParameterKey opk : optionRepresentationsByParameter.keySet()) {
        final Character optionShortName = opk.getShortName().orElse(null);
        final String optionLongName = opk.getLongName().orElse(null);
        final OptionParameterMetadata metadata =
            optionMetadataService.getOptionParameterMetadata(optionShortName, optionLongName);
        final boolean parameterIsRequired = metadata.isRequired();
        emitOptionParameterInstanceFieldDeclaration(out, optionShortName, optionLongName,
            parameterIsRequired);
      }
      out.println();

      // Generate instance fields for each flag parameter representation
      out.println("    // Flag parameters");
      for (FlagParameterKey fpk : flagRepresentationsByParameter.keySet()) {
        final Character flagPositiveShortName = fpk.getPositiveShortName().orElse(null);
        final String flagPositiveLongName = fpk.getPositiveLongName().orElse(null);
        final Character flagNegativeShortName = fpk.getNegativeShortName().orElse(null);
        final String flagNegativeLongName = fpk.getNegativeLongName().orElse(null);
        final FlagParameterMetadata metadata =
            flagMetadataService.getFlagParameterMetadata(flagPositiveShortName,
                flagPositiveLongName, flagNegativeShortName, flagNegativeLongName);
        final boolean parameterIsRequired = metadata.isRequired();
        emitFlagParameterInstanceFieldDeclaration(out, flagPositiveShortName, flagPositiveLongName,
            flagNegativeShortName, flagNegativeLongName, parameterIsRequired);
      }
      out.println();



      out.println("    public " + moduleClassName + "(String[] args) {");
      out.println("        this(asList(args));");
      out.println("    }");
      out.println();


      out.println("    public " + moduleClassName + "(List<String> args) {");

      out.println("        // Generate the maps for option short names and long names");
      out.println("        final Map<Character, String> optionShortNames = new HashMap<>();");
      out.println("        final Map<String, String> optionLongNames = new HashMap<>();");
      emitOptionParameterInstanceFieldInitPreparation(out,
          optionRepresentationsByParameter.keySet());
      out.println();

      out.println("        // Generate the maps for flag short names and long names");
      out.println("        final Map<Character, String> flagPositiveShortNames = new HashMap<>();");
      out.println("        final Map<String, String> flagPositiveLongNames = new HashMap<>();");
      out.println("        final Map<Character, String> flagNegativeShortNames = new HashMap<>();");
      out.println("        final Map<String, String> flagNegativeLongNames = new HashMap<>();");
      emitFlagParameterInstanceFieldInitPreparation(out, flagRepresentationsByParameter.keySet());
      if (commandMetadata.isProvideStandardHelp()) {
        out.println("        // Add the standard help flags");
        out.println("        flagPositiveShortNames.put('" + STANDARD_HELP_SHORT_NAME
            + "', \"rapier.standard.help\");");
        out.println("        flagPositiveLongNames.put(\"" + STANDARD_HELP_LONG_NAME
            + "\", \"rapier.standard.help\");");
        out.println();
      }
      if (commandMetadata.isProvideStandardVersion()) {
        out.println("        // Add the version flag");
        out.println("        flagPositiveShortNames.put('" + STANDARD_VERSION_SHORT_NAME
            + "', \"rapier.standard.version\");");
        out.println("        flagPositiveLongNames.put(\"" + STANDARD_VERSION_LONG_NAME
            + "\", \"rapier.standard.version\");");
        out.println();
      }
      out.println();

      out.println("        try {");
      out.println("            // Parse the arguments");
      out.println("            final JustArgs.ParsedArgs parsed = JustArgs.parseArgs(");
      out.println("                args,");
      out.println("                optionShortNames,");
      out.println("                optionLongNames,");
      out.println("                flagPositiveShortNames,");
      out.println("                flagPositiveLongNames,");
      out.println("                flagNegativeShortNames,");
      out.println("                flagNegativeLongNames);");
      out.println();

      // Generate the initialization of each positional parameter representation
      out.println("            // Initialize positional parameters");
      for (PositionalParameterKey ppk : positionalRepresentationsByParameter.keySet()) {
        final int position = ppk.getPosition();
        final PositionalParameterMetadata metadata =
            positionalMetadataService.getPositionalParameterMetadata(position);
        final boolean parameterIsRequired = metadata.isRequired();
        final boolean parameterIsList = metadata.isList();
        emitPositionalParameterInstanceFieldInitClause(out, position, parameterIsRequired,
            parameterIsList);
      }
      emitPositionalParameterValidationClause(out, positionalRepresentationsByParameter.keySet(),
          positionalMetadataService);
      out.println();

      out.println("            // Initialize option parameters");
      for (OptionParameterKey opk : optionRepresentationsByParameter.keySet()) {
        final Character optionShortName = opk.getShortName().orElse(null);
        final String optionLongName = opk.getLongName().orElse(null);
        final OptionParameterMetadata metadata =
            optionMetadataService.getOptionParameterMetadata(optionShortName, optionLongName);
        final boolean parameterIsRequired = metadata.isRequired();
        emitOptionParameterInstanceFieldInitClause(out, optionShortName, optionLongName,
            parameterIsRequired);
      }
      out.println();

      out.println("            // Initialize flag parameters");
      for (FlagParameterKey fpk : flagRepresentationsByParameter.keySet()) {
        final Character flagPositiveShortName = fpk.getPositiveShortName().orElse(null);
        final String flagPositiveLongName = fpk.getPositiveLongName().orElse(null);
        final Character flagNegativeShortName = fpk.getNegativeShortName().orElse(null);
        final String flagNegativeLongName = fpk.getNegativeLongName().orElse(null);
        final FlagParameterMetadata metadata =
            flagMetadataService.getFlagParameterMetadata(flagPositiveShortName,
                flagPositiveLongName, flagNegativeShortName, flagNegativeLongName);
        final boolean parameterIsRequired = metadata.isRequired();
        emitFlagParameterInstanceFieldInitClause(out, flagPositiveShortName, flagPositiveLongName,
            flagNegativeShortName, flagNegativeLongName, parameterIsRequired);
      }

      if (commandMetadata.isProvideStandardHelp()) {
        out.println("            // Check for standard help");
        out.println(
            "            final boolean standardHelpRequested = parsed.getFlags().containsKey(\"rapier.standard.help\");");
        out.println();
      }

      if (commandMetadata.isProvideStandardVersion()) {
        out.println("            // Check for standard version");
        out.println(
            "            final boolean standardVersionRequested = parsed.getFlags().containsKey(\"rapier.standard.version\");");
        out.println();
      }

      if (commandMetadata.isProvideStandardVersion()) {
        out.println("            if(standardVersionRequested) {");
        if (commandMetadata.isTesting()) {
          // We print to System.out so we capture the help as program output for testing
          out.println("                System.out.println(standardVersionMessage());");
        } else {
          out.println("                System.err.println(standardVersionMessage());");
        }
        out.println("            }");
        out.println();
      }

      if (commandMetadata.isProvideStandardHelp()) {
        out.println("            if(standardHelpRequested) {");
        if (commandMetadata.isTesting()) {
          // We print to System.out so we capture the help as program output for testing
          out.println("                System.out.println(standardHelpMessage());");
        } else {
          out.println("                System.err.println(standardHelpMessage());");
        }
        out.println("            }");
        out.println();
      }

      if (commandMetadata.isProvideStandardHelp() || commandMetadata.isProvideStandardVersion()) {
        final List<String> conditions = new ArrayList<>(2);
        if (commandMetadata.isProvideStandardHelp())
          conditions.add("standardHelpRequested");
        if (commandMetadata.isProvideStandardVersion())
          conditions.add("standardVersionRequested");
        out.println("            if(" + String.join(" || ", conditions) + ") {");
        if (commandMetadata.isTesting()) {
          out.println("                throw new ExitException(0);");
        } else {
          out.println("                System.exit(0);");
          out.println("                throw new AssertionError(\"exited\");");
        }
        out.println("            }");
      }

      out.println("        }");
      out.println("        catch (JustArgs.IllegalSyntaxException e) {");
      if (commandMetadata.isProvideStandardHelp()) {
        out.println("            // Standard help is active. Print the help message and exit.");
        if (commandMetadata.isTesting()) {
          out.println("            System.out.println(e.getMessage());");
          out.println("            System.out.println(standardHelpMessage());");
          out.println("            throw new ExitException(1);");
        } else {
          out.println("            System.err.println(e.getMessage());");
          out.println("            System.err.println(standardHelpMessage());");
          out.println("            System.exit(1);");
          out.println("            throw new AssertionError(\"exited\");");
        }
      } else {
        out.println("            // Standard help is not active. Map and propagate the exception.");
        out.println("            throw new CliSyntaxException(e.getMessage());");
      }
      out.println("        }");
      out.println("        catch(CliSyntaxException e) {");
      if (commandMetadata.isProvideStandardHelp()) {
        out.println("            // Standard help is active. Print the help message and exit.");
        if (commandMetadata.isTesting()) {
          out.println("            System.out.println(e.getMessage());");
          out.println("            System.out.println(standardHelpMessage());");
          out.println("            throw new ExitException(1);");
        } else {
          out.println("            System.err.println(e.getMessage());");
          out.println("            System.err.println(standardHelpMessage());");
          out.println("            System.exit(1);");
          out.println("            throw new AssertionError(\"exited\");");
        }
      } else {
        out.println("            // Standard help is not active. Propagate the exception.");
        out.println("            throw e;");
      }
      out.println("        }");
      out.println("    }");
      out.println();

      // Generate the binding methods for each positional parameter representation
      for (Map.Entry<PositionalParameterKey, ? extends Collection<PositionalRepresentationKey>> e : positionalRepresentationsByParameter
          .entrySet()) {
        final PositionalParameterKey parameter = e.getKey();
        final PositionalParameterMetadata metadata =
            positionalMetadataService.getPositionalParameterMetadata(parameter.getPosition());
        final Collection<PositionalRepresentationKey> representations = e.getValue();
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

      // Generate the binding methods for each option parameter representation
      for (Map.Entry<OptionParameterKey, ? extends Collection<OptionRepresentationKey>> e : optionRepresentationsByParameter
          .entrySet()) {
        final OptionParameterKey parameter = e.getKey();
        final Character optionShortName = parameter.getShortName().orElse(null);
        final String optionLongName = parameter.getLongName().orElse(null);
        final OptionParameterMetadata metadata =
            optionMetadataService.getOptionParameterMetadata(optionShortName, optionLongName);
        final Collection<OptionRepresentationKey> representations = e.getValue();
        final boolean parameterIsRequired = metadata.isRequired();
        for (OptionRepresentationKey representation : representations) {
          final TypeMirror representationType = representation.getType();
          final String representationDefaultValue = representation.getDefaultValue().orElse(null);
          emitOptionParameterRepresentationBindingMethods(out, optionShortName, optionLongName,
              parameterIsRequired, representationType, representationDefaultValue);
        }
      }

      // Generate the binding methods for each flag parameter representation
      for (Map.Entry<FlagParameterKey, ? extends Collection<FlagRepresentationKey>> e : flagRepresentationsByParameter
          .entrySet()) {
        final FlagParameterKey parameter = e.getKey();
        final Character flagPositiveShortName = parameter.getPositiveShortName().orElse(null);
        final String flagPositiveLongName = parameter.getPositiveLongName().orElse(null);
        final Character flagNegativeShortName = parameter.getNegativeShortName().orElse(null);
        final String flagNegativeLongName = parameter.getNegativeLongName().orElse(null);
        final FlagParameterMetadata metadata =
            flagMetadataService.getFlagParameterMetadata(flagPositiveShortName,
                flagPositiveLongName, flagNegativeShortName, flagNegativeLongName);
        final Collection<FlagRepresentationKey> representations = e.getValue();
        final boolean parameterIsRequired = metadata.isRequired();
        for (FlagRepresentationKey representation : representations) {
          final TypeMirror representationType = representation.getType();
          final Boolean representationDefaultValue = representation.getDefaultValue().orElse(null);
          emitFlagParameterRepresentationBindingMethods(out, flagPositiveShortName,
              flagPositiveLongName, flagNegativeShortName, flagNegativeLongName,
              parameterIsRequired, representationType, representationDefaultValue);
        }
      }

      // Generate the help message
      if (commandMetadata.isProvideStandardHelp()) {
        emitStandardHelpMessageMethod(out, commandMetadata, positionalRepresentationsByParameter,
            positionalMetadataService, optionRepresentationsByParameter, optionMetadataService,
            flagRepresentationsByParameter, flagMetadataService);
      }

      // Generate the version message
      if (commandMetadata.isProvideStandardVersion()) {
        emitStandardVersionMessageMethod(out, commandMetadata);
      }

      out.println("}");
    }

    return result.toString();
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // POSITIONAL PARAMETER CODE GENERATION //////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////////////

  private void emitPositionalParameterValidationClause(PrintWriter out,
      Set<PositionalParameterKey> parameters,
      PositionalParameterMetadataService parameterMetadataService) {
    // We only really need to check to see if there are more parameters than we know about.
    final boolean hasVarargs = parameters.stream().mapToInt(x -> x.getPosition())
        .mapToObj(parameterMetadataService::getPositionalParameterMetadata)
        .filter(PositionalParameterMetadata::isList).findFirst().isPresent();
    if (hasVarargs) {
      // If we have varargs, then there can't be too many positional parameters. Any "extras" just
      // get put into varargs.
      return;
    }

    final int maxPosition = parameters.stream().mapToInt(x -> x.getPosition()).max().orElse(-1);
    out.println("            if(parsed.getArgs().size() > " + (maxPosition + 1) + ") {");
    out.println("                throw new CliSyntaxException(");
    out.println("                    \"Too many positional parameters\");");
    out.println("            }");
  }

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

    out.println("            if(parsed.getArgs().size() <= " + position + ") {");
    if (parameterIsRequired) {
      out.println("                throw new CliSyntaxException(");
      out.println(
          "                    \"Missing required positional parameter " + position + "\");");
    } else {
      out.println("                this." + fieldName + " = " + defaultValueExpr + ";");
    }
    out.println("            } else {");
    out.println("                this." + fieldName + " = " + extractValueExpr + ";");
    out.println("            }");
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

    final String representationAnnotation;
    if (representationDefaultValue != null) {
      representationAnnotation = "@CliPositionalParameter(value=" + position + ", defaultValue=\""
          + Java.escapeString(representationDefaultValue) + "\")";
    } else {
      representationAnnotation = "@CliPositionalParameter(" + position + ")";
    }

    if (parameterIsList) {
      if (getTypes().isSameType(representationType, getListOfStringType())) {
        out.println("    @Provides");
        out.println("    " + representationAnnotation);
        out.println("    public List<String> " + baseMethodName + "AsListOfString() {");
        if (representationDefaultValue != null) {
          out.println("        if(" + fieldName + " == null)");
          out.println("            return singletonList(\""
              + Java.escapeString(representationDefaultValue) + "\");");
        } else if (parameterIsRequired == false) {
          // This code looks weird because the logic is not entirely localized. Additional logic is
          // included in the constructor. If a required parameter is not set, the constructor will
          // throw an exception. If an optional parameter is not set, the constructor will set the
          // field to null. This is why we check for null here.
          out.println("        if(" + fieldName + " == null)");
          out.println("            return emptyList();");
        }
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();

        if (representationDefaultValue == null && parameterIsRequired == false) {
          out.println("    @Provides");
          out.println("    " + representationAnnotation);
          out.println("    public Optional<List<String>> " + baseMethodName
              + "AsOptionalOfListOfString(" + representationAnnotation + " List<String> value) {");
          out.println("        return Optional.of(value);");
          out.println("    }");
          out.println();
        }
      } else {
        final String typeSimpleName = getSimpleTypeName(representationType);
        final String conversionExpr = getListOfStringConverter()
            .generateConversionExpr(representationType, "value").orElse(null);
        if (conversionExpr == null) {
          // This should never happen, we've already checked that the conversion is possible.
          getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Cannot convert " + representationType + " from " + getListOfStringType());
          return;
        }

        out.println("    @Provides");
        out.println("    " + representationAnnotation);
        out.println("    public " + representationType + " " + baseMethodName + "As"
            + typeSimpleName + "(" + representationAnnotation + " List<String> value) {");
        out.println("        " + representationType + " result;");
        out.println("        try {");
        out.println("            result = " + conversionExpr + ";");
        out.println("        } catch (Exception e) {");
        out.println("            throw new IllegalArgumentException(");
        out.println("                \"Positional parameter " + position + " representation "
            + representationType + " argument not valid\", e);");
        out.println("        }");
        out.println("        if(result == null) {");
        out.println("            throw new IllegalStateException(");
        out.println("                \"Positional parameter " + position + " representation "
            + representationType + " not set\");");
        out.println("        }");
        out.println("        return result;");
        out.println("    }");
        out.println();

        if (representationDefaultValue == null && parameterIsRequired == false) {
          out.println("    @Provides");
          out.println("    " + representationAnnotation);
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(" + representationAnnotation + " "
              + representationType + " value) {");
          out.println("        return Optional.of(value);");
          out.println("    }");
          out.println();
        }
      }
    } else {
      final boolean representationIsNullable =
          representationDefaultValue == null && !parameterIsRequired;

      final String nullableAnnotation = representationIsNullable ? "@Nullable" : "";

      if (getTypes().isSameType(representationType, getStringType())) {
        out.println("    " + nullableAnnotation);
        out.println("    @Provides");
        out.println("    " + representationAnnotation);
        out.println("    public String " + baseMethodName + "AsString() {");
        if (representationDefaultValue != null) {
          out.println("        if(" + fieldName + " == null) {");
          out.println(
              "            return \"" + Java.escapeString(representationDefaultValue) + "\";");
          out.println("        }");
        } else if (parameterIsRequired) {
          // No special action required here. It is handled in the constructor.
        }
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();

        if (representationIsNullable) {
          out.println("    @Provides");
          out.println("    " + representationAnnotation);
          out.println("    public Optional<String> " + baseMethodName + "AsOptionalOfString("
              + representationAnnotation + " String value) {");
          out.println("        return Optional.ofNullable(value);");
          out.println("    }");
          out.println();
        }
      } else {
        final String typeSimpleName = getSimpleTypeName(representationType);
        final String conversionExpr =
            getStringConverter().generateConversionExpr(representationType, "value").orElse(null);
        if (conversionExpr == null) {
          // This should never happen, we've already checked that the conversion is possible.
          getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Cannot convert " + representationType + " from " + getListOfStringType());
          return;
        }

        out.println("    " + nullableAnnotation);
        out.println("    @Provides");
        out.println("    " + representationAnnotation);
        out.println("    public " + representationType + " " + baseMethodName + "As"
            + typeSimpleName + "(" + representationAnnotation + " String value) {");
        out.println("        if(value == null)");
        out.println("            return null;");
        out.println("        " + representationType + " result;");
        out.println("        try {");
        out.println("            result = " + conversionExpr + ";");
        out.println("        } catch (Exception e) {");
        out.println("            throw new IllegalArgumentException(");
        out.println("                \"Positional parameter " + position + " representation "
            + representationType + " argument not valid\", e);");
        out.println("        }");
        if (representationIsNullable == false) {
          out.println("        if(result == null) {");
          out.println("            throw new IllegalStateException(");
          out.println("                \"Positional parameter " + position + " representation "
              + representationType + " not set\");");
          out.println("        }");
        }
        out.println("        return result;");
        out.println("    }");
        out.println();

        if (representationIsNullable == true) {
          out.println("    " + nullableAnnotation);
          out.println("    @Provides");
          out.println("    " + representationAnnotation);
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(" + representationAnnotation + " "
              + representationType + " value) {");
          out.println("        return Optional.ofNullable(value);");
          out.println("    }");
          out.println();
        }
      }
    }
  }

  private String positionalParameterInstanceFieldName(int position) {
    return "positional" + position;
  }

  private PositionalRepresentationKey toStringRepresentation(PositionalRepresentationKey key) {
    return new PositionalRepresentationKey(getStringType(), key.getPosition(),
        key.getDefaultValue().orElse(null));
  }

  private PositionalRepresentationKey toListOfStringRepresentation(
      PositionalRepresentationKey key) {
    return new PositionalRepresentationKey(getListOfStringType(), key.getPosition(),
        key.getDefaultValue().orElse(null));
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
      String longName, boolean parameterIsRequired) {
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
    out.println("    private final List<String> " + fieldName + ";");
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
      Collection<OptionParameterKey> parameters) {
    for (OptionParameterKey parameter : parameters) {
      final Character shortName = parameter.getShortName().orElse(null);
      if (shortName == null)
        continue;
      final String parameterSignature =
          optionParameterSignature(shortName, parameter.getLongName().orElse(null));
      out.println("        optionShortNames.put(\'" + shortName + "\', \"rapier.option."
          + parameterSignature + "\");");
    }
    for (OptionParameterKey parameter : parameters) {
      final String longName = parameter.getLongName().orElse(null);
      if (longName == null)
        continue;
      final String parameterSignature =
          optionParameterSignature(parameter.getShortName().orElse(null), longName);
      out.println("        optionLongNames.put(\"" + longName + "\", \"rapier.option."
          + parameterSignature + "\");");
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
      String longName, boolean parameterIsRequired) {
    final String fieldName = optionParameterInstanceFieldName(shortName, longName);
    final String signature = optionParameterSignature(shortName, longName);
    final String userFacingString = optionParameterUserFacingString(shortName, longName);

    out.println("            if(parsed.getOptions().getOrDefault(\"rapier.option." + signature
        + "\", emptyList()).isEmpty()) {");
    if (parameterIsRequired) {
      out.println("                throw new CliSyntaxException(");
      out.println(
          "                    \"Missing required option parameter " + userFacingString + "\");");
    } else {
      out.println("                this." + fieldName + " = null;");
    }
    out.println("            } else {");
    out.println("                this." + fieldName
        + " = unmodifiableList(parsed.getOptions().get(\"rapier.option." + signature + "\"));");
    out.println("            }");
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
      String longName, boolean parameterIsRequired, TypeMirror representationType,
      String representationDefaultValue) {
    final String fieldName = optionParameterInstanceFieldName(shortName, longName);
    final String signature = optionParameterSignature(shortName, longName);
    final String userFacingName = optionParameterUserFacingString(shortName, longName);
    final String shortNameClause = shortName != null ? "shortName='" + shortName + "'" : null;
    final String longNameClause = longName != null ? "longName=\"" + longName + "\"" : null;
    final String nameClauses = Stream.of(shortNameClause, longNameClause).filter(Objects::nonNull)
        .collect(Collectors.joining(", "));

    final String listOfStringConversionExpr =
        getListOfStringConverter().generateConversionExpr(representationType, "value").orElse(null);
    final String stringConversionExpr =
        getStringConverter().generateConversionExpr(representationType, "value").orElse(null);
    if (listOfStringConversionExpr == null && stringConversionExpr == null) {
      // The user should provide their own conversion
      getMessager().printMessage(Diagnostic.Kind.ERROR,
          "No conversion available for " + userFacingName + " to " + representationType);
      // TODO Print site
      return;
    }
    if (listOfStringConversionExpr != null && stringConversionExpr != null) {
      // The user should provide their own conversion
      getMessager().printMessage(Diagnostic.Kind.ERROR,
          "Multiple conversions available for " + userFacingName + " to " + representationType);
      // TODO Print site
      return;
    }

    final boolean representationIsList = listOfStringConversionExpr != null;

    final StringBuilder baseMethodName =
        new StringBuilder().append("provideOption").append(signature);
    if (representationDefaultValue != null) {
      baseMethodName.append("WithDefaultValue").append(stringSignature(representationDefaultValue));
    }

    final String representationAnnotation;
    if (representationDefaultValue != null) {
      representationAnnotation = "@CliOptionParameter(" + nameClauses + ", defaultValue=\""
          + Java.escapeString(representationDefaultValue) + "\")";
    } else {
      representationAnnotation = "@CliOptionParameter(" + nameClauses + ")";
    }

    if (representationIsList) {
      if (getTypes().isSameType(representationType, getListOfStringType())) {
        out.println("    @Provides");
        out.println("    " + representationAnnotation);
        out.println("    public List<String> " + baseMethodName + "AsListOfString() {");
        if (representationDefaultValue != null) {
          out.println("        if(" + fieldName + " == null)");
          out.println("            return singletonList(\""
              + Java.escapeString(representationDefaultValue) + "\");");
        } else if (parameterIsRequired == false) {
          // This code looks weird because the logic is not entirely localized. Additional logic is
          // included in the constructor. If a required parameter is not set, the constructor will
          // throw an exception. If an optional parameter is not set, the constructor will set the
          // field to null. This is why we check for null here.
          out.println("        if(" + fieldName + " == null)");
          out.println("            return emptyList();");
        }
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();

        if (representationDefaultValue == null && parameterIsRequired == false) {
          out.println("    @Provides");
          out.println("    " + representationAnnotation);
          out.println("    public Optional<List<String>> " + baseMethodName
              + "AsOptionalOfListOfString(" + representationAnnotation + " List<String> value) {");
          out.println("        return Optional.of(value);");
          out.println("    }");
          out.println();
        }
      } else {
        final String typeSimpleName = getSimpleTypeName(representationType);
        final String conversionExpr = listOfStringConversionExpr;

        out.println("    @Provides");
        out.println("    " + representationAnnotation);
        out.println("    public " + representationType + " " + baseMethodName + "As"
            + typeSimpleName + "(" + representationAnnotation + " List<String> value) {");
        out.println("        " + representationType + " result;");
        out.println("        try {");
        out.println("            result = " + conversionExpr + ";");
        out.println("        } catch (Exception e) {");
        out.println("            throw new IllegalArgumentException(");
        out.println("                \"Option parameter " + userFacingName + " representation "
            + representationType + " argument not valid\", e);");
        out.println("        }");
        out.println("        if(result == null) {");
        out.println("            throw new IllegalStateException(");
        out.println("                \"Option parameter " + userFacingName + " representation "
            + representationType + " not set\");");
        out.println("        }");
        out.println("        return result;");
        out.println("    }");
        out.println();

        if (representationDefaultValue == null && parameterIsRequired == false) {
          out.println("    @Provides");
          out.println("    " + representationAnnotation);
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(" + representationAnnotation + " "
              + representationType + " value) {");
          out.println("        return Optional.of(value);");
          out.println("    }");
          out.println();
        }
      }
    } else {
      final boolean representationIsNullable =
          representationDefaultValue == null && !parameterIsRequired;

      final String nullableAnnotation = representationIsNullable ? "@Nullable" : "";

      if (getTypes().isSameType(representationType, getStringType())) {
        out.println("    " + nullableAnnotation);
        out.println("    @Provides");
        out.println("    " + representationAnnotation);
        out.println("    public String " + baseMethodName + "AsString() {");
        if (representationDefaultValue != null) {
          out.println("        if(" + fieldName + " == null) {");
          out.println(
              "            return \"" + Java.escapeString(representationDefaultValue) + "\";");
          out.println("        }");
        } else if (parameterIsRequired) {
          // No special action required here. It is handled in the constructor.
        }
        out.println("        return " + fieldName + ".get(" + fieldName + ".size()-1);");
        out.println("    }");
        out.println();

        if (representationIsNullable) {
          out.println("    @Provides");
          out.println("    " + representationAnnotation);
          out.println("    public Optional<String> " + baseMethodName + "AsOptionalOfString("
              + representationAnnotation + " String value) {");
          out.println("        return Optional.ofNullable(value);");
          out.println("    }");
          out.println();
        }
      } else {
        final String typeSimpleName = getSimpleTypeName(representationType);
        final String conversionExpr = stringConversionExpr;

        out.println("    " + nullableAnnotation);
        out.println("    @Provides");
        out.println("    " + representationAnnotation);
        out.println("    public " + representationType + " " + baseMethodName + "As"
            + typeSimpleName + "(" + representationAnnotation + " String value) {");
        out.println("        if(value == null)");
        out.println("            return null;");
        out.println("        " + representationType + " result;");
        out.println("        try {");
        out.println("            result = " + conversionExpr + ";");
        out.println("        } catch (Exception e) {");
        out.println("            throw new IllegalArgumentException(");
        out.println("                \"Option parameter " + userFacingName + " representation "
            + representationType + " argument not valid\", e);");
        out.println("        }");
        if (representationIsNullable == false) {
          out.println("        if(result == null) {");
          out.println("            throw new IllegalStateException(");
          out.println("                \"Option parameter " + userFacingName + " representation "
              + representationType + " not set\");");
          out.println("        }");
        }
        out.println("        return result;");
        out.println("    }");
        out.println();

        if (representationIsNullable == true) {
          out.println("    " + nullableAnnotation);
          out.println("    @Provides");
          out.println("    " + representationAnnotation);
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(" + representationAnnotation + " "
              + representationType + " value) {");
          out.println("        return Optional.ofNullable(value);");
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
    return stringSignature(String.join("/", parts));
  }

  private OptionRepresentationKey toStringRepresentation(OptionRepresentationKey key) {
    return new OptionRepresentationKey(getStringType(), key.getShortName().orElse(null),
        key.getLongName().orElse(null), key.getDefaultValue().orElse(null));
  }

  private OptionRepresentationKey toListOfStringRepresentation(OptionRepresentationKey key) {
    return new OptionRepresentationKey(getListOfStringType(), key.getShortName().orElse(null),
        key.getLongName().orElse(null), key.getDefaultValue().orElse(null));
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // FLAG PARAMETER CODE GENERATION ////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Emits the instance field declaration for a flag parameter representation.
   * 
   * @param out the PrintWriter to write to
   * @param positiveShortName the name of the short positive version of the flag
   * @param positiveLongName the name of the long positive version of the flag
   * @param negativeShortName the name of the short negative version of the flag
   * @param negativeLongName the name of the long negative version of the flag
   * @param positionalMetadataService the positional metadata service
   */
  private void emitFlagParameterInstanceFieldDeclaration(PrintWriter out,
      Character positiveShortName, String positiveLongName, Character negativeShortName,
      String negativeLongName, boolean parameterIsRequired) {
    final String fieldName = flagParameterInstanceFieldName(positiveShortName, positiveLongName,
        negativeShortName, negativeLongName);
    out.println("    /**");
    out.println("     * Flag parameter");
    if (positiveShortName != null) {
      out.println("     * positiveShortName " + positiveShortName);
    }
    if (positiveLongName != null) {
      out.println("     * positiveLongName " + positiveLongName);
    }
    if (negativeShortName != null) {
      out.println("     * negativeShortName " + negativeShortName);
    }
    if (negativeLongName != null) {
      out.println("     * negativeLongName " + negativeLongName);
    }
    out.println("     */");
    out.println("    private final List<Boolean> " + fieldName + ";");
    out.println();
  }


  /**
   * Emits the instance field declaration for a flag parameter representation.
   * 
   * @param out the PrintWriter to write to
   * @param position the position of the positional parameter
   * @param positionalMetadataService the positional metadata service
   */
  private void emitFlagParameterInstanceFieldInitPreparation(PrintWriter out,
      Collection<FlagParameterKey> set) {
    for (FlagParameterKey parameter : set) {
      final Character positiveShortName = parameter.getPositiveShortName().orElse(null);
      if (positiveShortName == null)
        continue;
      final String parameterSignature =
          flagParameterSignature(positiveShortName, parameter.getPositiveLongName().orElse(null),
              parameter.getNegativeShortName().orElse(null),
              parameter.getNegativeLongName().orElse(null));
      out.println("        flagPositiveShortNames.put(\'" + positiveShortName + "\', \"rapier.flag."
          + parameterSignature + "\");");
    }
    for (FlagParameterKey parameter : set) {
      final String positiveLongName = parameter.getPositiveLongName().orElse(null);
      if (positiveLongName == null)
        continue;
      final String parameterSignature =
          flagParameterSignature(parameter.getPositiveShortName().orElse(null), positiveLongName,
              parameter.getNegativeShortName().orElse(null),
              parameter.getNegativeLongName().orElse(null));
      out.println("        flagPositiveLongNames.put(\"" + positiveLongName + "\", \"rapier.flag."
          + parameterSignature + "\");");
    }
    for (FlagParameterKey parameter : set) {
      final Character negativeShortName = parameter.getNegativeShortName().orElse(null);
      if (negativeShortName == null)
        continue;
      final String parameterSignature =
          flagParameterSignature(parameter.getPositiveShortName().orElse(null),
              parameter.getPositiveLongName().orElse(null), negativeShortName,
              parameter.getNegativeLongName().orElse(null));
      out.println("        flagNegativeShortNames.put(\'" + negativeShortName + "\', \"rapier.flag."
          + parameterSignature + "\");");
    }
    for (FlagParameterKey parameter : set) {
      final String negativeLongName = parameter.getNegativeLongName().orElse(null);
      if (negativeLongName == null)
        continue;
      final String parameterSignature =
          flagParameterSignature(parameter.getPositiveShortName().orElse(null),
              parameter.getPositiveLongName().orElse(null),
              parameter.getNegativeShortName().orElse(null), negativeLongName);
      out.println("        flagNegativeLongNames.put(\"" + negativeLongName + "\", \"rapier.flag."
          + parameterSignature + "\");");
    }
    out.println();
  }


  /**
   * Emits the clause that initializes an option parameter field.
   * 
   * @param out the PrintWriter to write to
   * @param positiveShortName the name of the short positive version of the flag
   * @param positiveLongName the name of the long positive version of the flag
   * @param negativeShortName the name of the short negative version of the flag
   * @param negativeLongName the name of the long negative version of the flag
   * @param parameterIsRequired whether the parameter is required
   * @param parameterIsList whether the parameter is a list
   */
  private void emitFlagParameterInstanceFieldInitClause(PrintWriter out,
      Character positiveShortName, String positiveLongName, Character negativeShortName,
      String negativeLongName, boolean parameterIsRequired) {
    final String fieldName = flagParameterInstanceFieldName(positiveShortName, positiveLongName,
        negativeShortName, negativeLongName);
    final String signature = flagParameterSignature(positiveShortName, positiveLongName,
        negativeShortName, negativeLongName);
    final String userFacingString = flagParameterUserFacingString(positiveShortName,
        positiveLongName, negativeShortName, negativeLongName);

    out.println("            if(parsed.getFlags().getOrDefault(\"rapier.flag." + signature
        + "\", emptyList()).isEmpty()) {");
    if (parameterIsRequired) {
      out.println("                throw new CliSyntaxException(");
      out.println(
          "                    \"Missing required flag parameter " + userFacingString + "\");");
    } else {
      out.println("                this." + fieldName + " = null;");
    }
    out.println("            } else {");
    out.println("                this." + fieldName
        + " = unmodifiableList(parsed.getFlags().get(\"rapier.flag." + signature + "\"));");
    out.println("            }");
    out.println();
  }

  /**
   * Emits the binding methods for a positional parameter representation.
   * 
   * @param out the PrintWriter to write to
   * @param positiveShortName the name of the short positive version of the flag
   * @param positiveLongName the name of the long positive version of the flag
   * @param negativeShortName the name of the short negative version of the flag
   * @param negativeLongName the name of the long negative version of the flag
   * @param parameterIsRequired whether the parameter is required
   * @param parameterIsList whether the parameter is a list
   * @param representationType the type of the representation, e.g., String
   * @param representationDefaultValue the default value of the representation, if any
   */
  private void emitFlagParameterRepresentationBindingMethods(PrintWriter out,
      Character positiveShortName, String positiveLongName, Character negativeShortName,
      String negativeLongName, boolean parameterIsRequired, TypeMirror representationType,
      Boolean representationDefaultValue) {
    final String fieldName = flagParameterInstanceFieldName(positiveShortName, positiveLongName,
        negativeShortName, negativeLongName);
    final String signature = flagParameterSignature(positiveShortName, positiveLongName,
        negativeShortName, negativeLongName);
    final String userFacingName = flagParameterUserFacingString(positiveShortName, positiveLongName,
        negativeShortName, negativeLongName);
    final String positiveShortNameClause =
        positiveShortName != null ? "positiveShortName='" + positiveShortName + "'" : null;
    final String positiveLongNameClause =
        positiveLongName != null ? "positiveLongName=\"" + positiveLongName + "\"" : null;
    final String negativeShortNameClause =
        negativeShortName != null ? "negativeShortName='" + negativeShortName + "'" : null;
    final String negativeLongNameClause =
        negativeLongName != null ? "negativeLongName=\"" + negativeLongName + "\"" : null;
    final String nameClauses =
        Stream.of(positiveShortNameClause, positiveLongNameClause, negativeShortNameClause,
            negativeLongNameClause).filter(Objects::nonNull).collect(Collectors.joining(", "));

    final String listOfBooleanConversionExpr = getListOfBooleanConverter()
        .generateConversionExpr(representationType, "value").orElse(null);
    final String booleanConversionExpr =
        getBooleanConverter().generateConversionExpr(representationType, "value").orElse(null);
    if (listOfBooleanConversionExpr == null && booleanConversionExpr == null) {
      // The user should provide their own conversion
      getMessager().printMessage(Diagnostic.Kind.ERROR,
          "No conversion available for " + userFacingName + " to " + representationType);
      // TODO Print site
      return;
    }
    if (listOfBooleanConversionExpr != null && booleanConversionExpr != null) {
      // The user should provide their own conversion
      getMessager().printMessage(Diagnostic.Kind.ERROR,
          "Multiple conversions available for " + userFacingName + " to " + representationType);
      // TODO Print site
      return;
    }

    final boolean representationIsList = listOfBooleanConversionExpr != null;


    final StringBuilder baseMethodName =
        new StringBuilder().append("provideFlag").append(signature);
    if (representationDefaultValue != null) {
      baseMethodName.append("WithDefaultValue")
          .append(representationDefaultValue.booleanValue() ? "True" : "False");
    }

    final String annotationDefaultValueExpr = "CliFlagParameterValue." + representationDefaultValue;

    final String javaDefaultValueExpr = "Boolean." + representationDefaultValue;

    final String representationAnnotation;
    if (representationDefaultValue != null) {
      representationAnnotation =
          "@CliFlagParameter(" + nameClauses + ", defaultValue=" + annotationDefaultValueExpr + ")";
    } else {
      representationAnnotation = "@CliFlagParameter(" + nameClauses + ")";
    }

    if (representationIsList) {
      if (getTypes().isSameType(representationType, getListOfBooleanType())) {
        out.println("    @Provides");
        out.println("    " + representationAnnotation);
        out.println("    public List<Boolean> " + baseMethodName + "AsListOfBoolean() {");
        if (representationDefaultValue != null) {
          out.println("        if(" + fieldName + " == null)");
          out.println("            return singletonList(" + javaDefaultValueExpr + ");");
        } else if (parameterIsRequired == false) {
          // This code looks weird because the logic is not entirely localized. Additional logic is
          // included in the constructor. If a required parameter is not set, the constructor will
          // throw an exception. If an optional parameter is not set, the constructor will set the
          // field to null. This is why we check for null here.
          out.println("        if(" + fieldName + " == null)");
          out.println("            return emptyList();");
        }
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();

        if (representationDefaultValue == null && parameterIsRequired == false) {
          out.println("    @Provides");
          out.println("    " + representationAnnotation);
          out.println("    public Optional<List<String>> " + baseMethodName
              + "AsOptionalOfListOfString(" + representationAnnotation + " List<String> value) {");
          out.println("        return Optional.of(value);");
          out.println("    }");
          out.println();
        }
      } else {
        final String typeSimpleName = getSimpleTypeName(representationType);
        final String conversionExpr = listOfBooleanConversionExpr;

        out.println("    @Provides");
        out.println("    " + representationAnnotation);
        out.println("    public " + representationType + " " + baseMethodName + "As"
            + typeSimpleName + "(" + representationAnnotation + " List<Boolean> value) {");
        out.println("        " + representationType + " result;");
        out.println("        try {");
        out.println("            result = " + conversionExpr + ";");
        out.println("        } catch (Exception e) {");
        out.println("            throw new IllegalArgumentException(");
        out.println("                \"Option parameter " + userFacingName + " representation "
            + representationType + " argument not valid\", e);");
        out.println("        }");
        out.println("        if(result == null) {");
        out.println("            throw new IllegalStateException(");
        out.println("                \"Option parameter " + userFacingName + " representation "
            + representationType + " not set\");");
        out.println("        }");
        out.println("        return result;");
        out.println("    }");
        out.println();

        if (representationDefaultValue == null && parameterIsRequired == false) {
          out.println("    @Provides");
          out.println("    " + representationAnnotation);
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(" + representationAnnotation + " "
              + representationType + " value) {");
          out.println("        return Optional.of(value);");
          out.println("    }");
          out.println();
        }
      }
    } else {
      final boolean representationIsNullable =
          representationDefaultValue == null && !parameterIsRequired;

      final String nullableAnnotation = representationIsNullable ? "@Nullable" : "";

      if (getTypes().isSameType(representationType, getBooleanType())) {
        out.println("    " + nullableAnnotation);
        out.println("    @Provides");
        out.println("    " + representationAnnotation);
        out.println("    public Boolean " + baseMethodName + "AsBoolean() {");
        if (representationDefaultValue != null) {
          out.println("        if(" + fieldName + " == null) {");
          out.println(
              "            return " + javaDefaultValueExpr + ";");
          out.println("        }");
        } else if (parameterIsRequired) {
          // No special action required here. It is handled in the constructor.
        }
        out.println("        return " + fieldName + ".get(" + fieldName + ".size()-1);");
        out.println("    }");
        out.println();

        if (representationIsNullable) {
          out.println("    @Provides");
          out.println("    " + representationAnnotation);
          out.println("    public Optional<Boolean> " + baseMethodName + "AsOptionalOfBoolean("
              + representationAnnotation + " String value) {");
          out.println("        return Optional.ofNullable(value);");
          out.println("    }");
          out.println();
        }
      } else {
        final String typeSimpleName = getSimpleTypeName(representationType);
        final String conversionExpr = booleanConversionExpr;

        out.println("    " + nullableAnnotation);
        out.println("    @Provides");
        out.println("    " + representationAnnotation);
        out.println("    public " + representationType + " " + baseMethodName + "As"
            + typeSimpleName + "(" + representationAnnotation + " Boolean value) {");
        out.println("        if(value == null)");
        out.println("            return null;");
        out.println("        " + representationType + " result;");
        out.println("        try {");
        out.println("            result = " + conversionExpr + ";");
        out.println("        } catch (Exception e) {");
        out.println("            throw new IllegalArgumentException(");
        out.println("                \"Flag parameter " + userFacingName + " representation "
            + representationType + " argument not valid\", e);");
        out.println("        }");
        if (representationIsNullable == false) {
          out.println("        if(result == null) {");
          out.println("            throw new IllegalStateException(");
          out.println("                \"Flag parameter " + userFacingName + " representation "
              + representationType + " not set\");");
          out.println("        }");
        }
        out.println("        return result;");
        out.println("    }");
        out.println();

        if (representationIsNullable == true) {
          out.println("    " + nullableAnnotation);
          out.println("    @Provides");
          out.println("    " + representationAnnotation);
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(" + representationAnnotation + " "
              + representationType + " value) {");
          out.println("        return Optional.ofNullable(value);");
          out.println("    }");
          out.println();
        }
      }
    }
  }

  private String flagParameterInstanceFieldName(Character positiveShortName,
      String positiveLongName, Character negativeShortName, String negativeLongName) {
    return "flag" + flagParameterSignature(positiveShortName, positiveLongName, negativeShortName,
        negativeLongName);
  }

  private String flagParameterUserFacingString(Character positiveShortName, String positiveLongName,
      Character negativeShortName, String negativeLongName) {
    List<String> parts = new ArrayList<>(4);
    if (positiveShortName != null)
      parts.add("-" + positiveShortName);
    if (positiveLongName != null)
      parts.add("--" + positiveLongName);
    if (negativeShortName != null)
      parts.add("-" + negativeShortName);
    if (negativeLongName != null)
      parts.add("--" + negativeLongName);
    if (parts.isEmpty())
      throw new IllegalArgumentException(
          "positiveShortName, positiveLongName, negativeShortName, negativeLongName cannot all be null");
    return String.join(" / ", parts);
  }

  private String flagParameterSignature(Character positiveShortName, String positiveLongName,
      Character negativeShortName, String negativeLongName) {
    final String[] parts = new String[4];
    parts[0] = positiveShortName == null ? "" : positiveShortName.toString();
    parts[1] = positiveLongName == null ? "" : positiveLongName;
    parts[2] = negativeShortName == null ? "" : negativeShortName.toString();
    parts[3] = negativeLongName == null ? "" : negativeLongName;
    return stringSignature(String.join("/", parts));
  }

  private FlagRepresentationKey toBooleanRepresentation(FlagRepresentationKey key) {
    return new FlagRepresentationKey(getBooleanType(), key.getPositiveShortName().orElse(null),
        key.getPositiveLongName().orElse(null), key.getNegativeShortName().orElse(null),
        key.getNegativeLongName().orElse(null), key.getDefaultValue().orElse(null));
  }

  private FlagRepresentationKey toListOfBooleanRepresentation(FlagRepresentationKey key) {
    return new FlagRepresentationKey(getListOfBooleanType(),
        key.getPositiveShortName().orElse(null), key.getPositiveLongName().orElse(null),
        key.getNegativeShortName().orElse(null), key.getNegativeLongName().orElse(null),
        key.getDefaultValue().orElse(null));
  }


  //////////////////////////////////////////////////////////////////////////////////////////////////
  // HELP MESSAGE //////////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////////////
  private static final int HELP_MESSAGE_MAX_WIDTH = 80;

  private static final int HELP_MAX_SAME_LINE_NAME_WIDTH = 16;

  private static final int HELP_DESCRIPTION_WIDTH = 60;


  private void emitStandardHelpMessageMethod(PrintWriter out, CommandMetadata commandMetadata,
      SortedMap<PositionalParameterKey, Collection<PositionalRepresentationKey>> positionalRepresentationsByParameter,
      PositionalParameterMetadataService positionalMetadataService,
      SortedMap<OptionParameterKey, Collection<OptionRepresentationKey>> optionRepresentationsByParameter,
      OptionParameterMetadataService optionMetadataService,
      SortedMap<FlagParameterKey, Collection<FlagRepresentationKey>> flagRepresentationsByParameter,
      FlagParameterMetadataService flagMetadataService) {
    assert commandMetadata.isProvideStandardHelp();

    final SortedSet<PositionalParameterKey> positionalParameters =
        new TreeSet<PositionalParameterKey>(POSITIONAL_PARAMETER_KEY_COMPARATOR);
    positionalParameters.addAll(positionalRepresentationsByParameter.keySet());

    final SortedSet<OptionParameterKey> optionParameters =
        new TreeSet<OptionParameterKey>(OPTION_PARAMETER_KEY_COMPARATOR);
    optionParameters.addAll(optionRepresentationsByParameter.keySet());

    final SortedSet<FlagParameterKey> flagParameters =
        new TreeSet<FlagParameterKey>(FLAG_PARAMETER_KEY_COMPARATOR);
    flagParameters.addAll(flagRepresentationsByParameter.keySet());
    if (commandMetadata.isProvideStandardHelp()) {
      final FlagParameterKey standardHelpFlagParameterKey =
          new FlagParameterKey(STANDARD_HELP_SHORT_NAME, STANDARD_HELP_LONG_NAME, null, null);
      flagParameters.add(standardHelpFlagParameterKey);
      final FlagParameterMetadataService flagMetadataService0 = flagMetadataService;
      flagMetadataService = new FlagParameterMetadataService() {
        @Override
        public FlagParameterMetadata getFlagParameterMetadata(Character shortPositiveName,
            String positiveLongName, Character negativeShortName, String negativeLongName) {
          final FlagParameterKey key = new FlagParameterKey(shortPositiveName, positiveLongName,
              negativeShortName, negativeLongName);
          if (key.equals(standardHelpFlagParameterKey))
            return new FlagParameterMetadata(false, "Print this help message and exit");
          return flagMetadataService0.getFlagParameterMetadata(shortPositiveName, positiveLongName,
              negativeShortName, negativeLongName);
        }
      };
    }
    if (commandMetadata.isProvideStandardVersion()) {
      final FlagParameterKey standardHelpFlagParameterKey =
          new FlagParameterKey(STANDARD_VERSION_SHORT_NAME, STANDARD_VERSION_LONG_NAME, null, null);
      flagParameters.add(standardHelpFlagParameterKey);
      final FlagParameterMetadataService flagMetadataService0 = flagMetadataService;
      flagMetadataService = new FlagParameterMetadataService() {
        @Override
        public FlagParameterMetadata getFlagParameterMetadata(Character shortPositiveName,
            String positiveLongName, Character negativeShortName, String negativeLongName) {
          final FlagParameterKey key = new FlagParameterKey(shortPositiveName, positiveLongName,
              negativeShortName, negativeLongName);
          if (key.equals(standardHelpFlagParameterKey))
            return new FlagParameterMetadata(false, "Print a version message and exit");
          return flagMetadataService0.getFlagParameterMetadata(shortPositiveName, positiveLongName,
              negativeShortName, negativeLongName);
        }
      };
    }

    out.println("    public String standardHelpMessage() {");
    out.println("        return String.join(\"\\n\", ");

    final String commandName = commandMetadata.getName();
    final List<String> positionalNames =
        positionalRepresentationsByParameter.keySet().stream()
            .sorted(Comparator.comparingInt(PositionalParameterKey::getPosition))
            .map(p -> Map.entry(p,
                positionalMetadataService.getPositionalParameterMetadata(p.getPosition())))
            .map(e -> {
              final String name = e.getValue().getHelpName();
              final boolean parameterIsRequired = e.getValue().isRequired();
              final boolean parameterIsList = e.getValue().isList();
              if (parameterIsRequired) {
                return "<" + name + ">";
              } else if (parameterIsList) {
                return "[" + name + "...]";
              } else {
                return "[" + name + "]";
              }
            }).collect(toList());

    final boolean hasOptions = !optionRepresentationsByParameter.isEmpty();
    final boolean hasFlags = !flagRepresentationsByParameter.isEmpty();

    final String optionsAndFlags;
    if (hasOptions && hasFlags) {
      optionsAndFlags = "[OPTIONS | FLAGS] ";
    } else if (hasOptions) {
      optionsAndFlags = "[OPTIONS] ";
    } else if (hasFlags) {
      optionsAndFlags = "[FLAGS] ";
    } else {
      optionsAndFlags = "";
    }

    // @formatter:off
    out.println("            \"Usage: " + commandName + " " + optionsAndFlags + String.join(" ", positionalNames) + "\",");
    out.println("            \"\",");
    if (commandMetadata.getDescription().isPresent()) {
      for(String line : wordwrap("Description: " + commandMetadata.getDescription().orElseThrow(), HELP_MESSAGE_MAX_WIDTH))
        out.println("            \"" + Java.escapeString(line) + "\",");
      out.println("            \"\",");
    }
    // @formatter:on

    if (positionalParameters.stream().anyMatch(p -> positionalMetadataService
        .getPositionalParameterMetadata(p.getPosition()).getHelpDescription().isPresent())) {
      out.println("            \"Positional parameters:\",");
      for (PositionalParameterKey parameter : positionalParameters) {
        final PositionalParameterMetadata metadata =
            positionalMetadataService.getPositionalParameterMetadata(parameter.getPosition());
        final String name = metadata.getHelpName();
        final String description = metadata.getHelpDescription().orElse("");
        emitNameAndDescription(out, name, description);
      }

      out.println("            \"\",");
    }

    if (!optionParameters.isEmpty()) {
      out.println("            \"Option parameters:\",");
      for (OptionParameterKey parameter : optionParameters) {
        final OptionParameterMetadata metadata = optionMetadataService.getOptionParameterMetadata(
            parameter.getShortName().orElse(null), parameter.getLongName().orElse(null));
        final String name = optionParameterHelpFacingString(parameter.getShortName().orElse(null),
            parameter.getLongName().orElse(null), metadata.getHelpValueName());
        final String description = metadata.getHelpDescription().orElse("");
        emitNameAndDescription(out, name, description);
      }

      out.println("            \"\",");
    }

    if (!flagRepresentationsByParameter.isEmpty()) {
      out.println("            \"Flag parameters:\",");
      for (FlagParameterKey parameter : flagParameters) {
        final FlagParameterMetadata metadata = flagMetadataService.getFlagParameterMetadata(
            parameter.getPositiveShortName().orElse(null),
            parameter.getPositiveLongName().orElse(null),
            parameter.getNegativeShortName().orElse(null),
            parameter.getNegativeLongName().orElse(null));

        final String name =
            flagParameterHelpFacingString(parameter.getPositiveShortName().orElse(null),
                parameter.getPositiveLongName().orElse(null),
                parameter.getNegativeShortName().orElse(null),
                parameter.getNegativeLongName().orElse(null));
        final String description = metadata.getHelpDescription().orElse("");
        emitNameAndDescription(out, name, description);
      }

      out.println("            \"\",");
    }

    out.println("            \"\");");
    out.println("    }");
    out.println();
  }

  private static String optionParameterHelpFacingString(Character shortName, String longName,
      String valueName) {
    if (shortName == null && longName == null)
      throw new IllegalArgumentException("shortName and longName cannot both be null");
    List<String> result = new ArrayList<>(2);
    if (shortName != null)
      result.add("-" + shortName + " <" + valueName + ">");
    if (longName != null)
      result.add("--" + longName + " <" + valueName + ">");
    return String.join(", ", result);
  }

  private static String flagParameterHelpFacingString(Character positiveShortName,
      String positiveLongName, Character negativeShortName, String negativeLongName) {
    final List<String> result = new ArrayList<>(4);
    if (positiveShortName != null)
      result.add("-" + positiveShortName);
    if (positiveLongName != null)
      result.add("--" + positiveLongName);
    if (negativeShortName != null)
      result.add("-" + negativeShortName);
    if (negativeLongName != null)
      result.add("--" + negativeLongName);
    return String.join(", ", result);
  }

  private static void emitNameAndDescription(PrintWriter out, String name, String description) {
    final List<String> descriptionLines = wordwrap(description, HELP_DESCRIPTION_WIDTH);
    if (descriptionLines.isEmpty()) {
      // @formatter:off
      out.println("            \"" + Java.escapeString(String.format(
          new StringBuilder()
              .append(repeat(" ", 2))
              .append("%").append("-" + HELP_MAX_SAME_LINE_NAME_WIDTH).append("s")
              .toString(),
          name)) + "\",");
      // @formatter:on
    } else {
      if (name.length() <= HELP_MAX_SAME_LINE_NAME_WIDTH) {
        // @formatter:off
        out.println("            \"" + Java.escapeString(String.format(
            new StringBuilder()
                .append(repeat(" ", 2))
                .append("%").append("-" + HELP_MAX_SAME_LINE_NAME_WIDTH).append("s")
                .append(repeat(" ", 2))
                .append("%").append("s")
                .toString(),
            name, 
            descriptionLines.get(0))) + "\",");
        // @formatter:on
      } else {
        // @formatter:off
        out.println("            \"" + Java.escapeString(String.format(
            new StringBuilder()
                .append(repeat(" ", 2))
                .append("%").append("s")
                .toString(),
            name)) + "\",");
        out.println("            \"" + Java.escapeString(String.format(
            new StringBuilder()
                .append(repeat(" ", 2 + HELP_MAX_SAME_LINE_NAME_WIDTH + 2))
                .append("%").append("s")
                .toString(),
            descriptionLines.get(0))) + "\",");
        // @formatter:on
      }
      for (int i = 1; i < descriptionLines.size(); i++) {
        // @formatter:off
        out.println("            \"" + Java.escapeString(String.format(
            new StringBuilder()
                .append(repeat(" ", 2 + HELP_MAX_SAME_LINE_NAME_WIDTH + 2))
                .append("%").append("s")
                .toString(),
            descriptionLines.get(i))) + "\",");
        // @formatter:on
      }
    }
  }

  private static String repeat(String s, int repetitions) {
    if (s == null)
      throw new NullPointerException();
    if (repetitions < 0)
      throw new IllegalArgumentException("repetition must be non-negative");
    if (repetitions == 0)
      return "";
    if (s == "")
      return s;
    if (repetitions == 1)
      return s;
    final StringBuilder result = new StringBuilder(s.length() * repetitions);
    for (int i = 0; i < repetitions; i++)
      result.append(s);
    return result.toString();
  }

  private static List<String> wordwrap(String input, int maxLength) {
    if (input == null)
      throw new NullPointerException();
    if (maxLength < 1)
      throw new IllegalArgumentException("maxLength must be positive");

    input = input.trim();
    if (input.isEmpty())
      return List.of();

    final List<String> result = new ArrayList<>();

    final String[] words = input.split("\\s+");
    final StringBuilder currentLine = new StringBuilder();
    for (String word : words) {
      if (currentLine.length() + word.length() <= maxLength) {
        if (currentLine.length() > 0) {
          currentLine.append(" ");
        }
        currentLine.append(word);
      } else {
        if (currentLine.length() > 0) {
          result.add(currentLine.toString());
        }
        // Start a new line with the current word
        currentLine.setLength(0);
        currentLine.append(word);
      }
    }

    // Add the last line if it contains any text
    if (currentLine.length() > 0) {
      result.add(currentLine.toString());
    }

    return result;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // VERSION MESSAGE ///////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////////////
  private void emitStandardVersionMessageMethod(PrintWriter out, CommandMetadata metadata) {
    assert metadata.isProvideStandardVersion();

    final String versionString = metadata.getName() + " version " + metadata.getVersion();

    out.println("    public String standardVersionMessage() {");
    out.println("        return \"" + Java.escapeString(versionString) + "\";");
    out.println("    }");
    out.println();
  }


  //////////////////////////////////////////////////////////////////////////////////////////////////
  // OTHER /////////////////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////////////
  private ConversionExprFactory getStringConverter() {
    return stringConverter;
  }

  private ConversionExprFactory getListOfStringConverter() {
    return listOfStringConverter;
  }

  private ConversionExprFactory getBooleanConverter() {
    return booleanConverter;
  }

  private ConversionExprFactory getListOfBooleanConverter() {
    return listOfBooleanConverter;
  }

  private transient TypeMirror booleanType;

  private TypeMirror getBooleanType() {
    if (booleanType == null) {
      booleanType = getElements().getTypeElement("java.lang.Boolean").asType();
    }
    return booleanType;
  }

  private transient TypeMirror listOfStringType;

  private TypeMirror getListOfStringType() {
    if (listOfStringType == null) {
      listOfStringType = getTypes().getDeclaredType(getElements().getTypeElement("java.util.List"),
          getStringType());
    }
    return listOfStringType;
  }

  private transient TypeMirror listOfBooleanType;

  private TypeMirror getListOfBooleanType() {
    if (listOfBooleanType == null) {
      listOfBooleanType = getTypes().getDeclaredType(getElements().getTypeElement("java.util.List"),
          getBooleanType());
    }
    return listOfBooleanType;
  }
}
