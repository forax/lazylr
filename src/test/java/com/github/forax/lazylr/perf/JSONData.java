package com.github.forax.lazylr.perf;

import com.github.forax.lazylr.Grammar;
import com.github.forax.lazylr.MetaGrammar;
import com.github.forax.lazylr.Terminal;
import com.github.forax.lazylr.Token;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

final class JSONData {
  private JSONData() {}

  private static MetaGrammar createMetaGrammar() {
    return MetaGrammar.load("""
        tokens {
          STRING: /"[^"]*"/
          NUMBER: /(?:-)?[0-9]+(?:\\.[0-9]+)?/
          /[ \\t\\r\\n]+/
        }
        grammar {
          Value : Object
          Value : Array
          Value : STRING
          Value : NUMBER
          Value : 'true'
          Value : 'false'
          Value : 'null'
        
          Object : '{' '}'
          Object : '{' Members '}'
          Pair : STRING ':' Value
          Members : Pair
          Members : Members ',' Pair
        
          Array : '[' ']'
          Array : '[' Elements ']'
        
          Elements : Value
          Elements : Elements ',' Value
        }
        """);
  }

  private static final MetaGrammar META_GRAMMAR = createMetaGrammar();

  public static final Grammar GRAMMAR = META_GRAMMAR.grammar();
  public static final List<Token> TOKENS = META_GRAMMAR.tokens();

  private static final Terminal OBJ_START = new Terminal("{");
  private static final Terminal OBJ_END = new Terminal("}");
  private static final Terminal ARR_START = new Terminal("[");
  private static final Terminal ARR_END = new Terminal("]");
  private static final Terminal COMMA = new Terminal(",");
  private static final Terminal COLON = new Terminal(":");
  private static final Terminal STRING = new Terminal("STRING");
  private static final Terminal NUMBER = new Terminal("NUMBER");
  private static final Terminal BOOL_TRUE = new Terminal("true");
  private static final Terminal BOOL_FALSE = new Terminal("false");
  private static final Terminal NULL_VALUE = new Terminal("null");

  private static final List<Terminal> PRIMITIVES =
      List.of(STRING, NUMBER, BOOL_TRUE, BOOL_FALSE, NULL_VALUE);

  public static List<Terminal> createJSONTerminals(RandomGenerator random, int targetSize) {

    // Each task is either "generate a value" or "add a specific terminal"
    // We use a special marker to mean "generate a value"
    var MARKER = new Terminal("marker");

    var terminals = new ArrayList<Terminal>();

    var stack = new ArrayDeque<Terminal>();
    stack.push(MARKER); // Start by generating one value

    Terminal task;
    while ((task = stack.poll()) != null) {
      if (task != MARKER) {
        // Just emit this terminal
        terminals.add(task);
        continue;
      }

      // Generate a primitive value
      if (terminals.size() >= targetSize || random.nextDouble() < 0.4) {
        terminals.add(PRIMITIVES.get(random.nextInt(PRIMITIVES.size())));
        continue;
      }

      if (random.nextBoolean()) {
        // Generate Object — push everything in reverse order
        var entryCount = random.nextInt(3) + 1;
        stack.push(OBJ_END);
        for (var i = entryCount; --i >= 0; ) {
          if (i < entryCount - 1) {
            stack.push(COMMA);
          }
          stack.push(MARKER);   // generate the value
          stack.push(COLON);
          stack.push(STRING);
        }
        terminals.add(OBJ_START);
      } else {
        // Generate Array — push everything in reverse order
        var elementCount = random.nextInt(3) + 1;
        stack.push(ARR_END);
        for (var i = elementCount; --i >= 0;) {
          if (i < elementCount - 1) {
            stack.push(COMMA);
          }
          stack.push(MARKER);  // generate the value
        }
        terminals.add(ARR_START);
      }
    }
    return terminals;
  }

  private static final List<String> STRINGS = List.of(
      "session_id", "alpha_vanguard_99", "is_active",
      "quantum_flux_capacity", "userDisplayName", "retry_count",
      "obsidian_shards", "lastLoginTimestamp", "nexus_endpoint",
      "vibrant_emerald_402", "account_status_code", "glitch_in_the_matrix",
      "total_revenue_usd", "payload_checksum", "echo_delta_foxtrot",
      "max_buffer_size", "stellar_drift", "is_premium_member",
      "request_latency_ms", "cryptic_metadata", "preferred_language",
      "velocity_vector_x", "shadow_realm_access", "system_uptime_seconds"
  );

  private static final List<Number> NUMBERS = List.of(
      101, 42.5, 999, 0.007, 1024,
      -15, 3.1415, 88, 123456, 0,
      50.55, 7, 2026, 0.5, 99,
      -1.2, 5000, 13, 0.1, 777,
      24, 1.618, 1000000, 404
  );

  public static String createJSONText(RandomGenerator random, List<Terminal> terminals) {
    return terminals.stream()
        .map(terminal -> {
          if (terminal == STRING) {
            return "\"" + STRINGS.get(random.nextInt(STRINGS.size())) + "\"";
          }
          if (terminal == NUMBER) {
            return "" + NUMBERS.get(random.nextInt(NUMBERS.size()));
          }
          return terminal.name();
        })
        .collect(Collectors.joining(" "));
  }
}
