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

import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
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
import rapier.core.ConversionExprFactory;
import rapier.core.DaggerComponentAnalyzer;
import rapier.core.RapierProcessorBase;
import rapier.core.conversion.expr.ConversionExprFactoryChain;
import rapier.core.conversion.expr.ElementwiseListConversionExprFactory;
import rapier.core.conversion.expr.FromStringConversionExprFactory;
import rapier.core.conversion.expr.SingleArgumentConstructorConversionExprFactory;
import rapier.core.conversion.expr.ValueOfConversionExprFactory;
import rapier.core.model.DaggerInjectionSite;
import rapier.core.util.Java;
import rapier.processor.cli.model.BindingMetadata;
import rapier.processor.cli.model.FlagParameterKey;
import rapier.processor.cli.model.FlagRepresentationKey;
import rapier.processor.cli.model.OptionParameterKey;
import rapier.processor.cli.model.OptionRepresentationKey;
import rapier.processor.cli.model.PositionalParameterKey;
import rapier.processor.cli.model.PositionalRepresentationKey;
import rapier.processor.cli.util.CliProcessing;

@SupportedAnnotationTypes("dagger.Component")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class CliProcessor extends RapierProcessorBase {
  private ConversionExprFactory stringConverter;
  private ConversionExprFactory listOfStringConverter;
  private ConversionExprFactory booleanConverter;
  private ConversionExprFactory listOfBooleanConverter;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    stringConverter = new ConversionExprFactoryChain(
        new ValueOfConversionExprFactory(getTypes(), getStringType()),
        new FromStringConversionExprFactory(getTypes()),
        new SingleArgumentConstructorConversionExprFactory(getTypes(), getStringType()));

    listOfStringConverter = new ConversionExprFactoryChain(
        new ValueOfConversionExprFactory(getTypes(), getListOfStringType()),
        new SingleArgumentConstructorConversionExprFactory(getTypes(), getListOfStringType()),
        new ElementwiseListConversionExprFactory(getTypes(), stringConverter));

    booleanConverter = new ConversionExprFactoryChain(
        new ValueOfConversionExprFactory(getTypes(), getBooleanType()),
        new SingleArgumentConstructorConversionExprFactory(getTypes(), getBooleanType()));

    listOfBooleanConverter = new ConversionExprFactoryChain(
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

    final String componentPackageName =
        getElements().getPackageOf(component).getQualifiedName().toString();
    final String componentClassName = component.getSimpleName().toString();
    final String moduleClassName = "Rapier" + componentClassName + "EnvironmentVariableModule";

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
      return;
    }

    boolean valid =
        validateInjectionSites(component, positionalInjectionSites, positionalMetadataService,
            optionInjectionSites, optionMetadataService, flagInjectionSites, flagMetadataService);

    if (valid == false)
      return;

    final String moduleSource = generateModuleSource(componentPackageName, moduleClassName,
        positionalInjectionSites, positionalMetadataService, optionInjectionSites,
        optionMetadataService, flagInjectionSites, flagMetadataService);

    try {
      // TODO Is this the right set of elements?
      final Element[] dependentElements = new Element[] {component};
      final JavaFileObject o =
          getFiler().createSourceFile(componentPackageName.equals("") ? moduleClassName
              : componentPackageName + "." + moduleClassName, dependentElements);
      try (final Writer writer = o.openWriter()) {
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

  private boolean validatePositionalInjectionSites(
      SortedMap<PositionalParameterKey, List<DaggerInjectionSite>> positionalInjecionSites,
      PositionalParameterMetadataService positionalMetadataService) {
    boolean valid = true;

    boolean seenOptional = false;
    boolean seenList = false;
    final int size = positionalInjecionSites.size();
    for (int position = 0; position < size; position++) {
      final List<DaggerInjectionSite> positionInjectionSites =
          positionalInjecionSites.get(PositionalParameterKey.forPosition(position));
      if (positionInjectionSites == null) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Missing required positional parameter: " + position);
        valid = false;
        continue;
      }

      final BindingMetadata metadata =
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
      if (seenList && parameterIsList) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Positional parameter " + position + " is a list following another list");
        // TODO Print example list
        valid = false;
      } else if (!seenList && parameterIsList) {
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
            getElements().getTypeElement(PositionalCliParameter.class.getCanonicalName()).asType()))
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
    final Map<PositionalParameterKey, BindingMetadata> metadataByPositionalParameter =
        new HashMap<>();
    for (Map.Entry<PositionalParameterKey, List<DaggerInjectionSite>> entry : positionalInjectionSites
        .entrySet()) {
      final PositionalParameterKey pk = entry.getKey();
      final List<DaggerInjectionSite> injectionSites = entry.getValue();
      final List<Map.Entry<PositionalRepresentationKey, DaggerInjectionSite>> rkes = injectionSites
          .stream().map(dis -> Map.entry(PositionalRepresentationKey.fromInjectionSite(dis), dis))
          .collect(toList());

      final Map<Boolean, List<DaggerInjectionSite>> injectionSitesByRequired = rkes.stream()
          .collect(Collectors.groupingBy(
              prke -> CliProcessing.isRequired(prke.getValue().isNullable(),
                  prke.getKey().getDefaultValue().orElse(null)),
              mapping(Map.Entry::getValue, toList())));

      if (injectionSitesByRequired.size() > 1) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Conflicting requiredness for positional parameter " + pk.getPosition());
        // TODO Print example required
        valid = false;
        continue;
      }

      final boolean required = injectionSitesByRequired.keySet().stream().anyMatch(b -> b == true);

      final boolean stringy = rkes.stream().allMatch(prke -> getStringConverter()
          .generateConversionExpr(prke.getValue().getProvisionedType(), "_").isPresent());
      final boolean listy = rkes.stream().allMatch(prke -> getListOfStringConverter()
          .generateConversionExpr(prke.getValue().getProvisionedType(), "_").isPresent());
      if (stringy && listy) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Ambiguous positional parameter " + pk.getPosition());
        // TODO Print example ambiguous
        valid = false;
        continue;
      }
      if (!stringy && !listy) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "No conversion for positional parameter " + pk.getPosition());
        // TODO Print example no conversion
        valid = false;
        continue;
      }

      metadataByPositionalParameter.put(pk, new BindingMetadata(required, listy));
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
            getElements().getTypeElement(OptionCliParameter.class.getCanonicalName()).asType()))
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
    final Map<OptionParameterKey, BindingMetadata> metadataByOptionParameter = new HashMap<>();
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

      final boolean stringy = rkes.stream().allMatch(prke -> getStringConverter()
          .generateConversionExpr(prke.getValue().getProvisionedType(), "_").isPresent());
      final boolean listy = rkes.stream().allMatch(prke -> getListOfStringConverter()
          .generateConversionExpr(prke.getValue().getProvisionedType(), "_").isPresent());
      if (stringy && listy) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Ambiguous option parameter " + optionParameterUserFacingString(
                pk.getShortName().orElse(null), pk.getLongName().orElse(null)));
        // TODO Print example ambiguous
        valid = false;
        continue;
      }
      if (!stringy && !listy) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "No conversion for positional parameter " + optionParameterUserFacingString(
                pk.getShortName().orElse(null), pk.getLongName().orElse(null)));
        // TODO Print example no conversion
        valid = false;
        continue;
      }

      metadataByOptionParameter.put(pk, new BindingMetadata(required, listy));
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
            getElements().getTypeElement(FlagCliParameter.class.getCanonicalName()).asType()))
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
    final Map<FlagParameterKey, BindingMetadata> metadataByFlagParameter = new HashMap<>();
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

      final boolean stringy = rkes.stream().allMatch(prke -> getStringConverter()
          .generateConversionExpr(prke.getValue().getProvisionedType(), "_").isPresent());
      final boolean listy = rkes.stream().allMatch(prke -> getListOfStringConverter()
          .generateConversionExpr(prke.getValue().getProvisionedType(), "_").isPresent());
      if (stringy && listy) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Ambiguous flag parameter " + flagParameterUserFacingString(
                pk.getPositiveShortName().orElse(null), pk.getPositiveLongName().orElse(null),
                pk.getNegativeShortName().orElse(null), pk.getNegativeLongName().orElse(null)));
        // TODO Print example ambiguous
        valid = false;
        continue;
      }
      if (!stringy && !listy) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "No conversion for positional parameter " + flagParameterUserFacingString(
                pk.getPositiveShortName().orElse(null), pk.getPositiveLongName().orElse(null),
                pk.getNegativeShortName().orElse(null), pk.getNegativeLongName().orElse(null)));
        // TODO Print example no conversion
        valid = false;
        continue;
      }

      metadataByFlagParameter.put(pk, new BindingMetadata(required, listy));
    }

    if (valid == false)
      return null;

    return (positiveShortName, positiveLongName, negativeShortName,
        negativeLongName) -> metadataByFlagParameter.get(new FlagParameterKey(positiveShortName,
            positiveLongName, negativeShortName, negativeLongName));
  }

  private String generateModuleSource(String packageName, String moduleClassName,
      SortedMap<PositionalParameterKey, List<DaggerInjectionSite>> positionalInjectionSites,
      PositionalParameterMetadataService positionalMetadataService,
      SortedMap<OptionParameterKey, List<DaggerInjectionSite>> optionInjectionSites,
      OptionParameterMetadataService optionMetadataService,
      SortedMap<FlagParameterKey, List<DaggerInjectionSite>> flagInjectionSites,
      FlagParameterMetadataService flagMetadataService) {

    final SortedMap<PositionalParameterKey, List<PositionalRepresentationKey>> positionalRepresentationsByParameter =
        positionalInjectionSites.entrySet().stream()
            .flatMap(e -> e.getValue().stream()
                .map(dis -> Map.entry(PositionalParameterKey.fromInjectionSite(dis),
                    PositionalRepresentationKey.fromInjectionSite(dis))))
            .collect(groupingBy(Map.Entry::getKey,
                () -> new TreeMap<>(POSITIONAL_PARAMETER_KEY_COMPARATOR),
                mapping(Map.Entry::getValue, toList())));

    final SortedMap<OptionParameterKey, List<OptionRepresentationKey>> optionRepresentationsByParameter =
        optionInjectionSites.entrySet().stream()
            .flatMap(e -> e.getValue().stream()
                .map(dis -> Map.entry(e.getKey(), OptionRepresentationKey.fromInjectionSite(dis))))
            .collect(
                groupingBy(Map.Entry::getKey, () -> new TreeMap<>(OPTION_PARAMETER_KEY_COMPARATOR),
                    mapping(Map.Entry::getValue, toList())));

    final SortedMap<FlagParameterKey, List<FlagRepresentationKey>> flagRepresentationsByParameter =
        flagInjectionSites.entrySet().stream()
            .flatMap(e -> e.getValue().stream()
                .map(dis -> Map.entry(e.getKey(), FlagRepresentationKey.fromInjectionSite(dis))))
            .collect(
                groupingBy(Map.Entry::getKey, () -> new TreeMap<>(FLAG_PARAMETER_KEY_COMPARATOR),
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
      out.println("import java.util.HashMap;");
      out.println("import java.util.List;");
      out.println("import java.util.Map;");
      out.println("import java.util.Optional;");
      out.println("import javax.annotation.Nullable;");
      out.println("import rapier.processor.cli.FlagCliParameter;");
      out.println("import rapier.processor.cli.OptionCliParameter;");
      out.println("import rapier.processor.cli.PositionalCliParameter;");
      out.println("import rapier.processor.cli.thirdparty.com.sigpwned.just.args.JustArgs;");
      out.println();
      out.println("@Module");
      out.println("public class " + moduleClassName + " {");
      out.println();

      // Generate instance fields for each positional parameter representation
      out.println("    // Positional parameters");
      for (PositionalParameterKey ppk : positionalRepresentationsByParameter.keySet()) {
        final int position = ppk.getPosition();
        final BindingMetadata metadata =
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
        final BindingMetadata metadata =
            optionMetadataService.getPositionalParameterMetadata(optionShortName, optionLongName);
        final boolean parameterIsRequired = metadata.isRequired();
        final boolean parameterIsList = metadata.isList();
        emitOptionParameterInstanceFieldDeclaration(out, optionShortName, optionLongName,
            parameterIsRequired, parameterIsList);
      }
      out.println();

      // Generate instance fields for each flag parameter representation
      out.println("    // Flag parameters");
      for (FlagParameterKey fpk : flagRepresentationsByParameter.keySet()) {
        final Character flagShortPositiveName = fpk.getPositiveShortName().orElse(null);
        final String flagpositiveLongName = fpk.getPositiveLongName().orElse(null);
        final Character flagnegativeShortName = fpk.getNegativeShortName().orElse(null);
        final String flagnegativeLongName = fpk.getNegativeLongName().orElse(null);
        final BindingMetadata metadata =
            flagMetadataService.getPositionalParameterMetadata(flagShortPositiveName,
                flagpositiveLongName, flagnegativeShortName, flagnegativeLongName);
        final boolean parameterIsRequired = metadata.isRequired();
        final boolean parameterIsList = metadata.isList();
        emitFlagParameterInstanceFieldDeclaration(out, flagShortPositiveName, flagpositiveLongName,
            flagnegativeShortName, flagnegativeLongName, parameterIsRequired, parameterIsList);
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
      out.println("        final Map<Character, String> flagShortPositiveNames = new HashMap<>();");
      out.println("        final Map<String, String> flagpositiveLongNames = new HashMap<>();");
      out.println("        final Map<Character, String> flagnegativeShortNames = new HashMap<>();");
      out.println("        final Map<String, String> flagnegativeLongNames = new HashMap<>();");
      emitFlagParameterInstanceFieldInitPreparation(out, flagRepresentationsByParameter.keySet());
      out.println();

      out.println("        // Parse the arguments");
      out.println("        final JustArgs.ParsedArgs parsed = JustArgs.parseArgs(");
      out.println("            args,");
      out.println("            optionShortNames,");
      out.println("            optionLongNames,");
      out.println("            flagShortPositiveNames,");
      out.println("            flagpositiveLongNames,");
      out.println("            flagnegativeShortNames,");
      out.println("            flagnegativeLongNames);");
      out.println();

      // Generate the initialization of each positional parameter representation
      out.println("        // Initialize positional parameters");
      for (PositionalParameterKey ppk : positionalRepresentationsByParameter.keySet()) {
        final int position = ppk.getPosition();
        final BindingMetadata metadata =
            positionalMetadataService.getPositionalParameterMetadata(position);
        final boolean parameterIsRequired = metadata.isRequired();
        final boolean parameterIsList = metadata.isList();
        emitPositionalParameterInstanceFieldInitClause(out, position, parameterIsRequired,
            parameterIsList);
      }
      out.println();

      out.println("        // Initialize option parameters");
      for (OptionParameterKey opk : optionRepresentationsByParameter.keySet()) {
        final Character optionShortName = opk.getShortName().orElse(null);
        final String optionLongName = opk.getLongName().orElse(null);
        final BindingMetadata metadata =
            optionMetadataService.getPositionalParameterMetadata(optionShortName, optionLongName);
        final boolean parameterIsRequired = metadata.isRequired();
        final boolean parameterIsList = metadata.isList();
        emitOptionParameterInstanceFieldInitClause(out, optionShortName, optionLongName,
            parameterIsRequired, parameterIsList);
      }
      out.println();

      out.println("        // Initialize flag parameters");
      for (FlagParameterKey fpk : flagRepresentationsByParameter.keySet()) {
        final Character flagShortPositiveName = fpk.getPositiveShortName().orElse(null);
        final String flagpositiveLongName = fpk.getPositiveLongName().orElse(null);
        final Character flagnegativeShortName = fpk.getNegativeShortName().orElse(null);
        final String flagnegativeLongName = fpk.getNegativeLongName().orElse(null);
        final BindingMetadata metadata =
            flagMetadataService.getPositionalParameterMetadata(flagShortPositiveName,
                flagpositiveLongName, flagnegativeShortName, flagnegativeLongName);
        final boolean parameterIsRequired = metadata.isRequired();
        final boolean parameterIsList = metadata.isList();
        emitFlagParameterInstanceFieldInitClause(out, flagShortPositiveName, flagpositiveLongName,
            flagnegativeShortName, flagnegativeLongName, parameterIsRequired, parameterIsList);
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

      // Generate the binding methods for each option parameter representation
      for (Map.Entry<OptionParameterKey, List<OptionRepresentationKey>> e : optionRepresentationsByParameter
          .entrySet()) {
        final OptionParameterKey parameter = e.getKey();
        final Character optionShortName = parameter.getShortName().orElse(null);
        final String optionLongName = parameter.getLongName().orElse(null);
        final BindingMetadata metadata =
            optionMetadataService.getPositionalParameterMetadata(optionShortName, optionLongName);
        final List<OptionRepresentationKey> representations = e.getValue();
        final boolean parameterIsRequired = metadata.isRequired();
        final boolean parameterIsList = metadata.isList();
        for (OptionRepresentationKey representation : representations) {
          final TypeMirror representationType = representation.getType();
          final String representationDefaultValue = representation.getDefaultValue().orElse(null);
          emitOptionParameterRepresentationBindingMethods(out, optionShortName, optionLongName,
              parameterIsRequired, parameterIsList, representationType, representationDefaultValue);
        }
      }

      // Generate the binding methods for each flag parameter representation
      for (Map.Entry<FlagParameterKey, List<FlagRepresentationKey>> e : flagRepresentationsByParameter
          .entrySet()) {
        final FlagParameterKey parameter = e.getKey();
        final Character flagShortPositiveName = parameter.getPositiveShortName().orElse(null);
        final String flagpositiveLongName = parameter.getPositiveLongName().orElse(null);
        final Character flagnegativeShortName = parameter.getNegativeShortName().orElse(null);
        final String flagnegativeLongName = parameter.getNegativeLongName().orElse(null);
        final BindingMetadata metadata =
            flagMetadataService.getPositionalParameterMetadata(flagShortPositiveName,
                flagpositiveLongName, flagnegativeShortName, flagnegativeLongName);
        final List<FlagRepresentationKey> representations = e.getValue();
        final boolean parameterIsRequired = metadata.isRequired();
        final boolean parameterIsList = metadata.isList();
        for (FlagRepresentationKey representation : representations) {
          final TypeMirror representationType = representation.getType();
          final Boolean representationDefaultValue = representation.getDefaultValue().orElse(null);
          emitFlagParameterRepresentationBindingMethods(out, flagShortPositiveName,
              flagpositiveLongName, flagnegativeShortName, flagnegativeLongName,
              parameterIsRequired, parameterIsList, representationType, representationDefaultValue);
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
        out.println("    @PositionalCliParameter(value=" + position + ", defaultValue="
            + defaultValueExpr + ")");
        out.println("    public List<String> " + baseMethodName + "AsListOfString() {");
        out.println("        if(" + fieldName + " == null)");
        out.println("            return singletonList(" + defaultValueExpr + ");");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else if (parameterIsRequired) {
        out.println("    @Provides");
        out.println("    @PositionalCliParameter(" + position + ")");
        out.println("    public List<String> " + baseMethodName + "AsListOfString() {");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else {
        // We never generate a nullable list of strings. We just use empty list.
        out.println("    @Provides");
        out.println("    @PositionalCliParameter(" + position + ")");
        out.println("    public List<String> " + baseMethodName + "AsListOfString() {");
        out.println("        if(" + fieldName + " == null)");
        out.println("            return emptyList();");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
        out.println("    @Provides");
        out.println("    @PositionalCliParameter(" + position + ")");
        out.println("    public Optional<List<String>> " + baseMethodName
            + "AsOptionalOfString(@PositionalCliParameter(" + position + ") List<String> value) {");
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
        out.println("    @PositionalCliParameter(value=" + position + ", defaultValue="
            + defaultValueExpr + ")");
        out.println("    public String " + baseMethodName + "AsString() {");
        out.println("        if(" + fieldName + " == null)");
        out.println("            return " + defaultValueExpr + ";");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else if (parameterIsRequired) {
        out.println("    @Provides");
        out.println("    @PositionalCliParameter(" + position + ")");
        out.println("    public String " + baseMethodName + "AsString() {");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else {
        out.println("    @Provides");
        out.println("    @Nullable");
        out.println("    @PositionalCliParameter(" + position + ")");
        out.println("    public String " + baseMethodName + "AsString() {");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
        out.println("    @Provides");
        out.println("    @PositionalCliParameter(" + position + ")");
        out.println("    public Optional<String> " + baseMethodName
            + "AsOptionalOfString(@Nullable @PositionalCliParameter(" + position
            + ") String value) {");
        out.println("        return Optional.ofNullable(value);");
        out.println("    }");
        out.println();
      }
    } else {
      final String typeSimpleName = getSimpleTypeName(representationType);
      if (parameterIsList) {
        final String conversionExpr = getListOfStringConverter()
            .generateConversionExpr(representationType, "value").orElse(null);
        if (conversionExpr == null) {
          // This should never happen, we've already checked that the conversion is possible.
          getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Cannot convert " + representationType + " from " + getStringType());
          return;
        }

        if (representationDefaultValue != null) {
          // We don't need to check nullability here because the default value "protects" us
          // from any possible null values.
          final String defaultValueExpr =
              "\"" + Java.escapeString(representationDefaultValue) + "\"";
          out.println("    @Provides");
          out.println("    @PositionalCliParameter(value=" + position + ", defaultValue="
              + defaultValueExpr + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@PositionalCliParameter(value=" + position + ", defaultValue="
              + defaultValueExpr + " ) List<String> value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else if (parameterIsRequired) {
          // We don't need to check for null here because we already did in the constructor
          out.println("    @Provides");
          out.println("    @PositionalCliParameter(" + position + ")");
          out.println(
              "    public " + representationType + " " + baseMethodName + "As" + typeSimpleName
                  + "(@PositionalCliParameter(" + position + ") List<String> value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else {
          // We never generate a nullable list of strings. We just use empty list.
          out.println("    @Provides");
          out.println("    @PositionalCliParameter(" + position + ")");
          out.println(
              "    public " + representationType + " " + baseMethodName + "As" + typeSimpleName
                  + "(@PositionalCliParameter(" + position + ") List<String> value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
          out.println("    @Provides");
          out.println("    @PositionalCliParameter(" + position + ")");
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(@PositionalCliParameter(" + position
              + ") Optional<List<String>> o) {");
          out.println("        return o.map(value -> " + conversionExpr + ");");
          out.println("    }");
          out.println();
        }
      } else {
        final String conversionExpr =
            getStringConverter().generateConversionExpr(representationType, "value").orElse(null);
        if (conversionExpr == null) {
          // This should never happen, we've already checked that the conversion is possible.
          getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Cannot convert " + representationType + " from " + getStringType());
          return;
        }

        if (representationDefaultValue != null) {
          // We don't need to check nullability here because the default value "protects" us
          // from any possible null values.
          final String defaultValueExpr =
              "\"" + Java.escapeString(representationDefaultValue) + "\"";
          out.println("    @Provides");
          out.println("    @PositionalCliParameter(value=" + position + ", defaultValue="
              + defaultValueExpr + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@PositionalCliParameter(value=" + position + ", defaultValue="
              + defaultValueExpr + " ) String value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else if (parameterIsRequired) {
          // We don't need to check for null here because we already did in the constructor
          out.println("    @Provides");
          out.println("    @PositionalCliParameter(" + position + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@PositionalCliParameter(" + position + ") String value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else {
          out.println("    @Provides");
          out.println("    @Nullable");
          out.println("    @PositionalCliParameter(" + position + ")");
          out.println(
              "    public " + representationType + " " + baseMethodName + "As" + typeSimpleName
                  + "(@Nullable @PositionalCliParameter(" + position + ") String value) {");
          out.println("        if(value == null)");
          out.println("            return null;");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
          out.println("    @Provides");
          out.println("    @PositionalCliParameter(" + position + ")");
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(@PositionalCliParameter(" + position
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
      Collection<OptionParameterKey> parameters) {
    for (OptionParameterKey parameter : parameters) {
      final Character shortName = parameter.getShortName().orElse(null);
      if (shortName == null)
        continue;
      final String parameterSignature =
          optionParameterSignature(shortName, parameter.getLongName().orElse(null));
      out.println(
          "        optionShortNames.put(\'" + shortName + "\', \"" + parameterSignature + "\");");
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
    } else {
      out.println("            this." + fieldName + " = null;");
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
            "    @OptionCliParameter(" + nameClauses + ", defaultValue=" + defaultValueExpr + ")");
        out.println("    public List<String> " + baseMethodName + "AsListOfString() {");
        out.println("        if(" + fieldName + " == null)");
        out.println("            return singletonList(" + defaultValueExpr + ");");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else if (parameterIsRequired) {
        // We don't need to check for null here because we already did in the constructor
        out.println("    @Provides");
        out.println("    @OptionCliParameter(" + nameClauses + ")");
        out.println("    public List<String> " + baseMethodName + "AsListOfString() {");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else {
        // We never generate a nullable list of strings. We just use empty list.
        out.println("    @Provides");
        out.println("    @OptionCliParameter(" + nameClauses + ")");
        out.println("    public List<String> " + baseMethodName + "AsListOfString() {");
        out.println("        if(" + fieldName + " == null)");
        out.println("            return emptyList();");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
        out.println("    @Provides");
        out.println("    @OptionCliParameter(" + nameClauses + ")");
        out.println("    public Optional<List<String>> " + baseMethodName
            + "AsOptionalOfString(@OptionCliParameter(" + nameClauses + ") List<String> value) {");
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
            "    @OptionCliParameter(" + nameClauses + ", defaultValue=" + defaultValueExpr + ")");
        out.println("    public String " + baseMethodName + "AsString() {");
        out.println("        if(" + fieldName + " == null)");
        out.println("            return " + defaultValueExpr + ";");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else if (parameterIsRequired) {
        out.println("    @Provides");
        out.println("    @OptionCliParameter(" + nameClauses + ")");
        out.println("    public String " + baseMethodName + "AsString() {");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else {
        out.println("    @Provides");
        out.println("    @Nullable");
        out.println("    @OptionCliParameter(" + nameClauses + ")");
        out.println("    public String " + baseMethodName + "AsString() {");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
        out.println("    @Provides");
        out.println("    @OptionCliParameter(" + nameClauses + ")");
        out.println("    public Optional<String> " + baseMethodName
            + "AsOptionalOfString(@Nullable @PositionalCliParameter(" + nameClauses
            + ") String value) {");
        out.println("        return Optional.ofNullable(value);");
        out.println("    }");
        out.println();
      }
    } else {
      final String typeSimpleName = getSimpleTypeName(representationType);
      if (parameterIsList) {
        final String conversionExpr = getListOfStringConverter()
            .generateConversionExpr(representationType, "value").orElse(null);
        if (conversionExpr == null) {
          // This should never happen, we've already checked that the conversion is possible.
          getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Cannot convert " + representationType + " from " + getStringType());
          return;
        }

        if (representationDefaultValue != null) {
          // We don't need to check nullability here because the default value "protects" us
          // from any possible null values.
          final String defaultValueExpr =
              "\"" + Java.escapeString(representationDefaultValue) + "\"";
          out.println("    @Provides");
          out.println("    @OptionCliParameter(" + nameClauses + ", defaultValue="
              + defaultValueExpr + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@OptionCliParameter(" + nameClauses + ", defaultValue="
              + defaultValueExpr + " ) List<String> value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else if (parameterIsRequired) {
          // We don't need to check for null here because we already did in the constructor
          out.println("    @Provides");
          out.println("    @OptionCliParameter(" + nameClauses + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "( List<String> value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else {
          // We never generate a nullable list of strings. We just use empty list.
          out.println("    @Provides");
          out.println("    @OptionCliParameter(" + nameClauses + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@OptionCliParameter(" + nameClauses + ") List<String> value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
          out.println("    @Provides");
          out.println("    @OptionCliParameter(" + nameClauses + ")");
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(@OptionCliParameter(" + nameClauses
              + ") Optional<List<String>> o) {");
          out.println("        return o.map(value -> " + conversionExpr + ");");
          out.println("    }");
          out.println();
        }
      } else {
        final String conversionExpr =
            getStringConverter().generateConversionExpr(representationType, "value").orElse(null);
        if (conversionExpr == null) {
          // This should never happen, we've already checked that the conversion is possible.
          getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Cannot convert " + representationType + " from " + getStringType());
          return;
        }

        if (representationDefaultValue != null) {
          // We don't need to check nullability here because the default value "protects" us
          // from any possible null values.
          final String defaultValueExpr =
              "\"" + Java.escapeString(representationDefaultValue) + "\"";
          out.println("    @Provides");
          out.println("    @OptionCliParameter(" + nameClauses + ", defaultValue="
              + defaultValueExpr + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@OptionCliParameter(" + nameClauses + ", defaultValue="
              + defaultValueExpr + " ) String value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else if (parameterIsRequired) {
          // We don't need to check for null here because we already did in the constructor
          out.println("    @Provides");
          out.println("    @OptionCliParameter(" + nameClauses + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@OptionCliParameter(" + nameClauses + ") String value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else {
          out.println("    @Provides");
          out.println("    @Nullable");
          out.println("    @OptionCliParameter(" + nameClauses + ")");
          out.println(
              "    public " + representationType + " " + baseMethodName + "As" + typeSimpleName
                  + "(@Nullable @OptionCliParameter(" + nameClauses + ") String value) {");
          out.println("        if(value == null)");
          out.println("            return null;");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
          out.println("    @Provides");
          out.println("    @OptionCliParameter(" + nameClauses + ")");
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(@OptionCliParameter(" + nameClauses
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
    return stringSignature(String.join("/", parts));
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // OPTION PARAMETER CODE GENERATION //////////////////////////////////////////////////////////////
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
      String negativeLongName, boolean parameterIsRequired, boolean parameterIsList) {
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
    if (parameterIsList) {
      out.println("    private final List<Boolean> " + fieldName + ";");
    } else {
      out.println("    private final Boolean " + fieldName + ";");
    }
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
      out.println("        flagShortPositiveNames.put(\'" + positiveShortName + "\', \""
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
      out.println("        flagpositiveLongNames.put(\"" + positiveLongName + "\", \""
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
      out.println("        flagnegativeShortNames.put(\'" + negativeShortName + "\', \""
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
      out.println("        flagnegativeLongNames.put(\"" + negativeLongName + "\", \""
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
      String negativeLongName, boolean parameterIsRequired, boolean parameterIsList) {
    final String fieldName = flagParameterInstanceFieldName(positiveShortName, positiveLongName,
        negativeShortName, negativeLongName);
    final String signature = flagParameterSignature(positiveShortName, positiveLongName,
        negativeShortName, negativeLongName);
    final String userFacingString = flagParameterUserFacingString(positiveShortName,
        positiveLongName, negativeShortName, negativeLongName);

    out.println("        if(parsed.getFlags().containsKey(\"" + signature + "\")) {");
    out.println("            List<Boolean> " + fieldName + " = parsed.getFlags().get(\""
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
      out.println("                \"Missing required flag parameter " + userFacingString + "\");");
    } else {
      out.println("            this." + fieldName + " = null;");
    }
    out.println("        }");
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
      String negativeLongName, boolean parameterIsRequired, boolean parameterIsList,
      TypeMirror representationType, Boolean representationDefaultValue) {
    final String fieldName = flagParameterInstanceFieldName(positiveShortName, positiveLongName,
        negativeShortName, negativeLongName);
    final String signature = flagParameterSignature(positiveShortName, positiveLongName,
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

    final StringBuilder baseMethodName =
        new StringBuilder().append("provideFlag").append(signature);
    if (representationDefaultValue != null) {
      baseMethodName.append("WithDefaultValue")
          .append(representationDefaultValue.booleanValue() ? "True" : "False");
    }

    if (parameterIsList == true
        && getTypes().isSameType(representationType, getListOfBooleanType())) {
      // This is a varargs parameter, and we're generating the "default" binding.
      if (representationDefaultValue != null) {
        final String annotationDefaultValueExpr =
            "FlagCliParameterValue." + representationDefaultValue;
        final String javaDefaultValueExpr = "Boolean." + representationDefaultValue;
        out.println("    @Provides");
        out.println("    @FlagCliParameter(" + nameClauses + ", defaultValue="
            + annotationDefaultValueExpr + ")");
        out.println("    public List<Boolean> " + baseMethodName + "AsListOfBoolean() {");
        out.println("        if(" + fieldName + " == null)");
        out.println("            return singletonList(" + javaDefaultValueExpr + ");");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else if (parameterIsRequired) {
        // We don't need to check for null here because we already did in the constructor
        out.println("    @Provides");
        out.println("    @FlagCliParameter(" + nameClauses + ")");
        out.println("    public List<Boolean> " + baseMethodName + "AsListOfBoolean() {");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else {
        // We never generate a nullable list of strings. We just use empty list.
        out.println("    @Provides");
        out.println("    @FlagCliParameter(" + nameClauses + ")");
        out.println("    public List<Boolean> " + baseMethodName + "AsListOfBoolean() {");
        out.println("        if(" + fieldName + " == null)");
        out.println("            return emptyList();");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
        out.println("    @Provides");
        out.println("    @FlagCliParameter(" + nameClauses + ")");
        out.println("    public Optional<List<Boolean>> " + baseMethodName
            + "AsOptionalOfBoolean(@FlagCliParameter(" + nameClauses + ") List<Boolean> value) {");
        out.println("        return Optional.of(value);");
        out.println("    }");
        out.println();
      }
    } else if (parameterIsList == false
        && getTypes().isSameType(representationType, getBooleanType())) {
      // This is a single positional parameter, and we are generating the "default" binding
      if (representationDefaultValue != null) {
        final String annotationDefaultValueExpr =
            "FlagCliParameterValue." + representationDefaultValue;
        final String javaDefaultValueExpr = "Boolean." + representationDefaultValue;
        out.println("    @Provides");
        out.println("    @FlagCliParameter(" + nameClauses + ", defaultValue="
            + annotationDefaultValueExpr + ")");
        out.println("    public Boolean " + baseMethodName + "AsBoolean() {");
        out.println("        if(" + fieldName + " == null)");
        out.println("            return " + javaDefaultValueExpr + ";");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else if (parameterIsRequired) {
        out.println("    @Provides");
        out.println("    @FlagCliParameter(" + nameClauses + ")");
        out.println("    public Boolean " + baseMethodName + "AsBoolean() {");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
      } else {
        out.println("    @Provides");
        out.println("    @Nullable");
        out.println("    @FlagCliParameter(" + nameClauses + ")");
        out.println("    public Boolean " + baseMethodName + "AsBoolean() {");
        out.println("        return " + fieldName + ";");
        out.println("    }");
        out.println();
        out.println("    @Provides");
        out.println("    @FlagCliParameter(" + nameClauses + ")");
        out.println("    public Optional<Boolean> " + baseMethodName
            + "AsOptionalOfBoolean(@Nullable @FlagCliParameter(" + nameClauses
            + ") Boolean value) {");
        out.println("        return Optional.ofNullable(value);");
        out.println("    }");
        out.println();
      }
    } else {
      final String typeSimpleName = getSimpleTypeName(representationType);
      if (parameterIsList) {
        final String conversionExpr = getListOfBooleanConverter()
            .generateConversionExpr(representationType, "value").orElse(null);
        if (conversionExpr == null) {
          // This should never happen, we've already checked that the conversion is possible.
          getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Cannot convert " + representationType + " from " + getListOfBooleanType());
          return;
        }

        if (representationDefaultValue != null) {
          // We don't need to check nullability here because the default value "protects" us
          // from any possible null values.
          final String annotationDefaultValueExpr =
              "FlagCliParameterValue." + representationDefaultValue;
          final String javaDefaultValueExpr = "Boolean." + representationDefaultValue;
          out.println("    @Provides");
          out.println("    @FlagCliParameter(" + nameClauses + ", defaultValue="
              + annotationDefaultValueExpr + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@FlagCliParameter(" + nameClauses + ", defaultValue="
              + javaDefaultValueExpr + " ) List<Boolean> value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else if (parameterIsRequired) {
          // We don't need to check for null here because we already did in the constructor
          out.println("    @Provides");
          out.println("    @FlagCliParameter(" + nameClauses + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(List<Boolean> value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else {
          // We never generate a nullable list of strings. We just use empty list.
          out.println("    @Provides");
          out.println("    @FlagCliParameter(" + nameClauses + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@FlagCliParameter(" + nameClauses + ") List<Boolean> value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
          out.println("    @Provides");
          out.println("    @FlagCliParameter(" + nameClauses + ")");
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(@FlagCliParameter(" + nameClauses
              + ") Optional<Boolean<String>> o) {");
          out.println("        return o.map(value -> " + conversionExpr + ");");
          out.println("    }");
          out.println();
        }
      } else {
        final String conversionExpr =
            getBooleanConverter().generateConversionExpr(representationType, "value").orElse(null);
        if (conversionExpr == null) {
          // This should never happen, we've already checked that the conversion is possible.
          getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Cannot convert " + representationType + " from " + getBooleanType());
          return;
        }

        if (representationDefaultValue != null) {
          // We don't need to check nullability here because the default value "protects" us
          // from any possible null values.
          final String annotationDefaultValueExpr =
              "FlagCliParameterValue." + representationDefaultValue;
          final String javaDefaultValueExpr = "Boolean." + representationDefaultValue;
          out.println("    @Provides");
          out.println("    @FlagCliParameter(" + nameClauses + ", defaultValue="
              + annotationDefaultValueExpr + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@FlagCliParameter(" + nameClauses + ", defaultValue="
              + annotationDefaultValueExpr + " ) Boolean value) {");
          out.println("        return " + javaDefaultValueExpr + ";");
          out.println("    }");
          out.println();
        } else if (parameterIsRequired) {
          // We don't need to check for null here because we already did in the constructor
          out.println("    @Provides");
          out.println("    @FlagCliParameter(" + nameClauses + ")");
          out.println("    public " + representationType + " " + baseMethodName + "As"
              + typeSimpleName + "(@FlagCliParameter(" + nameClauses + ") Boolean value) {");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
        } else {
          out.println("    @Provides");
          out.println("    @Nullable");
          out.println("    @FlagCliParameter(" + nameClauses + ")");
          out.println(
              "    public " + representationType + " " + baseMethodName + "As" + typeSimpleName
                  + "(@Nullable @FlagCliParameter(" + nameClauses + ") Boolean value) {");
          out.println("        if(value == null)");
          out.println("            return null;");
          out.println("        return " + conversionExpr + ";");
          out.println("    }");
          out.println();
          out.println("    @Provides");
          out.println("    @FlagCliParameter(" + nameClauses + ")");
          out.println("    public Optional<" + representationType + "> " + baseMethodName
              + "AsOptionalOf" + typeSimpleName + "(@FlagCliParameter(" + nameClauses
              + ") Optional<Boolean> o) {");
          out.println("        return o.map(value -> " + conversionExpr + ");");
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
