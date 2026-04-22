package com.github.forax.lazylr;

/// A common interface for components that participate in precedence-based
/// conflict resolution.
///
/// This interface allows the [Parser] to resolve ambiguities when multiple
/// grammatical actions are possible for a single lookahead token.
/// It is used to handle both **shift/reduce** and **reduce/reduce** conflicts.
///
/// ### Shift/Reduce Conflicts
/// When the parser can either shift a [Terminal] or reduce a [Production],
/// it compares the precedence of the production against the precedence of the
/// lookahead terminal. The [associativity][Precedence.Associativity] then
/// determines whether to favor the shift (right) or the reduction (left).
///
/// ### Reduce/Reduce Conflicts
/// When the parser can reduce two or more distinct productions on the same
/// lookahead, it compares their precedence levels. The [Production] with the
/// higher level wins. If both productions have the same level, the conflict
/// remains unresolved and causes a [ParsingException] at runtime.
///
/// Prefer resolving reduce/reduce conflicts by rewriting the grammar, as this
/// makes the grammar easier to understand. Use precedence-based resolution only
/// when restructuring the grammar is impractical.
///
/// ### Resolution Hierarchy
/// If a [Production] does not have an explicit [Precedence] assigned in the
/// configuration map, it inherits the precedence of its right-most terminal
/// (e.g., `expr : expr * expr` would inherit the precedence of `*`).
///
/// @see Precedence
/// @see Parser#createParser(Grammar, java.util.Map)
public sealed interface PrecedenceEntity permits Terminal, Production { }