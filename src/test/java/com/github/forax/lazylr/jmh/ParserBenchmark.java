package com.github.forax.lazylr.jmh;

import com.github.forax.lazylr.Lexer;
import com.github.forax.lazylr.MetaGrammar;
import com.github.forax.lazylr.Parser;
import com.github.forax.lazylr.ParserListener;
import com.github.forax.lazylr.Production;
import com.github.forax.lazylr.Terminal;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Gatherer;
import java.util.stream.IntStream;

@BenchmarkMode(Mode.AverageTime)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class ParserBenchmark {

  static final Lexer LEXER;
  static final Parser PARSER;
  static final ParserListener NOOP_LISTENER;
  static {
    var mg = MetaGrammar.load("""
            tokens {
              num : /[0-9]+/
              /[ ]+/
            }
            precedence {
              left : '+'
            }
            grammar {
              E : num
              E : E '+' E
            }
            """);
    mg.verify(err -> { throw new AssertionError(err); });

    var lexer = Lexer.createLexer(mg.tokens());
    LEXER = lexer;

    var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());
    PARSER = parser;

    var listener = new ParserListener() {
      @Override
      public void onShift(Terminal token) { }
      @Override
      public void onReduce(Production production) {}
    };
    NOOP_LISTENER = listener;
  }

  @Param(/*{"10", "100", "1000", "10000", "100000"}*/ {"1000"})
  public int terminalLength;

  private List<Terminal> terminals;
  private String inputText;

  @Setup(Level.Trial)
  public void setup() {
    var num = new Terminal("num");
    var plus = new Terminal("+");
    terminals = IntStream.range(0, terminalLength).boxed()
        .<Terminal>gather(Gatherer.ofSequential(
            (_, _, downstream) -> {
              downstream.push(num);
              return downstream.push(plus);
            }, (_, downstream) -> {
              downstream.push(num);
            }))
        .toList();

    inputText = IntStream.range(0, terminalLength)
        .mapToObj(i -> "" + i)
        .collect(Collectors.joining(" + "));
  }

  @Benchmark
  public void parser() {
    PARSER.parse(terminals.iterator(), NOOP_LISTENER);
  }

  //@Benchmark
  public void lexerAndParser() {
    PARSER.parse(LEXER.tokenize(inputText), NOOP_LISTENER);
  }

  static void main(String[] args) throws Exception {
    var options = new OptionsBuilder()
        .include(ParserBenchmark.class.getSimpleName())
        .build();

    new Runner(options).run();
  }
}