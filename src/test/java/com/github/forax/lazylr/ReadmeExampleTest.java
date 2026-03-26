package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

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

    class NodeVisitor implements Visitor<Node>{
      public Node num(Terminal term) {
        return new NumLit(Integer.parseInt(term.value()));
      }

      @ProductionName("E : E + E")
      public Node add(Node left, Node right) {
        return new BinaryOp("+", left, right);
      }
      @ProductionName("E : E - E")
      public Node sub(Node left, Node right) {
        return new BinaryOp("-", left, right);
      }
      @ProductionName("E : E * E")
      public Node mul(Node left, Node right) {
        return new BinaryOp("*", left, right);
      }
      @ProductionName("E : - E")
      public Node unary(Node node) {
        return new UnaryOp("-", node);
      }
    }

    // Usage Example
    String input = "2 + - 3 * 4";

    // Parse and create the AST
    Node ast = mg.parse(input, new NodeVisitor());

    // Profit!
    System.out.println(ast);
    // BinaryOp[op=+, left=NumLit[value=2], right=BinaryOp[op=*, left=UnaryOp[op=-, node=NumLit[value=3]], right=NumLit[value=4]]]
  }
}
