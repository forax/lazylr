package com.github.forax.lazylr.perf;

import com.github.forax.lazylr.Parser;
import com.github.forax.lazylr.ParserListener;
import com.github.forax.lazylr.Production;
import com.github.forax.lazylr.Terminal;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

public final class ParserJSONPerfTest {
  @Test
  public void jsonPerfTest() {
    // Generate more than 1_000_000 terminals
    var random = new Random(292);
    var targetSize = 1_000_000;

    var input = JSONData.createJSONTerminals(random, targetSize);
    // IO.println("Generated " + input.size() + " terminals");

    var parser = Parser.createParser(JSONData.GRAMMAR, Map.of());
    parser.parse(input.iterator(), new ParserListener() {
      @Override
      public void onShift(@NonNull Terminal token) {
        // empty
      }
      @Override public void onReduce(@NonNull Production production) {
        // empty
      }
    });
  }
}
