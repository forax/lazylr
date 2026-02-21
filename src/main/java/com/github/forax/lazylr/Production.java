package com.github.forax.lazylr;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/// Represents a derivation rule in the grammar.
///
/// A production consists of a [NonTerminal] **head** and a sequence of [Symbol]s
/// known as the **body**. It defines how the head can be expanded or reduced during
/// the parsing process.
///
/// This record implements [PrecedenceEntity], allowing it to participate in
/// conflict resolution when multiple productions could be applied (e.g., in
/// expressions with mixed operators).
///
/// ### Name
/// The [name()] of a production is used as a unique identifier for
/// [evaluation][Evaluator#evaluate(Production, List)]. It follows the format:
/// `head : symbol1 symbol2 ...` (or `head : ε` for empty productions).
///
/// @param head The left-hand side [NonTerminal] of the production.
/// @param body An ordered list of [Symbol]s (terminals and non-terminals)
///             that form the right-hand side of the production.
public record Production(NonTerminal head, List<Symbol> body) implements PrecedenceEntity {

  /// Creates an immutable production rule.
  ///
  /// @throws NullPointerException if the head is null.
  public Production {
    Objects.requireNonNull(head);
    body = List.copyOf(body);
  }

  /// Returns a string identifier for this production.
  ///
  /// This name is typically used in a switch statement within an [Evaluator]
  /// to map a grammatical structure to an AST node.
  ///
  /// * **Example**: `expr : num`
  /// * **Example (Infix)**: `expr : expr + expr`
  /// * **Example (Epsilon)**: `stmt : ε`
  ///
  /// @return A formatted string representation of the rule.
  public String name() {
    if (body.isEmpty()) {
      return head.name() + " : ε";
    }
    return body.stream()
        .map(Symbol::name)
        .collect(Collectors.joining(" ", head.name() + " : ", ""));
  }

  /// @return A string representation of the production.
  @Override
  public String toString() {
    return name();
  }
}