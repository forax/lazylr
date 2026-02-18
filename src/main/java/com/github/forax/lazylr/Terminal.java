package com.github.forax.lazylr;

import java.util.Objects;

public record Terminal(String name, String value) implements Symbol, PrecedenceEntity {
  public static final Terminal EPSILON = new Terminal("ε");
  public static final Terminal EOF = new Terminal("$");

  public Terminal {
    Objects.requireNonNull(name);
  }

  public Terminal(String name) {
    this(name, null);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Terminal terminal && name.equals(terminal.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return "Terminal(" + name + ")";
  }
}