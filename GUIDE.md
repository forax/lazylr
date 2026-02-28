# Building an Arithmetic Grammar

### A Step-by-Step Tutorial

This guide walks you through building a fully-featured arithmetic language parser
from the ground up — starting with a single number and ending with conditional expressions.

Along the way, you'll learn how context-free grammars work, how conflicts arise,
and how precedence rules tame ambiguity.

---

## What is an LALR Parser?

An **LALR (Look-Ahead Left-to-Right) parser** is a type of bottom-up parser that reads tokens left to right and
uses one token of "look-ahead" to decide what action to take.

It works by maintaining a **stack** and choosing between two actions:

- **Shift** — push the next input token onto the stack
- **Reduce** — replace a sequence of stack symbols with a non-terminal using a grammar production

When the parser can't decide which action to take, that's called a **conflict** — and
your grammar needs to be fixed.

---

## Step 1: The Base

> **Goal:** Parse and evaluate a single number like `42`.

Every language needs a foundation.
Here, the foundation is a single grammar rule: an expression `E` can be a number.

```java
import module java.base;
import com.github.forax.lazylr.*;

void main() {
  var E   = new NonTerminal("E");
  var NUM = new Terminal("num");

  var pNum    = new Production(E, List.of(NUM));
  var grammar = new Grammar(E, List.of(pNum));

  LALRVerifier.verify(grammar, Map.of(), msg -> fail("Unexpected conflict: " + msg));
}
```

> 💡 **Insight:** With only one production and one terminal,
>    the parser has exactly one thing it can do at every step.

Once we know that the grammar is correct, it can be evaluated!

```java
var lexer = Lexer.createLexer(List.of(
    new Rule("num", "[0-9]+"),
    new Rule("[ ]+")     // whitespaces are ignored
));

var parser = Parser.createParser(grammar, Map.of());

// compute the result
var input = "42";
var result = parser.parse(lexer.tokenize(input), new Evaluator<Integer>() {
  public Integer evaluate(Terminal t) {
    System.out.println("seen terminal " + t.name());
    return Integer.parseInt(t.value());
  }

  public Integer evaluate(Production p, List<Integer> args) {
    // args corresponds to the value of the symbols in the Production list (0-indexed).
    System.out.println("seen production " + p.name() + " with " + args);
    return args.get(0);
  }
});
System.out.println(result);
```

```
// Output:
// seen terminal num
// seen production E : num with [42]
// 42
```

> 💡 **Insight:** Notice the order of output: the terminal is always seen *before* the production that contains it.
>    This is bottom-up parsing in action — the parser fully resolves leaves before reducing them into
>    larger structures. The evaluator mirrors this: `evaluate(Terminal t)` runs first, and its return value
>    is then passed as an element of `args` into `evaluate(Production p, ...)`.

The full code is available in [GuideTest.java](src/test/java/com/github/forax/lazylr/GuideTest.java).

---

## Step 2: The Reduce/Reduce Conflict

> **Scenario:** What happens when the grammar has two ways to do the same thing?

```java
var E = new NonTerminal("E");
var A = new NonTerminal("A");
var B = new NonTerminal("B");
var NUM = new Terminal("num");

var pA = new Production(E, List.of(A));
var pB = new Production(E,  List.of(B));
var pNumViaA= new Production(A, List.of(NUM));
var pNumViaB = new Production(B, List.of(NUM));

var grammar = new Grammar(E, List.of(pA, pB, pNumViaA, pNumViaB));

// This will print a conflict
LALRVerifier.verify(grammar, Map.of(), error -> {
    System.err.println("Conflict detected: " + error);
});
```

```
// Output:
// Reduce/reduce conflict in state 4 on terminal '$' between
//   [Reduce[production=A : num]] and [Reduce[production=B : num]]
```

> ⚠️ **What's happening?** After reading a num, the parser knows it should *reduce* — but to
>    `pNumViaA` or `pNumViaB`? It has no way to decide. This is a **Reduce/Reduce conflict**.

> 💡 **Insight:** Reduce/Reduce conflicts almost always signal **redundant or overlapping logic** in your grammar.
>    The fix is simple: delete the duplicate. In real-world grammars, these conflicts can be subtle — e.g.,
>    two different non-terminals that can both derive the same textual form.

---

## Step 3: Recursion

> **Goal:** Parse and evaluate function calls like `sum(10, 20)`, `sum(3)`, or `sum()`.

Now we introduce recursion and an **epsilon production** (a rule that derives nothing — the empty terminal).

From this step onward, we use `MetaGrammar` to describe the grammar, tokens, and precedence
in a compact textual DSL instead of building the objects by hand.

The DSL has three sections:
- **`tokens`** — named terminals (`name: /regex/`) and anonymous ignored patterns (`/regex/`)
- **`precedence`** — operator associativity and priority, from lowest to highest
- **`grammar`** — BNF-style production rules; quoted literals like `'('` are automatically registered as terminals

```java
var mg = MetaGrammar.create("""
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

> 💡 **Insight:** The epsilon production `ARGS:` (an empty right-hand side) allows `sum()` to be valid
>    with zero arguments. The `ARGS: ARGS ',' E` rule is **left-recursive** — it builds the argument list
>    from left to right, which aligns naturally with how LALR parsers process input.
>    Right-recursive rules can sometimes cause stack overflows on deeply nested inputs.

```java
var lexer = Lexer.createLexer(mg.rules());
var parser = Parser.createParser(mg.grammar(), Map.of());

var input = "sum(42, 17)";
var result = parser.parse(lexer.tokenize(input), new Evaluator<Integer>() {
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
});
System.out.println(result);
```

```
// Output:
// 59
```

> 💡 **Insight:** Follow how `42, 17` accumulates through the grammar.
>    First, `42` is reduced to `ARGS` via `ARGS : E` (value: 42).
>    Then, when `, 17` is read, `ARGS : ARGS , E` fires — adding `args.get(0)` (42) and `args.get(2)` (17).
>    The fully reduced `ARGS` (value: 59) is then passed as `args.get(2)` into `E : sum ( ARGS )`,
>    which returns it directly. The outer `sum(...)` call simply surfaces the total its arguments accumulated.

---

## Step 4: Addition and Associativity

> **Goal:** Evaluate `1 + 2 + 3` to `6`.

The rule `E → E + E` is inherently **ambiguous** — does `1 + 2 + 3` mean `(1 + 2) + 3`
or `1 + (2 + 3)`?

It doesn't change the result, but you still need to tell the parser which way to group.

First, let's see the conflict without precedence:

```java
var mg = MetaGrammar.create("""
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

> 💡 **Insight:** The conflict occurs because the parser doesn't know whether
>    to finish the first addition (1+2) or wait to see if the second addition takes priority.

Declaring `left: '+'` in the `precedence` section resolves it:

```java
var mg = MetaGrammar.create("""
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

> 💡 **Insight:** Associativity resolves **Shift/Reduce conflicts** that arise from rules like `E → E + E`.
>    When the parser has `E + E` on its stack and sees another `+`,
>    it must choose: reduce now (left-assoc) or shift and wait (right-assoc).
>    The precedence map encodes this decision.

> **Note:** By default, the precedence of a production is the precedence of its right-most terminal,
>    so there is no need to also list the production in the precedence map.

```java
var lexer = Lexer.createLexer(mg.rules());
var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());

var input = "1 + 2 + 3";
var result = parser.parse(lexer.tokenize(input), new Evaluator<Integer>() {
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
});
System.out.println(result);
```

```
// Output:
// Reducing E : E + E with args [1, 0, 2]
// Reducing E : E + E with args [3, 0, 3]
// 6
```

> 💡 **Insight:** The `args` list for `E : E + E` always has three elements — left operand, the `+` terminal
>    (whose evaluated value is `0` from the `default` branch), and right operand.
>    The print trace confirms left-associativity: `1 + 2` is reduced *first* (producing 3),
>    and only then is `3 + 3` evaluated. If associativity were RIGHT, you would see `2 + 3` evaluated first.

---

## Step 5: Multiplication and Priority

> **Goal:** Evaluate `2 + 3 * 4` to `14` (not `20`).

Adding `E: E '*' E` without precedence introduces more conflicts.
Different operators need different **priority levels** — multiplication should bind more tightly than addition.

In the `precedence` section, **later lines have higher precedence than earlier ones**:

```java
var mg = MetaGrammar.create("""
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

LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), msg -> System.err.println(msg));
```

> 💡 **Insight:** Precedence levels are relative, not absolute — only their ordering matters.
>    When the parser has `E + E` on its stack and sees `*` as lookahead, it compares precedence levels.
>    Since `*` is declared after `+`, it has higher priority, so the parser **shifts** (reads more input)
>    rather than reducing, effectively giving `*` tighter binding.

```java
var lexer = Lexer.createLexer(mg.rules());
var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());

var input = "2 + 3 * 4";
var result = parser.parse(lexer.tokenize(input), new Evaluator<Integer>() {
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
        System.out.println("+ called with " + args);
        yield args.get(0) + args.get(2);
      }
      case "E : E * E" -> {
        System.out.println("* called with " + args);
        yield args.get(0) * args.get(2);
      }
      default -> throw new IllegalStateException("unknown production " + p.name());
    };
  }
});
System.out.println(result);
```

```
// Output:
// * called with [3, 0, 4]
// + called with [2, 0, 12]
// 14
```

> 💡 **Insight:** The output confirms that `*` fires before `+`: `3 * 4` is reduced to `12` first,
>    then `2 + 12` is evaluated.

---

## Step 6: Exponentiation

> **Goal:** Evaluate `2 ^ 3 ^ 2` to `512`.

Mathematically, exponentiation is **right-associative**: `2 ^ 3 ^ 2` = `2 ^ (3 ^ 2)` = `2 ^ 9` = 512,
not `(2 ^ 3) ^ 2` = 64.

```java
var mg = MetaGrammar.create("""
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

LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), msg -> System.err.println(msg));
```

> 💡 **Insight:** With **RIGHT** associativity, when the parser sees `E ^ E` on its stack
>    and a `^` lookahead, it **shifts** instead of reducing — deferring the reduction
>    and effectively grouping from the right.

```java
var lexer = Lexer.createLexer(mg.rules());
var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());

var input = "2 ^ 3 ^ 2";
var result = parser.parse(lexer.tokenize(input), new Evaluator<Integer>() {
  public Integer evaluate(Terminal t) {
    return switch (t.name()) {
      case "num" -> Integer.parseInt(t.value());
      default -> 0;
    };
  }
  public Integer evaluate(Production p, List<Integer> args) {
    return switch (p.name()) {
      case "E : num"   -> args.get(0);
      case "E : E + E" -> args.get(0) + args.get(2);
      case "E : E * E" -> args.get(0) * args.get(2);
      case "E : E ^ E" -> {
        System.out.println("Reducing " + p + " with args " + args);
        yield (int) Math.pow(args.get(0), args.get(2));
      }
      default -> throw new IllegalStateException("unknown production " + p.name());
    };
  }
});
System.out.println(result);
```

```
// Output:
// Reducing E : E ^ E with args [3, 0, 2]
// Reducing E : E ^ E with args [2, 0, 9]
// 512
```

> 💡 **Insight:** The output reveals right-associativity in action: `3 ^ 2`
>    is reduced *first* (to 9), then `2 ^ 9` is computed (giving 512).

---

## Step 7: The Dangling Else

> **Goal:** Evaluate `if 1 then if 0 then 99 else 42` to `42`.

The classic "dangling else" problem: given `if A then if B then X else Y`, which `if` does the `else` belong to?

In the `tokens` section, Keywords like `if`, `then`, and `else` must be declared as named tokens,
before `num`, so the lexer gives them priority over any general identifier pattern.
In the `precedence` section, `if` is listed first (lowest precedence) and `else` last (highest),
which forces the parser to always shift `else` rather than reduce:

```java
var mg = MetaGrammar.create("""
    tokens {
      if:   /if/
      then: /then/
      else: /else/
      num:  /[0-9]+/
      /[ ]+/
    }
    precedence {
      right: if
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

LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), msg -> System.err.println(msg));
```

> 💡 **Insight:** This is a **Shift/Reduce conflict**. When the parser sees `if E then E`
>    on the stack and an `else` as lookahead, should it reduce (`E: if E then E`) or shift the `else`?
>    By giving `else` the highest precedence, we force a **shift**, which means the `else`
>    always binds to the **nearest** (innermost) `if`.

```java
var lexer = Lexer.createLexer(mg.rules());
var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());

var evaluator = new Evaluator<Integer>() {
  public Integer evaluate(Terminal t) {
    return switch (t.name()) {
      case "num" -> Integer.parseInt(t.value());
      default -> 0;
    };
  }
  public Integer evaluate(Production p, List<Integer> args) {
    return switch (p.name()) {
      case "E : num"                -> args.get(0);
      case "E : E + E"              -> args.get(0) + args.get(2);
      case "E : E * E"              -> args.get(0) * args.get(2);
      case "E : E ^ E"              -> (int) Math.pow(args.get(0), args.get(2));
      case "E : if E then E"        -> args.get(1) != 0 ? args.get(3) : 0;
      case "E : if E then E else E" -> args.get(1) != 0 ? args.get(3) : args.get(5);
      default -> throw new IllegalStateException("unknown production: " + p.name());
    };
  }
};

System.out.println(parser.parse(lexer.tokenize("if 1 then 10 else 20"), evaluator));
System.out.println(parser.parse(lexer.tokenize("if 0 then 10 else 20"), evaluator));
System.out.println(parser.parse(lexer.tokenize("if 1 then if 0 then 99 else 42"), evaluator));
```

```
// Output:
// 10
// 20
// 42
```

> 💡 **Insight**: The output confirms that `E: if E then E else E` was chosen over `E: if E then E` —
>    the `else` was shifted and bound to the inner `if`.

---

## Summary: Grammar and Conflict Resolution Cheat Sheet

| Conflict Type | Cause | Fix |
|---|---|---|
| **Reduce/Reduce** | Two productions match the same input | Remove redundant production |
| **Shift/Reduce (assoc)** | Operator applied twice: `a + b + c` | Set LEFT or RIGHT associativity |
| **Shift/Reduce (prec)** | Two operators compete: `a + b * c` | Declare higher-priority operator later in the `precedence` section |
| **Dangling Else** | `else` could bind to multiple `if`s | Declare `else` last in `precedence` to force a shift |