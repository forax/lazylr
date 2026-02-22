package com.github.forax.lazylr;

import java.util.List;

/// An interface for transforming a successful parse into a domain-specific
/// result, such as an Abstract Syntax Tree (AST) or an interpreted value.
///
/// The `Evaluator` maps the formal structures of the [Grammar] (terminals and
/// productions) to meaningful objects. It is invoked when the [Parser] does
/// a shift or a reduce.
///
/// ### Evaluation Flow
/// * **Leaf Nodes**: When the parser encounters a [Terminal], it calls [#evaluate(Terminal)]
///    to convert the raw token into a value (e.g., parsing a string "123" into an `Integer`).
/// * **Internal Nodes**: When a [Production] is reduced, it calls
///    [#evaluate(Production, List)] with the results of its children.
///
/// @param <T> The type of the value produced by the evaluation.
///
/// @see Parser#parse(java.util.Iterator, Evaluator)
public interface Evaluator<T> {

  /// Transforms a matched [Terminal] into a value.
  ///
  /// This is typically where you extract the [Terminal#value()] (the actual
  /// lexeme from the input) and convert it into a literal or leaf node.
  ///
  /// @param terminal The terminal token matched by the lexer.
  /// @return A value representing the terminal.
  T evaluate(Terminal terminal);

  /// Reduces a [Production] into a single value using its previously evaluated
  /// components.
  ///
  /// The `arguments` list corresponds to the [Production#body()] in order.
  /// For example, in a production `expr : expr + expr`, the list will contain:
  /// * `arguments[0]`: Result of the first `expr`.
  /// * `arguments[1]`: Result of the `+` terminal.
  /// * `arguments[2]`: Result of the second `expr`.
  ///
  /// @param production The derivation rule being reduced.
  /// @param arguments The evaluated results of each [Symbol] in the production's body.
  /// @return The result of the reduction (the new value for the production non-terminal
  T evaluate(Production production, List<T> arguments);
}
