package com.github.forax.lazylr;

import java.util.Objects;

/// Defines the priority and grouping rules for a [PrecedenceEntity].
///
/// Precedence is used by the [Parser] to resolve shift/reduce conflicts in
/// ambiguous grammars, such as mathematical expressions.
///
/// ### Conflict Resolution Rules
/// * **Level**: Higher [level()] values indicate stronger binding (e.g., `*` has
///    a higher level than `+`).
/// * **Associativity**: When levels are equal, the [Associativity] determines
///    grouping:
///    * `LEFT`: `a + b + c` is parsed as `(a + b) + c`.
///    * `RIGHT`: `a ^ b ^ c` is parsed as `a ^ (b ^ c)`.
///
/// @param level A non-negative integer representing priority. Higher is stronger.
/// @param assoc The direction in which operators of the same level group.
public record Precedence(int level, Associativity assoc) {

  /// Specifies the grouping direction for operators with the same precedence level.
  public enum Associativity {
    /// Groups from left to right (e.g., `a - b - c` is parsed as `(a - b) - c`).
    LEFT,
    /// Groups from right to left (e.g.,`a = b = c` is parsed as `a = (b = c)`).
    RIGHT
  }

  /// Creates a precedence.
  ///
  /// @throws IllegalArgumentException if `level` is negative.
  /// @throws NullPointerException if `assoc` is null.
  public Precedence {
    if (level < 0) {
      throw new IllegalArgumentException("Precedence level must be non-negative");
    }
    Objects.requireNonNull(assoc);
  }
}