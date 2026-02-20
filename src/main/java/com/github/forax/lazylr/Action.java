package com.github.forax.lazylr;

import java.util.Objects;

sealed interface Action {
  record Shift(State nextState) implements Action {
    public Shift {
      Objects.requireNonNull(nextState);
    }
  }
  record Reduce(Production production) implements Action {
    public Reduce {
      Objects.requireNonNull(production);
    }
  }
}