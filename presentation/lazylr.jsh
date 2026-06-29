// To start, execute java -jar jvisualbook-*.jar on the command line
// jvisualbook is a notebook program that runs in the browser

// # 🦥 Lazy LR
// ` `

// Remi Forax

// JCrete, July 2026


// ## Setup

// Let's use the library LazyLR
/env --class-path ../target/lazylr-13.0.1.jar

// Then imports the classes
import com.github.forax.lazylr.*;


// ## Defining a Grammar
// A grammar is defined in the 'grammar' section

var mg = MetaGrammar.load("""
  grammar {
  }
  """);
IO.println(mg);


// ## Our first Grammar
// Let's try to recognize the JavaScript variable assignment

var mg = MetaGrammar.load("""
  grammar {
    start : 'let' 'a' '=' '3'
  }
  """);

IO.println(mg.grammar());


// ## Let's try to parse an input
// ` `

mg.parse("let a = 3");

// Oops, we have forgotten to define whitespaces as
// ignorable characters


// ## The 'tokens' section
// A token is a Java regex that matches a part of the input

// An ignorable token is a token that is not a terminal (no name)

var mg = MetaGrammar.load("""
  tokens {
    /[ ]+/
  }
  grammar {
    start : 'let' 'a' '=' '3'
  }
  """);

mg.parse("let a = 3");


// ## How to Recognize ids and numbers?
// Named tokens are terminals

var mg = MetaGrammar.load("""
  tokens {
    NUMBER: /[0-9]+/
    ID: /[a-z]+/
    /[ ]+/
  }
  grammar {
    start : 'let' ID '=' NUMBER
  }
  """);

mg.parse("let hello = 42");


// ## Context sensitive lexing
// Only tokens that are valid are activated

var mg = MetaGrammar.load("""
  tokens {
    IF: /if/
    ID: /[a-z]+/
    /[ ]+/
  }
  grammar {
    start : IF '(' ID ')' ID
  }
  """);

mg.parse("if (if) if");


// # What is 🦥 Lazy LR?
// ` `

// A library that **does not** generates parsers from grammars (unlike Bison, ANTLR, etc)

// A library that creates parsers that **lazily** evaluate an input using a grammar

// Enabling **fast feedback loop**


// # Guiding principles of Lazy LR
// ` `

// - **Lazy State Generation** (no upfront cost)
// - **Developer Velocity** (iterate fast)
// - **Developer Comforts** (precedence, diagnostics, coverage)
// - **Built for Modern Java** (type-checked, works with record and pattern matching)

// ## Why?
// Generating a parser is a secondary goal!

// Parsers of Java, Kotlin, Scala, C#, C++ (GCC/clang), Rust, Go are hand written (exceptions Python, Eclipse)

// The code is usually faster and has better error recovery

// The grammar is seen as the source of truth

// * Iterate fast when developing the grammar
// * Develop unit tests to check if the 'recursive descent' parser is aligned with the grammar

// ## Just for the demo
// Let's separate the tokens and the grammar using two strings
var TOKENS = """
  tokens {
    NUMBER: /[0-9]+/
    ID: /[a-z]+/
    /[ ]+/
  }
  """;
var mg = MetaGrammar.load(TOKENS + """
  grammar {
    start : 'let' ID '=' NUMBER
  }
  """);
mg.parse("let notfound = 404");


// ## Addition of numbers

var mg = MetaGrammar.load(TOKENS + """
  grammar {
    start : 'let' ID '=' expr
    expr : NUMBER '+' NUMBER
    expr : NUMBER
  }
  """);
mg.parse("let x = 40 + 2");

// Problem: can only adds two numbers!


// ## Use a left-recursive grammar

var mg = MetaGrammar.load(TOKENS + """
  grammar {
    start : 'let' ID '=' expr
    expr : expr '+' NUMBER
    expr : NUMBER
  }
  """);
mg.parse("let x = 40 + 1 + 1");


// ## Using two recursions?

var mg = MetaGrammar.load(TOKENS + """
  grammar {
    start : 'let' ID '=' expr
    expr : expr '+' expr
    expr : NUMBER
  }
  """);
mg.parse("let x = 40 + 1 + 1");

// Oops, we have a conflict


// ## Shift/Reduce conflict

// 40 + 1 + 1 can be parsed as (40 + 1) + 1 or 40 + (1 + 1)

// To resolve the conflict:
// * write the production with a left recursion (or a right recursion)
// * use a precedence rule to resolve the conflict (comfort feature)
//   * precedence: left => reduce, right => shift


// ## Fix the conflict using precedence
// We can say that '+' is left-associative

var mg = MetaGrammar.load(TOKENS + """
  precedence {
    left: '+'
  }
  grammar {
      start : 'let' ID '=' expr
      expr : expr '+' expr
      expr : NUMBER
    }
  """);
mg.parse("let x = 40 + 1 + 1");


// ## Precedence of a production?
// Inherits from the precedence of the rightmost terminal
// that have a precedence (can be overridden by %prec)

var mg = MetaGrammar.load(TOKENS + """
  precedence {
    left: '+'
  }
  grammar {
      start : 'let' ID '=' expr
      expr : expr '+' expr       %prec '+'
      expr : NUMBER
    }
  """);
mg.parse("let x = 40 + 1 + 1");


// ## Multiplication of numbers

mg = MetaGrammar.load(TOKENS + """
  precedence {
    left: '+'
  }
  grammar {
      start : 'let' ID '=' expr
      expr : expr '+' expr
      expr : expr '*' expr
      expr : NUMBER
    }
  """);
mg.parse("let x = 40 + 1 * 2");


// ## Fixing the conflict
// We can say that '*' is more important than '+'

mg = MetaGrammar.load(TOKENS + """
  precedence {
    left: '+'    // lower priority
    left: '*'    // higher priority
  }
  grammar {
      start : 'let' ID '=' expr
      expr : expr '+' expr
      expr : expr '*' expr
      expr : NUMBER
    }
  """);
mg.parse("let x = 40 + 1 * 2");


// ## Offline Grammar verification
// The full grammar can be verified (LALR) using the `verify()` method

mg = MetaGrammar.load(TOKENS + """
  precedence {
    //left: '+'
  }
  grammar {
      start : 'let' ID '=' expr
      expr : expr '+' expr
      expr : NUMBER
    }
  """);
mg.verify();

// ## Grammar verification
// `verify()` has also a less verbose version

mg = MetaGrammar.load(TOKENS + """
  precedence {
    left: '+'
    left: '*'
  }
  grammar {
      start : 'let' ID '=' expr
      expr : expr '+' expr
      expr : expr '*' expr
      expr : NUMBER
    }
  """);
mg.verify(IO::println);


// # How LazyLR works?
// The implementation is split into two parts:
// * a `Lexer` that transform the input into an iterator of terminals using the regexes

var lexer = Lexer.createLexer(mg.tokens());
var terminals = lexer.tokenize("let x = 40 + 1 * 2");

// * a `Parser` that scan the terminals and generate events

var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());
parser.parse(terminals, new ParserListener() {
  public void onShift(Terminal terminal) { }
  public void onReduce(Production production) { }
});

// ## The Lexer
// Splits the input into `Terminal` using the regexes

var lexer = Lexer.createLexer(mg.tokens());
var terminals = lexer.tokenize("let x = 40 + 2");
terminals.forEachRemaining(t -> {
  IO.println("[" + t.name() + "] " + t.value());
});

// ## The Parser

var terminals = Lexer.createLexer(mg.tokens()).tokenize("let x = 40");
var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());
parser.parse(terminals, new ParserListener() {
  public void onShift(Terminal terminal) { IO.println("shift " + terminal); }
  public void onReduce(Production production) { IO.println("  reduce " + production); }
});

// Emit a **shift** when a terminal is recognized

// Emit a **reduce** when a production is recognized (bottom-up)


// ## Events can be seen as a virtual tree

var terminals = Lexer.createLexer(mg.tokens()).tokenize("let x = 40 + 2");
var parser = Parser.createParser(mg.grammar(), mg.precedenceMap());
parser.parse(terminals, new ParserListener() {
  public void onShift(Terminal terminal) { IO.println("shift " + terminal); }
  public void onReduce(Production production) { IO.println("  reduce " + production); }
});


// ## The virtual tree?
// The events are the leafs and nodes of a tree from bottom to top

// ```
// Input:  "let x = 40 + 2"
//          │
//          ▼
// ┌─────────────────────┐
// │        Lexer        │
// └─────────────────────┘
//          │
//          │  stream of terminals
//          │  let  ID("x")  =  NUMBER("40")  +  NUMBER("2")
//          ▼
// ┌─────────────────────┐
// │       Parser        │
// └─────────────────────┘
//          │
//          │  events (left-to-right, bottom-up)
//          ▼
//   shift 'let'
//   shift ID('x')
//   shift '='
//   shift NUMBER(40)                    ← leaf value: "40"
//     reduce [expr : NUMBER]            ← Integer.parseInt()  →  40
//   shift '+'
//   shift NUMBER(2)                     ← leaf value: "2"
//     reduce [expr : NUMBER]            ← Integer.parseInt()  →  2
//     reduce [expr : expr + expr]       ← add(40, 2)  →  42
//     reduce [start : let ID = expr]    ← start(42)  →  42
//          │
//          ▼
//       result: 42
//         <=>
// Virtual tree (what the events encode):
//   start
//   └── expr  (42)
//       ├── expr  (40)
//       │   └── NUMBER("40")
//       ├── '+'
//       └── expr  (2)
//           └── NUMBER("2")
// ```


// ## Evaluation
// A `Visitor` can be used to evaluate the grammar (propagate values along the tree)

var visitor = new Visitor<Integer>() {
  public int NUMBER(Terminal terminal) {
    return Integer.parseInt(terminal.value());
  }
};
mg.parse("let x = 42", visitor);

// Oops: we forget the evaluation of start


// ## Evaluation of the expressions
// Like the grammar, the visitor can be built iteratively

// `@ProductionName` is used to specify the name of the production,
// 'let', ID and '=' have no value

var visitor = new Visitor<Integer>() {
  public int NUMBER(Terminal t) { return Integer.parseInt(t.value()); }
  @ProductionName("start : let ID = expr")
  public int start(int param0) {
    IO.println("start " + param0);
    return param0;
  }
};
mg.parse("let x = 42", visitor);

// How to get the value of ID?


// ## We need a terminal method 'ID'

var visitor = new Visitor<Integer>() {
  public int NUMBER(Terminal t) { return Integer.parseInt(t.value()); }
  public String ID(Terminal terminal) { return terminal.value(); }
  @ProductionName("start : let ID = expr")
  public int start(String id, int value) {
    IO.println("start " + id + " " + value);
    return value;
  }
};
mg.parse("let x = 42", visitor);


// ## parse() returns a result

var visitor = new Visitor<Integer>() {
  public int NUMBER(Terminal terminal) { return Integer.parseInt(terminal.value()); }
  @ProductionName("start : let ID = expr")
  public int start(int value) {
    return value;
  }
};
IO.println(mg.parse("let x = 42", visitor));

// How to add the addition?


// ## With the addition

var visitor = new Visitor<Integer>() {
  public int NUMBER(Terminal terminal) { return Integer.parseInt(terminal.value()); }
  @ProductionName("start : let ID = expr")
  public int start(int value) {
    return value;
  }
  @ProductionName("expr : expr + expr")
  public int add(int left, int right) {
    return left + right;
  }
};
IO.println(mg.parse("let x = 40 + 2", visitor));


// ## Create your own tree (AST)
// We use a sealed interface + records

sealed interface Expr {
  record Value(int value) implements Expr {}
  record Add(Expr left, Expr right) implements Expr {}
}

var visitor = new Visitor<Expr>() {
  public int NUMBER(Terminal terminal) { return Integer.parseInt(terminal.value()); }
  @ProductionName("start : let ID = expr")
  public Expr start(Expr expr) { return expr; }
  @ProductionName("expr : NUMBER")
  public Expr expr(int value) { return new Expr.Value(value); }
  @ProductionName("expr : expr + expr")
  public Expr add(Expr left, Expr right) { return new Expr.Add(left, right); }
};
IO.println(mg.parse("let x = 40 + 2", visitor));


// # Future?
// ` `

// * The project is new and not yet mature.
//   I need feedbacks to improve it

// * Lexing is not fast (`java.util.regex` is slow).
//   Maybe, use a faster regex engine (pluggable?)

// * Feature Envy: lazier, support incremental parsing, have a generic parse tree,
//   IntelliJ/LSP plugin for the grammar, LALR runtime option, support non-assoc,
//   coverage to use JaCoCo format

// # 🦥 Lazy LR
// [https://github.com/forax/lazylr](https://github.com/forax/lazylr)

// - Lazy State Generation
// - Developer Velocity
// - Developer Comforts
// - Built for Modern Java
