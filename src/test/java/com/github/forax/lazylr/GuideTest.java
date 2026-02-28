package com.github.forax.lazylr;

import module java.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public final class GuideTest {

  // -------------------------------------------------------------------------
  // Step 1 – The Base: parse and evaluate a single number
  // -------------------------------------------------------------------------
  @Test
  public void step1_singleNumber() {
    var E   = new NonTerminal("E");
    var NUM = new Terminal("num");

    var pNum    = new Production(E, List.of(NUM));
    var grammar = new Grammar(E, List.of(pNum));

    LALRVerifier.verify(grammar, Map.of(), msg -> fail("Unexpected conflict: " + msg));

    var lexer = Lexer.createLexer(List.of(
        new Token("num", "[0-9]+"),
        new Token("[ ]+")     // whitespaces are ignored
    ));
    var parser = Parser.createParser(grammar, Map.of());

    // compute the result
    var input = "42";
    var result = parser.parse(lexer.tokenize(input), new Evaluator<Integer>() {
      public Integer evaluate(Terminal t) {
        System.out.println("seen terminal " + t.name());
        return Integer.parseInt(t.value());
      }

      public Integer evaluate(Production p, List<Integer> args) {
        // args corresponds to the value of the symbols in the Production list (0-indexed).
        System.out.println("seen production " + p.name() + " with " + args);
        return args.get(0);
      }
    });

    assertEquals(42, result);
  }

  // ---------------------------------------------------------------------------
  // Step 2 – Reduce/Reduce Conflict: two possible reductions must be detected
  // ---------------------------------------------------------------------------
  @Test
  public void step2_reduceReduceConflict() {
    var E = new NonTerminal("E");
    var A = new NonTerminal("A");
    var B = new NonTerminal("B");
    var NUM = new Terminal("num");

    var pA       = new Production(E, List.of(A));
    var pB       = new Production(E, List.of(B));
    var pNumViaA = new Production(A, List.of(NUM));
    var pNumViaB = new Production(B, List.of(NUM));

    var grammar = new Grammar(E, List.of(pA, pB, pNumViaA, pNumViaB));

    // Both A and B can derive 'num'.
    // In state {E -> .A, E -> .B, A -> .num, B -> .num},
    // shifting 'num' leads to a state with two different reduction options.
    LALRVerifier.verify(grammar, Map.of(), System.out::println);
  }

  // -------------------------------------------------------------------------
  // Step 3 – Recursion: function calls like sum(42, 17)
  // -------------------------------------------------------------------------
  @Test
  public void step3_functionCall() {
    var mg = MetaGrammar.create("""
        tokens {
          sum: /sum/
          num: /[0-9]+/
          /[ ]+/
        }
        grammar {
          E:    num
          E:    sum '(' ARGS ')'
          ARGS: E
          ARGS: ARGS ',' E
          ARGS:
        }
        """);

    LALRVerifier.verify(mg.grammar(), Map.of(), msg -> fail("Unexpected conflict: " + msg));

    var lexer  = Lexer.createLexer(mg.tokens());
    var parser = Parser.createParser(mg.grammar(), Map.of());

    var input  = "sum(42, 17)";
    var result = parser.parse(lexer.tokenize(input), new Evaluator<Integer>() {
      public Integer evaluate(Terminal t) {
        return switch (t.name()) {
          case "num" -> Integer.parseInt(t.value());
          default    -> 0;
        };
      }
      public Integer evaluate(Production p, List<Integer> args) {
        return switch (p.name()) {
          case "E : num"          -> args.get(0);
          case "ARGS : E"         -> args.get(0);
          case "ARGS : ARGS , E"  -> args.get(0) + args.get(2);
          case "ARGS : ε"         -> 0;
          case "E : sum ( ARGS )" -> args.get(2);
          default -> throw new IllegalStateException("unknown production: " + p.name());
        };
      }
    });

    assertEquals(59, result);
  }

  // -------------------------------------------------------------------------
  // Step 4 – Addition and Left Associativity: 1 + 2 + 3 = 6
  // -------------------------------------------------------------------------
  @Test
  public void step4_additionLeftAssociative() {
    var mg = MetaGrammar.create("""
        tokens {
          num: /[0-9]+/
          /[ ]+/
        }
        precedence {
          left: '+'
        }
        grammar {
          E: num
          E: E '+' E
        }
        """);

    LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), msg -> fail("Unexpected conflict: " + msg));

    var lexer  = Lexer.createLexer(mg.tokens());
    var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());

    var input  = "1 + 2 + 3";
    var result = parser.parse(lexer.tokenize(input), new Evaluator<Integer>() {
      public Integer evaluate(Terminal t) {
        return switch (t.name()) {
          case "num" -> Integer.parseInt(t.value());
          default    -> 0;
        };
      }
      public Integer evaluate(Production p, List<Integer> args) {
        return switch (p.name()) {
          case "E : num"   -> args.get(0);
          case "E : E + E" -> args.get(0) + args.get(2);
          default -> throw new IllegalStateException("unknown production: " + p.name());
        };
      }
    });

    assertEquals(6, result);
  }

  // -------------------------------------------------------------------------
  // Step 5 – Multiplication and Priority: 2 + 3 * 4 = 14
  // -------------------------------------------------------------------------
  @Test
  public void step5_multiplicationPrecedence() {
    var mg = MetaGrammar.create("""
        tokens {
          num: /[0-9]+/
          /[ ]+/
        }
        precedence {
          left: '+'
          left: '*'
        }
        grammar {
          E: num
          E: E '+' E
          E: E '*' E
        }
        """);

    LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), msg -> fail("Unexpected conflict: " + msg));

    var lexer  = Lexer.createLexer(mg.tokens());
    var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());

    var input  = "2 + 3 * 4";
    var result = parser.parse(lexer.tokenize(input), new Evaluator<Integer>() {
      public Integer evaluate(Terminal t) {
        return switch (t.name()) {
          case "num" -> Integer.parseInt(t.value());
          default    -> 0;
        };
      }
      public Integer evaluate(Production p, List<Integer> args) {
        return switch (p.name()) {
          case "E : num"   -> args.get(0);
          case "E : E + E" -> args.get(0) + args.get(2);
          case "E : E * E" -> args.get(0) * args.get(2);
          default -> throw new IllegalStateException("unknown production: " + p.name());
        };
      }
    });

    assertEquals(14, result);
  }

  // -------------------------------------------------------------------------
  // Step 6 – Exponentiation and Right Associativity: 2 ^ 3 ^ 2 = 512
  // -------------------------------------------------------------------------
  @Test
  public void step6_exponentiationRightAssociative() {
    var mg = MetaGrammar.create("""
        tokens {
          num: /[0-9]+/
          /[ ]+/
        }
        precedence {
          left:  '+'
          left:  '*'
          right: '^'
        }
        grammar {
          E: num
          E: E '+' E
          E: E '*' E
          E: E '^' E
        }
        """);

    LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), msg -> fail("Unexpected conflict: " + msg));

    var lexer  = Lexer.createLexer(mg.tokens());
    var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());

    var input  = "2 ^ 3 ^ 2";
    var result = parser.parse(lexer.tokenize(input), new Evaluator<Integer>() {
      public Integer evaluate(Terminal t) {
        return switch (t.name()) {
          case "num" -> Integer.parseInt(t.value());
          default    -> 0;
        };
      }
      public Integer evaluate(Production p, List<Integer> args) {
        return switch (p.name()) {
          case "E : num"   -> args.get(0);
          case "E : E + E" -> args.get(0) + args.get(2);
          case "E : E * E" -> args.get(0) * args.get(2);
          case "E : E ^ E" -> (int) Math.pow(args.get(0), args.get(2));
          default -> throw new IllegalStateException("unknown production: " + p.name());
        };
      }
    });

    assertEquals(512, result);
  }

  // -------------------------------------------------------------------------
  // Step 7 – Dangling Else: else binds to the nearest if
  // -------------------------------------------------------------------------
  @Test
  public void step7_danglingElse() {
    var mg = MetaGrammar.create("""
        tokens {
          if:   /if/
          then: /then/
          else: /else/
          num:  /[0-9]+/
          /[ ]+/
        }
        precedence {
          right: if
          left:  '+'
          left:  '*'
          right: '^'
          right: else
        }
        grammar {
          E: num
          E: E '+' E
          E: E '*' E
          E: E '^' E
          E: if E then E
          E: if E then E else E
        }
        """);

    LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), msg -> fail("Unexpected conflict: " + msg));

    var lexer  = Lexer.createLexer(mg.tokens());
    var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());

    var evaluator = new Evaluator<Integer>() {
      public Integer evaluate(Terminal t) {
        return switch (t.name()) {
          case "num" -> Integer.parseInt(t.value());
          default    -> 0;
        };
      }
      public Integer evaluate(Production p, List<Integer> args) {
        return switch (p.name()) {
          case "E : num"                -> args.get(0);
          case "E : E + E"              -> args.get(0) + args.get(2);
          case "E : E * E"              -> args.get(0) * args.get(2);
          case "E : E ^ E"              -> (int) Math.pow(args.get(0), args.get(2));
          case "E : if E then E"        -> args.get(1) != 0 ? args.get(3) : 0;
          case "E : if E then E else E" -> args.get(1) != 0 ? args.get(3) : args.get(5);
          default -> throw new IllegalStateException("unknown production: " + p.name());
        };
      }
    };

    assertEquals(10, parser.parse(lexer.tokenize("if 1 then 10 else 20"), evaluator));
    assertEquals(20, parser.parse(lexer.tokenize("if 0 then 10 else 20"), evaluator));
    assertEquals(42, parser.parse(lexer.tokenize("if 1 then if 0 then 99 else 42"), evaluator));
  }
}
