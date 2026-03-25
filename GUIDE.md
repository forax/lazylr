# Building an Arithmetic Grammar

### A Step-by-Step Tutorial

This guide walks you through building a fully-featured arithmetic expression parser,
from scratch, step by step.

We start with a simple grammar (a single number) and gradually add operators,
precedence rules, function calls,  and even conditional expressions.
By the end, you'll have hands-on intuition for how context-free grammars work,
how conflicts arise, and how to resolve them cleanly.

> 💬 **Who is this for?** Anyone who wants to understand parser construction in practice.
>    No prior compiler theory required, just Java familiarity.

---

## What is a Grammar?

A **grammar** is a set of rules that describe what valid input looks like.
It is built from three kinds of building blocks: terminals, non-terminals, and productions.

**Terminals** are the concrete, literal tokens that actually appear in the input text,
the raw words the lexer will recognize.
For example:
- the digit sequence `42` is matched by a terminal named `num`
- the character `+` is a terminal
- the keyword `if` is a terminal

Terminals are the leaves of any parse tree: they cannot be broken down further,
hence their name.

**Non-terminals** are named variables that stand for a pattern yet to be expanded.
They never appear in the raw input, they exist only inside the grammar as placeholders
that get replaced by sequences of other symbols.
For example, `E` (short for "expression") is a non-terminal: it represents the concept
of an expression, whatever form that takes.

As a developer, you can think of a terminal as a grammar-level value and
non-terminal as a grammar-level variable.

**Productions** are the rules that define what a non-terminal can expand into.
A production has a **head** (the non-terminal being defined) and
a **body** (the sequence of terminals and/or non-terminals it expands to).
For example:
- `E : num` means "an expression can be a single number"
- `E : E '+' E` means "an expression can be two expressions joined by a `+`"
- `E : '-' E` means "an expression can have the negatif sign as prefix"

A grammar is the complete collection of productions, along with a **start symbol**,
the non-terminal that represents the complete, valid input.

---

## What is an LALR Parser?

An **LALR (Look-Ahead Left-to-Right) parser** is a bottom-up parser.
"Bottom-up" means it works from the leaves of the grammar upward:
it reads terminals from the input and progressively replaces them
with non-terminals by applying productions in reverse,
until it arrives at the start symbol.

It does this by maintaining a **stack** and choosing at each step between two actions:

| Action     | What it does                                                                   |
|------------|--------------------------------------------------------------------------------|
| **Shift**  | Read the next terminal from the input and push it onto the stack.              |
| **Reduce** | Pop a sequence of symbols off the stack that matches the body of a production, |
|            | and push the production’s head non-terminal in their place.                    |

For example, given the productions `E : num` and `E : E '+' E`,
parsing `1 + 2` proceeds like this:

Shift `1` (a `num` terminal) : stack: `[num]`
Reduce `num` to `E` using `E : num` : stack: `[E]`
Shift `+` : stack: `[E, +]`
Shift `2` (a `num` terminal) : stack: `[E, +, num]`
Reduce `num` to `E` using `E : num` : stack: `[E, +, E]`
Reduce `E + E` to `E` using `E : E '+' E` : stack: `[E]` ✓

The parser uses the next token (one token of look-ahead) to decide which action to take at each step.
When it can’t decide, that’s a **conflict**, and you need to fix your grammar
or indicate how to resolve the conflict.
You’ll encounter your first conflict in Step 2.

---

## Step 1: The Simplest Grammar

> **Goal:** Parse and evaluate a single number, like `42`.

Parsing a string involves three distinct pieces working together:
a **Grammar** that defines what valid input looks like,
a **Lexer** that breaks raw text into tokens, and
an **Evaluator** that computes a result from the parse.

Let's build each one in turn.

### The Grammar

Here is our first grammar, with a single production:

```java
import module java.base;
import com.github.forax.lazylr.*;

var E   = new NonTerminal("E");  // non-terminal: the start symbol, represents an expression
var NUM = new Terminal("num");   // terminal: a digit sequence from the raw input

var pNum    = new Production(E, List.of(NUM));  // production: E : num
var grammar = new Grammar(E, List.of(pNum));    // E is the start symbol
```

Before doing anything else, it's good practice to check the grammar for conflicts:

```java
LALRVerifier.verify(grammar, Map.of(), msg -> System.err.println("Conflict: " + msg));
```

> 💡 `LALRVerifier.verify` checks that the grammar is conflict-free, that the parser will
>     never face an ambiguous decision.
>     You need at least two productions to have a conflict, so with only one production
>     there's nothing to conflict here.


### The Lexer

The lexer's job is to turn raw input text into a stream of named tokens.
You define it by listing token patterns as Java regular expressions.
The lexer uses a **longest-match** rule: when multiple patterns match at the same
position, the one that consumes the most characters wins. Ties are broken by
declaration order, so if two patterns match the same length of input, the earlier
one wins. This is why keywords like `sum` must be listed before
the general `ident` pattern — they match exactly as many characters, so the
keyword wins only because it is declared first.

```java
var lexer = Lexer.createLexer(List.of(
    new Token("num", "[0-9]+"),  // named token: matches one or more digits
    new Token("[ ]+")            // anonymous token: matches whitespace and discards it
));
```

Named tokens (like `"num"`) become `Terminal` objects that flow into the parser.
Anonymous tokens (no name) are silently skipped, useful for whitespace, comments,
and anything else you want to ignore.

The lexer doesn't produce a result on its own.
It produces an iterator of terminals that the parser will consume:

```java
Iterator<Terminal> tokens = lexer.tokenize("42");
```

### The Parser and Evaluator

The Parser of lazylr, unlike a traditional parser that compiles the whole grammar
to an automaton at once, is lazy and computing its state as it goes as input
terminals are discovered.

Having a lazy parser has advantages and inconvenients:
- Avantage: creating a parser with `Parser.createParser(grammar, precedenceMap)`is
  lighweight and fast. So the parser can be created dynamically and not offline.
- Inconvenenient: because the data structures are mutated during parsing, a `Parser`
  is not thread safe.

When the parser parses the stream of terminals, it does the equivalent of walking
a tree, from the bottom shifting terminals to the top reducing productions.

The `Evaluator<T>` interface allows to propagate values on this virtual tree.
It defines two methods:
- `T evaluate(Terminal t)`, called when a terminal is shifted; receives the matched token
  (its name and raw text value) and returns a value of type `T`,
- `T evaluate(Production p, List<T> args)`, called when a production is reduced; `args`
  contains the `T` values already returned for each symbol in the production body, in order.

```java
var parser = Parser.createParser(grammar, Map.of());

class IntEvaluator implements Evaluator<Integer> {
  public Integer evaluate(Terminal t) {
    System.out.println("seen terminal: " + t.name() + " = " + t.value());
    return Integer.parseInt(t.value());
  }

  public Integer evaluate(Production p, List<Integer> args) {
    System.out.println("seen production: " + p.name() + " with args " + args);
    return args.get(0);
  }
}

var result = parser.parse(lexer.tokenize("42"), new IntEvaluator());
System.out.println(result);
```

```
// Output:
// seen terminal: num = 42
// seen production: E : num with args [42]
// 42
```

> 💡 **Notice the order:** the terminal fires *before* the production that contains it.
>    That's bottom-up parsing in action, the parser always resolves the leaves of
>    the parse tree first, then folds them upward into larger structures.
>    The value returned by `evaluate(Terminal t)` is passed directly as an element of `args`
>    in the production evaluator above it.

The full runnable code for all steps lives in
[GuideTest.java](src/test/java/com/github/forax/lazylr/GuideTest.java).

---

## Step 2: Your First Conflict (Reduce/Reduce)

> **Scenario:** What happens when the grammar has two redundant ways to derive the same thing?

```java
var E   = new NonTerminal("E");
var A   = new NonTerminal("A");
var B   = new NonTerminal("B");
var NUM = new Terminal("num");

var pA       = new Production(E, List.of(A));    // E : A
var pB       = new Production(E, List.of(B));    // E : B
var pNumViaA = new Production(A, List.of(NUM));  // A : num
var pNumViaB = new Production(B, List.of(NUM));  // B : num   ← same as A!

var grammar = new Grammar(E, List.of(pA, pB, pNumViaA, pNumViaB));

LALRVerifier.verify(grammar, Map.of(), error -> {
    System.err.println("Conflict detected: " + error);
});
```

```
// Output:
// Reduce/reduce conflict in state 4 on terminal '$' between
//   [Reduce[production=A : num]] and [Reduce[production=B : num]]
```

> ⚠️ **What happened?** After reading a `num`, the parser knows
>    it should reduce, but to `A` or `B`? Both are valid.
>    It has no way to choose. That's a **Reduce/Reduce conflict**.

> 💡 **How to fix it:** Reduce/Reduce conflicts almost always mean
>    your grammar has a redundant or overlapping structure.
>    The solution is to remove the duplication.

---

## Step 3: Recursion and Function Calls

> **Goal:** Parse and evaluate `sum(42, 17)`, `sum(3)`, and even `sum()`.

This step introduces two important ideas:
**recursion** and **epsilon productions** (rules that derive nothing).

From here on, we use `MetaGrammar.load(...)` to describe the grammar as text,
rather than building Java objects by hand.

The format has three sections:

| Section      | Purpose                                                                                    |
|--------------|--------------------------------------------------------------------------------------------|
| `tokens`     | Named terminals (`name: /regex/`) and anonymous ignored patterns (`/regex/`)               |
| `precedence` | Operator associativity and priority, lowest to highest                                     |
| `grammar`    | BNF-style production rules; quoted literals like `'('` are auto-registered as terminals    |

```java
var mg = MetaGrammar.load("""
    tokens {
      sum: /sum/
      num: /[0-9]+/
      /[ ]+/
    }
    grammar {
      E:    num
      E:    sum '(' ARGS ')'
      ARGS: E
      ARGS: ARGS ',' E
      ARGS:
    }
    """);

LALRVerifier.verify(mg.grammar(), Map.of(), System.err::println);
```

> 💡 **Two things to notice:**
>    - `ARGS:` (a bare rule with no right-hand side) is an **epsilon production**,
>      it lets `sum()` be valid with zero arguments.
>    - `ARGS: ARGS ',' E` is **left-recursive**, it builds the argument list
>      from left to right, which is exactly how LR parsers like to work.
>      Avoid right-recursion for lists; it can cause stack overflows
>      on deeply nested input.

```java
var lexer  = Lexer.createLexer(mg.tokens());
var parser = Parser.createParser(mg.grammar(), Map.of());

class IntEvaluator implements Evaluator<Integer> {
  public Integer evaluate(Terminal t) {
    return switch (t.name()) {
      case "num" -> Integer.parseInt(t.value());
      default -> 0;
    };
  }

  public Integer evaluate(Production p, List<Integer> args) {
    return switch (p.name()) {
      case "E : num"          -> args.get(0);
      case "ARGS : E"         -> args.get(0);
      case "ARGS : ARGS , E"  -> args.get(0) + args.get(2);
      case "ARGS : ε"         -> 0;
      case "E : sum ( ARGS )" -> args.get(2);
      default -> throw new IllegalStateException("unknown production " + p.name());
    };
  }
}

System.out.println(parser.parse(lexer.tokenize("sum(42, 17)"), new IntEvaluator()));
```

```
// Output:
// 59
```

> 💡 **Trace the accumulation:** `42` reduces to `ARGS` (value: 42). When `, 17` is seen,
>    `ARGS : ARGS , E` fires, adding 42 and 17. The accumulated 59 is then passed into
>    `E : sum ( ARGS )`, which returns it directly.

---

## Step 4: Addition and Associativity

> **Goal:** Evaluate `1 + 2 + 3` to `6`.

The rule `E : E + E` is inherently **ambiguous**. Does `1 + 2 + 3` mean `(1 + 2) + 3` or `1 + (2 + 3)`?
For addition, the result is the same either way, but the parser still needs to commit to one interpretation.
Without guidance, it complains:

```java
var mg = MetaGrammar.load("""
    tokens {
      num: /[0-9]+/
      /[ ]+/
    }
    grammar {
      E: num
      E: E '+' E
    }
    """);

LALRVerifier.verify(mg.grammar(), Map.of(), System.err::println);
```

```
// Output:
// Unresolved Shift/Reduce conflict in state 4 on terminal '+' between
//   [Reduce[production=E : E + E]] and [Shift[target=3]]
```

The parser has `E + E` on its stack and sees another `+`.
Should it finish the current addition first (reduce) or wait to see if the next addition takes priority (shift)?

Declaring `left: '+'` in the `precedence` section tells it: **reduce first** (left associative).

```java
var mg = MetaGrammar.load("""
    tokens {
      num: /[0-9]+/
      /[ ]+/
    }
    precedence {
      left: '+'
    }
    grammar {
      E: num
      E: E '+' E
    }
    """);

LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), System.err::println);
```

> 💡 **Note:** `%prec '+'` is not needed here, by default, a production inherits the precedence
>    of its rightmost terminal.
>    Since that's already `'+'`, the precedence map does the right thing automatically.

```java
var lexer  = Lexer.createLexer(mg.tokens());
var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());

class IntEvaluator implements Evaluator<Integer> {
  public Integer evaluate(Terminal t) {
    return switch (t.name()) {
      case "num" -> Integer.parseInt(t.value());
      default -> 0;
    };
  }

  public Integer evaluate(Production p, List<Integer> args) {
    return switch (p.name()) {
      case "E : num"   -> args.get(0);
      case "E : E + E" -> {
        System.out.println("Reducing " + p + " with args " + args);
        yield args.get(0) + args.get(2);
      }
      default -> throw new IllegalStateException("unknown production " + p.name());
    };
  }
}

System.out.println(parser.parse(lexer.tokenize("1 + 2 + 3"), new IntEvaluator()));
```

```
// Output:
// Reducing E : E + E with args [1, 0, 2]
// Reducing E : E + E with args [3, 0, 3]
// 6
```

> 💡 **Read the trace:** `1 + 2` reduces *first* (producing 3), then `3 + 3` is evaluated.
>    That's left-associativity in action.
>    Notice also that the middle element of `args` (index 1) is always `0`,
>    that's the `'+'` terminal, whose evaluated value comes from the `default -> 0` branch.

---

## Step 5: Multiplication and Precedence

> **Goal:** Evaluate `2 + 3 * 4` to `14`, not `20`.

Now let's add multiplication. Without extra guidance, the grammar would have even more conflicts,
one for `+` and one for `*`. More importantly, we need `*` to bind more tightly than `+`.

The rule is simple: **later lines in the `precedence` section have higher precedence**.

```java
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
      E: num
      E: E '+' E
      E: E '*' E
    }
    """);

LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), System.err::println);
```

> 💡 **Precedence levels are relative, not absolute**, only their ordering matters.
>    When the parser has `E + E` on its stack and sees `*` as lookahead, it compares levels.
>    Since `*` is declared after `+`, it has higher priority,
>    so the parser **shifts** (reads more input) rather than reducing `E + E` early.

```java
// ... (lexer and parser setup as above)

class IntEvaluator implements Evaluator<Integer> {
  public Integer evaluate(Terminal t) {
    return switch (t.name()) {
      case "num" -> Integer.parseInt(t.value());
      default -> 0;
    };
  }

  public Integer evaluate(Production p, List<Integer> args) {
    return switch (p.name()) {
      case "E : num"   -> args.get(0);
      case "E : E + E" -> { System.out.println("+ with " + args); yield args.get(0) + args.get(2); }
      case "E : E * E" -> { System.out.println("* with " + args); yield args.get(0) * args.get(2); }
      default -> throw new IllegalStateException("unknown production " + p.name());
    };
  }
}

System.out.println(parser.parse(lexer.tokenize("2 + 3 * 4"), new IntEvaluator()));
```

```
// Output:
// * with [3, 0, 4]
// + with [2, 0, 12]
// 14
```

> 💡 `*` fires before `+`: `3 * 4` becomes 12 first, then `2 + 12` is evaluated. Multiplication wins.

---

## Step 6: Exponentiation (Right Associativity)

> **Goal:** Evaluate `2 ^ 3 ^ 2` to `512`.

Exponentiation is **right-associative**: `2 ^ 3 ^ 2` = `2 ^ (3 ^ 2)` = `2 ^ 9` = 512, not `(2 ^ 3) ^ 2` = 64.

To declare this, use `right:` instead of `left:` in the precedence section:

```java
var mg = MetaGrammar.load("""
    tokens {
      num: /[0-9]+/
      /[ ]+/
    }
    precedence {
      left:  '+'
      left:  '*'
      right: '^'
    }
    grammar {
      E: num
      E: E '+' E
      E: E '*' E
      E: E '^' E
    }
    """);

LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), System.err::println);
```

> 💡 **How right-associativity works:** When the parser sees `E ^ E` on its stack and
>    a `^` lookahead, **left** associativity would reduce now; **right** associativity
>    makes it **shift** instead, deferring the reduction and grouping from the right.

```java
// ... evaluator with (int) Math.pow(args.get(0), args.get(2)) for "E : E ^ E"

System.out.println(parser.parse(lexer.tokenize("2 ^ 3 ^ 2"), evaluator));
```

```
// Output:
// Reducing E : E ^ E with args [3, 0, 2]   ← 3^2 = 9 first
// Reducing E : E ^ E with args [2, 0, 9]   ← then 2^9 = 512
// 512
```

> 💡 The trace reveals right-associativity in action: `3 ^ 2` reduces
>    *before* `2 ^ ...`, which is exactly the grouping we want.

---

## Step 7: The Dangling Else

> **Goal:** Evaluate `if 1 then if 0 then 99 else 42` to `42`.

This is a classic parser puzzle. Given `if A then if B then X else Y`, which `if` does the `else` belong to?

In most languages (and in this grammar), the answer is: **the nearest `if`**.
So `else 42` belongs to the inner `if 0`, giving `42` when the outer condition is true but the inner is false.

Two things to keep in mind when adding keywords:
1. Declare keyword tokens **before** more general patterns like `ident`, because
   when keywords match the same number of characters as identifiers, the declaration order is used.
2. Use the precedence section to resolve the ambiguity: give `else` higher precedence than `then`,
   forcing the parser to always shift `else` rather than reduce early.

```java
var mg = MetaGrammar.load("""
    tokens {
      if:   /if/
      then: /then/
      else: /else/
      num:  /[0-9]+/
      /[ ]+/
    }
    precedence {
      right: then
      left:  '+'
      left:  '*'
      right: '^'
      right: else
    }
    grammar {
      E: num
      E: E '+' E
      E: E '*' E
      E: E '^' E
      E: if E then E
      E: if E then E else E
    }
    """);

LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), System.err::println);
```

> 💡 **What's the conflict?** When the parser sees `if E then E` on its stack and an `else` lookahead,
>    it must choose: reduce (using `E: if E then E`) or shift the `else`.
>    By giving `else` higher precedence than `then`, we force a **shift**:
>    the `else` always binds to the nearest (innermost) `if`.

```java
var evaluator = new IntEvaluator(); // handles if/then/else cases

System.out.println(parser.parse(lexer.tokenize("if 1 then 10 else 20"),              evaluator));
System.out.println(parser.parse(lexer.tokenize("if 0 then 10 else 20"),              evaluator));
System.out.println(parser.parse(lexer.tokenize("if 1 then if 0 then 99 else 42"),    evaluator));
```

```
// Output:
// 10
// 20
// 42
```

> 💡 The last line confirms it: `else 42` attached to the inner `if 0`, not the outer `if 1`.
>    The outer condition was true, so we entered the inner `if`; the inner condition was false,
>    so we took the `else`.

---

## Step 8: Unary Operators and `%prec`

> **Goal:** Parse `- 4 * 5` as `(-4) * 5`, not `-(4 * 5)`, and record each number's source position.

All our operators so far have been binary. Let's add a **unary minus**.

```java
var mg = MetaGrammar.load("""
    tokens {
      num: /[0-9]+/
      /[ ]+/
    }
    precedence {
      left:  '-'
      left:  '*'
    }
    grammar {
      E: num
      E: E '-' E
      E: E '*' E
      E: '-' E
    }
    """);
```

This looks reasonable, but there's a problem.
When the parser sees `'-' E` on its stack and a `*` as lookahead, it compares the precedence
of the unary production, which it inherits from the terminal `'-'` (low), against `*` (high).
Since `*` wins, the parser **shifts**, producing `-(4 * 5)` instead of the correct `(-4) * 5`.

The fix is a **virtual precedence token**: a name declared in the `precedence` section
that has no corresponding entry in the `tokens` section and is never emitted by the lexer.
It exists purely as a named precedence level that a production can opt into via `%prec`,
overriding the default precedence.


```java
var mg = MetaGrammar.load("""
    tokens {
      num: /[0-9]+/
      /[ ]+/
    }
    precedence {
      left:  '-'
      left:  '*'
      right: UNARY  // virtual token
    }
    grammar {
      E: num
      E: E '-' E
      E: E '*' E
      E: '-' E      %prec UNARY
    }
    """);
```

> 💡 `UNARY` is declared after `*`, giving it higher priority than any binary operator.
>    The `%prec UNARY` annotation on `E: '-' E` overrides the default precedence (which would inherit from `-`)
>    with the `UNARY` level. Now when the parser has `'-' E` on its stack and sees `*`, `UNARY` outranks `*`,
>    so it reduces, binding the unary minus tightly to its operand before any binary operator can interfere.

### Recording source positions

Real compilers and interpreters need to report where in the source an error occurred.
The `Lexer.position(iterator)` method returns the character offset of the last terminal
returned by `next()`, making it straightforward to embed positions directly into AST nodes.

The key is to pass the iterator created by the lexer to the evaluator,
so that `Lexer.position()` can be called from inside `evaluate(Terminal)` at the
moment the token is shifted — before the iterator advances further:

```java
sealed interface Node {}
record Sub(Node left, Node right) implements Node {}
record Mul(Node left, Node right) implements Node {}
record UnaryMinus(Node node) implements Node {}
record Num(int value, int pos) implements Node {}

record NodeEvaluator(Iterator<Terminal> input) implements Evaluator<Node> {
  @Override
  public Node evaluate(Terminal terminal) {
    return switch (terminal.name()) {
      case "num" -> {
        var pos = Lexer.position(input);   // capture position at shift time
        yield new Num(Integer.parseInt(terminal.value()), pos);
      }
      default -> null;
    };
  }

  @Override
  public Node evaluate(Production production, List<Node> args) {
    return switch (production.name()) {
      case "E : num"   -> args.getFirst();
      case "E : E - E" -> new Sub(args.get(0), args.get(2));
      case "E : E * E" -> new Mul(args.get(0), args.get(2));
      case "E : - E"   -> new UnaryMinus(args.get(1));
      default -> throw new IllegalStateException("Unexpected production: " + production.name());
    };
  }
}
```

Notice that `NodeEvaluator` is declared as a **record** that holds the iterator as a component.
This is the idiomatic way to give the evaluator access to position information
without resorting to mutable fields or closures.

Putting it all together:

```java
var lexer  = Lexer.createLexer(mg.tokens());
var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());
var input  = lexer.tokenize("- 4 * 5");

var node = parser.parse(input, new NodeEvaluator(input));
System.out.println(node);
```

```
// Output:
// Mul[left=UnaryMinus[node=Num[value=4, pos=2]], right=Num[value=5, pos=6]]
```

`(-4) * 5`, exactly what we wanted.

> 💡 `pos=2` means the token `4` starts at character offset 2 in the input (`"- 4 * 5"`),
>    and `pos=6` means the token `5` starts at offset 6.

---

## That's all

To summarize, if there is a reduce/reduce conflict, the grammar has to be simplified.
If there is a shift/reduce conflict, the precedence map can be used to declare
which terminal is more important than the other and what is the associativity (LEFT vs RIGHT).

In doubt: run `LALRVerifier.verify` early and often :)

Happy parsing ...
