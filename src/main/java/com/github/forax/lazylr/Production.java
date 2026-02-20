package com.github.forax.lazylr;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record Production(NonTerminal head, List<Symbol> body) implements PrecedenceEntity {
  public Production {
    Objects.requireNonNull(head);
    body = List.copyOf(body);
  }

  public String name() {
    if (body.isEmpty()) {
      return head.name() + " -> ε";
    }
    return body.stream()
        .map(Symbol::name)
        .collect(Collectors.joining(" ", head.name() + " -> ", ""));
  }

  @Override
  public String toString() {
    return name();
  }
}