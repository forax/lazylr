package com.github.forax.lazylr;

import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.StringJoiner;

record Item(Production production, int dotPosition, Set<Terminal> lookaheads) {
  public Item {
    Objects.requireNonNull(production);
    if (dotPosition < 0 || dotPosition > production.body().size()) {
      throw new IllegalArgumentException("Dot position must be between 0 and body size");
    }
    lookaheads = Set.copyOf(lookaheads);
  }

  public Symbol getNextSymbol() {
    if (dotPosition < production.body().size()) {
      return production.body().get(dotPosition);
    }
    return null; // Dot is at the end (Reduce state)
  }

  public List<Symbol> getSymbolsAfterNext() {
    if (dotPosition + 1 < production.body().size()) {
      return production.body().subList(dotPosition + 1, production.body().size());
    }
    return List.of();
  }

  public boolean isCompleted() {
    return dotPosition == production.body().size();
  }

  @Override
  public String toString() {
    var joiner = new StringJoiner(" ", production.head().name() + " -> ", " " + lookaheads);
    var body = production.body();
    for( var i = 0; i < body.size(); i++ ) {
      if (i == dotPosition) {
        joiner.add(".");
      }
      joiner.add(body.get(i).name());
    }
    return joiner.toString();
  }
}