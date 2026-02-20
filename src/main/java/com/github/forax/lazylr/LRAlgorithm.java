package com.github.forax.lazylr;

import com.github.forax.lazylr.LRTransitionEngine.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class LRAlgorithm {
  private final Grammar grammar;
  private final Map<Symbol, Set<Terminal>> firstSets;

  LRAlgorithm(Grammar grammar, Map<Symbol, Set<Terminal>> firstSets) {
    this.grammar = grammar;
    this.firstSets = firstSets;
    super();
  }

  /**
   * Computes the LR(1) closure.
   * For every item [A -> α . B β, a], for each production B -> γ,
   * add item [B -> . γ, FIRST(βa)]
   */
  public Set<Item> computeClosure(Set<Item> seedItems) {
    var closure = new HashSet<>(seedItems);
    var workList = new ArrayList<>(seedItems);

    var index = 0;
    while (index < workList.size()) {
      var item = workList.get(index++);
      var next = item.getNextSymbol();

      // If the symbol after the dot is a NonTerminal, we expand it
      if (next instanceof NonTerminal nonTerminal) {
        // Calculate FIRST(βa)
        var nextLookaheads = calculateNextLookaheads(item);

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

  private Set<Terminal> calculateNextLookaheads(Item item) {
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

    var changed = true;
    while (changed) {
      changed = false;
      for (var production : grammar.productions()) {
        var targetSet = firstSets.get(production.head());
        var beforeSize = targetSet.size();

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
        if (targetSet.size() > beforeSize) {
          changed = true;
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


  /*public static Map<NonTerminal, Set<Terminal>> computeFollowSets(Grammar grammar, Map<Symbol, Set<Terminal>> firstSets) {
    Objects.requireNonNull(grammar);
    Objects.requireNonNull(firstSets);

    // Initialize
    var followSets = new HashMap<NonTerminal, Set<Terminal>>();
    for (var production : grammar.productions()) {
      followSets.putIfAbsent(production.head(), new HashSet<>());
    }

    // Rule 1: Start symbol gets EOF ($)
    followSets.get(grammar.startSymbol()).add(Terminal.EOF);

    var changed = true;
    while (changed) {
      changed = false;
      for (var production : grammar.productions()) {
        var head = production.head();
        var body = production.body();

        for (var i = 0; i < body.size(); i++) {
          if (body.get(i) instanceof NonTerminal nonTerminal) {
            var followSet = followSets.get(nonTerminal);
            var beforeSize = followSet.size();

            // Rule 2: If A -> αBβ, add FIRST(β) \ {ε} to FOLLOW(A)
            var beta = body.subList(i + 1, body.size());
            var betaCanBeEmpty = true;

            for (var symbol : beta) {
              var firstSet = firstSets.get(symbol);
              for (var terminal : firstSet) {
                if (!terminal.equals(Terminal.EPSILON)) {
                  followSet.add(terminal);
                }
              }
              if (!firstSet.contains(Terminal.EPSILON)) {
                betaCanBeEmpty = false;
                break;
              }
            }

            // Rule 3: If A -> αB or A -> αBβ where β -> ε,
            // add FOLLOW(B) to FOLLOW(A)
            if (betaCanBeEmpty) {
              followSet.addAll(followSets.get(head));
            }

            if (followSet.size() > beforeSize) {
              changed = true;
            }
          }
        }
      }
    }

    followSets.replaceAll((_, set) -> Set.copyOf(set));
    return Map.copyOf(followSets);
  }*/
}