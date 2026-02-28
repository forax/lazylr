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

### Define your Grammar

`MetaGrammar` lets you describe tokens, precedence, and productions in a compact textual format.

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
      expr : num
      expr : expr '+' expr
      expr : expr '*' expr
    }
    """);
```

The DSL has three sections:

- **`tokens`** — named terminals (`name: /regex/`) matched by the lexer in declaration order;
  anonymous patterns (`/regex/`) are matched and silently discarded (e.g. whitespace).
- **`precedence`** — operator associativity and priority; later lines have **higher** precedence than earlier ones.
- **`grammar`** — BNF-style production rules; quoted literals like `'+'` are automatically registered as tokens and terminals.

You may have noticed that the grammar above is ambiguous — the parser needs to know:
- for `2 + 3 * 4`, should it be `(2 + 3) * 4` or `2 + (3 * 4)`?
- for `2 + 3 + 4`, should it be `(2 + 3) + 4` or `2 + (3 + 4)`?

The `precedence` section resolves this: later lines have higher precedence (`'*'` binds more tightly than `'+'`),
and `left` associativity means `1 + 2 + 3` groups as `(1 + 2) + 3`.

### Check if your grammar is correct

The class `LALRVerifier` can be used to check if a grammar is LALR(1) or not.

```java
LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), error -> {
    System.err.println("Conflict detected: " + error);
});
```

### Transforming to an AST using an Evaluator

Lazy LR uses an `Evaluator<T>` to transform the parse tree into your desired result, usually an AST,
but you can also evaluate productions directly.

Using Java Records makes for a concise AST:

```java
sealed interface Node {}
record NumLit(int value) implements Node {}
record BinaryOp(String op, Node left, Node right) implements Node {}
```

Implement the evaluate methods to map terminals and productions to your AST nodes.
Because `Terminal` carries the matched value, you can extract the raw text here:

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

Tokenize the input, parse, and create the AST:

```java
Lexer lexer = Lexer.createLexer(mg.rules());
Parser parser = Parser.createParser(mg.grammar(), mg.precedenceMap());

String input = "2 + 3 * 4";

Iterator<Terminal> tokens = lexer.tokenize(input);
Node ast = parser.parse(tokens, new NodeEvaluator());

// Profit!
System.out.println(ast);
  // BinaryOp[op=+, left=NumLit[value=2], right=BinaryOp[op=*, left=NumLit[value=3], right=NumLit[value=4]]]
```

If you want to know more about how to design your grammar,
there is a step-by-step [GUIDE.md](GUIDE.md).

## Using with Maven

The binary distribution is available on the jitPack.io repository.

First, add jitpack.io as a repository in the POM file:

```xml
...
  <repositories>
      <repository>
          <id>jitpack.io</id>
          <url>https://jitpack.io</url>
      </repository>
  </repositories>
```

Then add Lazy LR as a dependency:

```xml
  <dependencies>
      ...
      <dependency>
          <groupId>com.github.forax</groupId>
          <artifactId>lazylr</artifactId>
          <version>1.1</version>
      </dependency>
  </dependencies>
```
