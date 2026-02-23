package com.github.forax.lazylr;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// A formal representation of a Context-Free Grammar (CFG) used by the [Parser].
///
/// A `Grammar` consists of a set of [Production] rules and a designated [startSymbol()].
/// It serves as the structural blueprint that the [Parser] uses to derive or reduce
/// valid sequences of [Terminal]s.
///
/// @see Parser#createParser(Grammar, java.util.Map)
public final class Grammar {
  private final NonTerminal startSymbol;
  private final List<Production> productions;
  private final Map<NonTerminal, List<Production>> productionMap;

  /// Creates a new immutable Grammar.
  ///
  /// @param startSymbol The entry point of the grammar (e.g., `program` or `expr`).
  /// @param productions A list of all valid derivation rules in the grammar.
  /// @throws NullPointerException if `startSymbol` or `productions` is null.
  /// @throws IllegalArgumentException if `startSymbol` is not defined by at least one production.
  public Grammar(NonTerminal startSymbol, List<Production> productions) {
    Objects.requireNonNull(startSymbol);
    productions = List.copyOf(productions);
    var productionMap = productions.stream()
        .collect(Collectors.groupingBy(Production::head, Collectors.toUnmodifiableList()));
    if (!productionMap.containsKey(startSymbol)) {
      throw new IllegalArgumentException("start symbol is not a non-terminal symbol");
    }
    this.startSymbol = startSymbol;
    this.productions = productions;
    this.productionMap = productionMap;
    super();
  }

  /// Returns all [Production] rules where the given [NonTerminal] is the head.
  ///
  /// @param nonTerminal The head symbol to look up.
  /// @return An unmodifiable list of productions belonging to the symbol.
  /// @throws IllegalArgumentException if the non-terminal is not defined in this grammar.
  /// @throws NullPointerException if the non-terminal is null.
  List<Production> productionsFor(NonTerminal nonTerminal) {
    Objects.requireNonNull(nonTerminal);
    var production = productionMap.get(nonTerminal);
    if (production == null) {
      throw new IllegalArgumentException("unknown nonTerminal " + nonTerminal);
    }
    return production;
  }

  Set<NonTerminal> nonTerminals() {
    return productionMap.keySet();
  }

  /// Returns the entry-point [NonTerminal] of this grammar.
  /// @return The entry-point [NonTerminal] of this grammar.
  public NonTerminal startSymbol() {
    return startSymbol;
  }

  /// Returns the list of all [Production]s defined in this grammar.
  /// @return The list of all [Production]s defined in this grammar.
  public List<Production> productions() {
    return productions;
  }

  /// @return A string representation of the grammar.
  @Override
  public String toString() {
    return "Grammar[" + "startSymbol=" + startSymbol + ", " + "productions=" + productions + ']';
  }
}