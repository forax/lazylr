package com.github.forax.lazylr;

public sealed interface Symbol permits Terminal, NonTerminal {
  String name();
}