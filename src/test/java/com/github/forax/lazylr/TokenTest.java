package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public final class TokenTest {

  @Test
  public void constructorIgnorableNullRegex() {
    assertThrows(NullPointerException.class, () ->
        new Token(null));
  }

  @Test
  public void constructorNamedNullName() {
    assertThrows(NullPointerException.class, () ->
        new Token(null, "[0-9]+"));
  }

  @Test
  public void constructorNamedNullRegex() {
    assertThrows(NullPointerException.class, () ->
        new Token("id", null));
  }

  @Test
  public void constructorIgnorableMalformedPattern() {
    assertThrows(IllegalArgumentException.class, () ->
        new Token("("));
  }

  @Test
  public void constructorNamedMalformedPattern() {
    assertThrows(IllegalArgumentException.class, () ->
        new Token("id", ")"));
  }

  @Test
  public void constructorIgnorablePatternWithGroup() {
    assertDoesNotThrow(() ->
        new Token("(foo)"));
  }

  @Test
  public void constructorNamedPatternWithGroup() {
    assertDoesNotThrow(() ->
        new Token("id", "(foo)"));
  }

  @Test
  public void constructorIgnorableValidPattern() {
    assertDoesNotThrow(() -> new Token("[ ]+"));
  }

  @Test
  public void constructorNamedValidPattern() {
    assertDoesNotThrow(() -> new Token("num", "[0-9]+"));
  }

  @Test
  public void constructorNonCapturingGroupIsAllowed() {
    // (?:...) is a non-capturing group and has groupCount() == 0
    assertDoesNotThrow(() -> new Token("num", "(?:[0-9]+)"));
  }

  @Test
  public void ignorableTokenHasNullName() {
    var t = new Token("[ ]+");
    assertNull(t.name());
  }

  @Test
  public void ignorableTokenReturnsRegex() {
    var t = new Token("[ ]+");
    assertEquals("[ ]+", t.regex());
  }

  @Test
  public void namedTokenReturnsName() {
    var t = new Token("num", "[0-9]+");
    assertEquals("num", t.name());
  }

  @Test
  public void namedTokenReturnsRegex() {
    var t = new Token("num", "[0-9]+");
    assertEquals("[0-9]+", t.regex());
  }

  @Test
  public void ignorableConstructorIsIgnorable() {
    assertTrue(new Token("[ ]+").isIgnorable());
  }

  @Test
  public void namedConstructorIsNotIgnorable() {
    assertFalse(new Token("num", "[0-9]+").isIgnorable());
  }

  @Test
  public void equalsSameNameAndRegex() {
    assertEquals(new Token("num", "[0-9]+"), new Token("num", "[0-9]+"));
  }

  @Test
  public void equalsSameIgnorableRegex() {
    assertEquals(new Token("[ ]+"), new Token("[ ]+"));
  }

  @Test
  public void notEqualsDifferentName() {
    assertNotEquals(new Token("num", "[0-9]+"), new Token("id", "[0-9]+"));
  }

  @Test
  public void notEqualsDifferentRegex() {
    assertNotEquals(new Token("num", "[0-9]+"), new Token("num", "[0-9]*"));
  }

  @Test
  public void notEqualsNamedVsIgnorable() {
    // same regex, but one has a name and one does not
    assertNotEquals(new Token("ws", "[ ]+"), new Token("[ ]+"));
  }

  @Test
  public void notEqualsNull() {
    assertNotEquals(new Token("num", "[0-9]+"), null);
  }

  @Test
  public void notEqualsOtherType() {
    assertNotEquals(new Token("num", "[0-9]+"), "[0-9]+");
  }

  @Test
  public void equalsIsReflexive() {
    var t = new Token("num", "[0-9]+");
    assertEquals(t, t);
  }

  @Test
  public void equalsIsSymmetric() {
    var a = new Token("num", "[0-9]+");
    var b = new Token("num", "[0-9]+");
    assertEquals(a, b);
    assertEquals(b, a);
  }

  @Test
  public void equalsIsTransitive() {
    var a = new Token("num", "[0-9]+");
    var b = new Token("num", "[0-9]+");
    var c = new Token("num", "[0-9]+");
    assertEquals(a, b);
    assertEquals(b, c);
    assertEquals(a, c);
  }

  @Test
  public void hashCodeConsistentWithEquals() {
    var a = new Token("num", "[0-9]+");
    var b = new Token("num", "[0-9]+");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  public void hashCodeConsistentWithEqualsIgnorable() {
    var a = new Token("[ ]+");
    var b = new Token("[ ]+");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  public void hashCodeStable() {
    var t = new Token("num", "[0-9]+");
    assertEquals(t.hashCode(), t.hashCode());
  }

  @Test
  public void toStringNamedContainsNameAndRegex() {
    var t = new Token("num", "[0-9]+");
    var s = t.toString();
    assertTrue(s.contains("num"), "toString should contain the name");
    assertTrue(s.contains("[0-9]+"), "toString should contain the regex");
  }

  @Test
  public void toStringIgnorableContainsRegex() {
    var t = new Token("[ ]+");
    var s = t.toString();
    assertTrue(s.contains("[ ]+"), "toString should contain the regex");
  }
}