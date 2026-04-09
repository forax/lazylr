package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class GrammarTest {
  @Test
  @SuppressWarnings("DataFlowIssue")
  public void constructorStartSymbolNull() {
    assertThrows(NullPointerException.class, () ->
        new Grammar(null, List.of()));
  }

  @Test
  @SuppressWarnings("DataFlowIssue")
  public void constructorProductionsNull() {
    var start = new NonTerminal("S");
    assertThrows(NullPointerException.class, () ->
        new Grammar(start, null));
  }

  @Test
  public void constructorStartSymbolNotDefined() {
    var start = new NonTerminal("S");
    var other = new NonTerminal("A");
    var prod = new Production(other, List.of(new Terminal("a")));
    assertThrows(IllegalArgumentException.class, () ->
        new Grammar(start, List.of(prod)));
  }

  @Test
  @SuppressWarnings("DataFlowIssue")
  public void productionsForNull() {
    var start = new NonTerminal("S");
    var grammar = new Grammar(start, List.of(new Production(start, List.of())));
    assertThrows(NullPointerException.class, () ->
        grammar.productionsFor(null));
  }

  @Test
  public void productionsForUnknown() {
    var start = new NonTerminal("S");
    var grammar = new Grammar(start, List.of(new Production(start, List.of())));
    var unknown = new NonTerminal("Unknown");
    assertThrows(IllegalArgumentException.class, () ->
        grammar.productionsFor(unknown));
  }

  @Test
  public void constructorValidGrammar() {
    var start = new NonTerminal("S");
    var prod = new Production(start, List.of(new Terminal("a")));
    var grammar = new Grammar(start, List.of(prod));
    assertEquals(start, grammar.startSymbol());
    assertEquals(List.of(prod), grammar.productions());
  }

  @Test
  public void constructorProductionsIsDefensiveCopy() {
    var start = new NonTerminal("S");
    var prod = new Production(start, List.of(new Terminal("a")));
    var mutableList = new java.util.ArrayList<>(List.of(prod));
    var grammar = new Grammar(start, mutableList);
    mutableList.clear();
    assertEquals(1, grammar.productions().size());
  }

  @Test
  public void constructorProductionsIsUnmodifiable() {
    var start = new NonTerminal("S");
    var prod = new Production(start, List.of(new Terminal("a")));
    var grammar = new Grammar(start, List.of(prod));
    assertThrows(UnsupportedOperationException.class, () ->
        grammar.productions().add(prod));
  }

  @Test
  public void startSymbol() {
    var start = new NonTerminal("S");
    var grammar = new Grammar(start, List.of(new Production(start, List.of())));
    assertEquals(start, grammar.startSymbol());
  }

  @Test
  public void productionsFor() {
    var start = new NonTerminal("S");
    var prod = new Production(start, List.of(new Terminal("a")));
    var grammar = new Grammar(start, List.of(prod));
    assertEquals(List.of(prod), grammar.productionsFor(start));
  }

  @Test
  public void productionsForMultiple() {
    var start = new NonTerminal("S");
    var prod1 = new Production(start, List.of(new Terminal("a")));
    var prod2 = new Production(start, List.of(new Terminal("b")));
    var grammar = new Grammar(start, List.of(prod1, prod2));
    assertEquals(List.of(prod1, prod2), grammar.productionsFor(start));
  }

  @Test
  public void productionsForIsUnmodifiable() {
    var start = new NonTerminal("S");
    var prod = new Production(start, List.of(new Terminal("a")));
    var grammar = new Grammar(start, List.of(prod));
    assertThrows(UnsupportedOperationException.class, () ->
        grammar.productionsFor(start).add(prod));
  }

  @Test
  public void nonTerminals() {
    var start = new NonTerminal("S");
    var other = new NonTerminal("A");
    var grammar = new Grammar(start, List.of(
        new Production(start, List.of()),
        new Production(other, List.of())));
    assertEquals(Set.of(start, other), grammar.nonTerminals());
  }

  @Test
  public void nonTerminalsOnlyStart() {
    var start = new NonTerminal("S");
    var grammar = new Grammar(start, List.of(new Production(start, List.of())));
    assertEquals(Set.of(start), grammar.nonTerminals());
  }

  @Test
  public void nonTerminalDeclaredButNoProductionForIt() {
    var A = new NonTerminal("A");
    var B = new NonTerminal("B");

    assertThrows(IllegalArgumentException.class, () ->
        new Grammar(A, List.of(
            new Production(A, List.of(B)))));
  }

  @Test
  public void toStringContainsStartSymbol() {
    var start = new NonTerminal("S");
    var grammar = new Grammar(start, List.of(new Production(start, List.of())));
    assertTrue(grammar.toString().contains("S"));
  }
}
