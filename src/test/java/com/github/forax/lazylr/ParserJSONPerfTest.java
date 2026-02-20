package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class ParserJSONPerfTest {
  private static void populateJSONValue(
      ArrayList<Terminal> tokens, Random random, List<Terminal> primitives,
      Terminal objStart, Terminal objEnd, Terminal arrStart, Terminal arrEnd,
      Terminal comma, Terminal colon, Terminal string, int targetSize) {

    // If we've reached the size limit or a random roll hits, pick a primitive
    // The branching factor of 0.4 is not realistic, but I want to test nested structures
    if (tokens.size() >= targetSize || random.nextDouble() < 0.4) {
      tokens.add(primitives.get(random.nextInt(primitives.size())));
      return;
    }
    if (random.nextBoolean()) {
      // Generate Object
      tokens.add(objStart);
      var entryCount = random.nextInt(3) + 1;
      for (int i = 0; i < entryCount; i++) {
        tokens.add(string);
        tokens.add(colon);
        populateJSONValue(tokens, random, primitives, objStart, objEnd, arrStart, arrEnd, comma, colon, string, targetSize);
        if (i < entryCount - 1) {
          tokens.add(comma);
        }
      }
      tokens.add(objEnd);
    } else {
      // Generate Array
      tokens.add(arrStart);
      var elementCount = random.nextInt(3) + 1;
      for (var i = 0; i < elementCount; i++) {
        populateJSONValue(tokens, random, primitives, objStart, objEnd, arrStart, arrEnd, comma, colon, string, targetSize);
        if (i < elementCount - 1) {
          tokens.add(comma);
        }
      }
      tokens.add(arrEnd);
    }
  }

  @Test
  public void jsonPerfTest() {
    // Terminals
    var objStart = new Terminal("{");
    var objEnd = new Terminal("}");
    var arrStart = new Terminal("[");
    var arrEnd = new Terminal("]");
    var comma = new Terminal(",");
    var colon = new Terminal(":");
    var string = new Terminal("STRING");
    var number = new Terminal("NUMBER");
    var boolTrue = new Terminal("true");
    var boolFalse = new Terminal("false");
    var nullVal = new Terminal("null");

    // Non-Terminals
    var Value = new NonTerminal("Value");
    var Object = new NonTerminal("Object");
    var Array = new NonTerminal("Array");
    var Members = new NonTerminal("Members");
    var Elements = new NonTerminal("Elements");
    var Pair = new NonTerminal("Pair");

    var grammar = new Grammar(Value, List.of(
        new Production(Value, List.of(Object)),
        new Production(Value, List.of(Array)),
        new Production(Value, List.of(string)),
        new Production(Value, List.of(number)),
        new Production(Value, List.of(boolTrue)),
        new Production(Value, List.of(boolFalse)),
        new Production(Value, List.of(nullVal)),

        new Production(Object, List.of(objStart, objEnd)),
        new Production(Object, List.of(objStart, Members, objEnd)),
        new Production(Pair, List.of(string, colon, Value)),
        new Production(Members, List.of(Pair)),
        new Production(Members, List.of(Members, comma, Pair)),

        new Production(Array, List.of(arrStart, arrEnd)),
        new Production(Array, List.of(arrStart, Elements, arrEnd)),
        new Production(Elements, List.of(Value)),
        new Production(Elements, List.of(Elements, comma, Value))
    ));

    var precedence = Map.<PrecedenceEntity, Precedence>of();

    var parser = Parser.createParser(grammar, precedence);

    // Generate terminals
    // The generation algorithm is recursive so we can not do too many
    var random = new Random(292);
    var targetSize = 20_000;

    var input = new ArrayList<Terminal>();
    var primitives = List.of(string, number, boolTrue, boolFalse, nullVal);
    populateJSONValue(input, random, primitives, objStart, objEnd, arrStart, arrEnd, comma, colon, string, targetSize);

    //IO.println("Generated " + input.size() + " tokens.");

    parser.parse(input.iterator(), new ParserListener() {
      @Override
      public void onShift(Terminal token) {
        // empty
      }
      @Override public void onReduce(Production production) {
        // empty
      }
    });
  }
}
