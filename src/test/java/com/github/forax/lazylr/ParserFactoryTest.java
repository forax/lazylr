package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public final class ParserFactoryTest {

  // -- Concurrency infrastructure

  private record Result<V>(V value, Throwable cause) {}

  private static <V> List<Result<V>> runAll(List<? extends Callable<? extends V>> callables,
                                            Supplier<? extends ExecutorService> executorSupplier)
      throws InterruptedException {
    try (var exec = executorSupplier.get()) {
      var futures = new ArrayList<Future<V>>();
      var barrier = new CyclicBarrier(callables.size());

      for (var callable : callables) {
        futures.add(exec.submit(() -> {
          try {
            barrier.await();
          } catch (InterruptedException | BrokenBarrierException e) {
            throw new AssertionError(e);
          }
          return callable.call();
        }));
      }

      var results = new ArrayList<Result<V>>(callables.size());
      for (var future : futures) {
        try {
          var value = future.get();
          results.add(new Result<>(value, null));
        } catch (ExecutionException e) {
          results.add(new Result<>(null, e.getCause()));
        }
      }
      return List.copyOf(results);
    }
  }

  // Asserts that every result completed without a throwable.
  private static void assertNoFailures(List<? extends Result<?>> results) {
    var failures = results.stream()
        .map(Result::cause)
        .filter(Objects::nonNull)
        .toList();
    assertTrue(failures.isEmpty(), () -> "unexpected failures: " + failures);
  }

  // -- Grammar / evaluator helpers

  // E -> E '+' E | E '*' E | num   with left-associative +, * and * > +
  private static MetaGrammar arithmeticMetaGrammar() {
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var num  = new Terminal("num");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, E)),
        new Production(E, List.of(E, mul,  E)),
        new Production(E, List.of(num))
    ));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        mul,  new Precedence(20, Precedence.Associativity.LEFT)
    );
    return new MetaGrammar(List.of(), precedence, grammar);
  }

  private static Evaluator<Integer> arithmeticEvaluator() {
    return new Evaluator<>() {
      @Override
      public Integer evaluate(Terminal terminal) {
        return switch (terminal.name()) {
          case "num" -> Integer.parseInt(terminal.value());
          default    -> 0;
        };
      }

      @Override
      public Integer evaluate(Production production, List<Integer> args) {
        return switch (production.name()) {
          case "E : num"   -> args.get(0);
          case "E : E + E" -> args.get(0) + args.get(2);
          case "E : E * E" -> args.get(0) * args.get(2);
          default -> throw new AssertionError("unknown production: " + production.name());
        };
      }
    };
  }

  // Value -> Object | Array | STRING | NUMBER | true | false | null  (minimal JSON)
  private static MetaGrammar jsonMetaGrammar() {
    var Value    = new NonTerminal("Value");
    var Object_  = new NonTerminal("Object");
    var Array    = new NonTerminal("Array");
    var Members  = new NonTerminal("Members");
    var Elements = new NonTerminal("Elements");
    var Pair     = new NonTerminal("Pair");

    var objOpen  = new Terminal("{");
    var objClose = new Terminal("}");
    var arrOpen  = new Terminal("[");
    var arrClose = new Terminal("]");
    var comma    = new Terminal(",");
    var colon    = new Terminal(":");
    var string   = new Terminal("STRING");
    var number   = new Terminal("NUMBER");
    var boolTrue = new Terminal("true");
    var boolFalse= new Terminal("false");
    var nullVal  = new Terminal("null");

    var grammar = new Grammar(Value, List.of(
        new Production(Value,    List.of(Object_)),
        new Production(Value,    List.of(Array)),
        new Production(Value,    List.of(string)),
        new Production(Value,    List.of(number)),
        new Production(Value,    List.of(boolTrue)),
        new Production(Value,    List.of(boolFalse)),
        new Production(Value,    List.of(nullVal)),
        new Production(Object_,  List.of(objOpen, objClose)),
        new Production(Object_,  List.of(objOpen, Members, objClose)),
        new Production(Pair,     List.of(string, colon, Value)),
        new Production(Members,  List.of(Pair)),
        new Production(Members,  List.of(Members, comma, Pair)),
        new Production(Array,    List.of(arrOpen, arrClose)),
        new Production(Array,    List.of(arrOpen, Elements, arrClose)),
        new Production(Elements, List.of(Value)),
        new Production(Elements, List.of(Elements, comma, Value))
    ));
    return new MetaGrammar(List.of(), Map.of(), grammar);
  }

  private static final ParserListener NOOP_LISTENER = new ParserListener() {
    @Override public void onShift(Terminal token) {}
    @Override public void onReduce(Production production) {}
  };


  // -- Tests

  @Test
  public void createParserReturnsDistinctInstances() {
    var mg      = arithmeticMetaGrammar();
    var factory = ParserFactory.createFactory(mg.grammar(), mg.precedenceMap());
    assertNotSame(factory.createParser(), factory.createParser());
  }

  @Test
  public void createParserFromDifferentThreads() throws InterruptedException {
    var mg      = arithmeticMetaGrammar();
    var factory = ParserFactory.createFactory(mg.grammar(), mg.precedenceMap());

    var callables = List.<Callable<Parser>>of(
        factory::createParser,
        factory::createParser
    );
    var results = runAll(callables, () -> Executors.newFixedThreadPool(2));

    assertNoFailures(results);
    results.forEach(r -> assertNotNull(r.value()));
  }

  @Test
  public void parserBoundToCreatingPlateformThread() throws Exception {
    var mg = arithmeticMetaGrammar();
    var factory = ParserFactory.createFactory(mg.grammar(), mg.precedenceMap());
    var parser = factory.createParser();

    var input = List.of(
        new Terminal("num", "1"), new Terminal("+", "+"), new Terminal("num", "2"));

    // parse() must be called from the thread that owns parser — a different
    // thread must receive WrongThreadException
    var result = runAll(List.of(
        (Callable<Object>) () -> {
          parser.parse(input.iterator(), arithmeticEvaluator());
          return fail("expected WrongThreadException");
        }),
        () -> Executors.newFixedThreadPool(1));

    assertInstanceOf(WrongThreadException.class, result.getFirst().cause());
  }

  @Test
  public void parserCreatedOnThreadCanParseOnThatThread() {
    var mg = arithmeticMetaGrammar();
    var factory = ParserFactory.createFactory(mg.grammar(), mg.precedenceMap());
    var parser = factory.createParser();
    // 2 + 3 * 4 = 14  (multiplication binds tighter)
    var input = List.of(
        new Terminal("num", "2"), new Terminal("+", "+"),
        new Terminal("num", "3"), new Terminal("*", "*"),
        new Terminal("num", "4"));
    assertEquals(14, parser.parse(input.iterator(), arithmeticEvaluator()));
  }

  @Test
  public void manyThreadsShareFactoryConcurrently() throws InterruptedException {
    var mg = arithmeticMetaGrammar();
    var factory = ParserFactory.createFactory(mg.grammar(), mg.precedenceMap());

    // 2 + 3 * 4 = 14
    var input = List.of(
        new Terminal("num", "2"), new Terminal("+", "+"),
        new Terminal("num", "3"), new Terminal("*", "*"),
        new Terminal("num", "4"));

    var threadCount = 20;
    var parseCount  = 50;

    // Each callable creates one parser and reuses it for parseCount parses.
    var callables = IntStream.range(0, threadCount)
        .mapToObj(_ -> (Callable<Integer>) () -> {
          var parser = factory.createParser();
          var last   = 0;
          for (var i = 0; i < parseCount; i++) {
            last = parser.parse(input.iterator(), arithmeticEvaluator());
            if (last != 14) {
              throw new AssertionError("expected 14, got " + last);
            }
          }
          return last;
        })
        .toList();

    var results = runAll(callables, () -> Executors.newFixedThreadPool(threadCount));

    assertNoFailures(results);
    assertEquals(threadCount, results.size());
    results.forEach(r -> assertEquals(14, r.value()));
  }

  @Test
  public void eachThreadGetsIndependentParser() throws InterruptedException {
    var mg = arithmeticMetaGrammar();
    var factory = ParserFactory.createFactory(mg.grammar(), mg.precedenceMap());

    // Even callables: 2 + 3 * 4 = 14,  odd callables: 10 * 3 + 2 = 32
    var evenInput = List.of(
        new Terminal("num", "2"), new Terminal("+", "+"),
        new Terminal("num", "3"), new Terminal("*", "*"),
        new Terminal("num", "4"));
    var oddInput = List.of(
        new Terminal("num", "10"), new Terminal("*", "*"),
        new Terminal("num", "3"),  new Terminal("+", "+"),
        new Terminal("num", "2"));

    var threadCount = 10;
    var callables = IntStream.range(0, threadCount)
        .mapToObj(i -> (Callable<Integer>) () -> {
          var input    = i % 2 == 0 ? evenInput : oddInput;
          var expected = i % 2 == 0 ? 14 : 32;

          var parser = factory.createParser();
          for (var iter = 0; iter < 20; iter++) {
            var result = parser.parse(input.iterator(), arithmeticEvaluator());
            if (result != expected) {
              throw new AssertionError("expected " + expected + ", got " + result);
            }
          }
          return expected;
         })
        .toList();

    var results = runAll(callables, () -> Executors.newFixedThreadPool(threadCount));

    assertNoFailures(results);
    for (var i = 0; i < results.size(); i++) {
      assertEquals(i % 2 == 0 ? 14 : 32, results.get(i).value());
    }
  }

  @Test
  public void createFactoryConcurrently() throws InterruptedException {
    var mg = arithmeticMetaGrammar();

    var input = List.of(
        new Terminal("num", "1"), new Terminal("+", "+"), new Terminal("num", "2"));

    var threadCount = 10;
    var callables   = IntStream.range(0, threadCount)
        .mapToObj(i -> (Callable<Integer>) () -> {
          var localFactory = ParserFactory.createFactory(mg.grammar(), mg.precedenceMap());
          return localFactory.createParser().parse(input.iterator(), arithmeticEvaluator());
        })
        .toList();

    var results = runAll(callables, () -> Executors.newFixedThreadPool(threadCount));

    assertNoFailures(results);
    results.forEach(r -> assertEquals(3, r.value()));
  }

  @Test
  public void parserReusedAcrossMultipleParsesOnSameThread() {
    var mg      = arithmeticMetaGrammar();
    var factory = ParserFactory.createFactory(mg.grammar(), mg.precedenceMap());
    var parser  = factory.createParser();

    // 2 + 3 * 4 = 14,  10 * 3 + 2 = 32,  1 + 2 * 3 = 7
    var input14 = List.of(
        new Terminal("num", "2"), new Terminal("+", "+"),
        new Terminal("num", "3"), new Terminal("*", "*"),
        new Terminal("num", "4"));
    var input32 = List.of(
        new Terminal("num", "10"), new Terminal("*", "*"),
        new Terminal("num", "3"),  new Terminal("+", "+"),
        new Terminal("num", "2"));
    var input7 = List.of(
        new Terminal("num", "1"), new Terminal("+", "+"),
        new Terminal("num", "2"), new Terminal("*", "*"),
        new Terminal("num", "3"));

    for (var i = 0; i < 100; i++) {
      assertEquals(14, parser.parse(input14.iterator(), arithmeticEvaluator()));
      assertEquals(32, parser.parse(input32.iterator(), arithmeticEvaluator()));
      assertEquals(7,  parser.parse(input7.iterator(),  arithmeticEvaluator()));
    }
  }

  @Test
  public void parseErrorOnOneThreadDoesNotCorruptOthers() throws InterruptedException {
    var mg = arithmeticMetaGrammar();
    var factory = ParserFactory.createFactory(mg.grammar(), mg.precedenceMap());

    var goodInput = List.of(
        new Terminal("num", "2"), new Terminal("+", "+"),
        new Terminal("num", "3"), new Terminal("*", "*"),
        new Terminal("num", "4"));
    var recoveryInput = List.of(
        new Terminal("num", "3"), new Terminal("+", "+"), new Terminal("num", "4"));

    // Callable 0: triggers a ParsingException then parses correctly afterwards.
    var faultyCallable = (Callable<Integer>) () -> {
      var parser = factory.createParser();
      try {
        // "+" alone is not a valid expression
        var input = List.of(new Terminal("+", "+")).iterator();
        parser.parse(input, arithmeticEvaluator());
        throw new AssertionError("expected ParsingException");
      } catch (ParsingException expected) {
        // correct — fall through to recovery parse
      }
      return parser.parse(recoveryInput.iterator(), arithmeticEvaluator());
    };

    // Callables 1-10: normal parsing throughout.
    var callables = new ArrayList<Callable<Integer>>();
    callables.add(faultyCallable);
    for (var i = 1; i < 10; i++) {
      callables.add(() -> {
        var parser = factory.createParser();
        return parser.parse(goodInput.iterator(), arithmeticEvaluator());
      });
    }

    var results = runAll(callables, () -> Executors.newFixedThreadPool(callables.size()));

    assertNoFailures(results);
    assertEquals(7,  results.getFirst().value(), "faulty callable should recover to 3+4=7");
    for (var i = 1; i < 10; i++) {
      assertEquals(14, results.get(i).value());
    }
  }

  @Test
  public void virtualThreadsCanUseFactory() throws InterruptedException {
    var mg      = arithmeticMetaGrammar();
    var factory = ParserFactory.createFactory(mg.grammar(), mg.precedenceMap());

    // 2 + 3 * 4 = 14
    var input = List.of(
        new Terminal("num", "2"), new Terminal("+", "+"),
        new Terminal("num", "3"), new Terminal("*", "*"),
        new Terminal("num", "4"));

    var threadCount = 50;
    var callables = IntStream.range(0, threadCount)
        .mapToObj(_ -> (Callable<Integer>) () -> {
          var parser = factory.createParser();
          return parser.parse(input.iterator(), arithmeticEvaluator());
        })
        .toList();

    var results = runAll(callables, Executors::newVirtualThreadPerTaskExecutor);

    assertNoFailures(results);
    results.forEach(r -> assertEquals(14, r.value()));
  }

  @Test
  public void parserBoundToCreatingVirtualThread() throws Exception {
    var mg = arithmeticMetaGrammar();
    var factory = ParserFactory.createFactory(mg.grammar(), mg.precedenceMap());
    var parser = factory.createParser();

    var input = List.of(
        new Terminal("num", "1"), new Terminal("+", "+"), new Terminal("num", "2"));

    // parse() must be called from the thread that owns parser — a different
    // thread must receive WrongThreadException
    var result = runAll(List.of(
            (Callable<Object>) () -> {
              parser.parse(input.iterator(), arithmeticEvaluator());
              return fail("expected WrongThreadException");
            }),
        Executors::newVirtualThreadPerTaskExecutor);

    assertInstanceOf(WrongThreadException.class, result.getFirst().cause());
  }


  @Test
  public void createFactoryNullGrammarThrows() {
    assertThrows(NullPointerException.class,
        () -> ParserFactory.createFactory(null, Map.of()));
  }

  @Test
  public void createFactoryNullPrecedenceThrows() {
    var mg = arithmeticMetaGrammar();
    assertThrows(NullPointerException.class,
        () -> ParserFactory.createFactory(mg.grammar(), null));
  }

  // -------------------------------------------------------------------------
  // Stress test: interleaved createParser() and parse() calls
  // -------------------------------------------------------------------------

  @Test
  public void stressTestInterleavedCreateAndParse() throws InterruptedException {
    var mg      = arithmeticMetaGrammar();
    var factory = ParserFactory.createFactory(mg.grammar(), mg.precedenceMap());

    // 10 * 3 + 2 = 32
    var input = List.of(
        new Terminal("num", "10"), new Terminal("*", "*"),
        new Terminal("num", "3"),  new Terminal("+", "+"),
        new Terminal("num", "2"));

    var threadCount = 8;
    var iterCount = 200;

    // Each iteration creates a fresh parser, exercising factory-level caching
    // under concurrent load.
    var callables = IntStream.range(0, threadCount)
        .mapToObj(t -> (Callable<Integer>) () -> {
          for (var i = 0; i < iterCount; i++) {
            var value = factory.createParser().parse(input.iterator(), arithmeticEvaluator());
            if (value != 32) {
              throw new AssertionError("expected 32, got " + value);
            }
          }
          return 0;
        })
        .toList();

    var results = runAll(callables, () -> Executors.newFixedThreadPool(threadCount));

    assertNoFailures(results);
  }

  // -------------------------------------------------------------------------
  // JSON grammar: many threads sharing a single factory (realistic workload)
  // -------------------------------------------------------------------------

  @Test
  public void jsonGrammarManyThreads() throws InterruptedException {
    var mg      = jsonMetaGrammar();
    var factory = ParserFactory.createFactory(mg.grammar(), mg.precedenceMap());

    // { "a": [true, false], "b": null }
    var input = List.of(
        new Terminal("{",      "{"),
        new Terminal("STRING", "a"),    new Terminal(":", ":"),
        new Terminal("[",      "["),
        new Terminal("true",   "true"), new Terminal(",", ","),
        new Terminal("false",  "false"),
        new Terminal("]",      "]"),    new Terminal(",", ","),
        new Terminal("STRING", "b"),    new Terminal(":", ":"),
        new Terminal("null",   "null"),
        new Terminal("}",      "}"));

    var threadCount = 20;
    var parseCount  = 10;

    var callables = IntStream.range(0, threadCount)
        .mapToObj(t -> (Callable<Integer>) () -> {
          var parser = factory.createParser();
          for (var i = 0; i < parseCount; i++) {
            parser.parse(input.iterator(), NOOP_LISTENER);
          }
          return parseCount;
         })
        .toList();

    var results = runAll(callables, () -> Executors.newFixedThreadPool(threadCount));

    assertNoFailures(results);
    results.forEach(r -> assertEquals(parseCount, r.value()));
  }
}