package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

public final class LexerTest {

  @Test
  public void simpleTokenization() {
    var rules = List.of(
        new Rule("ID", "[a-z]+"),
        new Rule("NUMBER", "[0-9]+")
    );
    var lexer = Lexer.createLexer(rules);
    var tokens = lexer.tokenize("abc123def");

    var t1 = tokens.next();
    assertEquals("ID", t1.name());
    assertEquals("abc", t1.value());

    var t2 = tokens.next();
    assertEquals("NUMBER", t2.name());
    assertEquals("123", t2.value());

    var t3 = tokens.next();
    assertEquals("ID", t3.name());
    assertEquals("def", t3.value());

    assertFalse(tokens.hasNext());
  }

  @Test
  public void rulePriority() {
    // Both rules match "if", but "KEYWORD" is first
    var rules = List.of(
        new Rule("KEYWORD", "if"),
        new Rule("ID", "[a-z]+")
    );
    var lexer = Lexer.createLexer(rules);
    var tokens = lexer.tokenize("if");

    var t = tokens.next();
    assertEquals("KEYWORD", t.name());
    assertEquals("if", t.value());
  }

  @Test
  public void ignorableRules() {
    var rules = List.of(
        new Rule("\\s+"),  // Ignorable whitespace
        new Rule("ID", "[a-z]+")
    );
    var lexer = Lexer.createLexer(rules);
    var tokens = lexer.tokenize("  hello   world  ");

    var t1 = tokens.next();
    assertEquals("ID", t1.name());
    assertEquals("hello", t1.value());

    var t2 = tokens.next();
    assertEquals("ID", t2.name());
    assertEquals("world", t2.value());

    assertFalse(tokens.hasNext());
  }

  @Test
  public void errorHandling() {
    var rules = List.of(
        new Rule("ID", "[a-z]+")
    );
    var lexer = Lexer.createLexer(rules);
    var tokens = lexer.tokenize("abc#def");

    tokens.next(); // Skip "abc"

    var error = tokens.next();
    assertEquals(Terminal.ERROR.name(), error.name());
    assertTrue(error.value().contains("#"));

    assertFalse(tokens.hasNext());
  }

  @Test
  public void emptyInput() {
    var lexer = Lexer.createLexer(List.of(new Rule("ID", "[a-z]+")));
    var tokens = lexer.tokenize("");
    
    assertFalse(tokens.hasNext());
    assertThrows(NoSuchElementException.class, tokens::next);
  }

  @Test
  public void onlyError() {
    var lexer = Lexer.createLexer(List.of(new Rule("ID", "[a-z]+")));
    var tokens = lexer.tokenize("!!");

    var error = tokens.next();
    assertEquals(Terminal.ERROR.name(), error.name());
    assertTrue(error.value().contains("!"));
    
    assertFalse(tokens.hasNext());
  }
}