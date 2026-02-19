package com.github.forax.lazylr;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Grammar {
  private final NonTerminal startSymbol;
  private final List<Production> productions;
  private final Map<NonTerminal, List<Production>> productionMap;

  public Grammar(NonTerminal startSymbol, List<Production> productions) {
    Objects.requireNonNull(startSymbol);
    productions = List.copyOf(productions);
    var productionMap = productions.stream()
        .collect(Collectors.groupingBy(Production::head, Collectors.toUnmodifiableList()));
    this.startSymbol = startSymbol;
    this.productions = productions;
    this.productionMap = productionMap;
    super();
  }

  public List<Production> productionsFor(NonTerminal nonTerminal) {
    Objects.requireNonNull(nonTerminal);
    var production = productionMap.get(nonTerminal);
    if (production == null) {
      throw new IllegalArgumentException("unknown nonTerminal " + nonTerminal);
    }
    return production;
  }

  public NonTerminal startSymbol() {
    return startSymbol;
  }

  public List<Production> productions() {
    return productions;
  }

  @Override
  public String toString() {
    return "Grammar[" + "startSymbol=" + startSymbol + ", " + "productions=" + productions + ']';
  }
}
