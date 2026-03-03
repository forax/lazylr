package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ParserErrorTest {
  @Test
  public void parsingErrorUnknownTerminalWithPosition() {
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(id))
    ));
    var precedence = Map.of(
        plus, new Precedence(10, Precedence.Associativity.LEFT)
    );

    var parser = Parser.createParser(grammar, precedence);

    var tokens = List.of(
        new Token("id", "[a-z]+"),
        new Token("+", "\\+"),
        new Token("\\s+")
    );
    var lexer = Lexer.createLexer(tokens);

    // Try to parse invalid input: "id + 2"
    var input = "id + 2";
    var terminals = lexer.tokenize(input);

    var exception = assertThrows(ParsingException.class, () -> {
      parser.parse(terminals, new ParserListener() {
        @Override public void onShift(Terminal token) {}
        @Override public void onReduce(Production production) {}
      });
    });

    var message = exception.getMessage();
    System.out.println(message);
    assertTrue(message.contains("Parsing error"));
    assertTrue(message.contains("line 1"));
    assertTrue(message.contains("column 6"));
    assertTrue(message.contains("id + 2"));
    assertTrue(message.contains("^"));
  }

  @Test
  public void parsingErrorNotAllowedByTheGrammarWithPosition() {
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(id))
    ));
    var precedence = Map.of(
        plus, new Precedence(10, Precedence.Associativity.LEFT)
    );

    var parser = Parser.createParser(grammar, precedence);

    var tokens = List.of(
        new Token("id", "[a-z]+"),
        new Token("+", "\\+"),
        new Token("\\s+")
    );
    var lexer = Lexer.createLexer(tokens);

    // Try to parse invalid input: "id + +"
    var input = "id + +";
    var terminals = lexer.tokenize(input);

    var exception = assertThrows(ParsingException.class, () -> {
      parser.parse(terminals, new ParserListener() {
        @Override public void onShift(Terminal token) {}
        @Override public void onReduce(Production production) {}
      });
    });

    var message = exception.getMessage();
    System.out.println(message);
    assertTrue(message.contains("Parsing error"));
    assertTrue(message.contains("line 1"));
    assertTrue(message.contains("column 6"));
    assertTrue(message.contains("id + +"));
    assertTrue(message.contains("^"));
  }

  @Test
  public void parsingErrorNotAllowedByTheGrammarWithMultipleLines() {
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(id))
    ));
    var precedence = Map.of(
        plus, new Precedence(10, Precedence.Associativity.LEFT)
    );

    var parser = Parser.createParser(grammar, precedence);

    var tokens = List.of(
        new Token("id", "[a-z]+"),
        new Token("+", "\\+"),
        new Token("\\s+")
    );
    var lexer = Lexer.createLexer(tokens);

    var input = """
        id
        id + +
        id
        """;
    var terminals = lexer.tokenize(input);

    var exception = assertThrows(ParsingException.class, () -> {
      parser.parse(terminals, new ParserListener() {
        @Override public void onShift(Terminal token) {}
        @Override public void onReduce(Production production) {}
      });
    });

    var message = exception.getMessage();
    System.out.println(message);
    assertTrue(message.contains("Parsing error"));
    assertTrue(message.contains("line 2"));
    assertTrue(message.contains("column 4"));
    assertTrue(message.contains("id + +"));
    assertTrue(message.contains("^"));
  }
}
