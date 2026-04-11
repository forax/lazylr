package com.github.forax.lazylr.perf;

import com.github.forax.lazylr.Evaluator;
import com.github.forax.lazylr.Lexer;
import com.github.forax.lazylr.Parser;
import com.github.forax.lazylr.ParserListener;
import com.github.forax.lazylr.Production;
import com.github.forax.lazylr.Terminal;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class JSONPerfTest {
  // Generate more than 1_000_000 terminals
  private static final List<Terminal> TERMINALS = JSONData.createJSONTerminals(new Random(292), 1_000_000);
  private static final String JSON_TEXT = JSONData.createJSONText(new Random(292), TERMINALS);

  @Test
  public void jsonLexerPerfTest() {
    var lexer = Lexer.createLexer(JSONData.TOKENS);
    var iterator = lexer.tokenize(JSON_TEXT);

    var result = new ArrayList<Terminal>();
    while (iterator.hasNext()) {
      var terminal = iterator.next();
      if (Terminal.ERROR.equals(terminal)) {
        throw new AssertionError(terminal.value(), null);
      }
      result.add(terminal);
    }
    iterator.forEachRemaining(result::add);

    assertEquals(TERMINALS, result);
  }

  @Test
  public void jsonParserPerfTest() {
    var parser = Parser.createParser(JSONData.GRAMMAR, Map.of());
    parser.parse(TERMINALS.iterator(), new ParserListener() {
      @Override
      public void onShift(@NonNull Terminal token) {
        // empty
      }
      @Override public void onReduce(@NonNull Production production) {
        // empty
      }
    });
  }

  @Test
  public void jsonLexerAndParserListenerPerfTest() {
    var lexer = Lexer.createLexer(JSONData.TOKENS);
    var parser = Parser.createParser(JSONData.GRAMMAR, Map.of());
    parser.parse(lexer.tokenize(JSON_TEXT), new ParserListener() {
      @Override
      public void onShift(@NonNull Terminal token) {
        // empty
      }
      @Override public void onReduce(@NonNull Production production) {
        // empty
      }
    });
  }

  @Test
  public void jsonLexerAndParserEvaluatorPerfTest() {
    var lexer = Lexer.createLexer(JSONData.TOKENS);
    var parser = Parser.createParser(JSONData.GRAMMAR, Map.of());
    parser.parse(lexer.tokenize(JSON_TEXT), new Evaluator<>() {
      @Override
      public Object evaluate(@NonNull Terminal terminal) {
        return null;
      }
      @Override
      public Object evaluate(@NonNull Production production, @NonNull List<Object> arguments) {
        return null;
      }
    });
  }
}
