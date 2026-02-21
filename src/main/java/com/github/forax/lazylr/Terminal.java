package com.github.forax.lazylr;

import java.util.Objects;

/// Represents a terminal symbol (token) in the grammar.
///
/// Terminals are the "atoms" of the parsing process. They serve two roles:
/// * **Grammar Definition**: Used as placeholders in a [Production].
/// * **Lexical Analysis**: Created by the [Lexer] to represent actual text fragments.
///
/// This record implements [PrecedenceEntity], allowing it to participate in
/// conflict resolution when multiple productions could be applied (e.g., in
/// expressions with mixed operators).
///
/// In this library, two terminals are considered equals if their [name]s
/// match, even if their [value]s differ.
///
/// @param name  The unique identifier for the terminal (e.g., `"num"`, `"+"`).
/// @param value The actual text fragment matched in the source, or `null` if this
///              is a grammar template.
public record Terminal(String name, String value) implements Symbol, PrecedenceEntity {

  /// Represents the empty string symbol (epsilon) used in grammar rules.
  /// This terminal is used internally by the grammar.
  public static final Terminal EPSILON = new Terminal("ε");

  /// Represents the end-of-stream marker ($), indicating no more tokens are available.
  /// This terminal is used internally by the grammar.
  public static final Terminal EOF = new Terminal("$");

  /// Validates that every terminal has a non-null identifier.
  ///
  /// @throws NullPointerException if `name` is null.
  public Terminal {
    Objects.requireNonNull(name, "Terminal name cannot be null");
  }

  /// Creates a template terminal without a specific matched value.
  ///
  /// This constructor is typically used when defining a [Grammar]:
  /// ```java
  /// var plus = new Terminal("+");
  /// var expr = new NonTerminal("expr");
  /// var prod = new Production(expr, List.of(expr, plus, expr));
  /// ```
  ///
  /// @param name The unique identifier for the terminal.
  public Terminal(String name) {
    this(name, null);
  }

  /// Compares this terminal with another object for equality.
  ///
  /// Equality is based **strictly on the name**. This allows a terminal produced
  /// by the lexer (with a value like `"42"`) to match a terminal defined in
  /// the grammar (with the name `"num"`).
  ///
  /// @param o The object to compare.
  /// @return {@code true} if the names match.
  @Override
  public boolean equals(Object o) {
    return o instanceof Terminal terminal && name.equals(terminal.name);
  }

  /// @return A hash code derived from the terminal's name.
  @Override
  public int hashCode() {
    return name.hashCode();
  }

  /// @return A string representation of the terminal.
  @Override
  public String toString() {
    return "Terminal(" + name + ")";
  }
}