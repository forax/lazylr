package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    assertTrue(error.value().contains("line 1"));
    assertTrue(error.value().contains("column 4"));

    assertFalse(terminals.hasNext());
  }

  @Test
  public void errorWithLineAndColumn() {
    var tokens = List.of(
        new Token("ID", "[a-z]+"),
        new Token("\\s+")
    );
    var lexer = Lexer.createLexer(tokens);
    var terminals = lexer.tokenize("hello world\ngood $bad");

    terminals.next(); // hello
    terminals.next(); // world
    terminals.next(); // good

    var error = terminals.next();
    assertEquals(Terminal.ERROR.name(), error.name());
    assertTrue(error.value().contains("line 2"));
    assertTrue(error.value().contains("column 6"));
    assertTrue(error.value().contains("'$'"));
    assertTrue(error.value().contains("good $bad"));

    assertFalse(terminals.hasNext());
  }

  @Test
  public void errorWithCaretPointer() {
    var tokens = List.of(
        new Token("ID", "[a-z]+"),
        new Token("\\s+")
    );
    var lexer = Lexer.createLexer(tokens);
    var terminals = lexer.tokenize("abc def@ghi");

    terminals.next(); // abc
    terminals.next(); // def

    var error = terminals.next();
    assertEquals(Terminal.ERROR.name(), error.name());
    var errorMsg = error.value();

    // Check that the error message contains the caret pointing to the error
    assertTrue(errorMsg.contains("abc def@ghi"));
    assertTrue(errorMsg.contains("^"));

    // The caret should be at position 7 (after "abc def")
    var lines = errorMsg.split("\n");
    assertTrue(lines.length >= 3);
    var caretLine = lines[2];
    assertEquals(7, caretLine.indexOf('^'));

    assertFalse(terminals.hasNext());
  }

  @Test
  public void errorWithMultipleLines() {
    var tokens = List.of(
        new Token("ID", "[a-z0-9]+"),
        new Token("eol", "\\n"),
        new Token("[ \\t]+")
    );
    var lexer = Lexer.createLexer(tokens);
    var input = """
        line1
        line2 #error
        line3
        """;
    var terminals = lexer.tokenize(input);

    assertEquals("ID", terminals.next().name());   // line1
    assertEquals("eol", terminals.next().name());  // \n
    assertEquals("ID", terminals.next().name());   // line2

    var error = terminals.next();
    assertEquals(Terminal.ERROR.name(), error.name());
    assertTrue(error.value().contains("line 2"));
    assertTrue(error.value().contains("column 7"));
    assertTrue(error.value().contains("line2 #error"));

    assertFalse(terminals.hasNext());
  }

  @Test
  public void errorWithSpaceCharacter() {
    var tokens = List.of(
        new Token("ID", "[a-z]+"),
        new Token("\\s+")
    );
    var lexer = Lexer.createLexer(tokens);

    var terminals = lexer.tokenize("hello  123");
    terminals.next(); // hello
    var error = terminals.next();
    assertTrue(error.value().contains("'1'"));
  }

  @Test
  public void errorWithUnprintableCharacter() {
    var tokens = List.of(
        new Token("ID", "[a-z]+"),
        new Token("\\s+")
    );
    var lexer = Lexer.createLexer(tokens);

    var terminals = lexer.tokenize("hello\t\u0007world");
    terminals.next(); // hello
    var error = terminals.next();
    assertTrue(error.value().contains("\\u0007"));
  }

  @Test
  public void errorAtStartOfLine() {
    var tokens = List.of(
        new Token("ID", "[a-z]+")
    );
    var lexer = Lexer.createLexer(tokens);
    var terminals = lexer.tokenize("#invalid");

    var error = terminals.next();
    assertEquals(Terminal.ERROR.name(), error.name());
    assertTrue(error.value().contains("line 1"));
    assertTrue(error.value().contains("column 1"));
    assertTrue(error.value().contains("'#'"));

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

  @Test
  public void unicodeIdentifiers() {
    // Test Unicode letter support using \p{L} (any Unicode letter)
    var tokens = List.of(
        new Token("ID", "\\p{L}+"),
        new Token("NUMBER", "\\p{N}+"),
        new Token("\\s+")  // Ignorable whitespace
    );
    var lexer = Lexer.createLexer(tokens);
    var terminals = lexer.tokenize("hello café 日本語 αβγ 123");

    var token1 = terminals.next();
    assertEquals("ID", token1.name());
    assertEquals("hello", token1.value());

    var token2 = terminals.next();
    assertEquals("ID", token2.name());
    assertEquals("café", token2.value());

    var token3 = terminals.next();
    assertEquals("ID", token3.name());
    assertEquals("日本語", token3.value());

    var token4 = terminals.next();
    assertEquals("ID", token4.name());
    assertEquals("αβγ", token4.value());

    var token5 = terminals.next();
    assertEquals("NUMBER", token5.name());
    assertEquals("123", token5.value());

    assertFalse(terminals.hasNext());
  }

  @Test
  public void unicodeEmojis() {
    // Test emoji support
    var tokens = List.of(
        new Token("EMOJI", "[\\p{So}\\p{Sk}]+"),  // Symbols, other + Symbols, modifier
        new Token("WORD", "\\p{L}+"),
        new Token("\\s+")
    );
    var lexer = Lexer.createLexer(tokens);
    var terminals = lexer.tokenize("hello 😀🎉 world 🚀");

    var token1 = terminals.next();
    assertEquals("WORD", token1.name());
    assertEquals("hello", token1.value());

    var token2 = terminals.next();
    assertEquals("EMOJI", token2.name());
    assertEquals("😀🎉", token2.value());

    var token3 = terminals.next();
    assertEquals("WORD", token3.name());
    assertEquals("world", token3.value());

    var token4 = terminals.next();
    assertEquals("EMOJI", token4.name());
    assertEquals("🚀", token4.value());

    assertFalse(terminals.hasNext());
  }

  @Test
  public void unicodeCyrillicAndArabic() {
    // Test Cyrillic and Arabic scripts
    var tokens = List.of(
        new Token("ID", "\\p{L}+"),
        new Token("\\s+")
    );
    var lexer = Lexer.createLexer(tokens);
    var terminals = lexer.tokenize("Привет مرحبا");

    var token1 = terminals.next();
    assertEquals("ID", token1.name());
    assertEquals("Привет", token1.value());

    var token2 = terminals.next();
    assertEquals("ID", token2.name());
    assertEquals("مرحبا", token2.value());

    assertFalse(terminals.hasNext());
  }

  @Test
  public void unicodeChineseNumbers() {
    // Test Chinese/Japanese/Korean numeric characters
    var tokens = List.of(
        new Token("CJK_NUMBER", "[一二三四五六七八九十百千万]+"),
        new Token("ARABIC_NUMBER", "\\p{Nd}+"),  // Decimal digit numbers
        new Token("\\s+")
    );
    var lexer = Lexer.createLexer(tokens);
    var terminals = lexer.tokenize("一二三 123 四五六");

    var token1 = terminals.next();
    assertEquals("CJK_NUMBER", token1.name());
    assertEquals("一二三", token1.value());

    var token2 = terminals.next();
    assertEquals("ARABIC_NUMBER", token2.name());
    assertEquals("123", token2.value());

    var token3 = terminals.next();
    assertEquals("CJK_NUMBER", token3.name());
    assertEquals("四五六", token3.value());

    assertFalse(terminals.hasNext());
  }

  @Test
  public void unicodeMixedScripts() {
    // Test that Unicode scripts can be mixed in identifiers
    var tokens = List.of(
        new Token("ID", "[\\p{L}\\p{N}_]+"),  // Letters, numbers, underscore
        new Token("\\s+")
    );
    var lexer = Lexer.createLexer(tokens);
    var terminals = lexer.tokenize("variable_name переменная_123 変数名_456");

    var token1 = terminals.next();
    assertEquals("ID", token1.name());
    assertEquals("variable_name", token1.value());

    var token2 = terminals.next();
    assertEquals("ID", token2.name());
    assertEquals("переменная_123", token2.value());

    var token3 = terminals.next();
    assertEquals("ID", token3.name());
    assertEquals("変数名_456", token3.value());

    assertFalse(terminals.hasNext());
  }
}