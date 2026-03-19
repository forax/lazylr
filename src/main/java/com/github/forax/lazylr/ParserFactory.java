package com.github.forax.lazylr;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// This class is responsible for creating parser instances based on a given grammar and precedence map.
///
/// Unlike [Parser#createParser(Grammar, Map)], this class precomputes the necessary data structures
/// (e.g., FIRST sets, augmented precedenceMap) to avoid recomputing them on each parse.
///
/// This class is thread-safe and can be used to create multiple parser instances concurrently.
public final class ParserFactory {
  private final Grammar grammar;
  private final Map<PrecedenceEntity, Precedence> fullPrecedenceMap;
  private final Map<Symbol, Set<Terminal>> firstSets;

  private ParserFactory(Grammar grammar, Map<PrecedenceEntity, Precedence> fullPrecedenceMap, Map<Symbol, Set<Terminal>> firstSets) {
    this.grammar = grammar;
    this.fullPrecedenceMap = fullPrecedenceMap;
    this.firstSets = firstSets;
    super();
  }

  /// Create a shareable instance of ParserFactory for the given grammar and precedence map.
  ///
  /// @param grammar the grammar
  /// @param precedenceMap the precedence map
  /// @return a shareable ParserFactory instance
  /// @throws NullPointerException if grammar or precedenceMap is null
  public static ParserFactory createFactory(Grammar grammar, Map<? extends PrecedenceEntity, ? extends Precedence> precedenceMap) {
    Objects.requireNonNull(grammar);
    Objects.requireNonNull(precedenceMap);

    // Complete the precedence map by computing the precedence of the production if necessary
    var fullPrecedenceMap = Precedence.complete(grammar, precedenceMap);

    // Compute FIRST sets
    var firstSets = LRAlgorithm.computeFirstSets(grammar);

    return new ParserFactory(grammar, fullPrecedenceMap, firstSets);
  }

  // Create a new parser instance.
  // @return a new parser instance (not thread-safe).
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
    return new Parser(engine, initialState, startProd);
  }
}
