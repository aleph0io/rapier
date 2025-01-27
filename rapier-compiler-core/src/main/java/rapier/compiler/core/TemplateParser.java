/*-
 * =================================LICENSE_START==================================
 * rapier-compiler-core
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
package rapier.compiler.core;

/**
 * A simple template parser that can be used to parse templates with variables in them. This parser
 * supports the following syntax:
 * 
 * <ul>
 * <li><code>${variableName}</code>
 * <li><code>${variableName:-defaultValue}</code>
 * </ul>
 */
public class TemplateParser {
  public static interface ParseHandler {
    /**
     * Called when raw text is encountered in the template
     * 
     * @param index the index of the text in the template
     * @param text the text that was encountered
     */
    public void onText(int index, String text);

    /**
     * Called when a variable expression is encountered in the template, e.g.,
     * <code>${variableName}</code>
     * 
     * @param index the index of the variable expression in the template
     * @param variableName the name of the variable in the expression
     */
    public void onVariableExpression(int index, String variableName);

    /**
     * Called when a variable expression with a default value is encountered in the template, e.g.,
     * <code>${variableName:-defaultValue}</code>
     * 
     * @param index the index of the variable expression in the template
     * @param variableName the name of the variable in the expression
     * @param defaultValue the default value for the variable
     */
    public void onVariableExpressionWithDefaultValue(int index, String variableName, String defaultValue);
  }

  @SuppressWarnings("serial")
  public static class TemplateSyntaxException extends IllegalArgumentException {
    private final int index;

    public TemplateSyntaxException(int index, String message) {
      super(message);
      this.index = index;
    }

    public int getIndex() {
      return index;
    }
  }

  /**
   * Parse the given template and call the appropriate methods on the given handler for each
   * encountered element in the template. The handler methods are called in the order in which the
   * elements appear in the template.
   * 
   * @param template the template to parse
   * @param handler the handler to call for each element in the template
   * 
   * @throws NullPointerException if either the template or handler is null
   * @throws TemplateSyntaxException if the template contains a syntax error
   */
  public void parse(String template, ParseHandler handler) {
    if (template == null)
      throw new NullPointerException();
    if (handler == null)
      throw new NullPointerException();
    
    if(template.isEmpty()) {
      handler.onText(0, "");
      return;
    }


    int index = 0;
    while (index < template.length()) {
      final int dollarIndex = template.indexOf('$', index);
      if (dollarIndex == -1) {
        handler.onText(index, template.substring(index, template.length()));
        index = template.length();
        continue;
      }

      handler.onText(index, template.substring(index, dollarIndex));

      if (dollarIndex == template.length() - 1) {
        handler.onText(index, "$");
        index = dollarIndex + 1;
        continue;
      }

      final char nextCharAfterDollar = template.charAt(dollarIndex + 1);
      if (nextCharAfterDollar != '{') {
        handler.onText(index, "$" + nextCharAfterDollar);
        index = dollarIndex + 2;
        continue;
      }

      final int openingBraceIndex = dollarIndex + 1;
      final int closingBraceIndex = template.indexOf('}', dollarIndex);
      if (closingBraceIndex == -1)
        throw new TemplateSyntaxException(dollarIndex, "Unclosed variable expression");

      final String expression = template.substring(openingBraceIndex + 1, closingBraceIndex);

      final int colonIndex = expression.indexOf(':');
      if (colonIndex == -1) {
        final String variableName = expression;
        handler.onVariableExpression(index, variableName);
        index = closingBraceIndex + 1;
        continue;
      }

      final String variableName = expression.substring(0, colonIndex);

      if (colonIndex == expression.length() - 1)
        throw new TemplateSyntaxException(dollarIndex, "Invalid variable expression");

      final char nextCharAfterColor = expression.charAt(colonIndex + 1);
      if (nextCharAfterColor == '-') {
        final String defaultValue = expression.substring(colonIndex + 2, expression.length());
        handler.onVariableExpressionWithDefaultValue(index, variableName, defaultValue);
        index = closingBraceIndex + 1;
      } else {
        throw new TemplateSyntaxException(dollarIndex, "Invalid variable expression");
      }
    }
  }
}
