package com.github.forax.lazylr;

import java.util.Objects;

public record Rule(String name, String regex) {
  public Rule {
    Objects.requireNonNull(name);
    Objects.requireNonNull(regex);
  }
}
