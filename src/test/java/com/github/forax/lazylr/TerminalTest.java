package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

public final class TerminalTest {

  @Test
  public void constructorNameOnlyNull() {
    assertThrows(NullPointerException.class, () ->
        new Terminal(null));
  }

  @Test
  public void constructorNameNull() {
    assertThrows(NullPointerException.class, () ->
        new Terminal(null, "value"));
  }

  @Test
  public void constructorValueNull() {
    assertThrows(NullPointerException.class, () ->
        new Terminal("name", null));
  }

  @Test
  public void nameOnlyConstructorReturnsName() {
    var t = new Terminal("num");
    assertEquals("num", t.name());
  }

  @Test
  public void nameOnlyConstructorHasNoValue() {
    var t = new Terminal("num");
    assertThrows(NoSuchElementException.class, t::value);
  }

  @Test
  public void nameAndValueConstructorReturnsName() {
    var t = new Terminal("num", "42");
    assertEquals("num", t.name());
  }

  @Test
  public void nameAndValueConstructorReturnsValue() {
    var t = new Terminal("num", "42");
    assertEquals("42", t.value());
  }

  @Test
  public void equalsSameName() {
    assertEquals(new Terminal("num"), new Terminal("num"));
  }

  @Test
  public void equalsSameNameDifferentValue() {
    // A lexer token ("42") must match its grammar placeholder (no value)
    assertEquals(new Terminal("num", "42"), new Terminal("num"));
  }

  @Test
  public void equalsSameNameBothWithValues() {
    assertEquals(new Terminal("num", "1"), new Terminal("num", "2"));
  }

  @Test
  public void notEqualsDifferentName() {
    assertNotEquals(new Terminal("num"), new Terminal("id"));
  }

  @Test
  public void notEqualsNull() {
    assertNotEquals(new Terminal("num"), null);
  }

  @Test
  public void notEqualsOtherType() {
    assertNotEquals(new Terminal("num"), "num");
  }

  @Test
  public void equalsIsReflexive() {
    var t = new Terminal("num");
    assertEquals(t, t);
  }

  @Test
  public void equalsIsSymmetric() {
    var a = new Terminal("num");
    var b = new Terminal("num");
    assertEquals(a, b);
    assertEquals(b, a);
  }

  @Test
  public void equalsIsTransitive() {
    var a = new Terminal("num", "1");
    var b = new Terminal("num", "2");
    var c = new Terminal("num", "3");
    assertEquals(a, b);
    assertEquals(b, c);
    assertEquals(a, c);
  }

  @Test
  public void hashCodeConsistentWithEquals() {
    var a = new Terminal("num", "42");
    var b = new Terminal("num");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  public void hashCodeStable() {
    var t = new Terminal("num");
    assertEquals(t.hashCode(), t.hashCode());
  }

  @Test
  public void toStringContainsName() {
    var t = new Terminal("num");
    assertTrue(t.toString().contains("num"), "toString should contain the terminal name");
  }

  @Test
  public void epsilonName() {
    assertEquals("ε", Terminal.EPSILON.name());
  }

  @Test
  public void eofName() {
    assertEquals("$", Terminal.EOF.name());
  }

  @Test
  public void errorName() {
    assertEquals("error", Terminal.ERROR.name());
  }

  @Test
  public void constantsAreDistinct() {
    assertNotEquals(Terminal.EPSILON, Terminal.EOF);
    assertNotEquals(Terminal.EPSILON, Terminal.ERROR);
    assertNotEquals(Terminal.EOF, Terminal.ERROR);
  }

  @Test
  public void epsilonMatchesTerminalWithSameName() {
    assertEquals(Terminal.EPSILON, new Terminal("ε"));
  }

  @Test
  public void eofMatchesTerminalWithSameName() {
    assertEquals(Terminal.EOF, new Terminal("$"));
  }

  @Test
  public void errorMatchesTerminalWithSameName() {
    assertEquals(Terminal.ERROR, new Terminal("error"));
  }
}