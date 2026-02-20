package com.github.forax.lazylr;

import java.util.Objects;
import java.util.List;
import java.util.StringJoiner;

final class Item {
  private final Production production;
  private final int dotPosition;
  private final Terminal lookahead;
  private final int hashCode;  // cached hashCode for perf reason

  public Item(Production production, int dotPosition, Terminal lookahead) {
    Objects.requireNonNull(production);
    if (dotPosition < 0 || dotPosition > production.body().size()) {
      throw new IllegalArgumentException("Dot position must be between 0 and body size");
    }
    Objects.requireNonNull(lookahead);
    var hashCode = (System.identityHashCode(production) * 31 + dotPosition) * 31 + lookahead.hashCode();
    this.production = production;
    this.dotPosition = dotPosition;
    this.lookahead = lookahead;
    this.hashCode = hashCode;
    super();
  }

  public Production production() {
    return production;
  }

  public int dotPosition() {
    return dotPosition;
  }

  public Terminal lookahead() {
    return lookahead;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Item item &&
        production == item.production &&    // production are unique
        dotPosition == item.dotPosition &&
        lookahead.equals(item.lookahead);
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
    var joiner = new StringJoiner(" ",
        production.head().name() + " -> ",
        " {" + lookahead.name() + "}");
    var body = production.body();
    for (var i = 0; i < body.size(); i++) {
      if (i == dotPosition) {
        joiner.add(".");
      }
      joiner.add(body.get(i).name());
    }
    return joiner.toString();
  }
}