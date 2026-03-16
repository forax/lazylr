package com.github.forax.lazylr;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/// Verifies whether a grammar is LALR(1), using a precedence map
/// to resolve shift/reduce conflicts.
///
/// Usage:
/// ```java
/// var PLUS = new Terminal("+");
/// var NUM = new Terminal("num");
/// var E = new NonTerminal("E");
///
/// var pPlus = new Production(E, List.of(E, PLUS, E));
/// var pNum = new Production(E, List.of(NUM));
/// var grammar = new Grammar(E, List.of(pPlus, pNum));
///
/// // Define Left Associativity for PLUS and pPlus
/// var prec = new Precedence(1, Precedence.Associativity.LEFT);
/// var precedenceMap = Map.of(PLUS, prec, pPlus, prec);
///
/// LALRVerifier.verify(grammar, precedenceMap, error -> {
///   System.err.println(error);
/// });
/// ```
///
/// This class is thread-safe and can be safely shared between multiple threads.
public final class LALRVerifier {
  // Design note:
  // LALRVerifier and Parser both build LR automata from the same grammar, and share
  // concepts like FIRST sets and LR(1) closure. However, they intentionally do not
  // share code because they have different goals and different performance requirements.
  //
  // Parser has to be fast: it is designed to be lazy (states are computed on
  // demand), memory-efficient, and optimized.
  //
  // LALRVerifier is a development tool, called once to validate a grammar during
  // development, not during normal execution. Its implementation prioritizes
  // clarity and proximity to the theoretical LALR(1) construction over performance.
  // Sharing implementation with Parser would make them both harder to evolve independently.

  private LALRVerifier() {
    throw new AssertionError();
  }

  /// Verifies that the grammar is LALR(1) (possibly with precedence-based
  /// conflict resolution).
  ///
  /// @param grammar       the grammar to verify.
  /// @param precedenceMap maps terminals and productions to their precedence;
  ///                      used to resolve shift/reduce conflicts.
  /// @param errorReporter called once per unresolved conflict with a human-readable
  ///                      description of the conflict.
  /// @return `true` if the grammar is LALR(1), `false` otherwise.
  /// @throws NullPointerException if `grammar`, `precedenceMap` or `errorReporter` is null.
  public static boolean verify(Grammar grammar, Map<? extends PrecedenceEntity, Precedence> precedenceMap,
                            Consumer<String> errorReporter) {
    return verify(grammar, precedenceMap, null, false, errorReporter);
  }

  /// Verifies that the grammar is LALR(1) (possibly with precedence-based
  /// conflict resolution), optionally printing the LALR state automaton.
  ///
  /// @param grammar       the grammar to verify.
  /// @param precedenceMap maps terminals and productions to their precedence;
  ///                      used to resolve shift/reduce conflicts.
  /// @param out           the print stream to write the automaton to, or `null`
  ///                      to disable printing.
  /// @param alwaysPrint   if `true`, the automaton is printed unconditionally;
  ///                      if `false`, it is printed only when conflicts are detected.
  ///                      Ignored if `out` is `null`.
  /// @param errorReporter called once per unresolved conflict with a human-readable
  ///                      description of the conflict.
  /// @return `true` if the grammar is LALR(1), `false` if conflicts remain after
  ///         precedence-based resolution.
  /// @throws NullPointerException if `grammar`, `precedenceMap` or `errorReporter` is null.
  public static boolean verify(Grammar grammar, Map<? extends PrecedenceEntity, Precedence> precedenceMap,
                            /*nullable*/ PrintStream out, boolean alwaysPrint, Consumer<String> errorReporter) {
    Objects.requireNonNull(grammar);
    Objects.requireNonNull(precedenceMap);
    Objects.requireNonNull(errorReporter);
    var augmentedStart = buildAugmentedProduction(grammar);
    var firstSets = computeFirstSets(grammar);
    var lr1Automaton = buildLR1Automaton(grammar, augmentedStart, firstSets);
    var lalrAutomaton = mergeLR1States(lr1Automaton);
    var fullPrecedenceMap = Precedence.complete(grammar, precedenceMap);
    var actionTable =
        buildActionTable(lalrAutomaton.states, lalrAutomaton.gotoTable, fullPrecedenceMap, augmentedStart);
    var conflicts = reportConflicts(actionTable, errorReporter);
    if (out != null && (alwaysPrint || conflicts)) {
      printAutomaton(lalrAutomaton, augmentedStart, actionTable, out);
    }
    return !conflicts;
  }


  // -----------------------------------------------------------------------
  // Internal representation
  // -----------------------------------------------------------------------

  /// An LR(1) item: a production with a "dot" position and a lookahead terminal.
  private record Item(Production production, int dot, Terminal lookahead) {

    public boolean isComplete() {
      return dot == production.body().size();
    }

    /// The symbol after the dot, or null if the item is complete.
    public Symbol nextSymbol() {
      if (isComplete()) {
        return null;
      }
      return production.body().get(dot);
    }

    /// Advance the dot past the next symbol, keeping the same lookahead.
    public Item advance() {
      return new Item(production, dot + 1, lookahead);
    }

    @Override
    public String toString() {
      var body = production.body();
      var builder = new StringBuilder(production.head().name()).append(" :");
      for (var i = 0; i < body.size(); i++) {
        if (i == dot) {
          builder.append(" •");
        }
        builder.append(" ").append(body.get(i).name());
      }
      if (dot == body.size()) {
        builder.append(" •");
      }
      builder.append("  [").append(lookahead.name()).append("]");
      return builder.toString();
    }
  }

  private record Automaton(List<Set<Item>> states, List<Map<Symbol, Integer>> gotoTable) {}


  // -----------------------------------------------------------------------
  // Step 1: Augmented grammar
  // -----------------------------------------------------------------------

  private static Production buildAugmentedProduction(Grammar grammar) {
    var augmentedStartSymbol = new NonTerminal(grammar.startSymbol().name() + "'");
    return new Production(augmentedStartSymbol, List.of(grammar.startSymbol()));
  }

  // -----------------------------------------------------------------------
  // Step 2: FIRST sets
  // -----------------------------------------------------------------------

  private static Map<NonTerminal, Set<Terminal>> computeFirstSets(Grammar grammar) {
    var firstSets = new HashMap<NonTerminal, Set<Terminal>>();
    for (var nt : grammar.nonTerminals()) {
      firstSets.put(nt, new HashSet<>());
    }

    var changed = true;
    while (changed) {
      changed = false;
      for (var prod : grammar.productions()) {
        var set = firstSets.get(prod.head());
        var added = firstOfSequence(prod.body(), firstSets);
        if (set.addAll(added)) {
          changed = true;
        }
      }
    }
    return firstSets;
  }

  /// Compute FIRST(sequence). Returns terminals (including EPSILON if nullable).
  private static Set<Terminal> firstOfSequence(List<Symbol> symbols, Map<NonTerminal, Set<Terminal>> firstSets) {
    var result = new HashSet<Terminal>();
    if (symbols.isEmpty()) {
      result.add(Terminal.EPSILON);
      return result;
    }
    var allNullable = true;
    for (var sym : symbols) {
      var first = firstOfSymbol(sym, firstSets);
      result.addAll(first);
      result.remove(Terminal.EPSILON);
      if (!first.contains(Terminal.EPSILON)) {
        allNullable = false;
        break;
      }
    }
    if (allNullable) {
      result.add(Terminal.EPSILON);
    }
    return result;
  }

  private static Set<Terminal> firstOfSymbol(Symbol symbol, Map<NonTerminal, Set<Terminal>> firstSets) {
    return switch (symbol) {
      case Terminal t -> Set.of(t);
      case NonTerminal nt -> firstSets.getOrDefault(nt, Set.of());
    };
  }

  // -----------------------------------------------------------------------
  // Step 3: LR(1) closure
  // -----------------------------------------------------------------------

  /// Compute FIRST(β a) where β is a list of symbols and a is a terminal.
  /// If β is nullable, a is included; ε is never included in the result.
  private static Set<Terminal> firstOfSequenceWithTerminal(List<Symbol> symbols, Terminal terminal,
                                                           Map<NonTerminal, Set<Terminal>> firstSets) {
    var result = firstOfSequence(symbols, firstSets);
    if (result.remove(Terminal.EPSILON)) {
      result.add(terminal);
    }
    return result;
  }

  /// Compute the LR(1) closure of an item set.
  /// For each item [A → α • B β, a], adds [B → • γ, b] for every b in FIRST(βa).
  private static Set<Item> closure(Set<Item> items, Grammar grammar,
                                   Map<NonTerminal, Set<Terminal>> firstSets) {
    var result = new LinkedHashSet<>(items);
    var worklist = new ArrayDeque<>(items);
    while (!worklist.isEmpty()) {
      var item = worklist.poll();
      if (!(item.nextSymbol() instanceof NonTerminal nt)) {
        continue;
      }
      // β is everything after the dot's symbol; a is the item's lookahead
      var body = item.production().body();
      var rest = body.subList(item.dot() + 1, body.size());
      var lookaheads = firstOfSequenceWithTerminal(rest, item.lookahead(), firstSets);
      for (var production : grammar.productionsFor(nt)) {
        for (var lookahead : lookaheads) {
          var newItem = new Item(production, 0, lookahead);
          if (result.add(newItem)) {
            worklist.add(newItem);
          }
        }
      }
    }
    return result;
  }

  // -----------------------------------------------------------------------
  // Step 4: LR(1) automaton
  // -----------------------------------------------------------------------

  private static Automaton buildLR1Automaton(Grammar grammar, Production augmentedStart,
                                             Map<NonTerminal, Set<Terminal>> firstSets) {
    var states = new ArrayList<Set<Item>>();
    var gotoTable = new ArrayList<Map<Symbol, Integer>>();
    var stateIndex = new HashMap<Set<Item>, Integer>();

    // Seed: [__start__ → • S, EOF]
    var initial = closure(Set.of(new Item(augmentedStart, 0, Terminal.EOF)), grammar, firstSets);
    states.add(initial);
    gotoTable.add(new HashMap<>());
    stateIndex.put(initial, 0);

    for (var i = 0; i < states.size(); i++) {
      var state = states.get(i);

      // Group advanced items by the symbol after the dot
      var kernelsBySymbol = new LinkedHashMap<Symbol, Set<Item>>();
      for (var item : state) {
        var sym = item.nextSymbol();
        if (sym != null) {
          kernelsBySymbol.computeIfAbsent(sym, _ -> new LinkedHashSet<>()).add(item.advance());
        }
      }

      for (var entry : kernelsBySymbol.entrySet()) {
        var sym = entry.getKey();
        var next = closure(entry.getValue(), grammar, firstSets);
        var target = stateIndex.computeIfAbsent(next, _ -> {
          var idx = states.size();
          states.add(next);
          gotoTable.add(new HashMap<>());
          return idx;
        });
        gotoTable.get(i).put(sym, target);
      }
    }
    return new Automaton(states, gotoTable);
  }

  // -----------------------------------------------------------------------
  // Step 5: Merge LR(1) states with identical LR(0) cores → LALR(1)
  // -----------------------------------------------------------------------

  /// An LR(0) core identifies a state by its items stripped of lookaheads.
  private record Core(Production production, int dot) {}

  private static Automaton mergeLR1States(Automaton lr1) {
    var states = lr1.states();

    // Group state indices by their LR(0) core set
    var coresMap = new LinkedHashMap<Set<Core>, List<Integer>>();
    for (var i = 0; i < states.size(); i++) {
      var core = states.get(i).stream()
          .map(item -> new Core(item.production(), item.dot()))
          .collect(toCollection(LinkedHashSet::new));
      coresMap.computeIfAbsent(core, _ -> new ArrayList<>()).add(i);
    }

    // Build merged states and an old-index → new-index remapping table
    var newStates = new ArrayList<Set<Item>>();
    var remap = new int[states.size()];
    for (var stateIndices : coresMap.values()) {
      var newIndex = newStates.size();
      // Union of the lookaheads of all states sharing the same core
      var merged = new LinkedHashSet<Item>();
      for (var index : stateIndices) {
        merged.addAll(states.get(index));
        remap[index] = newIndex;
      }
      newStates.add(merged);
    }

    // Rewrite the goto table using remapped indices.
    // All states in a stateIndices share the same core transitions, so using
    // the first representative of each stateIndices is enough.
    var newGoto = new ArrayList<Map<Symbol, Integer>>();
    for (var i = 0; i < newStates.size(); i++) {
      newGoto.add(new HashMap<>());
    }
    var oldGoto = lr1.gotoTable();
    for (var stateIndices : coresMap.values()) {
      var representative = stateIndices.getFirst();
      for (var entry : oldGoto.get(representative).entrySet()) {
        newGoto.get(remap[representative]).put(entry.getKey(), remap[entry.getValue()]);
      }
    }
    return new Automaton(newStates, newGoto);
  }

  // -----------------------------------------------------------------------
  // Step 6: Build LALR(1) action table (detect / resolve conflicts)
  // -----------------------------------------------------------------------

  // Action kinds
  private sealed interface Action permits Shift, Reduce, Accept {}
  private record Shift(int target) implements Action {}
  private record Reduce(Production production) implements Action {}
  private record Accept() implements Action {}

  private record Result(List<Action> actions, /*nullable*/ Action winner) {}

  private static List<Map<Terminal, Result>> buildActionTable(List<Set<Item>> states,
                                                              List<Map<Symbol, Integer>> gotoTable,
                                                              Map<PrecedenceEntity, Precedence> precedenceMap,
                                                              Production augmentedStart) {
    var actionTable = new ArrayList<Map<Terminal, Result>>();

    for (var i = 0; i < states.size(); i++) {
      var state = states.get(i);
      var transitions = gotoTable.get(i);

      var conflictMap = new HashMap<Terminal, List<Action>>();
      for (var item : state) {
        if (item.isComplete()) {
          if (item.production().equals(augmentedStart)) {
            // Accept on EOF, the augmented start item carries EOF as its lookahead
            conflictMap.computeIfAbsent(Terminal.EOF, _ -> new ArrayList<>())
                .add(new Accept());
          } else {
            // Reduce on this item's lookahead
            conflictMap.computeIfAbsent(item.lookahead, _ -> new ArrayList<>())
                .add(new Reduce(item.production()));
          }
        } else {
          // Shift on terminal
          var sym = item.nextSymbol();
          if (sym instanceof Terminal t) {
            var target = transitions.get(t);
            conflictMap.computeIfAbsent(t, _ -> new ArrayList<>())
                .add(new Shift(target));
          }
        }
      }

      var resultMap = conflictMap.entrySet().stream()
          .collect(toMap(
              Map.Entry::getKey,
              e -> resolveConflicts(e.getKey(), e.getValue(), precedenceMap)));
      actionTable.add(resultMap);
    }
    return actionTable;
  }

  private static Result resolveConflicts(Terminal lookahead, List<Action> actions,
                                         Map<PrecedenceEntity, Precedence> precedenceMap) {
    var theShift = (Shift) null;
    var reduces = new ArrayList<Reduce>();
    for(var action : actions) {
      switch (action) {
        case Shift shift -> theShift = shift;
        case Reduce reduce -> reduces.add(reduce);
        case Accept accept -> {
          return new Result(actions, accept);
        }
      }
    }

    if (theShift == null) {
      if (reduces.size() == 1) {
        return new Result(actions, reduces.getFirst());
      }
      return new Result(actions, null);  // reduce/reduce conflicts
    }

    if (reduces.isEmpty()) {
      return new Result(actions, theShift);
    }
    var termPrec = precedenceMap.get(lookahead);
    if (reduces.size() == 1) {
      var reduce = reduces.getFirst();
      var prodPrec = precedenceMap.get(reduce.production());
      var action = resolveShiftReduceConflict(theShift, reduce, termPrec, prodPrec);
      return new Result(actions, action);
    }
    for(var reduce : reduces) {
      var prodPrec = precedenceMap.get(reduce.production());
      var action = resolveShiftReduceConflict(theShift, reduce, termPrec, prodPrec);
      if (!(action instanceof Shift)) {  // shift/reduce conflict
        return new Result(actions, null);
      }
    }
    return new Result(actions, theShift);
  }

  private static boolean reportConflicts(List<Map<Terminal, Result>> actionTable, Consumer<String> errorReporter) {
    boolean conflicts = false;
    for (var i = 0; i < actionTable.size(); i++) {
      var actionMap = actionTable.get(i);
      for (var entry : actionMap.entrySet()) {
        var lookahead = entry.getKey();
        var result = entry.getValue();
        if (result.winner() == null) {  // conflict
          conflicts = true;
          var hasShift = result.actions.stream().anyMatch(a -> a instanceof Shift);
          var conflictName = hasShift ? "shift/reduce" : "reduce/reduce";
          errorReporter.accept(
              "Unresolved " + conflictName + " conflict in state " + i +
                  " on terminal '" + lookahead.name() + "'" +
                  " between " + result.actions.stream()
                  .map(action -> switch (action) {
                    case Shift _ -> "shift";
                    case Reduce(Production production) -> "reduce " + production.name();
                    case Accept _ -> throw new AssertionError();
                  })
                  .collect(joining(", ")));
        }
      }
    }
    return conflicts;
  }

  private static Action resolveShiftReduceConflict(Action shiftAction, Action reduceAction,
                                                   Precedence termPrec, Precedence prodPrec) {
    if (termPrec == null || prodPrec == null) {
      return null;  // shift/reduce conflict
    }
    // Resolve: higher level wins; on tie use associativity
    if (termPrec.level() > prodPrec.level()) {
      return shiftAction;
    }
    if (prodPrec.level() > termPrec.level()) {
      return reduceAction;
    }
    // Same level: use associativity
    if (termPrec.associativity() == Precedence.Associativity.LEFT) {
      return reduceAction;
    }
    return shiftAction;
  }



  private static void printAutomaton(Automaton automaton, Production augmentedStart,
                                     List<Map<Terminal, Result>> actionTable, PrintStream out) {
    var states = automaton.states();
    var gotoTable = automaton.gotoTable();

    for (var i = 0; i < states.size(); i++) {
      var state = states.get(i);
      var transitions = gotoTable.get(i);
      var stateActions = actionTable.get(i);

      // -- State header
      out.println("── State " + i + " " + "─".repeat(Math.max(0, 40 - ("State " + i).length())));

      // -- LR items
      // Compute column width for "Head :" prefix to align all dots
      var prefixWidth = state.stream()
          .mapToInt(item -> item.production().head().name().length() + 3)
          .max().orElse(0);

      // Group items by production+dot (core), collecting lookaheads together
      record CoreKey(Production production, int dot) {}
      var coreKeys = state.stream()
          .map(item -> new CoreKey(item.production(), item.dot()))
          .collect(toSet());

      for (var coreKey : coreKeys) {
        var prod = coreKey.production();
        var dot = coreKey.dot();
        var body = prod.body();

        // Build "Head :" left-padded to prefixWidth
        var head = prod.head().name() + " :";
        var builder = new StringBuilder("   ");
        builder.append(head);
        builder.repeat(" ", prefixWidth - head.length());

        // Emit body symbols, inserting the dot at the right position
        for (var j = 0; j < body.size(); j++) {
          if (j == dot) {
            builder.append(" •");
          }
          builder.append(" ").append(body.get(j).name());
        }
        if (dot == body.size()) {
          builder.append(" •");
        }

        out.println(builder);
      }

      out.println("  " + "·".repeat(38));

      // -- Goto / shift transitions
      // Sort: terminals first, then non-terminals, each group alphabetically
      transitions.entrySet().stream()
          .sorted(Map.Entry.comparingByKey(
              Comparator.<Symbol>comparingInt(s -> s instanceof Terminal ? 0 : 1)
                  .thenComparing(Symbol::name)))
          .forEach(e -> {
            var symbol = e.getKey();
            var target = e.getValue();
            switch (symbol) {
              case Terminal terminal -> {
                var result = stateActions.get(terminal);
                var suffix = switch (result.winner) {
                  case null -> " 🔥";      // unresolved conflict
                  case Shift _ -> "";
                  case Reduce _ -> " ❌";  // shift lost to reduce via precedence
                  case Accept _ -> throw new AssertionError();
                };
                out.printf("   goto( %-20s ) → %d%s\n", terminal.name(), target, suffix);
              }
              case NonTerminal nonTerminal ->
                out.printf("   goto( %-20s ) → %d\n", nonTerminal.name(), target);
            }
          });

      // -- Reduce / accept transitions
      // Group complete items by production, collecting their lookaheads
      record ReduceKey(Production production, boolean isAccept) {}
      var reduces = state.stream()
          .filter(Item::isComplete)
          .collect(groupingBy(
              item -> new ReduceKey(item.production(), item.production().equals(augmentedStart)),
              mapping(Item::lookahead, toList())));

      reduces.entrySet().stream()
          .sorted(Map.Entry.comparingByKey(Comparator.comparing(k -> k.production().name())))
          .forEach(entry -> {
            var key = entry.getKey();
            var terminals = entry.getValue();
            if (key.isAccept()) {
              var lookaheads = terminals.stream()
                  .map(Terminal::name)
                  .sorted()
                  .collect(joining(", "));
              out.printf("   accept()                     on [%s]\n", lookaheads);
            } else {
              var prod = key.production();
              var annotatedLookaheads = terminals.stream()
                  .sorted(Comparator.comparing(Terminal::name))
                  .map(lookahead -> {
                    var result = stateActions.get(lookahead);
                    var suffix = switch (result.winner()) {
                      case null -> " 🔥";      // unresolved conflict
                      case Reduce(Production production) when production.equals(prod) -> "";
                      case Reduce _ -> " ❌";  // reduce lost to shift via precedence
                      case Shift _ -> "";
                      case Accept _ -> throw new AssertionError();
                    };
                    return lookahead.name() + suffix;
                  })
                  .collect(joining(", "));
              out.printf("   reduce( %-18s ) on [%s]\n", prod.name(), annotatedLookaheads);
            }
          });

      out.println();
    }
  }
}