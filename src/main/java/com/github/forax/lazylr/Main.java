package com.github.forax.lazylr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Entry point for the `lazylr` command-line tool.
/// This tool processes a grammar file and optionally an input file:
/// - With only a grammar file: validates the grammar and prints its **railroad diagram**.
/// - With a grammar file and an input file: validates the grammar, parses the input,
///   and prints the **derivation tree**.
///
/// Usage:
/// ```
/// lazylr <grammar> [input]
/// ```
public final class Main {
  private Main() {
    throw new AssertionError();
  }

  /// Prints usage instructions.
  private static void usage() {
    System.err.println("""
      Usage: lazylr <grammar> [input]
      
      Arguments:
        <grammar>  path to the grammar file to validate
        [input]    optional path to an input file to parse against the grammar
      
      Examples:
        lazylr grammar.txt              # validate grammar and print railroad diagram
        lazylr grammar.txt input.txt    # parse input and print derivation tree
      """);
  }

  /// Represents a node in the derivation (parse) tree.
  /// Each node holds a [Symbol] and an immutable list of child nodes,
  /// forming a tree that reflects the grammatical structure of the parsed input.
  ///
  /// @param symbol   the grammar symbol (terminal or non-terminal) at this node.
  /// @param children the ordered list of child nodes produced by this symbol's derivation.
  private record Node(Symbol symbol, List<Node> children) {
    public Node {
      Objects.requireNonNull(symbol);
      children = List.copyOf(children);
    }
  }

  /// Renders a derivation tree as a formatted string using box-drawing characters.
  /// Example output:
  /// ```
  /// └── <expr>
  ///     ├── [number=42]
  ///     └── [+]
  ///         └── [number=1]
  /// ```
  ///
  /// @param node the root node of the tree to render.
  /// @return a multi-line string representation of the tree.
  private static String tree(Node node) {
    var builder = new StringBuilder();
    tree(node, "", true, builder);
    return builder.toString();
  }

  /// Recursively appends a subtree rooted at `node` to the provided `builder`.
  /// Terminals are rendered as `[name\` or `[name=value]` (when name and value differ).
  /// Non-terminals are rendered as `<name>`.
  ///
  /// @param node    the current node to render.
  /// @param prefix  the indentation prefix accumulated from parent nodes.
  /// @param last    `true` if this node is the last child of its parent, affecting connector style.
  /// @param builder the [StringBuilder] to append the rendered lines to.
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

  /// Main entry point for the \`lazylr\` tool.
  /// Behavior depends on the number of arguments:
  /// - **1 argument** `<grammar>`: reads and validates the grammar file, checks for
  ///   LALR conflicts, and prints the railroad diagram to standard output.
  /// - **2 arguments** `<grammar>` `<input>`: additionally tokenizes and parses the input
  ///   file against the grammar, then prints the derivation tree to standard output.
  ///
  /// Exit codes:
  /// - `0` — success
  /// - `1` — usage error, I/O error, or grammar/input parsing failure
  /// - `2` — LALR conflict(s) detected in the grammar
  ///
  /// @param args command-line arguments: `<grammar>` and optionally `[input]`.`
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
