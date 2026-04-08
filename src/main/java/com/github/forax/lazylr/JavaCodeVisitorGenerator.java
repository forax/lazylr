package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.stream.Collectors;

/// Generates source code for a [Visitor] implementation from a [Grammar] definition.
///
/// Call [#generate(Grammar, String)] to obtain a Java source string
/// containing a ready-to-compile visitor class.
public final class JavaCodeVisitorGenerator {

  // ── Pattern model ────────────────────────────────────────────────────────────

  sealed interface Pattern {
    /// A non-terminal that matches neither Optional nor List.
    record Normal(NonTerminal head, List<Production> productions) implements Pattern {}
    /// NT with two productions: one single-symbol body, one empty body.
    record Optional(NonTerminal head, Symbol symbol) implements Pattern {}
    /// NT with two productions: one single-symbol body, one recursive two-symbol body.
    record ListPattern(NonTerminal head, Symbol element) implements Pattern {}
  }

  // ── Public API ───────────────────────────────────────────────────────────────

  /// Generates a visitor source file.
  ///
  /// @param grammar   the grammar to generate a visitor for
  /// @return Java source code as a string
  public static String generate(Grammar grammar) {
    var gen = new JavaCodeVisitorGenerator(grammar);
    return gen.emit();
  }

  // ── Internal state ────────────────────────────────────────────────────────────

  private final Grammar grammar;
  /// Terminals whose names are valid Java identifiers.
  private final List<Terminal> identifierTerminals;
  /// Pattern for every non-terminal, in grammar declaration order.
  private final SequencedMap<NonTerminal, Pattern> patterns;
  /// Resolved Java type name for each non-terminal.
  private final Map<NonTerminal, String> types;

  private JavaCodeVisitorGenerator(Grammar grammar) {
    this.grammar = grammar;
    this.identifierTerminals = collectIdentifierTerminals();
    this.patterns = buildPatterns();
    this.types = resolveTypes();
  }

  // ── Step 1 – collect identifier terminals ────────────────────────────────────

  private List<Terminal> collectIdentifierTerminals() {
    return grammar.productions().stream()
        .flatMap(p -> p.body().stream())
        .filter(s -> s instanceof Terminal t && isJavaIdentifier(t.name()))
        .map(s -> (Terminal) s)
        .distinct()
        .toList();
  }

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

  // ── Step 2 – recognise patterns ──────────────────────────────────────────────

  /// Returns the body of a production with non-identifier terminals filtered out.
  private static List<Symbol> filteredBody(Production prod) {
    return prod.body().stream()
        .filter(s -> !(s instanceof Terminal t) || isJavaIdentifier(t.name()))
        .toList();
  }

  private SequencedMap<NonTerminal, Pattern> buildPatterns() {
    var map = new LinkedHashMap<NonTerminal, Pattern>();
    for (var nt : grammar.nonTerminals()) {
      map.put(nt, classify(nt, grammar.productionsFor(nt)));
    }
    return map;
  }

  private static @Nullable Pattern classify(NonTerminal nt, List<Production> prods) {
    // Work on filtered bodies for pattern recognition
    var filtered = prods.stream().map(JavaCodeVisitorGenerator::filteredBody).toList();
    if (filtered.size() == 2) {
      var optPat = tryOptional(nt, prods, filtered);
      if (optPat != null) return optPat;
      var listPat = tryList(nt, prods, filtered);
      if (listPat != null) return listPat;
    }
    return new Pattern.Normal(nt, prods);
  }

  private static Pattern.@Nullable Optional tryOptional(NonTerminal nt, List<Production> prods, List<List<Symbol>> filtered) {
    Production single = null, empty = null;
    for (var i = 0; i < filtered.size(); i++) {
      var body = filtered.get(i);
      if (body.isEmpty()) empty = prods.get(i);
      else if (body.size() == 1) single = prods.get(i);
    }
    if (single == null || empty == null) return null;
    return new Pattern.Optional(nt, filteredBody(single).getFirst());
  }

  private static Pattern.@Nullable ListPattern tryList(NonTerminal nt, List<Production> prods, List<List<Symbol>> filtered) {
    Production single = null, recursive = null;
    List<Symbol> singleBody = null, recBody = null;
    for (var i = 0; i < filtered.size(); i++) {
      var body = filtered.get(i);
      if (body.size() == 1) { single = prods.get(i); singleBody = body; }
      else if (body.size() == 2) { recursive = prods.get(i); recBody = body; }
    }
    if (single == null || recursive == null) return null;
    if (!recBody.getFirst().equals(nt)) return null;
    if (!recBody.get(1).equals(singleBody.getFirst())) return null;
    return new Pattern.ListPattern(nt, singleBody.getFirst());
  }

  // ── Step 3 – resolve Java types (lazy, memoised, cycle-safe) ────────────────

  private Map<NonTerminal, String> resolveTypes() {
    var map = new LinkedHashMap<NonTerminal, String>();
    for (var nt : grammar.nonTerminals()) {
      typeOf(nt, map);
    }
    return map;
  }

  /// Returns (and memoises) the Java type string for `nt`.
  private String typeOf(NonTerminal nt, Map<NonTerminal, String> memo) {
    var cached = memo.get(nt);
    if (cached != null) {
      return cached;
    }
    var pat = patterns.get(nt);
    var result = switch (pat) {
      case Pattern.Optional(var head, var sym) ->
          "Optional<" + symbolType(sym, memo) + ">";
      case Pattern.ListPattern(var head, var sym) ->
          "List<" + symbolType(sym, memo) + ">";
      case Pattern.Normal(var head, var prods) ->
          capitalize(head.name());       // record or sealed interface — name never depends on other NTs
    };
    memo.put(nt, result);
    return result;
  }

  private String symbolType(Symbol sym, Map<NonTerminal, String> memo) {
    return switch (sym) {
      case Terminal t -> "String";
      case NonTerminal nt -> typeOf(nt, memo);
    };
  }

  private String symbolType(Symbol sym) {
    return symbolType(sym, types);
  }

  // ── Step 4 – code emission ───────────────────────────────────────────────────

  private String emit() {
    var sb = new StringBuilder();

    // ── Nested type declarations ──────────────────────────────────────────────
    for (var entry : patterns.entrySet()) {
      emitTypeDeclarations(sb, entry.getValue());
    }
    sb.append("\n");

    // ── Visitor class ─────────────────────────────────────────────────────────
    var startType = types.get(grammar.startSymbol());
    sb.append("class MyVisitor implements Visitor<").append(startType).append("> {\n\n");

    // terminal methods
    for (var term : identifierTerminals) {
      sb.append("  public String ").append(term.name())
          .append("(Terminal terminal) {\n")
          .append("    return terminal.value();\n")
          .append("  }\n\n");
    }

    // production methods
    for (var entry : patterns.entrySet()) {
      emitProductionMethods(sb, entry.getValue());
    }

    sb.append("}\n");
    return sb.toString();
  }

  // ── Type declarations (records / sealed interfaces) ───────────────────────────

  private void emitTypeDeclarations(StringBuilder sb, Pattern pat) {
    switch (pat) {
      case Pattern.Normal(var nt, var prods) -> {
        if (prods.size() == 1) {
          emitRecord(sb, capitalize(nt.name()), null, prods.getFirst());
        } else {
          var sealedName = capitalize(nt.name());
          sb.append("public sealed interface ").append(sealedName)
              .append(" permits ");
          sb.append(prods.stream()
              .map(p -> recordNameForProduction(p))
              .collect(Collectors.joining(", ")));
          sb.append(" {}\n");
          for (var prod : prods) {
            emitRecord(sb, recordNameForProduction(prod), sealedName, prod);
          }
        }
      }
      case Pattern.Optional _, Pattern.ListPattern _ -> { /* no named types needed */ }
    }
  }

  private void emitRecord(StringBuilder sb, String name, @Nullable String sealedParent, Production prod) {
    var params = recordParams(prod);
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

  private List<Param> recordParams(Production prod) {
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
          params.add(new Param(types.getOrDefault(nt, capitalize(nt.name())),
              uniqueName(decapitalize(nt.name()), ntNameCounts)));
        }
      }
    }
    return params;
  }

  private static String uniqueName(String base, Map<String, Integer> counts) {
    var count = counts.merge(base, 1, Integer::sum);
    return count == 1 ? base : base + count;
  }

  // ── Production methods on the Visitor ────────────────────────────────────────

  private void emitProductionMethods(StringBuilder sb, Pattern pat) {
    switch (pat) {
      case Pattern.Normal(var nt, var prods) -> {
        if (prods.size() == 1) {
          emitNormalSingleMethod(sb, nt, prods.getFirst());
        } else {
          for (var prod : prods) {
            emitNormalMultiMethod(sb, nt, prod);
          }
        }
      }
      case Pattern.Optional(var nt, var sym) -> {
        var prods = grammar.productionsFor(nt);
        for (var prod : prods) {
          emitOptionalMethod(sb, nt, sym, prod);
        }
      }
      case Pattern.ListPattern(var nt, var sym) -> {
        var prods = grammar.productionsFor(nt);
        for (var prod : prods) {
          emitListMethod(sb, nt, sym, prod);
        }
      }
    }
  }

  // Normal – single production → record constructor
  private void emitNormalSingleMethod(StringBuilder sb, NonTerminal nt, Production prod) {
    var returnType = capitalize(nt.name());
    var params = recordParams(prod);
    if (params.isEmpty() && prod.body().isEmpty()) {
      // epsilon with single production – edge case, just return empty record
      sb.append("  @ProductionName(\"").append(prod.name()).append("\")\n");
      sb.append("  public ").append(returnType).append(" ")
          .append(decapitalize(returnType)).append("() {\n");
      sb.append("    return new ").append(returnType).append("();\n");
      sb.append("  }\n\n");
      return;
    }
    if (params.isEmpty()) {
      // all symbols are non-identifier terminals – pass-through (single body)
      // handled by Visitor default pass-through; no method needed
      return;
    }
    sb.append("  @ProductionName(\"").append(prod.name()).append("\")\n");
    sb.append("  public ").append(returnType).append(" ")
        .append(decapitalize(returnType)).append("(");
    sb.append(params.stream().map(p -> p.type() + " " + p.name()).collect(Collectors.joining(", ")));
    sb.append(") {\n");
    sb.append("    return new ").append(returnType).append("(");
    sb.append(params.stream().map(Param::name).collect(Collectors.joining(", ")));
    sb.append(");\n  }\n\n");
  }

  // Normal – multiple productions → sealed subtypes
  private void emitNormalMultiMethod(StringBuilder sb, NonTerminal nt, Production prod) {
    var returnType = capitalize(nt.name());
    var recName = recordNameForProduction(prod);
    var params = recordParams(prod);
    if (params.isEmpty() && !prod.body().isEmpty()) {
      // all symbols are non-identifier terminals – no method, pass-through
      return;
    }
    sb.append("  @ProductionName(\"").append(prod.name()).append("\")\n");
    sb.append("  public ").append(returnType).append(" ")
        .append(decapitalize(recName)).append("(");
    sb.append(params.stream().map(p -> p.type() + " " + p.name()).collect(Collectors.joining(", ")));
    sb.append(") {\n");
    sb.append("    return new ").append(recName).append("(");
    sb.append(params.stream().map(Param::name).collect(Collectors.joining(", ")));
    sb.append(");\n  }\n\n");
  }

  // Optional pattern
  private void emitOptionalMethod(StringBuilder sb, NonTerminal nt, Symbol sym, Production prod) {
    var returnType = "Optional<" + symbolType(sym) + ">";
    sb.append("  @ProductionName(\"").append(prod.name()).append("\")\n");
    if (prod.body().isEmpty()) {
      // epsilon → Optional.empty()
      sb.append("  public ").append(returnType).append(" ")
          .append(nt.name()).append("Empty() {\n");
      sb.append("    return Optional.empty();\n");
      sb.append("  }\n\n");
    } else {
      // single symbol → Optional.of(value)
      var paramType = symbolType(prod.body().getFirst());
      var paramName = paramNameFor(prod.body().getFirst());
      sb.append("  public ").append(returnType).append(" ")
          .append(nt.name()).append("(").append(paramType).append(" ").append(paramName).append(") {\n");
      sb.append("    return Optional.of(").append(paramName).append(");\n");
      sb.append("  }\n\n");
    }
  }

  // List pattern
  private void emitListMethod(StringBuilder sb, NonTerminal nt, Symbol sym, Production prod) {
    var elemType = symbolType(sym);
    var returnType = "List<" + elemType + ">";
    sb.append("  @ProductionName(\"").append(prod.name()).append("\")\n");
    if (prod.body().size() == 1) {
      // base case: create list with one element
      var paramName = paramNameFor(prod.body().getFirst());
      sb.append("  public ").append(returnType).append(" ")
          .append(nt.name()).append("Single(").append(elemType).append(" ").append(paramName).append(") {\n");
      sb.append("    var list = new ArrayList<").append(elemType).append(">();\n");
      sb.append("    list.add(").append(paramName).append(");\n");
      sb.append("    return list;\n");
      sb.append("  }\n\n");
    } else {
      // recursive case: append to existing list
      var listParamName = nt.name();
      var elemParamName = paramNameFor(prod.body().get(1));
      sb.append("  public ").append(returnType).append(" ")
          .append(nt.name()).append("Cons(")
          .append(returnType).append(" ").append(listParamName).append(", ")
          .append(elemType).append(" ").append(elemParamName).append(") {\n");
      sb.append("    ").append(listParamName).append(".add(").append(elemParamName).append(");\n");
      sb.append("    return ").append(listParamName).append(";\n");
      sb.append("  }\n\n");
    }
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

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

  private String recordNameForProduction(Production production) {
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

  private String paramNameFor(Symbol symbol) {
    return switch (symbol) {
      case Terminal t -> isJavaIdentifier(t.name()) ? t.name() : "value";
      case NonTerminal nt -> decapitalize(nt.name());
    };
  }

  private static String capitalize(String s) {
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static String decapitalize(String s) {
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }
}