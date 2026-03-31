# LazyLR User & Reference Manual

LazyLR is a Java runtime parsing library in the lex/yacc family
optimized for fast development and iterative grammar evolution.

---

## Suggested reading order

1. `src/main/demo/README.md` for incremental demos.
2. This manual for deep reference.
3. For API details, consult the generated Javadoc (https://jitpack.io/com/github/forax/lazylr/latest/javadoc/).
4. `src/test/java/...` for executable examples.

---

## Table of Contents

1. [Introduction](#introduction)
2. [Quick Start](#quick-start)
3. [Conceptual Overview](#conceptual-overview)
4. [Grammar Reference (`MetaGrammar`)](#grammar-dsl-reference-metagrammar)
5. [Lexing Reference (`Token`, `Lexer`, `Terminal`)](#lexing-reference-token-lexer-terminal)
6. [Parsing Reference (`Grammar`, `Parser`, `ParserFactory`)](#parsing-reference-grammar-parser-parserfactory)
7. [Semantic Actions (`Evaluator` and `Visitor`)](#semantic-actions-evaluator-and-visitor)
8. [Precedence, Associativity, and Conflict Resolution](#precedence-associativity-and-conflict-resolution)
9. [Error Reporting and Recovery Strategy](#error-reporting-and-recovery-strategy)
10. [Automaton Inspection and Verification (`LALRVerifier`)](#automaton-inspection-and-verification-lalrverifier)
11. [Command-Line Tool (`Main`)](#command-line-tool-main)
12. [Code Generation (`JavaCodeGenerator`)](#code-generation-javacodegenerator)
13. [Threading, Reentrancy, and Performance](#threading-reentrancy-and-performance)
14. [End-to-End Recipes (with JUnit)](#end-to-end-recipes-with-junit)
15. [Troubleshooting Checklist](#troubleshooting-checklist)
16[ANTLR-to-LazyLR Mapping Guide](#antlr-to-lazylr-mapping-guide)

---

## Introduction

LazyLR is for developers who want LR parser power without a code-generation build step 
to iterate quickly on a grammar.

- **Lexing** is defined by ordered regex rules (`Token`, `Lexer`).
- **Parsing** is bottom-up LR with lazy state construction (`Parser`).
- **Grammar declaration** is usually done with the built-in DSL (`MetaGrammar.load(...)`).
- **Semantic actions** are implemented with `Evaluator` or typed `Visitor` methods.

### What it gives you

- Runtime grammar loading of the grammar with lexing + parsing in one library.
- Parser states are built lazily as input is consumed.
- Optional offline LALR conflict validation and automaton diagnostics.
- Precedence/associativity rules to make the grammar more readable.
- Typed semantic actions through a reflection-backed visitor layer.
- A CLI for validation, grammar debugging via automaton printing, and Java source generation.

### What it does *not* try to be

- A complete language workbench.
- A parser-combinator framework.
- A built-in AST object model.

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

// Visitor that construct the AST
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

// Verifies that the grammar is LALR(1) or prints the automaton
mg.verify();

// run the grammar on a text, and produce the AST
var text = "2 + 3 * 4";
Expr ast = mg.parse(text, new ExprVisitor());

System.out.println(ast);
```

### API highlights

- `MetaGrammar.load(String text)` parses the `MetaGrammar` text file.
- `mg.tokens()` returns token list in priority order (or an empty list).
- `mg.grammar()` returns `Grammar` (or throw an exception).
  You can check if the grammar exists with `mg.hasGrammar()`.
- `mg.precedenceMap()` returns effective precedence table (or an empty map).
- `mg.verify(...)` checks unresolved LALR(1) conflicts.
- `mg.parse(input, evaluatorOrVisitor)` performs lexing and parsing.

---

## MetaGrammar Reference

The `MetaGrammar` text format has three sections: `tokens`, `precedence`, `grammar`.
Inside a section, newlines separate rules.
Each section is optional.

```text
tokens {
  num: /[0-9]+/
  /[ \t]+/          // skip token (anonymous)
}
precedence {
  left: '+', '-'
  left: '*'
  right: UNARY
}
grammar {
  E : num
  E : E '+' E
  E : E '-' E
  E : E '*' E
  E : '-' E %prec UNARY
}
```

### `tokens` section

#### Syntax
- `name: /regex/` defines a named token.
- `/regex/` defines an anonymous token, those are not sent to the parser (comments, whitespaces, etc.).

#### Example
```text
tokens {
  keyword_if: /if/
  id: /[A-Za-z_][A-Za-z_0-9]*/
  /[ \t\n]+/
}
```

Input `if foo` will tokenize to `keyword_if("if")`, `id("foo")`.
If `id` has the value `if`, both rules `keyword_if` and `id` match,
`keyword_if` is chosen because it is declared first.

### `precedence` section

#### Syntax
- Syntax: `left: ...` or `right: ...`.
- Later lines = higher precedence.
- Symbols may be quoted literals (`'+'`), token names (`plus`) defined in the section `tokens`, or an arbitrary string.
- Arbitrary strings are as target of `%prec SOME_SYMBOL` in the grammar to override a production's default precedence.

#### Example
```text
precedence {
  left: '+', '-'    // quoted literals
  left: if          // named token
  left: 'UMINUS'    // arbitrary string
}
grammar {
  E : '-' E      %prec UMINUS      // reference `UMINUS` to use that precedence instead pf the one of '-'
}
```

### `grammar` section

Core concepts:
- **Terminal**: concrete lexeme/category from input (`num`, `'+'`, etc.).
- **NonTerminal**: grammar variable (`Expr`, `Stmt`, ...).
- **Production**: rules explaining how variables are created `E : E + E` or `E : num`, ...
- **Grammar**: all productions + one start non-terminal.
- **Evaluator/Visitor**: semantic actions for terminals and reductions.

#### Syntax

- Rule syntax: nonTerminal ':' symbol1 symbol2 symbol3 ...   (a symbol is a terminal or a non-terminal) 
  For example: `E : E '+' E`.
- Empty right and side (`Head :`) is epsilon.
- The head of the first rule is the start symbol.
- Quoted literals in grammar are automatically registered as terminals.

#### Example

```text
grammar {
  E : num
  E : E '+' E
  E : '-' E %prec UMINUS
}
```

`-3+4` parses with unary minus precedence from `UMINUS`, not from binary `-`.

### Comments and readability

`//` comments are allowed.

Example:

```text
grammar {
  // Primary expression
  E : num
  // Binary addition
  E : E '+' E
}
```

---

## Lexing Reference

### `Token`

Represents a lexer rule:

- named token, emitted as `Terminal` of the same name:
  `new Token(name, regex)` 
- anonymous token, matched and discarded by the `Lexer`:
  `new Token(regex)`

Validation rejects malformed patterns and empty-string-matching patterns.

### `Lexer`

Create each terminal lazily from an input text using, the `tokens` description.

```java
var lexer = Lexer.createLexer(tokens);
var inputText = "a+b";

Iterator<Terminal> iterator = lexer.tokenize(inputText);
```

Matching rules:

1. longest match wins,
2. tie resolved by declaration order,
3. ignorable token rules consume but do not emit,
4. no match triggers `Terminal.ERROR` path.

### `Terminal`

Runtime token object with:

- `name()` (same as the token name)
- `value()` (the content of the input text that matches the regex)

Special sentinels exist internally (`EPSILON`, `EOF`, `ERROR`) and
are mainly relevant for parser internals and diagnostics.

---

## Parsing Reference

### `Grammar`, `Production`, `Symbol`

- `Symbol` is sealed (`Terminal` / `NonTerminal`).
- `Production` is `head -> body` and also a `PrecedenceEntity`.
- `Grammar` stores start symbol + productions.

### `Parser`

```java
var parser = Parser.createParser(grammar, precedenceMap);
var result = parser.parse(terminals, evaluator);
```

The parser adds augmented start rule `S' -> S` internally.

`Parser` is stateful while parsing and should not be shared concurrently.


### `ParserFactory`

Use factory for concurrent workloads:

```java
var factory = ParserFactory.createFactory(grammar, precedenceMap);
var parser = factory.createParser(); // call in the thread that will parse
```

---

## Semantic Actions (`ParserListener`, `Evaluator` and `Visitor`)

### ParserListener

If you only need parsing events (e.g., building your own stack, debugging, or streaming processing),
you can use a listener-style approach instead of producing semantic values.

A `ParserListener` receives callbacks during parsing:

* on shift (token consumed)
* on reduce (production applied)

#### Example

```java
ParserListener listener = new ParserListener() {
  @Override
  public void onShift(Terminal t) {
    System.out.println("Shift: " + t);
  }

  @Override
  public void onReduce(Production p, List<Object> values) {
    System.out.println("Reduce: " + p.name());
  }
};
```

### `Evaluator<V>`

#### Example 1: arithmetic evaluation

```java
Evaluator<Integer> eval = new Evaluator<>() {
  public Integer evaluate(Terminal t) {
    return switch (t.name()) {
      case "num" -> Integer.parseInt(t.value());
      default -> null;
    };
  }
  public Integer evaluate(Production p, List<Integer> a) {
    return switch (p.name()) {
      case "E : num" -> a.get(0);
      case "E : E + E" -> a.get(0) + a.get(2);
      case "E : E * E" -> a.get(0) * a.get(2);
      default -> throw new IllegalStateException(p.name());
    };
  }
};
```

#### Example 2: build a parenthesized string form

```java
Evaluator<String> eval = new Evaluator<>() {
  public String evaluate(Terminal t) {
    return t.name().equals("num") ? t.value() : null;
  }

  public String evaluate(Production p, List<String> a) {
    return switch (p.name()) {
      case "E : num" -> a.get(0);
      case "E : E + E" -> "(" + a.get(0) + " + " + a.get(2) + ")";
      default -> throw new IllegalStateException(p.name());
    };
  }
};
```

### `Visitor<V>` (reflection-backed convenience)

#### Example 1: AST construction

```java
sealed interface Node {}
record NumNode(int value) implements Node {}
record AddNode(Node left, Node right) implements Node {}

final class AstVisitor implements Visitor<Node> {
  public Node num(Terminal t) {
    return new NumNode(Integer.parseInt(t.value()));
  }

  @ProductionName("E : E + E")
  public Node add(Node left, Node right) {
    return new AddNode(left, right);
  }
}
```

#### Example 2: multiple productions with repeated annotation

```java
final class Printer implements Visitor<String> {
  public String num(Terminal t) { return t.value(); }

  @ProductionName("E : E + E")
  @ProductionName("E : E * E")
  public String bin(String left, String right) {
    return "(" + left + " ? " + right + ")";
  }
}
```

### Behavioral notes

- Terminal methods are looked up by method name = token name.
- Production methods are mapped by exact `@ProductionName` text.
- If a one-symbol production has no method, value is passed through.

### API
- `Visitor.reflect(...)` turns a visitor into an evaluator.
- `MetaGrammar.parse(input, visitor)` performs this operation automatically.

---

## Precedence, Associativity, and Conflict Resolution

### Example 1: precedence between `+` and `*`

```text
precedence {
  left: '+'
  left: '*'
}
grammar {
  E : E '+' E
  E : E '*' E
  E : num
}
```

Explanation: because `*` appears in a later precedence row, it has higher precedence. So `2 + 3 * 4` parses as `2 + (3 * 4)`.

### Example 2: left associativity

```text
precedence {
  left: '+'
}
grammar {
  E : E '+' E
  E : num
}
```

Explanation: `1 + 2 + 3` parses as `(1 + 2) + 3` because equal-precedence conflicts reduce on left-associative operators.

### Example 3: right associativity

```text
precedence {
  right: '^'
}
grammar {
  E : E '^' E
  E : num
}
```

Explanation: `2 ^ 3 ^ 2` parses as `2 ^ (3 ^ 2)` because equal-precedence conflicts shift for right-associative operators.

### Example 4: `%prec` override for unary minus

```text
precedence {
  left: '+', '-'
  left: '*'
  right: UMINUS
}
grammar {
  E : E '+' E
  E : E '*' E
  E : '-' E %prec UMINUS
  E : num
}
```

Explanation: unary minus gets the `UMINUS` precedence level, so `-3 * 4` groups as `(-3) * 4` instead of `-(3 * 4)`.

### Conflict rules summary

When shift/reduce conflict occurs:

1. compare lookahead-token precedence with reducing-production precedence,
2. higher precedence wins,
3. equal precedence uses associativity (`LEFT` reduce, `RIGHT` shift),
4. unresolved conflicts remain errors to diagnose with verifier output.

---

## Error Reporting and Recovery Strategy

LazyLR emphasizes high-quality diagnostics but does not expose yacc-style grammar `error` tokens for user-defined panic recovery,
at least not yet.

- The exception `ParsingException` is used for both lexing and syntax failures.
- Lexing failure occurs when no token rule matches at the current character.
- Parsing failure occurs when there is no action for the terminal in the current state of the automaton.

Typical production strategy in applications:

1. catch `ParsingException`,
2. report precise line/column context,
3. continue by parsing the next top-level unit.

---

## Automaton Inspection and Verification (`LALRVerifier`)

Run verification before shipping a grammar:
```java
mg.verify();
```

Variants exist to:
- force automaton printing `mg.verify(alwaysPrint)``,
- route errors to a callback `mg.verifies(errorReporter),
- customize destination streams (via lower-level API).

For debugging, you can ask to print always:
```java
mg.verify(true);
```

Use verification while grammar evolves; it catches unresolved shift/reduce or reduce/reduce conflicts early.


### Example automaton output (illustrative)

```text
state 11
  E -> E + E .
  E -> E . + E
  E -> E . * E

  +  reduce by rule (E -> E + E)
  *  shift and go to state 7

  ❌ conflict: shift/reduce on '+'
  🔥 chosen by precedence: reduce (LEFT '+')
```

Interpretation:

- `❌` marks a conflict location.
- `🔥` shows the conflict resolution selected by precedence/associativity.

If no precedence rule resolves it, the conflict remains an error report.

---

## Errors and Exceptions

Main failure type: `ParsingException`.

Typical sources:

1. **Lexing failure**: no token regex matches current input position.
2. **Parsing failure**: token not accepted in current parser state.
3. **Grammar/visitor mismatch**: missing or incompatible semantic method wiring.

!!!TODO!!!

Error messages are designed with line/column context and caret positioning for quick debugging.

---

## Command-Line Tool

Usage:

```text
lazylr [--generate | --print] <grammar> [input]
```

`lazylr grammar.txt`: the tool reads and validates the grammar and only prints on stderr the automaton when there are conflicts.
With the option:
- `lazylr --print grammar.txt`: the tool validates the grammar and prints the automaton on stdout even when the grammar is conflict-free.
- `lazylr --generate grammar.txt`:the tool validates the grammar and emits Java code with a `createGrammar()` method that reconstructs the grammar programmatically.

`lazylr grammar.txt input.txt`: the tool validates the grammar, parses the input file, and prints a derivation tree.

Exit code `1` indicates CLI misuse or file/grammar loading errors, and exit code `2` indicates unresolved grammar conflicts.

### Code Generation

`JavaCodeGenerator.generate(mg)` emits Java source that reconstructs the loaded grammar.

Practical uses:
- freezing grammar definitions in source control,
- producing reviewable grammar snapshots in code form.
- reducing time overhead to load the DSL in fixed deployments,

---

## Concurrency and Performance

- `Parser` is mutable during parse; use one per thread.
- All other classes, `MetaGrammar`, `Grammar`, tokens, precedence maps, etc. are immutable and shareable.
- Lexer and parser are lazy; cost scales with consumed input and explored states.

Performance notes:

- parser states are built lazily,
- factory setup (`ParserFactory) amortizes FIRST-set and precedence completion work,
- visitor reflection happens up front and parse dispatch uses method handles afterward.

- For high throughput, reuse `ParserFactory` and create parser-per-task.

---

## End-to-End Recipes (with JUnit)

### Recipe A: grammar verification test

```java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GrammarValidationTest {
  @Test
  void grammar_is_lalr1() {
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
}
```

### Recipe B: parser result test with visitor

```java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ParseResultTest {
  sealed interface Node {}
  record Num(int value) implements Node {}
  record Add(Node l, Node r) implements Node {}

  static final class NodeVisitor implements Visitor<Node> {
    public Node num(Terminal t) { return new Num(Integer.parseInt(t.value())); }
    @ProductionName("E : E + E")
    public Node add(Node left, Node rrigh) { return new Add(left, right); }
  }

  @Test
  void parses_simple_addition() {
    var mg = MetaGrammar.load("""
      tokens {
        num: /[0-9]+/ /[ ]+/
      }
      precedence {
        left: '+'
      }
      grammar {
        E : num
        E : E '+' E
      }
      """);

    var ast = mg.parse("1 + 2", new NodeVisitor());
    assertInstanceOf(Add.class, ast);
  }
}
```

### Recipe C: expected parse failure test

```java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ParseFailureTest {
  static final class NoOpEvaluator implements Evaluator<Object> {
    public Object evaluate(Terminal t) { return null; }

    public Object evaluate(Production p, List<Object> a) { return null; }
  }
  
  @Test
  void reportsErrorOnInvalidInput() {
    var mg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/ /[ ]+/
        }
        grammar {
          E : num 
        }
      """);

    var ex = assertThrows(ParsingException.class, () ->
        mg.parse("abc", new NoOpEvaluator()));

    assertTrue(ex.getMessage().contains("line"));
  }
}
```

### Recipe D: conflict diagnostics test

```java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;

class ConflictReportTest {
  @Test
  void reportsConflictsWhenGrammarIsAmbiguous() {
    var mg = MetaGrammar.load("""
      grammar {
        E : E '+' E
        E : E '*' E
        E : num
      }
      """);

    var errors = new ArrayList<String>();
    mg.verify(errors::add);
    assertFalse(errors.isEmpty());
  }
}
```


Testing guidance:

- keep grammar text inline in tests for readability,
- test both successful parse results and expected failures,
- use `verify(errors::add)` to enforce conflict-free grammars in CI,
- for regressions, snapshot AST string rendering or evaluator results.

---

## Troubleshooting Checklists

### Lexing failure ("My token never matches")

- Check longest-match interactions.
- For equal-length matches, put preferred token earlier.
- Ensure whitespace/comment skip tokens are present.

### Shift/reduce conflicts ("I get shift/reduce conflicts")

- Add or adjust precedence/associativity (lowest first).
- Use `%prec` for unary/bespoke cases.
- Run `mg.verify(true)` (or CLI `--print`) to inspect automaton states.

### Unexpected semantics ("Visitor method isn't called")

- Terminal handlers name must equal the token name.
- Production handlers require an annotation `@ProductionName` with the exat production text.
- Verify method signatures align with production symbol count/order.

### Threading issues ("I get a `WrongThreadException`")

- Do not reuse the same `Parser` concurrently, each parser is bound to a thread.
- Share `MetaGrammar`/`ParserFactory`, create parser per thread.

---

## ANTLR-to-LazyLR Mapping Guide

ANTLR and LazyLR can describe similar languages, but they differ in parser strategy and grammar style.

### 1) Lexer rules migration

ANTLR:

```antlr
ID : [a-zA-Z_][a-zA-Z_0-9]* ;
INT: [0-9]+ ;
WS : [ \t\r\n]+ -> skip ;
```

LazyLR:

```text
tokens {
  ID: /[A-Za-z_][A-Za-z_0-9]*/
  INT: /[0-9]+/
  /[ \t\r\n]+/
}
```

Migration note: `-> skip` maps to an anonymous regex token.

### 2) Parser rules migration

ANTLR (typical precedence by rule layering):

```antlr
expr : expr '*' expr
     | expr '+' expr
     | INT
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

Migration note: ANTLR often encodes precedence via rule nesting or explicit precedence constructs;
in LazyLR you usually declare it directly in `precedence`.

### 3) Actions/listeners/visitors migration

ANTLR options:

- embedded actions in grammar,
- parse tree listeners,
- generated visitors.

LazyLR options:

- `Evaluator<V>` for direct semantic reduction,
- `Visitor<V>` with `@ProductionName` methods.

Example migration shape:

- ANTLR visitor method `visitAddExpr(ctx)` -> LazyLR `@ProductionName("Expr : Expr + Expr")`.

### 4) Left recursion and ambiguity

ANTLR 4 supports direct left recursion in many expression contexts.
LazyLR also handles left recursion well (LR parser), but ambiguous grammars
require explicit precedence/associativity so conflict resolution is deterministic.

### 5) Error handling migration

ANTLR exposes rich parser/lexer error listeners and recovery hooks.
LazyLR uses `ParsingException` and verifier diagnostics; migration typically moves error reporting to call-site exception handling plus test assertions.

### 6) Practical migration checklist

1. Convert lexer fragments to Java regex tokens.
2. Convert skip channels to anonymous token rules.
3. Port parser rules to `grammar` section.
4. Add precedence table for ambiguous operators.
5. Port semantic logic to `Visitor` or `Evaluator`.
6. Add JUnit tests for parse success, parse failure, and conflict detection.

## Final Notes

!!!TODO!!!

