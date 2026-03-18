package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/// Those are the same tests as in [ParserTest] but using the meta grammar DSL,
/// Please update both files accordingly
public final class MetaGrammarParserTest {

  private static String parse(Grammar grammar,
                              Map<PrecedenceEntity, Precedence> precedence,
                              List<Terminal> input) {
    var parser = Parser.createParser(grammar, precedence);
    var result = new StringBuilder();
    parser.parse(input.iterator(), new ParserListener() {
      @Override public void onShift(Terminal token) {
        result.append("Shift ").append(token.name()).append('\n');
      }
      @Override public void onReduce(Production production) {
        result.append("Reduce ").append(production.name()).append('\n');
      }
    });
    return result.toString();
  }

  @Test
  public void createParserGrammarNull() {
    assertThrows(NullPointerException.class, () ->
        Parser.createParser(null, Map.of()));
  }

  @Test
  public void createParserPrecedenceNull() {
    var mg = MetaGrammar.load("""
        grammar {
          S : 'x'
        }
        """);
    var grammar = mg.grammar();
    assertThrows(NullPointerException.class, () ->
        Parser.createParser(grammar, null));
  }

  @Test
  public void parseEvaluatorInputNull() {
    var mg = MetaGrammar.load("""
        grammar {
          S : 'x'
        }
        """);
    var grammar = mg.grammar();
    var parser = Parser.createParser(grammar, Map.of());
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
  public void parseEvaluatorNull() {
    var mg = MetaGrammar.load("""
        grammar {
          S : 'a'
        }
        """);
    var grammar = mg.grammar();
    var parser = Parser.createParser(grammar, Map.of());
    var tokens = Collections.<Terminal>emptyIterator();
    assertThrows(NullPointerException.class, () ->
        parser.parse(tokens, (Evaluator<?>) null));
  }

  @Test
  public void parseListenerInputNull() {
    var mg = MetaGrammar.load("""
        grammar {
          S : 'x'
        }
        """);
    var grammar = mg.grammar();
    var parser = Parser.createParser(grammar, Map.of());
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
  public void parseListenerNull() {
    var mg = MetaGrammar.load("""
        grammar {
          S : 'x'
        }
        """);
    var grammar = mg.grammar();
    var parser = Parser.createParser(grammar, Map.of());
    var tokens = Collections.<Terminal>emptyIterator();
    assertThrows(NullPointerException.class, () ->
        parser.parse(tokens, (ParserListener) null));
  }


  @Test
  public void simple() {
    var metaGrammar = MetaGrammar.load("""
        precedence {
          left: '+'
          left: '*'
        }
        grammar {
          E: E '+' E
          E: E '*' E
          E: id
        }
        """);

    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var id   = new Terminal("id");

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    assertEquals("""
        Shift id
        Reduce E : id
        Shift +
        Shift id
        Reduce E : id
        Reduce E : E + E
        Shift +
        Shift id
        Reduce E : id
        Shift *
        Shift id
        Reduce E : id
        Reduce E : E * E
        Reduce E : E + E
        Reduce E' : E
        """, parse(grammar, precedence, List.of(id, plus, id, plus, id, mul, id)));
  }

  @Test
  public void singleId() {
    var metaGrammar = MetaGrammar.load("""
        grammar {
          E: id
        }
        """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var id = new Terminal("id");

    assertEquals("""
        Shift id
        Reduce E : id
        Reduce E' : E
        """, parse(grammar, precedence, List.of(id)));
  }

  @Test
  public void emptyProduction() {
    var metaGrammar = MetaGrammar.load("""
        precedence {
          left: '+'
        }
        grammar {
          E: E '+' E
          E: id
          E:
        }
        """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var plus = new Terminal("+");
    var id   = new Terminal("id");

    // id + ε  =>  the second operand is empty, reducing to E via ε-production
    assertEquals("""
      Shift id
      Reduce E : id
      Shift +
      Reduce E : ε
      Reduce E : E + E
      Reduce E' : E
      """, parse(grammar, precedence, List.of(id, plus)));
  }

  @Test
  public void emptyProduction2() {
    var metaGrammar = MetaGrammar.load("""
        precedence {
          left: '+'
        }
        grammar {
          E: E '+' E
          E: id
          E:
        }
        """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var plus = new Terminal("+");
    var id   = new Terminal("id");

    // ε + id => the first operand is empty, reducing to E via ε-production
    assertEquals("""
      Reduce E : ε
      Shift +
      Shift id
      Reduce E : id
      Reduce E : E + E
      Reduce E' : E
      """, parse(grammar, precedence, List.of(plus, id)));
  }

  @Test
  public void leftAssociativityPlus() {
    var metaGrammar = MetaGrammar.load("""
        precedence {
          left: '+'
        }
        grammar {
          E: E '+' E
          E: id
        }
        """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var plus = new Terminal("+");
    var id   = new Terminal("id");

    assertEquals("""
        Shift id
        Reduce E : id
        Shift +
        Shift id
        Reduce E : id
        Reduce E : E + E
        Shift +
        Shift id
        Reduce E : id
        Reduce E : E + E
        Reduce E' : E
        """, parse(grammar, precedence, List.of(id, plus, id, plus, id)));
  }

  @Test
  public void rightAssociativityPow() {
    var metaGrammar = MetaGrammar.load("""
        precedence {
          right: '^'
        }
        grammar {
          E: E '^' E
          E: id
        }
        """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var pow = new Terminal("^");
    var id  = new Terminal("id");

    // id ^ id ^ id  =>  id ^ (id ^ id)
    assertEquals("""
        Shift id
        Reduce E : id
        Shift ^
        Shift id
        Reduce E : id
        Shift ^
        Shift id
        Reduce E : id
        Reduce E : E ^ E
        Reduce E : E ^ E
        Reduce E' : E
        """, parse(grammar, precedence, List.of(id, pow, id, pow, id)));
  }

  @Test
  public void multiplyHasHigherPrecedenceThanPlus() {
    var metaGrammar = MetaGrammar.load("""
        precedence {
          left: '+'
          left: '*'
        }
        grammar {
          E: E '+' E
          E: E '*' E
          E: id
        }
        """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var id   = new Terminal("id");

    // id + id * id  =>  id + (id * id)
    assertEquals("""
        Shift id
        Reduce E : id
        Shift +
        Shift id
        Reduce E : id
        Shift *
        Shift id
        Reduce E : id
        Reduce E : E * E
        Reduce E : E + E
        Reduce E' : E
        """, parse(grammar, precedence, List.of(id, plus, id, mul, id)));
  }

  @Test
  public void multiplyHasHigherPrecedenceThanPlus2() {
    var metaGrammar = MetaGrammar.load("""
        precedence {
          left: '+'
          left: '*'
        }
        grammar {
          E: E '+' E
          E: E '*' E
          E: id
        }
        """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var id   = new Terminal("id");

    // id * id + id  =>  (id * id) + id
    assertEquals("""
        Shift id
        Reduce E : id
        Shift *
        Shift id
        Reduce E : id
        Reduce E : E * E
        Shift +
        Shift id
        Reduce E : id
        Reduce E : E + E
        Reduce E' : E
        """, parse(grammar, precedence, List.of(id, mul, id, plus, id)));
  }

  @Test
  public void threeLevelPrecedence() {
    var metaGrammar = MetaGrammar.load("""
        precedence {
          left:  '+'
          left:  '*'
          right: '^'
        }
        grammar {
          E: E '+' E
          E: E '*' E
          E: E '^' E
          E: id
        }
        """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var pow  = new Terminal("^");
    var id   = new Terminal("id");

    // id + id * id ^ id  =>  id + (id * (id ^ id))
    assertEquals("""
        Shift id
        Reduce E : id
        Shift +
        Shift id
        Reduce E : id
        Shift *
        Shift id
        Reduce E : id
        Shift ^
        Shift id
        Reduce E : id
        Reduce E : E ^ E
        Reduce E : E * E
        Reduce E : E + E
        Reduce E' : E
        """, parse(grammar, precedence, List.of(id, plus, id, mul, id, pow, id)));
  }

  @Test
  public void samePrecedenceMixedOperators() {
    var metaGrammar = MetaGrammar.load("""
        precedence {
          left: '+', '-'
        }
        grammar {
          E: E '+' E
          E: E '-' E
          E: id
        }
        """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var plus = new Terminal("+");
    var sub  = new Terminal("-");
    var id   = new Terminal("id");

    // id + id - id  =>  (id + id) - id
    assertEquals("""
        Shift id
        Reduce E : id
        Shift +
        Shift id
        Reduce E : id
        Reduce E : E + E
        Shift -
        Shift id
        Reduce E : id
        Reduce E : E - E
        Reduce E' : E
        """, parse(grammar, precedence, List.of(id, plus, id, sub, id)));
  }

  @Test
  public void longLeftAssocChain() {
    var metaGrammar = MetaGrammar.load("""
        precedence {
          left: '+'
        }
        grammar {
          E: E '+' E
          E: id
        }
        """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var plus = new Terminal("+");
    var id   = new Terminal("id");

    assertEquals("""
        Shift id
        Reduce E : id
        Shift +
        Shift id
        Reduce E : id
        Reduce E : E + E
        Shift +
        Shift id
        Reduce E : id
        Reduce E : E + E
        Shift +
        Shift id
        Reduce E : id
        Reduce E : E + E
        Shift +
        Shift id
        Reduce E : id
        Reduce E : E + E
        Reduce E' : E
        """, parse(grammar, precedence,
        List.of(id, plus, id, plus, id, plus, id, plus, id)));
  }

  @Test
  public void longRightAssocChain() {
    var metaGrammar = MetaGrammar.load("""
        precedence {
          right: '^'
        }
        grammar {
          E: E '^' E
          E: id
        }
        """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var pow = new Terminal("^");
    var id  = new Terminal("id");

    // id ^ id ^ id ^ id  =>  id ^ (id ^ (id ^ id))
    assertEquals("""
        Shift id
        Reduce E : id
        Shift ^
        Shift id
        Reduce E : id
        Shift ^
        Shift id
        Reduce E : id
        Shift ^
        Shift id
        Reduce E : id
        Reduce E : E ^ E
        Reduce E : E ^ E
        Reduce E : E ^ E
        Reduce E' : E
        """, parse(grammar, precedence,
        List.of(id, pow, id, pow, id, pow, id)));
  }

  @Test
  public void fourOperatorExpression() {
    var metaGrammar = MetaGrammar.load("""
        precedence {
          left: '+', '-'
          left: '*', '/'
        }
        grammar {
          E: E '+' E
          E: E '-' E
          E: E '*' E
          E: E '/' E
          E: id
        }
        """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var plus = new Terminal("+");
    var sub  = new Terminal("-");
    var mul  = new Terminal("*");
    var div  = new Terminal("/");
    var id   = new Terminal("id");

    // id * id / id + id - id  =>  ((id * id) / id) + id) - id
    assertEquals("""
        Shift id
        Reduce E : id
        Shift *
        Shift id
        Reduce E : id
        Reduce E : E * E
        Shift /
        Shift id
        Reduce E : id
        Reduce E : E / E
        Shift +
        Shift id
        Reduce E : id
        Reduce E : E + E
        Shift -
        Shift id
        Reduce E : id
        Reduce E : E - E
        Reduce E' : E
        """, parse(grammar, precedence,
        List.of(id, mul, id, div, id, plus, id, sub, id)));
  }

  @Test
  public void jsonTest() {
    var metaGrammar = MetaGrammar.load("""
        grammar {
          Value: Object
          Value: Array
          Value: STRING
          Value: NUMBER
          Value: true
          Value: false
          Value: null
          Object: '{' '}'
          Object: '{' Members '}'
          Pair: STRING ':' Value
          Members: Pair
          Members: Members ',' Pair
          Array: '[' ']'
          Array: '[' Elements ']'
          Elements: Value
          Elements: Elements ',' Value
        }
        """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var objStart  = new Terminal("{");
    var objEnd    = new Terminal("}");
    var arrStart  = new Terminal("[");
    var arrEnd    = new Terminal("]");
    var comma     = new Terminal(",");
    var colon     = new Terminal(":");
    var string    = new Terminal("STRING");
    var number    = new Terminal("NUMBER");
    var boolTrue  = new Terminal("true");
    var boolFalse = new Terminal("false");
    var nullVal   = new Terminal("null");

    var input = List.of(
        objStart,
        string, colon, arrStart,
        boolFalse, comma,
        objStart,
        string, colon, arrStart,
        boolTrue, comma, nullVal, comma, number,
        arrEnd,
        objEnd, comma,
        string,
        arrEnd, comma,
        string, colon, objStart,
        string, colon, objStart, objEnd,
        objEnd,
        objEnd
    );

    assertEquals("""
        Shift {
        Shift STRING
        Shift :
        Shift [
        Shift false
        Reduce Value : false
        Reduce Elements : Value
        Shift ,
        Shift {
        Shift STRING
        Shift :
        Shift [
        Shift true
        Reduce Value : true
        Reduce Elements : Value
        Shift ,
        Shift null
        Reduce Value : null
        Reduce Elements : Elements , Value
        Shift ,
        Shift NUMBER
        Reduce Value : NUMBER
        Reduce Elements : Elements , Value
        Shift ]
        Reduce Array : [ Elements ]
        Reduce Value : Array
        Reduce Pair : STRING : Value
        Reduce Members : Pair
        Shift }
        Reduce Object : { Members }
        Reduce Value : Object
        Reduce Elements : Elements , Value
        Shift ,
        Shift STRING
        Reduce Value : STRING
        Reduce Elements : Elements , Value
        Shift ]
        Reduce Array : [ Elements ]
        Reduce Value : Array
        Reduce Pair : STRING : Value
        Reduce Members : Pair
        Shift ,
        Shift STRING
        Shift :
        Shift {
        Shift STRING
        Shift :
        Shift {
        Shift }
        Reduce Object : { }
        Reduce Value : Object
        Reduce Pair : STRING : Value
        Reduce Members : Pair
        Shift }
        Reduce Object : { Members }
        Reduce Value : Object
        Reduce Pair : STRING : Value
        Reduce Members : Members , Pair
        Shift }
        Reduce Object : { Members }
        Reduce Value : Object
        Reduce Value' : Value
        """, parse(grammar, precedence, input));
  }

  @Test
  public void lr1ButNotLalr1GrammarTest() {
    // The classic grammar that is LR(1) but NOT LALR(1):

    var metaGrammar = MetaGrammar.load("""
      grammar {
        S: a E c
        S: a F d
        S: b F c
        S: b E d
        E: e
        F: e
      }
      """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    // In LR(1), the states for "e" after "a" and "e" after "b" are kept separate
    // because their lookaheads differ:
    //   - After "a": reduce E→e on 'c', reduce F→e on 'd'
    //   - After "b": reduce F→e on 'c', reduce E→e on 'd'
    //
    // In LALR(1), those two states get MERGED (same LR(0) core: E→e•, F→e•),
    // combining lookaheads into {c, d} for BOTH E→e and F→e — a reduce/reduce conflict.

    var conflicts = new ArrayList<String>();
    LALRVerifier.verify(grammar, Map.of(), conflicts::add);
    assertEquals(2, conflicts.size());

    var parser = Parser.createParser(grammar, precedence);
    var evaluator = new Evaluator<String>() {
      @Override
      public String evaluate(Terminal token) {
        return token.name();
      }

      @Override
      public String evaluate(Production production, List<String> args) {
        return production.head().name() + "(" + String.join(", ", args) + ")";
      }
    };

    var a = new Terminal("a");
    var b = new Terminal("b");
    var c = new Terminal("c");
    var d = new Terminal("d");
    var e = new Terminal("e");

    // "a e c" → S → a E c, E → e   (LR(1) knows to reduce e to E here, not F)
    var result1 = parser.parse(List.of(a, e, c).iterator(), evaluator);
    assertEquals("S(a, E(e), c)", result1);

    // "a e d" → S → a F d, F → e   (LR(1) knows to reduce e to F here, not E)
    var result2 = parser.parse(List.of(a, e, d).iterator(), evaluator);
    assertEquals("S(a, F(e), d)", result2);

    // "b e c" → S → b F c, F → e
    var result3 = parser.parse(List.of(b, e, c).iterator(), evaluator);
    assertEquals("S(b, F(e), c)", result3);

    // "b e d" → S → b E d, E → e
    var result4 = parser.parse(List.of(b, e, d).iterator(), evaluator);
    assertEquals("S(b, E(e), d)", result4);
  }


  @Test
  public void productionPrecedenceGrammarTest() {
    var mg = MetaGrammar.load("""
        precedence {
          left: '+', '-'
          right: UNARY
        }
        grammar {
          E: E '+' E
          E: E '-' E
          E: '+' E     %prec UNARY
          E: '-' E     %prec UNARY
          E: id
        }
        """);

    var E     = new NonTerminal("E");
    var id    = new Terminal("id");
    var plus  = new Terminal("+");
    var minus = new Terminal("-");

    var grammar = mg.grammar();
    var precedence = mg.precedenceMap();

    var input = List.of(id, plus, id, minus, plus, id);

    assertEquals("""
        Shift id
        Reduce E : id
        Shift +
        Shift id
        Reduce E : id
        Reduce E : E + E
        Shift -
        Shift +
        Shift id
        Reduce E : id
        Reduce E : + E
        Reduce E : E - E
        Reduce E' : E
        """, parse(grammar, precedence, input));
  }


  @Test
  public void parsingErrorBasic() {
    var metaGrammar = MetaGrammar.load("""
      precedence {
        left: '+'
      }
      grammar {
        E: E '+' E
        E: id
      }
      """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var parser = Parser.createParser(grammar, precedence);

    // Try to parse invalid input: "id id"
    var terminals = List.of(new Terminal("id"), new Terminal("id")).iterator();

    var exception = assertThrows(ParsingException.class, () -> {
      parser.parse(terminals, new ParserListener() {
        @Override public void onShift(Terminal token) {}
        @Override public void onReduce(Production production) {}
      });
    });

    var message = exception.getMessage();
    assertTrue(message.contains("Parsing error"));
    assertTrue(message.contains("'id'"));
    assertTrue(message.contains("expected"));
  }

  @Test
  public void lexingErrorUnknownCharacterWithPosition() {
    var metaGrammar = MetaGrammar.load("""
      precedence {
        left: '+'
      }
      grammar {
        E: E '+' E
        E: id
      }
      """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

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
    assertTrue(message.contains("Lexing error"));
    assertTrue(message.contains("line 1"));
    assertTrue(message.contains("column 6"));
    assertTrue(message.contains("id + 2"));
    assertTrue(message.contains("^"));
  }

  @Test
  public void parsingErrorNotAllowedByTheGrammarWithPosition() {
    var metaGrammar = MetaGrammar.load("""
      precedence {
        left: '+'
      }
      grammar {
        E: E '+' E
        E: id
      }
      """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

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
    assertTrue(message.contains("Parsing error"));
    assertTrue(message.contains("line 1"));
    assertTrue(message.contains("column 6"));
    assertTrue(message.contains("id + +"));
    assertTrue(message.contains("^"));
    assertTrue(message.contains("expected"));
  }

  @Test
  public void parsingErrorNotAllowedByTheGrammarWithMultipleLines() {
    var metaGrammar = MetaGrammar.load("""
      precedence {
        left: '+'
      }
      grammar {
        E: E '+' E
        E: id
      }
      """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

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
    assertTrue(message.contains("Parsing error"));
    assertTrue(message.contains("line 2"));
    assertTrue(message.contains("column 4"));
    assertTrue(message.contains("id + +"));
    assertTrue(message.contains("^"));
    assertTrue(message.contains("expected"));
  }

  @Test
  public void reduceReduceConflictThrows() {
    var metaGrammar = MetaGrammar.load("""
      grammar {
        S: A
        S: B
        A: id
        B: id
      }
      """);

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var id = new Terminal("id");

    var parser = Parser.createParser(grammar, precedence);

    assertThrows(ParsingException.class, () ->
        parser.parse(List.of(id).iterator(), new ParserListener() {
          @Override public void onShift(Terminal token) {}
          @Override public void onReduce(Production production) {}
        }));
  }

  @Test
  public void shiftReduceConflictNoPrecedenceThrows() {
    var metaGrammar = MetaGrammar.load("""
      grammar {
        E: E '+' E
        E: id
      }
      """);

    var id   = new Terminal("id");
    var plus = new Terminal("+");

    var grammar = metaGrammar.grammar();
    var precedence = metaGrammar.precedenceMap();

    var parser = Parser.createParser(grammar, precedence);

    assertThrows(ParsingException.class, () ->
        parser.parse(List.of(id, plus, id, plus, id).iterator(), new ParserListener() {
          @Override public void onShift(Terminal token) {}
          @Override public void onReduce(Production production) {}
        }));
  }
}