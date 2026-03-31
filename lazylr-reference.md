# LazyLR User & Reference Manual

LazyLR is a Java runtime parsing library in the lex/yacc family
optimized for fast development and iterative grammar evolution.

---

## Suggested reading order

1. [src/main/demo/README.md](src/main/demo/README.md) for incremental demos.
2. This manual for deep reference.
3. For API details, consult the generated Javadoc (https://jitpack.io/com/github/forax/lazylr/latest/javadoc/).
4. [src/test/java/...](src/test/java) for executable examples.

---

## Table of Contents

1. [Introduction](#introduction)
2. [Quick Start](#quick-start)
3. [Conceptual Overview](#conceptual-overview)
4. [Grammar DSL Reference (`MetaGrammar`)](#grammar-dsl-reference-metagrammar)
5. [Lexing Reference (`Token`, `Lexer`, `Terminal`)](#lexing-reference-token-lexer-terminal)
6. [Parsing Reference (`Grammar`, `Parser`, `ParserFactory`)](#parsing-reference-grammar-parser-parserfactory)
7. [Semantic Actions](#semantic-actions) (`ParserListener`, `Evaluator` and `Visitor`)
8. [Precedence, Associativity, and Conflict Resolution](#precedence-associativity-and-conflict-resolution)
9. [Error Reporting and Recovery Strategy](#error-reporting-and-recovery-strategy)
10. [Automaton Inspection and Verification (`LALRVerifier`)](#automaton-inspection-and-verification-lalrverifier)
11. [Command-Line Tool](#command-line-tool)
12. [Code Generation](#code-generation)
13. [Threading, Reentrancy, and Performance](#threading-reentrancy-and-performance)
14. [End-to-End Recipes (with JUnit)](#end-to-end-recipes-with-junit)
15. [Troubleshooting Checklist](#troubleshooting-checklist)
16. [ANTLR-to-LazyLR Mapping Guide](#antlr-to-lazylr-mapping-guide)

---

## Introduction

LazyLR is for developers who want LR parser power without a code-generation build step,
so they can iterate quickly on a grammar.

- **Lexing** is defined by ordered regex rules (`Token`, `Lexer`).
- **Parsing** is bottom-up LR(1) with lazy state construction (`Parser`).
- **Grammar declaration** is usually done with the built-in DSL (`MetaGrammar.load(...)`).
- **Semantic actions** are implemented with `Evaluator` or typed `Visitor` methods.

### What it gives you

- Runtime grammar loading with lexing + parsing in one library.
- Parser states are built lazily as input is consumed, so startup cost is low even for large grammars.
- Optional offline LALR(1) conflict validation and automaton diagnostics.
- Precedence/associativity rules to resolve shift/reduce conflicts without rewriting the grammar.
- Typed semantic actions through a reflection-backed visitor layer.
- A CLI for validation, grammar debugging via automaton printing, and Java source generation.
- Context-sensitive lexing: the lexer uses the parser's current state to decide which token regexes
  are valid candidates, reducing spurious matches.
- Coverage tracking: `parser.coverage()` returns the set of productions that have been reduced at least once,
  useful for verifying test coverage of a grammar.

### What it does *not* try to be

- A complete language workbench or IDE plugin.
- A parser-combinator framework.
- A built-in AST object model (you define your own with records and sealed interfaces).
- A yacc/bison replacement with embedded actions inside grammar text.

---

## Quick Start

The LazyLR library API lets you define a grammar (in a text block here),
verify it, and then use it to parse a text input.

```java
import com.github.forax.lazylr.*;

// AST definition
sealed interface Expr {}
record Num(int value) implements Expr {}
record Add(Expr left, Expr right) implements Expr {}
record Mul(Expr left, Expr right) implements Expr {}

// Visitor that constructs the AST
final class ExprVisitor implements Visitor<Expr> {
  public Expr num(Terminal t) {
    return new Num(Integer.parseInt(t.value()));
  }

  @ProductionName("E : E + E")
  public Expr add(Expr l, Expr r) { return new Add(l, r); }

  @ProductionName("E : E * E")
  public Expr mul(Expr l, Expr r) { return new Mul(l, r); }
}

// Grammar definition
MetaGrammar mg = MetaGrammar.load("""
  tokens {
    num: /[0-9]+/
    /[ \t\n]+/
  }
  precedence {
    left: '+'
    left: '*'
  }
  grammar {
    E : num
    E : E '+' E
    E : E '*' E
  }
  """);

// Verifies that the grammar is LALR(1), or prints automaton to stderr
mg.verify();

// Run the grammar on a text and produce the AST
String text = "2 + 3 * 4";
Expr ast = mg.parse(text, new ExprVisitor());

System.out.println(ast);
// Add[left=Num[value=2], right=Mul[left=Num[value=3], right=Num[value=4]]]
```

### API highlights

- `MetaGrammar.load(String text)` parses the MetaGrammar DSL text.
- `mg.tokens()` returns the token list in lexer priority order (or an empty list if no `tokens` section).
- `mg.hasGrammar()` returns `true` if a non-empty `grammar` section was present.
- `mg.grammar()` returns the `Grammar` object, or throws `IllegalStateException` if absent.
- `mg.precedenceMap()` returns the effective precedence table (or an empty map).
- `mg.verify(...)` checks for unresolved LALR(1) conflicts.
- `mg.parse(input, evaluatorOrVisitorOrVisitorFactory)` performs lexing then parsing in one call.

---

## Conceptual Overview

LazyLR is a *bottom-up* (LR) parser. Understanding its internal flow helps interpret both
successful parses and error messages.

### Bottom-up parsing

When the parser encounters terminals, it does not try to match a rule from the top down.
Instead, it uses a *shift/reduce* loop:

- **Shift**: consume the next input token and push it on an internal stack.
- **Reduce**: when the top of the stack matches the body of a production, pop those
  symbols and replace them with the production's head non-terminal.

Events fire in this order: all shifts and inner reductions for the children of a node
happen *before* the reduction for the node itself. This is why `Evaluator.evaluate(Terminal)`
fires before `Evaluator.evaluate(Production, List)` for any given subtree.

### Lazy state construction

A traditional LR table-driven parser pre-computes every possible parser state from the
grammar before any input is seen. LazyLR instead computes states on demand: the first
time the parser visits a (state, symbol) pair, it builds the next state, caches it, and
never recomputes it. States built in one `parse()` call are reused in later calls
on the same `Parser` instance.

### LR(1) vs LALR(1)

LazyLR's `Parser` uses *LR(1)*: each item in a state carries a one-token lookahead,
so two states whose LR(0) cores are identical but whose lookaheads differ remain
separate. This makes the parser strictly more powerful than LALR(1), it handles
the classic [DeRemer grammar](https://en.wikipedia.org/wiki/LALR_parser) that causes
reduce/reduce conflicts in LALR(1) without any conflicts at all.

`LALRVerifier`, by contrast, checks the grammar under *LALR(1)* rules and reports
conflicts that the lazy LR(1) parser would actually resolve correctly. Conflicts
reported by `verify()` are therefore conservative: some may not appear during actual
parsing. In production, you should still resolve all reported conflicts for safety.

---

## Grammar DSL Reference (`MetaGrammar`)

The `MetaGrammar` text format has three named sections: `tokens`, `precedence`, `grammar`.
Each section is optional and may appear more than once; multiple occurrences of the same section
are merged in declaration order.
Newlines separate lines within a section.
Line comments starting with `//` are allowed anywhere.

```text
tokens {
  num: /[0-9]+/
  /[ \t]+/          // skip token (unnamed)
}
precedence {
  left: '+', '-'
  left: '*'
  right: UMINUS
}
grammar {
  E : num
  E : E '+' E
  E : E '-' E
  E : E '*' E
  E : '-' E %prec UMINUS
}
```

### `tokens` section

#### Syntax

```text
tokens {
  name: /regex/     // named token — emits a Terminal
  /regex/           // unnamed token — consumed and discarded
}
```

Regex syntax follows Java's `java.util.regex.Pattern`. Each regex must match at least one
character; patterns that can match the empty string are rejected at construction time.

Any quoted literals extracted from the `grammar` section appear before the named tokens
of the `tokens` section and any unnamed tokens of the `tokens` section appear after the named tokens
in the final token list.
So the ordering is always quoted literals -> named tokens -> unnamed tokens.
This ensures that explicitly quoted operators like `'+'` are matched before user-defined identifiers
and that unnamed tokens are matched after any other tokens.
Within each group (named and unnamed), tokens appear in declaration order.

#### Matching rules

1. The lexer attempts to match at the current position with every eligible pattern.
2. The match with the **greatest length** wins.
3. If two matches have the same length, the rule that appears **earlier** in the final
   token list wins.
4. Unnamed tokens are consumed silently; no `Terminal` is produced.
5. If no pattern matches and the input is not exhausted, a `Terminal.ERROR` is returned
   and iteration stops.

#### Context-sensitive lexing

When the iterator is driven by `Parser.parse(...)`, the lexer uses the parser's current
state to restrict which token patterns are eligible at each position. Only patterns whose
token name is expected by the current parser state are considered. If no eligible pattern
matches, the lexer retries with all patterns as a fallback, so that the parser can
produce a more informative "unexpected terminal" error rather than an opaque lexing error.

#### Example

```text
tokens {
  keyword_if: /if/
  id: /[A-Za-z_][A-Za-z_0-9]*/
  num: /[0-9]+/
  /[ \t\n]+/
}
```

Input `if foo 42` tokenizes to `keyword_if("if")`, `id("foo")`, `num("42")`.

For input `iffy`, `keyword_if` and `id` both match at position 0. `id` wins because
it matches four characters (`iffy`) versus two (`if`), so the result is `id("iffy")`.

For input `if`, `keyword_if` matches two characters and `id` also matches two characters;
`keyword_if` wins because it is declared earlier.

### `precedence` section

#### Syntax

```text
precedence {
  left:  symbol, symbol, ...    // level N (lowest first)
  right: symbol, symbol, ...    // level N+1
  ...
}
```

Each line declares one precedence level. **Later lines have strictly higher precedence.**
Multiple terminals can share the same level by separating them with commas.
Symbols can be:

- Quoted literals (e.g., `'+'`), must also appear in the `grammar` section.
- Token names defined in the `tokens` section (e.g., `plus`).
- Arbitrary identifiers used only as targets for `%prec` in the `grammar` section
  (virtual tokens — they are never emitted by the lexer).

#### How precedence is applied

When a shift/reduce conflict is encountered, the parser compares:

- The precedence of the **lookahead terminal** (the token about to be shifted).
- The precedence of the **production being considered for reduction**.

A production's precedence is, by default, the precedence of its **rightmost terminal**.
This default can be overridden per-production with `%prec`.

Resolution:
- If the production has higher precedence -> reduce.
- If the lookahead has higher precedence -> shift.
- If equal precedence and `LEFT` associativity -> reduce.
- If equal precedence and `RIGHT` associativity -> shift.
- If either side has no declared precedence -> unresolved conflict (parse error at runtime).

#### Example

```text
precedence {
  left:  '+', '-'    // level 1
  left:  '*', '/'    // level 2
  right: '^'         // level 3
  right: UMINUS      // level 4, virtual token for unary minus
}
grammar {
  E : E '+' E
  E : E '-' E
  E : E '*' E
  E : E '/' E
  E : E '^' E
  E : '-' E    %prec UMINUS
  E : num
}
```

- `2 + 3 * 4` → `2 + (3 * 4)` because `*` (level 2) outranks `+` (level 1).
- `2 + 3 + 4` → `(2 + 3) + 4` because `+` is `LEFT`.
- `2 ^ 3 ^ 4` → `2 ^ (3 ^ 4)` because `^` is `RIGHT`.
- `-3 * 4` → `(-3) * 4` because `UMINUS` (level 4) outranks `*` (level 2).

### `grammar` section

#### Syntax

```text
grammar {
  Head : sym1 sym2 ...    // production with body
  Head :                  // epsilon production (empty body)
  Head : sym1 sym2 %prec TOKEN  // production with explicit precedence override
}
```

The head of the **first** rule is the **start symbol**. A non-terminal is any identifier
that appears as the head of at least one production.
Everything else in a rule body that is not a known non-terminal is treated as a terminal.
Quoted single-character or multi-character literals (e.g., `'+'`, `'+='`) are
automatically registered as terminals and added to the token list.

#### Epsilon productions

An empty right-hand side makes the production nullable:

```text
grammar {
  OptElse : 'else' Stmt
  OptElse :              // ε, OptElse can match nothing
}
```

#### Start symbol

The start symbol is always taken from the head of the very first rule across all sections.

### Comments and readability

Line comments (`//`) are allowed anywhere in the DSL, including on the same line as a rule.

```text
grammar {
  // Primary expression
  E : num

  // Binary operators
  E : E '+' E   // addition
  E : E '*' E   // multiplication
}
```

#### `%prec` override

When a production's rightmost terminal is not the right precedence anchor use `%prec`.
For example, the classic case is a unary operator sharing a terminal with a binary operator:

```text
E : '-' E    %prec UMINUS
```

`UMINUS` must appear somewhere in the `precedence` section. It does not need to correspond
to any lexer token.

#### Duplicate and self-referential rules

- Left recursion is handled correctly: `E : E '+' E` will not cause infinite loops.
- Duplicate productions (same head and same body) are rejected.

#### Multiple grammar sections

Declaring `grammar { }` (or any other section) multiple times is allowed but not recommended;
the sections are concatenated.

```text
grammar {
  Stmt : ExprStmt
  Stmt : IfStmt
}
grammar {
  ExprStmt : Expr ';'
  IfStmt   : 'if' '(' Expr ')' Stmt
}
```

Instead of using the `MetaGrammar`, one can use the programmatic API to create the tokens,
the precedence map, and the grammar from scratch.

---

## Lexing Reference (`Token`, `Lexer`, `Terminal`)

### `Token`

The class `Token` encapsulates one lexer rule: a name and a regex pattern,
or just a regex pattern for unnamed (skip) tokens.

```java
// Named token — emits a Terminal when matched
var numToken = new Token("num", "[0-9]+");

// Unnamed token — consumed silently
var wsToken = new Token("[ \t\n]+");
```

Construction validates:
- `null` name or `null` regex -> `NullPointerException`.
- Malformed regex -> `IllegalArgumentException`.
- Regex that matches the empty string → `IllegalArgumentException`. This prevents
  the lexer from looping infinitely at the same position.

Key methods:
- `name()` — returns the symbolic name, or `null` for unnamed tokens.
- `regex()` — returns the raw pattern string.
- `isIgnorable()` — returns `true` for unnamed tokens.

`Token` is a record-like type: two tokens are equal when they have the same name and regex.

### `Lexer`

The class `Lexer` converts a `CharSequence` into a lazy stream of `Terminal` objects.

```java
Lexer lexer = Lexer.createLexer(tokens);  // thread-safe, share freely
Iterator<Terminal> iterator = lexer.tokenize("12 + 34");
```

The returned `Iterator<Terminal>` is lazy: input is scanned only when `hasNext()` or
`next()` is called. The `Lexer` itself is immutable and thread-safe; you can call
`tokenize(...)` from multiple threads simultaneously.

#### Matching rules (detailed)

At each position the lexer:

1. Attempts every pattern (or the eligible subset when driven by a parser state).
2. Selects the pattern with the **longest match**. If two patterns tie, the one
   declared **earlier** in the list wins.
3. If the winning pattern is unnamed, its matched text is skipped and matching
   resumes immediately after it.
4. If the winning pattern is named, a `Terminal(name, matchedText)` is returned.
5. If no pattern matches and the input position is not at the end, a `Terminal.ERROR`
   terminal is returned and iteration halts.

#### Position tracking

`Lexer.position(iterator)` returns the start character index (zero-based) of the most
recently returned terminal. It returns `-1` if the iterator is not created by `Lexer.tokenize(...)`
(e.g., a plain `List.iterator()`).

```java
var iterator = lexer.tokenize("42 + 113");
iterator.next();                          // "42"
System.out.println(Lexer.position(iterator));  // 0
iterator.next();                          // "+"
System.out.println(Lexer.position(iterator));  // 3
```

This is primarily used inside `Evaluator.evaluate(Terminal)` to attach source positions
to AST nodes. Use `mg.parse(input, visitorFactory)` so the visitor receives the same
iterator instance that the parser is consuming.

### `Terminal`

The class `Terminal` is immutable and represents either:
- A **grammar-level placeholder** (no value): `new Terminal("num")`, used in `Production` bodies.
- A **lexer-produced token** (with value): `new Terminal("num", "42")`, produced by `Lexer`.

Construction validates:
- `null` name -> `NullPointerException`.
- Empty name -> `IllegalArgumentException`.
- `null` value for a named token -> `NullPointerException`.

Two terminals are **equal if their names match**, regardless of value. This is what allows
a lexer-produced `Terminal("num", "42")` to match the grammar placeholder `Terminal("num")`
in the parser's action table.

```java
assertEquals(new Terminal("num", "42"), new Terminal("num"));  // true
assertEquals(new Terminal("num", "1"),  new Terminal("num", "2")); // also true
```

Special sentinel terminals (used internally):
- `Terminal.EOF` (`"$"`), appended by the parser after the last user token.
- `Terminal.EPSILON` (`"ε"`), used internally by the parser to compute the LR states.
- `Terminal.ERROR` (`"error"`), returned by the lexer on no-match.

---

## Parsing Reference (`Grammar`, `Parser`, `ParserFactory`)

### `Grammar`

The class `Grammar` is an immutable, validated collection of productions plus a start symbol.

```java
var E    = new NonTerminal("E");
var plus = new Terminal("+");
var num  = new Terminal("num");

var grammar = new Grammar(E, List.of(
    new Production(E, List.of(E, plus, E)),   // E : E + E
    new Production(E, List.of(num))           // E : num
));
```

Construction validates:
- The start symbol must be the head of at least one production.
- Every non-terminal that appears in a rule body must itself have at least one production.
- Duplicate productions (same head and same body) are rejected.

`Grammar` is thread-safe and can be reused across many parsers and threads.

### `NonTerminal`

The class `NonTerminal` is immutable and represents an abstract grammatical construct,
the left-hand side of one or more productions.

```java
var expr = new NonTerminal("expr");
var stmt = new NonTerminal("stmt");
```

Constructor validation:
- `null` name -> `NullPointerException`.
- Empty name -> `IllegalArgumentException`.

Key method:
- `name()` — returns the identifier string passed at construction (e.g., `"expr"`, `"stmt"`).

Two non-terminals are **equal if their names match**:
```java
assertEquals(new NonTerminal("expr"), new NonTerminal("expr")); // true
assertNotEquals(new NonTerminal("expr"), new Terminal("expr")); // different types
```

`NonTerminal` implements `Symbol`, the same sealed interface as `Terminal`.
When iterating over a `production.body()`, use a pattern switch to distinguish the two:

```java
for (var symbol : production.body()) {
  switch (symbol) {
    case Terminal t -> System.out.println("terminal: " + t.name());
    case NonTerminal nt -> System.out.println("non-terminal: " + nt.name());
  }
}
```


### `Production`

The class `Production` is immutable and represents one grammar rule.

```java
var prod = new Production(E, List.of(E, plus, E));
System.out.println(prod.name());  // "E : E + E"
System.out.println(prod.head());  // NonTerminal(E)
System.out.println(prod.body());  // [NonTerminal(E), Terminal(+), ...]
```

`production.name()` is the canonical string identifier used in `Evaluator` switch
statements and `@ProductionName` annotations. Its format is always
`"Head : sym1 sym2 ..."`, or `"Head : ε"` for epsilon productions.

`Production` also implements `PrecedenceEntity` so it can appear as a key in the
precedence map (used by `%prec` overrides).

### `Parser`

The class `Parser` is the main parsing engine. It maintains mutable internal state during a
`parse()` call, and that state (the action and transition caches) accumulates across
multiple calls to avoid recomputing LR(1) states.

```java
var parser = Parser.createParser(grammar, precedenceMap);

// First call — may build new LR(1) states
var result1 = parser.parse(input1, evaluator);

// Second call — reuses cached states built during the first call
var result2 = parser.parse(input2, evaluator);
```

`Parser` supports two parse entry points:

```java
// Fires events, produces no value
void parse(Iterator<Terminal> input, ParserListener listener)

// Builds and returns a semantic value
<V> V parse(Iterator<Terminal> input, Evaluator<V> evaluator)
```

#### Augmented grammar

Internally, the parser adds an augmented production `S' → S` (where `S` is the declared
start symbol). This production fires exactly once, as the final reduce event at the end
of a successful parse. `ParserListener` users will see this reduce. `Evaluator` users do
not need to handle it; the `parse()` method extracts the result of the start symbol
automatically.

#### Coverage

```java
Set<Production> covered = parser.coverage();
```

Returns the set of productions reduced at least once across all `parse()` calls on this
instance. The set grows monotonically and is unmodifiable. Useful in tests to verify that
all grammar rules are exercised.

### `ParserFactory`

`ParserFactory` separates the one-time cost of computing FIRST sets and the completed
precedence map from the per-thread cost of creating a parser.

```java
// Shared, created once, thread-safe
static final ParserFactory FACTORY = ParserFactory.createFactory(grammar, precedenceMap);

// Per-thread, call createParser() on the thread that will do the parsing
var parser = FACTORY.createParser();
```

Calling `FACTORY.createParser()` is cheap. All computationally intensive static analysis
is done by `createFactory(...)` and shared.

#### Thread ownership

Each `Parser` is permanently **bound to the thread that created it** via `createParser()`.
Calling `parse()` from any other thread throws `WrongThreadException`. This applies to
both platform threads and virtual threads.

---

## Semantic Actions

### `ParserListener`

The lowest-level hook. It observes shifts and reduces without producing a value:

```java
class PrintParserListener implements ParserListener {
  @Override
  public void onShift(Terminal token) {
    System.out.println("Shift " + token.name() + "=" + token.value());
  }
  @Override
  public void onReduce(Production production) {
    System.out.println("Reduce " + production.name());
  }
}
...
parser.parse(inputText, new PrintParserListener());
```

Events fire bottom-up: for a production `E : E '+' E`, both inner `onShift` and inner
`onReduce` events for both `E` children fire before `onReduce(E : E + E)`.

The start position of the current terminal during `onShift` is available via
`Lexer.position(iterator)` if the iterator was produced by `Lexer.tokenize(...)`.

### `Evaluator<V>`

`Evaluator` is a functional interface that builds a result of type `V` while parsing.
It has two methods:

```java
V evaluate(Terminal terminal);                         // called on each shift
V evaluate(Production production, List<V> arguments);  // called on each reduce
```

`arguments` contains one entry per symbol in `production.body()`, in left-to-right order.
Terminals for which `evaluate(Terminal)` returned `null` still occupy their position in
`arguments` as `null`.

`V` may be `null`-typed (e.g., `Evaluator<@Nullable Object>`). Returning `null` from
`evaluate(Terminal)` is common for punctuation tokens that carry no semantic value.

#### Example 1: direct integer evaluation

```java
class IntEvaluator implements Evaluator<Integer> {
  @Override
  public Integer evaluate(Terminal t) {
    return "num".equals(t.name()) ? Integer.parseInt(t.value()) : null;
  }
  @Override
  public Integer evaluate(Production p, List<Integer> a) {
    return switch (p.name()) {
      case "E : num"   -> a.get(0);
      case "E : E + E" -> a.get(0) + a.get(2);  // a.get(1) is null (the '+')
      case "E : E * E" -> a.get(0) * a.get(2);
      default -> throw new IllegalStateException(p.name());
    };
  }
}
...
mg.parse(inputText, new IntEvaluator());
```

#### Example 2: build a parenthesized string

```java
class TextEvaluator implements Evaluator<String> {
  @Override
  public String evaluate(Terminal t) {
    return "num".equals(t.name()) ? t.value() : null;
  }
  @Override
  public String evaluate(Production p, List<String> a) {
    return switch (p.name()) {
      case "E : num"   -> a.get(0);
      case "E : E + E" -> "(" + a.get(0) + " + " + a.get(2) + ")";
      case "E : E * E" -> "(" + a.get(0) + " * " + a.get(2) + ")";
      default -> throw new IllegalStateException(p.name());
    };
  }
}
...
mg.parse(inputText, new TextEvaluator());
```

#### Exception propagation

Any `RuntimeException` thrown by either `evaluate` method propagates out of `parse()`.
After an evaluator exception, the parser remains reusable.

### `Visitor<V>` (reflection-backed convenience)

`Visitor<V>` lets you write one typed method per terminal or production instead of a central `switch`.
`Visitor.reflect(lookup, visitor)` inspects all public methods of the visitor and
builds an `Evaluator` from them.
The `Lookup` object taken as parameter should be created by `MethodHandles.lookup()`
at a location in the code where the `Visitor` class is visible.
Unlike `Visitor.reflect(...)`, the convenient method `mg.parse(input, visitor)` computes
the `Lookup` object via a stack walk, so the visitor class has to be visible from the
caller of the method `mg.parse(input, visitor)`.

#### Terminal methods

A public, non-void, non-static method whose **name equals a token name** is called when
that token is shifted. The method must take exactly one `Terminal` parameter.

```java
public Node num(Terminal t) {
  return new NumLit(Integer.parseInt(t.value()));
}
```

If no method matches a terminal name, `null` is returned for that terminal.

#### Production methods

A public, non-void, non-static method annotated with `@ProductionName` is called when
the named production is reduced. The value of the annotation must match `production.name()`
exactly.

```java
@ProductionName("E : E + E")
public Node add(Node left, Node right) {
  return new BinaryOp("+", left, right);
}
```

Parameters correspond to the evaluated values of the body symbols, in left-to-right order.
Terminals for which no terminal method was defined are **filtered out** and
do not appear as parameters.

> **Important:** if a terminal method exists, its value *does* appear as a parameter.
> This lets you use terminal values directly in production methods:
>
> ```java
> public boolean kw_else(Terminal t) { return true; } // contributes a boolean param
>
> @ProductionName("Stmt : if ( Expr ) Stmt else Stmt")
> public Stmt ifElse(Expr cond, Stmt then, boolean elseValue, Stmt else_) { ... }
> ```

#### Exception propagation

Any `RuntimeException` thrown by either a terminal method or a production method
propagates out of `parse()`. Checked exceptions are wrapped in `UndeclaredThrowableException`.
After an exception, the parser can be reused.

#### Single-body pass-through

If a production has exactly one symbol in its body and **no `@ProductionName` method** is
defined for it, the single argument is forwarded automatically.
This covers chain productions like `E : num` without requiring any code:

```java
// No method needed for "E : num" — the Num node is passed straight through.
```

#### Repeatable `@ProductionName`

One method can handle multiple productions by stacking the annotation:

```java
@ProductionName("E : E + E")
@ProductionName("E : E - E")
public int addOrSub(int a, int b) { return ...; }
```

All listed production names are routed to the same method.

#### Validation at reflection time

`Visitor.reflect(...)` validates all public methods on the visitor class immediately:

- Return type `void` -> `IllegalStateException`.
- Non-annotated method, terminal method, with parameter count != 1 or
  parameter type != `Terminal` -> `IllegalStateException`.
- Two methods annotated with the same production name -> `IllegalStateException`.
- Static methods or private methods in the visitor class are silently ignored.

Primitive return types and parameter types are accepted and handled transparently via
boxing/unboxing.

#### Missing production message

When `parse()` encounters a reduction for a production that has no handler, the thrown
`IllegalStateException` includes a skeleton of the missing method with inferred types:

```
production "E : E + E" has no evaluator method, proposed code:
@ProductionName("E : E + E")
public int method(int param0, int param1) {
  throw new UnsupportedOperationException("TODO");
}
```

The inferred types are derived from the return types of other visitor methods for the
same non-terminals.

#### Accessing source positions inside a visitor

```java
class PositionVisitor implements Visitor<Node> {
  private final Iterator<Terminal> input;

  PositionVisitor(Iterator<Terminal> input) {
    this.input = input;
  }

  public Node num(Terminal t) {
    int pos = Lexer.position(input);   // start offset in the input string
    return new NumLit(Integer.parseInt(t.value()), pos);
  }
  // ...
}

// Use the factory overload so the visitor gets the same iterator
Node ast = mg.parse("1 + 2", PositionVisitor::new);
```

---

## Precedence, Associativity, and Conflict Resolution

### Why conflicts arise

The grammar `E : E '+' E` is ambiguous: given `1 + 2 + 3`, the parser cannot tell
(without extra information) whether to reduce `1 + 2` first or `2 + 3` first.
This creates a shift/reduce conflict: after shifting `1`, `+`, `2`, the parser has `E + E`
on its stack and sees another `+` as the lookahead.
Should it reduce `E + E` to `E` (left-grouping), or shift the next `+` (right-grouping)?

Similarly, `E : E '+' E` and `E : E '*' E` conflict: after `1 + 2`, seeing `*` as
lookahead, should the parser reduce `1 + 2` first or shift `*` and bind `2` more tightly?

### Declaring precedence

```text
precedence {
  left: '+', '-'   // level 1
  left: '*', '/'   // level 2
  right: '^'       // level 3
}
```

Rules:
- **Later lines = higher precedence level.**
- `left` means equal-level conflicts resolve by reducing (left-associativity).
- `right` means equal-level conflicts resolve by shifting (right-associativity).
- Multiple terminals on one line share the same level.

### The `%prec` directive

By default, a production's precedence is that of its **rightmost terminal**. For a
production like `E : '-' E`, the rightmost terminal is `-`, which is declared at the
same level as binary subtraction. This causes unary minus to bind at the wrong level.

The `%prec TOKEN` directive assigns a different precedence to the whole production:

```text
precedence {
  left: '+', '-'
  left: '*'
  right: UMINUS     // virtual token — never emitted
}
grammar {
  E : '-' E    %prec UMINUS
}
```

Now the unary production has precedence level 3 (UMINUS), so when the parser has
`'-' E` on its stack and sees `*` (level 2), it reduces (binds unary minus tighter
than multiplication).

`%prec` can also be applied to productions that use named tokens:
```text
E : 'if' Expr 'then' Stmt    %prec kw_if
E : 'if' Expr 'then' Stmt 'else' Stmt
```

Here `%prec kw_if` gives the `if-then` production an explicit precedence so that the
dangling-else ambiguity is resolved in favor of binding `else` to the nearest `if`.

### Example 1: arithmetic with a full precedence table

```text
precedence {
  left: '+', '-'
  left: '*', '/'
  right: '^'
  right: UMINUS
}
grammar {
  E : E '+' E
  E : E '-' E
  E : E '*' E
  E : E '/' E
  E : E '^' E
  E : '-' E    %prec UMINUS
  E : num
}
```

- `2 + 3 * 4` -> `2 + (3 * 4)` = 14
- `2 + 3 + 4` -> `(2 + 3) + 4` = 9
- `2 ^ 3 ^ 2` -> `2 ^ (3 ^ 2)` = 512
- `-3 * 4`    -> `(-3) * 4`    = -12

### Example 2: assignment (right-associative)

```text
precedence {
  left:  '+', '-', '*', '/'
  right: '='
}
grammar {
  E : id '=' E   // assignment
  E : E '+' E
  E : id
  E : num
}
```

`a = b = 0` parses as `a = (b = 0)`.

### Example 3: dangling else

```text
precedence {
  right: kw_if       // level 1
  right: kw_else     // level 2, higher, so else binds to nearest if
}
grammar {
  Stmt : kw_if '(' Expr ')' Stmt                %prec kw_if
  Stmt : kw_if '(' Expr ')' Stmt kw_else Stmt
  Stmt : Expr ';'
}
```

`if (x) if (y) {} else {}` parses as `if (x) { if (y) {} else {} }`.

---

## Error Reporting and Recovery Strategy

### Exception types

All lexing and parsing failures surface as `ParsingException`. This is a
`RuntimeException`, so callers are not forced to declare it.

### Error message format

LazyLR generates detailed error messages when the parser is consuming
an iterator from a `Lexer`.

The message includes:
```
Parsing error at line 2, column 6: unexpected terminal '+', expected id
id + +
     ^
```

Components:
- Error type (lexing or parsing) and character classification.
- Line number (1-based) and column number (1-based).
- The offending terminal name and the set of expected terminals at that point.
- The full content of the offending line.
- A caret `^` pointing to the exact column.

When the iterator is not provided by the `Lexer`, the message
contains the terminal names but no line/column/caret information.

### Lexing errors

A lexing error occurs when no token pattern matches the current input position:

```
Lexing error at line 1, column 1: unexpected character '@'
@foo
^
```

The error message includes the character using a human-readable representation:
- Printable characters: `'@'`
- Whitespace: `'\n'`, `'\r'`, `'\t'`, `' '`
- Control characters: `'\u001B'`

### Parsing errors

A parsing error occurs when the parser receives a terminal that has no valid action in
the current state. The expected terminals in the error message are computed from the
current LR(1) state items:

- If the next symbol after the dot in a non-complete item is a terminal, it is expected.
- If an item is complete (reduce), its lookahead terminal is expected.

End-of-file (`$`) is displayed as `<end of file>` for readability.

### Recovery strategy

LazyLR does not implement panic-mode recovery or grammar-level `error` tokens.
The recommended strategy is:

1. Wrap `parse()` in a try-catch for `ParsingException`.
2. Report the error with line/column context from the message.
3. For incremental parsing (e.g., a REPL), create a fresh `Parser` for each independent
   top-level unit and virtually add an `eof` token to the input iterator.

After a `ParsingException`, the `Parser` instance is guaranteed to be in a clean state,
so it can be re-used.

---

## Automaton Inspection and Verification (`LALRVerifier`)

### When to run verification

Call `mg.verify()` during development any time the grammar changes. In CI pipelines,
add a test that calls `mg.verify(errorReporter)` and asserts no errors are reported.

### Verification overloads

```java
// Print conflicts to System.err; print automaton to System.err only on conflict
mg.verify();

// Always print the full automaton to System.out; conflicts go to System.err
mg.verify(true);

// Route conflicts to a callback; never print automatically
var errors = new ArrayList<String>();
mg.verify(errors::add);

// Full control: destination stream, always-print flag, error callback
var errors = new ArrayList<String>();
var out = System.out;
var alwaysPrint = true;
LALRVerifier.verify(grammar, precedenceMap, out, alwaysPrint, errors::add);
```

### Understanding automaton output

Each state is printed as a block. Here is an annotated example:

```
── State 4 ─────────────────────────────────
   E :  E + E •         <- reduce item (dot at end)
   E :  E • + E         <- shift item (dot before '+')
  ······································
   goto( +  ) → 3 🔥    <- shift on '+', but there is also a reduce on '+'
   reduce( E : E + E ) on [$, + 🔥]
```

Symbols:
- `•` marks the current dot position in an item.
- `goto(X) → N` is the transition on symbol `X` to state `N`.
- `reduce(P) on [a, b, ...]` means production `P` is reduced when the lookahead is any of `a`, `b`, ...
- `accept()` fires when the input is fully consumed and the start symbol has been reduced.
- `🔥` marks an **unresolved conflict** (neither side wins; runtime will throw `ParsingException`).
- `❌` marks a **resolved conflict** where that action was **not** selected
  (the winning action is shown without annotation).
  For example, a shift `❌` means the reduce won via precedence.

### LALR(1) vs LR(1) caveats

`LALRVerifier` runs an LALR(1) analysis (DeRemer & Pennello algorithm). The runtime
`Parser` is LR(1). Some grammars that are LR(1) but not LALR(1) will show
reduce/reduce conflicts in the verifier output, yet parse correctly at runtime.
These grammars pass through `Parser` without errors.

The reason to do a LALR(1) analysis and not a full LR(1) analysis is that, on large
grammars, doing a full LR(1) analysis takes too much time.
In production, you should still resolve all reported conflicts from the LALR(1) analysis for safety.

---

## Command-Line Tool

### Usage

```
lazylr [--generate | --print] <grammar-file> [input-file]
```

All output goes to stdout for normal results and to stderr for errors and conflict
diagnostics.

### Modes

Mode **Validation (default):**

```bash
lazylr grammar.txt
```

Parses and validates the grammar. On conflict, prints the LALR(1) automaton to stderr
and exits with code 2. On success, exits silently with code 0.

Mode **Unconditional automaton printing:**

```bash
lazylr --print grammar.txt
```

Validates the grammar and always prints the full LALR(1) automaton to stdout. Conflicts
are additionally reported to stderr. Exit code 0 if conflict-free, 2 if conflicts remain.

Mode **Java source generation:**

```bash
lazylr --generate grammar.txt
```

Validates the grammar, then emits a Java source snippet with a static `createGrammar()`
method to stdout. The generated code reconstructs the exact same `MetaGrammar`
programmatically. Exit code 0 on success, 2 on conflicts.

Mode **Parse and print derivation tree:**

```bash
lazylr grammar.txt input.txt
```

Validates the grammar, lexes and parses the input file, then prints the parse tree:

```
└── <E>
    ├── <E>
    │   └── [num=1]
    ├── [+]
    └── <E>
        └── [num=2]
```

Terminal nodes are shown as `[name=value]` when name ≠ value, or `[name]` when name == value
(e.g., operator literals). Non-terminal nodes are shown as `<Name>`. Exit code 0 on success,
1 on parse failure.

### Exit codes

| Code | Meaning                                                       |
|------|---------------------------------------------------------------|
| 0    | Success                                                       |
| 1    | CLI error, missing/unreadable file, or parse failure on input |
| 2    | Grammar has unresolved LALR(1) conflicts                      |

### Constraints

- `--print` and `--generate` are mutually exclusive.
- `--print` and `--generate` cannot be combined with an input file.

---

## Code Generation

The generated output is a standalone static method named `createGrammar()` that returns
a fully constructed `MetaGrammar`. It faithfully reproduces:

- All non-terminal variable declarations.
- All terminal variable declarations (with collision-safe names for symbols like `+`, `*`).
- All production declarations, referencing the non-terminal and terminal variables above.
- All `Token` objects from the `tokens` section, using `Pattern.quote(...)` for
  quoted literals and raw regex strings for named tokens.
- The `precedence` map, using a `LinkedHashMap` to preserve declaration order.
- The `Grammar` object with its start symbol and production list.

```java
// Example output structure:
import com.github.forax.lazylr.*;

public static MetaGrammar createGrammar() {
  // Non-terminals
  var nt_E = new NonTerminal("E");

  // Terminals
  var t_num = new Terminal("num");
  var t__   = new Terminal("+");   // sanitized name for '+'

  // Productions
  var p_0 = new Production(nt_E, List.of(t_num));
  var p_1 = new Production(nt_E, List.of(nt_E, t__, nt_E));

  // Grammar
  var startSymbol = nt_E;
  var grammar = new Grammar(startSymbol, List.of(p_0, p_1));

  // Tokens
  var tokens = List.of(
    new Token("+", Pattern.quote("+")),  // name and regex are the same
    new Token("num", "[0-9]+")
  );

  // Precedence map
  var precedenceMap = new LinkedHashMap<PrecedenceEntity, Precedence>();
  precedenceMap.put(t__, new Precedence(1, Precedence.Associativity.LEFT));

  return new MetaGrammar(tokens, precedenceMap, grammar);
}

static void main() {
  var mg = createGrammar();
  mg.verify();
}
```

Terminal or non-terminal variable names are derived from the name with non-alphanumeric
characters replaced by `_`. Collisions (e.g., multiple operators mapping to `t__`) are
resolved by appending a numeric suffix (`t__1`, `t__2`, ...).

---

## Threading, Reentrancy, and Performance

### Thread safety summary

| Class                                   | Thread safety                                                            |
|-----------------------------------------|--------------------------------------------------------------------------|
| `MetaGrammar`                           | Immutable; fully thread-safe                                             |
| `Grammar`                               | Immutable; fully thread-safe                                             |
| `Token`                                 | Immutable; fully thread-safe                                             |
| `Lexer`                                 | Immutable; fully thread-safe; `tokenize()` may be called from any thread |
| `Terminal`, `NonTerminal`, `Production` | Immutable; fully thread-safe                                             |
| `Precedence`                            | Immutable; fully thread-safe                                             |
| `ParserFactory`                         | Immutable; fully thread-safe                                             |
| `Parser`                                | Bound to its creating thread; **not** thread-safe                        |
| `Iterator<Terminal>` (from `Lexer`)     | Single-threaded; one per parse call                                      |

### Thread ownership of `Parser`

Each `Parser` is permanently bound to the thread that created it. Calling `parse()`
from a different thread — even if no other thread is currently using the parser — throws
`WrongThreadException`. This applies to both platform threads and virtual threads (a
parser created on virtual thread A cannot be used on virtual thread B, even if B runs
on the same carrier thread).

### Correct multithreaded patterns

**Pattern 1: one parser per request (stateless service)**

```java
// Shared
static final MetaGrammar MG = MetaGrammar.load(grammarText);
static final ParserFactory FACTORY = ParserFactory.createFactory(MG.grammar(), MG.precedenceMap());
static final Lexer LEXER = Lexer.createLexer(MG.tokens());

// Per request (e.g., in a servlet or virtual thread handler)
void handleRequest(String input) {
  var parser = FACTORY.createParser();   // cheap
  var result = parser.parse(LEXER.tokenize(input), evaluator);
  // ...
}
```

**Pattern 2: `ParserFactory` with many concurrent virtual threads**

```java
try (var scope = StructuredTaskScope.open()) {
  for (var input : inputs) {
    scope.fork(() -> {
      var parser = FACTORY.createParser();  // bound to this virtual thread
      return parser.parse(LEXER.tokenize(input), evaluator);
    });
  }
  scope.join();
}
```

### Performance notes

- `ParserFactory.createFactory(...)` runs FIRST-set computation and precedence
  completion. This is the most expensive step; do it once at startup.
- `ParserFactory.createParser()` only computes the initial LR(1) state (closure of the
  augmented start item). It is inexpensive.
- `Parser` caches LR(1) states lazily; the first parse of a grammar explores more states
  than later parses. For large grammars with many distinct token streams,
  warm-up parses improve throughput.
- The lexer is lazy; it only scans as much input as the parser demands. Context-sensitive
  lexing further reduces work by skipping ineligible patterns.
- The `Lexer` iterator caches the set of eligible token indices per parser state,
  so the per-token pattern selection cost is amortized over repeated states.

---

## End-to-End Recipes (with JUnit)

### Recipe A: grammar verification test

```java
import module java.base;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public final class GrammarValidationTest {
  @Test
  public void grammarIsLALR1() {
    // not tokens section is needed when it's a pure grammar
    var mg = MetaGrammar.load("""
      precedence {
        left: '+'
      }
      grammar {
        E : E '+' E
        E : num
      }
      """);
    assertDoesNotThrow(mg::verify);
  }

  @Test
  public void ambiguousGrammarIsDetected() {
    var mg = MetaGrammar.load("""
      grammar {
        E : E '+' E
        E : num
      }
      """);
    var errors = new ArrayList<String>();
    mg.verify(errors::add);
    assertFalse(errors.isEmpty());
    assertTrue(errors.getFirst().contains("shift/reduce"));
  }
}
```

### Recipe B: parser result test with visitor

```java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public final class ParseResultTest {
  sealed interface Node {}
  record Num(int value) implements Node {}
  record Add(Node l, Node r) implements Node {}
  record Mul(Node l, Node r) implements Node {}

  static final class NodeVisitor implements Visitor<Node> {
    public Node num(Terminal t) { return new Num(Integer.parseInt(t.value())); }
    @ProductionName("E : E + E")
    public Node add(Node l, Node r) { return new Add(l, r); }
    @ProductionName("E : E * E")
    public Node mul(Node l, Node r) { return new Mul(l, r); }
  }

  static final MetaGrammar MG = MetaGrammar.load("""
    tokens {
      num: /[0-9]+/
      /[ ]+/
    }
    precedence {
      left: '+'
      left: '*'
    }
    grammar {
      E : num
      E : E '+' E
      E : E '*' E
    }
    """);

  @Test
  public void additionIsLeftAssociative() {
    var ast = MG.parse("1 + 2 + 3", new NodeVisitor());
    assertEquals(new Add(new Add(new Num(1), new Num(2)), new Num(3)), ast);
  }

  @Test
  public void multiplicationBindsTighterThanAddition() {
    var ast = MG.parse("2 + 3 * 4", new NodeVisitor());
    assertEquals(new Add(new Num(2), new Mul(new Num(3), new Num(4))), ast);
  }
}
```

### Recipe C: expected parse failure test

```java
import module java.base;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public final class ParseFailureTest {
  static final MetaGrammar MG = MetaGrammar.load("""
    tokens {
      num: /[0-9]+/
      /[ ]+/
    }
    precedence {
      left: '+'
    }
    grammar {
      E : num
      E : E '+' E
    }
    """);

  static final class NoOpEvaluator implements Evaluator<Object> {
    public Object evaluate(Terminal t) { return null; }

    public Object evaluate(Production p, List<Object> a) { return null; }
  }
  
  @Test
  public void reportsLexErrorForUnknownCharacter() {
    var ex = assertThrows(ParsingException.class,
        () -> MG.parse("1 + @", new NoOpEvaluator()));
    var message = ex.getMessage();
    assertTrue(message.contains("Lexing error"));
    assertTrue(message.contains("'@'"));
  }

  @Test
  public void reportsParseErrorWithPosition() {
    var ex = assertThrows(ParsingException.class,
        () -> MG.parse("1 + +", new NoOpEvaluator()));
    var message = ex.getMessage();
    assertTrue(message.contains("Parsing error"));
    assertTrue(message.contains("column 5"));
  }
}
```

### Recipe D: coverage assertion

```java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public final class CoverageTest {
  @Test
  public void allProductionsExercised() {
    var mg = MetaGrammar.load("""
      tokens {
        num: /[0-9]+/
        /[ ]+/
      }
      precedence {
        left: '+'
        left: '*'
      }
      grammar {
        E : num
        E : E '+' E
        E : E '*' E
      }
      """);
    var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());
    var noop = new ParserListener() {
      public void onShift(Terminal t) {}
      public void onReduce(Production p) {}
    };
    parser.parse(Lexer.createLexer(mg.tokens()).tokenize("1 + 2 * 3"), noop);
    var allProductions = mg.grammar().productions();
    assertTrue(parser.coverage().containsAll(allProductions));
  }
}
```

---

## Troubleshooting Checklist

### Lexing failure ("My token never matches" or `Terminal.ERROR` returned)

- Check that your regex cannot match the empty string; such patterns are rejected at
  construction time, so this would cause a build failure, not a runtime failure.
- Check longest-match interactions. If `id: /[a-z]+/` is declared before `if: /if/`,
  `if` will be returned as `id("if")`, not as `keyword_if("if")`, because both patterns
  match two characters and `id` is declared first.
- Make sure a whitespace/comment unnamed token is present.
  Without it, the first non-matching character causes a `Terminal.ERROR`.
- Quoted literals in the `grammar` section are added to the token list **before**
  named tokens from `tokens`. To make a keyword take priority over an identifier
  pattern, either declare the keyword first in `tokens`, or rely on the automatic
  literal promotion.

### Shift/reduce conflicts ("I get shift/reduce conflicts")

- Add a `precedence` section with `left:` or `right:` lines for ambiguous operators.
  Later lines = higher precedence.
- Use `%prec TOKEN` on the production if its rightmost terminal has the wrong precedence
  (classic case: unary operators sharing a terminal with a binary operator).
- Run `mg.verify(true)` or `lazylr --print grammar.txt` to see the full automaton with
  conflict markers (`🔥`).
- A `🔥` on a `goto(...)` line means the shift side of a conflict; a `🔥` on a
  `reduce(...) on [...]` line means the reduce side. Both sides of the same conflict
  carry the `🔥` mark.

### Visitor method isn't called

- Terminal handler method name must equal the **token name** exactly (case-sensitive).
  If the lexer produces `Terminal("kw_if", "if")`, the method must be named `kw_if`.
- Production handler annotation must use the **exact text** of `production.name()`.
  Verify by printing `production.name()` or checking the missing-method error message;
  it always includes the correct string.
- The return type must not be `void`. Returning `null` is fine;
  `void` is rejected at the visitor creation time.
- If a terminal method unconditionally returns `null`, that terminal's value is passed
  as a parameter to production methods. You want to remove that method.
- Static and private methods are silently ignored. Public instance methods are required.

### `WrongThreadException` at parse time

- Each `Parser` is permanently bound to the thread that called `createParser()`.
- Never share a `Parser` between threads. Each thread must call `createParser()` to
  get its own parser.
- Share `MetaGrammar`, `Grammar`, `Lexer`, and `ParserFactory` freely across threads.
- When using virtual threads, the parser is bound to the virtual thread, not to the
  carrier platform thread. Two virtual threads running on the same carrier still cannot
  share a parser.

### Grammar accepted at runtime but the verifier fails on it

`LALRVerifier` uses LALR(1); the runtime parser uses LR(1). A small set of grammars
are LR(1) but not LALR(1). If the verifier reports a conflict but actual parsing
succeeds, the grammar is in this category.
It is not recommended to have a grammar that is not LALR(1) in a production
environment, but for toy examples, this is usually acceptable.

---

## ANTLR-to-LazyLR Mapping Guide

ANTLR and LazyLR can describe similar languages but differ in parser strategy
(LL(*) vs LR(1)), grammar style, and action mechanism.

### 1) Lexer rules migration

ANTLR:
```antlr
ID  : [a-zA-Z_][a-zA-Z_0-9]* ;
INT : [0-9]+ ;
WS  : [ \t\r\n]+ -> skip ;
```

LazyLR:
```text
tokens {
  ID:  /[A-Za-z_][A-Za-z_0-9]*/
  INT: /[0-9]+/
  /[ \t\r\n]+/
}
```

Migration notes:
- `-> skip` maps to an unnamed regex token (no name, no Terminal emitted).
- ANTLR uses implicit priority (longer match, then rule order); LazyLR uses the same
  longest-match-then-declaration-order rule.
- ANTLR lexer modes have no direct equivalent; simulate with context-sensitive lexing
  by structuring the grammar so that the parser state determines which tokens are valid.

### 2) Parser rules migration

ANTLR (precedence by rule layering):
```antlr
expr
    : expr '*' expr  # MulExpr
    | expr '+' expr  # AddExpr
    | INT            # IntExpr
    ;
```

LazyLR:
```text
precedence {
  left: '+'
  left: '*'
}
grammar {
  Expr : Expr '+' Expr
  Expr : Expr '*' Expr
  Expr : INT
}
```

Migration notes:
- ANTLR encodes precedence via rule-alternative ordering (earlier = higher precedence
  within a rule) and numeric `<assoc=right>` annotations. LazyLR uses an explicit
  `precedence` section — convert the implicit ordering into explicit levels.
- ANTLR supports rule labels (`# MulExpr`); these become `@ProductionName` annotations
  in LazyLR.
- ANTLR rule alternatives with no explicit precedence annotation are unambiguous in
  ANTLR's LL(*) framework; they may need precedence annotations in LazyLR's LR framework.
  Run `mg.verify()` to discover them.

### 3) Actions and visitors migration

| ANTLR mechanism                          | LazyLR equivalent                                    |
|------------------------------------------|------------------------------------------------------|
| Embedded actions `{ ... }` in grammar    | `Evaluator<V>.evaluate(Production, List<V>)`         |
| `ParseTreeListener.enterXxx` / `exitXxx` | `ParserListener.onShift` / `onReduce`                |
| Generated `Visitor.visitXxx(ctx)`        | `@ProductionName("...") method(...)` in `Visitor<V>` |
| `ctx.expr(0)`, `ctx.INT()`               | `arguments.get(0)`, `arguments.get(2)` in evaluator  |

Example migration:

ANTLR visitor:
```java
@Override
public Integer visitAddExpr(MyParser.AddExprContext ctx) {
    return visit(ctx.expr(0)) + visit(ctx.expr(1));
}
```

LazyLR visitor:
```java
@ProductionName("Expr : Expr + Expr")
public int addExpr(int left, int right) {
    return left + right;
}
```

### 4) Left recursion and ambiguity

ANTLR 4 supports left-recursive rules and resolves operator precedence within a single
rule using alternative ordering. LazyLR handles left recursion naturally (LR parsers are
designed for it) but requires an explicit `precedence` section for ambiguous grammars.
Any grammar that is unambiguous in ANTLR because of alternative ordering should be
accompanied by a corresponding `precedence` declaration in LazyLR.

### 5) Error handling migration

| ANTLR mechanism                            | LazyLR equivalent                       |
|--------------------------------------------|-----------------------------------------|
| `ANTLRErrorListener`                       | Catch `ParsingException` at call site   |
| `reportAmbiguity(...)`                     | `mg.verify(errors::add)` in tests       |
| `reportAttemptingFullContext(...)`         | Not applicable (LR has no backtracking) |
| Parser error recovery (`error` token rule) | Not built-in; catch and re-parse        |
| `ctx.exception` on parse tree nodes        | `ParsingException.getMessage()`         |

### 6) Practical migration checklist

1. Convert ANTLR lexer fragments and tokens to LazyLR `tokens` section entries.
2. Convert ANTLR `-> skip` and `-> channel(HIDDEN)` to unnamed token rules.
3. Port ANTLR parser rules to the `grammar` section.
4. For each rule with multiple alternatives ordered by precedence in ANTLR,
   add corresponding entries to the LazyLR `precedence` section.
5. Run `mg.verify()` and resolve any reported conflicts.
6. Port semantic logic: embedded actions -> `Evaluator`; generated visitors -> `Visitor<V>`.
7. Port error listeners to try-catch blocks on `parse()`.
8. Add JUnit tests for representative successful parses, expected failures, and
   conflict-free grammar verification.
