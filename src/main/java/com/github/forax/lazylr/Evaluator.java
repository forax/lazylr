package com.github.forax.lazylr;

import java.util.List;

public interface Evaluator<T> {
  T evaluate(Terminal terminal);
  T evaluate(Production production, List<T> arguments);
}
