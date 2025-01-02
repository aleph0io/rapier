/*-
 * =================================LICENSE_START==================================
 * rapier-processor-core
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
package rapier.core;


import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import org.junit.jupiter.api.Test;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import dagger.Component;
import rapier.core.DaggerComponentAnalyzer;
import rapier.core.model.DaggerComponentAnalysis;
import rapier.core.model.DaggerInjectionSite;

public class DaggerComponentAnalyzerTest {

  @Test
  public void givenComponentWithModulesAndJsr330InjectionSites_whenCompile_thenDiscoverAllDependencies() {
    // Mock Dagger @Component source
    String componentSource = """
            import dagger.Component;

            @Component(modules = {ReferencedModule.class})
            public interface TestComponent {
                Alpha provisionAlpha();
            }
        """;

    // Mock ReferencedModule source
    String referencedModuleSource = """
            import dagger.Module;
            import dagger.Provides;
            import javax.inject.Named;

            @Module(includes = {IncludedModule.class})
            public class ReferencedModule {
                @Provides
                @Named("referenced")
                public Alpha provideAlpha(Bravo bravo) {
                    return new Alpha(bravo);
                }
            }
        """;

    // Mock IncludedModule source
    String includedModuleSource = """
            import dagger.Module;
            import dagger.Provides;
            import javax.inject.Named;

            @Module
            public class IncludedModule {
                @Provides
                @Named("included")
                public Bravo provideBravo(Charlie charlie) {
                    return new Bravo(charlie);
                }
            }
        """;

    // Mock Alpha source
    String alphaSource = """
            import javax.inject.Inject;
            import javax.inject.Named;

            public class Alpha {
                @Inject
                @Named("alphaField")
                public String alphaField;

                @Inject
                public Alpha(@Named("alphaConstructor") Bravo alphaConstructor) {}

                @Inject
                public void setAlphaMethod(@Named("alphaMethod") String alphaMethod) {}
            }
        """;

    // Mock Bravo source
    String bravoSource = """
            import javax.inject.Inject;
            import javax.inject.Named;

            public class Bravo {
                @Inject
                @Named("bravoField")
                public String bravoField;

                @Inject
                public Bravo(@Named("bravoConstructor") Charlie alphaConstructor) {}

                @Inject
                public void setBravoMethod(@Named("bravoMethod") String bravoMethod) {}
            }
        """;

    // Mock Charlie source
    String charlieSource = """
            import javax.inject.Inject;
            import javax.inject.Named;

            public class Charlie {
                @Inject
                @Named("charlieField")
                public String charlieField;

                @Inject
                public Charlie(@Named("charlieConstructor") String charlieConstructor) {}

                @Inject
                public void setCharlieMethod(@Named("charlieMethod") String charlieMethod) {}
            }
        """;

    // Compile the sources and process them with DaggerComponentAnalyzer
    final Set<DaggerInjectionSite> dependencies = new HashSet<>();

    Compilation compilation = Compiler.javac().withProcessors(new AbstractProcessor() {
      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ProcessingEnvironment processingEnv = this.processingEnv;

        roundEnv.getElementsAnnotatedWith(Component.class).forEach(element -> {
          if (element instanceof TypeElement componentElement) {
            DaggerComponentAnalyzer analyzer = new DaggerComponentAnalyzer(processingEnv);
            DaggerComponentAnalysis analysis = analyzer.analyzeComponent(componentElement);
            dependencies.addAll(analysis.getDependencies());
          }
        });

        return false; // Allow other processors to process this round
      }

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("dagger.Component");
      }
    }).compile(JavaFileObjects.forSourceString("TestComponent", componentSource),
        JavaFileObjects.forSourceString("ReferencedModule", referencedModuleSource),
        JavaFileObjects.forSourceString("IncludedModule", includedModuleSource),
        JavaFileObjects.forSourceString("Alpha", alphaSource),
        JavaFileObjects.forSourceString("Bravo", bravoSource),
        JavaFileObjects.forSourceString("Charlie", charlieSource));

    // Ensure the compilation succeeded
    assertThat(compilation).succeeded();

    // Assertions
    assertEquals(12, dependencies.size());
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "DaggerInjectionSite [element=alphaField, siteType=INJECT_INSTANCE_FIELD, provisionStyle=VERBATIM, provisionedType=java.lang.String, providedType=java.lang.String, qualifier=@javax.inject.Named(\"alphaField\"), annotations=[@javax.inject.Inject, @javax.inject.Named(\"alphaField\")], nullable=false]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "DaggerInjectionSite [element=alphaConstructor, siteType=INJECT_CONSTRUCTOR_PARAMETER, provisionStyle=VERBATIM, provisionedType=Charlie, providedType=Charlie, qualifier=@javax.inject.Named(\"bravoConstructor\"), annotations=[@javax.inject.Named(\"bravoConstructor\")], nullable=false]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "DaggerInjectionSite [element=setAlphaMethod(java.lang.String), siteType=INJECT_INSTANCE_METHOD, provisionStyle=VERBATIM, provisionedType=java.lang.String, providedType=java.lang.String, qualifier=@javax.inject.Named(\"alphaMethod\"), annotations=[@javax.inject.Named(\"alphaMethod\")], nullable=false]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "DaggerInjectionSite [element=bravoField, siteType=INJECT_INSTANCE_FIELD, provisionStyle=VERBATIM, provisionedType=java.lang.String, providedType=java.lang.String, qualifier=@javax.inject.Named(\"bravoField\"), annotations=[@javax.inject.Inject, @javax.inject.Named(\"bravoField\")], nullable=false]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "DaggerInjectionSite [element=alphaConstructor, siteType=INJECT_CONSTRUCTOR_PARAMETER, provisionStyle=VERBATIM, provisionedType=Charlie, providedType=Charlie, qualifier=@javax.inject.Named(\"bravoConstructor\"), annotations=[@javax.inject.Named(\"bravoConstructor\")], nullable=false]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "DaggerInjectionSite [element=setBravoMethod(java.lang.String), siteType=INJECT_INSTANCE_METHOD, provisionStyle=VERBATIM, provisionedType=java.lang.String, providedType=java.lang.String, qualifier=@javax.inject.Named(\"bravoMethod\"), annotations=[@javax.inject.Named(\"bravoMethod\")], nullable=false]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "DaggerInjectionSite [element=charlieField, siteType=INJECT_INSTANCE_FIELD, provisionStyle=VERBATIM, provisionedType=java.lang.String, providedType=java.lang.String, qualifier=@javax.inject.Named(\"charlieField\"), annotations=[@javax.inject.Inject, @javax.inject.Named(\"charlieField\")], nullable=false]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "DaggerInjectionSite [element=charlieConstructor, siteType=INJECT_CONSTRUCTOR_PARAMETER, provisionStyle=VERBATIM, provisionedType=java.lang.String, providedType=java.lang.String, qualifier=@javax.inject.Named(\"charlieConstructor\"), annotations=[@javax.inject.Named(\"charlieConstructor\")], nullable=false]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "DaggerInjectionSite [element=setCharlieMethod(java.lang.String), siteType=INJECT_INSTANCE_METHOD, provisionStyle=VERBATIM, provisionedType=java.lang.String, providedType=java.lang.String, qualifier=@javax.inject.Named(\"charlieMethod\"), annotations=[@javax.inject.Named(\"charlieMethod\")], nullable=false]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "DaggerInjectionSite [element=charlie, siteType=MODULE_INSTANCE_PROVIDES_METHOD_PARAMETER, provisionStyle=VERBATIM, provisionedType=Charlie, providedType=Charlie, qualifier=null, annotations=[], nullable=false]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "DaggerInjectionSite [element=bravo, siteType=MODULE_INSTANCE_PROVIDES_METHOD_PARAMETER, provisionStyle=VERBATIM, provisionedType=Bravo, providedType=Bravo, qualifier=null, annotations=[], nullable=false]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "DaggerInjectionSite [element=provisionAlpha(), siteType=COMPONENT_PROVISION_METHOD_RESULT, provisionStyle=VERBATIM, provisionedType=Alpha, providedType=Alpha, qualifier=null, annotations=[], nullable=false]")));
  }
}
