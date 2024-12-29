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

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import com.sigpwned.rapier.core.model.DaggerComponentAnalysis;
import com.sigpwned.rapier.core.util.CaseFormat;
import dagger.Component;

@SupportedAnnotationTypes("dagger.Component")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class EnvironmentVariableProcessor extends AbstractProcessor {
  private List<TypeElement> components;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.components = new ArrayList<>();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    final Set<? extends Element> annotatedElementsThisRound =
        roundEnv.getElementsAnnotatedWith(Component.class);
    for (Element element : annotatedElementsThisRound) {
      if (element.getKind() != ElementKind.INTERFACE && element.getKind() != ElementKind.CLASS)
        continue;
      TypeElement typeElement = (TypeElement) element;
      components.add(typeElement);
    }

    // Wait until processing is over to generate the module
    if (!roundEnv.processingOver()) {
      return false;
    }

    for (TypeElement component : components) {
      processComponent(component);
    }

    return true;
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

    final SortedMap<String, Set<TypeMirror>> environmentVariables = analysis.getDependencies()
        .stream().filter(d -> d.getQualifier().isPresent())
        .filter(d -> getTypes().isSameType(d.getQualifier().get().getAnnotationType(),
            getElements().getTypeElement(EnvironmentVariable.class.getCanonicalName()).asType()))
        .collect(groupingBy(d -> extractEnvironmentVariable(d.getQualifier().get()), TreeMap::new,
            mapping(d -> d.getType(), toSet())));

    try {
      // TODO Is this right?
      final Element[] dependentElements = new Element[] {component};
      final JavaFileObject o = getFiler()
          .createSourceFile(componentPackageName + "." + moduleClassName, dependentElements);
      try (final PrintWriter writer = new PrintWriter(o.openWriter())) {
        writer.println("package " + componentPackageName + ";");
        writer.println();
        writer.println("import static java.util.Collections.unmodifiableMap;");
        writer.println();
        writer.println("import com.sigpwned.rapier.core.EnvironmentVariable;");
        writer.println("import dagger.Module;");
        writer.println("import dagger.Provides;");
        writer.println("import java.util.Map;");
        writer.println("import java.util.Optional;");
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
        for (Map.Entry<String, Set<TypeMirror>> e : environmentVariables.entrySet()) {
          final String environmentVariableName = e.getKey();
          final Set<TypeMirror> requiredTypes = e.getValue();
          final String baseMethodName = "provideEnvironmentVariable"
              + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, environmentVariableName);

          writer.println("    // " + environmentVariableName);

          writer.println("    @Provides");
          writer.println("    @EnvironmentVariable(\"" + environmentVariableName + "\")");
          writer.println("    public String " + baseMethodName + "() {");
          writer.println("        return env.get(\"" + environmentVariableName + "\");");
          writer.println("    }");
          writer.println();

          for (TypeMirror requiredType : requiredTypes) {
            requiredType = convertType(requiredType);

            if (requiredType.toString().equals("java.lang.String")) {
              // Skip the String type since we've already provided it
              continue;
            }

            final String expr = expr(requiredType).orElse(null);
            if (expr == null) {
              getMessager().printMessage(Diagnostic.Kind.ERROR,
                  "Cannot convert " + requiredType + " from a string");
              continue;
            }

            final String requiredTypeSimpleName =
                getTypes().asElement(requiredType).getSimpleName().toString();

            writer.println("    @Provides");
            writer.println("    @EnvironmentVariable(\"" + environmentVariableName + "\")");
            writer.println(
                "    public " + requiredType + " " + baseMethodName + "As" + requiredTypeSimpleName
                    + "(@EnvironmentVariable(\"" + environmentVariableName + "\") String value) {");
            writer.println("        return value != null ? " + expr + " : null;");
            writer.println("    }");
            writer.println();
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

  private Optional<String> expr(TypeMirror type) {
    // Get the TypeElement representing the declared type
    TypeElement typeElement = (TypeElement) getTypes().asElement(type);

    // Iterate through the enclosed elements to find the "valueOf" method
    Optional<ExecutableElement> maybeValueOfMethod = findValueOfMethod(typeElement);
    if (maybeValueOfMethod.isPresent()) {
      return Optional.of(type.toString() + ".valueOf(value)");
    }

    // Iterate through the enclosed elements to find the "fromString" method
    Optional<ExecutableElement> maybeFromStringMethod = findFromStringMethod(typeElement);
    if (maybeFromStringMethod.isPresent()) {
      return Optional.of(type.toString() + ".fromString(value)");
    }

    // Iterate through the enclosed elements to find the constructor that takes a string
    Optional<ExecutableElement> maybeStringConstructor = findStringConstructor(typeElement);
    if (maybeStringConstructor.isPresent()) {
      return Optional.of("new " + type.toString() + "(value)");
    }

    return Optional.empty();
  }

  private Optional<ExecutableElement> findValueOfMethod(TypeElement typeElement) {
    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.METHOD)
        continue;
      final ExecutableElement method = (ExecutableElement) enclosed;
      if (method.getSimpleName().contentEquals("valueOf")
          && method.getModifiers().contains(Modifier.PUBLIC)
          && method.getModifiers().contains(Modifier.STATIC) && method.getParameters().size() == 1
          && getTypes().isSameType(method.getParameters().get(0).asType(),
              getElements().getTypeElement("java.lang.String").asType())
          && getTypes().isSameType(method.getReturnType(), typeElement.asType())) {
        return Optional.of(method);
      }
    }
    return Optional.empty();

  }

  private Optional<ExecutableElement> findFromStringMethod(TypeElement typeElement) {
    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.METHOD)
        continue;
      final ExecutableElement method = (ExecutableElement) enclosed;
      if (method.getSimpleName().contentEquals("fromString")
          && method.getModifiers().contains(Modifier.PUBLIC)
          && method.getModifiers().contains(Modifier.STATIC) && method.getParameters().size() == 1
          && getTypes().isSameType(method.getParameters().get(0).asType(),
              getElements().getTypeElement("java.lang.String").asType())
          && getTypes().isSameType(method.getReturnType(), typeElement.asType())) {
        return Optional.of(method);
      }
    }
    return Optional.empty();

  }

  private Optional<ExecutableElement> findStringConstructor(TypeElement typeElement) {
    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.CONSTRUCTOR)
        continue;
      final ExecutableElement method = (ExecutableElement) enclosed;
      if (method.getModifiers().contains(Modifier.PUBLIC) && method.getParameters().size() == 1
          && getTypes().isSameType(method.getParameters().get(0).asType(),
              getElements().getTypeElement("java.lang.String").asType())) {
        return Optional.of(method);
      }
    }
    return Optional.empty();
  }

  private TypeMirror convertType(TypeMirror type) {
    if (type.getKind() == TypeKind.BYTE) {
      return getElements().getTypeElement(Byte.class.getCanonicalName()).asType();
    } else if (type.getKind() == TypeKind.SHORT) {
      return getElements().getTypeElement(Short.class.getCanonicalName()).asType();
    } else if (type.getKind() == TypeKind.INT) {
      return getElements().getTypeElement(Integer.class.getCanonicalName()).asType();
    } else if (type.getKind() == TypeKind.LONG) {
      return getElements().getTypeElement(Long.class.getCanonicalName()).asType();
    } else if (type.getKind() == TypeKind.FLOAT) {
      return getElements().getTypeElement(Float.class.getCanonicalName()).asType();
    } else if (type.getKind() == TypeKind.DOUBLE) {
      return getElements().getTypeElement(Double.class.getCanonicalName()).asType();
    } else if (type.getKind() == TypeKind.BOOLEAN) {
      return getElements().getTypeElement(Boolean.class.getCanonicalName()).asType();
    } else {
      return type;
    }
  }

  private String extractEnvironmentVariable(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(EnvironmentVariable.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("value")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            return s;
          }
        }, null)).orElseThrow(() -> {
          return new AssertionError("No string value for @EnvironmentVariable");
        });
  }

  private ProcessingEnvironment getProcessingEnv() {
    return processingEnv;
  }

  private Elements getElements() {
    return getProcessingEnv().getElementUtils();
  }

  private Types getTypes() {
    return getProcessingEnv().getTypeUtils();
  }

  private Filer getFiler() {
    return getProcessingEnv().getFiler();
  }

  private Messager getMessager() {
    return getProcessingEnv().getMessager();
  }
}
