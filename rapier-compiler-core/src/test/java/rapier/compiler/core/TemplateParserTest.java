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

import static java.util.Collections.unmodifiableList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TemplateParserTest {
  public static class EventStoringTemplateParseHandler implements TemplateParser.ParseHandler {
    private final List<String> events;

    public EventStoringTemplateParseHandler() {
      this.events = new ArrayList<>();
    }

    @Override
    public void onText(int index, String text) {
      events.add("onText(" + text + ")");
    }

    @Override
    public void onVariableExpression(int index, String variableName) {
      events.add("onVariableExpression(" + variableName + ")");
    }

    @Override
    public void onVariableExpressionWithDefaultValue(int index, String variableName, String defaultValue) {
      events
          .add("onVariableExpressionWithDefaultValue(" + variableName + ", " + defaultValue + ")");
    }

    public List<String> getEvents() {
      return unmodifiableList(events);
    }
  }

  @Test
  public void givenValidTemplateWithAllExpressionTypes_whenParse_thenReceiveExpectedEvents() {
    final EventStoringTemplateParseHandler handler = new EventStoringTemplateParseHandler();

    new TemplateParser().parse("hello ${WORLD} ${STUFF:-default value} foobar", handler);

    assertEquals(
        List.of("onText(hello )", "onVariableExpression(WORLD)", "onText( )",
            "onVariableExpressionWithDefaultValue(STUFF, default value)", "onText( foobar)"),
        handler.getEvents());
  }

  @Test
  public void givenTemplateWithUnclosedVariableExpression_whenParse_thenCatchTemplateSyntaxException() {
    final TemplateParser.TemplateSyntaxException problem =
        assertThrowsExactly(TemplateParser.TemplateSyntaxException.class, () -> {
          new TemplateParser().parse("hello ${WORLD", new TemplateParser.ParseHandler() {
            @Override
            public void onVariableExpressionWithDefaultValue(int index,
                String variableName, String defaultValue) {}

            @Override
            public void onVariableExpression(int index, String variableName) {}

            @Override
            public void onText(int index, String text) {}
          });
        });

    assertEquals("Unclosed variable expression", problem.getMessage());
    assertEquals(6, problem.getIndex());
  }

  @Test
  public void givenTemplateWithJustColonOperator_whenParse_thenCatchTemplateSyntaxException() {
    final TemplateParser.TemplateSyntaxException problem =
        assertThrowsExactly(TemplateParser.TemplateSyntaxException.class, () -> {
          new TemplateParser().parse("hello ${WORLD:} foobar", new TemplateParser.ParseHandler() {
            @Override
            public void onVariableExpressionWithDefaultValue(int index,
                String variableName, String defaultValue) {}

            @Override
            public void onVariableExpression(int index, String variableName) {}

            @Override
            public void onText(int index, String text) {}
          });
        });

    assertEquals("Invalid variable expression", problem.getMessage());
    assertEquals(6, problem.getIndex());
  }

  @Test
  public void givenTemplateWithInvalidColonOperator_whenParse_thenCatchTemplateSyntaxException() {
    final TemplateParser.TemplateSyntaxException problem =
        assertThrowsExactly(TemplateParser.TemplateSyntaxException.class, () -> {
          new TemplateParser().parse("hello ${WORLD:+} foobar", new TemplateParser.ParseHandler() {
            @Override
            public void onVariableExpressionWithDefaultValue(int index,
                String variableName, String defaultValue) {}

            @Override
            public void onVariableExpression(int index, String variableName) {}

            @Override
            public void onText(int index, String text) {}
          });
        });

    assertEquals("Invalid variable expression", problem.getMessage());
    assertEquals(6, problem.getIndex());
  }

  @Test
  public void givenTemplateBareDollar_whenParse_thenGetExpectedEvents() {
    final EventStoringTemplateParseHandler handler = new EventStoringTemplateParseHandler();

    new TemplateParser().parse("hello $WORLD $STUFF foobar", handler);

    assertEquals(List.of("onText(hello )", "onText($W)", "onText(ORLD )", "onText($S)",
        "onText(TUFF foobar)"), handler.getEvents());
  }

  @Test
  public void givenEmptyTemplate_whenParse_thenGetExpectedEvents() {
    final EventStoringTemplateParseHandler handler = new EventStoringTemplateParseHandler();

    new TemplateParser().parse("", handler);

    assertEquals(List.of("onText()"), handler.getEvents());
  }
}
