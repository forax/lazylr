package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

public final class LexerTest {

  @Test
  public void simpleTokenization() {
    var tokens = List.of(
        new Token("ID", "[a-z]+"),
        new Token("NUMBER", "[0-9]+")
    );
    var lexer = Lexer.createLexer(tokens);
    var terminals = lexer.tokenize("abc123def");

    var t1 = terminals.next();
    assertEquals("ID", t1.name());
    assertEquals("abc", t1.value());

    var t2 = terminals.next();
    assertEquals("NUMBER", t2.name());
    assertEquals("123", t2.value());

    var t3 = terminals.next();
    assertEquals("ID", t3.name());
    assertEquals("def", t3.value());

    assertFalse(terminals.hasNext());
  }

  @Test
  public void tokenPriority() {
    // Both tokens match "if", but "KEYWORD" is first
    var tokens = List.of(
        new Token("KEYWORD", "if"),
        new Token("ID", "[a-z]+")
    );
    var lexer = Lexer.createLexer(tokens);
    var terminals = lexer.tokenize("if");

    var t = terminals.next();
    assertEquals("KEYWORD", t.name());
    assertEquals("if", t.value());
  }

  @Test
  public void ignorableTokens() {
    var tokens = List.of(
        new Token("\\s+"),  // Ignorable whitespace
        new Token("ID", "[a-z]+")
    );
    var lexer = Lexer.createLexer(tokens);
    var terminals = lexer.tokenize("  hello   world  ");

    var t1 = terminals.next();
    assertEquals("ID", t1.name());
    assertEquals("hello", t1.value());

    var t2 = terminals.next();
    assertEquals("ID", t2.name());
    assertEquals("world", t2.value());

    assertFalse(terminals.hasNext());
  }

  @Test
  public void commentTokens() {
    var tokens = List.of(
        new Token("id", "[0-9]+"),
        new Token("eol", "[\\r]?\\n"),
        new Token("\\s+"),          // Ignorable space
        new Token("\\/\\/[^\\n]*")   // Ignorable comment
    );
    var lexer = Lexer.createLexer(tokens);

    var terminals = new ArrayList<Terminal>();
    lexer.tokenize("""
        12
        43  // comment
        54
        """).forEachRemaining(terminals::add);

    assertEquals(List.of(
        new Terminal("id"), new Terminal("eol"),
        new Terminal("id"), new Terminal("eol"),
        new Terminal("id"), new Terminal("eol")
    ), terminals);
  }

  @Test
  public void errorHandling() {
    var tokens = List.of(
        new Token("ID", "[a-z]+")
    );
    var lexer = Lexer.createLexer(tokens);
    var terminals = lexer.tokenize("abc#def");

    terminals.next(); // Skip "abc"

    var error = terminals.next();
    assertEquals(Terminal.ERROR.name(), error.name());
    assertTrue(error.value().contains("#"));

    assertFalse(terminals.hasNext());
  }

  @Test
  public void emptyInput() {
    var lexer = Lexer.createLexer(List.of(new Token("ID", "[a-z]+")));
    var terminals = lexer.tokenize("");
    
    assertFalse(terminals.hasNext());
    assertThrows(NoSuchElementException.class, terminals::next);
  }

  @Test
  public void onlyError() {
    var lexer = Lexer.createLexer(List.of(new Token("ID", "[a-z]+")));
    var terminals = lexer.tokenize("!!");

    var error = terminals.next();
    assertEquals(Terminal.ERROR.name(), error.name());
    assertTrue(error.value().contains("!"));
    
    assertFalse(terminals.hasNext());
  }
}