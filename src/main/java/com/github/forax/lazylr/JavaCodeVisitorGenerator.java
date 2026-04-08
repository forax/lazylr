package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// Generates source code for a [Visitor] implementation from a [Grammar] definition.
///
/// The generator runs in three phases:
/// 1. **Pattern detection** — each non-terminal is classified as [NormalPattern],
///    [OptionalPattern], or [ListPattern] based on the shape of its productions.
/// 2. **Type resolution** — a Java type string (`String`, `Optional<X>`, `List<X>`,
///    or a record/sealed-interface name) is assigned to each non-terminal.
///    Resolution is memoised and follows symbol references recursively.
/// 3. **Code emission** — sealed interfaces, records, and visitor methods are
///    written to a source string ready for compilation.
///
/// @see #generateVisitor(Grammar)
final class JavaCodeVisitorGenerator {

  /// Recognized pattern of the productions of non-terminal.
  ///
  /// Exactly one subtype is chosen per non-terminal during [#classify(NonTerminal, List)].
  /// Downstream phases switch on the subtype and read the fields directly,
  /// without re-inspecting grammar productions.
  private sealed interface Pattern {}

  /// A non-terminal that does not match the [OptionalPattern] or [ListPattern] shapes.
  /// @param head        the non-terminal being classified.
  /// @param productions all productions for `head`, in grammar order.
  private record NormalPattern(NonTerminal head, List<Production> productions) implements Pattern {}

  /// A non-terminal with exactly two productions: one empty body (ε) and one
  /// single-symbol body.
  ///
  /// @param head            the non-terminal being classified
  /// @param symbol          the single symbol of the non-empty production
  /// @param emptyProduction the ε production (used to emit `Optional.empty()`)
  private record OptionalPattern(NonTerminal head, Symbol symbol, Production emptyProduction) implements Pattern {}

  /// A non-terminal with exactly two productions: a single-symbol base case and
  /// a left-recursive two-symbol case of the form {@code NT sym}.
  ///
  /// @param head             the non-terminal being classified.
  /// @param element          the element symbol (shared by both productions).
  /// @param singleProduction the base production (used to emit `List<Element>`).
  private record ListPattern(NonTerminal head, Symbol element, Production singleProduction) implements Pattern {}

  private JavaCodeVisitorGenerator() {
    throw new AssertionError();
  }

  /// Generates a visitor source file for the given grammar.
  ///
  /// The returned string contains the Java source (with no imports) that can be written
  /// directly to a `.java` file or compiled in-memory.
  /// The class is named  {@code MyVisitor} and implements {@code Visitor<T>} where {@code T}
  /// is the resolved type of the grammar's start symbol.
  ///
  /// @param grammar the grammar to generate a visitor for.
  /// @return Java source code as a string.
  public static String generateVisitor(Grammar grammar) {
    var patterns = buildPatterns(grammar);
    var types = resolveTypes(grammar, patterns);
    return emitCode(grammar, patterns, types);
  }


  // -- Step 1: pattern detection


  /// Returns `true` if `name` is a legal Java identifier.
  ///
  /// Used to distinguish "meaningful" terminals (keywords, identifiers such as
  /// `num` or `id` from punctuation terminals (such as `+``or `;`)
  /// that are excluded from generated parameter lists.
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

  /// Returns the body of production with non-identifier terminals removed.
  ///
  /// Pattern recognition and parameter generation both work on filtered bodies
  /// so that punctuation does not influence the shape of a production.
  private static List<Symbol> filteredBody(Production production) {
    return production.body().stream()
        .filter(s -> !(s instanceof Terminal t) || isJavaIdentifier(t.name()))
        .toList();
  }

  /// Classifies every non-terminal in the grammar and returns the result as a
  /// map keyed by non-terminal, in grammar declaration order.
  private static Map<NonTerminal, Pattern> buildPatterns(Grammar grammar) {
    var map = new LinkedHashMap<NonTerminal, Pattern>();
    for (var nonTerminal : grammar.nonTerminals()) {
      map.put(nonTerminal, classify(nonTerminal, grammar.productionsFor(nonTerminal)));
    }
    return map;
  }

  /// Classifies a single non-terminal into a [Pattern].
  ///
  /// The two-production shortcut ([OptionalPattern], [ListPattern]) is attempted
  /// first; anything that does not match falls through to [NormalPattern].
  ///
  /// @param nonTerminal the non-terminal to classify.
  /// @param productions its productions, in grammar order.
  private static Pattern classify(NonTerminal nonTerminal, List<Production> productions) {
    var filtered = productions.stream().map(JavaCodeVisitorGenerator::filteredBody).toList();
    if (filtered.size() == 2) {
      var optPat = tryOptional(nonTerminal, filtered, productions);
      if (optPat != null) {
        return optPat;
      }
      var listPat = tryList(nonTerminal, filtered, productions);
      if (listPat != null) {
        return listPat;
      }
    }
    return new NormalPattern(nonTerminal, productions);
  }

  /// Tries to match the [OptionalPattern] shape: one ε body and one
  /// single-symbol body (in either order).
  ///
  /// @return the pattern, or {@code null} if the productions do not match
  private static @Nullable OptionalPattern tryOptional(NonTerminal nonTerminal, List<List<Symbol>> filtered,
                                                       List<Production> productions) {
    var emptyProduction = (Production) null;
    var symbol = (Symbol) null;
    for (var i = 0; i < filtered.size(); i++) {
      var body = filtered.get(i);
      switch (body.size()) {
        case 0 -> emptyProduction = productions.get(i);
        case 1 -> symbol = body.getFirst();
        default -> { return null; }
      }
    }
    if (emptyProduction == null || symbol == null) {
      return null;
    }
    return new OptionalPattern(nonTerminal, symbol, emptyProduction);
  }

  /// Tries to match the [ListPattern] shape: one single-symbol body and one
  /// left-recursive two-symbol body of the form {@code NT sym} (LR grammar only).
  ///
  /// @return the pattern, or {@code null} if the productions do not match
  private static @Nullable ListPattern tryList(NonTerminal nonTerminal, List<List<Symbol>> filtered,
                                               List<Production> productions) {
    var singleSymbol = (Symbol) null;
    var recBody = (List<Symbol>) null;
    var singleProduction = (Production) null;
    for (var i = 0; i < filtered.size(); i++) {
      var body = filtered.get(i);
      switch (body.size()) {
        case 1 -> { singleSymbol = body.getFirst(); singleProduction = productions.get(i); }
        case 2 -> recBody = body;
        default -> { return null; }
      }
    }
    if (recBody == null || !recBody.get(0).equals(nonTerminal) || !recBody.get(1).equals(singleSymbol)) {
      return null;
    }
    return new ListPattern(nonTerminal, singleSymbol, singleProduction);
  }

  // -- Step 2: type resolution

  /// Resolves a Java type string for every non-terminal and returns the result
  /// as a map.  [#typeOf] is called for each non-terminal in grammar order; the
  /// memo map makes repeated calls for the same non-terminal cheap.
  private static Map<NonTerminal, String> resolveTypes(Grammar grammar, Map<NonTerminal, Pattern> patternMap) {
    var map = new LinkedHashMap<NonTerminal, String>();
    for (var nt : grammar.nonTerminals()) {
      typeOf(patternMap, nt, map);
    }
    return map;
  }

  /// Returns (and memoises) the Java type string for `nonTerminal`.
  ///
  /// - [NormalPattern] -> the capitalised non-terminal name (record or sealed
  ///   interface name); this never depends on other non-terminals, so no cycle
  ///   can arise.
  /// - [OptionalPattern] -> `Optional<E>` where {@code E} is the type of the
  ///   contained symbol.
  /// - [ListPattern] -> `List<E>` where {@code E} is the type of the element
  ///   symbol.
  ///
  /// @param patternMap  the classification map produced by [#buildPatterns].
  /// @param nonTerminal the non-terminal whose type is needed.
  /// @param memo        accumulates results; entries are added as a side effect.
  private static String typeOf(Map<NonTerminal, Pattern> patternMap, NonTerminal nonTerminal,
                               Map<NonTerminal, String> memo) {
    var cached = memo.get(nonTerminal);
    if (cached != null) {
      return cached;
    }
    var type = switch (patternMap.get(nonTerminal)) {
      case OptionalPattern(_, Symbol sym, _) ->
          "Optional<" + findSymbolType(patternMap, sym, memo) + ">";
      case ListPattern(_, Symbol sym, _) ->
          "List<" + findSymbolType(patternMap, sym, memo) + ">";
      case NormalPattern(NonTerminal head, _) ->
          capitalize(head.name());
    };
    memo.put(nonTerminal, type);
    return type;
  }

  /// Returns the Java type of a symbol: `String` for terminals, the resolved
  /// non-terminal type (via [#typeOf]) for non-terminals.
  private static String findSymbolType(Map<NonTerminal, Pattern> patternMap, Symbol symbol,
                                       Map<NonTerminal, String> memo) {
    return switch (symbol) {
      case Terminal _ -> "String";
      case NonTerminal nt -> typeOf(patternMap, nt, memo);
    };
  }

  // -- Step 3: code emission

  /// Collects all distinct identifier terminals that appear in any production
  /// body across the whole grammar, in encounter order.
  ///
  /// Each collected terminal gets a corresponding visitor method of the form
  /// {@code public String name(Terminal terminal)}.
  private static List<Terminal> collectIdentifierTerminals(Grammar grammar) {
    return grammar.productions().stream()
        .flatMap(p -> p.body().stream())
        .filter(s -> s instanceof Terminal t && isJavaIdentifier(t.name()))
        .map(s -> (Terminal) s)
        .distinct()
        .toList();
  }

  /// Emits the complete Java source: type declarations, then the visitor class
  /// with terminal methods and production methods.
  private static String emitCode(Grammar grammar, Map<NonTerminal, Pattern> patternMap,
                                 Map<NonTerminal, String> types) {
    var sb = new StringBuilder();

    for (var entry : patternMap.entrySet()) {
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
    for (var entry : patternMap.entrySet()) {
      emitProductionMethods(grammar, types, sb, entry.getValue());
    }

    sb.append("}\n");
    return sb.toString();
  }

  // -- Type declarations (records / sealed interfaces)

  /// Emits the record or sealed-interface declaration(s) for one pattern.
  ///
  /// - [NormalPattern] with one production → one `record`.
  /// - [NormalPattern] with multiple productions → one `sealed interface` plus
  ///   one `record` per production.
  /// - [OptionalPattern] / [ListPattern] → no named types needed; `Optional`
  ///   and `List` from the JDK are used directly.
  private static void emitTypeDeclarations(Map<NonTerminal, String> types, StringBuilder sb, Pattern pattern) {
    switch (pattern) {
      case NormalPattern(var nt, var prods) -> {
        if (prods.size() == 1) {
          emitRecord(types, sb, capitalize(nt.name()), null, prods.getFirst());
        } else {
          var sealedName = capitalize(nt.name());
          sb.append("public sealed interface ").append(sealedName).append(" permits ");
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

  /// Emits a single `public record` declaration.
  ///
  /// @param types        the type map non-terminal -> type, produced by [#resolveTypes].
  /// @param sb           the string builder to append to.
  /// @param name         the record class name.
  /// @param sealedParent the sealed interface it implements, or `null`.
  /// @param prod         the production whose meaningful symbols become components.
  private static void emitRecord(Map<NonTerminal, String> types, StringBuilder sb, String name,
                                 @Nullable String sealedParent, Production prod) {
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

  /// A typed, named parameter derived from a single production symbol.
  private record Param(String type, String name) {}

  /// Builds the ordered parameter list for `production`, skipping non-identifier
  /// terminals and deduplicating repeated names with a numeric suffix.
  private static List<Param> params(Map<NonTerminal, String> types, Production production) {
    var params = new ArrayList<Param>();
    var terminalNameCounts = new LinkedHashMap<String, Integer>();
    var ntNameCounts = new LinkedHashMap<String, Integer>();
    for (var sym : production.body()) {
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

  /// Returns base` if it has not been seen before in `counts`,
  /// or {`base` + N} (N >= 2) on later occurrences.
  private static String uniqueName(String base, Map<String, Integer> counts) {
    var count = counts.merge(base, 1, Integer::sum);
    return count == 1 ? base : base + count;
  }

  // -- Production methods on the Visitor

  /// Emits all visitor methods for one pattern.  Dispatches to a specialized
  /// emitter based on the concrete pattern type.
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
      case OptionalPattern(NonTerminal nt, _, Production emptyProduction) -> {
        for (var prod : grammar.productionsFor(nt)) {
          emitOptionalMethod(types, sb, nt, prod, prod == emptyProduction);
        }
      }
      case ListPattern(NonTerminal nt, _, Production singleProduction) -> {
        for (var prod : grammar.productionsFor(nt)) {
          emitListMethod(types, sb, nt, prod, prod == singleProduction);
        }
      }
    }
  }

  /// Emits the `@ProductionName` annotation, return type, method name, and
  /// parameter list for one visitor method, leaving the body open.
  private static void emitProductionMethodDeclaration(StringBuilder sb, Production prod, String returnType, String name, List<Param> params) {
    sb.append("  @ProductionName(\"").append(prod.name()).append("\")\n");
    sb.append("  public ").append(returnType).append(" ").append(name).append("(");
    sb.append(params.stream().map(p -> p.type + " " + p.name).collect(Collectors.joining(", ")));
    sb.append(") {\n");
  }

  /// Emits a visitor method for a [NormalPattern] with a single production.
  /// The method body calls the record's constructor directly.
  private static void emitNormalSingleMethod(Map<NonTerminal, String> types, StringBuilder sb, NonTerminal nt, Production prod) {
    var returnType = capitalize(nt.name());
    var params = params(types, prod);
    emitProductionMethodDeclaration(sb, prod, returnType, decapitalize(returnType), params);
    sb.append("    return new ").append(returnType).append("(");
    sb.append(params.stream().map(Param::name).collect(Collectors.joining(", ")));
    sb.append(");\n  }\n\n");
  }

  /// Emits a visitor method for one production of a [NormalPattern] with multiple
  /// productions.  The method body calls the production-specific record constructor,
  /// returning the sealed interface type.
  private static void emitNormalMultiMethod(Map<NonTerminal, String> types, StringBuilder sb, NonTerminal nt, Production prod) {
    var returnType = capitalize(nt.name());
    var recName = recordNameForProduction(prod);
    var params = params(types, prod);
    emitProductionMethodDeclaration(sb, prod, returnType, decapitalize(recName), params);
    sb.append("    return new ").append(recName).append("(");
    sb.append(params.stream().map(Param::name).collect(Collectors.joining(", ")));
    sb.append(");\n  }\n\n");
  }

  /// Emits one of the two visitor methods for an [OptionalPattern].
  ///
  /// @param isEmptyProduction `true` for the ε branch -> `Optional.empty()`;
  ///                          `false` for the value branch -> `Optional.of(v)`
  private static void emitOptionalMethod(Map<NonTerminal, String> types, StringBuilder sb, NonTerminal nt, Production prod, boolean isEmptyProduction) {
    var returnType = types.get(nt);
    var params = params(types, prod);
    if (isEmptyProduction) {
      emitProductionMethodDeclaration(sb, prod, returnType, decapitalize(nt.name()) + "Empty", params);
      sb.append("    return Optional.empty();\n  }\n\n");
    } else {
      emitProductionMethodDeclaration(sb, prod, returnType, decapitalize(nt.name()) + "Of", params);
      sb.append("    return Optional.of(").append(params.getFirst().name).append(");\n  }\n\n");
    }
  }

  /// Emits one of the two visitor methods for a [ListPattern].
  ///
  /// @param isSingleProduction `true` for the base production -> creates a new one-element `ArrayList`;
  ///                           `false` for the recursive production -> appends the new element to the
  ///                           existing list and returns it
  private static void emitListMethod(Map<NonTerminal, String> types, StringBuilder sb, NonTerminal nonTerminal,
                                     Production production, boolean isSingleProduction) {
    var returnType = types.get(nonTerminal);
    var params = params(types, production);
    if (isSingleProduction) {
      var elementType = returnType.substring(returnType.indexOf('<') + 1, returnType.length() - 1);
      emitProductionMethodDeclaration(sb, production, returnType, decapitalize(nonTerminal.name()) + "Single", params);
      sb.append("    var list = new ArrayList<").append(elementType).append(">();\n");
      sb.append("    list.add(").append(params.getFirst().name).append(");\n");
      sb.append("    return list;\n  }\n\n");
    } else {
      emitProductionMethodDeclaration(sb, production, returnType, decapitalize(nonTerminal.name()) + "Cons", params);
      sb.append("    ").append(params.get(0).name).append(".add(").append(params.get(1).name).append(");\n");
      sb.append("    return ").append(params.get(0).name).append(";\n  }\n\n");
    }
  }

  // -- Naming Helpers

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

  /// Returns the CamelCase name segment contributed by a terminal:
  /// the capitalized identifier name for identifier terminals, the entry from
  /// [#SYMBOL_NAMES] for punctuation, or `"Unknown"` as a fallback.
  private static String terminalSegment(Terminal t) {
    if (isJavaIdentifier(t.name())) {
      return capitalize(t.name());
    }
    var name = SYMBOL_NAMES.get(t.name());
    return name == null ? "Unknown" : name;
  }

  /// Derives a unique CamelCase record class name from a production by
  /// concatenating the name segment of every symbol in the body, then
  /// appending the capitalized head name.
  ///
  /// Example: ` Exp : Exp '+' Term` yields `ExpPlusTermExp``
  private static String recordNameForProduction(Production production) {
    var sb = new StringBuilder();
    for (var symbol : production.body()) {
      switch (symbol) {
        case Terminal t -> sb.append(terminalSegment(t));
        case NonTerminal nt -> sb.append(capitalize(nt.name()));
      }
    }
    sb.append(capitalize(production.head().name()));
    return sb.toString();
  }

  private static String capitalize(String s) {
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static String decapitalize(String s) {
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }
}