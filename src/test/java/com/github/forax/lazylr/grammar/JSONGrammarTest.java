package com.github.forax.lazylr.grammar;

import com.github.forax.lazylr.Evaluator;
import com.github.forax.lazylr.Lexer;
import com.github.forax.lazylr.MetaGrammar;
import com.github.forax.lazylr.Parser;
import com.github.forax.lazylr.ParserFactory;
import com.github.forax.lazylr.ParsingException;
import com.github.forax.lazylr.Production;
import com.github.forax.lazylr.Terminal;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Integration tests for the full JSON grammar.
/// Each test parses a raw JSON string through the Lexer and Parser together
/// and asserts that the resulting AST matches the expected [JSONValue] tree.
/// JSON grammar:
/// ```
/// Value    : Object | Array | string | number | true | false | null
/// Object   : '{' '}' | '{' Members '}'
/// Members  : Pair | Members ',' Pair
/// Pair     : string ':' Value
/// Array    : '[' ']' | '[' Elements ']'
/// Elements : Value | Elements ',' Value
/// ```
@Execution(ExecutionMode.CONCURRENT)
public final class JSONGrammarTest {

  /// Grammar
  private static MetaGrammar buildMetaGrammar() {
    return MetaGrammar.load("""
        tokens {
          string: /"[^"]*"/
          number: /[0-9]+(?:\\.[0-9]+)?/
          /[ \\t\\r\\n]+/
        }
        grammar {
          Value: Object
          Value: Array
          Value: string
          Value: number
          Value: 'true'
          Value: 'false'
          Value: 'null'
        
          Object: '{' '}'
          Object: '{' Members '}'
          Pair: string ':' Value
          Members: Pair
          Members: Members ',' Pair
        
          Array: '[' ']'
          Array: '[' Elements ']'
          Elements: Value
          Elements: Elements ',' Value
        }
        """);
  }

  /// AST — the public JSON value types
  public sealed interface JSONValue
      permits JSONString, JSONNumber, JSONBoolean, JSONNull, JSONArray, JSONObject {}

  public record JSONString(String value)                   implements JSONValue {}
  public record JSONNumber(double value)                   implements JSONValue {}
  public record JSONBoolean(boolean value)                 implements JSONValue {}
  public record JSONNull()                                 implements JSONValue {}
  public record JSONArray(List<JSONValue> elements)        implements JSONValue {}
  public record JSONObject(Map<String, JSONValue> members) implements JSONValue {}

  /// Evaluator: parametrized by Object; intermediaries are plain collections
  ///   Pair     -> Map.Entry<String, JSONValue>
  ///   Members  -> ArrayList<Map.Entry<String, JSONValue>>;
  ///   Elements -> ArrayList<JSONValue>
  private static final Evaluator<@Nullable Object> EVALUATOR = new Evaluator<@Nullable Object>() {

    @Override
    public @Nullable Object evaluate(Terminal terminal) {
      assert terminal.value() != null;
      return switch (terminal.name()) {
        case "string"  -> new JSONString(stripQuotes(terminal.value()));
        case "number"  -> new JSONNumber(Double.parseDouble(terminal.value()));
        case "true"    -> new JSONBoolean(true);
        case "false"   -> new JSONBoolean(false);
        case "null"    -> new JSONNull();
        default        -> null;   // punctuation: '{', '}', '[', ']', ':', ','
      };
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object evaluate(Production production, @SuppressWarnings("NullableProblems") List<Object> args) {
      return switch (production.name()) {

        // Value — simple passthrough
        case "Value : Object",
             "Value : Array",
             "Value : string",
             "Value : number",
             "Value : true",
             "Value : false",
             "Value : null"         -> args.get(0);

        // Pair — Map.Entry<String, JSONValue>
        // args: string ':' Value  (indices 0, 1, 2)
        case "Pair : string : Value" ->
            new AbstractMap.SimpleImmutableEntry<>(
                ((JSONString) args.get(0)).value(),
                (JSONValue) args.get(2));

        // Members — ArrayList<Map.Entry<String, JSONValue>>
        case "Members : Pair" -> {
          var list = new ArrayList<Map.Entry<String, JSONValue>>();
          list.add((Map.Entry<String, JSONValue>) args.getFirst());
          yield list;
        }
        case "Members : Members , Pair" -> {
          var list = (ArrayList<Map.Entry<String, JSONValue>>) args.get(0);
          list.add((Map.Entry<String, JSONValue>) args.get(2));
          yield list;
        }

        // Object — LinkedHashMap preserves insertion order
        case "Object : { }"         -> new JSONObject(new LinkedHashMap<>());
        case "Object : { Members }" -> {
          var map = new LinkedHashMap<String, JSONValue>();
          for (var entry : (ArrayList<Map.Entry<String, JSONValue>>) args.get(1)) {
            map.put(entry.getKey(), entry.getValue());
          }
          yield new JSONObject(map);
        }

        // Elements — ArrayList<JSONValue>
        case "Elements : Value" -> {
          var list = new ArrayList<JSONValue>();
          list.add((JSONValue) args.getFirst());
          yield list;
        }
        case "Elements : Elements , Value" -> {
          var list = (ArrayList<JSONValue>) args.get(0);
          list.add((JSONValue) args.get(2));
          yield list;
        }

        // Array
        case "Array : [ ]"          -> new JSONArray(new ArrayList<>());
        case "Array : [ Elements ]" -> new JSONArray((ArrayList<JSONValue>) args.get(1));

        default -> throw new AssertionError("Unhandled production: " + production.name());
      };
    }

    private static String stripQuotes(String s) {
      return s.substring(1, s.length() - 1);
    }
  };

  private static final MetaGrammar META_GRAMMAR = buildMetaGrammar();
  private static final ParserFactory PARSER_FACTORY = ParserFactory.createFactory(
      META_GRAMMAR.grammar(), Map.of());
  private static final Lexer LEXER = Lexer.createLexer(META_GRAMMAR.tokens());

  /// Tests are run in parallel, so parse() had to be thread-safe
  private static JSONValue parse(String input) {
    var parser = PARSER_FACTORY.createParser();
    var value = (JSONValue) parser.parse(LEXER.tokenize(input), EVALUATOR);
    assert value != null;
    return value;
  }


  @Nested
  public class PrimitiveValues {

    @Test
    public void stringValue() {
      assertEquals(new JSONString("hello"), parse("\"hello\""));
    }

    @Test
    public void emptyStringValue() {
      assertEquals(new JSONString(""), parse("\"\""));
    }

    @Test
    public void integerNumberValue() {
      assertEquals(new JSONNumber(42), parse("42"));
    }

    @Test
    public void floatNumberValue() {
      assertEquals(new JSONNumber(3.14), parse("3.14"));
    }

    @Test
    public void trueValue() {
      assertEquals(new JSONBoolean(true), parse("true"));
    }

    @Test
    public void falseValue() {
      assertEquals(new JSONBoolean(false), parse("false"));
    }

    @Test
    public void nullValue() {
      assertEquals(new JSONNull(), parse("null"));
    }
  }

  @Nested
  public class Arrays {

    @Test
    public void emptyArray() {
      assertEquals(new JSONArray(List.of()), parse("[]"));
    }

    @Test
    public void singleElementArray() {
      assertEquals(
          new JSONArray(List.of(new JSONBoolean(true))),
          parse("[ true ]"));
    }

    @Test
    public void twoElementArray() {
      assertEquals(
          new JSONArray(List.of(new JSONBoolean(true), new JSONBoolean(false))),
          parse("[ true, false ]"));
    }

    @Test
    public void threeElementArray() {
      assertEquals(
          new JSONArray(List.of(new JSONString("s"), new JSONNumber(1), new JSONNull())),
          parse("[ \"s\", 1, null ]"));
    }

    @Test
    public void arrayOfEmptyArrays() {
      assertEquals(
          new JSONArray(List.of(new JSONArray(List.of()), new JSONArray(List.of()))),
          parse("[ [], [] ]"));
    }

    @Test
    public void arrayWithWhitespace() {
      assertEquals(
          new JSONArray(List.of(new JSONNumber(1), new JSONNumber(2))),
          parse("[\n  1,\n  2\n]"));
    }
  }


  @Nested
  public class Objects {

    @Test
    public void emptyObject() {
      assertEquals(new JSONObject(Map.of()), parse("{}"));
    }

    @Test
    public void singlePairObject() {
      assertEquals(
          new JSONObject(Map.of("k", new JSONBoolean(true))),
          parse("{ \"k\": true }"));
    }

    @Test
    public void twoPairObject() {
      assertEquals(
          new JSONObject(Map.of("k1", new JSONNumber(1), "k2", new JSONNull())),
          parse("{ \"k1\": 1, \"k2\": null }"));
    }

    @Test
    public void threePairObject() {
      assertEquals(
          new JSONObject(Map.of(
              "a", new JSONBoolean(true),
              "b", new JSONBoolean(false),
              "c", new JSONNull())),
          parse("{ \"a\": true, \"b\": false, \"c\": null }"));
    }

    @Test
    public void objectWithStringValue() {
      assertEquals(
          new JSONObject(Map.of("greeting", new JSONString("hello"))),
          parse("{ \"greeting\": \"hello\" }"));
    }

    @Test
    public void objectWithNestedEmptyObject() {
      assertEquals(
          new JSONObject(Map.of("nested", new JSONObject(Map.of()))),
          parse("{ \"nested\": {} }"));
    }

    @Test
    public void objectWithEmptyArrayValue() {
      assertEquals(
          new JSONObject(Map.of("list", new JSONArray(List.of()))),
          parse("{ \"list\": [] }"));
    }
  }


  @Nested
  public class ComplexStructures {

    @Test
    public void objectInsideArray() {
      assertEquals(
          new JSONArray(List.of(
              new JSONObject(Map.of("k", new JSONBoolean(true))))),
          parse("[ { \"k\": true } ]"));
    }

    @Test
    public void arrayInsideObject() {
      assertEquals(
          new JSONObject(Map.of(
              "items", new JSONArray(List.of(new JSONNumber(1), new JSONNumber(2))))),
          parse("{ \"items\": [ 1, 2 ] }"));
    }

    @Test
    public void deeplyNestedObjects() {
      assertEquals(
          new JSONObject(Map.of(
              "a", new JSONObject(Map.of(
                  "b", new JSONObject(Map.of(
                      "c", new JSONNull())))))),
          parse("{ \"a\": { \"b\": { \"c\": null } } }"));
    }

    @Test
    public void deeplyNestedArrays() {
      assertEquals(
          new JSONArray(List.of(
              new JSONArray(List.of(
                  new JSONArray(List.of(new JSONNull())))))),
          parse("[ [ [ null ] ] ]"));
    }

    @Test
    public void complexMixedDocument() {
      // { "a": [false, {"b": [true, null, 123]}, "s"], "c": {"d": {}} }
      assertEquals(
          new JSONObject(Map.of(
              "a", new JSONArray(List.of(
                  new JSONBoolean(false),
                  new JSONObject(Map.of(
                      "b", new JSONArray(List.of(
                          new JSONBoolean(true),
                          new JSONNull(),
                          new JSONNumber(123))))),
                  new JSONString("s"))),
              "c", new JSONObject(Map.of(
                  "d", new JSONObject(Map.of()))))),
          parse("{ \"a\": [false, {\"b\": [true, null, 123]}, \"s\"], \"c\": {\"d\": {}} }"));
    }
  }


  @Nested
  public class InvalidInputs {

    private void assertRejected(String input) {
      var mg = buildMetaGrammar();
      var lexer = Lexer.createLexer(mg.tokens());
      var parser = Parser.createParser(mg.grammar(), Map.of());
      assertThrows(ParsingException.class, () ->
          parser.parse(lexer.tokenize(input), EVALUATOR));
    }

    @Test
    public void emptyInputIsRejected() {
      assertRejected("");
    }

    @Test
    public void unclosedArrayIsRejected() {
      assertRejected("[ true");
    }

    @Test
    public void unclosedObjectIsRejected() {
      assertRejected("{ \"k\": true");
    }

    @Test
    public void missingColonInPairIsRejected() {
      assertRejected("{ \"k\" true }");
    }

    @Test
    public void trailingCommaInArrayIsRejected() {
      assertRejected("[ true, ]");
    }

    @Test
    public void trailingCommaInObjectIsRejected() {
      assertRejected("{ \"k\": true, }");
    }

    @Test
    public void missingValueAfterColonIsRejected() {
      assertRejected("{ \"k\": }");
    }

    @Test
    public void bareCommaIsRejected() {
      assertRejected(",");
    }

    @Test
    public void unknownTokenIsRejected() {
      assertRejected("@");
    }
  }
}