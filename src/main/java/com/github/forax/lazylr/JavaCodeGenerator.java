package com.github.forax.lazylr;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class JavaCodeGenerator {
  /// Escapes a string for use as a Java string literal (double-quoted).
  private static String escapeJavaString(String s) {
    var builder = new StringBuilder();
    for (var i = 0; i < s.length(); i++) {
      var c = s.charAt(i);
      switch (c) {
        case '\\' -> builder.append("\\\\");
        case '"'  -> builder.append("\\\"");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        case '\0' -> builder.append("\\0");
        default -> {
          if (c < 0x20 || c == 0x7F) {
            // other ASCII control characters → \\uXXXX
            builder.append(String.format("\\u%04X", (int) c));
          } else {
            builder.append(c);
          }
        }
      }
    }
    return builder.toString();
  }

  /// Converts an arbitrary grammar symbol name into a valid Java identifier fragment
  /// by replacing non-alphanumeric characters with underscores.
  private static String sanitizeId(String name) {
    return name.replaceAll("[^A-Za-z0-9]", "_");
  }

  private static String quoteRegex(String text) {
    if (text.startsWith("\\Q") && text.endsWith("\\E")) {
      var regex = text.substring(2, text.length() - 2);
      return "Pattern.quote(\"" + escapeJavaString(regex) + "\")";
    }
    return "\"" + escapeJavaString(text) + "\"";
  }

  private static final class TerminalIdMap {
    private final HashMap<String, Integer> counterMap = new HashMap<>();
    private final LinkedHashMap<Terminal, String> terminalIdMap = new LinkedHashMap<>();

    public void add(Terminal terminal) {
      if (terminalIdMap.containsKey(terminal)) {
        return;
      }
      var id = sanitizeId(terminal.name());
      var counter = counterMap.get(id);
      if (counter == null) {
        counterMap.put(id, 1);
      } else {
        counterMap.put(id, counter + 1);
        id += counter;
      }
      terminalIdMap.put(terminal, id);
    }

    public String id(Terminal terminal) {
      return terminalIdMap.get(terminal);
    }

    public Set<Map.Entry<Terminal, String>> entrySet() {
      return terminalIdMap.entrySet();
    }
  }

  private static TerminalIdMap collectTerminals(List<Production> productions,
                                                Map<PrecedenceEntity, Precedence> precedenceMap) {
    // Collect unique terminals
    var terminalIdMap = new TerminalIdMap();
    for (var production : productions) {
      for (var symbol : production.body()) {
        switch (symbol) {
          case Terminal t -> terminalIdMap.add(t);
          case NonTerminal _ -> {}
        }
      }
    }
    // Also add terminals that appear in the precedence map but not in productions
    for (var entity : precedenceMap.keySet()) {
      switch (entity) {
        case Terminal t -> terminalIdMap.add(t);
        case Production _ -> {}
      }
    }

    return terminalIdMap;
  }


  /// Generates the Java source code for a static {@code createGrammar()} method
  /// that programmatically reconstructs the given {@link MetaGrammar}.
  ///
  /// The generated method declares all {@link Terminal}, {@link NonTerminal},
  /// {@link Production}, and {@link Token} instances as local variables,
  /// builds the {@link Grammar}, assembles the precedence map, and returns
  /// a fully constructed {@link MetaGrammar} using its public constructor.
  ///
  /// @param mg the {@code MetaGrammar} to generate code for; must not be {@code null}.
  /// @return a {@code String} containing the formatted Java source of the method.
  public static String generate(MetaGrammar mg) {
    Objects.requireNonNull(mg);

    var sb = new StringBuilder();
    sb.append("""
      import com.github.forax.lazylr.*;
      
      public static MetaGrammar createGrammar() {
      """);

    var grammar = mg.grammar();
    var productions = grammar.productions();
    var precedenceMap = mg.precedenceMap();

    // Collect unique non-terminals and terminals
    var nonTerminals = grammar.nonTerminals();
    var terminalIdMap = collectTerminals(productions, precedenceMap);

    // Map each production to an index
    var productionIndexMap = IntStream.range(0, productions.size())
        .boxed()
        .collect(Collectors.toMap(productions::get, i -> i));

    // -- Emit NonTerminal declarations
    sb.append("  // Non-terminals\n");
    for (var nonTerminal : nonTerminals) {
      sb.append("  var nt_").append(sanitizeId(nonTerminal.name()))
          .append(" = new NonTerminal(\"").append(escapeJavaString(nonTerminal.name())).append("\");\n");
    }
    sb.append('\n');

    // -- Emit Terminal declarations
    sb.append("  // Terminals\n");
    for (var entry : terminalIdMap.entrySet()) {
      var terminal = entry.getKey();
      var id = entry.getValue();
      sb.append("  var t_").append(id)
          .append(" = new Terminal(\"").append(escapeJavaString(terminal.name())).append("\");\n");
    }
    sb.append('\n');

    // -- Emit Production declarations
    sb.append("  // Productions\n");
    for (var i = 0; i < productions.size(); i++) {
      var production = productions.get(i);
      sb.append("  var p").append(i).append(" = new Production(")
          .append("nt_").append(sanitizeId(production.head().name())).append(", ");

      var body = production.body();
      if (body.isEmpty()) {
        sb.append("List.of()");
      } else {
        sb.append("List.of(");
        var separator = "";
        for (var symbol : body) {
          sb.append(separator);
          switch (symbol) {
            case NonTerminal nt -> sb.append("nt_").append(sanitizeId(nt.name()));
            case Terminal t -> sb.append("t_").append(terminalIdMap.id(t));
          }
          separator = ", ";
        }
        sb.append(")");
      }
      sb.append(");\n");
    }
    sb.append('\n');

    // -- Emit Grammar
    sb.append("  // Grammar\n");
    sb.append("  var startSymbol = nt_")
        .append(sanitizeId(grammar.startSymbol().name())).append(";\n");
    sb.append("  var grammar = new Grammar(startSymbol, List.of(");
    var separator = "";
    for (var i = 0; i < productions.size(); i++) {
      sb.append(separator).append("p").append(i);
      separator = ", ";
    }
    sb.append("));\n\n");

    // -- Emit Token declarations
    sb.append("  // Tokens\n");
    var tokens = mg.tokens();
    sb.append("  var tokens = List.of(\n");
    separator = "";
    for (var token : tokens) {
      sb.append(separator).append("    ");
      if (token.name() != null) {
        // named token: Token(String name, String regex)
        sb.append("new Token(\"").append(escapeJavaString(token.name())).append("\", ")
            .append(quoteRegex(token.regex())).append(")");
      } else {
        // anonymous token: Token(String regex)
        sb.append("new Token(").append(quoteRegex(token.regex())).append(")");
      }
      separator = ",\n";
    }
    sb.append("\n  );\n\n");

    // -- Emit Precedence Map
    sb.append("  // Precedence map\n");
    if (precedenceMap.isEmpty()) {
      sb.append("  var precedenceMap = Map.<PrecedenceEntity, Precedence>of();\n");
    } else {
      sb.append("  var precedenceMap = new LinkedHashMap<PrecedenceEntity, Precedence>();\n");
      for (var entry : precedenceMap.entrySet()) {
        var entity = entry.getKey();
        var prec = entry.getValue();
        var entityRef = switch (entity) {
          case Terminal t -> "t_" + terminalIdMap.id(t);
          case Production p -> "p" + productionIndexMap.get(p);
        };
        sb.append("  precedenceMap.put(").append(entityRef).append(", ")
            .append("new Precedence(").append(prec.level()).append(", ")
            .append("Precedence.Associativity.").append(prec.associativity().name())
            .append("));\n");
      }
    }
    sb.append('\n');

    // -- Return MetaGrammar
    sb.append("  return new MetaGrammar(tokens, precedenceMap, grammar);\n");
    sb.append("}\n\n");

    // -- main
    sb.append("""
        static void main() {
          var mg = createGrammar();
          LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), System.err::println);
        }
        """);

    return sb.toString();
  }
}
