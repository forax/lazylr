package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    // ε + id =>  the first operand is empty, reducing to E via ε-production
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
}
