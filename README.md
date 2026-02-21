# 🦥 Lazy LR

**Lazy LR** is a lightweight, high-performance Java library for creating LR(1) parsers.

Unlike traditional parser generators (like Yacc or ANTLR) that require a separate compilation step,
**Lazy LR** builds its states and lookahead sets on-the-fly, combining the power of LR(1) context-free grammars
with the agility of a modern library.

## Key Features

* **Lazy State Generation:**
  Parser states are computed only as they are encountered in the terminal stream,
  ensuring fast startup times even for complex grammars.
* **True LR(1) Power:**
  Handles any grammars that can be handled by LL(k) or LALR(1) parsers.
* **Declarative Precedence:**
  Resolve shift/reduce conflicts (like the "dangling else" or operator precedence) using a simple `Precedence` map
  rather than complex grammar restructuring.
* **Built for Modern Java:**
  Designed to work seamlessly with records, sealed types, and pattern matching (Java 25+).


## Getting Started

### Define your Lexer
The `Lexer` is created by defining a list of `Rule` objects consisting of a token name and a Regex pattern.

```java
Lexer lexer = Lexer.createLexer(List.of(
    new Rule("num", "[0-9]+"),
    new Rule("+", "\\+"),
    new Rule("*", "\\*")
));
```

In case a text can be recognized by several regexes
- the longest text is preferred
- if the same text is recognized by several rules, the first defined rule wins. 

### Define your Grammar

Construct your grammar using Terminal, NonTerminal, and Production objects.

```java
var expr = new NonTerminal("expr");
var num = new Terminal("num");
var plus = new Terminal("+");
var mul = new Terminal("*");

Grammar grammar = new Grammar(expr, List.of(
    new Production(expr, List.of(num)),
    new Production(expr, List.of(expr, plus, expr)),
    new Production(expr, List.of(expr, mul, expr))
));
```

### Handle Precedence and create the Parser

You may have noticed that the grammar above is ambiguous, the parser need to know
- for 2 + 3 * 4, should it be (2 + 3) * 4 or 2 + (3 * 4) ? 
- for 2 - 3 - 4, should it be (2 - 3) - 4 or 2 - (3 - 4) ? 

To avoid "expression ladders" in your grammar, you can define precedence
(which terminal is more important) and associativity (LEFT or RIGHT)
on terminals and/or productions.

```java
var precedence = Map.of(
    plus, new Precedence(10, Precedence.Associativity.LEFT),
    mul,  new Precedence(20, Precedence.Associativity.LEFT)
);

Parser parser = Parser.createParser(grammar, precedence);
```

### Transforming to an AST using an Evaluator

Lazy LR uses an Evaluator<T> to transform the parse tree into your desired result, usually an AST,
but you can also evaluate the productions directly.

Using Java Records makes for a concise AST:
```java
sealed interface Node {}
record NumLit(int value) implements Node {}
record BinaryOp(String op, Node left, Node right) implements Node {}
```

Implement the evaluate methods to map terminals and productions to your AST nodes.
Because Terminal carries the matched value, you can extract the raw text here:

```java
class NodeEvaluator implements Evaluator<Node> {
  @Override
  public Node evaluate(Terminal term) {
    return switch (term.name()) {
        case "num" -> new NumLit(Integer.parseInt(term.value()));
        default -> null;
    };
  }

  @Override
  public Node evaluate(Production prod, List<Node> args) {
    return switch (prod.name()) {
        case "expr : num" -> args.get(0);
        case "expr : expr + expr" -> new BinaryOp("+", args.get(0), args.get(2));
        case "expr : expr * expr" -> new BinaryOp("*", args.get(0), args.get(2));
        default -> throw new AssertionError("Unknown: " + prod.name());
    };
  }
}
```

### Bringing it all together

We can now tokenize the input, parse, and create the AST:

```java
Lexer lexer = Lexer.createLexer(...);
Parser parser = Parser.createParser(...);

String input = "2 + 3 * 4";

// Tokenize using token names
Iterator<Terminal> tokens = lexer.tokenize(input);

// Parse and create the AST
Node ast = parser.parse(tokens, new NodeEvaluator());

// Profit!
System.out.println(ast);
  // BinaryOp[op=+, left=NumLit[value=2], right=BinaryOp[op=*, left=NumLit[value=3], right=NumLit[value=4]]]
```




