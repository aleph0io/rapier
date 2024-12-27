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
package com.sigpwned.rapier.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import com.sigpwned.rapier.core.util.CaseFormat;

@SupportedAnnotationTypes("com.sigpwned.rapier.core.SystemProperty")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@SupportedOptions({"rapier.targetPackage"})
public class SystemPropertyProcessor extends AbstractProcessor {
  private List<Map.Entry<Element, String>> systemProperties;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.systemProperties = new ArrayList<>();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    roundEnv.getElementsAnnotatedWith(SystemProperty.class).stream().flatMap(
        e -> extractSystemProperty(e).map(n -> Map.<Element, String>entry(e, n)).stream())
        .forEach(systemProperties::add);

    // Wait until processing is over to generate the module
    if (!roundEnv.processingOver()) {
      return false;
    }

    final String targetPackage = Optional
        .ofNullable(getProcessingEnv().getOptions().get("rapier.targetPackage")).orElse("rapier");

    final Element[] dependentElements =
        systemProperties.stream().map(Map.Entry::getKey).toArray(Element[]::new);

    final SortedSet<String> systemPropertyNames = systemProperties.stream().map(Map.Entry::getValue)
        .collect(Collectors.toCollection(TreeSet::new));

    try {
      final JavaFileObject o = getFiler()
          .createSourceFile(targetPackage + ".RapierSystemPropertyModule", dependentElements);
      try (final PrintWriter writer = new PrintWriter(o.openWriter())) {
        writer.println("package " + targetPackage + ";");
        writer.println();
        writer.println("import static java.util.Collections.unmodifiableMap;");
        writer.println("import static java.util.stream.Collectors.toMap;");
        writer.println();
        writer.println("import com.sigpwned.rapier.core.SystemProperty;");
        writer.println("import dagger.Module;");
        writer.println("import dagger.Provides;");
        writer.println("import java.util.Map;");
        writer.println("import java.util.Optional;");
        writer.println("import java.util.Properties;");
        writer.println("import javax.inject.Inject;");
        writer.println();
        writer.println("@Module");
        writer.println("public class RapierSystemPropertyModule {");
        writer.println("    private final Map<String, String> properties;");
        writer.println();
        writer.println("    @Inject");
        writer.println("    public RapierSystemPropertyModule() {");
        writer.println("        this(System.getProperties());");
        writer.println("    }");
        writer.println();
        writer.println("    public RapierSystemPropertyModule(Properties properties) {");
        writer.println("        this(properties.entrySet()");
        writer.println("            .stream()");
        writer.println("            .collect(toMap(");
        writer.println("                e -> String.valueOf(e.getKey()),");
        writer.println("                e -> String.valueOf(e.getValue()))));");
        writer.println("    }");
        writer.println();
        writer.println("    public RapierSystemPropertyModule(Map<String, String> properties) {");
        writer.println("        this.properties = unmodifiableMap(properties);");
        writer.println("    }");
        writer.println();
        for (String systemPropertyName : systemPropertyNames) {
          final String baseMethodName = "provideSystemProperty"
              + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, systemPropertyName);

          writer.println("    // " + systemPropertyName);

          writer.println("    @Provides");
          writer.println("    @SystemProperty(\"" + systemPropertyName + "\")");
          writer.println("    public String " + baseMethodName + "() {");
          writer.println("        return properties.get(\"" + systemPropertyName + "\");");
          writer.println("    }");
          writer.println();

          writer.println("    @Provides");
          writer.println("    @SystemProperty(\"" + systemPropertyName + "\")");
          writer.println("    public Byte " + baseMethodName + "AsByte() {");
          writer.println("        return Optional.ofNullable(" + baseMethodName
              + "()).map(Byte::parseByte).orElse(null);");
          writer.println("    }");
          writer.println();

          writer.println("    @Provides");
          writer.println("    @SystemProperty(\"" + systemPropertyName + "\")");
          writer.println("    public Short " + baseMethodName + "AsShort() {");
          writer.println("        return Optional.ofNullable(" + baseMethodName
              + "()).map(Short::parseShort).orElse(null);");
          writer.println("    }");
          writer.println();

          writer.println("    @Provides");
          writer.println("    @SystemProperty(\"" + systemPropertyName + "\")");
          writer.println("    public Integer " + baseMethodName + "AsInteger() {");
          writer.println("        return Optional.ofNullable(" + baseMethodName
              + "()).map(Integer::parseInt).orElse(null);");
          writer.println("    }");
          writer.println();

          writer.println("    @Provides");
          writer.println("    @SystemProperty(\"" + systemPropertyName + "\")");
          writer.println("    public Long " + baseMethodName + "AsLong() {");
          writer.println("        return Optional.ofNullable(" + baseMethodName
              + "()).map(Long::parseLong).orElse(null);");
          writer.println("    }");
          writer.println();

          writer.println("    @Provides");
          writer.println("    @SystemProperty(\"" + systemPropertyName + "\")");
          writer.println("    public Float " + baseMethodName + "AsFloat() {");
          writer.println("        return Optional.ofNullable(" + baseMethodName
              + "()).map(Float::parseFloat).orElse(null);");
          writer.println("    }");
          writer.println();

          writer.println("    @Provides");
          writer.println("    @SystemProperty(\"" + systemPropertyName + "\")");
          writer.println("    public Double " + baseMethodName + "AsDouble() {");
          writer.println("        return Optional.ofNullable(" + baseMethodName
              + "()).map(Double::parseDouble).orElse(null);");
          writer.println("    }");
          writer.println();

          writer.println("    @Provides");
          writer.println("    @SystemProperty(\"" + systemPropertyName + "\")");
          writer.println("    public Boolean " + baseMethodName + "AsBoolean() {");
          writer.println("        return Optional.ofNullable(" + baseMethodName
              + "()).map(Boolean::parseBoolean).orElse(null);");
          writer.println("    }");
          writer.println();
        }
        writer.println("}");
      }
    } catch (IOException e) {
      e.printStackTrace();
      getMessager().printMessage(Diagnostic.Kind.ERROR,
          "Failed to create source file: " + e.getMessage());
    }

    return true;
  }

  /**
   * Process the annotated element. Example: Log its details.
   */
  private Optional<String> extractSystemProperty(Element element) {
    AnnotationMirror annotation = element.getAnnotationMirrors().stream()
        .filter(
            a -> a.getAnnotationType().toString().equals(SystemProperty.class.getCanonicalName()))
        .findFirst().orElse(null);

    if (annotation == null)
      return Optional.empty();

    final String systemPropertyNames = annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("value")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            return s;
          }
        }, null)).orElseThrow(() -> {
          return new AssertionError("No string value for @SystemProperty");
        });

    return Optional.of(systemPropertyNames);
  }

  private ProcessingEnvironment getProcessingEnv() {
    return processingEnv;
  }

  private Filer getFiler() {
    return getProcessingEnv().getFiler();
  }

  private Messager getMessager() {
    return getProcessingEnv().getMessager();
  }
}
