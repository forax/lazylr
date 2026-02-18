package com.github.forax.lazylr;

import java.util.Set;

record State(Set<Item> items) {
  public State {
    items = Set.copyOf(items);
  }
}