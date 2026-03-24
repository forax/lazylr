package com.github.forax.lazylr.perf;

import com.github.forax.lazylr.Lexer;
import com.github.forax.lazylr.Parser;
import com.github.forax.lazylr.ParserListener;
import com.github.forax.lazylr.Production;
import com.github.forax.lazylr.Terminal;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class LexerJSONPerfTest {
  @Test
  public void jsonPerfTest() {
    // Generate more than 1_000_000 terminals
    var random = new Random(292);
    var targetSize = 1_000_000;
    var terminals = JSONData.createJSONTerminals(random, targetSize);
    var input = JSONData.createJSONText(random, terminals);

    // IO.println("Generated " + input.size() + " terminals");

    var lexer = Lexer.createLexer(JSONData.TOKENS);
    var iterator = lexer.tokenize(input);

    var result = new ArrayList<Terminal>();
    for(; iterator.hasNext();) {
      var terminal = iterator.next();
      if (Terminal.ERROR.equals(terminal)) {
        throw new AssertionError(terminal.value());
      }
      result.add(terminal);
    }
    iterator.forEachRemaining(result::add);

    assertEquals(terminals, result);
  }
}
