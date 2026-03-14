package com.github.forax.lazylr;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

  private LALRVerifier() {
    throw new AssertionError();
  }

  /// Verifies that the grammar is LALR(1) (possibly with precedence-based
  /// conflict resolution).
  ///
  /// @param grammar       the grammar to verify.
  /// @param precedenceMap maps terminals and productions to their precedence;
  ///                      used to resolve shift/reduce conflicts.
  /// @param errorReporter report conflicts error messages.
  /// @throws NullPointerException if `grammar`, `precedenceMap` or `errorReporter` is null.
  public static void verify(Grammar grammar, Map<? extends PrecedenceEntity, Precedence> precedenceMap,
                            Consumer<String> errorReporter) {
    Objects.requireNonNull(grammar);
    Objects.requireNonNull(precedenceMap);
    Objects.requireNonNull(errorReporter);
    var fullPrecedenceMap = Parser.complete(grammar, precedenceMap);
    var augmentedStart = buildAugmentedProduction(grammar);
    var firstSets = computeFirstSets(grammar);
    var lr1Automaton = buildLR1Automaton(grammar, augmentedStart, firstSets);
    var lalrAutomaton = mergeLR1States(lr1Automaton);
    buildActionTable(lalrAutomaton.states, lalrAutomaton.gotoTable, fullPrecedenceMap, augmentedStart, errorReporter);
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
    var augmentedStartSymbol = new NonTerminal("__start__");
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
          .collect(Collectors.toCollection(LinkedHashSet::new));
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

  private static void buildActionTable(List<Set<Item>> states, List<Map<Symbol, Integer>> gotoTable,
                                       Map<PrecedenceEntity, Precedence> precedenceMap,
                                       Production augmentedStart,
                                       Consumer<String> errorReporter) {
    var actionTable = new ArrayList<Map<Terminal, Action>>();
    for (var i = 0; i < states.size(); i++) {
      actionTable.add(new HashMap<>());
    }

    for (var i = 0; i < states.size(); i++) {
      var state = states.get(i);
      var actions = actionTable.get(i);
      var transitions = gotoTable.get(i);

      for (var item : state) {
        if (item.isComplete()) {
          if (item.production().equals(augmentedStart)) {
            // Accept on EOF, the augmented start item carries EOF as its lookahead
            mergeAction(actions, precedenceMap, Terminal.EOF, new Accept(), i, errorReporter);
          } else {
            // Reduce on this item's lookahead
            mergeAction(actions, precedenceMap, item.lookahead, new Reduce(item.production()), i, errorReporter);
          }
        } else {
          // Shift on terminal
          var sym = item.nextSymbol();
          if (sym instanceof Terminal t) {
            var target = transitions.get(t);
            mergeAction(actions, precedenceMap, t, new Shift(target), i, errorReporter);
          }
        }
      }
    }
  }

  /// Merge a new action into the action table, resolving conflicts via precedence.
  private static void mergeAction(Map<Terminal, Action> actions, Map<PrecedenceEntity, Precedence> precedenceMap,
                                  Terminal lookahead, Action newAction, int stateIndex,
                                  Consumer<String> errorReporter) {
    var existing = actions.get(lookahead);
    if (existing == null) {
      actions.put(lookahead, newAction);
      return;
    }
    if (existing.equals(newAction)) {
      return;
    }

    // ---- Shift/Reduce conflict ----
    Shift shiftAction = null;
    Reduce reduceAction = null;
    if (existing instanceof Reduce r && newAction instanceof Shift s) {
      shiftAction = s;
      reduceAction = r;
    } else if (existing instanceof Shift s && newAction instanceof Reduce r) {
      shiftAction = s;
      reduceAction = r;
    }

    if (shiftAction != null) {
      var termPrec = precedenceMap.get(lookahead);
      var prodPrec = precedenceMap.get(reduceAction.production());
      if (termPrec != null && prodPrec != null) {
        var action = resolveShiftReduceConflict(shiftAction, reduceAction, termPrec, prodPrec);
        actions.put(lookahead, action);
        return;
      }
      errorReporter.accept(
          "Unresolved shift/reduce conflict in state " + stateIndex +
              " on terminal '" + lookahead.name() + "'" +
              " between [" + existing + "] and [" + newAction + "]");
      return;
    }

    // ---- Reduce/Reduce conflict ----
    errorReporter.accept(
        "Unresolved reduce/reduce conflict in state " + stateIndex +
            " on terminal '" + lookahead.name() + "'" +
            " between [" + existing + "] and [" + newAction + "]");
  }

  private static Action resolveShiftReduceConflict(Action shiftAction, Action reduceAction,
                                                   Precedence termPrec, Precedence prodPrec) {
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
}