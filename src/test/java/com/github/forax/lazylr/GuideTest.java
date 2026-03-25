package com.github.forax.lazylr;

import module java.base;

import org.jspecify.annotations.NonNull;
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

    LALRVerifier.verifySilently(grammar, Map.of(), msg -> fail("Unexpected conflict: " + msg));

    var lexer = Lexer.createLexer(List.of(
        new Token("num", "[0-9]+"),
        new Token("[ ]+")     // whitespaces are ignored
    ));
    var parser = Parser.createParser(grammar, Map.of());

    class IntEvaluator implements Evaluator<Integer> {
      public Integer evaluate(@NonNull Terminal t) {
        System.out.println("seen terminal: " + t.name() + " = " + t.value());
        return Integer.parseInt(t.value());
      }

      public Integer evaluate(@NonNull Production p, @NonNull List<Integer> args) {
        System.out.println("seen production: " + p.name() + " with args " + args);
        return args.get(0);
      }
    }

    // compute the result
    var input = "42";
    var result = parser.parse(lexer.tokenize(input), new IntEvaluator());

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
    LALRVerifier.verify(grammar, Map.of());
  }

  // -------------------------------------------------------------------------
  // Step 3 – Recursion: function calls like sum(42, 17)
  // -------------------------------------------------------------------------
  @Test
  public void step3_functionCall() {
    var mg = MetaGrammar.load("""
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

    // Optional: verify the grammar
    mg.verifySilently(msg -> fail("Unexpected conflict: " + msg));

    class IntEvaluator implements Evaluator<Integer> {
      @Override
      public Integer evaluate(@NonNull Terminal terminal) {
        return switch (terminal.name()) {
          case "num" -> Integer.parseInt(terminal.value());
          default    -> 0;
        };
      }
      @Override
      public Integer evaluate(@NonNull Production production, @NonNull List<Integer> args) {
        return switch (production.name()) {
          case "E : num"          -> args.get(0);
          case "ARGS : E"         -> args.get(0);
          case "ARGS : ARGS , E"  -> args.get(0) + args.get(2);
          case "ARGS : ε"         -> 0;
          case "E : sum ( ARGS )" -> args.get(2);
          default -> throw new IllegalStateException("unknown production: " + production.name());
        };
      }
    }

    var input  = "sum(42, 17)";
    var result = mg.parse(input, new IntEvaluator());

    assertEquals(59, result);
  }

  // -------------------------------------------------------------------------
  // Step 4 – Addition and Left Associativity: 1 + 2 + 3 = 6
  // -------------------------------------------------------------------------
  @Test
  public void step4_additionLeftAssociative() {
    var mg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/
          /[ ]+/
        }
        precedence {
          left: '+'
        }
        grammar {
          E: num
          E: E '+' E    %prec '+'
        }
        """);

    mg.verifySilently(msg -> fail("Unexpected conflict: " + msg));

    class IntEvaluator implements Evaluator<Integer> {
      @Override
      public Integer evaluate(@NonNull Terminal terminal) {
        return switch (terminal.name()) {
          case "num" -> Integer.parseInt(terminal.value());
          default    -> 0;
        };
      }

      @Override
      public Integer evaluate(@NonNull Production production, @NonNull List<Integer> args) {
        return switch (production.name()) {
          case "E : num"   -> args.get(0);
          case "E : E + E" -> args.get(0) + args.get(2);
          default -> throw new IllegalStateException("unknown production: " + production.name());
        };
      }
    }

    var input  = "1 + 2 + 3";
    var result = mg.parse(input, new IntEvaluator());

    assertEquals(6, result);
  }

  // -------------------------------------------------------------------------
  // Step 5 – Multiplication and Priority: 2 + 3 * 4 = 14
  // -------------------------------------------------------------------------
  @Test
  public void step5_multiplicationPrecedence() {
    var mg = MetaGrammar.load("""
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

    mg.verifySilently(msg -> fail("Unexpected conflict: " + msg));

    class IntEvaluator implements Evaluator<Integer> {
      @Override
      public Integer evaluate(@NonNull Terminal terminal) {
        return switch (terminal.name()) {
          case "num" -> Integer.parseInt(terminal.value());
          default    -> 0;
        };
      }
      @Override
      public Integer evaluate(@NonNull Production production, @NonNull List<Integer> args) {
        return switch (production.name()) {
          case "E : num"   -> args.get(0);
          case "E : E + E" -> args.get(0) + args.get(2);
          case "E : E * E" -> args.get(0) * args.get(2);
          default -> throw new IllegalStateException("unknown production: " + production.name());
        };
      }
    }

    var input  = "2 + 3 * 4";
    var result = mg.parse(input, new IntEvaluator());

    assertEquals(14, result);
  }

  // -------------------------------------------------------------------------
  // Step 6 – Exponentiation and Right Associativity: 2 ^ 3 ^ 2 = 512
  // -------------------------------------------------------------------------
  @Test
  public void step6_exponentiationRightAssociative() {
    var mg = MetaGrammar.load("""
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

    mg.verifySilently(msg -> fail("Unexpected conflict: " + msg));

    class IntEvaluator implements Evaluator<Integer> {
      @Override
      public Integer evaluate(@NonNull Terminal terminal) {
        return switch (terminal.name()) {
          case "num" -> Integer.parseInt(terminal.value());
          default    -> 0;
        };
      }
      @Override
      public Integer evaluate(@NonNull Production production, @NonNull List<Integer> args) {
        return switch (production.name()) {
          case "E : num"   -> args.get(0);
          case "E : E + E" -> args.get(0) + args.get(2);
          case "E : E * E" -> args.get(0) * args.get(2);
          case "E : E ^ E" -> (int) Math.pow(args.get(0), args.get(2));
          default -> throw new IllegalStateException("unknown production: " + production.name());
        };
      }
    }

    var input  = "2 ^ 3 ^ 2";
    var result = mg.parse(input, new IntEvaluator());

    assertEquals(512, result);
  }

  // -------------------------------------------------------------------------
  // Step 7 – Dangling Else: else binds to the nearest if
  // -------------------------------------------------------------------------
  @Test
  public void step7_danglingElse() {
    var mg = MetaGrammar.load("""
        tokens {
          if:   /if/
          then: /then/
          else: /else/
          num:  /[0-9]+/
          /[ ]+/
        }
        precedence {
          right: then
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

    mg.verifySilently(msg -> fail("Unexpected conflict: " + msg));

    class IntEvaluator implements Evaluator<Integer> {
      @Override
      public Integer evaluate(@NonNull Terminal terminal) {
        return switch (terminal.name()) {
          case "num" -> Integer.parseInt(terminal.value());
          default    -> 0;
        };
      }
      @Override
      public Integer evaluate(@NonNull Production production, @NonNull List<Integer> args) {
        return switch (production.name()) {
          case "E : num"                -> args.get(0);
          case "E : E + E"              -> args.get(0) + args.get(2);
          case "E : E * E"              -> args.get(0) * args.get(2);
          case "E : E ^ E"              -> (int) Math.pow(args.get(0), args.get(2));
          case "E : if E then E"        -> args.get(1) != 0 ? args.get(3) : 0;
          case "E : if E then E else E" -> args.get(1) != 0 ? args.get(3) : args.get(5);
          default -> throw new IllegalStateException("unknown production: " + production.name());
        };
      }
    }
    var evaluator = new IntEvaluator();

    assertEquals(10, mg.parse("if 1 then 10 else 20", evaluator));
    assertEquals(20, mg.parse("if 0 then 10 else 20", evaluator));
    assertEquals(42, mg.parse("if 1 then if 0 then 99 else 42", evaluator));
  }

  // -------------------------------------------------------------------------
  // Step 8 – Unary Operators and Production Precedence and AST
  // -------------------------------------------------------------------------
  @Test
  public void step8_unaryOperatorsAndAST() {
    /*sealed*/ interface Node {}
    record Sub(Node left, Node right) implements Node {}
    record Mul(Node left, Node right) implements Node {}
    record UnaryMinus(Node node) implements Node {}
    record Num(int value, int pos) implements Node {}

    record NodeEvaluator(Iterator<Terminal> input) implements Evaluator<Node> {
      @Override
      public Node evaluate(@NonNull Terminal terminal) {
        return switch (terminal.name()) {
          case "num" -> {
            var pos = Lexer.position(input);
            yield new Num(Integer.parseInt(terminal.value()), pos);
          }
          default -> null;
        };
      }
      @Override
      public Node evaluate(@NonNull Production production, @NonNull List<Node> args) {
        return switch (production.name()) {
          case "E : num" -> args.getFirst();
          case "E : E - E" -> new Sub(args.get(0), args.get(2));
          case "E : E * E" -> new Mul(args.get(0), args.get(2));
          case "E : - E" -> new UnaryMinus(args.get(1));
          default -> throw new IllegalStateException("Unexpected production: " + production.name());
        };
      }
    }

    // This is not a correct grammar, precedence of the unary minus is wrong
    var badMg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/
          /[ ]+/
        }
        precedence {
          left: '-'
          left: '*'
        }
        grammar {
          E: num
          E: E '-' E
          E: E '*' E
          E: '-' E
        }
        """);

    var input = "- 4 * 5";
    var node = badMg.parse(input, NodeEvaluator::new);
    assertEquals("UnaryMinus[node=Mul[left=Num[value=4, pos=2], right=Num[value=5, pos=6]]]", node.toString());

    // Use a virtual token UNARY to fix the precedence of the unary minus
    var mg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/
          /[ ]+/
        }
        precedence {
          left: '-'
          left: '*'
          right: UNARY  // virtual token
        }
        grammar {
          E: num
          E: E '-' E
          E: E '*' E
          E: '-' E      %prec UNARY
        }
    """);

    node = mg.parse(input, NodeEvaluator::new);
    assertEquals("Mul[left=UnaryMinus[node=Num[value=4, pos=2]], right=Num[value=5, pos=6]]", node.toString());
  }
}