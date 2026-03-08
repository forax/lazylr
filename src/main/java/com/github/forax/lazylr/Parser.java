package com.github.forax.lazylr;

import com.github.forax.lazylr.LRTransitionEngine.Item;
import com.github.forax.lazylr.LRTransitionEngine.State;

import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/// The primary engine for performing LR(1) parsing.
///
/// The parser uses a bottom-up approach to recognize languages defined by a [Grammar].
///
/// ### Evaluation
/// The parser supports two ways to process the input:
/// 1. **Functional**: Using [#parse(Iterator, Evaluator)] to directly produce a result.
/// 2. **Event-driven**: Using [#parse(Iterator, ParserListener)] to observe transitions
///    as they occur.
///
/// This class is not thread-safe and should not be shared between multiple threads.
public final class Parser {
  private final LRTransitionEngine engine;
  private final State initialState;
  private final Production startProduction;

  private Parser(LRTransitionEngine engine, State initialState, Production startProduction) {
    this.engine = engine;
    this.initialState = initialState;
    this.startProduction = startProduction;
    super();
  }

  /// Creates an LR parser.
  ///
  /// This factory method augments the grammar with a unique start production
  /// (`S' -> S $`) and then create a parser on the grammar.
  ///
  /// @param grammar       The context-free grammar to parse.
  /// @param precedenceMap A map defining priority and associativity for operators/rules.
  /// @return A parser ready to process token streams.
  /// @throws NullPointerException if grammar or precedenceMap is null.
  public static Parser createParser(Grammar grammar, Map<? extends PrecedenceEntity, ? extends Precedence> precedenceMap) {
    Objects.requireNonNull(grammar);
    Objects.requireNonNull(precedenceMap);

    // Complete the precedence map by computing the precedence of the production if necessary
    var fullPrecedenceMap = complete(grammar, precedenceMap);

    // Compute FIRST sets
    var firstSets = LRAlgorithm.computeFirstSets(grammar);

    // Prepare the Initial State (S' -> . S $)
    // We create an "Augmented" production to represent the entry point
    var augmentedStart = new NonTerminal(grammar.startSymbol().name() + "'");
    var startProd = new Production(augmentedStart, List.of(grammar.startSymbol()));

    // Initial Item: [S' -> . S, { $ }]
    var startItem = new Item(startProd, 0, Terminal.EOF);

    // Initialize the LALR Builder and Transition Engine
    var algorithm = new LRAlgorithm(grammar, firstSets);
    var engine = new LRTransitionEngine(algorithm, fullPrecedenceMap);

    // Compute the Closure of the initial item to create State 0
    var initialItems = algorithm.computeClosure(Set.of(startItem));
    var initialState = new State(initialItems);

    // Create the Parser
    return new Parser(engine, initialState, startProd);
  }

  private static Precedence computePrecedence(Production production, Map<PrecedenceEntity, Precedence> precedenceMap) {
    // inherits from the precedence of the last terminal of the production
    loop:
    for (var symbol : production.body().reversed()) {
      switch (symbol) {
        case Terminal t -> {
          var precedence = precedenceMap.get(t);
          if (precedence != null) {
            return precedence;
          }
          break loop;
        }
        case NonTerminal _ -> {}
      }
    }
    return new Precedence(0, Precedence.Associativity.LEFT);
  }

  static Map<PrecedenceEntity, Precedence> complete(Grammar grammar, Map<? extends PrecedenceEntity, ? extends Precedence> precedenceMap) {
    var newPrecedenceMap = new HashMap<PrecedenceEntity, Precedence>(precedenceMap);
    for (var production : grammar.productions()) {
      newPrecedenceMap.computeIfAbsent(production, _ -> computePrecedence(production, newPrecedenceMap));
    }
    return newPrecedenceMap;
  }

  private static Iterator<Terminal> wrapAndAppendEOF(Iterator<? extends Terminal> iterator) {
    // if it's a Tokenized (Iterator + better error reporting) wrap to a Tokenizer
    if (iterator instanceof Tokenizer tokenizer) {
      return new Tokenizer() {
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

        @Override
        public int index() {
          return tokenizer.index();
        }

        @Override
        public CharSequence input() {
          return tokenizer.input();
        }
      };
    }
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

  /// Parses a stream of tokens and evaluates them into a single result.
  ///
  /// This method manages an internal value stack. On a shift, the [Terminal] is
  /// evaluated; on a reduction, the [Production] and its collected arguments
  /// are passed to the [Evaluator#evaluate(Production, List)].
  ///
  /// @param <V>       The type of the final result (e.g., an AST `Node`).
  /// @param input     An iterator of tokens, typically provided by a [Lexer].
  /// @param evaluator The strategy for building results from tokens and rules.
  /// @return The final evaluated result of the start production.
  /// @throws ParsingException if a syntax error occurs during parsing
  public <V> V parse(Iterator<Terminal> input, Evaluator<V> evaluator) throws ParsingException {
    Objects.requireNonNull(input);
    Objects.requireNonNull(evaluator);

    final class EvaluatorListener implements ParserListener {
      private V[] stack;   // null is allowed as a value
      private int size;

      private EvaluatorListener() {
        @SuppressWarnings("unchecked")
        var stack = (V[]) new Object[32];   // big enough for most small grammars
        this.stack = stack;
        super();
      }

      private void add(V value) {
        if (size == stack.length) {
          resize();
        }
        stack[size++] = value;
      }

      private void resize() {
        stack = Arrays.copyOf(stack, stack.length << 1);
      }

      @Override
      public void onShift(Terminal token) {
        add(evaluator.evaluate(token));
      }

      @Override
      public void onReduce(Production production) {
        if (production == startProduction) {
          return;
        }
        var from = size - production.body().size();
        // the VM should be able to optimize this copy if it does not escape
        var copy = Arrays.copyOfRange(stack, from, size);
        var result = evaluator.evaluate(production, new AbstractList<>() {
          @Override
          public int size() {
            return copy.length;
          }

          @Override
          public V get(int index) {
            return copy[index];
          }
        });
        // not really needed given there is no way to stop the parser and access the stack
        //Arrays.fill(stack, from, size, null);
        size = from;
        add(result);
      }
    }

    var listener = new EvaluatorListener();
    parse(input, listener);
    return listener.stack[listener.size - 1];
  }

  /// Parses a stream of tokens and notifies a listener of every transition.
  ///
  /// This is a low-level method that allows for custom handling of shift and
  /// reduce events without necessarily building a value stack.
  ///
  /// @param input    An iterator of tokens.
  /// @param listener The listener to receive parser events.
  /// @throws ParsingException if a syntax error occurs during parsing
  public void parse(Iterator<Terminal> input, ParserListener listener) throws ParsingException {
    Objects.requireNonNull(input);
    Objects.requireNonNull(listener);

    // We add the EOF marker to the input
    var tokens = wrapAndAppendEOF(input);

    var stack = new ArrayDeque<State>();
    stack.push(initialState);

    var currentToken = tokens.next();
    for (;;) {
      var currentState = stack.peek();

      var action = engine.getAction(currentState, currentToken);
      if (action == null) {
        throw new ParsingException(errorMessage(currentToken, input));
      }

      switch (action) {
        case LRTransitionEngine.Action.Shift(var nextState) -> {
          executeShift(stack, currentToken, nextState, listener);
          currentToken = tokens.next();
        }
        case LRTransitionEngine.Action.Reduce(var production) -> {
          if (executeReduction(stack, production, listener)) {
            return;
          }
        }
      }
    }
  }

  /// Generate an error message for parsing exceptions
  private static String errorMessage(Terminal terminal, Iterator<Terminal> input) {
    if (input instanceof Tokenizer tokenizer) {
      if (terminal.equals(Terminal.ERROR)) {
        // lexical error
        return Tokenizer.ErrorHandler.lexingErrorMessage(tokenizer.index(), tokenizer.input());
      }
      return Tokenizer.ErrorHandler.parsingErrorMessage(terminal, tokenizer.index(), tokenizer.input());
    }
    return Tokenizer.ErrorHandler.parsingErrorMessage(terminal);
  }

  /// Pushes the token's destination state onto the stack and
  /// consumes the token from the input.
  private static void executeShift(ArrayDeque<State> stack, Terminal token, State nextState, ParserListener listener) {
    listener.onShift(token);
    stack.push(nextState);
  }

  /// Shrinks the stack and then performs a 'GOTO' transition.
  /// Returns true if the reduction leads to an Accept state, false otherwise.
  private boolean executeReduction(ArrayDeque<State> stack, Production production, ParserListener listener) {
    listener.onReduce(production);

    // 1. Pop N states from the stack, where N is the number of
    // symbols on the right-hand side of the rule.
    // (e.g., if E -> E + E, pop 3 states)
    for (var i = 0; i < production.body().size(); i++) {
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

      // Trying to reduce a non-terminal that is not reachable from the start symbol
      throw new AssertionError("can not reduce " + production +
          " because there is no GOTO transition for " + production.head());
    }

    // 4. Push that destination state onto the stack
    stack.push(nextState);

    return false;
  }
}