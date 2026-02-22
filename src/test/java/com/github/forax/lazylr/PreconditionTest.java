package com.github.forax.lazylr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public final class PreconditionTest {

  @Nested
  public class GrammarPreconditions {

    @Test
    @DisplayName("Constructor should throw NPE if startSymbol is null")
    public void constructorStartSymbolNull() {
      assertThrows(NullPointerException.class, () ->
          new Grammar(null, List.of()));
    }

    @Test
    @DisplayName("Constructor should throw NPE if productions list is null")
    public void constructorProductionsNull() {
      var start = new NonTerminal("S");
      assertThrows(NullPointerException.class, () ->
          new Grammar(start, null));
    }

    @Test
    @DisplayName("Constructor should throw IAE if startSymbol is not defined in any production")
    public void constructorStartSymbolNotDefined() {
      var start = new NonTerminal("S");
      var other = new NonTerminal("A");
      var prod = new Production(other, List.of(new Terminal("a")));
      assertThrows(IllegalArgumentException.class, () ->
          new Grammar(start, List.of(prod)));
    }

    @Test
    @DisplayName("productionsFor should throw NPE if nonTerminal is null")
    public void productionsForNull() {
      var start = new NonTerminal("S");
      var grammar = new Grammar(start, List.of(new Production(start, List.of())));
      assertThrows(NullPointerException.class, () ->
          grammar.productionsFor(null));
    }

    @Test
    @DisplayName("productionsFor should throw IAE if nonTerminal is unknown")
    public void productionsForUnknown() {
      var start = new NonTerminal("S");
      var grammar = new Grammar(start, List.of(new Production(start, List.of())));
      var unknown = new NonTerminal("Unknown");
      assertThrows(IllegalArgumentException.class, () ->
          grammar.productionsFor(unknown));
    }
  }

  @Nested
  public class NonTerminalPreconditions {

    @Test
    @DisplayName("Constructor should throw NPE if name is null")
    public void constructorNameNull() {
      assertThrows(NullPointerException.class, () ->
          new NonTerminal(null));
    }
  }

  @Nested
  @DisplayName("Precedence Preconditions")
  public class PrecedencePreconditions {

    @Test
    @DisplayName("Constructor should throw IAE if level is negative")
    public void constructorLevelNegative() {
      assertThrows(IllegalArgumentException.class, () ->
          new Precedence(-1, Precedence.Associativity.LEFT));
    }

    @Test
    @DisplayName("Constructor should throw NPE if associativity is null")
    public void constructorAssocNull() {
      assertThrows(NullPointerException.class, () ->
          new Precedence(1, null));
    }
  }

  @Nested
  @DisplayName("Production Preconditions")
  public class ProductionPreconditions {

    @Test
    @DisplayName("Constructor should throw NPE if head is null")
    public void constructorHeadNull() {
      assertThrows(NullPointerException.class, () ->
          new Production(null, List.of()));
    }

    @Test
    @DisplayName("Constructor should throw NPE if body list is null")
    public void constructorBodyNull() {
      var head = new NonTerminal("S");
      assertThrows(NullPointerException.class, () ->
          new Production(head, null));
    }
  }

  @Nested
  @DisplayName("Terminal Preconditions")
  public class TerminalPreconditions {

    @Test
    @DisplayName("Grammar constructor (name only) should throw NPE if name is null")
    public void constructorNameOnlyNull() {
      assertThrows(NullPointerException.class, () ->
          new Terminal(null));
    }

    @Test
    @DisplayName("Lexer constructor (name and value) should throw NPE if name is null")
    public void constructorNameNull() {
      assertThrows(NullPointerException.class, () ->
          new Terminal(null, "value"));
    }

    @Test
    @DisplayName("Lexer constructor (name and value) should throw NPE if value is null")
    public void constructorValueNull() {
      assertThrows(NullPointerException.class, () ->
          new Terminal("name", null));
    }
  }

  @Nested
  @DisplayName("Lexer Preconditions")
  public class LexerPreconditions {

    @Test
    @DisplayName("createLexer should throw NPE if rules list is null")
    public void createLexerRulesNull() {
      assertThrows(NullPointerException.class, () ->
          Lexer.createLexer(null));
    }

    @Test
    @DisplayName("tokenize should throw NPE if input is null")
    public void tokenizeInputNull() {
      var lexer = Lexer.createLexer(List.of(new Rule("ID", "[a-z]+")));
      assertThrows(NullPointerException.class, () ->
          lexer.tokenize(null));
    }

    @Test
    @DisplayName("Iterator.next should throw NoSuchElementException if no more tokens")
    public void iteratorNextOutOfBounds() {
      var lexer = Lexer.createLexer(List.of(new Rule("ID", "[a-z]+")));
      var tokens = lexer.tokenize("");  // Empty input results in no matches
      assertThrows(NoSuchElementException.class, tokens::next);
    }
  }

  @Nested
  @DisplayName("Parser Preconditions")
  public class ParserPreconditions {

    private static Grammar createSampleGrammar() {
      var start = new NonTerminal("S");
      return new Grammar(start, List.of(
          new Production(start, List.of(new Terminal("a")))));
    }

    @Test
    @DisplayName("createParser should throw NPE if grammar is null")
    public void createParserGrammarNull() {
      assertThrows(NullPointerException.class, () ->
          Parser.createParser(null, Map.of()));
    }

    @Test
    @DisplayName("createParser should throw NPE if precedenceMap is null")
    public void createParserPrecedenceNull() {
      var grammar = createSampleGrammar();
      assertThrows(NullPointerException.class, () ->
          Parser.createParser(grammar, null));
    }

    @Test
    @DisplayName("parse (Evaluator) should throw NPE if input is null")
    public void parseEvaluatorInputNull() {
      var parser = Parser.createParser(createSampleGrammar(), Map.of());
      var evaluator = new Evaluator<>() {
        @Override
        public Object evaluate(Terminal terminal) {
          return fail();
        }

        @Override
        public Object evaluate(Production production, List<Object> args) {
          return fail();
        }
      };
      assertThrows(NullPointerException.class, () ->
              parser.parse(null, evaluator));
    }

    @Test
    @DisplayName("parse (Evaluator) should throw NPE if evaluator is null")
    public void parseEvaluatorNull() {
      var parser = Parser.createParser(createSampleGrammar(), Map.of());
      var tokens = Collections.<Terminal>emptyIterator();
      assertThrows(NullPointerException.class, () ->
              parser.parse(tokens, (Evaluator<Object>) null));
    }

    @Test
    @DisplayName("parse (Listener) should throw NPE if input is null")
    public void parseListenerInputNull() {
      var parser = Parser.createParser(createSampleGrammar(), Map.of());
      ParserListener listener = new ParserListener() {
        @Override
        public void onShift(Terminal token) {
          fail();
        }

        @Override
        public void onReduce(Production production) {
          fail();
        }
      };
      assertThrows(NullPointerException.class,
          () -> parser.parse(null, listener));
    }

    @Test
    @DisplayName("parse (Listener) should throw NPE if listener is null")
    public void parseListenerNull() {
      var parser = Parser.createParser(createSampleGrammar(), Map.of());
      var tokens = Collections.<Terminal>emptyIterator();
      assertThrows(NullPointerException.class, () ->
              parser.parse(tokens, (ParserListener) null));
    }
  }
}