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
///
/// @param name The unique identifier for this non-terminal (e.g., `"expr"`, `"stmt"`).
public record NonTerminal(String name) implements Symbol {

  /// Validates that the non-terminal has a unique name.
  ///
  /// @throws NullPointerException if the `name` is null.
  public NonTerminal {
    Objects.requireNonNull(name);
  }

  /// @return A string representation of the non-terminal.
  @Override
  public String toString() {
    return "NonTerminal(" + name + ")";
  }
}