package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// Generates source code for a [Visitor] implementation from a [Grammar] definition.
///
/// Call [#generate(Grammar)] to obtain a Java source string
/// containing a ready-to-compile visitor class.
public final class JavaCodeVisitorGenerator {

  sealed interface Pattern {
    /// A non-terminal that matches neither Optional nor List.
    record Normal(NonTerminal head, List<Production> productions) implements Pattern {}
    /// NT with two productions: one single-symbol body, one empty body.
    record Optional(NonTerminal head, Symbol symbol) implements Pattern {}
    /// NT with two productions: one single-symbol body, one recursive two-symbol body.
    record ListPattern(NonTerminal head, Symbol element) implements Pattern {}
  }

  private JavaCodeVisitorGenerator() {
    throw new AssertionError();
  }

  /// Generates a visitor source file.
  ///
  /// @param grammar   the grammar to generate a visitor for
  /// @return Java source code as a string
  public static String generate(Grammar grammar) {
    var patterns = buildPatterns(grammar);
    var types = resolveTypes(grammar, patterns);
    return emit(grammar, patterns, types);
  }

  // -- Step 1 – collect identifier terminals



  private static boolean isJavaIdentifier(String name) {
    if (!Character.isJavaIdentifierStart(name.charAt(0))) {
      return false;
    }
    for (var i = 1; i < name.length(); i++) {
      if (!Character.isJavaIdentifierPart(name.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /// Returns the body of a production with non-identifier terminals filtered out.
  private static List<Symbol> filteredBody(Production prod) {
    return prod.body().stream()
        .filter(s -> !(s instanceof Terminal t) || isJavaIdentifier(t.name()))
        .toList();
  }

  // -- Step 1

  private static Map<NonTerminal, Pattern> buildPatterns(Grammar grammar) {
    var map = new LinkedHashMap<NonTerminal, Pattern>();
    for (var nt : grammar.nonTerminals()) {
      map.put(nt, classify(nt, grammar.productionsFor(nt)));
    }
    return map;
  }

  private static Pattern classify(NonTerminal nt, List<Production> prods) {
    // Work on filtered bodies for pattern recognition
    var filtered = prods.stream().map(JavaCodeVisitorGenerator::filteredBody).toList();
    if (filtered.size() == 2) {
      var optPat = tryOptional(nt, prods, filtered);
      if (optPat != null) {
        return optPat;
      }
      var listPat = tryList(nt, prods, filtered);
      if (listPat != null) {
        return listPat;
      }
    }
    return new Pattern.Normal(nt, prods);
  }

  private static Pattern.@Nullable Optional tryOptional(NonTerminal nt, List<Production> prods, List<List<Symbol>> filtered) {
    var empty = false;
    var symbol = (Symbol) null;
    for (var body : filtered) {
      switch (body.size()) {
        case 0 -> empty = true;
        case 1 -> symbol = body.getFirst();
        default -> {
          return null;
        }
      }
    }
    if (empty == false || symbol == null) {
      return null;
    }
    return new Pattern.Optional(nt, symbol);
  }

  private static Pattern.@Nullable ListPattern tryList(NonTerminal nt, List<Production> prods, List<List<Symbol>> filtered) {
    var singleSymbol = (Symbol) null;
    var recBody = (List<Symbol>) null;
    for (var body : filtered) {
      switch (body.size()) {
        case 1 -> singleSymbol = body.getFirst();
        case 2 -> recBody = body;
        default -> { return null; }
      }
    }
    if (singleSymbol == null || recBody == null ||
        !recBody.get(0).equals(nt) ||
        !recBody.get(1).equals(singleSymbol)) {
      return null;
    }
    return new Pattern.ListPattern(nt, singleSymbol);
  }

  // -- Step 3 – resolve Java types (lazy, memoized, cycle-safe)

  private static Map<NonTerminal, String> resolveTypes(Grammar grammar, Map<NonTerminal, Pattern> patterns) {
    var map = new LinkedHashMap<NonTerminal, String>();
    for (var nt : grammar.nonTerminals()) {
      typeOf(patterns, nt, map);
    }
    return map;
  }

  /// Returns (and memoises) the Java type string for `nt`.
  private static String typeOf(Map<NonTerminal, Pattern> patterns, NonTerminal nt, Map<NonTerminal, String> memo) {
    var cached = memo.get(nt);
    if (cached != null) {
      return cached;
    }
    var pat = patterns.get(nt);
    var result = switch (pat) {
      case Pattern.Optional(var _, var sym) ->
          "Optional<" + findSymbolType(patterns, sym, memo) + ">";
      case Pattern.ListPattern(var _, var sym) ->
          "List<" + findSymbolType(patterns, sym, memo) + ">";
      case Pattern.Normal(var head, var _) ->
          capitalize(head.name());       // record or sealed interface — name never depends on other NTs
    };
    memo.put(nt, result);
    return result;
  }

  private static String findSymbolType(Map<NonTerminal, Pattern> patterns, Symbol sym, Map<NonTerminal, String> memo) {
    return switch (sym) {
      case Terminal _ -> "String";
      case NonTerminal nt -> typeOf(patterns, nt, memo);
    };
  }

  // ─--Step 4 – code emission

  private static List<Terminal> collectIdentifierTerminals(Grammar grammar) {
    return grammar.productions().stream()
        .flatMap(p -> p.body().stream())
        .filter(s -> s instanceof Terminal t && isJavaIdentifier(t.name()))
        .map(s -> (Terminal) s)
        .distinct()
        .toList();
  }

  private static String emit(Grammar grammar, Map<NonTerminal, Pattern> patterns, Map<NonTerminal, String> types) {
    var sb = new StringBuilder();

    // nested type declarations
    for (var entry : patterns.entrySet()) {
      emitTypeDeclarations(types, sb, entry.getValue());
    }
    sb.append("\n");

    var startType = types.get(grammar.startSymbol());
    sb.append("class MyVisitor implements Visitor<").append(startType).append("> {\n\n");

    // terminal methods
    var identifierTerminals = collectIdentifierTerminals(grammar);
    for (var term : identifierTerminals) {
      sb.append("  public String ").append(term.name())
          .append("(Terminal terminal) {\n")
          .append("    return terminal.value();\n")
          .append("  }\n\n");
    }

    // production methods
    for (var entry : patterns.entrySet()) {
      emitProductionMethods(grammar, types, sb, entry.getValue());
    }

    sb.append("}\n");
    return sb.toString();
  }

  // -- Type declarations (records / sealed interfaces)

  private static void emitTypeDeclarations(Map<NonTerminal, String> types, StringBuilder sb, Pattern pat) {
    switch (pat) {
      case Pattern.Normal(var nt, var prods) -> {
        if (prods.size() == 1) {
          emitRecord(types, sb, capitalize(nt.name()), null, prods.getFirst());
        } else {
          var sealedName = capitalize(nt.name());
          sb.append("public sealed interface ").append(sealedName)
              .append(" permits ");
          sb.append(prods.stream()
              .map(JavaCodeVisitorGenerator::recordNameForProduction)
              .collect(Collectors.joining(", ")));
          sb.append(" {}\n");
          for (var prod : prods) {
            emitRecord(types, sb, recordNameForProduction(prod), sealedName, prod);
          }
        }
      }
      case Pattern.Optional _, Pattern.ListPattern _ -> { /* no named types needed */ }
    }
  }

  private static void emitRecord(Map<NonTerminal, String> types, StringBuilder sb, String name, @Nullable String sealedParent, Production prod) {
    var params = params(types, prod);
    sb.append("public record ").append(name).append("(");
    sb.append(params.stream()
        .map(p -> p.type() + " " + p.name())
        .collect(Collectors.joining(", ")));
    sb.append(")");
    if (sealedParent != null) {
      sb.append(" implements ").append(sealedParent);
    }
    sb.append(" {}\n");
  }

  private record Param(String type, String name) {}

  private static List<Param> params(Map<NonTerminal, String> types, Production prod) {
    var params = new ArrayList<Param>();
    var terminalNameCounts = new LinkedHashMap<String, Integer>();
    var ntNameCounts = new LinkedHashMap<String, Integer>();
    for (var sym : prod.body()) {
      switch (sym) {
        case Terminal t -> {
          if (isJavaIdentifier(t.name())) {
            params.add(new Param("String", uniqueName(t.name(), terminalNameCounts)));
          }
        }
        case NonTerminal nt -> {
          params.add(new Param(types.get(nt), uniqueName(decapitalize(nt.name()), ntNameCounts)));
        }
      }
    }
    return params;
  }

  private static String uniqueName(String base, Map<String, Integer> counts) {
    var count = counts.merge(base, 1, Integer::sum);
    return count == 1 ? base : base + count;
  }

  // -- Production methods on the Visitor

  private static void emitProductionMethods(Grammar grammar, Map<NonTerminal, String> types, StringBuilder sb, Pattern pat) {
    switch (pat) {
      case Pattern.Normal(var nt, var prods) -> {
        if (prods.size() == 1) {
          emitNormalSingleMethod(types, sb, nt, prods.getFirst());
        } else {
          for (var prod : prods) {
            emitNormalMultiMethod(types, sb, nt, prod);
          }
        }
      }
      case Pattern.Optional(var nt, var sym) -> {
        var prods = grammar.productionsFor(nt);
        for (var prod : prods) {
          emitOptionalMethod(types, sb, nt, sym, prod);
        }
      }
      case Pattern.ListPattern(var nt, var sym) -> {
        var prods = grammar.productionsFor(nt);
        for (var prod : prods) {
          emitListMethod(types, sb, nt, sym, prod);
        }
      }
    }
  }

  private static void emitProductionMethodDeclaration(StringBuilder sb, Production prod, String returnType, String name, List<Param> params) {
    sb.append("  @ProductionName(\"").append(prod.name()).append("\")\n");
    sb.append("  public ").append(returnType).append(" ").append(decapitalize(name)).append("(");
    sb.append(params.stream().map(p -> p.type() + " " + p.name()).collect(Collectors.joining(", ")));
    sb.append(") {\n");
  }

  // Normal pattern, single production → record constructor
  private static void emitNormalSingleMethod(Map<NonTerminal, String> types, StringBuilder sb, NonTerminal nt, Production prod) {
    var returnType = capitalize(nt.name());
    var params = params(types, prod);
    emitProductionMethodDeclaration(sb, prod, returnType, returnType, params);
    sb.append("    return new ").append(returnType).append("(");
    sb.append(params.stream().map(Param::name).collect(Collectors.joining(", ")));
    sb.append(");\n  }\n\n");
  }

  // Normal pattern, multiple productions → sealed subtypes
  private static void emitNormalMultiMethod(Map<NonTerminal, String> types, StringBuilder sb, NonTerminal nt, Production prod) {
    var returnType = capitalize(nt.name());
    var recName = recordNameForProduction(prod);
    var params = params(types, prod);
    emitProductionMethodDeclaration(sb, prod, returnType, recName, params);
    sb.append("    return new ").append(recName).append("(");
    sb.append(params.stream().map(Param::name).collect(Collectors.joining(", ")));
    sb.append(");\n  }\n\n");
  }

  private static String symbolType(Map<NonTerminal, String> types, Symbol sym) {
    return switch(sym) {
      case Terminal t -> "String";
      case NonTerminal nt -> types.get(nt);
    };
  }

  // Optional pattern
  private static void emitOptionalMethod(Map<NonTerminal, String> types, StringBuilder sb, NonTerminal nt, Symbol sym, Production prod) {
    var returnType = types.get(nt);
    if (prod.body().isEmpty()) {
      // epsilon → Optional.empty()
      var params = params(types, prod);
      emitProductionMethodDeclaration(sb, prod, returnType, nt.name() + "Empty", params);
      sb.append("    return Optional.empty();\n");
      sb.append("  }\n\n");
    } else {
      // single symbol → Optional.of(value)
      var params = params(types, prod);
      emitProductionMethodDeclaration(sb, prod, returnType, nt.name() + "Of", params);
      sb.append("    return Optional.of(").append(params.getFirst().name).append(");\n");
      sb.append("  }\n\n");
    }
  }

  // List pattern
  private static void emitListMethod(Map<NonTerminal, String> types, StringBuilder sb, NonTerminal nt, Symbol sym, Production prod) {
    var returnType = types.get(nt);
    if (prod.body().size() == 1) {
      // base case: create list with one element
      var params = params(types, prod);
      emitProductionMethodDeclaration(sb, prod, returnType, nt.name() + "Single", params);
      sb.append("    var list = new Array").append(returnType).append("();\n");
      sb.append("    list.add(").append(params.getFirst().name).append(");\n");
      sb.append("    return list;\n");
      sb.append("  }\n\n");
    } else {
      // recursive case: append to existing list
      var params = params(types, prod);
      emitProductionMethodDeclaration(sb, prod, returnType, nt.name() + "Cons", params);
      sb.append("    ").append(params.get(0).name).append(".add(").append(params.get(1).name).append(");\n");
      sb.append("    return ").append(params.get(0).name).append(";\n");
      sb.append("  }\n\n");
    }
  }

  // -- Helpers

  private static final Map<String, String> SYMBOL_NAMES = Map.ofEntries(
      Map.entry("+",  "Plus"),   Map.entry("-",  "Minus"),  Map.entry("*",  "Mul"),
      Map.entry("/",  "Div"),    Map.entry("%",  "Mod"),    Map.entry("^",  "Pow"),
      Map.entry("&",  "And"),    Map.entry("|",  "Or"),     Map.entry("~",  "BitNot"),
      Map.entry("!",  "Not"),    Map.entry("<",  "Lt"),     Map.entry(">",  "Gt"),
      Map.entry("<=", "Le"),     Map.entry(">=", "Ge"),     Map.entry("==", "Eq"),
      Map.entry("!=", "Ne"),     Map.entry("=",  "Assign"), Map.entry("->", "Arrow"),
      Map.entry("=>", "FatArrow"), Map.entry("::", "ColonColon"), Map.entry(":", "Colon"),
      Map.entry(";",  "Semi"),   Map.entry(",",  "Comma"),  Map.entry(".",  "Dot"),
      Map.entry("..", "DotDot"), Map.entry("(",  "LParen"), Map.entry(")",  "RParen"),
      Map.entry("{",  "LBrace"), Map.entry("}",  "RBrace"), Map.entry("[",  "LBracket"),
      Map.entry("]",  "RBracket")
  );

  private static String terminalSegment(Terminal t) {
    if (isJavaIdentifier(t.name())) {
      return capitalize(t.name());
    }
    var name = SYMBOL_NAMES.get(t.name());
    return name == null ? "Unknown" : name;
  }

  private static String recordNameForProduction(Production production) {
    // Build a CamelCase name from all symbols that have a known name segment
    var builder = new StringBuilder();
    for (var symbol : production.body()) {
      switch (symbol) {
        case Terminal t -> builder.append(terminalSegment(t));
        case NonTerminal nt -> builder.append(capitalize(nt.name()));
      }
    }
    builder.append(capitalize(production.head().name()));
    return builder.toString();
  }

  private static String paramNameFor(Symbol symbol) {
    return decapitalize(switch (symbol) {
      case Terminal t -> isJavaIdentifier(t.name()) ? t.name() : "value";
      case NonTerminal nt -> nt.name();
    });
  }

  private static String capitalize(String s) {
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static String decapitalize(String s) {
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }
}