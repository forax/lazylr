package com.github.forax.lazylr;

import java.util.Set;

final class State {
  private final Set<Item> items;
  private final int hashCode;  // cached hashCode for perf reason

  public State(Set<Item> items) {
    items = Set.copyOf(items);
    this.items = items;
    this.hashCode = items.hashCode();
    super();
  }

  public Set<Item> items() {
    return items;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof State state && items.equals(state.items);
  }

  @Override
  public String toString() {
    return "State[items=" + items + ']';
  }
}