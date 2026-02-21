package com.github.forax.lazylr;

import java.util.Objects;

/// Defines a lexical rule for the [Lexer].
///
/// A rule consists of a symbolic **name** and a **regular expression**.
/// During tokenization, the lexer attempts to match the input string against
/// these patterns to produce [Terminal] tokens.
///
/// ### Lexing Behavior
/// * **Pattern Matching**: The [regex()] must follow standard Java
///   [java.util.regex.Pattern] syntax.
/// * **Token Creation**: When a match is found, a new [Terminal] is created
///   using the rule's name and the matched text as its value.
/// * **Priority**: If the lexer supports multiple rules, it typically follows
///   the **longest match** rule, or the **order of definition** to break ties.
///
/// ### Example
/// ```java
/// new Rule("num", "[0-9]+")
/// ```
///
/// @param name The identifier for the token type (must match a name used in a [terminal] of the grammar).
/// @param regex The regular expression pattern used to match input text.
///
/// @see Lexer
/// @see Terminal
public record Rule(String name, String regex) {

  /// Creates a rule.
  ///
  /// @throws NullPointerException if [name] or [regex] is null.
  public Rule {
    Objects.requireNonNull(name);
    Objects.requireNonNull(regex);
  }
}
