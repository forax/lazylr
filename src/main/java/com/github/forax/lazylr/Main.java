package com.github.forax.lazylr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class Main {
  private static void usage() {
    System.err.println("Usage: lazylr <grammar> [input]");
  }

  private record Node(Symbol symbol, List<Node> children) {
    public Node {
      Objects.requireNonNull(symbol);
      children = List.copyOf(children);
    }
  }

  private static String tree(Node node) {
    var builder = new StringBuilder();
    tree(node, "", true, builder);
    return builder.toString();
  }

  private static void tree(Node node, String prefix, boolean last, StringBuilder builder) {
    var connector = last ? "└── " : "├── ";
    var text = switch (node.symbol) {
      case Terminal t -> {
        if (t.name().equals(t.value())) {
          yield '[' + t.name() + ']';
        }
        yield '[' + t.name() + "=" + t.value() + ']';
      }
      case NonTerminal nt -> '<' + nt.name() + '>';
    };

    builder.append(prefix).append(connector).append(text).append('\n');

    var childPrefix = prefix + (last ? "    " : "│   ");
    var children = node.children;
    for (var i = 0; i < children.size(); i++) {
      tree(children.get(i), childPrefix, i == children.size() - 1, builder);
    }
  }

  /// if called with a grammar file, verify the grammar, then print the railroad diagram of the grammar.
  /// if called with a grammar file and an input file, verify the grammar, then prints the derivation tree.
  static void main(String[] args) {
    if (args.length == 0 || args.length > 2) {
      usage();
      System.exit(1);
      return;
    }

    var grammarFile = Path.of(args[0]);
    String grammarInput;
    try {
      grammarInput = Files.readString(grammarFile);
    } catch (IOException e) {
      System.err.println("Error while reading the grammar file " + e.getMessage());
      System.exit(1);
      return;
    }
    MetaGrammar mg;
    try {
      mg = MetaGrammar.create(grammarInput);
    } catch (ParsingException e) {
      System.err.println("Error while parsing the grammar file\n" + e.getMessage());
      System.exit(1);
      return;
    }

    var conflicts = new ArrayList<String>();
    LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), conflicts::add);
    if (!conflicts.isEmpty()) {
      conflicts.forEach(System.err::println);
      System.exit(2);
      return;
    }

    if (args.length == 1) {
      System.out.print(RailroadDiagram.generate(mg.grammar(), false));
      return;
    }

    var inputFile = Path.of(args[1]);
    String input;
    try {
      input = Files.readString(inputFile);
    } catch (IOException e) {
      System.err.println("Error while reading the input file " + e.getMessage());
      System.exit(1);
      return;
    }

    var lexer = Lexer.createLexer(mg.tokens());
    var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());
    var evaluator = new Evaluator<Node>() {
      @Override
      public Node evaluate(Terminal terminal) {
        return new Node(terminal, List.of());
      }

      @Override
      public Node evaluate(Production production, List<Node> arguments) {
        return new Node(production.head(), arguments);
      }
    };

    Node node;
    try {
      node = parser.parse(lexer.tokenize(input), evaluator);
    } catch (ParsingException e) {
      System.err.println("Error while parsing the input file\n" + e.getMessage());
      System.exit(1);
      return;
    }

    System.out.print(tree(node));
  }
}
