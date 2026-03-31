/// # Lazy LR — A Lightweight Runtime LR(1) Parser Library
///
/// This package provides all the building blocks to define a context-free grammar,
/// tokenize raw text, and parse token streams into structured results at runtime,
/// without a separate code-generation step.
///
/// ## Core Concepts
///
/// A [com.github.forax.lazylr.Grammar] is a validated set of
/// [com.github.forax.lazylr.Production] rules plus a
/// start [com.github.forax.lazylr.NonTerminal].
/// Productions are built from two kinds of [com.github.forax.lazylr.Symbol]s:
/// [com.github.forax.lazylr.Terminal] (a concrete token, e.g. `"+"` or `"num"`)
/// and [com.github.forax.lazylr.NonTerminal] (an abstract construct, e.g. `"E"`).
///
/// [com.github.forax.lazylr.MetaGrammar] lets you describe tokens, precedence,
/// and productions in a compact text DSL instead of building Java objects by hand:
///
/// ```java
/// var mg = MetaGrammar.load("""
///     tokens {
///       num: /[0-9]+/
///       /[ ]+/
///     }
///     precedence {
///       left:  '+'
///       left:  '*'
///       right: '^'
///     }
///     grammar {
///       E: num
///       E: E '+' E
///       E: E '*' E
///       E: E '^' E   %prec '^'
///     }
///     """);
/// ```
///
/// The DSL has three optional sections:
/// - **`tokens`**: named terminals (`name: /regex/`) and unnamed
///   skip patterns (`/regex/`).
/// - **`precedence`**: `left:` or `right:` lines, lowest first,
///   multiple terminals per line share the same level.
/// - **`grammar`**: BNF-style rules; quoted literals like `'+'` are
///   auto-registered as terminals.
///   Lines with no right-hand side are epsilon productions,
///   `%prec TOKEN` overrides a production's default precedence.
///
/// ## Typical Usage
///
/// ```java
/// // 1. Load the grammar
/// MetaGrammar mg = MetaGrammar.load(grammarText);
///
/// // 2. Optionally verify for conflicts
/// mg.verify();
///
/// // 3. Parse and evaluate
/// String input = ...
/// var result = mg.parse(input, new MyVisitor());
/// ```
///
/// ## Key Classes
/// [com.github.forax.lazylr.LALRVerifier] performs a full offline LALR(1)
/// analysis and can print the complete state automaton with conflict markers.
/// This is the class used by [com.github.forax.lazylr.MetaGrammar#verify()].
///
/// [com.github.forax.lazylr.Lexer] converts a `CharSequence` into
/// a lazy `Iterator<Terminal>` using a longest-match rule, ties
/// broken by declaration order.
///
/// [com.github.forax.lazylr.Parser] implements a lazy LR(1) algorithm:
/// states are computed on demand.
/// Shift/reduce conflicts are resolved via a [com.github.forax.lazylr.Precedence] map;
/// unresolved conflicts cause a [com.github.forax.lazylr.ParsingException]
/// during parsing.
/// A call to [com.github.forax.lazylr.MetaGrammar#parse(java.lang.CharSequence, com.github.forax.lazylr.Visitor)()]
/// creates a lexer and a parser.
///
/// [com.github.forax.lazylr.Visitor] maps terminal and productions to typechecked methods.
/// When a terminal is shifted, the corresponding terminal method is called.
/// When a production is reduced, the production method is called with the values
/// of the symbols of the production in left-to-right order.
/// [com.github.forax.lazylr.Visitor#reflect(java.lang.invoke.MethodHandles.Lookup, com.github.forax.lazylr.Visitor)]
/// creates an evaluator from a visitor using reflection.
///
/// [com.github.forax.lazylr.Evaluator] maps parse events to a result
/// of type `T`: `evaluate(Terminal)` is called on every shift,
/// `evaluate(Production, List<T>)` on every reduction.
/// For event-driven use without a result, prefer [com.github.forax.lazylr.ParserListener].
///
/// ## Thread Safety
/// All classes are immutable and thread-safe except [com.github.forax.lazylr.Parser],
/// which is stateful and bound to the thread that uses it.
///
/// [com.github.forax.lazylr.MetaGrammar] is thread-safe and can be shared globally.
//
/// For concurrent workloads, you can share a [com.github.forax.lazylr.ParserFactory]
/// and call [com.github.forax.lazylr.ParserFactory#createParser()]
/// once per thread.
///
/// @see com.github.forax.lazylr.Terminal
/// @see com.github.forax.lazylr.Production
/// @see com.github.forax.lazylr.Grammar
/// @see com.github.forax.lazylr.MetaGrammar
/// @see com.github.forax.lazylr.Visitor
@NullMarked
package com.github.forax.lazylr;

import org.jspecify.annotations.NullMarked;
