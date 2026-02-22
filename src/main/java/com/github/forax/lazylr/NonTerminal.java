package com.github.forax.lazylr;

import java.util.Objects;

/// Represents a non-terminal symbol in a context-free grammar.
///
/// A non-terminal is a placeholder for a sequence of [Symbol]s (terminals or other
/// non-terminals). It defines the recursive structure of the language and serves
/// as the left-hand side of one or more [Production]s.
///
/// ### Usage Example
/// ```java
/// var plus = new Terminal("+");
/// var expr = new NonTerminal("expr");
/// var prod = new Production(expr, List.of(expr, plus, expr));
/// ```
public final class NonTerminal implements Symbol {
  private final String name;

  /// Create an immutable non-terminal from a unique name.
  ///
  /// @param name The unique identifier for the non-terminal.
  /// @throws NullPointerException if the `name` is null.
  public NonTerminal(String name) {
    Objects.requireNonNull(name);
    this.name = name;
    super();
  }

  /// Returns the unique identifier for this non-terminal (e.g., `"expr"`, `"stmt"`).
  ///
  /// @return The unique identifier for this non-terminal.
  public String name() {
    return name;
  }

  /// @return A hash code derived from the non-terminal's name.
  @Override
  public int hashCode() {
    return name.hashCode();
  }

  /// Compares this non-terminal with another object for equality.
  @Override
  public boolean equals(Object o) {
    return o instanceof NonTerminal nonTerminal && name.equals(nonTerminal.name);
  }

  /// @return A string representation of the non-terminal.
  @Override
  public String toString() {
    return "NonTerminal(" + name + ")";
  }
}