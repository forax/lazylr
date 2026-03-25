package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Entry point for the `lazylr` command-line tool.
/// This tool processes a grammar file and optionally an input file:
/// - With only a grammar file: validates the grammar and optionally prints its **automaton**.
/// - With a grammar file and an input file: validates the grammar, parses the input,
///   and prints the **derivation tree**.
///
/// - With `--print` and a grammar file: print the **automaton** unconditionally.
/// - With `--generate` and a grammar file: generates Java source code for a
///   `createGrammar()` static method that reconstructs the grammar programmatically.
///
/// Usage:
/// ```
/// lazylr [--generate|--print] <grammar> [input]
/// ```
public final class Main {
  private Main() {
    throw new AssertionError();
  }

  /// Prints usage instructions.
  private static void usage() {
    System.err.println("""
      Usage: lazylr [--generate|--print] <grammar> [input]
      
      Arguments:
        <grammar>  path to the grammar file to validate
        [input]    optional path to an input file to parse against the grammar
      
      Options:
        --generate  generate Java source code for a createGrammar() static method
        --print     unconditionally print the automaton
      
      Examples:
        lazylr grammar.txt              # validate grammar and print the automaton if there is a conflict
        lazylr --print grammar.txt      # as above, but the automaton is printed unconditionally
        lazylr --generate grammar.txt   # generate Java code that builds the grammar
        lazylr grammar.txt input.txt    # parse input and print derivation tree
      
      """);
  }

  /// Represents a node in the derivation (parse) tree.
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

  private record CmdLineArgument (
      boolean generate,
      boolean print,
      Path grammarPath,
      @Nullable Path inputPath) {
  }

  private static @Nullable CmdLineArgument parse(String[] args) {
    var generate = false;
    var print = false;
    var grammarPath = (Path) null;
    var inputPath  = (Path) null;
    for (var arg : args) {
      switch (arg) {
        case "--generate" -> generate = true;
        case "--print" -> print = true;
        default -> {
          if (grammarPath == null) {
            grammarPath = Path.of(arg);
            continue;
          }
          if (inputPath == null) {
            inputPath = Path.of(arg);
            continue;
          }
          return null;  // too many arguments
        }
      }
    }
    if (grammarPath == null ||                         // grammarPath is mandatory
        (print && generate) ||                         // print and generate are mutually exclusive
        (inputPath != null && (print || generate))) {  // print/generate are only valid with no input
      return null;
    }
    return new CmdLineArgument(generate, print, grammarPath, inputPath);
  }

  static void main(String[] args) {
    var cmdLineArgument = parse(args);
    if (cmdLineArgument == null) {
      usage();
      System.exit(1);
      return;
    }

    String grammarText;
    try {
      grammarText = Files.readString(cmdLineArgument.grammarPath);
    } catch (IOException e) {
      System.err.println("Error while reading the grammar file: " + e.getMessage());
      System.exit(1);
      return;
    }
    MetaGrammar mg;
    try {
      mg = MetaGrammar.load(grammarText);
    } catch (ParsingException e) {
      System.err.println("Error while parsing the grammar file\n" + e.getMessage());
      System.exit(1);
      return;
    }

    var printStream = cmdLineArgument.print ? System.out : System.err;
    var valid = new boolean[] { true };
    LALRVerifier.verify(mg.grammar(), mg.precedenceMap(),
        printStream, cmdLineArgument.print, error -> {
      valid[0] = false;
      System.err.println(error);
    });
    if (!valid[0]) {
      System.exit(2);
      return;
    }

    if (cmdLineArgument.generate) {
      System.out.print(JavaCodeGenerator.generate(mg));
      return;
    }

    if (cmdLineArgument.inputPath == null) {
      return;
    }

    String inputText;
    try {
      inputText = Files.readString(cmdLineArgument.inputPath);
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
      node = parser.parse(lexer.tokenize(inputText), evaluator);
    } catch (ParsingException e) {
      System.err.println("Error while parsing the input file\n" + e.getMessage());
      System.exit(1);
      return;
    }

    System.out.print(tree(node));
  }
}