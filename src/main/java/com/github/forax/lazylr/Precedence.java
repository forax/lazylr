package com.github.forax.lazylr;

import java.util.HashMap;
import java.util.Map;
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
/// @param associativity The direction in which operators of the same level group.
///
/// This class is immutable, thus thread-safe.
public record Precedence(int level, Associativity associativity) {

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
  /// @throws NullPointerException if `associativity` is null.
  public Precedence {
    if (level < 0) {
      throw new IllegalArgumentException("Precedence level must be non-negative");
    }
    Objects.requireNonNull(associativity);
  }

  /// Returns a copy of [precedenceMap] extended with an inferred [Precedence]
  /// for each [Production] not already present, derived from its rightmost terminal
  /// with known precedence.
  static Map<PrecedenceEntity, Precedence> complete(Grammar grammar, Map<? extends PrecedenceEntity, ? extends Precedence> precedenceMap) {
    var newPrecedenceMap = new HashMap<PrecedenceEntity, Precedence>(precedenceMap);
    for (var production : grammar.productions()) {
      newPrecedenceMap.computeIfAbsent(production, _ -> computePrecedence(production, newPrecedenceMap));
    }
    return newPrecedenceMap;
  }

  private static Precedence computePrecedence(Production production, Map<PrecedenceEntity, Precedence> precedenceMap) {
    loop:
    for (var symbol : production.body().reversed()) {
      switch (symbol) {
        case Terminal t -> {
          var precedence = precedenceMap.get(t);
          if (precedence != null) {
            return precedence;
          }
          break loop;
        }
        case NonTerminal _ -> {}
      }
    }
    return null;  // No precedence
  }
}