package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Manage state transitions and resolve grammar conflicts using precedence.
///
/// `LRTransitionEngine` implements the "Lazy" aspect of the parser by computing
/// states and transitions on-demand. It maintains a canonical map of [State]s
/// to ensure that equivalent item sets (including lookaheads) are represented
/// by the same object.
///
/// ### Conflict Resolution
/// The engine implements standard LR(1) **Shift/Reduce** resolution rules:
/// - The highest [Precedence] level of the [Production] and the [Terminal] wins,
/// - If levels are tied, [Precedence.Associativity] is used.
///
/// ### State Identity
/// In LR(1), a [State] is defined by its set of [Item]s, where each item includes
/// a lookahead. This engine uses the [#stateCache] to ensure state uniqueness,
/// which is critical for the parser's correctness and performance.
///
final class LRTransitionEngine {

  /// Represents an LR(1) item: a production rule, a dot position, and a lookahead.
  ///
  /// This record includes performance optimizations like identity-based hashing
  /// for [Production]s and a cached hash code.
  static final class Item {
    private final Production production;
    private final int dot;
    private final Terminal lookahead;
    private final int hashCode;  // cached hashCode for perf reason

    Item(Production production, int dot, Terminal lookahead) {
      var hashCode = (System.identityHashCode(production) * 31 + dot) * 31 + lookahead.hashCode();
      this.production = production;
      this.dot = dot;
      this.lookahead = lookahead;
      this.hashCode = hashCode;
      super();
    }

    public Production production() {
      return production;
    }

    public Terminal lookahead() {
      return lookahead;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    /// Two items are equal if they represent the same rule at the same position with the same lookahead.
    ///
    /// Production equality uses reference identity (`==`) rather than structural equality.
    /// So we can detect duplicate productions as reduce/reduce conflicts.
    /// This is also a performance optimization.
    @Override
    public boolean equals(Object o) {
      return o instanceof Item item &&
          production == item.production &&
          dot == item.dot &&
          lookahead.equals(item.lookahead);
    }

    /// @return `true` if the dot is at the end of the production.
    public boolean isCompleted() {
      return dot == production.body().size();
    }

    /// @return The symbol immediately following the dot, or `null` if the rule is completed.
    public @Nullable Symbol getNextSymbol() {
      if (dot < production.body().size()) {
        return production.body().get(dot);
      }
      return null; // Dot is at the end (Reduce state)
    }

    /// @return The sequence of symbols following the symbol after the dot.
    public List<Symbol> getSymbolsAfterNext() {
      if (dot + 1 < production.body().size()) {
        return production.body().subList(dot + 1, production.body().size());
      }
      return List.of();
    }

    /// Move the dot forward.
    private Item moveDotForward() {
      return new Item(production, dot + 1, lookahead);
    }
  }

  /// A set of [Item]s representing a specific state in the LR automaton.
  static final class State {
    private final Set<Item> items;
    private final int hashCode;  // cached hashCode for perf reason

    State(Set<Item> items) {
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
    /// Report an error
    record Error(ErrorKind kind, String message) implements Action {
      public Error {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(message);
      }
    }
    enum ErrorKind { CONFLICT, PARSE }
  }

  private final Grammar grammar;
  private final Map<PrecedenceEntity, Precedence> precedenceMap;
  private final Map<Symbol, Set<Terminal>> firstSets;

  // The "Canonical Map": Maps a set of Items (including lookaheads) to a unique State
  private final HashMap<Set<Item>, State> stateCache = new HashMap<>();

  // The Transition Table: (CurrentState -> Symbol) -> NextState
  private final HashMap<State, Map<Symbol, State>> transitionTable = new HashMap<>();

  // The Action Table: (CurrentState -> Terminal) -> Action
  private final HashMap<State, Map<Terminal, Action>> actionTable = new HashMap<>();

  LRTransitionEngine(Grammar grammar, Map<PrecedenceEntity, Precedence> precedenceMap, Map<Symbol, Set<Terminal>> firstSets) {
    this.grammar = grammar;
    this.precedenceMap = precedenceMap;
    this.firstSets = firstSets;
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
    actionTable
        .computeIfAbsent(currentState, _ -> new HashMap<>())
        .put(lookahead, action);
    return action;
  }

  private Action resolveAction(State currentState, Terminal lookahead) {
    // Find a possible Reduction
    var reduceItem = (Item) null;
    for(var item : currentState.items()) {
      if (item.isCompleted() && item.lookahead().equals(lookahead)) {
        if (reduceItem == null) {
          reduceItem = item;
          continue;
        }
        var itemPrec = precedenceMap.get(item.production);
        var reduceItemPrec = precedenceMap.get(reduceItem.production);
        if (itemPrec != null && reduceItemPrec != null) {
          if (itemPrec.level() < reduceItemPrec.level()) {
            continue;
          }
          if (itemPrec.level() > reduceItemPrec.level()) {
            reduceItem = item;
            continue;
          }
        }
        return new Action.Error(Action.ErrorKind.CONFLICT, "reduce/reduce conflict " +
            reduceItem.production.name() + " vs " + item.production.name());
      }
    }

    // Find a possible Shift
    var shiftState = move(currentState, lookahead);

    if (reduceItem != null && shiftState != null) {
      // Shift/Reduce conflict resolution via precedence
      var production = reduceItem.production();
      var terminalPrec = precedenceMap.get(lookahead);
      var productionPrec = precedenceMap.get(production);
      if (terminalPrec != null && productionPrec != null) {
        return shouldReduce(terminalPrec, productionPrec)
            ? new Action.Reduce(production)
            : new Action.Shift(shiftState);
      }
      return new Action.Error(Action.ErrorKind.CONFLICT,
          "shift/reduce conflict " + production.name());
    }
    if (reduceItem != null) {
      return new Action.Reduce(reduceItem.production());
    }
    if (shiftState != null) {
      return new Action.Shift(shiftState);
    }
    return new Action.Error(Action.ErrorKind.PARSE, "");
  }

  /// Decides between a shift and a reduction based on precedence rules.
  ///
  /// Logic:
  /// * Higher [Precedence#level()] wins.
  /// * If levels are equal, [Precedence.Associativity#LEFT] results in a reduction.
  private boolean shouldReduce(Precedence terminalPrec, Precedence productionPrec) {
    if (productionPrec.level() > terminalPrec.level()) {
      return true;  // Reduce (Production is stronger)
    }
    if (productionPrec.level() < terminalPrec.level()) {
      return false; // Shift (Terminal is stronger)
    }
    // Levels are equal? Use associativity
    return terminalPrec.associativity() == Precedence.Associativity.LEFT; // Left-associativity means Reduce
  }

  /// Implements the GOTO function of LR parsing.
  ///
  /// This method calculates the next state when transitioning from `currentState``
  /// via `symbol`. It computes the kernel, expands it via [LRAlgorithm#computeClosure],
  /// and retrieves the canonical [State] from the cache.
  public @Nullable State move(State currentState, Symbol symbol) {
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
    var kernels = new HashSet<Item>();
    for (var item : currentState.items) {
      if (symbol.equals(item.getNextSymbol())) {
        kernels.add(item.moveDotForward());
      }
    }

    // If no items can accept this symbol, there is no transition (error or accept)
    if (kernels.isEmpty()) {
      return null;
    }

    // 3. Compute the Closure
    // This expands the kernel to include all rules reachable via non-terminals.
    var closureItems = LRAlgorithm.computeClosure(grammar, firstSets, kernels);

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

  /// Returns the set of productions that have been reduced during parsing so far.
  ///
  /// This method relies on the lazy nature of the parser: because states are computed
  /// on demand as input is processed, a [Action.Reduce] entry for a production can
  /// only exist in the action table if the parser actually visited the state containing
  /// that reduction while processing real input.
  ///
  /// @param startProduction the production to exclude from the result.
  /// @return an unmodifiable snapshot of covered productions.
  Set<Production> reducedProductions(Production startProduction) {
    var covered = new HashSet<Production>();
    for (var stateActions : actionTable.values()) {
      for (var action : stateActions.values()) {
        if (action instanceof Action.Reduce(var production) && production != startProduction) {
          covered.add(production);
        }
      }
    }
    return covered;
  }
}