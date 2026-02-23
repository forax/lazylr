# Building an Arithmetic Grammar

### A Step-by-Step Tutorial

This guide walks you through building a fully-featured arithmetic language parser
from the ground up — starting with a single number and ending with conditional expressions.

Along the way, you'll learn how context-free  grammars work, how conflicts arise,
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
  var E = new NonTerminal("E");
  var NUM = new Terminal("num");
  var pNum = new Production(E, List.of(NUM));

  var grammar = new Grammar(E, List.of(pNum));

  // verify
  LALRVerifier.verify(grammar, Map.of(), System.err::println);
}
```

> 💡 **Insight:** With only one production and one terminal.
>    The parser has exactly one thing it can do at every step.

Once we know that the grammar is correct, it can be evaluated !

```java
var input = "42";

var lexer = Lexer.createLexer(List.of(
    new Rule("num", "[0-9]+"),
    new Rule("[ ]+")     // whitespaces are ignored
));

// compute the result
var parser = Parser.createParser(grammar, Map.of());
var result = parser.parse(lexer.tokenize(input), new Evaluator<Integer>() {
  public Integer evaluate(Terminal t) {
    System.out.println("seen terminal " + t.name());
    return Integer.parseInt(t.value());
  }

  public Integer evaluate(Production p, List<Integer> args) {
    System.out.println("seen production " + p.name());
    return args.get(0); // Pass the number's value straight through
  }
});
System.out.println(result);
```

---

## Step 2: The Reduce/Reduce Conflict

> **Scenario:** What happens when the grammar has two identical rules?

```java
var pNum1 = new Production(E, List.of(NUM));
var pNum2 = new Production(E, List.of(NUM)); // Duplicate!

var grammar = new Grammar(E, List.of(pNum1, pNum2));

// This will print a conflict
LALRVerifier.verify(grammar, Map.of(), error -> {
    System.err.println("Conflict detected: " + error);
});
```

> ⚠️ **What's happening?** After reading `42`, the parser knows it should *reduce* — but to `pNum1` or `pNum2`?
>    It has no way to decide. This is a **Reduce/Reduce conflict**.

> 💡 **Insight:** Reduce/Reduce conflicts almost always signal **redundant or overlapping logic** in your grammar.
>    The fix is simple: delete the duplicate. In real-world grammars, these conflicts can be subtle — e.g.,
>    two different non-terminals that can both derive the same string.

---

## Step 3: Recursion

> **Goal:** Parse and evaluate function calls like `sum(10, 20)`, `sum(3)` or `sum()`.

Now we introduce recursion and an **epsilon production** (a rule that derives nothing — the empty terminal).

```java
import com.github.forax.lazylr.Parser;

var SUM = new Terminal("sum");
var ARGS = new NonTerminal("ARGS");

var pArgsSingle = new Production(ARGS, List.of(E));
var pArgsMulti = new Production(ARGS, List.of(ARGS, new Terminal(","), E));
var pArgsEmpty = new Production(ARGS, List.of()); // ε (epsilon)
var pCall = new Production(E, List.of(SUM, new Terminal("("), ARGS, new Terminal(")")));

var grammar = new Grammar(E, List.of(pNum, pArgsSingle, pArgsMulti, pArgsEmpty, pCall));

LALRVerifier.verify(grammar, Map.of(), System.err::println);
```

> 💡 **Insight:** The epsilon production `pArgsEmpty` allows `sum()` to be valid with zero arguments.
>    The `pArgsMulti` rule is **left-recursive** — it builds the argument list from left to right,
>    which aligns naturally with how LALR parsers process input.
>    Right-recursive rules can sometimes cause stack overflows on deeply nested inputs.

```java
var input = "sum(42, 17)";

var lexer = Lexer.createLexer(List.of(
    new Rule("sum", "sum"),
    new Rule(",", ","),
    new Rule("(", "("),
    new Rule(")", ")"),
    new Rule("num", "[0-9]+"),
    new Rule("[ ]+")     // whitespaces are ignored
));

var parser = Parser.createParser(grammar, Map.of());
int result = parser.parse(lexer.tokenize(input), new Evaluator<Integer>() {
  public Integer evaluate(Terminal t) {
    return switch (t.name()) {
      case "num" -> Integer.parseInt(t.value());
      default -> 0;
    };
  }

  public Integer evaluate(Production p, List<Integer> args) {
    return switch(p.name()) {
      case "E : num" -> args.get(0);
      case "ARGS : E" -> args.get(0);
      case "ARGS : ARGS , E" -> args.get(0) + args.get(2);
      case "ARGS : ε" -> 0;
      case "E : sum ( ARGS )" -> args.get(2);
      default -> throw new IllegalStateException("unknown production " + p.name());
    };
  });
```

## Step 4: Addition and Associativity

> **Goal:** Evaluate `1 + 2 + 3 → 6`.

The rule `E → E + E` is inherently **ambiguous** — does `1 + 2 + 3` mean `(1 + 2) + 3`
or `1 + (2 + 3)`?

In addition, it doesn't change the result, but you still need to tell the parser which way to group.

```java
var PLUS = new Terminal("+");
var pPlus = new Production(E, List.of(E, PLUS, E));

// LEFT associativity: groups as (1 + 2) + 3
var precAdd = new Precedence(10, Precedence.Associativity.LEFT);
var precedenceMap = Map.of(PLUS, precAdd, pPlus, precAdd);

var grammar = new Grammar(E, List.of(pNum, pPlus));

// Evaluator
  ...
  case "E : E + E" -> {
    System.out.println("called with " + args);
    yield args.get(0) + args.get(2);
  }    
```

> 💡 **Insight:** Associativity resolves **Shift/Reduce conflicts** that arise from rules like `E → E + E`.
>    When the parser has `E + E` on its stack and sees another `+`,
>    it must choose: reduce now (left-assoc) or shift and wait (right-assoc).
>    The `Precedence` map encodes this decision.

---

## Step 5: Multiplication and Priority

> **Goal:** Evaluate `2 + 3 * 4 → 14` (not 20).

Different operators need different **priority levels**. Multiplication should bind more tightly than addition.

```java
var MUL = new Terminal("*");
var pMul = new Production(E, List.of(E, MUL, E));

// Level 20 > Level 10: multiplication wins over addition
var precMul = new Precedence(20, Precedence.Associativity.LEFT);

var precedenceMap = Map.of(
    PLUS, precAdd, pPlus, precAdd,
    MUL,  precMul, pMul,  precMul
);

// Evaluator
  ...
  case "E : E * E" -> args.get(0) * args.get(2);
```

> 💡 **Insight:** Precedence numbers are relative, not absolute — only their ordering matters.
>    When the parser has `E + E` on its stack and sees `*`, it compares precedence levels.
>    Since `*` (20) > `+` (10), the parser **shifts** (reads more input) rather than reducing,
>    effectively giving `*` higher priority.

---

## Step 6: Exponentiation

> **Goal:** Evaluate `2 ^ 3 ^ 2` → 512.

Mathematically, exponentiation is **right-associative**: `2 ^ 3 ^ 2` = `2 ^ (3 ^ 2)` = `2 ^ 9` = 512,
*not* `(2 ^ 3) ^ 2` = 64.

```java
var POW = new Terminal("^");
var pPow = new Production(E, List.of(E, POW, E));
var precPow = new Precedence(30, Precedence.Associativity.RIGHT);

// Evaluator
  ...
  case "E : E ^ E" -> (int) Math.pow(args.get(0), args.get(2));
```

> 💡 **Insight:** With **RIGHT** associativity, when the parser sees `E ^ E` on its stack and a `^` lookahead,
>    it **shifts** instead of reducing — deferring the reduction and effectively grouping from the right.
>    This is the only operator in standard arithmetic that requires right-associativity.

---

## Step 7: The Dangling Else

> **Goal:** Evaluate `if 1 then 10 else 20 → 10`.

The classic "dangling else" problem: given `if A then if B then X else Y`, which `if` does the `else` belong to?

```java
var IF   = new Terminal("if");
var THEN = new Terminal("then");
var ELSE = new Terminal("else");

var pIf     = new Production(E, List.of(IF, E, THEN, E));           // if without else
var pIfElse = new Production(E, List.of(IF, E, THEN, E, ELSE, E));  // if with else

// ELSE gets higher precedence, forcing a Shift — binding to the nearest if
var precIf   = new Precedence(0,  Precedence.Associativity.RIGHT);
var precElse = new Precedence(40, Precedence.Associativity.RIGHT);

// Evaluator
  ...
  case "E : if E then E else E" -> args.get(1) != 0 ? args.get(3) : args.get(5);
```

> 💡 **Insight:** This is a **Shift/Reduce conflict** by design. When the parser sees `if E then E`
>    on the stack and an `else` as lookahead, should it reduce (`pIf`) or shift the `else`?
>    By giving `ELSE` a high precedence (40), we force a **shift**, which means the `else`
>    always binds to the **nearest** (innermost) `if`.
>    This matches standard language semantics (C, Java, etc.).

---

## Summary: Conflict Resolution Cheat Sheet

| Conflict Type | Cause | Fix |
|---|---|---|
| **Reduce/Reduce** | Two productions match the same input | Remove redundant production |
| **Shift/Reduce (assoc)** | Operator applied twice: `a + b + c` | Set LEFT or RIGHT associativity |
| **Shift/Reduce (prec)** | Two operators compete: `a + b * c` | Give higher-priority operator a larger precedence number |
| **Dangling Else** | `else` could bind to multiple `if`s | Give `else` the highest precedence to force a shift |
