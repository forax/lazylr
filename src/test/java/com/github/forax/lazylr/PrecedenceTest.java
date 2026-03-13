package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public final class PrecedenceTest {
  @Test
  public void constructorLevelNegative() {
    assertThrows(IllegalArgumentException.class, () ->
        new Precedence(-1, Precedence.Associativity.LEFT));
  }

  @Test
  public void constructorAssocNull() {
    assertThrows(NullPointerException.class, () ->
        new Precedence(1, null));
  }

  @Test
  public void constructorLevelZeroIsValid() {
    var p = new Precedence(0, Precedence.Associativity.LEFT);
    assertEquals(0, p.level());
  }

  @Test
  public void constructorStoresLevel() {
    var p = new Precedence(5, Precedence.Associativity.LEFT);
    assertEquals(5, p.level());
  }

  @Test
  public void constructorStoresAssociativityLeft() {
    var p = new Precedence(1, Precedence.Associativity.LEFT);
    assertEquals(Precedence.Associativity.LEFT, p.associativity());
  }

  @Test
  public void constructorStoresAssociativityRight() {
    var p = new Precedence(1, Precedence.Associativity.RIGHT);
    assertEquals(Precedence.Associativity.RIGHT, p.associativity());
  }

  @Test
  public void equalsSameLevelAndAssociativity() {
    var p1 = new Precedence(3, Precedence.Associativity.LEFT);
    var p2 = new Precedence(3, Precedence.Associativity.LEFT);
    assertEquals(p1, p2);
  }

  @Test
  public void equalsDifferentLevel() {
    var p1 = new Precedence(1, Precedence.Associativity.LEFT);
    var p2 = new Precedence(2, Precedence.Associativity.LEFT);
    assertNotEquals(p1, p2);
  }

  @Test
  public void equalsDifferentAssociativity() {
    var p1 = new Precedence(1, Precedence.Associativity.LEFT);
    var p2 = new Precedence(1, Precedence.Associativity.RIGHT);
    assertNotEquals(p1, p2);
  }

  @Test
  public void hashCodeConsistentWithEquals() {
    var p1 = new Precedence(3, Precedence.Associativity.RIGHT);
    var p2 = new Precedence(3, Precedence.Associativity.RIGHT);
    assertEquals(p1.hashCode(), p2.hashCode());
  }

  @Test
  public void toStringContainsLevelAndAssociativity() {
    var p = new Precedence(2, Precedence.Associativity.LEFT);
    var s = p.toString();
    assertTrue(s.contains("2"));
    assertTrue(s.contains("LEFT"));
  }

  @Test
  public void constructorLevelVeryLargeIsValid() {
    var p = new Precedence(Integer.MAX_VALUE, Precedence.Associativity.RIGHT);
    assertEquals(Integer.MAX_VALUE, p.level());
  }
}