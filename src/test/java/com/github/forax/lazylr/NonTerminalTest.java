package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public final class NonTerminalTest {
  @Test
  public void nameReturnsConstructorArgument() {
    var nt = new NonTerminal("expr");
    assertEquals("expr", nt.name());
  }

  @Test
  @SuppressWarnings("DataFlowIssue")
  public void constructorNameNull() {
    assertThrows(NullPointerException.class, () ->
        new NonTerminal(null));
  }

  @Test
  public void equalsSameName() {
    assertEquals(new NonTerminal("expr"), new NonTerminal("expr"));
  }

  @Test
  public void notEqualsDifferentName() {
    assertNotEquals(new NonTerminal("expr"), new NonTerminal("stmt"));
  }

  @Test
  public void notEqualsNull() {
    assertNotEquals(new NonTerminal("expr"), null);
  }

  @Test
  public void notEqualsOtherType() {
    assertNotEquals(new NonTerminal("expr"), "expr");
  }

  @Test
  public void notEqualsTerminalWithSameName() {
    // a NonTerminal and a Terminal with the same name must not be equal
    assertNotEquals(new NonTerminal("expr"), new Terminal("expr"));
  }

  @Test
  public void equalsIsReflexive() {
    var nt = new NonTerminal("expr");
    assertEquals(nt, nt);
  }

  @Test
  public void equalsIsSymmetric() {
    var a = new NonTerminal("expr");
    var b = new NonTerminal("expr");
    assertEquals(a, b);
    assertEquals(b, a);
  }

  @Test
  public void equalsIsTransitive() {
    var a = new NonTerminal("expr");
    var b = new NonTerminal("expr");
    var c = new NonTerminal("expr");
    assertEquals(a, b);
    assertEquals(b, c);
    assertEquals(a, c);
  }

  @Test
  public void hashCodeConsistentWithEquals() {
    var a = new NonTerminal("expr");
    var b = new NonTerminal("expr");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  public void hashCodeStable() {
    var nt = new NonTerminal("expr");
    assertEquals(nt.hashCode(), nt.hashCode());
  }

  @Test
  public void toStringContainsName() {
    var nt = new NonTerminal("expr");
    assertTrue(nt.toString().contains("expr"), "toString should contain the non-terminal name");
  }
}