/*-
 * =================================LICENSE_START==================================
 * rapier-core
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
package rapier.core.util;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.unmodifiableSet;
import java.util.HashSet;
import java.util.Set;

public final class MoreSets {
  private MoreSets() {}

  /**
   * Returns an unmodifiable copy of the specified set.
   * 
   * @param <T> the type of the elements
   * @param set the set to copy
   * @return an unmodifiable copy of the specified set
   * 
   * @throws NullPointerException if {@code set} is {@code null}
   */
  public static <T> Set<T> copyOf(Set<T> set) {
    if (set == null)
      throw new NullPointerException();
    return unmodifiableSet(new HashSet<>(set));
  }

  /**
   * Returns an unmodifiable empty set.
   * 
   * @param <T> the type of the elements
   * @return an unmodifiable empty set
   */
  public static <T> Set<T> of() {
    return emptySet();
  }

  /**
   * Returns an unmodifiable set containing a single element.
   * 
   * @param <T> the type of the element
   * @param element the element
   * @return an unmodifiable set containing a single element
   */
  public static <T> Set<T> of(T element) {
    return singleton(element);
  }

  /**
   * Returns an unmodifiable set containing the specified elements.
   * 
   * @param <T> the type of the elements
   * @param firstElement the first element
   * @param secondElement the second element
   * @param moreElements the remaining elements
   * @return an unmodifiable set containing the specified elements
   */
  @SuppressWarnings("unchecked")
  public static <T> Set<T> of(T firstElement, T secondElement, T... moreElements) {
    final Set<T> result = new HashSet<>();
    result.add(firstElement);
    result.add(secondElement);
    for (T element : moreElements)
      result.add(element);
    return unmodifiableSet(result);
  }

  /**
   * Returns a new unmodifiable set containing the elements that are present in {@code a} but not in
   * {@code b}. Subsequent changes to the input sets will not be reflected in the result.
   * 
   * @param <T> the type of the elements
   * @param a the first set
   * @param b the second set
   * @return a new unmodifiable set containing the elements that are present in {@code a} but not in
   *         {@code b}
   * 
   * @throws NullPointerException if {@code a} is {@code null}
   * @throws NullPointerException if {@code b} is {@code null}
   */
  public static <T> Set<T> difference(Set<T> a, Set<T> b) {
    if (a == null)
      throw new NullPointerException();
    if (b == null)
      throw new NullPointerException();
    if (a == b)
      return emptySet();
    if (a.isEmpty())
      return emptySet();
    if (b.isEmpty())
      return unmodifiableSet(new HashSet<>(a));
    final Set<T> result = new HashSet<>(a);
    result.removeAll(b);
    return unmodifiableSet(result);
  }

  /**
   * Returns a new unmodifiable set containing the elements that are present in both {@code a} and
   * {@code b}. Subsequent changes to the input sets will not be reflected in the result.
   * 
   * @param <T> the type of the elements
   * @param a the first set
   * @param b the second set
   * @return a new unmodifiable set containing the elements that are present in both {@code a} and
   *         {@code b}
   */
  public static <T> Set<T> intersection(Set<T> a, Set<T> b) {
    if (a == null)
      throw new NullPointerException();
    if (b == null)
      throw new NullPointerException();
    if (a == b)
      return unmodifiableSet(new HashSet<>(a));
    if (a.isEmpty())
      return emptySet();
    if (b.isEmpty())
      return emptySet();
    final Set<T> result = new HashSet<>(a);
    result.retainAll(b);
    return unmodifiableSet(result);
  }
}
