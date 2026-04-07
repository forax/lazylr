package com.github.forax.lazylr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// Generates a Java [Visitor] implementation from a [Grammar].
class JavaCodeVisitorGenerator {

  sealed interface NtInfo permits NormalNtInfo, PassThroughNtInfo, OptionalNtInfo, ListNtInfo {}
  enum NormalNtInfo implements NtInfo { INSTANCE }
  record PassThroughNtInfo(Symbol element) implements NtInfo {}
  record OptionalNtInfo(Production empty, Production present, Symbol element) implements NtInfo {}
  record ListNtInfo(Production recursive, Production base, Symbol element) implements NtInfo {}

  /// Generates a Java [Visitor] implementation from a [Grammar].
  public static String generateVisitor(Grammar grammar) {
    var nonTerminals = grammar.nonTerminals();

    // ---------------------------------------------------------------------------
    // 1. Classify non-terminals
    // ---------------------------------------------------------------------------

    var ntInfoMap = new LinkedHashMap<NonTerminal, NtInfo>();

    for (var nonTerminal : nonTerminals) {
      var prods = grammar.productionsFor(nonTerminal);
      var info = (NtInfo) null;

      // Optional: exactly two productions, one ε and one single-symbol
      if (prods.size() == 2) {
        var p0 = prods.get(0);
        var p1 = prods.get(1);
        if (p0.body().isEmpty() && p1.body().size() == 1) {
          info = new OptionalNtInfo(p0, p1, p1.body().getFirst());
        }
        if (p1.body().isEmpty() && p0.body().size() == 1) {
          info = new OptionalNtInfo(p1, p0, p0.body().getFirst());
        }
      }

      // List: exactly two productions — one left-recursive append and one single base element
      if (info == null && prods.size() == 2) {
        var p0 = prods.get(0);
        var p1 = prods.get(1);

        if (p0.body().size() == 2 && p0.body().getFirst().equals(nonTerminal)) {
          var element = p0.body().get(1);
          if (p1.body().size() == 1 && p1.body().getFirst().equals(element)) {
            info = new ListNtInfo(p0, p1, element);
          }
        }
        if (p1.body().size() == 2 && p1.body().getFirst().equals(nonTerminal)) {
          var element = p1.body().get(1);
          if (p0.body().size() == 1 && p0.body().getFirst().equals(element)) {
            info = new ListNtInfo(p1, p0, element);
          }
        }
      }

      // Pass-through: one production with one symbol.
      if (info == null && prods.size() == 1) {
        var p = prods.getFirst();
        if (p.body().size() == 1) {
          info = new PassThroughNtInfo(p.body().getFirst());
        }
      }

      ntInfoMap.put(nonTerminal, info == null ? NormalNtInfo.INSTANCE : info);
    }

    // ---------------------------------------------------------------------------
    // 2. Detect precedence ladders via Union-Find
    //
    //    A NORMAL NT that has a single-symbol production pointing to another
    //    NORMAL NT is a ladder step: both NTs share the same sealed interface.
    //    We union them and keep the root's name as the interface name.
    // ---------------------------------------------------------------------------

    // Union-Find over NonTerminals
    var parent = new LinkedHashMap<NonTerminal, NonTerminal>();
    for (var nt : nonTerminals) parent.put(nt, nt);

    var unionFind = new Object() {
      NonTerminal findRef(NonTerminal nt) {
        var ref = parent.get(nt);
        if (!ref.equals(nt)) {
          ref = findRef(ref);
          parent.put(nt, ref);
        }
        return ref;
      }
    };

    for (var nt : nonTerminals) {
      if (!(ntInfoMap.get(nt) instanceof NormalNtInfo)) {
        continue;
      }
      for (var prod : grammar.productionsFor(nt)) {
        var body = prod.body();
        if (body.size() == 1 && body.getFirst() instanceof NonTerminal target
            && ntInfoMap.get(target) instanceof NormalNtInfo) {
          // union: keep the one that appears first in grammar order as root
          var rootNt  = unionFind.findRef(nt);
          var rootTgt = unionFind.findRef(target);
          if (!rootNt.equals(rootTgt)) {
            // prefer the root that appears earlier in nonTerminals
            var list = new ArrayList<>(nonTerminals);
            if (list.indexOf(rootNt) <= list.indexOf(rootTgt)) {
              parent.put(rootTgt, rootNt);
            } else {
              parent.put(rootNt, rootTgt);
            }
          }
        }
      }
    }

    // ---------------------------------------------------------------------------
    // 3. Assign types
    //
    //    NORMAL NTs in the same union group share the sealed interface of the root.
    //    PASS_THROUGH / OPTIONAL / LIST get structural types, resolved via fixpoint.
    // ---------------------------------------------------------------------------

    var ntTypeMap = new LinkedHashMap<NonTerminal, String>();

    // Initial assignment
    for (var nonTerminal : nonTerminals) {
      var info = ntInfoMap.get(nonTerminal);
      var prods = grammar.productionsFor(nonTerminal);
      switch (info) {
        case NormalNtInfo _ -> {
          var root = unionFind.findRef(nonTerminal);
          ntTypeMap.put(nonTerminal, capitalize(root.name()));
        }
        case PassThroughNtInfo(Symbol element) -> ntTypeMap.put(nonTerminal, capitalize(element.name()));
        case OptionalNtInfo(Production _, Production _, Symbol element) -> {
          ntTypeMap.put(nonTerminal, "Optional<" + capitalize(element.name()) + ">");
        }
        case ListNtInfo(Production _, Production _, Symbol element) -> {
          ntTypeMap.put(nonTerminal, "List<" + capitalize(element.name()) + ">");
        }
      }
    }

    // Fixpoint: propagate resolved types into PASS_THROUGH, OPTIONAL, LIST
    var changed = true;
    while (changed) {
      changed = false;
      for (var nonTerminal : nonTerminals) {
        var info = ntInfoMap.get(nonTerminal);
        var newType = switch (info) {
          case NormalNtInfo _ -> ntTypeMap.get(nonTerminal);
          case PassThroughNtInfo(NonTerminal innerNt) -> ntTypeMap.get(innerNt);
          case PassThroughNtInfo(Symbol element) -> capitalize(element.name());
          case OptionalNtInfo(Production _, Production _, NonTerminal innerNt) ->
              "Optional<" + ntTypeMap.get(innerNt) + '>';
          case OptionalNtInfo(Production _, Production _, Symbol element) ->
              "Optional<" + capitalize(element.name()) + '>';
          case ListNtInfo(Production _, Production _, NonTerminal innerNt) ->
              "List<" + ntTypeMap.get(innerNt) + '>';
          case ListNtInfo(Production _, Production _, Symbol element) ->
              "List<" + capitalize(element.name()) + '>';
        };
        if (!newType.equals(ntTypeMap.get(nonTerminal))) {
          ntTypeMap.put(nonTerminal, newType);
          changed = true;
        }
      }
    }

    // ---------------------------------------------------------------------------
    // 4. Determine which NTs own a sealed interface
    //    Only the root of each NORMAL union group emits a sealed interface.
    // ---------------------------------------------------------------------------

    var sealedRoots = new LinkedHashSet<NonTerminal>();
    for (var nt : nonTerminals) {
      if (ntInfoMap.get(nt) instanceof NormalNtInfo && unionFind.findRef(nt).equals(nt)) {
        sealedRoots.add(nt);
      }
    }

    // Collect all productions (from all NTs in a ladder group) under their root
    var rootProductions = new LinkedHashMap<NonTerminal, List<Production>>();
    for (var root : sealedRoots) {
      var allProds = new ArrayList<Production>();
      for (var nt : nonTerminals) {
        if (ntInfoMap.get(nt) instanceof NormalNtInfo && unionFind.findRef(nt).equals(root)) {
          grammar.productionsFor(nt).stream()
              .filter(p -> !p.body().isEmpty())              // skip epsilon
              .filter(p -> !(p.body().size() == 1            // skip ladder passthroughs
                  && p.body().getFirst() instanceof NonTerminal target
                  && ntInfoMap.get(target) instanceof NormalNtInfo
                  && unionFind.findRef(target).equals(root)))
              .forEach(allProds::add);
        }
      }
      rootProductions.put(root, allProds);
    }

    var sb = new StringBuilder();

    // ---------------------------------------------------------------------------
    // 5. Imports
    // ---------------------------------------------------------------------------
    sb.append("import com.github.forax.lazylr.*;\n");
    sb.append("import java.util.*;\n");
    sb.append("\n");

    // ---------------------------------------------------------------------------
    // 6. Sealed interfaces + records (one interface per root, records for all prods)
    // ---------------------------------------------------------------------------
    for (var root : sealedRoots) {
      var interfaceName = capitalize(root.name());
      var productions = rootProductions.get(root);
      // only non-transparent productions get a record
      var recordProductions = productions.stream().filter(p -> !isTransparentWrapper(p, ntTypeMap)).toList();
      sb.append("sealed interface ").append(interfaceName).append(" permits ");
      sb.append(recordProductions.stream().map(JavaCodeVisitorGenerator::recordNameFor).collect(Collectors.joining(", ")));
      sb.append(" {}\n");
      for (var recordProduction : recordProductions) {
        var components = buildComponentList(recordProduction, ntTypeMap);
        sb.append("record ").append(recordNameFor(recordProduction)).append("(")
            .append(components).append(") implements ").append(interfaceName).append(" {}\n");
      }
      sb.append("\n");
    }

    // ---------------------------------------------------------------------------
    // 7. Visitor class
    // ---------------------------------------------------------------------------
    var visitorType = ntTypeMap.getOrDefault(grammar.startSymbol(), "Object");
    sb.append("class GeneratedVisitor implements Visitor<").append(visitorType).append("> {\n\n");

    // Terminal methods
    var emittedTerminals = new LinkedHashSet<String>();
    for (var production : grammar.productions()) {
      for (var symbol : production.body()) {
        if (symbol instanceof Terminal t && !isQuotedPunctuation(t.name())
            && emittedTerminals.add(t.name())) {
          sb.append("  public String ").append(javaId(t.name())).append("(Terminal terminal) {\n")
              .append("    return terminal.value();\n")
              .append("  }\n\n");
        }
      }
    }

    // Production methods for NORMAL roots
    for (var root : sealedRoots) {
      for (var production : rootProductions.get(root)) {
        var rootType = ntTypeMap.getOrDefault(root, "Object");
        sb.append("  @ProductionName(\"").append(production.name()).append("\")\n");
        sb.append("  public ").append(rootType).append(" ").append(methodNameFor(production)).append("(");
        sb.append(buildComponentList(production, ntTypeMap));
        sb.append(") {\n");
        if (isTransparentWrapper(production, ntTypeMap)) {
          // just forward the single meaningful parameter
          sb.append("    return ").append(componentNames(production, ntTypeMap).getFirst()).append(";\n");
        } else {
          var recordName = recordNameFor(production);
          var parameterNames = componentNames(production, ntTypeMap);
          sb.append("    return new ").append(recordName).append("(")
              .append(String.join(", ", parameterNames)).append(");\n");
        }
        sb.append("  }\n\n");
      }
    }

    // Production methods for OPTIONAL and LIST non-terminals
    for (var nonTerminal : nonTerminals) {
      var info = ntInfoMap.get(nonTerminal);
      if (info instanceof PassThroughNtInfo|| info instanceof NormalNtInfo) {
        continue;
      }
      for (var production : grammar.productionsFor(nonTerminal)) {
        var returnType = ntTypeMap.getOrDefault(nonTerminal, "Object");
        sb.append("  @ProductionName(\"").append(production.name()).append("\")\n");
        sb.append("  public ").append(returnType).append(" ").append(methodNameFor(production)).append("(");
        sb.append(buildComponentList(production, ntTypeMap));
        sb.append(") {\n");

        if (info instanceof OptionalNtInfo) {
          if (production.body().isEmpty()) {
            sb.append("    return Optional.empty();\n");
          } else {
            sb.append("    return Optional.of(").append(componentNames(production, ntTypeMap).getFirst()).append(");\n");
          }
        } else {
          // LIST
          if (production.body().size() == 2 && production.body().getFirst().equals(nonTerminal)) {
            var names = componentNames(production, ntTypeMap);
            sb.append("    ").append(names.getFirst()).append(".add(").append(names.get(1)).append(");\n");
            sb.append("    return ").append(names.getFirst()).append(";\n");
          } else {
            var listType = ntTypeMap.getOrDefault(nonTerminal, "List<Object>");
            var elementType = listType.substring(listType.indexOf('<') + 1, listType.lastIndexOf('>'));
            var elementName = componentNames(production, ntTypeMap).getFirst();
            var listName = javaId(elementName) + "List";
            sb.append("    var ").append(listName).append(" = new ArrayList<").append(elementType).append(">();\n");
            sb.append("    ").append(listName).append(".add(").append(elementName).append(");\n");
            sb.append("    return ").append(listName).append(";\n");
          }
        }
        sb.append("  }\n\n");
      }
    }

    sb.append("}\n");
    return sb.toString();
  }

// ---------------------------------------------------------------------------
// Symbol → readable name map
// ---------------------------------------------------------------------------

  private static final Map<String, String> SYMBOL_NAMES = Map.ofEntries(
      Map.entry("+",  "Plus"),
      Map.entry("-",  "Minus"),
      Map.entry("*",  "Mul"),
      Map.entry("/",  "Div"),
      Map.entry("%",  "Mod"),
      Map.entry("^",  "Pow"),
      Map.entry("&",  "And"),
      Map.entry("|",  "Or"),
      Map.entry("~",  "BitNot"),
      Map.entry("!",  "Not"),
      Map.entry("<",  "Lt"),
      Map.entry(">",  "Gt"),
      Map.entry("<=", "Le"),
      Map.entry(">=", "Ge"),
      Map.entry("==", "Eq"),
      Map.entry("!=", "Ne"),
      Map.entry("=",  "Assign"),
      Map.entry("->", "Arrow"),
      Map.entry("=>", "FatArrow"),
      Map.entry("::", "ColonColon"),
      Map.entry(":",  "Colon"),
      Map.entry(";",  "Semi"),
      Map.entry(",",  "Comma"),
      Map.entry(".",  "Dot"),
      Map.entry("..", "DotDot"),
      Map.entry("(",  "LParen"),
      Map.entry(")",  "RParen"),
      Map.entry("{",  "LBrace"),
      Map.entry("}",  "RBrace"),
      Map.entry("[",  "LBracket"),
      Map.entry("]",  "RBracket")
  );

  private static String symbolReadableName(Symbol symbol) {
    if (symbol instanceof Terminal terminal && isQuotedPunctuation(terminal.name())) {
      var symbolName = SYMBOL_NAMES.get(terminal.name());
      if (symbolName != null) {
        return symbolName;
      }
      return javaId(terminal.name());
    }
    return capitalize(symbol.name());
  }

// ---------------------------------------------------------------------------
// Naming helpers
// ---------------------------------------------------------------------------

  private static String lowerFirst(String s) {
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }

  private static String capitalize(String name) {
    var clean = name.replaceAll("[?+*]$", "");
    return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
  }

  private static String javaId(String name) {
    return name.replaceAll("[^A-Za-z0-9_]", "_");
  }

  private static boolean isQuotedPunctuation(String terminalName) {
    return !Character.isLetter(terminalName.charAt(0));
  }

  /// Returns true if a production is a transparent wrapper:
  /// its meaningful symbols (non-quoted punctuation) reduce to exactly one parameter
  /// whose type equals the sealed interface type (i.e. just forwarding the value up).
  private static boolean isTransparentWrapper(Production p, Map<NonTerminal, String> ntTypeMap) {
    var meaningful = p.body().stream()
        .filter(s -> !(s instanceof Terminal t && isQuotedPunctuation(t.name())))
        .toList();
    if (meaningful.size() != 1) return false;
    var sym = meaningful.getFirst();
    var symType = sym instanceof NonTerminal nt
        ? ntTypeMap.getOrDefault(nt, "Object") : "String";
    var headType = ntTypeMap.getOrDefault(p.head(), "Object");
    return symType.equals(headType);
  }

  /// Record name: driven by the first symbol that is not a self-reference to the head's ladder group.
  /// e.g. Exp : Exp '+' Term  →  PlusExp
  ///      Factor : num         →  NumExp   (if Factor is in Exp's group)
  private static String recordNameFor(Production p) {
    for (var symbol : p.body()) {
      if (symbol.equals(p.head())) continue;
      return symbolReadableName(symbol) + capitalize(p.head().name());
    }
    return capitalize(p.head().name());
  }

  private static String methodNameFor(Production p) {
    if (p.body().isEmpty()) {
      return "empty" + capitalize(p.head().name());
    }
    // same logic as recordNameFor but camelCase: plusExp(...)
    var recName = recordNameFor(p);
    return Character.toLowerCase(recName.charAt(0)) + recName.substring(1);
  }

// ---------------------------------------------------------------------------
// Component / parameter helpers — unified names used for both records and methods
// ---------------------------------------------------------------------------

  /// Returns a list of "Type name" strings for the meaningful symbols of a production
  /// (quoted punctuation is excluded). Names are deduplicated with a counter suffix.
  private static List<String> buildComponentDecls(Production p, Map<NonTerminal, String> ntTypeMap) {
    var parts = new ArrayList<String>();
    var nameCounts = new LinkedHashMap<String, Integer>();
    for (var sym : p.body()) {
      switch (sym) {
        case Terminal t -> {
          if (isQuotedPunctuation(t.name())) continue;
          var base = javaId(t.name());
          var n = nameCounts.merge(base, 1, Integer::sum);
          var rawName = n == 1 ? base : base + n;
          parts.add("String " + lowerFirst(rawName));
        }
        case NonTerminal nt -> {
          var type = ntTypeMap.getOrDefault(nt, "Object");
          var base = nt.name();
          var n = nameCounts.merge(base, 1, Integer::sum);
          var rawName = n == 1 ? base : base + n;
          parts.add(type + " " + lowerFirst(rawName));
        }
      }
    }
    return parts;
  }

  /// Comma-separated "Type name" list — shared by record components and method parameters.
  private static String buildComponentList(Production p, Map<NonTerminal, String> ntTypeMap) {
    return String.join(", ", buildComponentDecls(p, ntTypeMap));
  }

  /// Just the parameter names (right-hand side of each decl), for use in `new Record(...)`.
  private static List<String> componentNames(Production p, Map<NonTerminal, String> ntTypeMap) {
    return buildComponentDecls(p, ntTypeMap).stream()
        .map(decl -> decl.substring(decl.lastIndexOf(' ') + 1))
        .toList();
  }
}
