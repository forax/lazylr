package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unused")
public final class VisitorTest {

  @Test
  public void reflectObject() {
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var num  = new Terminal("num");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(E, mul,  E)),
        new Production(E, List.of(num))
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        mul,  new Precedence(20, Precedence.Associativity.LEFT)
    );
    var lexer = Lexer.createLexer(List.of(
        new Token("+",   "\\+"),
        new Token("*",   "\\*"),
        new Token("num", "[0-9]+"),
        new Token(" +")
    ));
    var parser = Parser.createParser(grammar, precedence);

    var evaluator = Visitor.reflect(MethodHandles.lookup(), new Visitor<Integer>() {
      public int num(Terminal terminal) {
        return Integer.parseInt(terminal.value());
      }

      @ProductionName("E : E + E")
      public int add(int a, int b) { return a + b; }

      @ProductionName("E : E * E")
      public int mul(int a, int b) { return a * b; }
    });

    var result = parser.parse(lexer.tokenize("2 + 3 * 5"), evaluator);
    assertEquals(17, result);
  }

  @Test
  public void reflectObjectSingleNumber() {
    var E   = new NonTerminal("E");
    var num = new Terminal("num");
    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(num))));
    var parser = Parser.createParser(grammar, Map.of());

    var visitor = new Visitor<Integer>() {
      public int num(Terminal t) { return Integer.parseInt(t.value()); }
    };
    var evaluator = Visitor.reflect(MethodHandles.lookup(), visitor);

    var result = parser.parse(
        List.of(new Terminal("num", "42")).iterator(),
        evaluator);
    assertEquals(42, result);
  }

  @Test
  public void reflectObjectTerminalMethodWithStringReturnType() {
    var E   = new NonTerminal("E");
    var num = new Terminal("num");
    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(num))));
    var parser = Parser.createParser(grammar, Map.of());

    var visitor = new Visitor<String>() {
      public String num(Terminal t) { return t.value(); }
    };
    var evaluator = Visitor.reflect(MethodHandles.lookup(), visitor);

    var result = parser.parse(
        List.of(new Terminal("num", "hello")).iterator(),
        evaluator);
    assertEquals("hello", result);
  }

  @Test
  public void reflectObjectUnknownTerminalNameReturnsNull() {
    var E  = new NonTerminal("E");
    var id = new Terminal("id");
    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(id))));
    var parser = Parser.createParser(grammar, Map.of());

    // visitor has no method named "id", so evaluate(Terminal) should return null
    var visitor = new Visitor<Integer>() {
      public int num(Terminal t) { return Integer.parseInt(t.value()); }
    };
    var evaluator = Visitor.reflect(MethodHandles.lookup(), visitor);

    var result = parser.parse(
        List.of(new Terminal("id", "x")).iterator(),
        evaluator);
    assertNull(result);
  }


  @Test
  public void reflectObjectBinaryAddition() {
    var E    = new NonTerminal("E");
    var num  = new Terminal("num");
    var plus = new Terminal("+");
    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(num)),
        new Production(E, List.of(E, plus, E))));
    var parser = Parser.createParser(grammar,
        Map.of(plus, new Precedence(10, Precedence.Associativity.LEFT)));

    var visitor = new Visitor<Integer>() {
      public int num(Terminal t) { return Integer.parseInt(t.value()); }

      @ProductionName("E : E + E")
      public int add(int a, int b) { return a + b; }
    };
    var evaluator = Visitor.reflect(MethodHandles.lookup(), visitor);

    var result = parser.parse(
        List.of(
            new Terminal("num", "3"), new Terminal("+", "+"),
            new Terminal("num", "4")
        ).iterator(), evaluator);
    assertEquals(7, result);
  }

  @Test
  public void reflectObjectLeftAssociativity() {
    var E    = new NonTerminal("E");
    var num  = new Terminal("num");
    var plus = new Terminal("+");
    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(num)),
        new Production(E, List.of(E, plus, E))));
    var parser = Parser.createParser(grammar,
        Map.of(plus, new Precedence(10, Precedence.Associativity.LEFT)));

    var visitor = new Visitor<Integer>() {
      public int num(Terminal t) { return Integer.parseInt(t.value()); }

      @ProductionName("E : E + E")
      public int add(int a, int b) { return a + b; }
    };
    var evaluator = Visitor.reflect(MethodHandles.lookup(), visitor);

    var result = parser.parse(
        List.of(
            new Terminal("num", "1"), new Terminal("+", "+"),
            new Terminal("num", "2"), new Terminal("+", "+"),
            new Terminal("num", "3")
        ).iterator(), evaluator);
    assertEquals(6, result);
  }

  @Test
  public void reflectObjectMultiplicationHasHigherPrecedenceThanAddition() {
    var E    = new NonTerminal("E");
    var num  = new Terminal("num");
    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(num)),
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(E, mul,  E))));
    var parser = Parser.createParser(grammar, Map.of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        mul,  new Precedence(20, Precedence.Associativity.LEFT)));

    var visitor = new Visitor<Integer>() {
      public int num(Terminal t) { return Integer.parseInt(t.value()); }

      @ProductionName("E : E + E")
      public int add(int a, int b) { return a + b; }

      @ProductionName("E : E * E")
      public int mul(int a, int b) { return a * b; }
    };
    var evaluator = Visitor.reflect(MethodHandles.lookup(), visitor);

    // 2 + 3 * 4 = 14, not 20
    var result = parser.parse(
        List.of(
            new Terminal("num", "2"), new Terminal("+", "+"),
            new Terminal("num", "3"), new Terminal("*", "*"),
            new Terminal("num", "4")
        ).iterator(), evaluator);
    assertEquals(14, result);
  }

  @Test
  public void reflectObjectRightAssociativity() {
    var E   = new NonTerminal("E");
    var num = new Terminal("num");
    var pow = new Terminal("^");
    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(num)),
        new Production(E, List.of(E, pow, E))));
    var parser = Parser.createParser(grammar,
        Map.of(pow, new Precedence(30, Precedence.Associativity.RIGHT)));

    var visitor = new Visitor<Integer>() {
      public int num(Terminal t) { return Integer.parseInt(t.value()); }

      @ProductionName("E : E ^ E")
      public int pow(int base, int exp) { return (int) Math.pow(base, exp); }
    };
    var evaluator = Visitor.reflect(MethodHandles.lookup(), visitor);

    // 2 ^ 3 ^ 2 = 2 ^ (3 ^ 2) = 2 ^ 9 = 512
    var result = parser.parse(
        List.of(new Terminal("num", "2"), new Terminal("^", "^"),
                new Terminal("num", "3"), new Terminal("^", "^"),
                new Terminal("num", "2")).iterator(), evaluator);
    assertEquals(512, result);
  }

  @Test
  public void reflectObjectNullReturnedByTerminalMethodIsFilteredFromProductionArgs() {
    // The "+" terminal method returns null; null args are filtered before add() is called
    var E    = new NonTerminal("E");
    var num  = new Terminal("num");
    var plus = new Terminal("+");
    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(num)),
        new Production(E, List.of(E, plus, E))));
    var parser = Parser.createParser(grammar,
        Map.of(plus, new Precedence(10, Precedence.Associativity.LEFT)));

    var visitor = new Visitor<Integer>() {
      public @Nullable Object plus(Terminal t) { return null; }
      public int num(Terminal t) { return Integer.parseInt(t.value()); }

      @ProductionName("E : E + E")
      public int add(int a, int b) { return a + b; }
    };
    var evaluator = Visitor.reflect(MethodHandles.lookup(), visitor);

    var result = parser.parse(
        List.of(
            new Terminal("num", "10"),
            new Terminal("+", "+"),
            new Terminal("num", "5")
        ).iterator(), evaluator);
    assertEquals(15, result);
  }


  @Test
  public void reflectObjectSingleBodyProductionWithoutAnnotationPassesThrough() {
    // E -> A, A -> num: "E : A" has one body symbol and no @ProductionName → pass-through
    var E   = new NonTerminal("E");
    var A   = new NonTerminal("A");
    var num = new Terminal("num");
    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(A)),
        new Production(A, List.of(num))));
    var parser = Parser.createParser(grammar, Map.of());

    var visitor = new Visitor<Integer>() {
      public int num(Terminal t) { return Integer.parseInt(t.value()); }
    };
    var evaluator = Visitor.reflect(MethodHandles.lookup(), visitor);

    var result = parser.parse(List.of(new Terminal("num", "99")).iterator(), evaluator);
    assertEquals(99, result);
  }


  @Test
  public void reflectObjectNullThrows() {
    assertThrows(NullPointerException.class,
        () -> Visitor.reflect(MethodHandles.lookup(), (Visitor<?>) null));
  }

  @Test
  public void reflectObjectMethodWithNoArgumentsThrows() {
    var bad = new Visitor<Integer>() {
      public int noArgs() { return 0; }
    };
    assertThrows(IllegalStateException.class,
        () -> Visitor.reflect(MethodHandles.lookup(), bad));
  }

  @Test
  public void reflectObjectVoidReturnTypeThrows() {
    var bad = new Visitor<Object>() {
      public void num(Terminal t) { /* intentionally void */ }
    };
    assertThrows(IllegalStateException.class,
        () -> Visitor.reflect(MethodHandles.lookup(), bad));
  }

  @Test
  public void reflectObjectTerminalMethodWrongParameterTypeThrows() {
    // Method takes int instead of Terminal
    var bad = new Visitor<Integer>() {
      public int num(int i) { return i; }
    };
    assertThrows(IllegalStateException.class,
        () -> Visitor.reflect(MethodHandles.lookup(), bad));
  }

  @Test
  public void reflectObjectMultiBodyProductionWithNoAnnotationThrowsOnInvoke() {
    // "E : E + E" has 2 body symbols but no @ProductionName → exception at parse time
    var E    = new NonTerminal("E");
    var num  = new Terminal("num");
    var plus = new Terminal("+");
    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(num)),
        new Production(E, List.of(E, plus, E))));
    var parser = Parser.createParser(grammar,
        Map.of(plus, new Precedence(1, Precedence.Associativity.LEFT)));

    var visitor = new Visitor<Integer>() {
      public int num(Terminal t) { return Integer.parseInt(t.value()); }
      // deliberately no @ProductionName for "E : E + E"
    };
    var evaluator = Visitor.reflect(MethodHandles.lookup(), visitor);

    assertThrows(IllegalStateException.class,
        () -> parser.parse(
            List.of(
                new Terminal("num", "1"),
                new Terminal("+", "+"),
                new Terminal("num", "2")
            ).iterator(), evaluator));
  }


  @Test
  public void reflectLookupSingleNumber() {
    var E   = new NonTerminal("E");
    var num = new Terminal("num");
    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(num))));
    var parser = Parser.createParser(grammar, Map.of());

    var visitor = new Visitor<Integer>() {
      public int num(Terminal t) { return Integer.parseInt(t.value()); }
    };
    var evaluator = Visitor.reflect(MethodHandles.lookup(), visitor);

    var result = parser.parse(List.of(new Terminal("num", "7")).iterator(), evaluator);
    assertEquals(7, result);
  }

  @Test
  public void reflectLookupBinaryAddition() {
    var E    = new NonTerminal("E");
    var num  = new Terminal("num");
    var plus = new Terminal("+");
    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(num)),
        new Production(E, List.of(E, plus, E))));
    var parser = Parser.createParser(grammar,
        Map.of(plus, new Precedence(10, Precedence.Associativity.LEFT)));

    var visitor = new Visitor<Integer>() {
      public int num(Terminal t) { return Integer.parseInt(t.value()); }

      @ProductionName("E : E + E")
      public int add(int a, int b) { return a + b; }
    };
    var evaluator = Visitor.reflect(MethodHandles.lookup(), visitor);

    var result = parser.parse(
        List.of(
            new Terminal("num", "8"), new Terminal("+", "+"), new Terminal("num", "9")
        ).iterator(), evaluator);
    assertEquals(17, result);
  }

  @Test
  public void reflectLookupPrecedenceRespected() {
    var E    = new NonTerminal("E");
    var num  = new Terminal("num");
    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(num)),
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(E, mul,  E))));
    var parser = Parser.createParser(grammar, Map.of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        mul,  new Precedence(20, Precedence.Associativity.LEFT)));

    var visitor = new Visitor<Integer>() {
      public int num(Terminal t) { return Integer.parseInt(t.value()); }

      @ProductionName("E : E + E")
      public int add(int a, int b) { return a + b; }

      @ProductionName("E : E * E")
      public int mul(int a, int b) { return a * b; }
    };
    var evaluator = Visitor.reflect(MethodHandles.lookup(), visitor);

    // 3 * 3 = 9
    var result = parser.parse(
        List.of(new Terminal("num", "3"), new Terminal("*", "*"), new Terminal("num", "3")).iterator(), evaluator);
    assertEquals(9, result);
  }

  @Test
  public void reflectLookupNullLookupThrows() {
    var visitor = new Visitor<Integer> () {
      public int num(Terminal t) { return 0; }
    };
    assertThrows(NullPointerException.class,
        () -> Visitor.reflect(null, visitor));
  }

  @Test
  public void reflectLookupNullObjectThrows() {
    assertThrows(NullPointerException.class,
        () -> Visitor.reflect(MethodHandles.lookup(), null));
  }

  @Test
  public void reflectLookupBothNullThrows() {
    assertThrows(NullPointerException.class,
        () -> Visitor.reflect(null, null));
  }

  @Test
  public void reflectLookupMethodWithNoArgumentsThrows() {
    var bad = new Visitor<Integer>() {
      public int noArgs() { return 0; }
    };
    assertThrows(IllegalStateException.class,
        () -> Visitor.reflect(MethodHandles.lookup(), bad));
  }

  @Test
  public void reflectLookupVoidReturnTypeThrows() {
    var bad = new Visitor<Object>() {
      public void num(Terminal t) { /* intentionally void */ }
    };
    assertThrows(IllegalStateException.class,
        () -> Visitor.reflect(MethodHandles.lookup(), bad));
  }
}