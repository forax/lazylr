package com.github.forax.lazylr;

import java.util.Objects;

public record Precedence(int level, Associativity assoc) {
  public enum Associativity { LEFT, RIGHT }

  public Precedence {
    if (level < 0) {
      throw new IllegalArgumentException("Precedence level must be non-negative");
    }
    Objects.requireNonNull(assoc);
  }
}