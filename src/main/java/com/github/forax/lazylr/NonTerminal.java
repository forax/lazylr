package com.github.forax.lazylr;

import java.util.Objects;

public record NonTerminal(String name) implements Symbol {
  public NonTerminal {
    Objects.requireNonNull(name);
  }

  @Override
  public String toString() {
    return "NonTerminal(" + name + ")";
  }
}