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

/// Those are the same tests as in [MetaGrammarParserTest] but using objects
/// for terminals, non-terminals, productions, etc.
/// Please update both files accordingly
public final class ParserTest {

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
  @SuppressWarnings("DataFlowIssue")
  public void createParserGrammarNull() {
    assertThrows(NullPointerException.class, () ->
        Parser.createParser(null, Map.of()));
  }

  @Test
  @SuppressWarnings("DataFlowIssue")
  public void createParserPrecedenceNull() {
    var start = new NonTerminal("S");
    var grammar = new Grammar(start, List.of(
        new Production(start, List.of(new Terminal("a")))));
    assertThrows(NullPointerException.class, () ->
        Parser.createParser(grammar, null));
  }

  @Test
  @SuppressWarnings("DataFlowIssue")
  public void parseEvaluatorInputNull() {
    var start = new NonTerminal("S");
    var grammar = new Grammar(start, List.of(
        new Production(start, List.of(new Terminal("a")))));
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
  @SuppressWarnings("DataFlowIssue")
  public void parseEvaluatorNull() {
    var start = new NonTerminal("S");
    var grammar = new Grammar(start, List.of(
        new Production(start, List.of(new Terminal("a")))));
    var parser = Parser.createParser(grammar, Map.of());
    var tokens = Collections.<Terminal>emptyIterator();
    assertThrows(NullPointerException.class, () ->
        parser.parse(tokens, (Evaluator<?>) null));
  }

  @Test
  @SuppressWarnings("DataFlowIssue")
  public void parseListenerInputNull() {
    var start = new NonTerminal("S");
    var grammar = new Grammar(start, List.of(
        new Production(start, List.of(new Terminal("a")))));
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
  @SuppressWarnings("DataFlowIssue")
  public void parseListenerNull() {
    var start = new NonTerminal("S");
    var grammar = new Grammar(start, List.of(
        new Production(start, List.of(new Terminal("a")))));
    var parser = Parser.createParser(grammar, Map.of());
    var tokens = Collections.<Terminal>emptyIterator();
    assertThrows(NullPointerException.class, () ->
        parser.parse(tokens, (ParserListener) null));
  }


  @Test
  public void simple() {
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(E, mul,  E)),
        new Production(E, List.of(id))
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        mul,  new Precedence(20, Precedence.Associativity.LEFT)
    );

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
    var E  = new NonTerminal("E");
    var id = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(id))
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of();

    assertEquals("""
        Shift id
        Reduce E : id
        Reduce E' : E
        """, parse(grammar, precedence, List.of(id)));
  }

  @Test
  public void emptyProduction() {
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(id)),
        new Production(E, List.of())          // E : ε
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT)
    );

    // id + ε => the second operand is empty, reducing to E via ε-production
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
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(id)),
        new Production(E, List.of())          // E : ε
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT)
    );

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
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(id))
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT)
    );

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
    var E   = new NonTerminal("E");
    var pow = new Terminal("^");
    var id  = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, pow, E)),
        new Production(E, List.of(id))
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        pow, new Precedence(30, Precedence.Associativity.RIGHT)
    );

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
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(E, mul,  E)),
        new Production(E, List.of(id))
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        mul,  new Precedence(20, Precedence.Associativity.LEFT)
    );

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
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(E, mul,  E)),
        new Production(E, List.of(id))
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        mul,  new Precedence(20, Precedence.Associativity.LEFT)
    );

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
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var pow  = new Terminal("^");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(E, mul,  E)),
        new Production(E, List.of(E, pow,  E)),
        new Production(E, List.of(id))
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        mul,  new Precedence(20, Precedence.Associativity.LEFT),
        pow,  new Precedence(30, Precedence.Associativity.RIGHT)
    );

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
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var sub  = new Terminal("-");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(E, sub,  E)),
        new Production(E, List.of(id))
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        sub,  new Precedence(10, Precedence.Associativity.LEFT)
    );

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
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(id))
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT)
    );

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
    var E   = new NonTerminal("E");
    var pow = new Terminal("^");
    var id  = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, pow, E)),
        new Production(E, List.of(id))
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        pow, new Precedence(30, Precedence.Associativity.RIGHT)
    );

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
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var sub  = new Terminal("-");
    var mul  = new Terminal("*");
    var div  = new Terminal("/");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(E, sub,  E)),
        new Production(E, List.of(E, mul,  E)),
        new Production(E, List.of(E, div,  E)),
        new Production(E, List.of(id))
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        sub,  new Precedence(10, Precedence.Associativity.LEFT),
        mul,  new Precedence(20, Precedence.Associativity.LEFT),
        div,  new Precedence(20, Precedence.Associativity.LEFT)
    );

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
    // Terminals
    var objStart = new Terminal("{");
    var objEnd   = new Terminal("}");
    var arrStart = new Terminal("[");
    var arrEnd   = new Terminal("]");
    var comma    = new Terminal(",");
    var colon    = new Terminal(":");
    var string   = new Terminal("STRING");
    var number   = new Terminal("NUMBER");
    var boolTrue = new Terminal("true");
    var boolFalse= new Terminal("false");
    var nullVal  = new Terminal("null");

    // Non-Terminals
    var Value    = new NonTerminal("Value");
    var Object   = new NonTerminal("Object");
    var Array    = new NonTerminal("Array");
    var Members  = new NonTerminal("Members");
    var Elements = new NonTerminal("Elements");
    var Pair     = new NonTerminal("Pair");

    var grammar = new Grammar(Value, List.of(
        new Production(Value, List.of(Object)),
        new Production(Value, List.of(Array)),
        new Production(Value, List.of(string)),
        new Production(Value, List.of(number)),
        new Production(Value, List.of(boolTrue)),
        new Production(Value, List.of(boolFalse)),
        new Production(Value, List.of(nullVal)),

        new Production(Object,  List.of(objStart, objEnd)),
        new Production(Object,  List.of(objStart, Members, objEnd)),
        new Production(Pair,    List.of(string, colon, Value)),
        new Production(Members, List.of(Pair)),
        new Production(Members, List.of(Members, comma, Pair)),

        new Production(Array,    List.of(arrStart, arrEnd)),
        new Production(Array,    List.of(arrStart, Elements, arrEnd)),
        new Production(Elements, List.of(Value)),
        new Production(Elements, List.of(Elements, comma, Value))
    ));

    var precedence = Map.<PrecedenceEntity, Precedence>of();

    // Input: {"a": [false, {"b": [true, null, 123]}, "nested"], "c": {"d": {}}}
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

    var a = new Terminal("a");
    var b = new Terminal("b");
    var c = new Terminal("c");
    var d = new Terminal("d");
    var e = new Terminal("e");
    var S = new NonTerminal("S");
    var E = new NonTerminal("E");
    var F = new NonTerminal("F");

    var pSaEc = new Production(S, List.of(a, E, c));
    var pSaFd = new Production(S, List.of(a, F, d));
    var pSbFc = new Production(S, List.of(b, F, c));
    var pSbEd = new Production(S, List.of(b, E, d));
    var pEe   = new Production(E, List.of(e));
    var pFe   = new Production(F, List.of(e));

    var grammar = new Grammar(S, List.of(pSaEc, pSaFd, pSbFc, pSbEd, pEe, pFe));

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

    var parser = Parser.createParser(grammar, Map.of());
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
    var E     = new NonTerminal("E");
    var id    = new Terminal("id");
    var plus  = new Terminal("+");
    var minus = new Terminal("-");

    var pEplusE = new Production(E, List.of(E, plus, E));
    var pEminusE = new Production(E, List.of(E, minus, E));
    var pPlusE = new Production(E, List.of(plus, E));
    var pMinusE = new Production(E, List.of(minus, E));
    var pEid = new Production(E, List.of(id));
    var grammar = new Grammar(E, List.of(pEplusE, pEminusE, pPlusE, pMinusE, pEid));

    var precedence = Map.of(
        plus, new Precedence(1, Precedence.Associativity.LEFT),
        minus, new Precedence(1, Precedence.Associativity.LEFT),
        pPlusE, new Precedence(2, Precedence.Associativity.RIGHT),
        pMinusE, new Precedence(2, Precedence.Associativity.RIGHT)
    );

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

    // Try to parse invalid input: "id id"
    var terminals = List.of(new Terminal("id"), new Terminal("id")).iterator();

    var exception = assertThrows(ParsingException.class, () ->
      parser.parse(terminals, new ParserListener() {
        @Override public void onShift(Terminal token) {}
        @Override public void onReduce(Production production) {}
      })
    );

    var message = exception.getMessage();
    assertTrue(message.contains("Parsing error"));
    assertTrue(message.contains("unexpected terminal 'id'"));
    assertTrue(message.contains("expected '+', <end of file>"));
  }

  @Test
  public void lexingErrorUnknownCharacterWithPosition() {
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

    var exception = assertThrows(ParsingException.class, () ->
      parser.parse(terminals, new ParserListener() {
        @Override public void onShift(Terminal token) {}
        @Override public void onReduce(Production production) {}
      })
    );

    var message = exception.getMessage();
    assertTrue(message.contains("Lexing error"));
    assertTrue(message.contains("unexpected character '2'"));
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

    var exception = assertThrows(ParsingException.class, () ->
      parser.parse(terminals, new ParserListener() {
        @Override public void onShift(Terminal token) {}
        @Override public void onReduce(Production production) {}
      })
    );

    var message = exception.getMessage();
    assertTrue(message.contains("Parsing error"));
    assertTrue(message.contains("unexpected terminal '+'"));
    assertTrue(message.contains("expected id"));
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
        new Token("[\\s\n]+")
    );
    var lexer = Lexer.createLexer(tokens);

    var input = """
        id +
        id + +
        id
        """;
    var terminals = lexer.tokenize(input);

    var exception = assertThrows(ParsingException.class, () ->
      parser.parse(terminals, new ParserListener() {
        @Override public void onShift(Terminal token) {}
        @Override public void onReduce(Production production) {}
      })
    );

    var message = exception.getMessage();
    assertTrue(message.contains("Parsing error"));
    assertTrue(message.contains("unexpected terminal '+'"));
    assertTrue(message.contains("expected id"));
    assertTrue(message.contains("line 2"));
    assertTrue(message.contains("column 6"));
    assertTrue(message.contains("id + +"));
    assertTrue(message.contains("^"));
  }

  @Test
  public void contextSensitiveLexingFallbackKeepsParsingError() {
    var S = new NonTerminal("S");
    var eqeq = new Terminal("==");
    var grammar = new Grammar(S, List.of(
        new Production(S, List.of(eqeq))
    ));
    var parser = Parser.createParser(grammar, Map.of());
    var lexer = Lexer.createLexer(List.of(
        new Token("==", "=="),
        new Token("=", "=")
    ));

    var exception = assertThrows(ParsingException.class, () ->
        parser.parse(lexer.tokenize("="), new ParserListener() {
          @Override
          public void onShift(Terminal token) {}
          @Override
          public void onReduce(Production production) {}
        }));

    var message = exception.getMessage();
    assertTrue(message.contains("Parsing error at line 1"));
    assertTrue(message.contains("unexpected terminal '='"));
    assertTrue(message.contains("expected '=='"));
  }

  @Test
  public void parseListenerLongReduceChain() {
    var S = new NonTerminal("S");
    var id = new Terminal("id");

    var productions = new ArrayList<Production>();
    productions.add(new Production(S, List.of(new NonTerminal("S0"))));
    for(int i = 0; i < 64; i++) {
      var production = new Production(
          new NonTerminal("S" + i),
          List.of(new NonTerminal("S" + (i + 1))));
      productions.add(production);
    }
    productions.add(new Production(new NonTerminal("S64"), List.of(id)));

    var grammar = new Grammar(S, productions);
    var parser = Parser.createParser(grammar, Map.of());
    var input = List.of(id);

    var listener = new ParserListener() {
      private int shiftCount;
      private int reduceCount;

      @Override
      public void onShift(Terminal token) {
        shiftCount++;
      }

      @Override
      public void onReduce(Production production) {
        reduceCount++;
      }
    };
    parser.parse(input.iterator(), listener);

    assertEquals(1, listener.shiftCount);
    assertEquals(67, listener.reduceCount);
  }

  @Test
  public void parseListenerResizesInternalStateStack() {
    var S = new NonTerminal("S");
    var id = new Terminal("id");
    var grammar = new Grammar(S,
        List.of(new Production(S, Collections.nCopies(64, id)))
    );
    var parser = Parser.createParser(grammar, Map.of());
    var input = Collections.nCopies(64, id);

    var listener = new ParserListener() {
      private int shiftCount;
      private int reduceCount;

      @Override
      public void onShift(Terminal token) {
        shiftCount++;
      }

      @Override
      public void onReduce(Production production) {
        reduceCount++;
      }
    };
    parser.parse(input.iterator(), listener);

    assertEquals(64, listener.shiftCount);
    assertEquals(2, listener.reduceCount);
  }

  @Test
  public void parseEvaluatorResizesInternalValueStack() {
    var S = new NonTerminal("S");
    var id = new Terminal("id");
    var grammar = new Grammar(S,
        List.of(new Production(S, Collections.nCopies(64, id)))
    );
    var parser = Parser.createParser(grammar, Map.of());
    var input = Collections.nCopies(64, id);

    var result = parser.parse(input.iterator(), new Evaluator<Integer>() {
      @Override
      public Integer evaluate(Terminal terminal) {
        return 1;
      }

      @Override
      public Integer evaluate(Production production, List<Integer> args) {
        return args.stream().mapToInt(v -> v).sum();
      }
    });

    assertEquals(64, result);
  }

  @Test
  public void reduceReduceConflictDuplicateProductionThrows() {
    var E    = new NonTerminal("E");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(id)),
        new Production(E, List.of(id))
    ));

    var parser = Parser.createParser(grammar, Map.of());

    assertThrows(ParsingException.class, () ->
        parser.parse(List.of(id).iterator(), new ParserListener() {
          @Override public void onShift(Terminal token) {}
          @Override public void onReduce(Production production) {}
        }));
  }

  @Test
  public void reduceReduceConflictThrows() {
    var S  = new NonTerminal("S");
    var A  = new NonTerminal("A");
    var B  = new NonTerminal("B");
    var id = new Terminal("id");

    var grammar = new Grammar(S, List.of(
        new Production(S, List.of(A)),
        new Production(S, List.of(B)),
        new Production(A, List.of(id)),
        new Production(B, List.of(id))
    ));

    var parser = Parser.createParser(grammar, Map.of());

    assertThrows(ParsingException.class, () ->
        parser.parse(List.of(id).iterator(), new ParserListener() {
          @Override public void onShift(Terminal token) {}
          @Override public void onReduce(Production production) {}
        }));
  }

  @Test
  public void reduceReduceConflictWithShiftThrows() {
    var S  = new NonTerminal("S");
    var A  = new NonTerminal("A");
    var B  = new NonTerminal("B");
    var id = new Terminal("id");

    var grammar = new Grammar(S, List.of(
        new Production(S, List.of(A, id)),
        new Production(S, List.of(B, id)),
        new Production(S, List.of(id, id)),
        new Production(A, List.of(id)),
        new Production(B, List.of(id))
    ));

    var parser = Parser.createParser(grammar, Map.of());

    assertThrows(ParsingException.class, () ->
        parser.parse(List.of(id, id).iterator(), new ParserListener() {
          @Override public void onShift(Terminal token) {}
          @Override public void onReduce(Production production) {}
        }));
  }

  @Test
  public void reduceReduceConflictWithAHigherShiftStillThrows() {
    var S  = new NonTerminal("S");
    var A  = new NonTerminal("A");
    var B  = new NonTerminal("B");
    var id = new Terminal("id");

    Production aId, bId;

    var grammar = new Grammar(S, List.of(
              new Production(S, List.of(A, id)),
              new Production(S, List.of(B, id)),
              new Production(S, List.of(id, id)),
        aId = new Production(A, List.of(id)),
        bId = new Production(B, List.of(id))
    ));

    var precedenceMap = Map.of(
        aId, new Precedence(1, Precedence.Associativity.LEFT),
        bId, new Precedence(2, Precedence.Associativity.LEFT),
        id, new Precedence(3, Precedence.Associativity.LEFT)
    );

    var parser = Parser.createParser(grammar, precedenceMap);

    assertThrows(ParsingException.class, () ->
        parser.parse(List.of(id, id).iterator(), new ParserListener() {
          @Override public void onShift(Terminal token) {}
          @Override public void onReduce(Production production) {}
        }));
  }

  @Test
  public void shiftReduceConflictNoPrecedenceThrows() {
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(id))
    ));

    var parser = Parser.createParser(grammar, Map.of());

    assertThrows(ParsingException.class, () ->
        parser.parse(List.of(id, plus, id, plus, id).iterator(), new ParserListener() {
          @Override public void onShift(Terminal token) {}
          @Override public void onReduce(Production production) {}
        }));
  }

  @Test
  public void javadocDemo() {
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var id   = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(E, mul, E)),
        new Production(E, List.of(id))
    ));

    var precedenceMap = Map.of(
      plus, new Precedence(1, Precedence.Associativity.LEFT),
      mul, new Precedence(2, Precedence.Associativity.LEFT)
    );

    var parser = Parser.createParser(grammar, precedenceMap);
    var input = List.of(id, plus, id, mul, id).iterator();

    parser.parse(input, new ParserListener() {
      @Override public void onShift(Terminal terminal) {
        //System.out.println("shift " + terminal);
      }
      @Override public void onReduce(Production production) {
        //System.out.println("reduce " + production);
      }
    });

    //shift Terminal(id)
    //reduce E : id
    //shift Terminal(+)
    //shift Terminal(id)
    //reduce E : id
    //shift Terminal(*)
    //shift Terminal(id)
    //reduce E : id
    //reduce E : E * E
    //reduce E : E + E
    //reduce E' : E
  }
}