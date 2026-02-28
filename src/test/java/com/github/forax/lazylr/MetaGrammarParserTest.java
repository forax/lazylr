package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Those are the same tests as in [ParserTest] but using the meta grammar DSL
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
  public void simple() {
    var metaGrammar = MetaGrammar.create("""
        tokens {
          id: /id/
        }
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
        """, parse(metaGrammar.grammar(), metaGrammar.precedenceMap(),
        List.of(id, plus, id, plus, id, mul, id)));
  }

  @Test
  public void singleId() {
    var metaGrammar = MetaGrammar.create("""
        tokens {
          id: /id/
        }
        grammar {
          E: id
        }
        """);

    var id = new Terminal("id");

    assertEquals("""
        Shift id
        Reduce E : id
        Reduce E' : E
        """, parse(metaGrammar.grammar(), metaGrammar.precedenceMap(),
        List.of(id)));
  }

  @Test
  public void emptyProduction() {
    var metaGrammar = MetaGrammar.create("""
        tokens {
          id: /id/
        }
        precedence {
          left: '+'
        }
        grammar {
          E: E '+' E
          E: id
          E:
        }
        """);

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
      """, parse(metaGrammar.grammar(), metaGrammar.precedenceMap(),
        List.of(id, plus)));
  }

  @Test
  public void emptyProduction2() {
    var metaGrammar = MetaGrammar.create("""
        tokens {
          id: /id/
        }
        precedence {
          left: '+'
        }
        grammar {
          E: E '+' E
          E: id
          E:
        }
        """);

    var plus = new Terminal("+");
    var id   = new Terminal("id");

    // ε + id =>  the first operand is empty, reducing to E via ε-production
    assertEquals("""
      Reduce E : ε
      Shift +
      Shift id
      Reduce E : id
      Reduce E : E + E
      Reduce E' : E
      """, parse(metaGrammar.grammar(), metaGrammar.precedenceMap(),
        List.of(plus, id)));
  }

  @Test
  public void leftAssociativityPlus() {
    var metaGrammar = MetaGrammar.create("""
        tokens {
          id: /id/
        }
        precedence {
          left: '+'
        }
        grammar {
          E: E '+' E
          E: id
        }
        """);

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
        """, parse(metaGrammar.grammar(), metaGrammar.precedenceMap(),
        List.of(id, plus, id, plus, id)));
  }

  @Test
  public void rightAssociativityPow() {
    var metaGrammar = MetaGrammar.create("""
        tokens {
          id: /id/
        }
        precedence {
          right: '^'
        }
        grammar {
          E: E '^' E
          E: id
        }
        """);

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
        """, parse(metaGrammar.grammar(), metaGrammar.precedenceMap(),
        List.of(id, pow, id, pow, id)));
  }

  @Test
  public void multiplyHasHigherPrecedenceThanPlus() {
    var metaGrammar = MetaGrammar.create("""
        tokens {
          id: /id/
        }
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
        """, parse(metaGrammar.grammar(), metaGrammar.precedenceMap(),
        List.of(id, plus, id, mul, id)));
  }

  @Test
  public void multiplyHasHigherPrecedenceThanPlus2() {
    var metaGrammar = MetaGrammar.create("""
        tokens {
          id: /id/
        }
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
        """, parse(metaGrammar.grammar(), metaGrammar.precedenceMap(),
        List.of(id, mul, id, plus, id)));
  }

  @Test
  public void threeLevelPrecedence() {
    var metaGrammar = MetaGrammar.create("""
        tokens {
          id: /id/
        }
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
        """, parse(metaGrammar.grammar(), metaGrammar.precedenceMap(),
        List.of(id, plus, id, mul, id, pow, id)));
  }

  @Test
  public void samePrecedenceMixedOperators() {
    var metaGrammar = MetaGrammar.create("""
        tokens {
          id: /id/
        }
        precedence {
          left: '+', '-'
        }
        grammar {
          E: E '+' E
          E: E '-' E
          E: id
        }
        """);

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
        """, parse(metaGrammar.grammar(), metaGrammar.precedenceMap(),
        List.of(id, plus, id, sub, id)));
  }

  @Test
  public void longLeftAssocChain() {
    var metaGrammar = MetaGrammar.create("""
        tokens {
          id: /id/
        }
        precedence {
          left: '+'
        }
        grammar {
          E: E '+' E
          E: id
        }
        """);

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
        """, parse(metaGrammar.grammar(), metaGrammar.precedenceMap(),
        List.of(id, plus, id, plus, id, plus, id, plus, id)));
  }

  @Test
  public void longRightAssocChain() {
    var metaGrammar = MetaGrammar.create("""
        tokens {
          id: /id/
        }
        precedence {
          right: '^'
        }
        grammar {
          E: E '^' E
          E: id
        }
        """);

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
        """, parse(metaGrammar.grammar(), metaGrammar.precedenceMap(),
        List.of(id, pow, id, pow, id, pow, id)));
  }

  @Test
  public void fourOperatorExpression() {
    var metaGrammar = MetaGrammar.create("""
        tokens {
          id: /id/
        }
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
        """, parse(metaGrammar.grammar(), metaGrammar.precedenceMap(),
        List.of(id, mul, id, div, id, plus, id, sub, id)));
  }

  @Test
  public void jsonTest() {
    var metaGrammar = MetaGrammar.create("""
        tokens {
          STRING: /STRING/
          NUMBER: /NUMBER/
          true:   /true/
          false:  /false/
          null:   /null/
        }
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
        """, parse(metaGrammar.grammar(), metaGrammar.precedenceMap(), input));
  }
}