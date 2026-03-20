package com.github.forax.lazylr;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// A factory that amortizes the cost of grammar analysis across
/// multiple [Parser] instances.
///
/// This class is **immutable and thread-safe**: the factory can be shared freely
/// across threads. Each [Parser] returned by [#createParser()] is independent and
/// bound to the thread that called [#createParser()].
///
/// ```java
/// // Shared across threads — create once
/// private static final FACTORY = ParserFactory.createFactory(GRAMMAR, PRECEDENCE_MAP);
///
/// // Per-thread — call createParser() on the thread that will parse the input
/// var parser = FACTORY.createParser();
/// var result = parser.parse(input, EVALUATOR);
/// ```
public final class ParserFactory {
  private final Grammar grammar;
  private final Map<PrecedenceEntity, Precedence> fullPrecedenceMap;
  private final Map<Symbol, Set<Terminal>> firstSets;

  private ParserFactory(Grammar grammar,
                        Map<PrecedenceEntity, Precedence> fullPrecedenceMap,
                        Map<Symbol, Set<Terminal>> firstSets) {
    this.grammar = grammar;
    this.fullPrecedenceMap = fullPrecedenceMap;
    this.firstSets = firstSets;
    super();
  }

  /// Creates a new ParserFactory for the given grammar and precedence map.
  ///
  /// @param grammar       the context-free grammar; must not be {@code null}.
  /// @param precedenceMap operator precedence and associativity; must not be {@code null}.
  /// @return a shared, immutable factory ready to produce parser instances.
  /// @throws NullPointerException if either argument is {@code null}.
  public static ParserFactory createFactory(Grammar grammar,
                                            Map<? extends PrecedenceEntity, ? extends Precedence> precedenceMap) {
    Objects.requireNonNull(grammar);
    Objects.requireNonNull(precedenceMap);

    // Complete the precedence map by computing the precedence of the production if necessary
    var fullPrecedenceMap = Precedence.complete(grammar, precedenceMap);

    // Compute FIRST sets
    var firstSets = LRAlgorithm.computeFirstSets(grammar);

    return new ParserFactory(grammar, fullPrecedenceMap, firstSets);
  }

  /// Creates a lazy LR(1) parser for the given grammar.
  ///
  /// The returned parser computes states on demand as input is processed,
  /// rather than building the full parse table upfront.
  /// This means the cost of [#createParser] is low and not proportional
  /// to the full grammar.
  ///
  /// The grammar is augmented with a start production `S' -> S`,
  /// which means [ParserListener#onReduce] will fire once for that production
  /// at the end of a successful parse. Users of [Evaluator] do not need to
  /// handle this production, as it is handled automatically.
  ///
  /// If the grammar contains shift/reduce conflicts resolvable by precedence,
  /// the `precedenceMap` is used to resolve them.
  ///
  /// ### Thread ownership
  /// The returned parser is bound to the calling thread. Both this method and
  /// all later calls to [Parser#parse(Iterator, Evaluator)] must be invoked
  /// from the same thread.
  ///
  /// @return a new parser instance bound to the calling thread.
  public Parser createParser() {
    // Prepare the Initial State (S' -> . S $)
    // We create an "Augmented" production to represent the entry point
    var augmentedStart = new NonTerminal(grammar.startSymbol().name() + "'");
    var startProd = new Production(augmentedStart, List.of(grammar.startSymbol()));

    // Initial Item: [S' -> . S, { $ }]
    var startItem = new LRTransitionEngine.Item(startProd, 0, Terminal.EOF);

    // Initialize the LALR Builder and Transition Engine
    var algorithm = new LRAlgorithm(grammar, firstSets);
    var engine = new LRTransitionEngine(algorithm, fullPrecedenceMap);

    // Compute the Closure of the initial item to create State 0
    var initialItems = algorithm.computeClosure(Set.of(startItem));
    var initialState = new LRTransitionEngine.State(initialItems);

    // Create the Parser
    return new Parser(Thread.currentThread(), engine, initialState, startProd);
  }
}
