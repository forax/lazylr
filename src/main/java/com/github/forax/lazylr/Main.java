package com.github.forax.lazylr;

import java.util.List;
import java.util.Map;

public class Main {
  static void main() {
    // 1. Define Symbols
    var E = new NonTerminal("E");
    var plus = new Terminal("+");
    var mul = new Terminal("*");
    var id = new Terminal("id");

    // 2. Define Grammar: E -> E + E | E * E | id
    var prod_plus = new Production(E, List.of(E, plus, E));
    var prod_mult = new Production(E, List.of(E, mul, E));
    var prod_id = new Production(E, List.of(id));
    var grammar = new Grammar(E, List.of(prod_plus, prod_mult, prod_id));

    // 3. Define Precedences
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        mul, new Precedence(20, Precedence.Associativity.LEFT),
        prod_plus, new Precedence(10, Precedence.Associativity.LEFT),
        prod_mult, new Precedence(20, Precedence.Associativity.LEFT)
    );

    // 4. Create the Parser
    var parser = Parser.createParser(grammar, precedence);

    // 5. Create the Lexer
    var lexer = Lexer.createLexer( List.of(
        new Rule("id", "[a-z]+"),
        new Rule("+", "\\+"),
        new Rule("*", "\\*")));

    // 6. Define The Syntax Tree
    interface Expr {}
    record Add(Expr left, Expr right) implements Expr {}
    record Mul(Expr left, Expr right) implements Expr {}
    record Id(String name) implements Expr {}

    // 7. Run the Parser on: id + id + id * id
    var terminals = lexer.tokenize("a + b + c * d");
    var result = parser.parse(terminals, new Evaluator<Expr>() {

      @Override
      public Expr evaluate(Terminal terminal) {
        return switch(terminal.name()) {
          case "id" -> new Id(terminal.value());
          default -> null;
        };
      }

      @Override
      public Expr evaluate(Production production, List<Expr> arguments) {
        return switch (production.name()) {
          case "E -> E + E" -> new Add(arguments.get(0), arguments.get(2));
          case "E -> E * E" -> new Mul(arguments.get(0), arguments.get(2));
          case "E -> id" -> arguments.getFirst();
          default -> throw new AssertionError("unknown " + production);
        };
      }
    });

    System.out.println(result);
  }
}