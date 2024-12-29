package com.sigpwned.rapier.core;

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
import com.sigpwned.rapier.core.model.DaggerComponentAnalysis;
import com.sigpwned.rapier.core.model.Dependency;
import dagger.Component;

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
    final Set<Dependency> dependencies = new HashSet<>();

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

        /*
         * [Dependency [type=java.lang.String, qualifier=@javax.inject.Named("charlieField")],
         * Dependency [type=Charlie, qualifier=null], Dependency [type=java.lang.String,
         * qualifier=@javax.inject.Named("charlieMethod")], Dependency [type=java.lang.String,
         * qualifier=@javax.inject.Named("alphaField")], Dependency [type=java.lang.String,
         * qualifier=@javax.inject.Named("alphaMethod")], Dependency [type=Alpha, qualifier=null]]
         * 
         */

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
        "Dependency [type=java.lang.String, qualifier=@javax.inject.Named(\"alphaField\")]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString()
        .equals("Dependency [type=Bravo, qualifier=@javax.inject.Named(\"alphaConstructor\")]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "Dependency [type=java.lang.String, qualifier=@javax.inject.Named(\"alphaMethod\")]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "Dependency [type=java.lang.String, qualifier=@javax.inject.Named(\"bravoField\")]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString()
        .equals("Dependency [type=Charlie, qualifier=@javax.inject.Named(\"bravoConstructor\")]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "Dependency [type=java.lang.String, qualifier=@javax.inject.Named(\"bravoMethod\")]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "Dependency [type=java.lang.String, qualifier=@javax.inject.Named(\"charlieField\")]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "Dependency [type=java.lang.String, qualifier=@javax.inject.Named(\"charlieConstructor\")]")));
    assertTrue(dependencies.stream().anyMatch(d -> d.toString().equals(
        "Dependency [type=java.lang.String, qualifier=@javax.inject.Named(\"charlieMethod\")]")));
    assertTrue(dependencies.stream()
        .anyMatch(d -> d.toString().equals("Dependency [type=Charlie, qualifier=null]")));
    assertTrue(dependencies.stream()
        .anyMatch(d -> d.toString().equals("Dependency [type=Bravo, qualifier=null]")));
    assertTrue(dependencies.stream()
        .anyMatch(d -> d.toString().equals("Dependency [type=Alpha, qualifier=null]")));
  }
}
