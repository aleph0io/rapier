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

import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import dagger.Component;

@SupportedAnnotationTypes({"dagger.Component", "dagger.Module"})
@SupportedSourceVersion(SourceVersion.RELEASE_11) // Change as needed
public class RapierAnnotationProcessor extends AbstractProcessor {
  // private List<Map.Entry<Element,>>

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      if (annotation.getQualifiedName().toString().equals(Component.class.getCanonicalName())) {
        processComponentAnnotation(roundEnv.getElementsAnnotatedWith(annotation));
      } else if (annotation.getQualifiedName().toString().equals(Module.class.getCanonicalName())) {
        processModuleAnnotation(roundEnv.getElementsAnnotatedWith(annotation));
      }
    }
    return true; // Indicate that the annotations have been processed
  }

  private void processComponentAnnotation(Set<? extends Element> elements) {
    for (Element element : elements) {
      if (element.getKind() != ElementKind.INTERFACE) {
        getMessager().printMessage(Diagnostic.Kind.ERROR,
            "@Component can only be applied to interfaces.", element);
        continue;
      }
      TypeElement typeElement = (TypeElement) element;
      getMessager().printMessage(Diagnostic.Kind.NOTE,
          "Found @Component: " + typeElement.getQualifiedName());

      // Example: Inspect provision methods
      for (Element enclosed : typeElement.getEnclosedElements()) {
        if (enclosed.getKind() == ElementKind.METHOD) {
          getMessager().printMessage(Diagnostic.Kind.NOTE,
              "Provision method: " + enclosed.getSimpleName(), enclosed);
        }
      }
    }
  }

  private void processModuleAnnotation(Set<? extends Element> elements) {
    for (Element element : elements) {
      if (element.getKind() != ElementKind.CLASS) {
        getMessager().printMessage(Diagnostic.Kind.ERROR, "@Module can only be applied to classes.",
            element);
        continue;
      }
      TypeElement typeElement = (TypeElement) element;
      getMessager().printMessage(Diagnostic.Kind.NOTE,
          "Found @Module: " + typeElement.getQualifiedName());

      // Example: Inspect @Provides methods
      for (Element enclosed : typeElement.getEnclosedElements()) {
        if (enclosed.getKind() == ElementKind.METHOD) {
          getMessager().printMessage(Diagnostic.Kind.NOTE,
              "Potential @Provides method: " + enclosed.getSimpleName(), enclosed);
        }
      }
    }
  }

  private ProcessingEnvironment getProcessingEnv() {
    return processingEnv;
  }

  private Messager getMessager() {
    return getProcessingEnv().getMessager();
  }
}
