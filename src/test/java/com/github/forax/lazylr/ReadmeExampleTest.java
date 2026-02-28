package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class ReadmeExampleTest {
  @Test
  public void example() {
    // Define your grammar
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
        expr : num
        expr : expr '+' expr
        expr : expr '*' expr
      }
    """);

    // Verifie the grammar for conflicts (optional)
    LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), error -> {
      System.err.println("Conflict detected: " + error);
    });

    //Transforming to an AST using an Evaluator
    /*sealed*/ interface Node {}
    record NumLit(int value) implements Node {}
    record BinaryOp(String op, Node left, Node right) implements Node {}

    class NodeEvaluator implements Evaluator<Node> {
      @Override
      public Node evaluate(Terminal term) {
        return switch (term.name()) {
          case "num" -> new NumLit(Integer.parseInt(term.value()));
          default -> null;
        };
      }

      @Override
      public Node evaluate(Production prod, List<Node> args) {
        return switch (prod.name()) {
          case "expr : num" -> args.get(0);
          case "expr : expr + expr" -> new BinaryOp("+", args.get(0), args.get(2));
          case "expr : expr * expr" -> new BinaryOp("*", args.get(0), args.get(2));
          default -> throw new AssertionError("Unknown: " + prod.name());
        };
      }
    }

    Lexer lexer = Lexer.createLexer(mg.rules());
    Parser parser = Parser.createParser(mg.grammar(), mg.precedenceMap());

    // Usage Example
    String input = "2 + 3 * 4";

    // Tokenize using token names
    Iterator<Terminal> tokens = lexer.tokenize(input);

    // Parse and create the AST
    Node ast = parser.parse(tokens, new NodeEvaluator());

    // Profit!
    System.out.println(ast);
    // BinaryOp[op=+, left=NumLit[value=2], right=BinaryOp[op=*, left=NumLit[value=3], right=NumLit[value=4]]]
  }
}
