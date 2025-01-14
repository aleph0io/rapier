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
package rapier.compiler.core.util;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class MoreMaps {
  private MoreMaps() {}

  /**
   * Merges all the values of the second map into the first map, using the provided merge function.
   *
   * @param <K> the type of keys in the first map
   * @param <V> the type of values in the first map
   * @param xs the first map with types K and V
   * @param ys the second map with the most general types compatible with K and V
   * @return a new map containing all entries from map1 and map2 (map2 values overwrite map1 values
   *         if keys overlap)
   */
  public static <K, V> Map<K, V> mergeAll(Map<K, V> xs, Map<? extends K, ? extends V> ys,
      BiFunction<? super V, ? super V, ? extends V> mergeFunction) {
    return mergeAll(xs, ys, mergeFunction, Function.identity());
  }

  /**
   * Merges all the values of the second map into the first map, using the provided merge function.
   *
   * @param <K> the type of keys in the first map
   * @param <V> the type of values in the first map
   * @param xs the first map with types K and V
   * @param ys the second map with the most general types compatible with K and V
   * @param mergeFunction the function used to merge the values from xs and ys, if both maps are
   *        populated for a given key
   * @param finishFunction the function used to finish the value from ys before it is inserted into
   *        xs, if only ys is populated for a given key
   * @return a new map containing all entries from map1 and map2 (map2 values overwrite map1 values
   *         if keys overlap)
   */
  public static <K, V> Map<K, V> mergeAll(Map<K, V> xs, Map<? extends K, ? extends V> ys,
      BiFunction<? super V, ? super V, ? extends V> mergeFunction,
      Function<? super V, ? extends V> finishFunction) {
    ys.forEach((k, yv) -> {
      V xv = xs.get(k);
      if (xv != null) {
        xs.put(k, mergeFunction.apply(xv, yv));
      } else {
        xs.put(k, finishFunction.apply(yv));
      }
    });
    return xs;
  }
}
