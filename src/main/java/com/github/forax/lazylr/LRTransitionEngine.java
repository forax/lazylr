package com.github.forax.lazylr;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/// Manage state transitions and resolve grammar conflicts using precedence.
///
/// `LRTransitionEngine` implements the "Lazy" aspect of the parser by computing
/// states and transitions on-demand. It maintains a canonical map of [State]s
/// to ensure that equivalent item sets (including lookaheads) are represented
/// by the same object.
///
/// ### Conflict Resolution
/// The engine implements standard LR(1) resolution rules:
/// * **Shift/Reduce**: Resolved using the [Precedence] levels of the [Production]
///    and the [Terminal]. If levels are tied, [Precedence.Associativity] is used.
/// * **Reduce/Reduce**: Resolved by picking the [Production] with the highest
///    explicitly assigned precedence.
///
/// ### State Identity
/// In LR(1), a [State] is defined by its set of [Item]s, where each item includes
/// a lookahead. This engine uses the [stateCache] to ensure state uniqueness,
/// which is critical for the parser's correctness and performance.
///
final class LRTransitionEngine {

  /// Represents an LR(1) item: a production rule, a dot position, and a lookahead.
  ///
  /// This record includes performance optimizations like identity-based hashing
  /// for [Production]s and a cached hash code.
  static final class Item {
    private final Production production;
    private final int dotPosition;
    private final Terminal lookahead;
    private final int hashCode;  // cached hashCode for perf reason

    public Item(Production production, int dotPosition, Terminal lookahead) {
      Objects.requireNonNull(production);
      if (dotPosition < 0 || dotPosition > production.body().size()) {
        throw new IllegalArgumentException("Dot position must be between 0 and body size");
      }
      Objects.requireNonNull(lookahead);
      var hashCode = (System.identityHashCode(production) * 31 + dotPosition) * 31 + lookahead.hashCode();
      this.production = production;
      this.dotPosition = dotPosition;
      this.lookahead = lookahead;
      this.hashCode = hashCode;
      super();
    }

    public Production production() {
      return production;
    }

    public int dotPosition() {
      return dotPosition;
    }

    public Terminal lookahead() {
      return lookahead;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Item item &&
          production == item.production &&    // production are unique
          dotPosition == item.dotPosition &&
          lookahead.equals(item.lookahead);
    }

    /// @return The symbol immediately following the dot, or `null` if the rule is completed.
    public Symbol getNextSymbol() {
      if (dotPosition < production.body().size()) {
        return production.body().get(dotPosition);
      }
      return null; // Dot is at the end (Reduce state)
    }

    /// @return The sequence of symbols following the symbol after the dot.
    public List<Symbol> getSymbolsAfterNext() {
      if (dotPosition + 1 < production.body().size()) {
        return production.body().subList(dotPosition + 1, production.body().size());
      }
      return List.of();
    }

    /// @return `true` if the dot is at the end of the production.
    public boolean isCompleted() {
      return dotPosition == production.body().size();
    }

    @Override
    public String toString() {
      var joiner = new StringJoiner(" ",
          production.head().name() + " -> ",
          " {" + lookahead.name() + "}");
      var body = production.body();
      for (var i = 0; i < body.size(); i++) {
        if (i == dotPosition) {
          joiner.add(".");
        }
        joiner.add(body.get(i).name());
      }
      return joiner.toString();
    }
  }

  /// A set of [Item]s representing a specific state in the LR automaton.
  static final class State {
    private final Set<Item> items;
    private final int hashCode;  // cached hashCode for perf reason

    public State(Set<Item> items) {
      items = Set.copyOf(items);
      this.items = items;
      this.hashCode = items.hashCode();
      super();
    }

    public Set<Item> items() {
      return items;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof State state && items.equals(state.items);
    }

    @Override
    public String toString() {
      return "State[items=" + items + ']';
    }
  }

  /// Represents a parser decision for a given state and lookahead.
  sealed interface Action {
    /// Move to a [nextState] and consume the current token.
    record Shift(State nextState) implements Action {
      public Shift {
        Objects.requireNonNull(nextState);
      }
    }
    /// Apply a [production] and pop symbols from the stack.
    record Reduce(Production production) implements Action {
      public Reduce {
        Objects.requireNonNull(production);
      }
    }
  }

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

  /// Retrieves or computes the action for a given state and lookahead.
  ///
  /// If the action has not been encountered before, it is calculated via
  /// [#resolveAction] and cached in the [actionTable].
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

  /// Decides between a shift and a reduction based on precedence rules.
  ///
  /// Logic:
  /// * Higher [Precedence#level()] wins.
  /// * If levels are equal, [Precedence.Associativity#LEFT] results in a reduction.
  /// * Default is to **Shift** if no precedence is defined.
  private boolean shouldReduce(Production production, Terminal lookahead) {
    var rulePrec = precedenceMap.get(production);
    var tokenPrec = precedenceMap.get(lookahead);

    if (rulePrec != null && tokenPrec != null) {
      if (rulePrec.level() > tokenPrec.level()) return true;  // Reduce (Rule is stronger)
      if (rulePrec.level() < tokenPrec.level()) return false; // Shift (Token is stronger)

      // Levels are equal? Use associativity
      return rulePrec.associativity() == Precedence.Associativity.LEFT; // Left-associativity means Reduce
    }

    // Default: Shift wins (standard yacc/bison behavior)
    return false;
  }

  /// Implements the GOTO function of LR parsing.
  ///
  /// This method calculates the next state when transitioning from [currentState]
  /// via [symbol]. It computes the kernel, expands it via [LRAlgorithm#computeClosure],
  /// and retrieves the canonical [State] from the cache.
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