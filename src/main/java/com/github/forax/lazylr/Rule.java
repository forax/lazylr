package com.github.forax.lazylr;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// Defines a lexical rule for the [Lexer].
///
/// A rule consists of a regular expression and an optional name.
/// During tokenization, the lexer attempts to match the input string against
/// these patterns to produce [Terminal] tokens.
///
/// ### Lexing Behavior
/// * **Pattern Matching**: The [regex()] must follow standard Java
///   [java.util.regex.Pattern] syntax.
/// * **Token Creation**: When a match is found and the rule has a [name], a
///   new [Terminal] is created using that name and the matched text.
/// * **Ignorable Tokens**: If a rule has no name (is `null`), it is considered
///   an "ignorable token." The matched text is consumed by the [Lexer] but
///   no [Terminal] is produced for the output stream.
/// * **Priority**: If multiple rules can match at the same position, the
///   rule that appears **first** in the list provided to the lexer takes precedence.
///
/// ### Examples
/// ```java
/// new Rule("num", "[0-9]+"); // Produces a "num" terminal
/// new Rule("[ ]+");          // Ignorable: matches spaces but produces no terminal
/// ```
public final class Rule {
  private static void checkPattern(String regex) {
    Pattern pattern;
    try {
      pattern = Pattern.compile(regex);
    } catch(PatternSyntaxException e) {
      throw new IllegalArgumentException("invalid pattern " + regex, e);
    }
    var matcher = pattern.matcher("");
    if (matcher.groupCount() != 0) {
      throw new IllegalArgumentException("pattern " + regex + " should not use group " + regex);
    }
  }

   private final String name;
   private final String regex;

   private Rule(String name, String regex, boolean unused) {
     this.name = name;
     this.regex = regex;
     super();
   }

  /// Creates a new Rule.
  ///
  /// @param name  The symbolic name of the token. If `null`, the rule is
  ///              treated as ignorable and its matches will be skipped by the lexer.
  /// @param regex The regular expression pattern to match.
  /// @throws NullPointerException if name or regex is null.
  /// @throws IllegalArgumentException if the pattern is malformed or contains a group
  public Rule(String name, String regex) {
    Objects.requireNonNull(name);
    Objects.requireNonNull(regex);
    checkPattern(regex);
    this(name, regex, false);
  }

  /// Creates an ignorable rule, with no name.
  ///
  /// Matches against this rule will be consumed by the [Lexer] but will not
  /// produce a [Terminal] in the terminal stream.
  ///
  /// @param regex The regular expression pattern to match and skip.
  /// @throws IllegalArgumentException if the pattern is malformed or contains a group
  public Rule(String regex) {
    Objects.requireNonNull(regex);
    checkPattern(regex);
    this(null, regex, false);
  }

  /// Returns The identifier for the token type or `null` if the rule
  /// is treated as ignorable.
  ///
  /// @return The symbolic name of the token or `null`.
  public String name() {
    return name;
  }

  /// Returns The regular expression pattern used to match input text.
  ///
  /// @return The regular expression pattern.
  public String regex() {
    return regex;
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
    return 31 * (31 + (name == null ? 0 : name.hashCode())) + regex.hashCode();
  }

  /// Compares this rule with another object for equality.
  @Override
  public boolean equals(Object o) {
    return o instanceof Rule rule && regex.equals(rule.regex) && Objects.equals(name, rule.name);
  }

  /// @return A string representation of the rule.
  @Override
  public String toString() {
    if (name == null) {
      return "Rule(" + regex + ")";
    }
    return "Rule(" + name + ", " + regex + ")";
  }
}
