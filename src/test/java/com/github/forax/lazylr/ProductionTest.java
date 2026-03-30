package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public final class ProductionTest {

  @Test
  @SuppressWarnings("DataFlowIssue")
  public void constructorHeadNull() {
    assertThrows(NullPointerException.class, () ->
        new Production(null, List.of()));
  }

  @Test
  @SuppressWarnings("DataFlowIssue")
  public void constructorBodyNull() {
    var head = new NonTerminal("S");
    assertThrows(NullPointerException.class, () ->
        new Production(head, null));
  }

  @Test
  public void constructorBodyIsDefensivelyCopied() {
    var head = new NonTerminal("S");
    var terminal = new Terminal("+");
    var mutableBody = new ArrayList<Symbol>(List.of(terminal));
    var production = new Production(head, mutableBody);
    mutableBody.clear();
    assertEquals(List.of(terminal), production.body());
  }

  @Test
  public void bodyIsUnmodifiable() {
    var head = new NonTerminal("S");
    var production = new Production(head, List.of(new Terminal("a")));
    assertThrows(UnsupportedOperationException.class, () ->
        production.body().add(new Terminal("b")));
  }

  @Test
  public void head() {
    var head = new NonTerminal("S");
    var production = new Production(head, List.of());
    assertEquals(head, production.head());
  }

  @Test
  public void bodyEmpty() {
    var head = new NonTerminal("S");
    var production = new Production(head, List.of());
    assertEquals(List.of(), production.body());
  }

  @Test
  public void bodyWithSymbols() {
    var head = new NonTerminal("expr");
    var symbols = List.of(
        new NonTerminal("expr"),
        new Terminal("+"),
        new NonTerminal("expr"));
    var production = new Production(head, symbols);
    assertEquals(symbols, production.body());
  }

  @Test
  public void equalsSameInstance() {
    var head = new NonTerminal("S");
    var production = new Production(head, List.of());
    assertEquals(production, production);
  }

  @Test
  public void equalsEquivalentProductions() {
    var head = new NonTerminal("S");
    var body = List.of(new Terminal("a"));
    var p1 = new Production(head, body);
    var p2 = new Production(head, body);
    assertEquals(p1, p2);
  }

  @Test
  public void equalsDifferentHead() {
    var body = List.<Symbol>of(new Terminal("a"));
    var p1 = new Production(new NonTerminal("S"), body);
    var p2 = new Production(new NonTerminal("T"), body);
    assertNotEquals(p1, p2);
  }

  @Test
  public void equalsDifferentBody() {
    var head = new NonTerminal("S");
    var p1 = new Production(head, List.of(new Terminal("a")));
    var p2 = new Production(head, List.of(new Terminal("b")));
    assertNotEquals(p1, p2);
  }

  @Test
  public void equalsNotNull() {
    var production = new Production(new NonTerminal("S"), List.of());
    assertNotEquals(null, production);
  }

  @Test
  public void equalsDifferentType() {
    var production = new Production(new NonTerminal("S"), List.of());
    assertNotEquals("not a production", production);
  }

  @Test
  public void hashCodeConsistentWithEquals() {
    var head = new NonTerminal("S");
    var body = List.<Symbol>of(new Terminal("a"));
    var p1 = new Production(head, body);
    var p2 = new Production(head, body);
    assertEquals(p1, p2);
    assertEquals(p1.hashCode(), p2.hashCode());
  }

  @Test
  public void hashCodeIsStable() {
    var production = new Production(new NonTerminal("S"), List.of(new Terminal("a")));
    var production2 = new Production(new NonTerminal("S"), List.of(new Terminal("a")));
    assertEquals(production.hashCode(), production2.hashCode());
  }

  @Test
  public void nameWithEmptyBodyIsEpsilon() {
    var head = new NonTerminal("stmt");
    var production = new Production(head, List.of());
    assertEquals("stmt : ε", production.name());
  }

  @Test
  public void nameWithSingleSymbol() {
    var head = new NonTerminal("expr");
    var production = new Production(head, List.of(new NonTerminal("num")));
    assertEquals("expr : num", production.name());
  }

  @Test
  public void nameWithMultipleSymbols() {
    var head = new NonTerminal("expr");
    var body = List.of(new NonTerminal("expr"), new Terminal("+"), new NonTerminal("expr"));
    var production = new Production(head, body);
    assertEquals("expr : expr + expr", production.name());
  }

  @Test
  public void nameIsCached() {
    var production = new Production(new NonTerminal("S"), List.of());
    assertSame(production.name(), production.name());
  }

  @Test
  public void testToString() {
    var head = new NonTerminal("expr");
    var body = List.of(new NonTerminal("num"));
    var production = new Production(head, body);
    assertEquals("expr : num", production.toString());
  }
}