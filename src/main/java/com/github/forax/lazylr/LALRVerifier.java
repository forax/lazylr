package com.github.forax.lazylr;

import java.util.*;
import java.util.function.Consumer;

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
    var followSets = computeFollowSets(grammar, firstSets);
    var automaton = buildLR0Automaton(grammar, augmentedStart);
    var states = automaton.states();
    var gotoTable = automaton.gotoTable();
    buildActionTable(states, gotoTable, fullPrecedenceMap, augmentedStart, followSets, errorReporter);
  }

  // -----------------------------------------------------------------------
  // Internal representation
  // -----------------------------------------------------------------------

  /// An LR(0) item: a production with a "dot" position.
  private record Item(Production production, int dot) {

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

    /// Advance the dot past the next symbol.
    public Item advance() {
      return new Item(production, dot + 1);
    }

    @Override
    public String toString() {
      var body = production.body();
      var builder = new StringBuilder(production.head().name()).append(" :");
      for (var i = 0; i < body.size(); i++) {
        if (i == dot) {
          builder.append(" .");
        }
        builder.append(" ").append(body.get(i).name());
      }
      if (dot == body.size()) {
        builder.append(" •");
      }
      return builder.toString();
    }
  }

  private record Automaton(List<Set<Item>> states, List<Map<Symbol, Integer>> gotoTable) {}


  // -----------------------------------------------------------------------
  // Step 1: Augmented grammar
  // -----------------------------------------------------------------------

  private static Production buildAugmentedProduction(Grammar grammar) {
    var augmentedStartSymbol = new NonTerminal("__start__");
    return new Production(augmentedStartSymbol,
        List.of(grammar.startSymbol()));
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
  // Step 3: FOLLOW sets
  // -----------------------------------------------------------------------

  private static Map<NonTerminal, Set<Terminal>> computeFollowSets(Grammar grammar, Map<NonTerminal, Set<Terminal>> firstSets) {
    var followSets = new HashMap<NonTerminal, Set<Terminal>>();
    for (var nt : grammar.nonTerminals()) {
      followSets.put(nt, new HashSet<>());
    }
    // The augmented start's follow set includes EOF
    followSets.get(grammar.startSymbol()).add(Terminal.EOF);

    boolean changed = true;
    while (changed) {
      changed = false;
      for (var prod : grammar.productions()) {
        var body = prod.body();
        for (int i = 0; i < body.size(); i++) {
          if (!(body.get(i) instanceof NonTerminal nt)) {
            continue;
          }
          var follow = followSets.get(nt);
          // FIRST of everything after position i
          var rest = body.subList(i + 1, body.size());
          var firstRest = firstOfSequence(rest, firstSets);
          // Add FIRST(rest) \ {ε} to FOLLOW(nt)
          for (var terminal : firstRest) {
            if (!terminal.equals(Terminal.EPSILON)) {
              if (follow.add(terminal)) {
                changed = true;
              }
            }
          }
          // If ε ∈ FIRST(rest), add FOLLOW(head) to FOLLOW(nt)
          if (firstRest.contains(Terminal.EPSILON)) {
            if (follow.addAll(followSets.get(prod.head()))) {
              changed = true;
            }
          }
        }
      }
    }
    return followSets;
  }

  // -----------------------------------------------------------------------
  // Step 4: LR(0) automaton
  // -----------------------------------------------------------------------

  private static Automaton buildLR0Automaton(Grammar grammar, Production augmentedStart) {
    var states = new ArrayList<Set<Item>>();
    var gotoTable = new ArrayList<Map<Symbol, Integer>>();

    var initial = closure(Set.of(new Item(augmentedStart, 0)), grammar);
    states.add(initial);
    gotoTable.add(new HashMap<>());

    // BFS
    for (var i = 0; i < states.size(); i++) {
      var state = states.get(i);
      // Collect all symbols after dots
      var nextSymbols = new LinkedHashSet<Symbol>();
      for (var item : state) {
        var sym = item.nextSymbol();
        if (sym != null) {
          nextSymbols.add(sym);
        }
      }
      for (var sym : nextSymbols) {
        var next = goTo(state, sym, grammar);
        // Check if this state already exists
        int target = states.indexOf(next);
        if (target == -1) {
          target = states.size();
          states.add(next);
          gotoTable.add(new HashMap<>());
        }
        gotoTable.get(i).put(sym, target);
      }
    }
    return new Automaton(states, gotoTable);
  }

  /// Compute closure of an item set.
  private static Set<Item> closure(Set<Item> items, Grammar grammar) {
    var result = new LinkedHashSet<>(items);
    var worklist = new ArrayDeque<>(items);
    while (!worklist.isEmpty()) {
      var item = worklist.poll();
      var sym = item.nextSymbol();
      if (!(sym instanceof NonTerminal nt)) {
        continue;
      }
      for (var prod : grammar.productionsFor(nt)) {
        var newItem = new Item(prod, 0);
        if (result.add(newItem)) {
          worklist.add(newItem);
        }
      }
    }
    return result;
  }

  /// Compute goto(state, sym).
  private static Set<Item> goTo(Set<Item> state, Symbol sym, Grammar grammar) {
    var kernel = new LinkedHashSet<Item>();
    for (var item : state) {
      if (sym.equals(item.nextSymbol())) {
        kernel.add(item.advance());
      }
    }
    return closure(kernel, grammar);
  }

  // -----------------------------------------------------------------------
  // Step 5: Build LALR(1) action table (detect / resolve conflicts)
  // -----------------------------------------------------------------------

  // Action kinds
  private sealed interface Action permits Shift, Reduce, Accept {}
  private record Shift(int target) implements Action {}
  private record Reduce(Production production) implements Action {}
  private record Accept() implements Action {}

  private static void buildActionTable(List<Set<Item>> states, List<Map<Symbol, Integer>> gotoTable,
                                       Map<PrecedenceEntity, Precedence> precedenceMap,
                                       Production augmentedStart, Map<NonTerminal, Set<Terminal>> followSets,
                                       Consumer<String> errorReporter) {
    // action[state][terminal] = Action
    var actionTable = new ArrayList<Map<Terminal, Action>>();
    for (int i = 0; i < states.size(); i++) {
      actionTable.add(new HashMap<>());
    }

    for (int i = 0; i < states.size(); i++) {
      var state = states.get(i);
      var actions = actionTable.get(i);
      var transitions = gotoTable.get(i);

      for (var item : state) {
        if (item.isComplete()) {
          // It's a reduce (or accept) item
          if (item.production().equals(augmentedStart)) {
            // accept on EOF
            mergeAction(actions, precedenceMap, Terminal.EOF, new Accept(), item.production(), i, errorReporter);
          } else {
            // reduce on each terminal in FOLLOW(head)
            var follow = followSets.getOrDefault(item.production().head(), Set.of());
            for (var lookahead : follow) {
              mergeAction(actions, precedenceMap, lookahead, new Reduce(item.production()), item.production(), i, errorReporter);
            }
          }
        } else {
          // Shift on terminal
          var sym = item.nextSymbol();
          if (sym instanceof Terminal t) {
            var target = transitions.get(t);
            mergeAction(actions, precedenceMap, t, new Shift(target), null, i, errorReporter);
          }
        }
      }
    }
  }

  /// Merge a new action into the action table, resolving conflicts via precedence.
  private static void mergeAction(Map<Terminal, Action> actions, Map<PrecedenceEntity, Precedence> precedenceMap,
                                  Terminal lookahead, Action newAction, Production reduceProd, int stateIndex,
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
    if (isShiftReduceConflict(existing, newAction) || isShiftReduceConflict(newAction, existing)) {
      Production reduceProd2 = (existing instanceof Reduce r) ? r.production()
          : (newAction instanceof Reduce r2) ? r2.production() : reduceProd;

      Precedence termPrec = precedenceMap.get(lookahead);
      Precedence prodPrec = precedenceMap.get(reduceProd2);

      if (termPrec != null && prodPrec != null) {
        // Resolve: higher level wins; on tie use associativity
        if (termPrec.level() > prodPrec.level()) {
          // Shift wins
          Action shiftAction = (existing instanceof Shift) ? existing : newAction;
          actions.put(lookahead, shiftAction);
        } else if (prodPrec.level() > termPrec.level()) {
          // Reduce wins
          Action reduceAction = (existing instanceof Reduce) ? existing : newAction;
          actions.put(lookahead, reduceAction);
        } else {
          // Same level: use associativity
          if (termPrec.assoc() == Precedence.Associativity.LEFT) {
            // Reduce wins (left associative)
            Action reduceAction = (existing instanceof Reduce) ? existing : newAction;
            actions.put(lookahead, reduceAction);
          } else {
            // Shift wins (right associative)
            Action shiftAction = (existing instanceof Shift) ? existing : newAction;
            actions.put(lookahead, shiftAction);
          }
        }
        return;
      }
      errorReporter.accept(
          "Unresolved shift/reduce conflict in state " + stateIndex +
              " on terminal '" + lookahead.name() + "'" +
              " between [" + existing + "] and [" + newAction + "]");
    }

    // ---- Reduce/Reduce conflict ----
    errorReporter.accept(
        "Unresolved Reduce/reduce conflict in state " + stateIndex +
            " on terminal '" + lookahead.name() + "'" +
            " between [" + existing + "] and [" + newAction + "]");
  }

  private static boolean isShiftReduceConflict(Action a, Action b) {
    return a instanceof Shift && b instanceof Reduce;
  }
}