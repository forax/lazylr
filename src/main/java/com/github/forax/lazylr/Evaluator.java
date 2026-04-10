package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.util.List;

/// An interface for transforming a successful parse into a result of type `V`,
/// such as an Abstract Syntax Tree (AST) or an interpreted value.
///
/// The `Evaluator` maps the formal structures of the [Grammar] (terminals and
/// productions) to meaningful objects. It is invoked when the [Parser] does
/// a shift or a reduce.
///
/// ### Evaluation Flow
/// - **Leaf Nodes**: When the parser encounters a [Terminal],
///   it calls [#evaluate(Terminal)] to convert the raw token into a value
///   (e.g., parsing a string "123" into an `int`).
/// - **Internal Nodes**: When a [Production] is reduced, it calls
///    [#evaluate(Production, List)] with the results of its children.
///
/// ### Exception propagation
/// Any exception thrown by either `evaluate` method stops the parse loop
/// and propagates out of [Parser#parse(java.util.Iterator, Evaluator)].
/// The parser may be reused normally with another input after an evaluator exception.
///
/// ### Creating an Evaluator
/// Implement this interface directly with a class:
/// ```java
/// class IntEvaluator implements Evaluator<Integer> {
///   public Integer evaluate(Terminal terminal) {
///     return switch (terminal.name()) {
///       case "num" -> Integer.parseInt(terminal.value());
///       default    -> 0;
///     };
///   }
///   public Integer evaluate(Production production, List<Integer> args) {
///     return switch (production.name()) {
///       case "E : num"   -> args.get(0);
///       case "E : E + E" -> args.get(0) + args.get(2);
///       default -> throw new IllegalStateException("unknown production: " + production.name());
///     };
///   }
/// }
/// ```
///
/// ### Reflective Evaluator
/// As an alternative to implementing this interface directly,
/// [Visitor#reflect(MethodHandles.Lookup, Visitor)] builds an `Evaluator` from the
/// methods of an arbitrary object implementing the [Visitor] interface
/// using a lookup and reflection.
///
/// This lets you write plain Java methods instead of a `switch` dispatch:
///
/// ```java
/// class IntVisitor implements Visitor<Integer> {
///   public int num(Terminal t) { return Integer.parseInt(t.value()); }
///
///   @ProductionName("E : E + E")
///   public int add(int left, int right) { return left + right; }
///
///   @ProductionName("E : E * E")
///   public int mul(int left, int right) { return left * right; }
/// }
///
/// var evaluator = Visitor.reflect(MethodHandles.lookup(), new IntVisitor());
/// ```
///
/// @param <V> The type of the value produced by the evaluation.
///
/// @see Visitor
/// @see Parser#parse(java.util.Iterator, Evaluator)
public interface Evaluator<V extends @Nullable Object> {
  /// Transforms a matched [Terminal] into a value.
  ///
  /// This is typically where you extract the [Terminal#value()] (the actual
  /// lexeme from the input) and convert it into a literal or leaf node.
  ///
  /// The start position of the terminal in the input can be obtained
  /// by calling [Lexer#position(java.util.Iterator)].
  ///
  /// @param terminal The terminal token matched by the lexer.
  /// @return A value representing the terminal or `null` if the terminal has no value.
  V evaluate(Terminal terminal);

  /// Reduces a [Production] into a single value using its previously evaluated
  /// components.
  ///
  /// The `arguments` list corresponds to the [Production#body()] in order.
  /// For example, in a production `expr : E + E`, the list will contain:
  /// * `arguments[0]`: Result of the first `E`.
  /// * `arguments[1]`: Result of the `+` terminal.
  /// * `arguments[2]`: Result of the second `E`.
  ///
  /// @param production The derivation rule being reduced.
  /// @param arguments The evaluated results of each [Symbol] in the production's body.
  /// @return The result of the reduction (the new value for the production non-terminal)
  ///         or `null` if the production has no value.
  V evaluate(Production production, List<V> arguments);
}