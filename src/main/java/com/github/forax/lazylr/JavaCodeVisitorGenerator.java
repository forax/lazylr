package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// Generates source code for a [Visitor] implementation from a [Grammar] definition.
///
/// Call [#generateVisitor(Grammar)] to get a Java source string
/// containing a ready-to-compile visitor class.
public final class JavaCodeVisitorGenerator {

  private sealed interface Pattern { }
  /// A non-terminal that matches neither Optional nor List.
  record NormalPattern(NonTerminal head, List<Production> productions) implements Pattern {}
  /// NT with two productions: one single-symbol body, one empty body.
  record OptionalPattern(NonTerminal head, Symbol symbol, Production emptyProduction) implements Pattern {}
  /// NT with two productions: one single-symbol body, one recursive two-symbol body.
  record ListPattern(NonTerminal head, Symbol element, Production singleProduction) implements Pattern {}

  private JavaCodeVisitorGenerator() {
    throw new AssertionError();
  }

  /// Generates a visitor source file.
  ///
  /// @param grammar   the grammar to generate a visitor for
  /// @return Java source code as a string
  public static String generateVisitor(Grammar grammar) {
    // step 1: pattern detection
    var patterns = buildPatterns(grammar);

    // step 2: type resolution
    var types = resolveTypes(grammar, patterns);

    // step 3: code emission
    return emitCode(grammar, patterns, types);
  }


  // -- Step 1: pattern detection

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
      var optPat = tryOptional(nt, filtered, prods);
      if (optPat != null) {
        return optPat;
      }
      var listPat = tryList(nt, filtered, prods);
      if (listPat != null) {
        return listPat;
      }
    }
    return new NormalPattern(nt, prods);
  }

  private static @Nullable OptionalPattern tryOptional(NonTerminal nt, List<List<Symbol>> filtered, List<Production> prods) {
    var emptyProduction = (Production) null;
    var symbol = (Symbol) null;
    for (var i = 0; i < filtered.size(); i++) {
      var body = filtered.get(i);
      switch (body.size()) {
        case 0 -> emptyProduction = prods.get(i);
        case 1 -> symbol = body.getFirst();
        default -> {
          return null;
        }
      }
    }
    if (emptyProduction == null || symbol == null) {
      return null;
    }
    return new OptionalPattern(nt, symbol, emptyProduction);
  }

  // The grammar is supposed to be LR, so only check for left-recursive list pattern
  private static @Nullable ListPattern tryList(NonTerminal nt, List<List<Symbol>> filtered, List<Production> prods) {
    var singleSymbol = (Symbol) null;
    var recBody = (List<Symbol>) null;
    var singleProduction = (Production) null;
    for (var i = 0; i < filtered.size(); i++) {
      var body = filtered.get(i);
      switch (body.size()) {
        case 1 -> { singleSymbol = body.getFirst(); singleProduction = prods.get(i); }
        case 2 -> recBody = body;
        default -> {
          return null;
        }
      }
    }
    if (recBody == null || !recBody.get(0).equals(nt) || !recBody.get(1).equals(singleSymbol)) {
      return null;
    }
    return new ListPattern(nt, singleSymbol, singleProduction);
  }

  // -- Step 2: type resolution

  private static Map<NonTerminal, String> resolveTypes(Grammar grammar, Map<NonTerminal, Pattern> patterns) {
    var map = new LinkedHashMap<NonTerminal, String>();
    for (var nt : grammar.nonTerminals()) {
      typeOf(patterns, nt, map);
    }
    return map;
  }

  /// Returns (and memoises) the Java type string for `nt`.
  /// No need to have cycle detection, normal non-terminals have a name, other patterns
  /// cannot create a cycle
  private static String typeOf(Map<NonTerminal, Pattern> patterns, NonTerminal nt, Map<NonTerminal, String> memo) {
    var cached = memo.get(nt);
    if (cached != null) {
      return cached;
    }
    var pat = patterns.get(nt);
    var result = switch (pat) {
      case OptionalPattern(var _, var sym, Production emptyProduction) ->
          "Optional<" + findSymbolType(patterns, sym, memo) + ">";
      case ListPattern(var _, var sym, Production singleProduction) ->
          "List<" + findSymbolType(patterns, sym, memo) + ">";
      case NormalPattern(var head, var _) ->
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

  // -- Step 3: code emission

  private static List<Terminal> collectIdentifierTerminals(Grammar grammar) {
    return grammar.productions().stream()
        .flatMap(p -> p.body().stream())
        .filter(s -> s instanceof Terminal t && isJavaIdentifier(t.name()))
        .map(s -> (Terminal) s)
        .distinct()
        .toList();
  }

  private static String emitCode(Grammar grammar, Map<NonTerminal, Pattern> patterns, Map<NonTerminal, String> types) {
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
      case NormalPattern(var nt, var prods) -> {
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
      case OptionalPattern _, ListPattern _ -> { /* no named types needed */ }
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
        case NonTerminal nt ->
          params.add(new Param(types.get(nt), uniqueName(decapitalize(nt.name()), ntNameCounts)));
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
      case NormalPattern(var nt, var prods) -> {
        if (prods.size() == 1) {
          emitNormalSingleMethod(types, sb, nt, prods.getFirst());
        } else {
          for (var prod : prods) {
            emitNormalMultiMethod(types, sb, nt, prod);
          }
        }
      }
      case OptionalPattern(var nt, var _, Production emptyProduction) -> {
        var prods = grammar.productionsFor(nt);
        for (var prod : prods) {
          emitOptionalMethod(types, sb, nt, prod, prod == emptyProduction);
        }
      }
      case ListPattern(var nt, var _, Production singleProduction) -> {
        var prods = grammar.productionsFor(nt);
        for (var prod : prods) {
          emitListMethod(types, sb, nt, prod, prod == singleProduction);
        }
      }
    }
  }

  private static void emitProductionMethodDeclaration(StringBuilder sb, Production prod, String returnType, String name, List<Param> params) {
    sb.append("  @ProductionName(\"").append(prod.name()).append("\")\n");
    sb.append("  public ").append(returnType).append(" ").append(name).append("(");
    sb.append(params.stream().map(p -> p.type + " " + p.name).collect(Collectors.joining(", ")));
    sb.append(") {\n");
  }

  // Normal pattern, single production → record constructor
  private static void emitNormalSingleMethod(Map<NonTerminal, String> types, StringBuilder sb, NonTerminal nt, Production prod) {
    var returnType = capitalize(nt.name());
    var params = params(types, prod);
    emitProductionMethodDeclaration(sb, prod, returnType, decapitalize(returnType), params);
    sb.append("    return new ").append(returnType).append("(");
    sb.append(params.stream().map(Param::name).collect(Collectors.joining(", ")));
    sb.append(");\n  }\n\n");
  }

  // Normal pattern, multiple productions → sealed subtypes
  private static void emitNormalMultiMethod(Map<NonTerminal, String> types, StringBuilder sb, NonTerminal nt, Production prod) {
    var returnType = capitalize(nt.name());
    var recName = recordNameForProduction(prod);
    var params = params(types, prod);
    emitProductionMethodDeclaration(sb, prod, returnType, decapitalize(recName), params);
    sb.append("    return new ").append(recName).append("(");
    sb.append(params.stream().map(Param::name).collect(Collectors.joining(", ")));
    sb.append(");\n  }\n\n");
  }

  // Optional pattern
  private static void emitOptionalMethod(Map<NonTerminal, String> types, StringBuilder sb, NonTerminal nt, Production prod, boolean isEmptyProduction) {
    var returnType = types.get(nt);
    var params = params(types, prod);
    if (isEmptyProduction) {
      // epsilon → Optional.empty()
      emitProductionMethodDeclaration(sb, prod, returnType, decapitalize(nt.name()) + "Empty", params);
      sb.append("    return Optional.empty();\n");
      sb.append("  }\n\n");
    } else {
      // single symbol → Optional.of(value)
      emitProductionMethodDeclaration(sb, prod, returnType, decapitalize(nt.name()) + "Of", params);
      sb.append("    return Optional.of(").append(params.getFirst().name).append(");\n");
      sb.append("  }\n\n");
    }
  }

  // List pattern
  private static void emitListMethod(Map<NonTerminal, String> types, StringBuilder sb, NonTerminal nt, Production prod, boolean isSingleProduction) {
    var returnType = types.get(nt);
    var params = params(types, prod);
    if (isSingleProduction) {
      // base case: create list with one element
      var elementType = returnType.substring(returnType.indexOf('<')+ 1, returnType.length() - 1);
      emitProductionMethodDeclaration(sb, prod, returnType, decapitalize(nt.name()) + "Single", params);
      sb.append("    var list = new ArrayList<").append(elementType).append(">();\n");
      sb.append("    list.add(").append(params.getFirst().name).append(");\n");
      sb.append("    return list;\n");
      sb.append("  }\n\n");
    } else {
      // recursive case: append to existing list
      emitProductionMethodDeclaration(sb, prod, returnType, decapitalize(nt.name()) + "Cons", params);
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

  private static String capitalize(String s) {
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static String decapitalize(String s) {
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }
}