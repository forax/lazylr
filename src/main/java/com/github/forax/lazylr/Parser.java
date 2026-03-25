package com.github.forax.lazylr;

import com.github.forax.lazylr.LRTransitionEngine.State;
import org.jspecify.annotations.Nullable;

import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
/// This class is not thread-safe. Each instance is permanently bound to the thread
/// that created it (via [#createParser] or [ParserFactory#createParser]).
/// Calling [#parse] from any other thread will throw [WrongThreadException].
/// To parse concurrently, create one [Parser] per thread using a shared [ParserFactory].
public final class Parser {
  private final Thread ownerThread;
  private final LRTransitionEngine engine;
  private final State initialState;
  private final Production startProduction;

  Parser(Thread ownerThread, LRTransitionEngine engine, State initialState, Production startProduction) {
    this.ownerThread = ownerThread;
    this.engine = engine;
    this.initialState = initialState;
    this.startProduction = startProduction;
    super();
  }

  /// Creates a lazy LR(1) parser for the given grammar.
  ///
  /// The returned parser computes states on demand as input is processed,
  /// rather than building the full parse table upfront.
  /// This means the cost of [#createParser] is low and not proportional
  /// to the full grammar.
  ///
  /// The grammar is augmented with a start production `S' -> S`,
  /// which means [ParserListener#onReduce(Production)] will fire once for
  /// that production at the end of a successful parse.
  /// Users of [Evaluator] do not need to handle this production,
  /// as it is handled automatically.
  ///
  /// If the grammar contains shift/reduce conflicts resolvable by precedence,
  /// the `precedenceMap` is used to resolve them.
  ///
  /// ### Thread ownership
  /// The returned parser is bound to the calling thread. Both this method and
  /// all later calls to [#parse(Iterator, Evaluator)] must be invoked from
  /// the same thread.
  ///
  /// @param grammar       The context-free grammar to parse.
  /// @param precedenceMap A map defining the precedence and associativity of terminals
  ///                      (e.g., operators) and productions.
  /// @return A new parser bound to the calling thread, ready to process token streams.
  /// @throws NullPointerException if grammar or precedenceMap is null.
  public static Parser createParser(Grammar grammar, Map<? extends PrecedenceEntity, ? extends Precedence> precedenceMap) {
    Objects.requireNonNull(grammar);
    Objects.requireNonNull(precedenceMap);

    var factory = ParserFactory.createFactory(grammar, precedenceMap);
    return factory.createParser();
  }

  /// A pull-based source of terminals for the parser main loop.
  ///
  /// Wraps an [Iterator] of [Terminal]s and appends a [Terminal#EOF] sentinel.
  private static abstract class Scanner {
    /// Returns the next terminal from the input, or [Terminal#EOF] if the input is exhausted.
    ///
    /// @param state the current LR parser state.
    /// @return the next terminal, never 'null'.
    abstract Terminal pollTerminal(State state);
  }

  private static Scanner wrapAndAppendEOF(Iterator<? extends Terminal> iterator) {
    if (iterator instanceof Tokenizer tokenizer) {
      return new Scanner() {
        @Override
        public Terminal pollTerminal(State state) {
          var terminal = tokenizer.pollTerminal(state);
          if (terminal != null) {
            return terminal;
          }
          return Terminal.EOF;
        }
      };
    }
    return new Scanner() {
      @Override
      public Terminal pollTerminal(State unused) {
        if (iterator.hasNext()) {
          return iterator.next();
        }
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
  /// @throws ParsingException if a syntax error occurs during parsing.
  /// @throws WrongThreadException if the method is called from a different thread
  ///         than the one the parser was created on.
  public <V extends @Nullable Object> V parse(Iterator<Terminal> input, Evaluator<V> evaluator) throws ParsingException {
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
        // We do a defensive copy to not expose the internal stack
        // This code is carefully crafted to help VM escape analysis
        // if the user code in Evaluator.evaluate does not escape the List
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
        // not really needed, there is no way to stop the parser and access the stack
        //Arrays.fill(stack, from, size, null);
        size = from;
        add(result);
      }
    }

    var listener = new EvaluatorListener();
    parse(input, listener);
    return listener.stack[listener.size - 1];
  }

  private void checkOwnerThread() {
    if (Thread.currentThread() != ownerThread) {
      throw new WrongThreadException("Parser can only be used from the thread it was created on");
    }
  }

  /// Parses a stream of tokens and notifies a listener of every transition.
  ///
  /// This is a low-level method that allows for custom handling of shift and
  /// reduce events without necessarily building a value stack.
  ///
  /// @param input    An iterator of tokens.
  /// @param listener The listener to receive parser events.
  /// @throws ParsingException if a syntax error occurs during parsing
  /// @throws WrongThreadException if the method is called from a different thread
  ///         than the one the parser was created on.
  public void parse(Iterator<Terminal> input, ParserListener listener) throws ParsingException {
    Objects.requireNonNull(input);
    Objects.requireNonNull(listener);
    checkOwnerThread();

    // We add the EOF marker to the input
    var scanner = wrapAndAppendEOF(input);

    var stack = new State[32];
    stack[0] = initialState;
    var stackSize = 1;

    var currentToken = scanner.pollTerminal(initialState);
    for (;;) {
      var currentState = stack[stackSize - 1];

      var action = engine.getAction(currentState, currentToken);
      if (action == null) {
        throw new ParsingException(errorMessage(currentToken, currentState, input));
      }

      switch (action) {
        case LRTransitionEngine.Action.Shift(var nextState) -> {
          listener.onShift(currentToken);
          if (stackSize == stack.length) {
            stack = resize(stack);
          }
          stack[stackSize++] = nextState;
          currentToken = scanner.pollTerminal(nextState);
        }
        case LRTransitionEngine.Action.Reduce(var production) -> {
          listener.onReduce(production);

          // 1. Pop N states from the stack, where N is the number of
          // symbols on the right-hand side of the rule.
          // (e.g., if E -> E + E, pop 3 states)
          stackSize -= production.body().size();

          // 2. Look at the state now on top of the stack
          var topState = stack[stackSize - 1];

          // 3. Find the GOTO transition for the NonTerminal we just "created"
          // After reducing tokens to an 'Expression', where do we go from here?
          var nextState = engine.move(topState, production.head());
          if (nextState == null) {
            return;  // Accept
          }

          // 4. Push that destination state onto the stack
          if (stackSize == stack.length) {
            stack = resize(stack);
          }
          stack[stackSize++] = nextState;
        }
      }
    }
  }

  private State[] resize(State[] stack) {
    return Arrays.copyOf(stack, stack.length << 1);
  }

  /// Generate an error message for parsing exceptions
  private static String errorMessage(Terminal terminal, State state, Iterator<Terminal> input) {
    if (input instanceof Tokenizer tokenizer) {
      if (terminal.equals(Terminal.ERROR)) {
        // lexical error
        return Tokenizer.ErrorHandler.lexingErrorMessage(tokenizer.index(), tokenizer.input());
      }
      var expected = expectedTerminals(state);
      return Tokenizer.ErrorHandler.parsingErrorMessage(terminal, expected, tokenizer.index(), tokenizer.input());
    }
    var expected = expectedTerminals(state);
    return Tokenizer.ErrorHandler.parsingErrorMessage(terminal, expected);
  }

  /// Returns the set of terminals that are syntactically valid in the given state.
  ///
  /// @param state the current LR parser state.
  /// @return the set of terminals that can legally appear next, in the input.
  static Set<Terminal> expectedTerminals(State state) {
    var expected = new HashSet<Terminal>();
    for (var item : state.items()) {
      if (item.isCompleted()) {
        expected.add(item.lookahead());
      } else {
        var next = item.getNextSymbol();
        if (next instanceof Terminal terminal) {
          expected.add(terminal);
        }
      }
    }
    return expected;
  }

  /// Returns the set of productions that have been reduced at least once
  /// across all [#parse] calls on this instance.
  ///
  /// The set grows monotonically: once a production is covered.
  /// It remains covered for the lifetime of this parser instance.
  ///
  /// @return an unmodifiable set of covered productions.
  public Set<Production> coverage() {
    return Set.copyOf(engine.reducedProductions(startProduction));
  }
}