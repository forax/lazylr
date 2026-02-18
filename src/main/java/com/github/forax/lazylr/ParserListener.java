package com.github.forax.lazylr;

public interface ParserListener {
  void onShift(Terminal token);
  void onReduce(Production production);
}