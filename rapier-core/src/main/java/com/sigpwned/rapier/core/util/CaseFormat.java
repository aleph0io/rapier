package com.sigpwned.rapier.core.util;

import static java.util.stream.Collectors.joining;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public enum CaseFormat {
  LOWER_CAMEL {
    private final Pattern SPLIT_PATTERN = Pattern.compile("(?=\\p{Lu})");

    @Override
    protected Stream<String> split(String s) {
      if (s == null)
        throw new NullPointerException();
      if (s.isEmpty())
        return Stream.empty();
      return SPLIT_PATTERN.splitAsStream(s);
    }

    @Override
    protected String join(Stream<String> parts) {
      final String uc = UPPER_CAMEL.join(parts);
      return uc.substring(0, 1).toLowerCase() + uc.substring(1);
    }

    @Override
    public String to(CaseFormat other, String s) {
      if (other == UPPER_CAMEL)
        return s.substring(0, 1).toUpperCase() + s.substring(1, s.length());;
      return super.to(other, s);
    }
  },
  LOWER_HYPHEN {
    private final Pattern SPLIT_PATTERN = Pattern.compile("-+");

    @Override
    protected Stream<String> split(String s) {
      if (s == null)
        throw new NullPointerException();
      if (s.isEmpty())
        return Stream.empty();
      return SPLIT_PATTERN.splitAsStream(s);
    }

    @Override
    protected String join(Stream<String> parts) {
      return parts.map(String::toLowerCase).collect(joining("-"));
    }

    @Override
    public String to(CaseFormat other, String s) {
      if (other == LOWER_UNDERSCORE)
        return s.replace('-', '_');
      return super.to(other, s);
    }
  },
  LOWER_UNDERSCORE {
    private final Pattern SPLIT_PATTERN = Pattern.compile("_+");

    @Override
    protected Stream<String> split(String s) {
      if (s == null)
        throw new NullPointerException();
      if (s.isEmpty())
        return Stream.empty();
      return SPLIT_PATTERN.splitAsStream(s);
    }

    @Override
    protected String join(Stream<String> parts) {
      return parts.map(String::toLowerCase).collect(joining("_"));
    }

    @Override
    public String to(CaseFormat other, String s) {
      if (other == LOWER_HYPHEN)
        return s.replace('_', '-');
      if (other == UPPER_UNDERSCORE)
        return s.toUpperCase();
      return super.to(other, s);
    }
  },
  UPPER_CAMEL {
    private final Pattern SPLIT_PATTERN = Pattern.compile("(?!^)(?=\\p{Lu})");

    @Override
    protected Stream<String> split(String s) {
      if (s == null)
        throw new NullPointerException();
      if (s.isEmpty())
        return Stream.empty();
      return SPLIT_PATTERN.splitAsStream(s);
    }

    @Override
    protected String join(Stream<String> parts) {
      return parts.map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
          .collect(joining());
    }

    @Override
    public String to(CaseFormat other, String s) {
      if (other == LOWER_CAMEL)
        return s.substring(0, 1).toLowerCase() + s.substring(1, s.length());
      return super.to(other, s);
    }
  },
  UPPER_UNDERSCORE {
    private final Pattern SPLIT_PATTERN = Pattern.compile("_+");

    @Override
    protected Stream<String> split(String s) {
      if (s == null)
        throw new NullPointerException();
      if (s.isEmpty())
        return Stream.empty();
      return SPLIT_PATTERN.splitAsStream(s);
    }

    @Override
    protected String join(Stream<String> parts) {
      return parts.map(String::toUpperCase).collect(joining("_"));
    }

    @Override
    public String to(CaseFormat other, String s) {
      if (other == LOWER_UNDERSCORE)
        return s.toLowerCase();
      return super.to(other, s);
    }
  };

  protected abstract Stream<String> split(String s);

  protected abstract String join(Stream<String> parts);

  public String to(CaseFormat other, String s) {
    if (other == null)
      throw new NullPointerException();
    if (s == null)
      throw new NullPointerException();
    if (s.isEmpty())
      return s;
    if (this == other)
      return s;
    return other.join(this.split(s));
  }
}
