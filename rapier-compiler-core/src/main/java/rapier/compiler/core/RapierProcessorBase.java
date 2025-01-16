/*-
 * =================================LICENSE_START==================================
 * rapier-processor-core
 * ====================================SECTION=====================================
 * Copyright (C) 2024 - 2025 Andy Boothe
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
package rapier.compiler.core;



import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import rapier.compiler.core.util.Hashing;
import rapier.compiler.core.util.Hex;
import rapier.compiler.core.util.Java;

public abstract class RapierProcessorBase extends AbstractProcessor {
  /**
   * Unpacks a type to its provider equivalent. For example, converts primitives to boxed types.
   * 
   * @param type the type to unpack
   * @return the unpacked type
   */
  protected TypeMirror unpack(TypeMirror type) {
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

  protected ProcessingEnvironment getProcessingEnv() {
    return processingEnv;
  }

  protected Elements getElements() {
    return getProcessingEnv().getElementUtils();
  }

  protected Types getTypes() {
    return getProcessingEnv().getTypeUtils();
  }

  protected Filer getFiler() {
    return getProcessingEnv().getFiler();
  }

  protected Messager getMessager() {
    return getProcessingEnv().getMessager();
  }

  protected String getSimpleTypeName(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return getTypes().asElement(type).getSimpleName().toString();
    }

    DeclaredType declaredType = (DeclaredType) type;
    StringBuilder result = new StringBuilder();
    result.append(getTypes().asElement(declaredType).getSimpleName());

    if (!declaredType.getTypeArguments().isEmpty()) {
      result.append("Of");
      for (TypeMirror arg : declaredType.getTypeArguments()) {
        result.append(getSimpleTypeName(arg));
      }
    }

    return result.toString();
  }

  private transient TypeMirror stringType;

  protected TypeMirror getStringType() {
    if (stringType == null) {
      stringType = getElements().getTypeElement("java.lang.String").asType();
    }
    return stringType;
  }

  /**
   * Generate a signature for a string that is unique enough to be used as a method name suffix. The
   * current implementation uses the first 7 lowercase characters of the SHA1 hash of the given
   * string encoded using UTF-8.
   * 
   * @param s the string
   * @return the signature
   */
  protected String stringSignature(String s) {
    if (s == null)
      throw new NullPointerException();
    final byte[] digest = Hashing.sha1(s);
    final String hex = Hex.encode(digest);
    // Use the first 7 characters of the hash as the signature. If it's good enough for git, it's
    // good enough for us!
    return hex.substring(0, 7);
  }

  protected String compileTemplate(String template, String envVariableName,
      String sysVariableName) {
    final StringBuilder result = new StringBuilder();

    new TemplateParser().parse(template, new TemplateParser.ParseHandler() {
      private boolean first = true;

      @Override
      public void onVariableExpressionWithDefaultValue(int index, String variableName,
          String defaultValue) {
        if (first == false)
          result.append("+");

        if (variableName.startsWith("env.")) {
          final String n = variableName.substring(4);
          result.append("Optional.ofNullable(").append(envVariableName).append(".get(\"")
              .append(Java.escapeString(n)).append("\")).orElse(\"")
              .append(Java.escapeString(defaultValue)).append("\")");
        } else if (variableName.startsWith("sys.")) {
          final String n = variableName.substring(4);
          result.append("Optional.ofNullable(").append(sysVariableName).append(".get(\"")
              .append(Java.escapeString(n)).append("\")).orElse(\"")
              .append(Java.escapeString(defaultValue)).append("\")");
        } else {
          throw new TemplateParser.TemplateSyntaxException(index,
              "Variable name must start with 'env.' or 'sys.'");
        }

        first = false;
      }

      @Override
      public void onVariableExpression(int index, String variableName) {
        if (first == false)
          result.append("+");

        if (variableName.startsWith("env.")) {
          final String n = variableName.substring(4);
          result.append("Optional.ofNullable(").append(envVariableName).append(".get(\"")
              .append(Java.escapeString(n))
              .append("\")).orElseThrow(() -> new IllegalStateException(\"Environment variable ")
              .append(Java.escapeString(n)).append(" not set\"))");
        } else if (variableName.startsWith("sys.")) {
          final String n = variableName.substring(4);
          result.append("Optional.ofNullable(").append(sysVariableName).append(".get(\"")
              .append(Java.escapeString(n))
              .append("\")).orElseThrow(() -> new IllegalStateException(\"System property ")
              .append(Java.escapeString(n)).append(" not set\"))");
        } else {
          throw new TemplateParser.TemplateSyntaxException(index,
              "Variable name must start with 'env.' or 'sys.'");
        }

        first = false;
      }

      @Override
      public void onText(int index, String text) {
        if (first == false)
          result.append("+");

        result.append("\"").append(Java.escapeString(text)).append("\"");

        first = false;
      }
    });

    return result.toString();
  }
}
