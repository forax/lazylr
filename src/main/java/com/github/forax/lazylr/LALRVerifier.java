package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

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

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

/// Verifies whether a grammar is well-formed and conforms to LALR(1) constraints,
/// using a precedence map to resolve potential shift/reduce conflicts.
///
/// A grammar is considered **well-formed** if:
///
///   - Every non-terminal is reachable from the start symbol.
///   - Every non-terminal is productive (can derive a string of terminals).
///
/// A grammar is **LALR(1)** if it is free of conflict:
///
///   - **Reduce/Reduce conflict:** Two or more distinct productions can be
///     reduced using the same lookahead symbol.
///   - **Shift/Reduce conflict:** A terminal can be shifted onto the stack
///     OR a production can be reduced using the same lookahead symbol, and
///     no precedence rule is defined to resolve it.
///
/// ### Usage Example:
/// ```java
/// var PLUS = new Terminal("+");
/// var NUM = new Terminal("num");
/// var E = new NonTerminal("E");
/// var pPlus = new Production(E, List.of(E, PLUS, E));
/// var pNum = new Production(E, List.of(NUM));
/// var grammar = new Grammar(E, List.of(pPlus, pNum));
///
/// // Define Left Associativity for PLUS and pPlus to resolve ambiguity
/// var prec = new Precedence(1, Precedence.Associativity.LEFT);
/// var precedenceMap = Map.of(PLUS, prec, pPlus, prec);
///
/// LALRVerifier.verify(grammar, precedenceMap, error -> {
///   System.err.println("Verify Error: " + error);
/// });
/// ```
///
/// This class is thread-safe and can be safely shared between multiple threads.
///
/// @see Grammar
/// @see Precedence
public final class LALRVerifier {
  // Design note:
  // LALRVerifier and Parser both build LR automata from the same grammar, and share
  // concepts like FIRST sets and LR(1) closure. However, they intentionally do not
  // share code because they have different goals and different performance requirements.
  //
  // Parser has to be fast: it is designed to be lazy (states are computed on
  // demand), memory-efficient, and optimized.
  //
  // This implementation follows the DeRemer & Pennello (1982) algorithm:
  // "Efficient Computation of LALR(1) Look-Ahead Sets".
  // Rather than building the full LR(1) automaton and merging states,
  // it works directly in LR(0) space and computes lookaheads via a
  // separate data-flow analysis (spontaneous generation plus propagation).

  private LALRVerifier() {
    throw new AssertionError();
  }

  /// Verifies that the grammar is well-formed and LALR(1)
  /// (possibly with precedence-based conflict resolution).
  /// In case of conflicts, prints the LALR state automaton to `System.err`.
  ///
  /// @param grammar       the grammar to verify.
  /// @param precedenceMap maps terminals and productions to their precedence;
  ///                      used to resolve shift/reduce conflicts.
  /// @throws NullPointerException if `grammar`, `precedenceMap` is null.
  public static void verify(Grammar grammar, Map<? extends PrecedenceEntity, Precedence> precedenceMap) {
    verify(grammar, precedenceMap, System.err, false, System.err::println);
  }

  /// Verifies that the grammar is well-formed and LALR(1)
  /// (possibly with precedence-based conflict resolution).
  /// If `alwaysPrint` is `true`, the LALR state automaton is printed unconditionally on `System.out`.
  /// Otherwise, it is printed only when conflicts are detected on `System.err`.
  ///
  /// @param grammar       the grammar to verify.
  /// @param precedenceMap maps terminals and productions to their precedence;
  ///                      used to resolve shift/reduce conflicts.
  /// @param alwaysPrint   if `true` the automaton is printed unconditionally.
  /// @throws NullPointerException if `grammar` or `precedenceMap` is null.
  public static void verify(Grammar grammar, Map<? extends PrecedenceEntity, Precedence> precedenceMap,
                            boolean alwaysPrint) {
    var out = alwaysPrint ? System.out : System.err;
    verify(grammar, precedenceMap, out, alwaysPrint, System.err::println);
  }

  /// Verifies that the grammar is well-formed and LALR(1)
  /// (possibly with precedence-based conflict resolution).
  /// In case of conflicts, the 'errorReporter' is called once per unresolved conflict.
  ///
  /// @param grammar       the grammar to verify.
  /// @param precedenceMap maps terminals and productions to their precedence;
  ///                      used to resolve shift/reduce conflicts.
  /// @param errorReporter called once per unresolved conflict with a human-readable
  ///                      description of the conflict.
  /// @throws NullPointerException if `grammar`, `precedenceMap` or `errorReporter` is null.
  public static void verify(Grammar grammar, Map<? extends PrecedenceEntity, Precedence> precedenceMap,
                            Consumer<? super String> errorReporter) {
    verify(grammar, precedenceMap, null, false, errorReporter);
  }

  /// Verifies that the grammar is well-formed and LALR(1)
  /// (possibly with precedence-based conflict resolution),
  /// optionally printing the LALR state automaton.
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
  /// @throws NullPointerException if `grammar`, `precedenceMap` or `errorReporter` is null.
  public static void verify(Grammar grammar, Map<? extends PrecedenceEntity, Precedence> precedenceMap,
                            @Nullable PrintStream out, boolean alwaysPrint, Consumer<? super String> errorReporter) {
    Objects.requireNonNull(grammar);
    Objects.requireNonNull(precedenceMap);
    Objects.requireNonNull(errorReporter);
    if (preAnalysis(grammar, errorReporter)) {
      return;
    }
    var augmentedStart = buildAugmentedProduction(grammar);
    var firstSets = computeFirstSets(grammar);
    var lr0Automaton = buildLR0Automaton(grammar, augmentedStart);
    var lookaheads = computeLookaheads(lr0Automaton, grammar, augmentedStart, firstSets);
    var fullPrecedenceMap = Precedence.complete(grammar, precedenceMap);
    var actionTable = buildActionTable(lr0Automaton, lookaheads, fullPrecedenceMap, augmentedStart);
    var conflicts = reportConflicts(actionTable, errorReporter);
    if (out != null && (alwaysPrint || conflicts)) {
      printAutomaton(lr0Automaton, augmentedStart, lookaheads, actionTable, out);
    }
  }

  // -- pre-analysis

  /// Computes the set of productive non-terminals.
  ///
  /// A non-terminal `A` is productive if there exists a derivation
  /// `A ->* w` where `w ∈ Σ*` (a string of terminals only).
  ///
  /// @param grammar the grammar to analyse
  /// @return a set of generating non-terminals
  static Set<NonTerminal> productiveNonTerminals(Grammar grammar) {
    // Count how many distinct non-terminal body symbols are still unproductive, per production.
    // When the count hits 0, the head becomes productive.
    var remainingDepsCount = new HashMap<Production, Integer>();
    // Reverse index: non-terminal -> productions that have that non-terminal in their body
    var dependents = new HashMap<NonTerminal, List<Production>>();

    var productives = new HashSet<NonTerminal>();
    var worklist = new ArrayDeque<NonTerminal>();

    for (var production : grammar.productions()) {
      // Collect distinct non-terminals in the body
      var bodyNonTerminals = new HashSet<NonTerminal>();
      for (var symbol : production.body()) {
        if (symbol instanceof NonTerminal nonTerminal && bodyNonTerminals.add(nonTerminal)) {
          dependents
              .computeIfAbsent(nonTerminal, _ -> new ArrayList<>())
              .add(production);
        }
      }

      remainingDepsCount.put(production, bodyNonTerminals.size());

      // Seed: if no non-terminals in body, head is immediately productive
      if (bodyNonTerminals.isEmpty() && productives.add(production.head())) {
        worklist.add(production.head());
      }
    }

    // Propagate
    while (!worklist.isEmpty()) {
      var nonTerminal = worklist.poll();
      for (var production : dependents.getOrDefault(nonTerminal, List.of())) {
        var remaining = (int) remainingDepsCount.merge(production, -1, Integer::sum);
        if (remaining == 0 && productives.add(production.head())) {
          worklist.add(production.head());
        }
      }
    }

    return productives;
  }

  /// Computes the set of reachable non-terminals.
  ///
  /// A non-terminal `A` is reachable if there exists `a` derivation
  /// `S ->*  αAβ` starting from the grammar's start symbol `S`.
  ///
  /// @param grammar the grammar to analyse
  /// @return a set of reachable non-terminals
  static Set<NonTerminal> reachableNonTerminals(Grammar grammar) {
    var startSymbol = grammar.startSymbol();
    var reachable = new HashSet<NonTerminal>();
    reachable.add(startSymbol);

    var stack = new ArrayDeque<NonTerminal>();
    stack.add(startSymbol);

    while (!stack.isEmpty()) {
      var current = stack.pop();
      for (var production : grammar.productionsFor(current)) {
        for (var symbol : production.body()) {
          if (symbol instanceof NonTerminal nt) {
            if (reachable.add(nt)) {
              stack.push(nt);
            }
          }
        }
      }
    }
    return reachable;
  }

  static boolean preAnalysis(Grammar grammar, Consumer<? super String> errorReporter) {
    var allNonTerminals = grammar.nonTerminals();

    // check un-productive non-terminals
    var unproductive = new LinkedHashSet<>(allNonTerminals);
    unproductive.removeAll(productiveNonTerminals(grammar));
    for (var nonTerminal : unproductive) {
      errorReporter.accept("unproductive non-terminal: " + nonTerminal);
    }

    // check un-reachable non-terminals
    var unreachable = new LinkedHashSet<>(allNonTerminals);
    unreachable.removeAll(reachableNonTerminals(grammar));
    for (var nonTerminal : unreachable) {
      errorReporter.accept("unreachable non-terminal: " + nonTerminal);
    }

    return !unproductive.isEmpty() || !unreachable.isEmpty();
  }

  // -----------------------------------------------------------------------
  // Internal representation
  // -----------------------------------------------------------------------

  /// An LR(0) item: a production with a "dot" position, no lookahead.
  private record LR0Item(Production production, int dot) {

    public boolean isComplete() {
      return dot == production.body().size();
    }

    /// The symbol after the dot, or null if the item is complete.
    public @Nullable Symbol nextSymbol() {
      if (isComplete()) {
        return null;
      }
      return production.body().get(dot);
    }

    /// Advance the dot past the next symbol.
    public LR0Item moveDotForward() {
      return new LR0Item(production, dot + 1);
    }
  }

  /// An LR(1) item used only during the spontaneous-generation pass.
  /// The lookahead field is either a real terminal or the dummy terminal `#`.
  private record LR1Item(Production production, int dot, Terminal lookahead) {

    public boolean isComplete() {
      return dot == production.body().size();
    }

    /// The symbol after the dot, or null if the item is complete.
    public @Nullable Symbol nextSymbol() {
      if (isComplete()) {
        return null;
      }
      return production.body().get(dot);
    }
  }

  /// One LALR state: a set of LR(0) items together with its transition map.
  /// The transition map is mutable only during the construction of
  /// the LR(0) automaton.
  private record LR0State(Set<LR0Item> items, Map<Symbol, Integer> transitions) {}

  /// The LR(0) automaton: ordered list of states
  private record LR0Automaton(List<LR0State> states) {}

  /// A propagation link: lookaheads on `fromItem` in state `fromState`
  /// propagate to `toItem` in state `toState`.
  private record PropagationLink(int fromState, LR0Item fromItem,
                                 int toState,   LR0Item toItem) {}


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

  /// Computes FIRST sets for all non-terminals using a worklist algorithm with
  /// dependency tracking, rather than the naïve fixed-point loop that rescans all
  /// productions on every iteration. A reverse-dependency map records which
  /// non-terminals must be re-queued when a given FIRST set grows, so work is
  /// proportional to the number of new terminals discovered, not to grammar size.
  private static Map<NonTerminal, Set<Terminal>> computeFirstSets(Grammar grammar) {
    var nonTerminals = grammar.nonTerminals();

    // Initialize one empty set per non-terminal.
    var firstSets = new HashMap<NonTerminal, Set<Terminal>>(nonTerminals.size() * 2);
    for (var nonTerminal : nonTerminals) {
      firstSets.put(nonTerminal, new HashSet<>());
    }

    // Build a reverse-dependency map:
    // dependents.get(B) = { A | some production of A has B in a prefix position }
    // "Prefix position" here is conservative: every non-terminal in any production
    // body is registered as a potential dependency of the head.
    var dependents = new HashMap<NonTerminal, Set<NonTerminal>>(nonTerminals.size() * 2);
    for (var nonTerminal : nonTerminals) {
      dependents.put(nonTerminal, new HashSet<>());
    }
    for (var production : grammar.productions()) {
      loop: for (var symbol : production.body()) {
        switch (symbol) {
          case Terminal _ -> {
            break loop;
          }
          case NonTerminal bodyNt ->
            dependents.computeIfAbsent(bodyNt, _ -> new HashSet<>())
                .add(production.head());
        }
      }
    }

    // Seed the worklist with every non-terminal so that the initial pass
    // propagates terminals from simple productions (A → t) into all FIRST sets.
    var worklist = new ArrayDeque<>(nonTerminals);
    var inWorklist = new HashSet<>(nonTerminals);

    while (!worklist.isEmpty()) {
      var head = worklist.poll();
      inWorklist.remove(head);

      var headFirst = firstSets.get(head);
      var sizeBefore = headFirst.size();

      for (var production : grammar.productionsFor(head)) {
        // Walk the body left-to-right, adding FIRST(Xᵢ) \ {ε} and stopping
        // as soon as a non-nullable symbol is encountered.
        var allNullable = true;
        for (var symbol : production.body()) {
          switch (symbol) {
            case Terminal t -> {
              // A terminal contributes itself; it cannot be ε (ε is a grammar
              // sentinel, not a real input token), so the body is non-nullable
              // from this point onward.
              headFirst.add(t);
              allNullable = false;
            }
            case NonTerminal nonTerminal -> {
              var firstSet = firstSets.get(nonTerminal);
              for (var terminal : firstSet) {
                if (!terminal.equals(Terminal.EPSILON)) {
                  headFirst.add(terminal);
                }
              }
              if (!firstSet.contains(Terminal.EPSILON)) {
                allNullable = false;
              }
            }
          }
          if (!allNullable) {
            break;
          }
        }

        // ε ∈ FIRST(A) iff every symbol in the body is nullable (or body is empty).
        if (allNullable) {
          headFirst.add(Terminal.EPSILON);
        }
      }

      // If FIRST(head) grew, re-queue every non-terminal that depends on it:
      // their FIRST sets may gain new terminals in later iterations.
      if (headFirst.size() > sizeBefore) {
        for (var dependent : dependents.get(head)) {
          if (inWorklist.add(dependent)) {
            worklist.add(dependent);
          }
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
  // Step 3: LR(0) automaton
  // -----------------------------------------------------------------------

  /// Compute the LR(0) closure of a set of LR(0) items.
  private static Set<LR0Item> lr0Closure(Set<LR0Item> items, Grammar grammar) {
    var result = new LinkedHashSet<>(items);
    var worklist = new ArrayDeque<>(items);
    while (!worklist.isEmpty()) {
      var item = worklist.poll();
      if (!(item.nextSymbol() instanceof NonTerminal nt)) {
        continue;
      }
      for (var production : grammar.productionsFor(nt)) {
        var newItem = new LR0Item(production, 0);
        if (result.add(newItem)) {
          worklist.add(newItem);
        }
      }
    }
    return result;
  }

  private static LR0Automaton buildLR0Automaton(Grammar grammar, Production augmentedStart) {
    var states    = new ArrayList<LR0State>();
    var stateIndex = new HashMap<Set<LR0Item>, Integer>();

    var initial = lr0Closure(Set.of(new LR0Item(augmentedStart, 0)), grammar);
    var initialTransitions = new LinkedHashMap<Symbol, Integer>();
    states.add(new LR0State(initial, initialTransitions));
    stateIndex.put(initial, 0);

    for (var i = 0; i < states.size(); i++) {
      var state = states.get(i);

      // Group advanced items by the symbol after the dot
      var kernelsBySymbol = new LinkedHashMap<Symbol, Set<LR0Item>>();
      for (var item : state.items) {
        var sym = item.nextSymbol();
        if (sym != null) {
          kernelsBySymbol.computeIfAbsent(sym, _ -> new LinkedHashSet<>())
              .add(item.moveDotForward());
        }
      }

      for (var entry : kernelsBySymbol.entrySet()) {
        var symbol = entry.getKey();
        var items = entry.getValue();
        var next = lr0Closure(items, grammar);
        var target = stateIndex.computeIfAbsent(next, _ -> {
          var idx = states.size();
          states.add(new LR0State(next, new LinkedHashMap<>()));
          return idx;
        });
        // record the transition in the *current* state's map
        // (states.get(i) may be a different object if the list grew, but
        //  we captured the transitions-map reference above)
        state.transitions().put(symbol, target);
      }
    }
    return new LR0Automaton(states);
  }

  // -----------------------------------------------------------------------
  // Step 4: DeRemer & Pennello lookahead computation
  // -----------------------------------------------------------------------

  /// Dummy terminal used during the spontaneous-generation pass.
  /// It must be distinct from every real terminal in the grammar.
  private static final Terminal DUMMY = new Terminal("#DUMMY#");

  /// Compute the LR(1) closure of a set of LR(1) items (used only for the
  /// spontaneous-generation pass; the lookahead may be DUMMY or a real terminal).
  private static Set<LR1Item> lr1Closure(Set<LR1Item> items,
                                         Grammar grammar,
                                         Map<NonTerminal, Set<Terminal>> firstSets) {
    var result   = new LinkedHashSet<>(items);
    var worklist = new ArrayDeque<>(items);
    while (!worklist.isEmpty()) {
      var item = worklist.poll();
      if (!(item.nextSymbol() instanceof NonTerminal nonTerminal)) {
        continue;
      }
      // 'β' is everything after the non-terminal; 'a' is the item's lookahead
      var body = item.production.body();
      var rest = body.subList(item.dot + 1, body.size());
      var lookaheads = firstOfSequenceWithTerminal(rest, item.lookahead, firstSets);
      for (var production : grammar.productionsFor(nonTerminal)) {
        for (var lookahead : lookaheads) {
          var newItem = new LR1Item(production, 0, lookahead);
          if (result.add(newItem)) {
            worklist.add(newItem);
          }
        }
      }
    }
    return result;
  }

  private static Set<Terminal> firstOfSequenceWithTerminal(List<Symbol> symbols,
                                                           Terminal terminal,
                                                           Map<NonTerminal, Set<Terminal>> firstSets) {
    var result = firstOfSequence(symbols, firstSets);
    if (result.remove(Terminal.EPSILON)) {
      result.add(terminal);
    }
    return result;
  }

  /// Compute LALR(1) lookaheads using DeRemer & Pennello:
  ///   1. Determine which lookaheads are "spontaneously generated" for each
  ///      kernel item in each state.
  ///   2. Build propagation links between kernel items across states.
  ///   3. Seed EOF on the augmented-start item and propagate to a fixed point.
  ///
  /// Returns: for each state index → for each (complete) LR(0) item → set of lookahead terminals.
  private static List<Map<LR0Item, Set<Terminal>>> computeLookaheads(
      LR0Automaton lr0,
      Grammar grammar,
      Production augmentedStart,
      Map<NonTerminal, Set<Terminal>> firstSets) {

    var states = lr0.states;

    // lookaheads[stateIdx][item] = mutable set of lookahead terminals
    // We store lookaheads for every LR(0) item (not just complete ones) because
    // propagation links can target non-complete items too.
    var lookaheads = new ArrayList<Map<LR0Item, Set<Terminal>>>();
    for (var lr0State : states) {
      var map = new HashMap<LR0Item, Set<Terminal>>();
      for (var item : lr0State.items) {
        map.put(item, new HashSet<>());
      }
      lookaheads.add(map);
    }

    var propagationLinks = new ArrayList<PropagationLink>();

    // For each state and each kernel item in that state, run the
    // spontaneous-generation / propagation-link discovery pass.
    for (var stateIdx = 0; stateIdx < states.size(); stateIdx++) {
      var state = states.get(stateIdx);
      for (var kernelItem : kernelItemsOf(state, augmentedStart)) {

        // Compute LR(1) closure of {[kernelItem, DUMMY]}
        var seedSet = Set.of(new LR1Item(kernelItem.production, kernelItem.dot, DUMMY));
        var closure = lr1Closure(seedSet, grammar, firstSets);

        for (var lr1Item : closure) {
          if (lr1Item.isComplete()) {
            // A complete item [A → γ •, a]:
            //   if a ≠ DUMMY → "spontaneously generated" lookahead for the
            //                   kernel item that "owns" this complete item.
            //   if a == DUMMY → the lookahead propagates (handled via the
            //                   propagation links for "complete" items below).
            // We record this on the complete item itself in this state.
            if (lr1Item.lookahead != DUMMY) {
              var completeItem = new LR0Item(lr1Item.production, lr1Item.dot);
              var stateMap = lookaheads.get(stateIdx);
              var set = stateMap.get(completeItem);
              set.add(lr1Item.lookahead);
            }
            continue;
          }

          // A non-complete item [B → α • X β, a]:
          // After shifting 'X' the automaton moves to a successor state.
          var sym = lr1Item.nextSymbol();
          var successorIdx = state.transitions.get(sym);
          var advancedLR0 = new LR0Item(lr1Item.production, lr1Item.dot + 1);

          if (lr1Item.lookahead == DUMMY) {
            // The lookahead on kernelItem propagates to advancedLR0 in successorIdx
            var propagationLink = new PropagationLink(
                stateIdx, kernelItem,
                successorIdx, advancedLR0);
            propagationLinks.add(propagationLink);
          } else {
            // Spontaneous generation: 'a' is directly generated for advancedLR0
            var successorMap = lookaheads.get(successorIdx);
            var set = successorMap.get(advancedLR0);
            set.add(lr1Item.lookahead);
          }
        }
      }
    }

    // Seed: EOF on the augmented-start kernel item in state 0
    var augStartItem = new LR0Item(augmentedStart, 0);
    var state0Map = lookaheads.getFirst();
    var seedSet = state0Map.get(augStartItem);
    seedSet.add(Terminal.EOF);

    // Fixed-point propagation
    var changed = true;
    while (changed) {
      changed = false;
      for (var link : propagationLinks) {
        var fromMap = lookaheads.get(link.fromState);
        var toMap   = lookaheads.get(link.toState);

        var fromSet = fromMap.get(link.fromItem);
        var toSet   = toMap.get(link.toItem);
        if (toSet.addAll(fromSet)) {
          changed = true;
        }
      }
    }

    return lookaheads;
  }

  /// Returns the kernel items of an LR(0) state.
  /// Kernel items are: items with dot > 0, plus the augmented-start item [S'→•S].
  private static List<LR0Item> kernelItemsOf(LR0State state, Production augmentedStart) {
    var kernels = new ArrayList<LR0Item>();
    for (var item : state.items) {
      if (item.dot > 0 || item.production.equals(augmentedStart)) {
        kernels.add(item);
      }
    }
    return kernels;
  }

  // -----------------------------------------------------------------------
  // Step 5: Build LALR(1) action table
  // -----------------------------------------------------------------------

  // Action kinds
  private sealed interface Action permits Shift, Reduce, Accept {}
  private record Shift(int target) implements Action {}
  private record Reduce(Production production) implements Action {}
  private record Accept() implements Action {}

  private record Result(List<Action> actions, @Nullable Action winner) {}

  private static List<Map<Terminal, Result>> buildActionTable(
      LR0Automaton lr0Automaton,
      List<Map<LR0Item, Set<Terminal>>> lookaheads,
      Map<PrecedenceEntity, Precedence> precedenceMap,
      Production augmentedStart) {

    var states = lr0Automaton.states;
    var actionTable = new ArrayList<Map<Terminal, Result>>();

    for (var i = 0; i < states.size(); i++) {
      var state = states.get(i);
      var stateLookaheads = lookaheads.get(i);
      var conflictMap = new HashMap<Terminal, List<Action>>();

      for (var item : state.items) {
        if (item.isComplete()) {
          // Reduce actions: driven by computed LALR lookaheads
          var itemLookaheads = stateLookaheads.get(item);
          if (itemLookaheads == null) {
            continue;
          }
          if (item.production.equals(augmentedStart)) {
            // Accept on EOF
            for (var la : itemLookaheads) {
              conflictMap.computeIfAbsent(la, _ -> new ArrayList<>()).add(new Accept());
            }
          } else {
            for (var la : itemLookaheads) {
              conflictMap.computeIfAbsent(la, _ -> new ArrayList<>()).add(new Reduce(item.production()));
            }
          }
        } else {
          // Shift on terminal
          var sym = item.nextSymbol();
          if (sym instanceof Terminal t) {
            var target = state.transitions().get(t);
            if (target != null) {
              conflictMap.computeIfAbsent(t, _ -> new ArrayList<>()).add(new Shift(target));
            }
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
    for (var action : actions) {
      switch (action) {
        case Shift shift -> theShift = shift;
        case Reduce reduce -> reduces.add(reduce);
        case Accept accept -> { return new Result(actions, accept); }
      }
    }

    // Several reduces
    Reduce theReduce;
    switch (reduces.size()) {
      case 0 -> theReduce = null;
      case 1 -> theReduce = reduces.getFirst();
      default -> {
        theReduce = reduces.getFirst();
        var theReducePrec = precedenceMap.get(theReduce.production);
        for (int i = 1; i < reduces.size(); i++) {
          var reduce = reduces.get(i);
          var reducePrec = precedenceMap.get(reduce.production);
          if (theReducePrec != null && reducePrec != null) {
            if (theReducePrec.level() > reducePrec.level()) {
              continue;
            }
            if (theReducePrec.level() < reducePrec.level()) {
              theReduce = reduce;
              theReducePrec = reducePrec;
              continue;
            }
          }
          return new Result(actions, null);  // reduce/reduce conflict
        }
      }
    }

    // Check if it's a shift/reduce conflict?
    if (theShift != null && theReduce != null) {
      var terminalPrec = precedenceMap.get(lookahead);
      var productionPrec = precedenceMap.get(theReduce.production());
      if (terminalPrec == null || productionPrec == null) {
        return new Result(actions, null);  // shift/reduce conflict
      }
      var action = resolveShiftReduceConflict(theShift, theReduce, terminalPrec, productionPrec);
      return new Result(actions, action);
    }

    if (theShift != null) {
      return new Result(actions, theShift);
    }
    return new Result(actions, theReduce);
  }

  private static @Nullable Action resolveReduceReduceConflict(Reduce reduce1, Reduce reduce2,
                                                    Precedence reduce1Prec, Precedence reduce2Prec) {
    if (reduce1Prec.level() > reduce2Prec.level()) {
      return reduce1;
    }
    if (reduce1Prec.level() < reduce2Prec.level()) {
      return reduce2;
    }
    return null;
  }

  private static Action resolveShiftReduceConflict(Action shiftAction, Action reduceAction,
                                                   Precedence terminalPrec, Precedence productionPrec) {
    if (terminalPrec.level() > productionPrec.level()) {
      return shiftAction;
    }
    if (productionPrec.level() > terminalPrec.level()) {
      return reduceAction;
    }
    return terminalPrec.associativity() == Precedence.Associativity.LEFT
        ? reduceAction
        : shiftAction;
  }

  // -----------------------------------------------------------------------
  // Step 6: Conflict reporting
  // -----------------------------------------------------------------------

  private static final String CONFLICT_UNRESOLVED = "\uD83D\uDD25";
  private static final String CONFLICT_OVERRIDDEN = "\uD83D\uDEAB";

  private static boolean reportConflicts(List<Map<Terminal, Result>> actionTable,
                                         Consumer<? super String> errorReporter) {
    var conflicts = false;
    for (var i = 0; i < actionTable.size(); i++) {
      for (var entry : actionTable.get(i).entrySet()) {
        var lookahead = entry.getKey();
        var result = entry.getValue();
        if (result.winner() == null) {
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


  private static void printAutomaton(LR0Automaton lr0,
                                     Production augmentedStart,
                                     List<Map<LR0Item, Set<Terminal>>> lookaheads,
                                     List<Map<Terminal, Result>> actionTable,
                                     PrintStream out) {
    var states = lr0.states;

    for (var i = 0; i < states.size(); i++) {
      var state = states.get(i);
      var transitions = state.transitions;
      var stateActions = actionTable.get(i);

      // -- State header
      out.println("── State " + i + " " + "─".repeat(Math.max(0, 40 - ("State " + i).length())));

      // -- LR(0) items
      var prefixWidth = state.items.stream()
          .mapToInt(item -> item.production.head().name().length() + 3)
          .max().orElse(0);

      for (var item : state.items()) {
        var production = item.production;
        var dot = item.dot;
        var body= production.body();

        // Build "Head :" left-padded to prefixWidth
        var head = production.head().name() + " :";
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
                var suffix = switch (result.winner()) {
                  case null -> " " + CONFLICT_UNRESOLVED;
                  case Shift _ -> "";
                  case Reduce _ -> " " + CONFLICT_OVERRIDDEN;
                  case Accept _ -> throw new AssertionError();
                };
                out.printf("   goto( %-20s ) → %d%s\n", terminal.name(), target, suffix);
              }
              case NonTerminal nonTerminal ->
                  out.printf("   goto( %-20s ) → %d\n", nonTerminal.name(), target);
            }
          });

      // -- Reduce / accept actions (derived from the computed lookaheads)
      var stateLookaheads = lookaheads.get(i);
      record ReduceKey(Production production, boolean isAccept) {}
      var reduces = new LinkedHashMap<ReduceKey, List<Terminal>>();

      for (var item : state.items) {
        if (!item.isComplete()) {
          continue;
        }
        var itemLookaheads = stateLookaheads.get(item);
        if (itemLookaheads == null || itemLookaheads.isEmpty()) {
          continue;
        }
        var key = new ReduceKey(item.production(), item.production().equals(augmentedStart));
        reduces.computeIfAbsent(key, _ -> new ArrayList<>()).addAll(itemLookaheads);
      }

      reduces.entrySet().stream()
          .sorted(Map.Entry.comparingByKey(Comparator.comparing(k -> k.production().name())))
          .forEach(entry -> {
            var key = entry.getKey();
            var terminals = entry.getValue();
            if (key.isAccept()) {
              var lookaheadText = terminals.stream()
                  .map(Terminal::name)
                  .sorted()
                  .collect(joining(", "));
              out.printf("   accept()                     on [%s]\n", lookaheadText);
            } else {
              var production = key.production();
              var annotatedLookaheads = terminals.stream()
                  .sorted(Comparator.comparing(Terminal::name))
                  .map(lookahead -> {
                    var result = stateActions.get(lookahead);
                    var suffix = switch (result.winner()) {
                      case null -> " " + CONFLICT_UNRESOLVED;
                      case Reduce(Production p) when p.equals(production) -> "";
                      case Reduce _ -> " " + CONFLICT_OVERRIDDEN;  // reduce lost to shift via precedence
                      case Shift _ -> "";
                      case Accept _ -> throw new AssertionError();
                    };
                    return lookahead.name() + suffix;
                  })
                  .collect(joining(", "));
              out.printf("   reduce( %-18s ) on [%s]\n", production.name(), annotatedLookaheads);
            }
          });

      out.println();
    }
  }
}