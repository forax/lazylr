package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// Represents a terminal symbol in the grammar.
///
/// Terminals are the "atoms" of the parsing process. They serve two roles:
/// * **Grammar Definition**: Used as placeholders in a [Production].
/// * **Lexical Analysis**: Created by the [Lexer] to represent actual text fragments.
///
/// This class implements [PrecedenceEntity], allowing it to participate in
/// conflict resolution when multiple productions could be applied (e.g., in
/// expressions with mixed operators).
///
/// Two terminals are considered equals if their [name]s match,
/// even if their [value]s differ.
///
/// This class is immutable, thus thread-safe.
public final class Terminal implements Symbol, PrecedenceEntity {

  /// Represents the empty string symbol (epsilon) used in grammar rules.
  /// The parser uses this terminal internally.
  public static final Terminal EPSILON = new Terminal("ε");

  /// Represents the end-of-stream marker ($), indicating no more terminals are available.
  /// The parser uses this terminal internally.
  public static final Terminal EOF = new Terminal("$");

  /// Represents a lexical error encountered during tokenization.
  ///
  /// This terminal is returned by the [Lexer] when the input character sequence
  /// at the current position does not match any provided [Token].
  public static final Terminal ERROR = new Terminal("error");

  private final String name;
  private final @Nullable String value;

  private Terminal(String name, @Nullable String value, boolean unused) {
    this.name = name;
    this.value = value;
    super();
  }

  /// Create an immutable terminal with a unique name and a value.
  ///
  /// @param name The unique identifier for the terminal.
  /// @param value The actual text fragment matched in the source.
  /// @throws NullPointerException if `name` is null or `value` is null.
  public Terminal(String name, String value) {
    Objects.requireNonNull(name);
    if (name.isEmpty()) {
      throw new IllegalArgumentException("name must not be empty");
    }
    Objects.requireNonNull(value);
    this(name, value, false);
  }

  /// Creates an immutable grammar's terminal without a specific matched value.
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
    Objects.requireNonNull(name);
    if (name.isEmpty()) {
      throw new IllegalArgumentException("name must not be empty");
    }
    this(name, null, false);
  }

  /// Returns the unique identifier for the terminal.
  ///
  /// @return The terminal's name'.
  public String name() {
    return name;
  }

  /// Returns whether the terminal has a value.
  /// This method is not intended for general use but for debugging purposes.
  /// This is unlikely that you want to mix a terminal from a grammar
  /// and a terminal from the lexer.
  ///
  /// @return `true` if the terminal has a value, `false` otherwise.
  public boolean hasValue() {
    return value != null;
  }

  /// The actual text fragment matched in the source, or
  /// throw [IllegalStateException] if the terminal is a grammar's terminal.
  ///
  /// @return The terminal's matched value.
  /// @throws IllegalStateException if the terminal has no value.
  public String value() {
    if (value == null) {
      throw new IllegalStateException("terminal has no value");
    }
    return value;
  }

  /// Compares this terminal with another object for equality.
  ///
  /// Equality is based **strictly on the name**. This allows a terminal produced
  /// by the lexer (with a value like `"42"`) to match a terminal defined in
  /// the grammar (with the name `"num"`).
  ///
  /// @param o The object to compare.
  /// @return `true` if the names match.
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