package com.github.forax.lazylr;

import com.github.forax.lazylr.LRTransitionEngine.Item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Internal utility providing the fundamental algorithms for LR(1) parser generation.
///
/// This class encapsulates the mathematical logic required to analyze a [Grammar],
/// specifically the computation of FIRST sets and the **Closure** of item sets.
/// These are the building blocks used by the [Parser] to construct the state machine.
///
/// ### FIRST Set Computation
/// The [#computeFirstSets(Grammar)] method implements a fixed-point iteration algorithm.
/// For each [Symbol], it determines the set of [Terminal]s that can appear at the
/// beginning of a string derived from that symbol. It correctly handles
/// [Terminal#EPSILON] derivations to propagate lookaheads.
///
/// ### LR(1) Closure
/// The [#computeClosure(Grammar, Map, Set)] method expands a "seed" set of [Item]s
/// into a full state.
/// It follows the rule: If an item `[A -> α . B β, a]` is in the closure,
/// then for every production `B -> γ`, the item `[B -> . γ, b]` is added
/// for every `b` in `FIRST(βa)`.
///
final class LRAlgorithm {
  private LRAlgorithm() {
    throw new AssertionError();
  }

  /// Computes the LR(1) closure of a set of items.
  ///
  /// This expansion is what allows the parser to predict which productions
  /// might be encountered next.
  ///
  /// @param seedItems The kernel or initial items of a state.
  /// @return A complete set of items representing a full LR(1) state.
  public static Set<Item> computeClosure(Grammar grammar, Map<Symbol, Set<Terminal>> firstSets, Set<Item> seedItems) {
    var closure = new HashSet<>(seedItems);
    var workList = new ArrayList<>(seedItems);

    var index = 0;
    while (index < workList.size()) {
      var item = workList.get(index++);
      var next = item.getNextSymbol();

      // If the symbol after the dot is a NonTerminal, we expand it
      if (next instanceof NonTerminal nonTerminal) {
        // Calculate FIRST(βa)
        var nextLookaheads = calculateNextLookaheads(firstSets, item);

        for (var production : grammar.productionsFor(nonTerminal)) {
          // Create the new Items.
          for (var nextLookahead : nextLookaheads) {
            var newItem = new Item(production, 0, nextLookahead);

            // If this exact item (including lookaheads) isn't in the closure, add it
            if (closure.add(newItem)) {
              workList.add(newItem);
            }
          }
        }
      }
    }
    return closure;
  }

  /// Calculates the FIRST(βa) lookahead set for a given item expansion.
  ///
  /// It looks at the symbols following a non-terminal (β) and determines
  /// which terminals can start them. If β can derive epsilon, it includes
  /// the parent item's lookahead (a).
  ///
  /// @param item The item being expanded.
  /// @return The set of terminals that can follow the expanded non-terminal.
  private static Set<Terminal> calculateNextLookaheads(Map<Symbol, Set<Terminal>> firstSets, Item item) {
    var result = new HashSet<Terminal>();
    var beta = item.getSymbolsAfterNext(); // Symbols following the NonTerminal

    var betaCanBeEmpty = true;
    for (var symbol : beta) {

      // Add all terminals from FIRST but ε
      var firstSet = firstSets.get(symbol);
      for (var terminal : firstSet) {
        if (!terminal.equals(Terminal.EPSILON)) {
          result.add(terminal);
        }
      }

      // if no ε, we can stop here
      if (!firstSet.contains(Terminal.EPSILON)) {
        betaCanBeEmpty = false;
        break;
      }
    }

    // If β is empty or can derive epsilon, add the parent's lookahead 'a'
    if (betaCanBeEmpty) {
      result.add(item.lookahead());
    }
    return result;
  }

  /// Computes the FIRST sets for all symbols (not only non-terminals) in the grammar.
  ///
  /// This is a static analysis phase that runs before the parser state machine
  /// is constructed. It accounts for:
  /// 1. Terminals (FIRST is the terminal itself).
  /// 2. Non-Terminals (FIRST is the union of the FIRST sets of its productions).
  /// 3. Epsilon derivations.
  ///
  /// @param grammar The grammar to analyze.
  /// @return A mapping from each [Symbol] to its set of starting [Terminal]s.
  public static Map<Symbol, Set<Terminal>> computeFirstSets(Grammar grammar) {
    Objects.requireNonNull(grammar);

    // Initialize: FIRST(terminal) is {terminal}
    var firstSets = new HashMap<Symbol, Set<Terminal>>();
    for (var production : grammar.productions()) {
      initFirst(production.head(), firstSets);
      for (var symbol : production.body()) {
        initFirst(symbol, firstSets);
      }
    }

    // Build a reverse dependency map:
    // if FIRST(B) changes, we need to re-examine every production that has B
    // somewhere in a prefix position (i.e., B could contribute to FIRST(head)).
    //
    // Conservative but correct: for each production A -> X1 X2 ... Xn,
    // every Xi that can be reached from the front (i.e., all X1 ... Xi-1 are
    // nullable) is a potential contributor. We add A as a dependent of each
    // such Xi that is a NonTerminal.
    var dependents = new HashMap<NonTerminal, Set<NonTerminal>>();
    for (var nonTerminal : grammar.nonTerminals()) {
      dependents.put(nonTerminal, new HashSet<>());
    }
    for (var production : grammar.productions()) {
      loop: for (var symbol : production.body()) {
        switch (symbol) {
          case Terminal _ -> {
            // Terminals are never nullable; nothing to their right can reach FIRST(head).
            break loop;
          }
          case NonTerminal dependency ->
            // head depends on this non-terminal (it may be nullable, so we keep going).
            dependents.computeIfAbsent(dependency, _ -> new HashSet<>())
                .add(production.head());
        }
      }
    }

    // Seed the worklist with every non-terminal that has at least one
    // terminal in its FIRST set (i.e., any non-terminal with a direct
    // terminal production or an epsilon production).
    var worklist = new ArrayDeque<NonTerminal>();
    var inWorklist = new HashSet<NonTerminal>();
    for (var nt : grammar.nonTerminals()) {
      worklist.add(nt);
      inWorklist.add(nt);
    }

    while (!worklist.isEmpty()) {
      var head = worklist.poll();
      inWorklist.remove(head);

      var targetSet = firstSets.get(head);
      var beforeSize = targetSet.size();

      for (var production : grammar.productionsFor(head)) {

        // Rule: If production is A -> Y1 Y2 ... Yn
        var allCanBeEpsilon = true;
        for (var symbol : production.body()) {
          var firstSet = firstSets.get(symbol);

          // Add all non-epsilon symbols from FIRST(symbol) to FIRST(head)
          for (var terminal : firstSet) {
            if (!terminal.equals(Terminal.EPSILON)) {
              targetSet.add(terminal);
            }
          }

          // If symbol doesn't have epsilon, we stop looking at this production
          if (!firstSet.contains(Terminal.EPSILON)) {
            allCanBeEpsilon = false;
            break;
          }
        }

        if (allCanBeEpsilon) {
          targetSet.add(Terminal.EPSILON);
        }
      }

      // If this non-terminal's FIRST set grew, schedule all non-terminals
      // that depend on it — they may gain new terminals too.
      if (targetSet.size() > beforeSize) {
        for (var dependent : dependents.get(head)) {
          if (inWorklist.add(dependent)) {
            worklist.add(dependent);
          }
        }
      }
    }

    firstSets.replaceAll((_, set) -> Set.copyOf(set));
    return Map.copyOf(firstSets);
  }

  private static void initFirst(Symbol symbol, HashMap<Symbol, Set<Terminal>> firstSets) {
    if (!firstSets.containsKey(symbol)) {
      var terminalSet = new HashSet<Terminal>();
      firstSets.put(symbol, terminalSet);
      switch (symbol) {
        case Terminal t -> terminalSet.add(t);
        case NonTerminal _ -> {}
      }
    }
  }
}