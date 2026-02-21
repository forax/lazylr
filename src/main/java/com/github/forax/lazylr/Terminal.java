package com.github.forax.lazylr;

import java.util.Objects;

/// Represents a terminal symbol in a formal grammar.
///
/// A terminal is a basic symbol from which strings are formed by the [Lexer]
/// and which cannot be further broken down by the rules of the [Grammar].
///
/// This record implements [Symbol] for grammar construction and
/// [PrecedenceEntity] for resolving operator priority during parsing.
///
/// @param name The unique identifier for the terminal (e.g., "ID", "PLUS").
/// @param value The actual text fragment matched in the source code, null otherwise.
public record Terminal(String name, String value) implements Symbol, PrecedenceEntity {

  /// Represents the empty string symbol (epsilon) in grammar rules.
  public static final Terminal EPSILON = new Terminal("ε");

  /// Represents the end-of-file or end-of-stream marker.
  public static final Terminal EOF = new Terminal("$");

  /// A Terminal with non-null name and a nullable value.
  ///
  /// This is typically a terminal emitted by the [Lexer] during
  /// the [tokenization][Lexer#tokenize(CharSequence)].
  ///
  /// @throws NullPointerException if the name is null.
  public Terminal {
    Objects.requireNonNull(name);
  }

  /// Convenience constructor for creating a terminal without a specific matched value.
  ///
  /// This is typically a terminal used when defining
  /// [grammar productions][Grammar#Grammar(NonTerminal, java.util.List)].
  ///
  /// @param name The unique identifier for the terminal.
  public Terminal(String name) {
    this(name, null);
  }

  /// Compares this terminal with another object for equality.
  ///
  /// Two terminals are considered equal if they share the same [name],
  /// regardless of their specific matched [value].
  ///
  /// @param o The object to compare with.
  /// @return true if the objects are functionally identical terminals.
  @Override
  public boolean equals(Object o) {
    return o instanceof Terminal terminal && name.equals(terminal.name);
  }

  /// Generates a hash code based solely on the terminal's name.
  ///
  /// @return A hash code based solely on the terminal's name.
  @Override
  public int hashCode() {
    return name.hashCode();
  }

  /// Returns a string representation of the terminal.
  ///
  /// @return A string representation of the terminal.
  @Override
  public String toString() {
    return "Terminal(" + name + ")";
  }
}