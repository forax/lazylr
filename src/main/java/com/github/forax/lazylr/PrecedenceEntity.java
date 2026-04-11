package com.github.forax.lazylr;

/// A common interface for components that participate in precedence-based
/// conflict resolution.
///
/// This interface allows the [Parser] to resolve ambiguities when multiple
/// grammatical actions are possible for a single lookahead token. It is primarily
/// used to handle **shift/reduce** conflicts:
///
/// ### Shift/Reduce Conflicts
/// When the parser can either shift a [Terminal] or reduce a [Production],
/// it compares the precedence of the production against the precedence of the
/// lookahead terminal. The [associativity][Precedence.Associativity] then
/// determines whether to favor the shift (right) or the reduction (left).
///
/// Reduce/reduce conflicts are **not** resolved by precedence.
/// They remain unresolved conflicts and must be removed by rewriting the grammar.
///
/// ### Resolution Hierarchy
/// If a [Production] does not have an explicit [Precedence] assigned in the
/// configuration map, it inherits the precedence of its right-most terminal
/// (e.g., `expr : expr * expr` would inherit the precedence of `*`).
///
/// @see Precedence
/// @see Parser#createParser(Grammar, java.util.Map)
public sealed interface PrecedenceEntity permits Terminal, Production { }