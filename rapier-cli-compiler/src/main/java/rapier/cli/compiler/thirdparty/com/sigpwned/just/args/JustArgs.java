/*-
 * =================================LICENSE_START==================================
 * just-args
 * ====================================SECTION=====================================
 * Copyright (C) 2025 Andy Boothe
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
package rapier.cli.compiler.thirdparty.com.sigpwned.just.args;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * A single-class library for parsing command line arguments in Java 8.
 */
public final class JustArgs {
  private JustArgs() {}

  /**
   * Represents the result of parsing command line arguments.
   */
  public static class ParsedArgs {
    private final List<String> args;
    private final Map<String, List<String>> options;
    private final Map<String, List<Boolean>> flags;

    /**
     * Constructs a new ParsedArgs object.
     *
     * @param args the positional arguments
     * @param options a map from option-name to a list of string values
     * @param flags a map from flag-name to a list of boolean values
     */
    public ParsedArgs(List<String> args, Map<String, List<String>> options,
        Map<String, List<Boolean>> flags) {
      this.args = unmodifiableList(args);
      this.options = unmodifiableMapOfLists(options);
      this.flags = unmodifiableMapOfLists(flags);
    }

    /**
     * Returns the list of positional arguments
     */
    public List<String> getArgs() {
      return args;
    }

    /**
     * Returns the map of options. Keys are option names; values are the list of values provided for
     * that option.
     */
    public Map<String, List<String>> getOptions() {
      return options;
    }

    /**
     * Returns the map of flags. Keys are flag names; values are the list of boolean occurrences
     * (e.g., repeated flags).
     */
    public Map<String, List<Boolean>> getFlags() {
      return flags;
    }

    @Override
    public int hashCode() {
      return Objects.hash(args, flags, options);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      ParsedArgs other = (ParsedArgs) obj;
      return Objects.equals(args, other.args) && Objects.equals(flags, other.flags)
          && Objects.equals(options, other.options);
    }

    @Override
    public String toString() {
      return "ParsedArgs [args=" + args + ", options=" + options + ", flags=" + flags + "]";
    }
  }

  /**
   * Thrown when a syntactic error in the arguments is encountered.
   */
  @SuppressWarnings("serial")
  public static class IllegalSyntaxException extends IllegalArgumentException {
    private final int index;

    public IllegalSyntaxException(int index, String message) {
      super(message);
      this.index = index;
      if (index < 0)
        throw new IllegalArgumentException("index must be at least 0");
    }

    public int getIndex() {
      return index;
    }
  }

  /**
   * Parses Java command line arguments.
   *
   * <p>
   * An "option" is a switch that takes a value. A switch can be short (e.g., {@code -o}) or long
   * (e.g., {@code --option}). A value can always be given as the next argument after the switch. A
   * value can also be given to a long switch in attached style using an {@code =} sign (e.g.,
   * {@code --option=value}).
   *
   * <p>
   * A "flag" is a switch that has an implicit boolean value. A flag is represented by a short
   * switch or a long switch. A positive flag has the value {@link Boolean#TRUE} and a negative flag
   * has the value {@link Boolean#FALSE}. They are syntactically equivalent, so names should be
   * chosen carefully. By convention, short positive flags are lowercase (e.g., {@code -x}) whereas
   * short negative flags are uppercase (e.g., {@code -X}), whereas long negative flags start with a
   * negative prefix (e.g., {@code --no-flag}), whereas long positive flags do not (e.g.,
   * {@code --flag}).
   *
   * <p>
   * Short flags, positive or negative, can appear in "batches." For example, {@code -abc} is
   * equivalent to {@code -a -b -c}. The last short switch in a batch can be an option that takes a
   * value (e.g., {@code -abc value} is equivalent to {@code -a -b -c value}), but all other short
   * switches in the batch must be flags.
   *
   * <p>
   * A positional argument is any value which is not a switch or a parameter to an option. The
   * parser supports the interleaving of switches and parameters throughout the command arguments.
   * The special token {@code --} can be used to indicate that all subsequent arguments are
   * positional arguments, even if they look like switches.
   *
   * @param args the command line arguments to parse, generally from the main function
   * @param shortOptionNames a map from valid short option names to the string to use to collect
   *        values into the options result. Keys in shortOptionNames must not appear in any other
   *        option or flag name map in character form. If a short option name does not appear in
   *        shortOptionNames, then it is not a valid short option.
   * @param longOptionNames a map from valid long option names to the string to use to collect
   *        values into the options result. Keys in longOptionNames must not appear in any other
   *        option or flag name map in string form. If a long option name does not appear in
   *        longOptionNames, then it is not a valid long option.
   * @param shortPositiveFlagNames a map from valid short positive flag names to the string to use
   *        to collect values into the flags result. Keys in shortPositiveFlagNames must not appear
   *        in any other option or flag name map in character form. If a short positive flag name
   *        does not appear in shortPositiveFlagNames, then it is not a valid short positive flag
   *        name. A positive flag takes the value of {@link Boolean#TRUE}.
   * @param longPositiveFlagNames a map from valid long positive flag names to the string to use to
   *        collect values into the flags result. Keys in longPositiveFlagNames must not appear in
   *        any other option or flag name map in string form. If a long positive flag name does not
   *        appear in longPositiveFlagNames, then it is not a valid long positive flag name. A
   *        positive flag takes the value of {@link Boolean#TRUE}.
   * @param shortNegativeFlagNames a map from valid short negative flag names to the string to use
   *        to collect values into the flags result. Keys in shortNegativeFlagNames must not appear
   *        in any other option or flag name map in character form. If a short negative flag name
   *        does not appear in shortNegativeFlagNames, then it is not a valid short negative flag
   *        name. A negative flag takes the value of {@link Boolean#FALSE}.
   * @param longNegativeFlagNames a map from valid long negative flag names to the string to use to
   *        collect values into the flags result. Keys in longNegativeFlagNames must not appear in
   *        any other option or flag name map in string form. If a long negative flag name does not
   *        appear in longNegativeFlagNames, then it is not a valid long negative flag name. A
   *        negative flag takes the value of {@link Boolean#FALSE}.
   * @return the parsed arguments
   *
   * @throws NullPointerException if any argument is null
   * @throws IllegalArgumentException if any short option or flag name appears in more than one of
   *         shortOptionNames, shortPositiveFlagNames, and shortNegativeFlagNames; or if any long
   *         option or flag name appears in more than one of longOptionNames, longPositiveFlagNames,
   *         and longNegativeFlagNames.
   * @throws IllegalSyntaxException if any short switch is not an element in shortOptionNames,
   *         shortPositiveFlagNames, or shortNegativeFlagNames; or if any long switch is not an
   *         element in longOptionNames, longPositiveFlagNames, or longNegativeFlagNames; or if any
   *         option switch does not have a value; or if any flag switch has a value.
   */
  public static ParsedArgs parseArgs(List<String> args, Map<Character, String> shortOptionNames,
      Map<String, String> longOptionNames, Map<Character, String> shortPositiveFlagNames,
      Map<String, String> longPositiveFlagNames, Map<Character, String> shortNegativeFlagNames,
      Map<String, String> longNegativeFlagNames) {
    if (args == null)
      throw new NullPointerException();
    if (shortOptionNames == null)
      throw new NullPointerException();
    if (longOptionNames == null)
      throw new NullPointerException();
    if (shortPositiveFlagNames == null)
      throw new NullPointerException();
    if (longPositiveFlagNames == null)
      throw new NullPointerException();
    if (shortNegativeFlagNames == null)
      throw new NullPointerException();
    if (longNegativeFlagNames == null)
      throw new NullPointerException();

    final Set<Character> duplicateShortKeys = duplicates(shortOptionNames.keySet(),
        shortPositiveFlagNames.keySet(), shortNegativeFlagNames.keySet());
    if (!duplicateShortKeys.isEmpty())
      throw new IllegalArgumentException("Duplicate short switch keys: " + duplicateShortKeys);

    final Set<String> duplicateLongKeys = duplicates(longOptionNames.keySet(),
        longPositiveFlagNames.keySet(), longNegativeFlagNames.keySet());
    if (!duplicateLongKeys.isEmpty())
      throw new IllegalArgumentException("Duplicate long switch keys: " + duplicateLongKeys);

    // Prepare result holders
    List<String> positionalArgs = new ArrayList<>();
    Map<String, List<String>> options = new LinkedHashMap<>();
    Map<String, List<Boolean>> flags = new LinkedHashMap<>();

    // // Helper to add an option value to the map
    // // e.g. if the "optionName" is "output", store the value in options.get("output")
    // java.util.function.BiConsumer<String, String> addOption = (optionName, value) -> {
    // options.computeIfAbsent(optionName, k -> new ArrayList<>()).add(value);
    // };
    //
    // // Helper to add a flag occurrence
    // // e.g. if the "flagName" is "verbose", store true in flags.get("verbose")
    // java.util.function.BiConsumer<String, Boolean> addFlag = (flagName, boolVal) -> {
    // flags.computeIfAbsent(flagName, k -> new ArrayList<>()).add(boolVal);
    // };

    boolean separated = false;
    final ListIterator<String> iterator = args.listIterator();
    while (iterator.hasNext()) {
      final String arg = iterator.next();

      // If the argument is exactly `--`, all subsequent are positional
      if ("--".equals(arg) && separated == false) {
        separated = true;
        continue;
      }

      // If we've already encountered `--`, everything is a positional arg
      if (separated) {
        positionalArgs.add(arg);
        continue;
      }

      // Check if it looks like a short or long switch
      if (arg.startsWith("--")) {
        // LONG SWITCH
        // Could be a flag or an option
        // Also check if it's in the form --key=value
        final String switchName;
        final String attachedValue;
        final int equalsAt = arg.indexOf('=', 2);
        if (equalsAt != -1) {
          switchName = arg.substring(2, equalsAt);
          attachedValue = arg.substring(equalsAt + 1, arg.length());
        } else {
          switchName = arg.substring(2, arg.length());
          attachedValue = null;
        }


        // Now see if it's a valid option name
        if (longOptionNames.containsKey(switchName)) {
          // It's an option
          final String optionName = longOptionNames.get(switchName);

          // If we have an attached value (--key=value), use that. Otherwise, use next argument.
          final String value;
          if (attachedValue != null) {
            value = attachedValue;
          } else {
            if (!iterator.hasNext()) {
              // Options must have a value. If there is none, that's a syntax error.
              throw new IllegalSyntaxException(iterator.previousIndex(),
                  "Option --" + switchName + " requires a value but none given");
            }
            value = iterator.next();
          }

          options.computeIfAbsent(optionName, k -> new ArrayList<>()).add(value);
        } else if (longPositiveFlagNames.containsKey(switchName)) {
          if (attachedValue != null) {
            // Flags do not take a value
            throw new IllegalSyntaxException(iterator.previousIndex(),
                "Flag --" + switchName + " does not take a value");
          }

          final String flagName = longPositiveFlagNames.get(switchName);

          flags.computeIfAbsent(flagName, k -> new ArrayList<>()).add(Boolean.TRUE);
        } else if (longNegativeFlagNames.containsKey(switchName)) {
          if (attachedValue != null) {
            // Flags do not take a value
            throw new IllegalSyntaxException(iterator.previousIndex(),
                "Flag --" + switchName + " does not take a value");
          }

          final String flagName = longPositiveFlagNames.get(switchName);

          flags.computeIfAbsent(flagName, k -> new ArrayList<>()).add(Boolean.FALSE);
        } else {
          // Not recognized
          throw new IllegalSyntaxException(iterator.previousIndex(),
              "Unrecognized long switch --" + switchName);
        }
      } else if (arg.startsWith("-") && arg.length() > 1) {
        // SHORT SWITCH(ES)
        // e.g. -abc or -o
        // We'll parse each character except possibly the last if it's an option
        final CharacterIterator itc = new StringCharacterIterator(arg);

        // If there's only 1 char, it's either a flag or an option
        // If multiple chars, all but last must be flags
        // Yes, this approach DOES skip the first char. Yes, it is tested. No, it is not a typo.
        for (char switchName = itc.next(); switchName != CharacterIterator.DONE; switchName =
            itc.next()) {
          // If this char is recognized as an option (and it's the last char in the batch),
          // then we parse the next argument for the option value
          if (shortOptionNames.containsKey(switchName)) {
            final boolean isLastChar = itc.next() == CharacterIterator.DONE;
            itc.previous(); // Go back to the last char

            if (!isLastChar) {
              throw new IllegalSyntaxException(iterator.previousIndex(),
                  "Option -" + switchName + " must be the last character in the batch");
            }

            // It's an option
            final String optionName = shortOptionNames.get(switchName);

            // We look for the next argument
            if (!iterator.hasNext()) {
              throw new IllegalSyntaxException(iterator.previousIndex(),
                  "Option -" + switchName + " requires a value but none given");
            }

            final String nextVal = iterator.next();

            options.computeIfAbsent(optionName, k -> new ArrayList<>()).add(nextVal);
          } else if (shortPositiveFlagNames.containsKey(switchName)) {
            final String flagName = shortPositiveFlagNames.get(switchName);
            flags.computeIfAbsent(flagName, k -> new ArrayList<>()).add(Boolean.TRUE);
          } else if (shortNegativeFlagNames.containsKey(switchName)) {
            final String flagName = shortNegativeFlagNames.get(switchName);
            flags.computeIfAbsent(flagName, k -> new ArrayList<>()).add(Boolean.FALSE);
          } else {
            throw new IllegalSyntaxException(iterator.previousIndex(),
                "Unrecognized short switch -" + switchName);
          }
        }
      } else {
        // POSITIONAL ARG
        positionalArgs.add(arg);
      }
    }

    return new ParsedArgs(positionalArgs, options, flags);
  }

  @SafeVarargs
  private static <T> Set<T> duplicates(Set<T>... sets) {
    return Arrays.stream(sets).flatMap(Set::stream)
        .collect(groupingBy(Function.identity(), counting())).entrySet().stream()
        .filter(e -> e.getValue() > 1L).map(Map.Entry::getKey).collect(toSet());
  }

  /**
   * Returns an unmodifiable copy of the given map of lists.
   * 
   * @param mapOfLists the map of lists to copy
   * @return an unmodifiable copy of the given map of lists
   */
  private static <T> Map<String, List<T>> unmodifiableMapOfLists(Map<String, List<T>> mapOfLists) {
    return unmodifiableMap(
        mapOfLists.entrySet().stream().map(e -> entry(e.getKey(), copyOf(e.getValue())))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
              // This should never happen, since we started with a valid map
              throw new IllegalArgumentException("map contains duplicate keys");
            }, LinkedHashMap::new)));
  }

  /**
   * Returns an unmodifiable copy of the given list.
   * 
   * @param <T> the type of the elements in the list
   * @param xs the list to copy
   * @return an unmodifiable copy of the given list
   * 
   * @throws NullPointerException if the list is null
   */
  private static <T> List<T> copyOf(List<T> xs) {
    if (xs == null)
      throw new NullPointerException();
    return Collections.unmodifiableList(new ArrayList<>(xs));
  }

  /**
   * Creates a new {@link Map.Entry} with the given key and value.
   * 
   * @param <K> the type of the key
   * @param <V> the type of the value
   * @param key the key
   * @param value the value
   * @return a new {@link Map.Entry} with the given key and value
   */
  private static <K, V> Map.Entry<K, V> entry(K key, V value) {
    return new java.util.AbstractMap.SimpleImmutableEntry<>(key, value);
  }
}
