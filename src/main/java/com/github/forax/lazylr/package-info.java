/// # Lazy LR — A Lightweight Runtime LR(1) Parser Library
///
/// This package provides all the building blocks needed to define
/// a context-free grammar, tokenize raw text, and parse token
/// streamsinto structured results, all at runtime, without a separate
/// code-generation step.
///
/// ## Core Concepts
///
/// ### Symbols
/// A grammar is built from two kinds of [Symbol]s:
/// - [com.github.forax.lazylr.Terminal]: a concrete token produced
///   by the lexer (e.g. `"+"` or `"num"`).
///
/// - [com.github.forax.lazylr.NonTerminal]: an abstract grammatical
///   construct (e.g. `"E"`, `"Stmt"`) that appears as the head of
///   one or more productions.
///
/// ### Grammar
/// A [com.github.forax.lazylr.Grammar] is a validated
/// set of [com.github.forax.lazylr.Production] rules plus
/// a designated start [com.github.forax.lazylr.NonTerminal].
///
/// ### MetaGrammar
/// [com.github.forax.lazylr.MetaGrammar] lets you describe tokens,
/// precedence, and productions in a compact text DSL instead of
/// building Java objects by hand:
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
///       E: E '^' E
///     }
///     """);
/// ```
///
/// The DSL has three optional sections:
/// - **`tokens`**: named terminals (`name: /regex/`) and anonymous
///   skip patterns (`/regex/`).
/// - **`precedence`**: `left:` or `right:` lines, lowest first,
///   multiple terminals per line share the same level.
/// - **`grammar`**: BNF-style rules; quoted literals like `'+'`
///   are auto-registered as terminals.
///   Lines with no right-hand side are epsilon productions;
///   `%prec TOKEN` overrides a production's default precedence.
///
/// ## Typical Usage
///
/// ```java
/// // 1. Load the grammar
/// var mg = MetaGrammar.load(grammarText);
///
/// // 2. Optionally verify for conflicts
/// LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), System.err::println);
///
/// // 3. Create a lexer and a parser
/// var lexer  = Lexer.createLexer(mg.tokens());
/// var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());
///
/// // 4. Parse and evaluate
/// var result = parser.parse(lexer.tokenize(input), new MyEvaluator());
/// ```
///
/// ## Lexer
/// [Lexer] converts a `CharSequence` into a lazy `Iterator<Terminal>`.
/// Token matching uses a longest-match rule; ties are broken
/// by declaration order. Anonymous tokens (no name) are silently skipped.
/// On an unrecognized character the lexer emits
/// [com.github.forax.lazylr.Terminal#ERROR] and stops.
///
/// ## Parser
/// [com.github.forax.lazylr.Parser] implements a lazy LR(1) algorithm:
/// states are computed on demand rather than all upfront, so
/// `createParser` is cheap and proportional only to the portion of
/// the grammar actually exercised by the input.
///
/// Shift/reduce conflicts are resolved via a [Precedence] map supplied
/// at construction time; unresolved conflicts cause a [ParsingException]
/// during the parsing.
///
/// ## Evaluator
/// [com.github.forax.lazylr.Evaluator] is the functional interface
/// that maps parse events to a result of type `T`:
/// - `evaluate(Terminal)` — called on every shift; typically extracts
///    the terminal value.
/// - `evaluate(Production, List<T>)` — called on every reduction,
///    receives the already-evaluated values of each body symbol, in order.
///
/// For event-driven use (no result needed), [ParserListener] exposes
/// the same two events as `onShift()` and `onReduce()` callbacks.
///
/// ## Conflict Verification
/// [LALRVerifier] performs a full offline LALR(1) analysis of the grammar.
/// It can optionally print the complete state automaton (with conflict markers)
/// to a  [java.io.PrintStream], making it a useful development tool.
/// The parser itself is LR(1), so it can correctly handle grammars that are LR(1)
/// or LALR(1).
/// The verifier reports only the conflicts that cannot be resolved with
/// the supplied precedence map.
///
/// ## Precedence and Associativity
/// [Precedence] pairs a non-negative integer level with a
/// [com.github.forax.lazylr.Precedence.Associativity] (`LEFT` or `RIGHT`).
/// The [com.github.forax.lazylr.Precedence] map passed to the parser and
/// verifier can contain entries for both [com.github.forax.lazylr.Terminal]s
/// and [com.github.forax.lazylr.Production]s (the `%prec` override).
/// Both implement [com.github.forax.lazylr.PrecedenceEntity].
/// Missing production entries are automatically inferred from
/// the production's rightmost terminal.
///
/// ## Thread Safety
/// All classes in this package are immutable and thread-safe except [Parser].
/// Each `Parser` instance is bound to the thread that created it; calling
/// `parse()` from another thread throws [java.lang.WrongThreadException].
/// For concurrent workloads, share a [com.github.forax.lazylr.ParserFactory]
/// and call [com.github.forax.lazylr.ParserFactory#createParser()]
/// once per thread.
///
/// @see com.github.forax.lazylr.Terminal
/// @see com.github.forax.lazylr.NonTerminal
/// @see com.github.forax.lazylr.Production
/// @see com.github.forax.lazylr.Grammar
/// @see com.github.forax.lazylr.MetaGrammar
/// @see com.github.forax.lazylr.Lexer
/// @see com.github.forax.lazylr.Parser
/// @see com.github.forax.lazylr.ParserFactory
/// @see com.github.forax.lazylr.Evaluator
/// @see com.github.forax.lazylr.ParserListener
/// @see com.github.forax.lazylr.Precedence
/// @see com.github.forax.lazylr.LALRVerifier
@NullMarked
package com.github.forax.lazylr;

import org.jspecify.annotations.NullMarked;
