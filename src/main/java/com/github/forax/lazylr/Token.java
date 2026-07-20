package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// Defines a lexical rule for the [Lexer].
///
/// A token can be:
/// - A **named token**, which has a name and a regular expression.
/// - An **unnamed token**, which has only a regular expression.
///
/// The [regex()] must follow standard Java [java.util.regex.Pattern] syntax
/// and must not match the empty string. This constraint prevents infinite loops
/// during [Lexer] tokenization.
///
/// A named token is created using the 2-argument constructor `Token(name, regex)`.
/// An unnamed token is created using the 1-argument constructor `Token(regex)`.
///
/// During tokenization, the lexer attempts to match the input string against
/// these patterns to produce [Terminal] tokens.
///
/// When a token matches:
/// - If it has a [#name()], a new [Terminal] is created using that name and
///   the matched text as its value.
/// - If it has no name, it is considered **ignorable** (e.g., whitespace or
///   comments) and is skipped.
///
/// Example:
/// ```java
///   var number     = new Token("NUMBER", "[0-9]+");
///   var whitespace = new Token("[ \\t\\n]+");        // ignorable token
/// ```
///
/// This class is immutable and thread-safe.
public final class Token {
  private static Pattern asPattern(String regex) {
    try {
      return Pattern.compile(regex);
    } catch(PatternSyntaxException e) {
      throw new IllegalArgumentException("invalid pattern " + regex, e);
    }
  }

  private static void checkEmptyInput(Pattern pattern) {
    var matcher = pattern.matcher("");
    if (matcher.matches()) {
      throw new IllegalArgumentException("regex '" + pattern.pattern() + "' matches empty input");
    }
  }

   private final @Nullable String name;
   final Pattern pattern;

   private Token(@Nullable String name, Pattern pattern) {
     this.name = name;
     this.pattern = pattern;
     super();
   }

  /// Creates a new Rule with a name.
  ///
  /// @param name  The symbolic name of the token.
  /// @param regex The regular expression pattern to match.
  /// @throws NullPointerException if the name or the regex is null.
  /// @throws IllegalArgumentException if the name is empty, or if the pattern is malformed
  ///         or matches the empty input.
  public Token(String name, String regex) {
    Objects.requireNonNull(name);
    if (name.isEmpty()) {
      throw new IllegalArgumentException("name must not be empty");
    }
    Objects.requireNonNull(regex);
    var pattern = asPattern(regex);
    checkEmptyInput(pattern);
    this(name, pattern);
  }

  /// Creates an ignorable rule, with no name.
  ///
  /// Matches against this rule will be consumed by the [Lexer] but will not
  /// produce a [Terminal] in the terminal stream.
  ///
  /// @param regex The regular expression pattern to match and skip.
  /// @throws IllegalArgumentException if the pattern is malformed or matches the empty input.
  public Token(String regex) {
    Objects.requireNonNull(regex);
    var pattern = asPattern(regex);
    checkEmptyInput(pattern);
    this(null, pattern);
  }

  /// Returns The identifier for the token type or `null` if the rule
  /// is treated as ignorable.
  ///
  /// @return The symbolic name of the token or `null`.
  public @Nullable String name() {
    return name;
  }

  /// Return The pattern automata.
  /// @return The pattern automata.
  Pattern pattern() {
    // Design Note: this method is used only in the constructor of Tokenizer
    return pattern;
  }

  /// Returns The regular expression pattern used to match input text.
  ///
  /// @return The regular expression pattern.
  public String regex() {
    return pattern.pattern();
  }

  /// Returns whether this rule is considered ignorable.
  ///
  /// @return {@code true} if matches should not produce a [Terminal].
  public boolean isIgnorable() {
    return name == null;
  }

  /// @return A hash code derived from the rule's name and the rule's regex.
  @Override
  public int hashCode() {
    // Design Note: String.hashCode() already cache the hashCode, so
    // there is no need to cache the result of hashCode here.
    return 31 * (31 + (name == null ? 0 : name.hashCode())) + pattern.pattern().hashCode();
  }

  /// Compares this rule with another object for equality.
  @Override
  public boolean equals(Object o) {
    return o instanceof Token token &&
        pattern.pattern().equals(token.pattern.pattern()) &&
        Objects.equals(name, token.name);
  }

  /// @return A string representation of the rule.
  @Override
  public String toString() {
    if (name == null) {
      return "Token(" + pattern.pattern() + ")";
    }
    return "Token(" + name + ", " + pattern.pattern() + ")";
  }
}
