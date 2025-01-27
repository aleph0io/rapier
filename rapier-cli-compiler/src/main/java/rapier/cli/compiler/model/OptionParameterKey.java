/*-
 * =================================LICENSE_START==================================
 * rapier-cli-compiler
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
package rapier.cli.compiler.model;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import rapier.cli.CliOptionParameter;
import rapier.compiler.core.model.DaggerInjectionSite;

public class OptionParameterKey {
  public static OptionParameterKey fromInjectionSite(DaggerInjectionSite dependency) {
    final AnnotationMirror qualifier = dependency.getQualifier().orElseThrow(() -> {
      return new IllegalArgumentException("Dependency must have qualifier");
    });

    if (!qualifier.getAnnotationType().toString()
        .equals(CliOptionParameter.class.getCanonicalName())) {
      throw new IllegalArgumentException("Dependency qualifier must be @CliOptionParameter");
    }
    
    final Character shortName = extractOptionParameterShortName(qualifier);
    
    final String longName = extractOptionParameterLongName(qualifier);

    return new OptionParameterKey(shortName, longName);
  }
    
  private static boolean isValidShortName(char ch) {
    return (ch>='a' && ch<='z') || (ch>='A' && ch<='Z') || (ch>='0' && ch<='9');
  }
  
  private static final Pattern PATTERN = Pattern.compile("[-a-zA-Z0-9_]*");

  private static boolean isValidLongName(String s) {
    return PATTERN.matcher(s).matches();
  }

  private final Character shortName;
  private final String longName;

  public OptionParameterKey(Character shortName, String longName) {
    if (longName != null && !isValidLongName(longName))
      throw new IllegalArgumentException("Option parameter longName is invalid");
    if (shortName != null && !isValidShortName(shortName))
      throw new IllegalArgumentException("Option parameter shortName is invalid");
    if (shortName == null && longName == null)
      throw new IllegalArgumentException("At least one of option parameter shortName, longName must be non-null");
    this.shortName = shortName;
    this.longName = longName;
  }

  public Optional<Character> getShortName() {
    return Optional.ofNullable(shortName);
  }

  public Optional<String> getLongName() {
    return Optional.ofNullable(longName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(longName, shortName);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    OptionParameterKey other = (OptionParameterKey) obj;
    return Objects.equals(longName, other.longName) && Objects.equals(shortName, other.shortName);
  }

  @Override
  public String toString() {
    return "OptionParameterKey [shortName=" + shortName + ", longName=" + longName + "]";
  }

  /* default */ static Character extractOptionParameterShortName(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(CliOptionParameter.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("shortName")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<Character, Void>() {
          @Override
          public Character visitChar(char c, Void p) {
            if (c == '\0')
              return null;
            return c;
          }
        }, null)).orElse(null);
  }

  /* default */ static String extractOptionParameterLongName(AnnotationMirror annotation) {
    assert annotation.getAnnotationType().toString()
        .equals(CliOptionParameter.class.getCanonicalName());
    return annotation.getElementValues().entrySet().stream()
        .filter(e -> e.getKey().getSimpleName().contentEquals("longName")).findFirst()
        .map(Map.Entry::getValue)
        .map(v -> v.accept(new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            if (s.isEmpty())
              return null;
            return s;
          }
        }, null)).orElse(null);
  }
}
