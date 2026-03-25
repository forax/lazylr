package com.github.forax.lazylr;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.List;

public final class ReadmeExampleTest {
  @Test
  public void example() {
    // Define your grammar
    MetaGrammar mg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/
          /[ ]+/
        }
        precedence {
          left:  '+', '-'
          left:  '*'
          right: UNARY
        }
        grammar {
          E : num
          E : E '+' E
          E : E '-' E
          E : E '*' E
          E : '-' E      %prec UNARY
        }
        """);

    // Verifie the grammar for conflicts (optional)
    mg.verify();

    //Transforming to an AST using an Evaluator
    /*sealed*/ interface Node {}
    record NumLit(int value) implements Node {}
    record UnaryOp(String op, Node node) implements Node {}
    record BinaryOp(String op, Node left, Node right) implements Node {}

    class NodeEvaluator implements Evaluator<Node> {
      @Override
      public Node evaluate(@NonNull Terminal term) {
        return switch (term.name()) {
          case "num" -> new NumLit(Integer.parseInt(term.value()));
          default -> null;
        };
      }

      @Override
      public Node evaluate(@NonNull Production prod, @NonNull List<Node> args) {
        return switch (prod.name()) {
          case "E : num" -> args.get(0);
          case "E : E + E" -> new BinaryOp("+", args.get(0), args.get(2));
          case "E : E - E" -> new BinaryOp("-", args.get(0), args.get(2));
          case "E : E * E" -> new BinaryOp("*", args.get(0), args.get(2));
          case "E : - E" -> new UnaryOp("-", args.get(1));
          default -> throw new AssertionError("Unknown: " + prod.name());
        };
      }
    }

    // Usage Example
    String input = "2 + - 3 * 4";

    // Parse and create the AST
    Node ast = mg.parse(input, new NodeEvaluator());

    // Profit!
    System.out.println(ast);
    // BinaryOp[op=+, left=NumLit[value=2], right=BinaryOp[op=*, left=UnaryOp[op=-, node=NumLit[value=3]], right=NumLit[value=4]]]
  }
}
