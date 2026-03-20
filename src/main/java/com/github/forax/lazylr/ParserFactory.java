package com.github.forax.lazylr;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// A factory that amortizes the cost of grammar analysis across multiple [Parser] instances.
///
/// [Parser#createParser(Grammar, Map)] performs upfront analysis work on every call.
/// When many parsers are needed for the same grammar (e.g., one per thread in a
/// concurrent application), creating a {@code ParserFactory} once and calling
/// [#createParser()] repeatedly avoids that repeated work.
///
/// This class is immutable and thread-safe. Each [Parser] returned by [#createParser()]
/// is independent and not thread-safe.
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

  /// Creates a new [Parser] instance for this factory's grammar.
  ///
  /// Each call returns an independent parser that is not thread-safe
  /// and must not be shared between threads.
  ///
  /// @return a new parser instance.
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
