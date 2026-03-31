# LazyLR Reference Manual

This document is a **single, practical reference** for using LazyLR, similar in spirit to PLY's manual, but adapted to this repository's Java-first API and runtime LR(1) model.

- Source package: `com.github.forax.lazylr`
- Java baseline: modern Java (project examples use Java 25+)
- Parsing model: runtime LALR/LR with lazy state construction

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Mental Model](#mental-model)
3. [Defining a Grammar with `MetaGrammar`](#defining-a-grammar-with-metagrammar)
4. [Lexing (`Token`, `Lexer`, `Terminal`)](#lexing-token-lexer-terminal)
5. [Grammar Objects (`Grammar`, `Production`, `NonTerminal`, `Symbol`)](#grammar-objects-grammar-production-nonterminal-symbol)
6. [Precedence and Associativity](#precedence-and-associativity)
7. [Parsing (`Parser`, `ParserFactory`)](#parsing-parser-parserfactory)
8. [Semantic Actions (`Evaluator` and `Visitor`)](#semantic-actions-evaluator-and-visitor)
9. [Verification and Conflict Diagnostics (`LALRVerifier`)](#verification-and-conflict-diagnostics-lalrverifier)
10. [Errors and Exceptions](#errors-and-exceptions)
11. [Command-Line Usage](#command-line-usage)
12. [Code Generation (`JavaCodeGenerator`)](#code-generation-javacodegenerator)
13. [Concurrency and Performance Notes](#concurrency-and-performance-notes)
14. [End-to-End Examples](#end-to-end-examples)
15. [Troubleshooting](#troubleshooting)

---

## Quick Start

```java
import com.github.forax.lazylr.*;

sealed interface Expr {}
record Num(int value) implements Expr {}
record Add(Expr left, Expr right) implements Expr {}
record Mul(Expr left, Expr right) implements Expr {}

final class ExprVisitor implements Visitor<Expr> {
  public Expr num(Terminal t) {
    return new Num(Integer.parseInt(t.value()));
  }

  @ProductionName("E : E + E")
  public Expr add(Expr l, Expr r) { return new Add(l, r); }

  @ProductionName("E : E * E")
  public Expr mul(Expr l, Expr r) { return new Mul(l, r); }
}

void parseExample() {
  var mg = MetaGrammar.load("""
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

  mg.verify();
  Expr expr = mg.parse("2 + 3 * 4", new ExprVisitor());
  System.out.println(expr);
}
```

Use this workflow in most projects:

1. Define grammar DSL text.
2. `MetaGrammar.load(...)`.
3. `mg.verify()` while iterating grammar design.
4. `mg.parse(input, visitorOrEvaluator)`.

---

## Mental Model

LazyLR is to parser generators what a runtime regex engine is to code-generated scanners:

- You provide grammar/tokens at runtime.
- Parser states are built **lazily** as input is consumed.
- No mandatory external grammar compilation step.

Core concepts:

- **Terminal**: concrete lexeme/category from input (`num`, `'+'`, etc.).
- **NonTerminal**: grammar variable (`Expr`, `Stmt`, ...).
- **Production**: rule `Head : body...`.
- **Grammar**: all productions + one start non-terminal.
- **Evaluator/Visitor**: semantic actions for terminals and reductions.

---

## Defining a Grammar with `MetaGrammar`

`MetaGrammar` parses a DSL with up to three sections.

```text
tokens {
  num: /[0-9]+/
  /[ \t]+/          // skip token (anonymous)
}
precedence {
  left: '+'
  left: '*'
  right: UNARY
}
grammar {
  E : num
  E : E '+' E
  E : E '*' E
  E : '-' E %prec UNARY
}
```

### `tokens` section

- `name: /regex/` defines a named token.
- `/regex/` defines an anonymous skip token.
- Lexer behavior: longest match first, declaration order as tie-breaker.

### `precedence` section

- Syntax: `left: ...` or `right: ...`.
- Later lines = higher precedence.
- Symbols may be quoted literals (`'+'`) or token names (`PLUS`).
- `%prec SOME_SYMBOL` can override a production's precedence.

### `grammar` section

- Rule syntax: `Head : sym1 sym2 ...`.
- Empty RHS (`Head :`) is epsilon.
- First rule's head is the start symbol.
- Quoted literals in grammar are automatically registered as terminals.

### API highlights

- `MetaGrammar.load(String text)` parses the DSL.
- `mg.tokens()` returns token list in priority order.
- `mg.grammar()` returns `Grammar` (if defined).
- `mg.precedenceMap()` returns effective precedence table.
- `mg.parse(input, evaluatorOrVisitor)` performs lex + parse.
- `mg.verify(...)` checks unresolved LALR conflicts.

---

## Lexing (`Token`, `Lexer`, `Terminal`)

### `Token`

Represents a lexer rule:

- named: emits a terminal (`new Token("num", "[0-9]+")`)
- anonymous: matched and discarded (`new Token("[ \t]+")` style via DSL)

### `Lexer`

Create once from ordered tokens:

```java
var lexer = Lexer.createLexer(tokens);
var iterator = lexer.tokenize("12 + 34");
```

Tokenization returns `Iterator<Terminal>` and is lazy.

### `Terminal`

A runtime token instance with:

- `name()` (token category)
- `value()` (matched text)

Special sentinels exist internally (`EPSILON`, `EOF`, `ERROR`) and are mainly relevant for parser internals and diagnostics.

---

## Grammar Objects (`Grammar`, `Production`, `NonTerminal`, `Symbol`)

If you prefer programmatic construction instead of DSL:

- Build `Production` values with head/body.
- Build `Grammar` from productions + start non-terminal.
- Parse with `Parser.createParser(grammar, precedenceMap)`.

Object roles:

- `Symbol` is the sealed base type (`Terminal | NonTerminal`).
- `Production` is also a `PrecedenceEntity`, so precedence can target productions (especially via `%prec` derivations in DSL parsing).
- `Grammar` validates and stores all rules.

Most users should start with `MetaGrammar` and drop to low-level objects only when integrating with custom grammar pipelines.

---

## Precedence and Associativity

Conflict resolution for ambiguous expression grammars is done through a map from `PrecedenceEntity` to `Precedence(level, associativity)`.

Associativity values:

- `LEFT`
- `RIGHT`

Typical arithmetic setup:

```text
precedence {
  left: '+', '-'
  left: '*', '/'
  right: UNARY
}
```

Meaning:

- `*` and `/` bind tighter than `+` and `-`.
- binary operators reduce left-to-right.
- `%prec UNARY` can force unary minus precedence independently from binary `-`.

When no rule resolves a conflict, parsing or verification reports it.

---

## Parsing (`Parser`, `ParserFactory`)

### Single parser instance

```java
var parser = Parser.createParser(grammar, precedenceMap);
var result = parser.parse(terminals, evaluator);
```

`Parser` is stateful while parsing and should not be shared concurrently.

### Reusable factory for concurrency

```java
var factory = ParserFactory.createFactory(grammar, precedenceMap);
var parser = factory.createParser();
```

Share the factory, not parser instances, across threads.

### Listener mode

If you only need parsing events, use `ParserListener`-based workflows instead of materialized semantic values.

---

## Semantic Actions (`Evaluator` and `Visitor`)

LazyLR supports two equivalent styles.

### 1) `Evaluator<V>` (explicit)

```java
Evaluator<Integer> eval = new Evaluator<>() {
  public Integer evaluate(Terminal t) {
    return switch (t.name()) {
      case "num" -> Integer.parseInt(t.value());
      default -> null;
    };
  }

  public Integer evaluate(Production p, java.util.List<Integer> args) {
    return switch (p.toString()) {
      case "E : num" -> args.get(0);
      case "E : E + E" -> args.get(0) + args.get(2);
      default -> throw new IllegalStateException("Unknown production " + p);
    };
  }
};
```

### 2) `Visitor<V>` (reflection-backed convenience)

- Terminal method name matches token name (`num(Terminal)` for `num`).
- Reduction methods are annotated with `@ProductionName("Head : body...")`.
- `Visitor.reflect(...)` turns a visitor into an evaluator.
- `MetaGrammar.parse(input, visitor)` performs this automatically.

Pass-through behavior: for single-symbol productions with no explicit reduction method, the symbol value is forwarded automatically.

---

## Verification and Conflict Diagnostics (`LALRVerifier`)

Before production use, run verification:

```java
mg.verify();
```

Variants exist to:

- force automaton printing,
- route errors to a callback,
- customize destination streams (via lower-level API).

Use verification while grammar evolves; it catches unresolved shift/reduce or reduce/reduce conflicts early.

---

## Errors and Exceptions

Main failure type: `ParsingException`.

Typical sources:

1. **Lexing failure**: no token regex matches current input position.
2. **Parsing failure**: token not accepted in current parser state.
3. **Grammar/visitor mismatch**: missing or incompatible semantic method wiring.

Error messages are designed with line/column context and caret positioning for quick debugging.

---

## Command-Line Usage

CLI entry point: `com.github.forax.lazylr.Main`.

```text
lazylr [--generate|--print] <grammar> [input]
```

Modes:

- `lazylr grammar.txt` → verify grammar.
- `lazylr --print grammar.txt` → always print automaton.
- `lazylr --generate grammar.txt` → output Java reconstruction code.
- `lazylr grammar.txt input.txt` → parse input and print derivation tree.

Exit behavior distinguishes invalid CLI use, grammar issues, and parse issues.

---

## Code Generation (`JavaCodeGenerator`)

`JavaCodeGenerator.generate(mg)` emits Java source that reconstructs the same `MetaGrammar`.

This is useful for:

- freezing DSL definitions into source,
- embedding known-good grammars,
- reducing startup DSL parsing in fixed deployments.

---

## Concurrency and Performance Notes

- `MetaGrammar`, `Grammar`, tokens, precedence maps are immutable and shareable.
- `Parser` is mutable during parse; use one per thread.
- For high throughput, reuse `ParserFactory` and create parser-per-task.
- Lexer and parser are lazy; cost scales with consumed input and explored states.

---

## End-to-End Examples

### A) Validate grammar only

```java
var mg = MetaGrammar.load(grammarText);
mg.verify();
```

### B) Parse with evaluator

```java
var value = mg.parse("1 + 2 * 3", evaluator);
```

### C) Parse with visitor

```java
var ast = mg.parse("1 + 2 * 3", new AstVisitor());
```

### D) Low-level composition

```java
var lexer = Lexer.createLexer(mg.tokens());
var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());
var out = parser.parse(lexer.tokenize(input), evaluator);
```

---

## Troubleshooting

### "My token never matches"

- Check longest-match interactions.
- For equal-length matches, put preferred token earlier.
- Ensure whitespace/comment skip tokens are present.

### "I get shift/reduce conflicts"

- Add or adjust precedence/associativity.
- Use `%prec` for unary/bespoke cases.
- Run `mg.verify(true)` (or CLI `--print`) to inspect automaton states.

### "Visitor method isn't called"

- Terminal handler name must equal token name.
- Production handlers require exact `@ProductionName` text.
- Verify method signatures align with production symbol count/order.

### "Threading issues"

- Do not reuse the same `Parser` concurrently.
- Share `MetaGrammar`/`ParserFactory`, create parser per thread.

---

## Where to Go Next

- Read the stepwise tutorial in `src/main/demo/README.md`.
- Use project tests under `src/test/java/...` as executable examples.
- For API details, consult Javadoc generated from `src/main/java/com/github/forax/lazylr`.
