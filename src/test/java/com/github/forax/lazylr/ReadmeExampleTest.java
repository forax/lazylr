package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class ReadmeExampleTest {
  @Test
  public void example() {
    // Define your Lexer
    Lexer lexer = Lexer.createLexer(List.of(
        new Rule("num", "[0-9]+"),
        new Rule("+", "\\+"),
        new Rule("*", "\\*"),
        new Rule("[ ]+")
    ));


    // Define your Grammar
    var expr = new NonTerminal("expr");
    var num = new Terminal("num");
    var plus = new Terminal("+");
    var mul = new Terminal("*");

    Grammar grammar = new Grammar(expr, List.of(
        new Production(expr, List.of(num)),
        new Production(expr, List.of(expr, plus, expr)),
        new Production(expr, List.of(expr, mul, expr))
    ));


    // Handle Precedence and create the Parser
    var precedence = Map.of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        mul,  new Precedence(20, Precedence.Associativity.LEFT)
    );

    Parser parser = Parser.createParser(grammar, precedence);


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
