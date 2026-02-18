package com.github.forax.lazylr;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record Grammar(NonTerminal startSymbol,
                      Set<Production> productions) {
  public Grammar {
    Objects.requireNonNull(startSymbol);
    productions = Set.copyOf(productions);
  }

  public Set<Production> getProductionsFor(NonTerminal nt) {
    return productions.stream()
        .filter(p -> p.head().equals(nt))
        .collect(Collectors.toSet());
  }
}