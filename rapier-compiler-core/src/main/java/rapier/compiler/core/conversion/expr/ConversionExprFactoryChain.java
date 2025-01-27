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
package rapier.compiler.core.conversion.expr;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import java.util.List;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import rapier.compiler.core.ConversionExprFactory;

public class ConversionExprFactoryChain implements ConversionExprFactory {
  private final List<ConversionExprFactory> links;

  public ConversionExprFactoryChain(ConversionExprFactory... links) {
    this(asList(links));
  }

  public ConversionExprFactoryChain(List<ConversionExprFactory> links) {
    this.links = unmodifiableList(links);
  }

  @Override
  public Optional<String> generateConversionExpr(TypeMirror targetType, String sourceValue) {
    for (ConversionExprFactory link : getLinks()) {
      final Optional<String> maybeExpr =
          link.generateConversionExpr(targetType, sourceValue);
      if (maybeExpr.isPresent())
        return maybeExpr;
    }
    return Optional.empty();
  }

  private List<ConversionExprFactory> getLinks() {
    return links;
  }
}
