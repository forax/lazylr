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
The runnable code is in
[GuideTest.java](src/test/java/com/github/forax/lazylr/GuideTest.java).

[The javadoc](https://jitpack.io/com/github/forax/lazylr/latest/javadoc/).

## Getting Started

### Define your Grammar

`MetaGrammar` lets you describe tokens, precedence, and productions in a compact textual format.
```java
MetaGrammar mg = MetaGrammar.load("""
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
Sometimes this is wrong, for example, a unary minus shares the `-` terminal with binary subtraction
but should bind more tightly than any binary operator.

Here `UNARY` is a virtual token (never emitted by the lexer) declared at a higher level than `*`.
The directive `%prec UNARY` on `E: '-' E` overrides the production's default precedence,
so `- 3 * 4` correctly parses as `(-3) * 4`.

### Check if your Grammar is Correct

Call `verify()` to check whether the grammar is LALR(1). If there are unresolved conflicts,
they are described on stderr along with the full LALR automaton to help you diagnose them.
```java
mg.verify();
```

### Transforming to an AST using a Visitor

Lazy LR uses a `Visitor` to transform the parse tree into your desired result,
usually an AST (Abstract Syntax Tree), but you can also evaluate productions directly.

Using Java records makes for a concise AST:
```java
sealed interface Node {}
record NumLit(int value) implements Node {}
record UnaryOp(String op, Node node) implements Node {}
record BinaryOp(String op, Node left, Node right) implements Node {}
```

Implement one method per terminal that has a semantic value, and one method per production
to create your AST nodes. Because `Terminal` carries the matched value, you can extract
the raw text directly:
```java
class NodeVisitor implements Visitor<Node> {
  public Node num(Terminal term) {
    return new NumLit(Integer.parseInt(term.value()));
  }

  @ProductionName("E : E + E")
  public Node add(Node left, Node right) {
    return new BinaryOp("+", left, right);
  }
  @ProductionName("E : E - E")
  public Node sub(Node left, Node right) {
    return new BinaryOp("-", left, right);
  }
  @ProductionName("E : E * E")
  public Node mul(Node left, Node right) {
    return new BinaryOp("*", left, right);
  }
  @ProductionName("E : - E")
  public Node unary(Node node) {
    return new UnaryOp("-", node);
  }
}
```

Terminal methods are matched by name: a method named `num` is called whenever the `num` terminal
is shifted. If no method matches a terminal, `null` is returned for it.
Production methods are matched by the `@ProductionName` annotation.
As a convenience, if a production has exactly one symbol in its body and no `@ProductionName`
method is defined, its single argument is passed through automatically.

### Bringing it all Together

Parse a text and create the AST:
```java
String input = "2 + - 3 * 4";

Node ast = mg.parse(input, new NodeVisitor());

// Profit!
System.out.println(ast);
// BinaryOp[op=+, left=NumLit[value=2], right=BinaryOp[op=*, left=UnaryOp[op=-, node=NumLit[value=3]], right=NumLit[value=4]]]
```

For more detail on grammar design, see the step-by-step [GUIDE.md](GUIDE.md).


## Using with Maven

The binary distribution is available on the jitPack.io repository.

First, add jitpack.io as a repository in your POM file:
```xml
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
    <version>9.1.1</version>
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

**Validate grammar** (default)
```bash
java -jar lazylr.jar grammar.txt
```

Validates the grammar for LALR(1) conflicts. If conflicts are found, the LALR(1) automaton
is printed to stderr to help diagnose them.

**Print LALR automaton unconditionally**
```bash
java -jar lazylr.jar --print grammar.txt
```

Validates the grammar and always prints the full LALR(1) automaton to stdout,
whether or not conflicts are present.

**Parse an input file and show the derivation tree**
```bash
java -jar lazylr.jar grammar.txt input.txt
```

Validates the grammar, parses the input file against it, and prints the derivation tree:
```
└── <E>
    ├── <E>
    │   └── [num=1]
    ├── [+]
    └── <E>
        └── [num=2]
```

**Generate Java source**
```bash
java -jar lazylr.jar --generate grammar.txt
```

Emits Java source containing a static method `createGrammar()` that reconstructs
the grammar programmatically, useful for embedding a grammar without the DSL at runtime.
