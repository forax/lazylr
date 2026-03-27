# LazyLR Demo Tour

This folder contains a series of small, self-contained programs that introduce
the LazyLR library step by step.
Each file builds on the previous one, so reading them in order is the best way to get started.

---

## [_01_grammar.java] — Your First Grammar

The starting point. Shows how to load a grammar from a text description using
`MetaGrammar.load(...)`, then navigate the resulting objects:
the `Grammar`, its `Production`s, the `NonTerminal` head of a production,
and the `Terminal` and non-terminal symbols in its body.

You don't parse anything yet — this is purely about understanding the grammar data model.

---

## [_02_token_and_lexer.java] — Tokens and the Lexer

Introduces the `Lexer`, which turns raw input text into a stream of `Terminal` tokens.
You'll see how named tokens (like `number: /[0-9]+/`) become named terminals,
and how anonymous patterns (like `/[ \t]+/`) are silently discarded.

`Lexer.createLexer(tokens)` and `lexer.tokenize(input)` give you a lazy
`Iterator<Terminal>` ready to be consumed.

---

## [_03_parsing.java] — Parsing a Token Stream

Shows how to wire a `Parser` to a list of terminals and observe what happens during
parsing using the `PrintEvaluator` helper class (defined in [PrintEvaluator.java]).

The parsing is driven by an `Evaluator`, which receives a callback each
time a terminal is shifted or a production is reduced. The key insight: the parser
walks the grammar **bottom-up**, so leaf terminals fire before the productions that
contain them.

---

## [_04_evaluator.java] — Evaluating with MetaGrammar

Shows the convenience shortcut `mg.parse(input, evaluator)`, which bundles the
lexer and parser creation for you. The same `PrintEvaluator` is used to print every
shift and reduce event, making the parse trace visible.

---

## [_05_recursive_production.java] — Recursive Productions

Introduces a recursive grammar: `E : '(' E ')'`. The parser handles arbitrarily deep
nesting like `(32)` or `((32))` without any extra code.

The result is explored using `TreeEvaluator` (defined in [TreeEvaluator.java],
which builds a plain `Node`/`Value` tree so you can see the exact parse structure.

---

## [_06_errors.java] — Error Handling

Shows the three kinds of errors LazyLR can report, all as `ParsingException`:

- **Lexing error** — an unrecognized character (e.g. `@`)
- **Parsing error** — a token that the grammar doesn't allow at that point (e.g. `)`)
- **Unexpected end of file** — input ends before the grammar is satisfied (e.g. `(32`)

Run each case to see the formatted error messages with line/column information and
a caret pointer.

---

## [_07_visitor_oops.java] — The Visitor Interface (Naïve Attempt)

Introduces `Visitor<T>`, a higher-level alternative to `Evaluator` that lets you write
one typed method per terminal or production instead of a big `switch`.

This file deliberately shows a common pitfall: the `IntVisitor` has a `number` method
but no method for the `E : ( E )` production. The error message provides the skeleton
of the production method to implement.

---

## [_08_visitor.java] — The Visitor Interface (Correct Version)

The corrected version of the previous demo.
Adds an explicit `@ProductionName("E : ( E )")` method named `parens`that
simply returns the inner value.
It demonstrates how `@ProductionName` binds a method to a specific production.

---

## [_09_visitor_AST.java] — Building an AST

Upgrades the visitor to return a proper AST node (`Expr`) instead of a raw `int`.
Sealed interfaces and records make the AST concise. The `parens` method returns the
`Expr` unchanged, and `number` wraps the parsed integer in a `Value` record.

This is the pattern you'll use in real parsers when you need to inspect, transform,
or evaluate the parse result later.

---

## [_10_conflict_shift_reduce.java] — Reduce/Reduce Conflict

A grammar where two non-terminals `A` and `B` can both derive `number`. After shifting
`number`, the parser has two equally valid reductions and doesn't know which to pick.

Running `mg.verify()` prints the conflict report and the full LALR automaton so you can
diagnose the problem. The fix is to remove the duplication from the grammar.

---

## [_11_conflict_shift_reduce.java] — Shift/Reduce Conflict

An ambiguous expression grammar (`E : E '+' E`) without any precedence declaration.
When the parser has `E + E` on its stack and sees another `+`, it doesn't know whether
to reduce now or shift the new `+` first.

`mg.verify()` surfaces the conflict. The next two demos show how to fix it.

---

## [_12_conflict_association.java] — Resolving with `left:`

Adds a `precedence { left: '+' }` declaration to the grammar from the previous demo.
`left` means "when in doubt, reduce first", which gives left-to-right grouping:
`40 + 2 + 3` becomes `(40 + 2) + 3`.
The conflict disappears and `mg.verify()` passes.

---

## [_13_conflict_level.java] — Operator Precedence Levels

Extends the grammar with `*`. By listing `'+'` before `'*'` in the precedence section,
`*` gets a higher level than `+`, so `40 + 2 * 3` is parsed as `40 + (2 * 3)`.

This is the standard way to express "multiplication binds more tightly than addition"
without restructuring the grammar.

---

## [_14_right_assoc.java] — Right Associativity with `right:`

Adds exponentiation (`^`) with `right: '^'`. Right associativity means the parser shifts
instead of reducing on a tie, so `2 ^ 3 ^ 4` groups as `2 ^ (3 ^ 4)` rather than
`(2 ^ 3) ^ 4`.
The demo builds an AST so the grouping is clearly visible.

---

## [_15_unary_oops.java] — Unary Operator Gone Wrong

Introduces a unary minus (`E : '-' E`) alongside a binary minus (`E : E '-' E`).
Both share the `-` terminal, so the unary production inherits binary's (low) precedence.
This means `-4 * 5` is parsed as `-(4 * 5)` instead of the expected `(-4) * 5`.

The file shows the wrong output.

---

## [_16_unary_prec.java] — Fixing Unary with `%prec`

The fix: declare a virtual token `UNARY` at a higher precedence level than `*`,
then annotate the unary production with `%prec UNARY`:

```
E : '-' E    %prec UNARY
```

`%prec` overrides the production's default precedence (which would be inherited from `-`)
with the `UNARY` level. Now `-4 * 5` correctly parses as `(-4) * 5`. No changes to the
grammar rules themselves are needed.
