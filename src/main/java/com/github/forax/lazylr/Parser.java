package com.github.forax.lazylr;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class Parser {
  private final LRTransitionEngine engine;
  private final State initialState;
  private final Production startProduction;
  private final Map<PrecedenceEntity, Precedence> precedenceMap;

  private Parser(LRTransitionEngine engine, State initialState, Production startProduction, Map<PrecedenceEntity, Precedence> precedenceMap) {
    this.engine = engine;
    this.initialState = initialState;
    this.startProduction = startProduction;
    this.precedenceMap = precedenceMap;
    super();
  }

  public static Parser createParser(Grammar grammar, Map<PrecedenceEntity, Precedence> precedenceMap) {
    Objects.requireNonNull(grammar);
    Objects.requireNonNull(precedenceMap);

    // Complete the precedence map by computing the precedence of the production if necessary
    precedenceMap = complete(grammar, precedenceMap);

    // Compute FIRST and FOLLOW sets
    var firstSets = LRAlgorithm.computeFirstSets(grammar);
    //var followSets = LRAlgorithm.computeFollowSets(grammar, firstSets);

    // Prepare the Initial State (S' -> . S $)
    // We create an "Augmented" production to represent the entry point
    var augmentedStart = new NonTerminal(grammar.startSymbol().name() + "'");
    var startProd = new Production(augmentedStart, List.of(grammar.startSymbol()));

    // Initial Item: [S' -> . S, { $ }]
    var startItem = new Item(startProd, 0, Set.of(Terminal.EOF));

    // Initialize the LALR Builder and Transition Engine
    var algorithm = new LRAlgorithm(grammar, firstSets);
    var engine = new LRTransitionEngine(algorithm);

    // Compute the Closure of the initial item to create State 0
    var initialItems = algorithm.computeClosure(Set.of(startItem));
    var initialState = new State(initialItems);

    // Create the Parser
    return new Parser(engine, initialState, startProd, precedenceMap);
  }

  private static Precedence computePrecedence(Production production, Map<PrecedenceEntity, Precedence> precedenceMap) {
    // inherits from the precedence of the last terminal of the production
    return production.body().reversed().stream()
        .flatMap(s -> switch(s) {
          case Terminal t -> Stream.of(t);
          case NonTerminal _ -> null;
        })
        .findFirst()
        .flatMap(terminal -> Optional.ofNullable(precedenceMap.get(terminal)))
        .orElseGet(() -> new Precedence(0, Precedence.Associativity.LEFT));
  }

  private static Map<PrecedenceEntity, Precedence> complete(Grammar grammar, Map<PrecedenceEntity, Precedence> precedenceMap) {
    var newPrecedenceMap = new HashMap<>(precedenceMap);
    for(var production : grammar.productions()) {
      newPrecedenceMap.computeIfAbsent(production, _ -> computePrecedence(production, newPrecedenceMap));
    }
    return newPrecedenceMap;
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

  private static Iterator<Terminal> wrapAndAppendEOF(Iterator<? extends Terminal> iterator) {
    return new Iterator<>() {
      private boolean eofSeen;

      @Override
      public boolean hasNext() {
        return iterator.hasNext() || !eofSeen;
      }
      @Override
      public Terminal next() {
        if (iterator.hasNext()) {
          return iterator.next();
        }
        if (eofSeen) {
          throw new NoSuchElementException();
        }
        eofSeen = true;
        return Terminal.EOF;
      }
    };
  }

  public <V> V parse(Iterator<Terminal> input, Evaluator<V> evaluator) {
    Objects.requireNonNull(input);
    Objects.requireNonNull(evaluator);

    var listener =
        new ParserListener() {
          // Use ArrayList because null is allowed as a value + subList
          private final ArrayList<V> stack = new ArrayList<>();

          @Override
          public void onShift(Terminal token) {
            stack.add(evaluator.evaluate(token));
          }

          @Override
          public void onReduce(Production production) {
            if (production == startProduction) {
              return;
            }
            var subList = stack.subList(stack.size() - production.body().size(), stack.size());
            var result = evaluator.evaluate(production, Collections.unmodifiableList(new ArrayList<>(subList)));
            subList.clear();
            stack.add(result);
          }
        };
    parse(input, listener);
    return listener.stack.removeLast();
  }

  public void parse(Iterator<Terminal> input, ParserListener listener) {
    Objects.requireNonNull(input);
    Objects.requireNonNull(listener);

    // We add the EOF marker to the input
    var tokens = wrapAndAppendEOF(input);

    var stack = new ArrayDeque<State>();
    stack.push(initialState);

    var currentToken = tokens.next();
    for(;;) {
      var currentState = stack.peek();

      // Find all possible Reductions
      var _currentToken = currentToken;
      var possibleReductions = currentState.items().stream()
          .filter(Item::isCompleted)
          .filter(item -> item.lookaheads().contains(_currentToken))
          .toList();

      var bestCandidate = bestCandidateForReduction(possibleReductions);

      // Find a possible Shift
      var shiftState = engine.move(currentState, currentToken);

      // Conflict resolution
      if (bestCandidate != null && shiftState != null) {
        // Shift/Reduce Conflict!
        // Default rule: Shift wins, UNLESS we have precedence rules.
        if (shouldReduce(bestCandidate.production(), currentToken)) {
          if (executeReduction(stack, bestCandidate.production(), listener)) {
            return;
          }
          continue;
        } else {
          executeShift(stack, currentToken, shiftState, listener);
          currentToken = tokens.next();
          continue;
        }
      }

      if (bestCandidate != null) {
        if (executeReduction(stack, bestCandidate.production(), listener)) {
          return;
        }
        continue;
      }
      if (shiftState != null) {
        executeShift(stack, currentToken, shiftState, listener);
        currentToken = tokens.next();
        continue;
      }

      // ERROR
      throw new RuntimeException("Syntax Error at token: " + currentToken.name());
    }
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
   * Pushes the token's destination state onto the stack and
   * consumes the token from the input.
   */
  private void executeShift(ArrayDeque<State> stack, Terminal token, State nextState, ParserListener listener) {
    listener.onShift(token);
    stack.push(nextState);
  }

  /**
   * Shrinks the stack and then performs a 'GOTO' transition.
   * Returns true if the reduction leads to an accept state, false otherwise.
   */
  private boolean executeReduction(ArrayDeque<State> stack, Production production, ParserListener listener) {
    listener.onReduce(production);

    // 1. Pop N states from the stack, where N is the number of
    // symbols on the right-hand side of the rule.
    // (e.g., if E -> E + E, pop 3 states)
    for (var i = 0; i < production.body().size(); i++) {
      if (stack.isEmpty()) {
        throw new RuntimeException("Stack underflow during reduction of " + production);
      }
      stack.pop();
    }

    // 2. Look at the state now on top of the stack
    var topState = stack.peek();

    // 3. Find the GOTO transition for the NonTerminal we just "created"
    // After reducing tokens to an 'Expression', where do we go from here?
    var nextState = engine.move(topState, production.head());

    if (nextState == null) {
      if (production == startProduction) {
        return true;  // Accept
      }

      throw new RuntimeException("GOTO Error: No transition from " +
          topState + " on symbol " + production.head().name());
    }

    // 4. Push that destination state onto the stack
    stack.push(nextState);

    return false;
  }
}