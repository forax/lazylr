package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EvaluatorTest {
  @Test
  public void directEvaluation() {
    var E = new NonTerminal("E");
    var plus = new Terminal("+");
    var mul = new Terminal("*");
    var num = new Terminal("num");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(E, mul, E)),
        new Production(E, List.of(num))
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        mul, new Precedence(20, Precedence.Associativity.LEFT)
    );

    var lexer = Lexer.createLexer(List.of(
        new Token("+", "\\+"),
        new Token("*", "\\*"),
        new Token("num", "[0-9]+"),
        new Token(" +")
    ));
    var parser = Parser.createParser(grammar, precedence);

    var input = "2 + 3 * 5";
    var result = parser.parse(lexer.tokenize(input), new Evaluator<Integer>() {
      @Override
      public Integer evaluate(Terminal terminal) {
        return switch (terminal.name()) {
          case "num" -> Integer.parseInt(terminal.value());
          default -> 0;
        };
      }

      @Override
      public Integer evaluate(Production production, List<Integer> arguments) {
        return switch (production.name()) {
          case "E : E + E" ->  arguments.get(0) + arguments.get(2);
          case "E : E * E" ->  arguments.get(0) * arguments.get(2);
          case "E : num" -> arguments.get(0);
          default -> throw new IllegalStateException("unknown production: " + production.name());
        };
      }
    });

    assertEquals(17, result);
  }

  sealed interface Expr {}
  record Binary(Expr left, Op op, Expr right) implements Expr {
    enum Op { ADD, MUL }
  }
  record Literal(int value) implements Expr {}

  static int eval(Expr expr) {
    return switch (expr) {
      case Binary(var left, var op, var right) when op == Binary.Op.ADD -> eval(left)  + eval(right);
      case Binary(var left, var _, var right) -> eval(left) * eval(right);
      case Literal(var value) -> value;
    };
  }

  @Test
  public void astEvaluation() {
    var E = new NonTerminal("E");
    var plus = new Terminal("+");
    var mul = new Terminal("*");
    var num = new Terminal("num");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(E, mul, E)),
        new Production(E, List.of(num))
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        mul, new Precedence(20, Precedence.Associativity.LEFT)
    );

    var lexer = Lexer.createLexer(List.of(
        new Token("+", "\\+"),
        new Token("*", "\\*"),
        new Token("num", "[0-9]+"),
        new Token(" +")
    ));
    var parser = Parser.createParser(grammar, precedence);

    var input = "2 + 3 * 5";
    var expr = parser.parse(lexer.tokenize(input), new Evaluator<Expr>() {
      @Override
      public Expr evaluate(Terminal terminal) {
        return switch (terminal.name()) {
          case "num" -> new Literal(Integer.parseInt(terminal.value()));
          default -> null;
        };
      }

      @Override
      public Expr evaluate(Production production, List<Expr> arguments) {
        return switch (production.name()) {
          case "E : E + E" ->  new Binary(arguments.get(0), Binary.Op.ADD, arguments.get(2));
          case "E : E * E" ->  new Binary(arguments.get(0), Binary.Op.MUL, arguments.get(2));
          case "E : num" -> arguments.get(0);
          default -> throw new IllegalStateException("unknown production: " + production.name());
        };
      }
    });

    assertEquals(17, eval(expr));
  }
}
