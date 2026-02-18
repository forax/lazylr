package com.github.forax.lazylr;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class LRTransitionEngine {
  private final LRAlgorithm algorithm;

  // The "Canonical Map": Maps a set of Items (including lookaheads) to a unique State
  private final HashMap<Set<Item>, State> stateCache = new HashMap<>();

  // The Transition Table: (CurrentState -> Symbol) -> NextState
  private final HashMap<State, Map<Symbol, State>> transitionTable = new HashMap<>();

  LRTransitionEngine(LRAlgorithm algorithm) {
    this.algorithm = Objects.requireNonNull(algorithm);
    super();
  }

  /**
   * The Goto function: Moves the parser from currentState via symbol.
   * In LR(1), lookaheads are part of the state's identity.
   */
  public State move(State currentState, Symbol symbol) {
    // 1. Check if the transition is already cached
    if (transitionTable.containsKey(currentState)) {
      var cachedNext = transitionTable.get(currentState).get(symbol);
      if (cachedNext != null) {
        return cachedNext;
      }
    }

    // 2. Compute the "Kernel" for the next state
    // Find all items where the dot is before the current symbol and advance it.
    var kernels = currentState.items().stream()
        .filter(item -> symbol.equals(item.getNextSymbol()))
        .map(this::advanceItem)
        .collect(Collectors.toSet());

    // If no items can accept this symbol, there is no transition (error or accept)
    if (kernels.isEmpty()) {
      return null;
    }

    // 3. Compute the Closure
    // This expands the kernel to include all rules reachable via non-terminals.
    var closureItems = algorithm.computeClosure(kernels);

    // 4. State Identity (LR(1) Logic)
    // We use the full set of items (rules + dots + lookaheads) as the key.
    // If this exact state exists, we use it. Otherwise, create it.
    var nextState = stateCache.computeIfAbsent(closureItems, State::new);

    // 5. Memoize the transition for future use
    transitionTable
        .computeIfAbsent(currentState, _ -> new HashMap<>())
        .put(symbol, nextState);

    return nextState;
  }

  /**
   * Helper to move the dot forward.
   * In LR(1), lookaheads are carried forward exactly as they are.
   */
  private Item advanceItem(Item item) {
    return new Item(item.production(), item.dotPosition() + 1, item.lookaheads());
  }
}