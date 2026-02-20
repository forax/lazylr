package com.github.forax.lazylr;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class LRTransitionEngine {
  private final LRAlgorithm algorithm;
  private final Map<PrecedenceEntity, Precedence> precedenceMap;

  // The "Canonical Map": Maps a set of Items (including lookaheads) to a unique State
  private final HashMap<Set<Item>, State> stateCache = new HashMap<>();

  // The Transition Table: (CurrentState -> Symbol) -> NextState
  private final HashMap<State, Map<Symbol, State>> transitionTable = new HashMap<>();

  // The Action Table: (CurrentState -> Symbol) -> Action
  private final HashMap<State, Map<Symbol, Action>> actionTable = new HashMap<>();

  LRTransitionEngine(LRAlgorithm algorithm, Map<PrecedenceEntity, Precedence> precedenceMap) {
    this.algorithm = algorithm;
    this.precedenceMap = precedenceMap;
    super();
  }

  public Action getAction(State currentState, Terminal lookahead) {
    var stateActions = actionTable.get(currentState);
    if (stateActions != null) {
      var cached = stateActions.get(lookahead);
      if (cached != null) {
        return cached;
      }
    }

    var action = resolveAction(currentState, lookahead);
    if (action != null) {
      actionTable
          .computeIfAbsent(currentState, _ -> new HashMap<>())
          .put(lookahead, action);
    }
    return action;
  }

  private Action resolveAction(State currentState, Terminal lookahead) {
    // Find all possible Reductions
    var possibleReductions = currentState.items().stream()
        .filter(Item::isCompleted)
        .filter(item -> item.lookahead().equals(lookahead))
        .toList();

    // Find a possible Shift
    var shiftState = move(currentState, lookahead);

    var bestCandidate = bestCandidateForReduction(possibleReductions);

    if (bestCandidate != null && shiftState != null) {
      // Shift/Reduce conflict resolution via precedence
      return shouldReduce(bestCandidate.production(), lookahead)
          ? new Action.Reduce(bestCandidate.production())
          : new Action.Shift(shiftState);
    }
    if (bestCandidate != null) {
      return new Action.Reduce(bestCandidate.production());
    }
    if (shiftState != null)    {
      return new Action.Shift(shiftState);
    }
    return null;
  }

  private Item bestCandidateForReduction(List<Item> possibleReductions) {
    return switch (possibleReductions.size()) {
      case 0 -> null;
      case 1 -> possibleReductions.getFirst();
      default -> possibleReductions.stream()
          .max(Comparator.comparingInt(i -> {
            var precedence = precedenceMap.get(i.production());
            return precedence.level();
          }))
          .orElseThrow();
    };
  }

  private boolean shouldReduce(Production production, Terminal lookahead) {
    var rulePrec = precedenceMap.get(production);
    var tokenPrec = precedenceMap.get(lookahead);

    if (rulePrec != null && tokenPrec != null) {
      if (rulePrec.level() > tokenPrec.level()) return true;  // Reduce (Rule is stronger)
      if (rulePrec.level() < tokenPrec.level()) return false; // Shift (Token is stronger)

      // Levels are equal? Use associativity
      return rulePrec.assoc() == Precedence.Associativity.LEFT; // Left-assoc means Reduce
    }

    // Default: Shift wins (standard yacc/bison behavior)
    return false;
  }

  /**
   * The Goto function: Moves the parser from currentState via symbol.
   * In LR(1), lookaheads are part of the state's identity.
   */
  public State move(State currentState, Symbol symbol) {
    // 1. Check if the transition is already cached
    var stateMap = transitionTable.get(currentState);
    if (stateMap != null) {
      var cachedNext = stateMap.get(symbol);
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
    return new Item(item.production(), item.dotPosition() + 1, item.lookahead());
  }
}