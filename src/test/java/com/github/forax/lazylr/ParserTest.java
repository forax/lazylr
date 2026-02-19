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
        Reduce E -> id
        Shift +
        Shift id
        Reduce E -> id
        Reduce E -> E + E
        Shift +
        Shift id
        Reduce E -> id
        Shift *
        Shift id
        Reduce E -> id
        Reduce E -> E * E
        Reduce E -> E + E
        Reduce E' -> E
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
        Reduce E -> id
        Reduce E' -> E
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
        new Production(E, List.of())          // E -> ε
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT)
    );

    // id + ε  =>  the second operand is empty, reducing to E via ε-production
    assertEquals("""
      Shift id
      Reduce E -> id
      Shift +
      Reduce E -> ε
      Reduce E -> E + E
      Reduce E' -> E
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
        new Production(E, List.of())          // E -> ε
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT)
    );

    // ε + id =>  the first operand is empty, reducing to E via ε-production
    assertEquals("""
      Reduce E -> ε
      Shift +
      Shift id
      Reduce E -> id
      Reduce E -> E + E
      Reduce E' -> E
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
        Reduce E -> id
        Shift +
        Shift id
        Reduce E -> id
        Reduce E -> E + E
        Shift +
        Shift id
        Reduce E -> id
        Reduce E -> E + E
        Reduce E' -> E
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
        Reduce E -> id
        Shift ^
        Shift id
        Reduce E -> id
        Shift ^
        Shift id
        Reduce E -> id
        Reduce E -> E ^ E
        Reduce E -> E ^ E
        Reduce E' -> E
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
        Reduce E -> id
        Shift +
        Shift id
        Reduce E -> id
        Shift *
        Shift id
        Reduce E -> id
        Reduce E -> E * E
        Reduce E -> E + E
        Reduce E' -> E
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
        Reduce E -> id
        Shift *
        Shift id
        Reduce E -> id
        Reduce E -> E * E
        Shift +
        Shift id
        Reduce E -> id
        Reduce E -> E + E
        Reduce E' -> E
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
        Reduce E -> id
        Shift +
        Shift id
        Reduce E -> id
        Shift *
        Shift id
        Reduce E -> id
        Shift ^
        Shift id
        Reduce E -> id
        Reduce E -> E ^ E
        Reduce E -> E * E
        Reduce E -> E + E
        Reduce E' -> E
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
        Reduce E -> id
        Shift +
        Shift id
        Reduce E -> id
        Reduce E -> E + E
        Shift -
        Shift id
        Reduce E -> id
        Reduce E -> E - E
        Reduce E' -> E
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
        Reduce E -> id
        Shift +
        Shift id
        Reduce E -> id
        Reduce E -> E + E
        Shift +
        Shift id
        Reduce E -> id
        Reduce E -> E + E
        Shift +
        Shift id
        Reduce E -> id
        Reduce E -> E + E
        Shift +
        Shift id
        Reduce E -> id
        Reduce E -> E + E
        Reduce E' -> E
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
        Reduce E -> id
        Shift ^
        Shift id
        Reduce E -> id
        Shift ^
        Shift id
        Reduce E -> id
        Shift ^
        Shift id
        Reduce E -> id
        Reduce E -> E ^ E
        Reduce E -> E ^ E
        Reduce E -> E ^ E
        Reduce E' -> E
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
        Reduce E -> id
        Shift *
        Shift id
        Reduce E -> id
        Reduce E -> E * E
        Shift /
        Shift id
        Reduce E -> id
        Reduce E -> E / E
        Shift +
        Shift id
        Reduce E -> id
        Reduce E -> E + E
        Shift -
        Shift id
        Reduce E -> id
        Reduce E -> E - E
        Reduce E' -> E
        """, parse(grammar, precedence,
        List.of(id, mul, id, div, id, plus, id, sub, id)));
  }
}
