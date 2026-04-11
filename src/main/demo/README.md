# 🦥 LazyLR Demo Tutorial

This tutorial walks you through the LazyLR library step by step, using a series of small,
self-contained programs. Each demo builds on the previous one — read them in order for the best experience.

## Before You Start

**Prerequisites**
- JDK 25+ (for LazyLR and the modern syntax used in demos).
- Ability to run single-file Java snippets (for example, with `java --source 25 src/main/demo/file.java`)
  or any Java IDEs (IntelliJ, Eclipse, VSCode), open LazyLR as a Maven project.

---

## What is LazyLR?

LazyLR is a runtime LR(1) parser library for Java.

Unlike traditional parser generators (ANTLR, Yacc) that require a separate compilation step
and pre-compute massive state tables, LazyLR builds its states on the fly as input is consumed.
This "Lazy" approach means your parser starts instantly, providing a fast feedback loop during development,
you can change your grammar and re-run your tests without any extra build steps.

Three key concepts underpin everything:

- **Terminal** — a concrete token that appears in the raw input (e.g. `42`, `+`, `if`).
  These are the leaves of any parse tree.
- **NonTerminal** — a named placeholder that stands for a pattern the grammar will expand
  (e.g. `E` for "expression"). Non-terminals never appear in the raw input.
- **Production** — a rule that says what a non-terminal can expand into, written
  as `head : symbol1 symbol2 ...`. A grammar is a collection of productions plus a start symbol.

As a developer, you can think of a terminal as a grammar-level value and
non-terminal as a grammar-level variable.

Parsing is *bottom-up*: the parser reads terminals from the input and progressively replaces them
with non-terminals by applying productions in reverse, until it arrives at the start symbol.

---

## 01 — Your First Grammar [_01_grammar.java](_01_grammar.java)

**Goal:** Load a grammar from text and navigate the resulting Java objects.

`MetaGrammar.load(text)` parses a text description and returns a `MetaGrammar` object.
From it you can retrieve the `Grammar`, which holds the list of `Production` rules.
Each `Production` has a `NonTerminal` head and a list of `Symbol` objects
(either `Terminal` or `NonTerminal`) as its body.

```java
import com.github.forax.lazylr.*;

void main() {
  var mg = MetaGrammar.load("""
      grammar {
        E : number
      }
      """);

  var grammar = mg.grammar();
  IO.println(grammar);

  var productions = grammar.productions();
  var production = productions.getFirst();
  var nonTerminal = production.head();
  //IO.println(nonTerminal);

  var terminal = production.body().getFirst();
  //IO.println(terminal);
}
```

This demo does not yet parse any input — its purpose is purely to show the object model.
Call `mg.grammar()` to get the `Grammar`, then `grammar.productions()` to inspect the rules,
`production.head()` for the left-hand side, and `production.body()` for the right-hand side symbols.

> 💡 The grammar text has three optional sections: `tokens`, `precedence`, and `grammar`.
>    Here we only use `grammar`. Quoted literals like `'+'` in productions are automatically
>    registered as terminals — no explicit `tokens` entry is needed for them.

---

## 02 — Tokens and the Lexer [_02_token_and_lexer.java](_02_token_and_lexer.java)

**Goal:** Turn raw input text into a stream of named tokens.

The `Lexer` is configured with a list of `Token` objects. Each `Token` pairs a name
with a Java regular expression.
The lexer applies a **longest-match** rule at each position; ties are broken
by declaration order (earlier wins).
A `Token` created with only a regex and no name is *unnamed*, it is matched and
silently discarded, which is useful for whitespace and comments.

`Lexer.createLexer(tokens)` builds the lexer. `lexer.tokenize(input)` returns
a lazy `Iterator<Terminal>` that produces tokens one at a time.

```java
import com.github.forax.lazylr.*;

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/            // ignorable
      }
      grammar {
        E : number
      }
      """);

  var tokens = mg.tokens();
  IO.println(tokens);

  var lexer = Lexer.createLexer(tokens);
  var iterator = lexer.tokenize("32 12 14");

  while (iterator.hasNext()) {
    var terminal = iterator.next();
    //IO.println(terminal.name() + " " + terminal.value());
  }
}
```

The `MetaGrammar` extracts the token list for you via `mg.tokens()`, so you rarely need to build it by hand.

> 💡 **Tie-breaking Rules:** The lexer always prefers the **longest match** first.
>    If you have two tokens, `if: /if/` and `identifier: /[a-z]+/` and your input is `ifnot`,
>    it will always match `ifnot` as an identifier.
>    However, if two patterns match the **same** number of characters (a tie), the **earlier declaration wins**.
>    This is why `if: /if/` must be declared before `ident: /[a-z]+/` in the `tokens` section.

---

## 03 — Parsing a Token Stream [_03_parsing.java](_03_parsing.java)

**Goal:** Wire a `Parser` to a list of terminals and observe every shift and reduce event.

`Parser.createParser(grammar, precedenceMap)` builds a lazy LR(1) parser.

Parsing is driven by an `Evaluator`. The `PrintEvaluator` helper used here prints every event to the console.

```java
import com.github.forax.lazylr.*;

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      grammar {
        E : number
      }
      """);

  var input = List.of(new Terminal("number", "42"));

  var parser = Parser.createParser(mg.grammar(), Map.of());
  parser.parse(input.iterator(), new PrintEvaluator());
}
```

Because LazyLR is a lazy parser, internal state is mutated during parsing,
the class **`Parser` is not thread-safe**; use one per thread (or share a `ParserFactory`).

> 💡 **Notice the order:** terminals fire *before* the production that contains them.
>    That's bottom-up parsing in action — the parser always resolves the leaves
>    of the parse tree first, then folds them upward into larger structures.

---

## 04 — Evaluating with MetaGrammar [_04_evaluator.java](_04_evaluator.java)

**Goal:** Use `mg.parse(input, evaluator)` as a convenient all-in-one entry point.

`MetaGrammar` bundles the lexer and parser creation for you.
`mg.parse(input, evaluator)` creates a `Lexer` from the tokens section,
tokenizes the input, creates a `Parser`, and drives it with your `Evaluator` — all in one call.

The `Evaluator<T>` interface defines two methods:
- `T evaluate(Terminal t)` — called on each *shift*; receives the matched token and returns a value.
- `T evaluate(Production p, List<T> args)` — called on each *reduce*; `args` holds the `T` values
   already returned for each body symbol, in order.

```java
import com.github.forax.lazylr.*;

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      grammar {
        E : number
      }
      """);

  mg.parse("32", new PrintEvaluator());
}
```

> 💡 The value returned by `evaluate(Terminal t)` is passed directly as an element of `args`
>    in the production evaluator above it, threading values up the parse tree automatically.
>    For terminals that carry no semantic value (punctuation like `'+'`), simply return `null`
>    from the terminal method.
>    Those `null` values will be available in `args` at their respective positions.

---

## 05 — Recursive Productions [_05_recursive_production.java](_05_recursive_production.java)

**Goal:** Parse arbitrarily nested input like `(32)` or `((32))` using a recursive grammar rule.

Adding the production `E : '(' E ')'` makes the grammar self-referential.
The parser handles this naturally, there is no depth limit in code, only the constraints of the input.
The `TreeEvaluator` builds an explicit `Node`/`Value` tree so you can inspect the exact parse structure.

```java
import com.github.forax.lazylr.*;

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      grammar {
        E : number
        E : '(' E ')'
      }
      """);

  var tree = mg.parse("(32)", new TreeEvaluator());
  IO.println(tree);

  var tree2 = mg.parse("((32))", new TreeEvaluator());
  //IO.println(tree2);
}
```

For `(32)`, the tree has an outer node for `E : '(' E ')'` containing an inner node
for `E : number` containing the value `32`.

> 💡 Prefer **left recursion** for lists and sequences (e.g. `E : E ',' num`).
>    LR parsers handle left-recursive rules efficiently.
>    Right recursion works too, but can cause very deep stacks on large inputs.

---

## 06 — Error Handling [_06_errors.java](_06_errors.java)

**Goal:** Understand the three kinds of parse errors and the detailed messages LazyLR produces.

All errors surface as `ParsingException`. The messages include line number, column number,
the offending line of input, and a caret (`^`) pointing to the exact position.

```java
import com.github.forax.lazylr.*;

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      grammar {
        E : number
        E : '(' E ')'
      }
      """);

  // Unknown character -> lexing error
  mg.parse("@", new PrintEvaluator());

  // bad terminal -> parser error
  mg.parse("()", new PrintEvaluator());

  // end of file reached -> parser error
  mg.parse("(32", new PrintEvaluator());
}
```

Running each case produces:

```
// Exception: Lexing error at line 1, column 1: unexpected character '@'
// @
// ^

// Exception: Parsing error at line 1, column 2: unexpected terminal ')', expected '(', number
// ()
//  ^

// Exception: Parsing error at line 1, column 2: unexpected terminal '$', expected ')'
// (32
//  ^
```

| Error type         | When it occurs                                               | Example                 |
|--------------------|--------------------------------------------------------------|-------------------------|
| **Lexing error**   | No token pattern matches the current character               | `@`                     |
| **Parsing error**  | A token exists but is not valid at this point in the grammar | `)` with no opening `(` |
| **Unexpected EOF** | Input ends before the grammar is satisfied                   | `(32` — missing `)`     |

> 💡 The parser uses $ internally to represent the end of the input stream.
>    When you see `unexpected terminal '$'` in an error message, it means the input ended
>    earlier than the grammar expected.
>    You do not need to define '$' in your grammar; the parser injects it automatically
>    to ensure the entire input is consumed.

---

## 07 — The Visitor Interface (Naïve Attempt) [_07_visitor_oops.java](_07_visitor_oops.java)

**Goal:** Introduce the `Visitor` interface and see what happens when a production method is missing.

`Visitor<T>` is a higher-level alternative to `Evaluator` that lets you write one typed Java method
per terminal or production.
Terminal methods are matched by name; production methods are matched by the `@ProductionName` annotation.
The `mg.parse(text, visitor)` first inspects the visitor's public methods
using reflection and builds the dispatch table before delegating to `mg.parse(text, evaluator)`.

This demo deliberately omits the method for `E : '(' E ')'`:

```java
import com.github.forax.lazylr.*;

class IntVisitor implements Visitor<Integer> {
  public int number(Terminal terminal) { return Integer.parseInt(terminal.value()); }
}

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      grammar {
        E : number
        E : '(' E ')'
      }
      """);
  mg.verify();

  var value = mg.parse("(32)", new IntVisitor());
  IO.println(value);
}
```

When the parser tries to reduce `E : '(' E ')'` and finds no handler, it throws `IllegalStateException`
with a **skeleton of the missing method** so you know exactly what to add:

```
Exception in thread "main" java.lang.IllegalStateException: production "E : ( E )" has no evaluator method,  proposed code:
@ProductionName("E : ( E )")
public int method(int param0) {
  throw new UnsupportedOperationException("TODO");
}
```

> 💡 The method skeleton shows you the **exact parameter types** you need.
>    LazyLR infers the type of each parameter from the return types of the other visitor methods.
>    This makes it easy to get started: just copy the skeleton, rename the method, and fill in the body.

---

## 08 — The Visitor Interface (Correct Version) [_08_visitor.java](_08_visitor.java)

**Goal:** Fix the missing production method and confirm the visitor works end-to-end.

Adding the `@ProductionName("E : ( E )")` method resolves the error.
The method takes a single `int` parameter (the value already computed for the inner `E`)
and returns it unchanged, parentheses have no semantic effect, they just control grouping.

All productions sharing the same head (the same `NonTerminal`) must have corresponding
visitor methods that return the same type.
For example, if `E : number` returns an `int`, then `E : '(' E ')'` must also return an `int`.
This ensures the evaluator can consistently pass values up the tree.

```java
import com.github.forax.lazylr.*;

class IntVisitor implements Visitor<Integer> {
  public int number(Terminal terminal) { return Integer.parseInt(terminal.value()); }

  @ProductionName("E : ( E )")
  public int parens(int value) { return value; }
}

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      grammar {
        E : number
        E : '(' E ')'
      }
      """);
  mg.verify();

  var value = mg.parse("(32)", new IntVisitor());
  IO.println(value);
}
```

> 💡 **Single-body pass-through:** if a production has exactly one symbol in its body and
>    no `@ProductionName` method is defined for it, the single argument is passed through automatically.
>    That means you do not need a method for chain productions like `E : number` — the `int`
>    returned by `number(Terminal)` is forwarded up the tree without any extra code.

---

## 09 — Building an AST [_09_visitor_AST.java](_09_visitor_AST.java)

**Goal:** Return structured AST nodes instead of raw integers, using sealed interfaces and records.

Swapping `Visitor<Integer>` for `Visitor<Expr>` changes the visitor from immediate evaluation
to tree construction.
The `number` method wraps the parsed value in a `Value` record; the `parens` method returns
its `Expr` argument unchanged.

```java
import com.github.forax.lazylr.*;

sealed interface Expr {}
record Value(int value) implements Expr {}

class ExprVisitor implements Visitor<Expr> {
  public Expr number(Terminal terminal) {
    return new Value(Integer.parseInt(terminal.value()));
  }

  @ProductionName("E : ( E )")
  public Expr parens(Expr expr) { return expr; }
}

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      grammar {
        E : number
        E : '(' E ')'
      }
      """);
  mg.verify();

  var expr = mg.parse("( 32 )", new ExprVisitor());
  IO.println(expr);
}
```

Output: `Value[value=32]`

> 💡 Building an AST is useful when you need to inspect, transform, or serialize
>    the parse result before evaluating it, or when the same input will be evaluated
>    multiple times under different rules.
>    Java records make AST nodes concise and pattern-matching-friendly.

---

## 10 — Reduce/Reduce Conflict [_10_conflict_reduce_reduce.java](_10_conflict_reduce_reduce.java)

**Goal:** Understand what a reduce/reduce conflict is and how LazyLR reports it.

A reduce/reduce conflict occurs when the parser can apply two different productions
to the same sequence of symbols.
In this grammar, both `A` and `B` derive `number`:

```java
import com.github.forax.lazylr.*;

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      grammar {
        E : A
        E : B
        A : number
        B : number
      }
      """);

  //mg.verify();

  mg.parse("42", new PrintEvaluator());
}
```

After shifting `number`, the parser cannot decide whether to reduce to `A` or `B`.

The parser fails and reports a reduce/reduce conflict exception
```
ParsingException: Parsing error: reduce/reduce conflict for terminal '$'
```

`mg.verify()` helps to diagnose the issue,
if it fails, it prints the LR state automaton to the console.
Look specifically for the 🔥 (fire) markers, these pinpoint exactly which **terminals**
are causing the ambiguity and in which state they occur:

```
── State 4 ─────────────────────────────────
  A :  number •
  B :  number •
 ······································
  reduce( A : number ) on [$ 🔥]
  reduce( B : number ) on [$ 🔥]
```

> 💡 Reduce/reduce conflicts almost always indicate **redundant or overlapping structure** in the grammar.
>    The solution is to restructure the grammar itself, here, remove the duplication.

---

## 11 — Shift/Reduce Conflict [_11_conflict_shift_reduce.java](_11_conflict_shift_reduce.java)

**Goal:** Understand what a shift/reduce conflict is and why `E : E '+' E` is inherently ambiguous.

A shift/reduce conflict occurs when the parser has a partially-matched production on its stack
and the lookahead token could either complete it (reduce) or extend it further (shift).

```java
import com.github.forax.lazylr.*;

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      grammar {
        E : number
        E : '(' E ')'
        E : E '+' E
      }
      """);
  
  //mg.verify();

  mg.parse("40 + 2 + 3", new PrintEvaluator());
}
```

With `E + E` on its stack and another `+` as lookahead, the parser does not know whether
to finish the current addition first (reduce) or wait (shift).
For example, should it be `(2 + 3) + 4` (reduce first) or `2 + (3 + 4)` (shift first) ?

The parser fails with:
```
ParsingException: Parsing error: shift/reduce conflict for terminal '+'
```

Using `mg.verify()`, the automaton shows the conflict:

```
── State 6 ─────────────────────────────────
  E :  E + E •
  E :  E • + E
 ······································
  goto( +  ) → 4 🔥
  reduce( E : E + E ) on [$, ), + 🔥]
```

> 💡 Shift/reduce conflicts are the normal result of writing natural, concise grammars.
>    Unlike reduce/reduce conflicts, they do not require restructuring the grammar,
>    they can be resolved cleanly with a `precedence` declaration, as shown in the next demo.

---

## 12 — Resolving with `left:` [_12_conflict_association.java](_12_conflict_association.java)

**Goal:** Resolve the shift/reduce conflict by declaring left associativity,
       giving `40 + 2 + 3 = (40 + 2) + 3 = 45`.

Adding `left: '+'` to the `precedence` section tells the parser: **when there is a conflict on '+', reduce first**.
This is left-to-right grouping (left associativity). The conflict disappears and `mg.verify()` passes cleanly.

```java
import com.github.forax.lazylr.*;

sealed interface Expr {}
record Value(int value) implements Expr {}
record Binary(char op, Expr left, Expr right) implements Expr {}

class ExprVisitor implements Visitor<Expr> {
  public Expr number(Terminal terminal) {
    return new Value(Integer.parseInt(terminal.value()));
  }

  @ProductionName("E : ( E )")
  public Expr parens(Expr expr) { return expr; }

  @ProductionName("E : E + E")
  public Expr add(Expr left, Expr right) { return new Binary('+', left, right); }
}

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      precedence {
        left : '+'
      }
      grammar {
        E : number
        E : '(' E ')'
        E : E '+' E
      }
      """);

  mg.verify();

  var expr = mg.parse("40 + 2 + 3", new ExprVisitor());
  IO.println(expr);
}
```

Output: `Binary[op=+, left=Binary[op=+, left=Value[40], right=Value[2]], right=Value[3]]`

> 💡 Internally, when the parser has `E + E` on its stack and sees another `+` as lookahead,
>    it compares the precedence of the production (`E : E + E`, which inherits from `+`)
>    against the lookahead token (`+`).
>    The levels are equal, and `LEFT` associativity means **reduce wins**,
>    so `40 + 2` is folded first, then `3` is appended.

---

## 13 — Operator Precedence Levels [_13_conflict_level.java](_13_conflict_level.java)

**Goal:** Make `*` bind more tightly than `+`, so `40 + 2 * 3 = 40 + (2 * 3) = 46`.

**Later lines in the `precedence` section have higher precedence**.
Listing `'*'` after `'+'` in the precedence section gives multiplication a higher level than addition.

| Declaration | Priority | Result          |
|-------------|----------|-----------------|
| left : '+'  | Lowest   | Evaluated last  |
| left : '*'  | Highest  | Evaluated first |

```java
import com.github.forax.lazylr.*;

sealed interface Expr {}
record Value(int value) implements Expr {}
record Binary(char op, Expr left, Expr right) implements Expr {}

class ExprVisitor implements Visitor<Expr> {
  public Expr number(Terminal terminal) {
    return new Value(Integer.parseInt(terminal.value()));
  }

  @ProductionName("E : ( E )")
  public Expr parens(Expr expr) { return expr; }

  @ProductionName("E : E + E")
  public Expr add(Expr left, Expr right) { return new Binary('+', left, right); }

  @ProductionName("E : E * E")
  public Expr mul(Expr left, Expr right) { return new Binary('*', left, right); }
}

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      precedence {
        left : '+'
        left : '*'
      }
      grammar {
        E : number
        E : '(' E ')'
        E : E '+' E
        E : E '*' E
      }
      """);

  mg.verify();

  var expr = mg.parse("40 + 2 * 3", new ExprVisitor());
  IO.println(expr);
}
```

Output: `Binary[op=+, left=Value[40], right=Binary[op=*, left=Value[2], right=Value[3]]]`

> 💡 Precedence levels are **relative, not absolute**, only their ordering matters.
>    When the parser has `E + E` on its stack and sees `*` as lookahead, it compares levels:
>    since `*` is higher, it **shifts** (reads more input) rather than reducing the addition early.
>    You can declare multiple terminals at the same level on one line: `left: '+', '-'`.

---

## 14 — Right Associativity with `right:` [_14_right_assoc.java](_14_right_assoc.java)

**Goal:** Make `^` right-associative so `2 ^ 3 ^ 4 = 2 ^ (3 ^ 4)`.

Using `right:` instead of `left:` reverses the tie-breaking rule:
when the parser has `E ^ E` on its stack and sees another `^`, it **shifts** instead of reducing.
This defers the reduction and naturally groups from the right.

| Declaration | Priority | Associativity | Result        |
|-------------|----------|---------------|---------------|
| left : '+'  | Lowest   | left          | Grouped first |
| left : '*'  | Medium   | left          |               |
| right : '^' | Highest  | right         | Grouped last  |

```java
import com.github.forax.lazylr.*;

sealed interface Expr {}
record Value(int value) implements Expr {}
record Binary(char op, Expr left, Expr right) implements Expr {}

class ExprVisitor implements Visitor<Expr> {
  public Expr number(Terminal terminal) {
    return new Value(Integer.parseInt(terminal.value()));
  }

  @ProductionName("E : ( E )")
  public Expr parens(Expr expr) { return expr; }

  @ProductionName("E : E + E")
  public Expr add(Expr left, Expr right) { return new Binary('+', left, right); }

  @ProductionName("E : E * E")
  public Expr mul(Expr left, Expr right) { return new Binary('*', left, right); }

  @ProductionName("E : E ^ E")
  public Expr pow(Expr left, Expr right) { return new Binary('^', left, right); }
}

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      precedence {
        left : '+'
        left : '*'
        right : '^'
      }
      grammar {
        E : number
        E : '(' E ')'
        E : E '+' E
        E : E '*' E
        E : E '^' E
      }
      """);

  mg.verify();

  var expr = mg.parse("2 ^ 3 ^ 4", new ExprVisitor());
  IO.println(expr);

  var expr2 = mg.parse("2 + 3 ^ 4", new ExprVisitor());
  //IO.println(expr2);
}
```

Output for `2 ^ 3 ^ 4`: `Binary[op=^, left=Value[2], right=Binary[op=^, left=Value[3], right=Value[4]]]`

> 💡 Right-to-left grouping is the natural behavior of right-recursive grammars,
>    but LR parsers handle left recursion more efficiently.
>    The `right:` associativity declaration achieves right-grouping without rewriting the grammar
>    as right-recursive, the best of both worlds.

---

## 15 — Unary Operator Gone Wrong [_15_unary_oops.java](_15_unary_oops.java)

**Goal:** See why naïvely adding a unary minus produces the wrong parse for `3 + - 2 * 4`.

Adding `E : '-' E` alongside `E : E '-' E` seems reasonable, but there is a trap:
by default, a production inherits the precedence of its **rightmost terminal**.
For `E : '-' E`, that terminal is `-`, which shares the same low precedence level as binary subtraction.
When the parser has `'-' E` on its stack and sees `*` as lookahead, `*` outranks `-`,
so it **shifts** — producing `-(2 * 4)` instead of the correct `(-2) * 4`.

```java
import com.github.forax.lazylr.*;

sealed interface Expr {}
record Value(int value) implements Expr {}
record Binary(char op, Expr left, Expr right) implements Expr {}
record Unary(char op, Expr expr) implements Expr {}

class ExprVisitor implements Visitor<Expr> {
  public Expr number(Terminal terminal) {
    return new Value(Integer.parseInt(terminal.value()));
  }

  @ProductionName("E : ( E )")
  public Expr parens(Expr expr) { return expr; }

  @ProductionName("E : E + E")
  public Expr add(Expr left, Expr right) { return new Binary('+', left, right); }

  @ProductionName("E : E * E")
  public Expr mul(Expr left, Expr right) { return new Binary('*', left, right); }

  @ProductionName("E : E ^ E")
  public Expr pow(Expr left, Expr right) { return new Binary('^', left, right); }

  @ProductionName("E : E - E")
  public Expr sub(Expr left, Expr right) { return new Binary('-', left, right); }

  @ProductionName("E : - E")
  public Expr minus(Expr expr) { return new Unary('-', expr); }
}

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      precedence {
        left : '+', '-'
        left : '*'
        right : '^'
      }
      grammar {
        E : number
        E : '(' E ')'
        E : E '+' E
        E : E '-' E
        E : E '*' E
        E : E '^' E
        E : '-' E
      }
      """);

  mg.verify();

  var expr = mg.parse("3 + - 2 * 4", new ExprVisitor());
  IO.println(expr);
}
```

Output (wrong): `Binary[op=+, left=Value[3], right=Unary[op=-, expr=Binary[op=*, left=Value[2], right=Value[4]]]]`

> 💡 The root cause is that a **production inherits the precedence of its rightmost terminal**.
>    Both `E : E - E` and `E : '-' E` end with `-`, so they share the same precedence level.
>    The unary production needs its own, higher level, which is exactly what `%prec` provides in the next demo.

---

## 16 — Fixing Unary with `%prec` [_16_unary_prec.java](_16_unary_prec.java)

**Goal:** Correct the unary minus precedence using a virtual token and the `%prec` directive,
so `3 + - 2 * 4 = 3 + ((-2) * 4) = -5`.

A **virtual token** is a name declared in the `precedence` section that is never emitted by the lexer.
It exists purely as a named precedence level. Declaring `UNARY` after `*` gives it a higher level;
annotating `E : '-' E` with `%prec UNARY` overrides the production's default precedence (inherited from `-`)
with the `UNARY` level.

```java
import com.github.forax.lazylr.*;

sealed interface Expr {}
record Value(int value) implements Expr {}
record Binary(char op, Expr left, Expr right) implements Expr {}
record Unary(char op, Expr expr) implements Expr {}

class ExprVisitor implements Visitor<Expr> {
  public Expr number(Terminal terminal) {
    return new Value(Integer.parseInt(terminal.value()));
  }

  @ProductionName("E : ( E )")
  public Expr parens(Expr expr) { return expr; }

  @ProductionName("E : E + E")
  public Expr add(Expr left, Expr right) { return new Binary('+', left, right); }

  @ProductionName("E : E * E")
  public Expr mul(Expr left, Expr right) { return new Binary('*', left, right); }

  @ProductionName("E : E ^ E")
  public Expr pow(Expr left, Expr right) { return new Binary('^', left, right); }

  @ProductionName("E : E - E")
  public Expr sub(Expr left, Expr right) { return new Binary('-', left, right); }

  @ProductionName("E : - E")
  public Expr minus(Expr expr) { return new Unary('-', expr); }
}

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      precedence {
        left : '+', '-'
        left : '*'
        right : '^'
        left : UNARY        // virtual token — never emitted by the lexer
      }
      grammar {
        E : number
        E : '(' E ')'
        E : E '+' E
        E : E '-' E
        E : E '*' E
        E : E '^' E
        E : '-' E    %prec UNARY
      }
      """);

  mg.verify();

  var expr = mg.parse("3 + - 2 * 4", new ExprVisitor());
  IO.println(expr);
}
```

Output (correct): `Binary[op=+, left=Value[3], right=Binary[op=*, left=Unary[op=-, expr=Value[2]], right=Value[4]]]`

Now when the parser has `'-' E` on its stack and sees `*`, `UNARY` outranks `*`,
so it reduces — binding the unary minus tightly to its operand before any binary operator can interfere.

> 💡 `%prec` can override the precedence of **any** production, not just unary operators.
>     A common use case is the dangling-else problem: give the `else` terminal higher precedence than
>     the bare `if-then` production so the parser always attaches `else` to the nearest `if`.
>     In general, whenever a production should not inherit its precedence from its rightmost terminal,
>     `%prec` is the right tool.

---

## 17 — Tracking Terminal Positions [_17_position_AST.java](_17_position_AST.java)

**Goal:** Record the source position of each token in the AST, so that later
phases (type-checkers, interpreters, error reporters) can point back to the
original input.

`Lexer#position(Iterator)` returns the character offset of the **most recently
shifted terminal** in the input string. The trick is that the visitor needs a
reference to the live iterator, which is why this demo uses the
`mg.parse(input, visitorFactory)` overload: the factory receives the iterator
before parsing starts, and the visitor stores it as a field.

```java
import com.github.forax.lazylr.*;

sealed interface Expr {
  int pos();
}
record Value(int value, int pos) implements Expr {}
record Binary(char op, Expr left, Expr right, int pos) implements Expr {}
record Unary(char op, Expr expr, int pos) implements Expr {}

class ExprVisitor implements Visitor<Expr> {
  final Iterator<Terminal> input;

  ExprVisitor(Iterator<Terminal> input) {
    this.input = input;
    super();
  }

  public Expr number(Terminal terminal) {
    var pos = Lexer.position(input);
    return new Value(Integer.parseInt(terminal.value()), pos);
  }

  public int minus(Terminal unusedTerminal) {
    return Lexer.position(input);
  }

  ...

  @ProductionName("E : E minus E")
  public Expr sub(Expr left, int unusedPos, Expr right) { return new Binary('-', left, right, left.pos()); }

  @ProductionName("E : minus E")
  public Expr unary(int minusPos, Expr expr) { return new Unary('-', expr, minusPos); }
}

void main() {
  var mg = MetaGrammar.load(...);

  mg.verify();

  var expr = mg.parse("3 + - 2 * 4", ExprVisitor::new);
  IO.println(expr);
}
```

Output: `Binary[op=+, left=Value[value=3, pos=0], right=Binary[op=*, left=Unary[op=-, expr=Value[value=2, pos=6], pos=4], right=Value[value=4, pos=10], pos=4], pos=0]`

There are two things worth noticing:

First, the `minus` terminal method returns `int` rather than `Expr`. Because
terminal methods are matched by name and can return any non-void type, you can
return a plain position integer instead of an AST node. The production method
receives the position directly as an `int` parameter.

Second, the method `mg.parse(input, visitorFactory)` overload is used instead of
`mg.parse(input, visitor)`. This is necessary because `Lexer.position(Iterator)`
requires the **exact same iterator object** as the parser is pulling from.
The factory hands that iterator to the visitor before the first token
is consumed, allowing the visitor to hold a reference to it.

> 💡 `Lexer.position(iterator)` returns the start offset (zero-based character index)
>    of the last terminal returned by the iterator.
>    Call it inside a terminal method right when the shift is done.

---

## That's all

To summarize, if there is a reduce/reduce conflict, the grammar has to be simplified.
If there is a shift/reduce conflict, the precedence map can be used to declare
which terminal is more important than the other and what is the associativity (`left` vs `right`).
For a production, if the inherited precedence, from the right-most terminal, is wrong, use `%prec`.

In doubt: run `mg.verify()` early and often :)

Happy parsing ...
