package com.github.forax.lazylr;

/// The base interface for all elements that can appear in a grammar.
///
/// A symbol is the fundamental unit of a [Grammar]. It can be either a
/// "leaf" of the tree (a [Terminal]) or a "branch" representing a logical
/// structure (a [NonTerminal]).
///
/// ### Implementations
/// * [Terminal]: Represents raw tokens matched by the [Lexer] (e.g., `"num"`, `"+"`).
/// * [NonTerminal]: Represents abstract grammatical constructs (e.g., `"expr"`, `"stmt"`).
public sealed interface Symbol permits Terminal, NonTerminal {

  /// Returns the unique identifying name of this symbol.
  ///
  /// @return The identifier for this symbol.
  String name();
}