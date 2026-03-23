# 🦥 Lazy LR

**Lazy LR** is a lightweight Java library for building LR(1) parsers at runtime.

Unlike traditional parser generators (like Yacc or ANTLR) that require a separate compilation step,
**Lazy LR** builds its states and lookahead sets on-the-fly, combining the power of LR(1) context-free grammars
with the agility of a modern library.

## Key Features

* **Lazy State Generation:**
  Parser states are computed only as they are encountered in the terminal stream,
  ensuring fast startup times even for complex grammars.
* **True LR(1) Power:**
  More powerful than LL(1) and even LALR(1) parsers, handles a strictly larger class of grammars.
* **Context-Sensitive Lexing:**
  When used by the parser, the lexer activates only the token patterns that are
  syntactically valid in the current state, removing ambiguities between tokens
  with the same value such as keywords and identifiers.
* **Declarative Precedence:**
  Resolve shift/reduce conflicts (like the "dangling else" or operator precedence) using a simple `Precedence` map
  rather than complex grammar restructuring.
* **Built for Modern Java:**
  Designed to work seamlessly with records, sealed types, and pattern matching (Java 25+).

## Tutorial

[GUIDE.md](GUIDE.md) walks you through building grammars from scratch in steps,
starting from a single-number parser and progressing through recursion,
operator precedence, associativity, the dangling-else problem, and unary operators with `%prec`.
The Runnable code is in
[GuideTest.java](src/test/java/com/github/forax/lazylr/GuideTest.java).

[The javadoc](https://jitpack.io/com/github/forax/lazylr/latest/javadoc/).

## Getting Started

### Define your Grammar

`MetaGrammar` lets you describe tokens, precedence, and productions in a compact textual format.

```java
var mg = MetaGrammar.load("""
    tokens {
      num: /[0-9]+/
      /[ ]+/
    }
    precedence {
      left:  '+', '-'
      left:  '*'
      right: UNARY
    }
    grammar {
      E : num
      E : E '+' E
      E : E '-' E
      E : E '*' E
      E : '-' E      %prec UNARY
    }
    """);
```

The DSL has three sections:

- **`tokens`**: named terminals (`name: /regex/`) matched by the lexer using longest-match,
  with declaration order breaking ties.
  Anonymous patterns (`/regex/`) are matched and silently discarded (e.g. whitespace or comments).
- **`precedence`**: operator associativity and priority; later lines have **higher** precedence than earlier ones.
  Multiple terminals can share the same precedence level by separating them with commas.
- **`grammar`**: BNF-style production rules; quoted literals like `'+'` are automatically registered
  as tokens and terminals.

Line comments (`// ...`) are also supported in grammar files.

You may have noticed that the grammar above is ambiguous.
The parser needs to know:
- for `2 + 3 * 4`, should it be `(2 + 3) * 4` or `2 + (3 * 4)`?
- for `2 + 3 + 4`, should it be `(2 + 3) + 4` or `2 + (3 + 4)`?

The `precedence` section resolves this: later lines have higher precedence (`'*'` binds more tightly than `'+'`),
and `left` associativity means `1 + 2 + 3` groups as `(1 + 2) + 3`.

By default, a production inherits the precedence of its rightmost terminal.
Sometimes this is wrong, for example, a unary minus shares the `-` terminal with binary subtraction,
but should bind more tightly than any binary operator.

Here `UNARY` is a virtual token (never emitted by the lexer) declared at a higher level than `*`.
The annotation `%prec UNARY` on `E: '-' E` makes the unary minus bind more tightly than multiplication,
so `- 3 * 4` correctly parses as `(-3) * 4`.

### Check if your grammar is correct

The class `LALRVerifier` can be used to check if a grammar is LALR(1) or not.

```java
LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), error -> {
    System.err.println("Conflict detected: " + error);
});
```

### Transforming to an AST using an Evaluator

Lazy LR uses an `Evaluator<T>` to transform the parse tree into your desired result,
usually an AST (Abstract Syntax Tree), but you can also evaluate productions directly.

Using Java Records makes for a concise AST:

```java
sealed interface Node {}
record NumLit(int value) implements Node {}
record UnaryOp(String op, Node node) implements Node {}
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
      case "E : num"   -> args.get(0);
      case "E : E + E" -> new BinaryOp("+", args.get(0), args.get(2));
      case "E : E - E" -> new BinaryOp("-", args.get(0), args.get(2));
      case "E : E * E" -> new BinaryOp("*", args.get(0), args.get(2));
      case "E : - E"   -> new UnaryOp("-", args.get(1));
      default -> throw new AssertionError("Unknown: " + prod.name());
    };
  }
}
```

### Bringing it all together

Tokenize the input, parse, and create the AST:

```java
Lexer lexer = Lexer.createLexer(mg.tokens());
Parser parser = Parser.createParser(mg.grammar(), mg.precedenceMap());

String input = "2 + - 3 * 4";

Iterator<Terminal> terminals = lexer.tokenize(input);
Node ast = parser.parse(terminals, new NodeEvaluator());

// Profit!
System.out.println(ast);
// BinaryOp[op=+, left=NumLit[value=2], right=BinaryOp[op=*, left=UnaryOp[op=-, node=NumLit[value=3]], right=NumLit[value=4]]]
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
    <version>7.2.1</version>
  </dependency>
</dependencies>
```


## Command-Line Tool

In addition to being used as a library, Lazy LR ships as a standalone CLI tool
for validating grammars, inspecting parse results, and generating code.

Download the latest release:
[Latest Release](https://github.com/forax/lazylr/releases/latest)

### Usage

```
java -jar lazylr.jar [--generate|--print] <grammar> [input]
```

### Modes

**Validate and show LALR automaton** (default)

```bash
java -jar lazylr.jar [--print] grammar.txt
```

Validates the grammar for LALR(1) conflicts and prints on stderr the LALR(1) automaton
if there is a conflict.
With `--print`, the automaton is printed unconditionally on stdout.

**Parse an input file and show the derivation tree**

```bash
java -jar lazylr.jar grammar.txt input.txt
```

Validates the grammar, parses the input file against it, and prints the derivation tree:

```
└── <expr>
    ├── <expr>
    │   └── [num=2]
    ├── [+]
    └── <expr>
        ...
```

**Generate Java source**

```bash
java -jar lazylr.jar --generate grammar.txt
```

Emits a Java code containing a static method `createGrammar()` that reconstructs
the grammar programmatically, useful for embedding a grammar without the DSL at runtime.
