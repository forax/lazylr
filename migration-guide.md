# Migration Guide

This document collects migration guides for developers moving an existing parser to LazyLR.
Each guide is self-contained and covers lexer rules, grammar syntax, semantic actions,
error handling, and a step-by-step checklist.

If you are new to LazyLR, reading the [Reference Manual](reference-manual.md) first
will make this guide easier to follow.

---

## Table of Contents

1. [Lex+Yacc/Flex+Bison-to-LazyLR Migration Guide](#yacc-to-lazylr-migration-guide)
2. [ANTLR-to-LazyLR Migration Guide](#antlr-to-lazylr-migration-guide)
3. [Lark-to-LazyLR Migration Guide](#lark-to-lazylr-migration-guide)

---

## Yacc-to-LazyLR Migration Guide

Lex/Yacc (and its GNU successor Flex/Bison) is a C-based toolchain:: `lex` generates a C lexer from regex rules,
`yacc`/`bison` generates a C LALR(1) parser from a grammar file, both are compiled into your program. 
- 
LazyLR is a Java runtime library: the grammar is a string loaded at runtime.
No code generation or compilation step is involved.

The underlying parsing model is very similar, both are LALR(1)/LR-family parsers,
so the grammar structure migrates almost one-for-one.
The main work is replacing C semantic actions and the lex/yacc file format with Java `Visitor<V>` methods.

---

### 1) The fundamental similarity: both are LR parsers

This is the key insight that makes this migration tractable. Yacc and LazyLR share the
same bottom-up shift/reduce parsing strategy. Concepts you already know carry over directly:

| Yacc/Bison concept                  | LazyLR equivalent                         |
|-------------------------------------|-------------------------------------------|
| `%token`                            | `tokens` section                          |
| `%left`, `%right`                   | `left:`, `right:` in `precedence` section |
| `%prec TOKEN`                       | `%prec TOKEN` (identical syntax)          |
| Grammar rule `A : B C`              | Production `A : B C` (identical syntax)   |
| Semantic action `{ $$ = $1 + $3; }` | `Visitor<V>` production methods           |           |  
| `yylex()` / lex rules               | `Lexer.tokenize()` / `tokens` section     |
| `yyerror()`                         | `catch (ParsingException e)`              |

---

### 2) Lexer rules (lex → `tokens` section)

A lex file has three sections separated by `%%`: definitions, rules, and user code.
Only the rules section migrates to LazyLR.

Lex:
```c
%%
[0-9]+          { yylval.ival = atoi(yytext); return INT; }
[a-zA-Z_]\w*    { return ID; }
"+"             { return PLUS; }
"*"             { return STAR; }
[ \t\r\n]+      { /* skip */ }
.               { yyerror("unexpected character"); }
%%
```

LazyLR:
```text
tokens {
  INT: /[0-9]+/
  ID:  /[a-zA-Z_]\w*/
  /[ \t\r\n]+/
}
```

Migration notes:
- Lex rules embed C actions (`{ return TOKEN; }`). 
  In LazyLR there are no embedded actions; the token name is declared once,
  and its matched text is available via `Terminal.value()` in visitor methods.
- `yylval` assignments (e.g., `yylval.ival = atoi(yytext)`) move into the visitor's
  terminal method:
  ```java
  public int INT(Terminal t) { return Integer.parseInt(t.value()); }
  ```
- The catch-all `.` rule that calls `yyerror` is handled automatically:
  LazyLR emits `Terminal.ERROR` and throws `ParsingException` when no pattern matches.
- Single-character operator tokens like `+`, `*`, `(`, `)` do **not** need explicit
  `tokens` entries. Quote them directly in the `grammar` section (`'+'`, `'*'`) and
  LazyLR registers them automatically, matching them before named tokens.

---

### 3) Grammar rules (yacc → `grammar` section)

The yacc grammar section maps almost directly to LazyLR's `grammar` section.
The syntax is nearly identical; the main difference is that semantic actions
(`{ ... }`) are removed and handled separately by a visitor.

Yacc:
```c
%%
expr
    : expr '+' term   { $$ = $1 + $3; }
    | term            { $$ = $1; }
    ;

term
    : term '*' factor { $$ = $1 * $3; }
    | factor          { $$ = $1; }
    ;

factor
    : INT             { $$ = $1; }
    | '(' expr ')'    { $$ = $2; }
    ;
%%
```

LazyLR:
```text
grammar {
  Expr   : Expr '+' Term
  Expr   : Term
  Term   : Term '*' Factor
  Term   : Factor
  Factor : INT
  Factor : '(' Expr ')'
}
```

The start symbol is the head of the first production (`Expr` here), exactly as in yacc
where the first rule is the start symbol.

#### Epsilon productions

Yacc:
```c
opt_else
    : /* empty */
    | ELSE stmt
    ;
```

LazyLR:
```text
grammar {
  OptElse :    // empty
  OptElse : 'else' Stmt
}
```

An empty right-hand side (a blank line after `:`) declares a nullable production,
identical in concept to yacc.

---

### 4) Declarations (`%token`, `%left`, `%right`, `%prec`)

Yacc declarations in the `%{ ... %}` prologue and `%token`/`%left`/`%right` lines map
directly to the LazyLR `tokens` and `precedence` sections.

Yacc:
```c
%token INT ID
%token IF ELSE WHILE RETURN

%left  '+' '-'
%left  '*' '/'
%right UMINUS
```

LazyLR:
```text
tokens {
  INT:    /[0-9]+/
  ID:     /[a-zA-Z_]\w*/
  /[ \t\n]+/
}
precedence {
  left:  '+', '-'
  left:  '*', '/'
  right: UMINUS      // virtual token, never emitted by the lexer
}
```

Migration notes:
- `%token` just declares a name in yacc. In LazyLR, tokens are declared with their
  regex in the `tokens` section. Keywords like `IF`, `ELSE` can be quoted directly in
  the grammar (`'if'`, `'else'`) without a `tokens` entry.
- `%left` and `%right` map to `left:` and `right:` lines.
  Later lines = higher  precedence in LazyLR, the same ordering convention as yacc.
- `%nonassoc` (yacc) has no direct LazyLR equivalent. Restructure the grammar to reject it syntactically
   or reject it semantically on the AST.
- `%prec TOKEN` in a grammar rule is identical syntax in LazyLR.

#### `%prec` example — unary minus

Yacc:
```c
%right UMINUS
%%
expr : expr '+' expr
     | expr '-' expr
     | expr '*' expr
     | '-' expr  %prec UMINUS
     | INT
     ;
```

LazyLR:
```text
precedence {
  left:  '+', '-'
  left:  '*'
  right: UMINUS
}
grammar {
  Expr : Expr '+' Expr
  Expr : Expr '-' Expr
  Expr : Expr '*' Expr
  Expr : '-' Expr    %prec UMINUS
  Expr : INT
}
```

---

### 5) Semantic actions (`$$`, `$1`, `$2`, ... → `Visitor<V>`)

Yacc embeds C code directly inside grammar rules using `$$` (result) and `$1`, `$2`, ... (positional arguments).
LazyLR separates the grammar from the semantic actions entirely, placing them in a `Visitor` class.

The mapping is direct:

| Yacc                  | LazyLR `Visitor<V>`                          |
|-----------------------|----------------------------------------------|
| `$1`, `$3`, ...       | First, third, ... typed method parameters    |
| `$$`                  | The method's return value                    |
| `$2` for a terminal   | Filtered out unless a terminal method exists |
| `yylval.ival`         | `int INT(Terminal t)` terminal method        |
| Mid-rule actions      | Not supported; restructure into sub-rules    |

Yacc:
```c
expr
    : expr '+' term   { $$ = $1 + $3; }
    | expr '-' term   { $$ = $1 - $3; }
    | term            { $$ = $1; }
    ;

term
    : term '*' factor { $$ = $1 * $3; }
    | factor          { $$ = $1; }
    ;

factor
    : INT             { $$ = $1; }
    | '(' expr ')'    { $$ = $2; }
    ;
```

LazyLR visitor:
```java
class CalcVisitor implements Visitor<Integer> {

    // Terminal method: replaces yylval assignment in lex
    public int INT(Terminal t) {
        return Integer.parseInt(t.value());
    }

    // Production methods: replace { $$ = ... } actions in yacc
    @ProductionName("Expr : Expr + Term")
    public int add(int left, int right) { return left + right; }

    @ProductionName("Expr : Expr - Term")
    public int sub(int left, int right) { return left - right; }

    @ProductionName("Term : Term * Factor")
    public int mul(int left, int right) { return left * right; }

    // "Expr : Term", "Term : Factor", "Factor : INT" are single-body pass-throughs.
    // No method needed — the child value is forwarded automatically.

    @ProductionName("Factor : ( Expr )")
    public int paren(int inner) { return inner; }
    // Note: '(' and ')' have no terminal method, so they are filtered from parameters.
}
```

Key points:
- **Single-body pass-through**: productions like `Expr : Term` or `Factor : INT` with no
  `@ProductionName` method automatically forward the single child value. The yacc
  `{ $$ = $1; }` action is implicit and requires no code.
- **Terminal filtering**: terminals without a matching terminal method (e.g., `'+'`, `'('`)
  do not appear as parameters in production methods. `$2` in yacc's `{ $$ = $2; }` for
  `'(' expr ')'` becomes just `inner`, the sole parameter.

#### Mid-rule actions

Yacc supports mid-rule actions, C code embedded partway through a production:

```c
stmt : IF expr { setup(); } stmt  { $$ = makeIf($2, $4); }
```

LazyLR does not support mid-rule actions. Refactor by introducing a helper non-terminal:

```text
grammar {
  Stmt     : 'if' Expr IfBody
  IfBody   : Stmt
}
```

Then put any setup logic at the start of the `IfBody` production method.

---

### 6) Running the parser

Yacc:
```c
int main() {
    yyparse();   // calls yylex() internally
    return 0;
}
```

LazyLR:
```java
static final MetaGrammar MG = MetaGrammar.load(GRAMMAR_TEXT);

static void main() {
    int result = MG.parse("1 + 2 * 3", new CalcVisitor());
    IO.println(result);  // 7
}
```

---

### 7) Error handling

Yacc/lex surface errors through `yyerror()` callbacks and special `error` token rules
for panic-mode recovery. LazyLR throws `ParsingException` for all failures, with a
detailed message already formatted for display.

| Yacc/lex mechanism          | LazyLR equivalent                                       |
|-----------------------------|---------------------------------------------------------|
| `yyerror(const char* msg)`  | `catch (ParsingException e)` at the `parse()` call site |
| `error` token rule          | Not supported; catch and re-parse from the next unit    |
| Panic-mode recovery         | Not built-in; restart the parser on a fresh input unit  |
| Lex `{ yyerror(...); }`     | Automatic: `ParsingException` with "Lexing error"       |

After a `ParsingException`, the `Parser` instance is clean and can be reused for the
next input.

---

### 8) Grammar verification

Yacc reports shift/reduce and reduce/reduce conflicts as warnings during code generation.
LazyLR provides an explicit `verify()` call you run during development and in your test suite.

```java
// In a JUnit test — run this whenever the grammar changes:
var errors = new ArrayList<String>();
MG.verify(errors::add);
assertTrue(errors.isEmpty());
```

The output of `mg.verify(true)` or `lazylr --print grammar.txt` mirrors the yacc conflict report,
with 🔥 marking unresolved conflicts and 🚫 marking actions suppressed by precedence resolution.

---

## ANTLR-to-LazyLR Migration Guide

ANTLR 4 is a top-down LL(*) parser generator: it runs a code generation and produces
a Java parser you compile into your project.
LazyLR is a bottom-up LR(1) runtime library: grammars are strings loaded at runtime,
no code generation is required.

---

### 1) LL(*) vs LR(1)

ANTLR's LL(*) strategy works **top-down**: starting from the goal rule, it predicts
which alternative to expand by looking ahead as far as needed. This makes alternatives
and EBNF operators (`*`, `+`, `?`) feel natural, but it cannot handle left-recursive
rules without ANTLR's special rewriting (which it does silently in ANTLR 4).

LazyLR's LR(1) strategy works **bottom-up**: it reads tokens left to right, accumulating
them on a stack, and folds them into non-terminals when a complete right-hand side is
recognized. Left recursion is handled *naturally*, no rewriting needed.
Ambiguous grammars require explicit `precedence` declarations rather than implicit
alternative ordering.

The practical consequence for migration:
- Left-recursive rules migrate unchanged (or become simpler).
- EBNF shorthands (`*`, `+`, `?`) must be desugared into explicit productions.
- Alternative ordering as a precedence mechanism must be replaced with a `precedence` section.

---

### 2) Lexer rules

ANTLR lexer rules are uppercase identifiers with regex-like bodies.
LazyLR uses the `tokens` section with Java `java.util.regex.Pattern` syntax.

ANTLR:
```antlr
lexer grammar ExprLexer;

ID     : [a-zA-Z_][a-zA-Z_0-9]* ;
INT    : [0-9]+ ;
FLOAT  : [0-9]+ '.' [0-9]* ;
WS     : [ \t\r\n]+  -> skip ;
SEMI   : ';' ;
```

LazyLR:
```text
tokens {
  ID:    /[a-zA-Z_][a-zA-Z_0-9]*/
  INT:   /[0-9]+/
  FLOAT: /[0-9]+\.[0-9]*/
  /[ \t\r\n]+/
}
```

Migration notes:
- `-> skip` becomes an **unnamed token** (a regex with no `name:` prefix). The matched
  text is silently discarded; no `Terminal` is emitted.
- `-> channel(HIDDEN)` has no direct equivalent. Tokens only used for tooling
  (e.g., whitespace passed to a comment channel) should become unnamed tokens.
- ANTLR uses lexer modes for context-sensitive tokenization. LazyLR achieves a similar
  effect through **context-sensitive lexing**: only token patterns expected by the current
  parser state are tried. Restructuring the grammar to encode the context is usually
  enough to replace simple mode usage.
- Quoted literals used directly inside parser rules (e.g., `'+'`, `'if'`) are
  **automatically registered as tokens** in LazyLR, before named tokens. Keyword
  terminals (`if`, `else`, `while`) do not need explicit lexer rules if you quote them
  in the grammar; the promotion is automatic.

---

### 3) Parser rules

ANTLR parser rules are lowercase identifiers. Each rule can have multiple alternatives
separated by `|`, with optional EBNF operators and inline labels.

#### 3a) Simple rules

ANTLR:
```antlr
stmt
    : expr ';'
    | 'return' expr ';'
    | 'if' '(' expr ')' stmt
    | 'if' '(' expr ')' stmt 'else' stmt
    ;
```

LazyLR:
```text
grammar {
  Stmt : Expr ';'
  Stmt : 'return' Expr ';'
  Stmt : 'if' '(' Expr ')' Stmt
  Stmt : 'if' '(' Expr ')' Stmt 'else' Stmt
}
```

Each ANTLR alternative becomes its own production line in LazyLR.

#### 3b) EBNF operators

ANTLR supports `?` (optional), `*` (zero or more), and `+` (one or more).
LazyLR has no EBNF shorthands; desugar each one into explicit productions.

| ANTLR       | LazyLR equivalent                                          |
|-------------|------------------------------------------------------------|
| `rule?`     | Two productions: one with `rule`, one epsilon (`Head :`)   |
| `rule*`     | `List :` (epsilon) and `List : List rule` (left-recursive) |
| `rule+`     | `List : rule` and `List : List rule` (left-recursive)      |

`rule?`: optional else clause:

ANTLR:
```antlr
ifStmt : 'if' '(' expr ')' stmt ('else' stmt)? ;
```

LazyLR:
```text
grammar {
  IfStmt  : 'if' '(' Expr ')' Stmt OptElse
  OptElse : 'else' Stmt
  OptElse :                    // ε — no else branch
}
```

`rule*`: zero or more arguments:

ANTLR:
```antlr
call : ID '(' (expr (',' expr)*)? ')' ;
```

LazyLR:
```text
grammar {
  Call    : ID '(' ArgList ')'
  ArgList :                        // ε — zero arguments
  ArgList : Expr                   // first argument
  ArgList : ArgList ',' Expr       // subsequent arguments
}
```

`rule+`: one or more statements:

ANTLR:
```antlr
block : '{' stmt+ '}' ;
```

LazyLR:
```text
grammar {
  Block   : '{' StmtList '}'
  StmtList : Stmt
  StmtList : StmtList Stmt
}
```

#### 3c) Rule labels and aliases

ANTLR uses `# Label` alternatives or `=` field assignments to name parse tree nodes.
LazyLR identifies productions by their full text, matched by `@ProductionName` in visitors.

ANTLR:
```antlr
expr
    : expr '*' expr   # MulExpr
    | expr '+' expr   # AddExpr
    | INT             # IntExpr
    ;
```

LazyLR grammar:
```text
grammar {
  Expr : Expr '*' Expr
  Expr : Expr '+' Expr
  Expr : INT
}
```

---

### 4) Operator precedence

In ANTLR 4, precedence is encoded by the **order of alternatives** within a single rule:
alternatives listed first have higher precedence. This is implicit.

In LazyLR, precedence is explicit via the `precedence` section: later lines have higher
precedence. `left` and `right` control associativity.

ANTLR:
```antlr
expr
    : <assoc=right> expr '^' expr  // highest precedence, right-associative
    | '-' expr                     // unary minus
    | expr ('*'|'/') expr
    | expr ('+'|'-') expr          // lowest precedence, left-associative
    | INT
    | '(' expr ')'
    ;
```

LazyLR:
```text
precedence {
  left:  '+', '-'      // lowest precedence
  left:  '*', '/'
  right: '^' 
  right: UMINUS        // highest precedence (virtual token)
}
grammar {
  Expr : Expr '+' Expr
  Expr : Expr '-' Expr
  Expr : Expr '*' Expr
  Expr : Expr '/' Expr
  Expr : Expr '^' Expr
  Expr : '-' Expr        %prec UMINUS
  Expr : INT
  Expr : '(' Expr ')'
}
```

Notes:
- ANTLR uses `<assoc=right>` to mark right-associative alternatives.
  Use `right:` in the LazyLR `precedence` section.
- Unary operators that share a terminal with a binary operator (e.g., `-`) need a
  `%prec VIRTUAL_TOKEN` directive, where the virtual token is declared in `precedence`
  but never emitted by the lexer.
- After writing the grammar, run `mg.verify()` to confirm all conflicts are resolved.

---

### 5) Semantic actions and visitors

ANTLR generates a typed `Visitor` interface and a `ParseTree` hierarchy from the grammar.
LazyLR provides a reflection-backed `Visitor<V>` that you implement directly, with no
generated code.

| ANTLR concept                        | LazyLR equivalent                                         |
|--------------------------------------|-----------------------------------------------------------|
| Generated `ExprVisitor` interface    | `Visitor<V>` (you write the class; no generation)         |
| `visitMulExpr(MulExprContext ctx)`   | `@ProductionName("Expr : Expr * Expr") mul(V a, V b)`     |
| `ctx.expr(0)`, `ctx.expr(1)`         | Typed method parameters, left to right                    |
| `ctx.INT()` / `ctx.INT().getText()`  | Terminal method: `public V INT(Terminal t)` → `t.value()` |
| `visitChildren(ctx)`                 | Single-body pass-through (automatic, no code needed)      |
| `ParseTreeListener.enterXxx/exitXxx` | `ParserListener.onShift` / `onReduce`                     |

ANTLR visitor:
```java
class EvalVisitor extends ExprBaseVisitor<Integer> {
  @Override
  public Integer visitMulExpr(ExprParser.MulExprContext ctx) {
    return visit(ctx.expr(0)) * visit(ctx.expr(1));
  }

  @Override
  public Integer visitAddExpr(ExprParser.AddExprContext ctx) {
    return visit(ctx.expr(0)) + visit(ctx.expr(1));
  }

  @Override
  public Integer visitIntExpr(ExprParser.IntExprContext ctx) {
    return Integer.parseInt(ctx.INT().getText());
  }
}
```

LazyLR visitor:
```java
class EvalVisitor implements Visitor<Integer> {
  public int INT(Terminal t) {
    return Integer.parseInt(t.value());
  }

  @ProductionName("Expr : Expr * Expr")
  public int mulExpr(int left, int right) {
    return left * right;
  }

  @ProductionName("Expr : Expr + Expr")
  public int addExpr(int left, int right) {
    return left + right;
  }
}
```

Key differences:
- There is no base class to extend. Implement `Visitor<V>` directly.
- Production method parameters are **already-evaluated child values**, not context objects.
  No recursive `visit(...)` calls are needed.
  The bottom-up evaluation order means children are always evaluated before their parent.
- Terminals without a matching terminal method are **filtered out** of production
  parameters. There is no parameter for `'+'`, `'*'`, `'('`, `')'`, etc., unless you
  explicitly write a terminal method for them.
- Primitive types (`int`, `long`, `boolean`) are accepted and handled via boxing/unboxing.

#### Building an AST

Define your node types as a `sealed interface` with `record` implementations, then return
them from visitor methods:

```java
sealed interface Expr {}
record Num(int value) implements Expr {}
record BinOp(String op, Expr left, Expr right) implements Expr {}
record Neg(Expr operand) implements Expr {}

class AstVisitor implements Visitor<Expr> {
  public Expr INT(Terminal t) {
    return new Num(Integer.parseInt(t.value()));
  }

  @ProductionName("Expr : Expr + Expr")
  public Expr add(Expr l, Expr r) { return new BinOp("+", l, r); }

  @ProductionName("Expr : Expr * Expr")
  public Expr mul(Expr l, Expr r) { return new BinOp("*", l, r); }

  @ProductionName("Expr : - Expr")
  public Expr neg(Expr e) { return new Neg(e); }
}
```

---

### 6) Running the parser

ANTLR requires wiring a `CharStream`, `Lexer`, `CommonTokenStream`, and `Parser` together.
LazyLR exposes a single call through `MetaGrammar`.

ANTLR:
```java
CharStream input = CharStreams.fromString("1 + 2 * 3");
ExprLexer lexer = new ExprLexer(input);
CommonTokenStream tokens = new CommonTokenStream(lexer);
ExprParser parser = new ExprParser(tokens);
ParseTree tree = parser.expr();
int result = new EvalVisitor().visit(tree);
```

LazyLR:
```java
// grammar and visitor defined once, reused freely
static final MetaGrammar MG = MetaGrammar.load(GRAMMAR_TEXT);

int result = MG.parse("1 + 2 * 3", new EvalVisitor());
```

---

### 7) Error handling

ANTLR uses a listener-based error reporting mechanism with separate exception types for
lexer and parser errors. LazyLR throws a single `ParsingException` for all failures.

| ANTLR mechanism                       | LazyLR equivalent                                       |
|---------------------------------------|---------------------------------------------------------|
| `ANTLRErrorListener.syntaxError(...)` | Catch `ParsingException` at the `parse()` call site     |
| `RecognitionException`                | `ParsingException.getMessage()` (includes line, column) |
| `InputMismatchException`              | `ParsingException` — "unexpected terminal, expected ..."  |
| `NoViableAltException`                | `ParsingException` — no valid action in current state   |
| `LexerNoViableAltException`           | `ParsingException` with "Lexing error" prefix           |
| `reportAmbiguity(...)`                | `mg.verify(errors::add)` — resolve statically, not at runtime |

After a `ParsingException`, the `Parser` instance is in a clean state and can be
reused for the next input.

---

### 8) Grammar verification (replacing `reportAmbiguity`)

ANTLR reports ambiguities at runtime during a parse.
LazyLR provides a static verification step that checks for LALR(1) conflicts before any input is processed.

```java
// In a @BeforeAll or @Test in your test suite:
var errors = new ArrayList<String>();
MG.verify(errors::add);
assertTrue("Grammar has conflicts: " + errors, errors.isEmpty());
```

Run `mg.verify()` or `lazylr --print grammar.txt` whenever you change the grammar.
The output marks unresolved conflicts with 🔥 and resolved-but-suppressed actions with 🚫,
making it easy to audit precedence decisions.

---

## Lark-to-LazyLR Migration Guide

Lark is a Python parsing library that supports both Earley and LALR(1) parsing strategies,
with a PEG-inspired grammar syntax. LazyLR is a Java runtime LR(1) parser library.
This guide covers the most common migration patterns.

---

### 1) Grammar syntax mapping

Lark uses a grammar format with EBNF conveniences.
LazyLR uses a yacc-style format with explicit productions.

Lark:
```python
grammarText = r"""
    start: expr+
    
    expr:  expr "+" term   -> add
         | term

    term:  term "*" atom   -> mul
         | atom

    atom:  NUMBER
         | "(" expr ")"

    NUMBER: /[0-9]+/
    %ignore /\s+/
  """
```

LazyLR:
```java
var grammarText = """
  tokens {
    NUMBER: /[0-9]+/
    /\s+/
  }
  precedence {
    left: '+'
    left: '*'
  }
  grammar {
    Start  : Expr
    Expr   : Expr '+' Term
    Expr   : Term
    Term   : Term '*' Atom
    Term   : Atom
    Atom   : NUMBER
    Atom   : '(' Expr ')'
  }
  """;
```

Migration notes:
- Lark rule names are lowercase by convention; LazyLR has no casing constraint for non-terminals.
- Lark's `start` rule is implicit. In LazyLR, the **first production** in the `grammar` section
  defines the start symbol.
- Lark's `-> alias` labels (e.g., `-> add`) correspond to `@ProductionName` annotations in
  a LazyLR `Visitor<V>`.
- Lark's `%ignore` maps to unnamed token rules (no name prefix, no terminal emitted).
- Lark's `%import common.NUMBER` style imports have no LazyLR equivalent; copy the
  regex directly into the `tokens` section.

### 2) EBNF operators

Lark supports EBNF shorthands (`?`, `*`, `+`, `~n`) that LazyLR does not have.
Each must be desugared into explicit nullable or recursive productions.

| Lark EBNF    | Meaning             | LazyLR equivalent                                   |
|--------------|---------------------|-----------------------------------------------------|
| `rule?`      | zero or one         | Two productions: one with `rule`, one epsilon       |
| `rule*`      | zero or more        | `List :` (epsilon) and `List : List rule`           |
| `rule+`      | one or more         | `List : rule` and `List : List rule`                |
| `~2..4`      | repetition range    | Expand manually into 2, 3, and 4-element productions|

Example — `arg*` (zero or more arguments):

Lark:
```python
  call: NAME "(" arg* ")"
```

LazyLR:
```text
grammar {
  Call    : NAME '(' ArgList ')'
  ArgList :               // epsilon; zero args
  ArgList : ArgList Arg   // one or more args (left-recursive)
}
```

Example — `item+` (one or more items):

Lark:
```python
"items: item+"
```

LazyLR:
```text
grammar {
  Items : Item            // base case
  Items : Items Item      // left-recursive accumulation
}
```

### 3) Terminals (tokens) migration

Lark terminals are uppercase identifiers with regex bodies.
LazyLR uses the `tokens` section with the same `name: /regex/` syntax.

Lark:
```python
NAME    : /[a-zA-Z_]\w*/
INT     : /[0-9]+/
FLOAT   : /[0-9]+\.[0-9]*/
NEWLINE : /\n/
%ignore /[ \t]+/
```

LazyLR:
```text
tokens {
  NAME:    /[a-zA-Z_]\w*/
  INT:     /[0-9]+/
  FLOAT:   /[0-9]+\.[0-9]*/
  NEWLINE: /\n/
  /[ \t]+/
}
```

Migration notes:
- Lark terminal priority is controlled by explicit `priority` declarations or by rule order.
  LazyLR uses only the declaration order.

### 4) Ambiguity and precedence

When using Lark's LALR(1) backend, operator precedence is handled by grammar stratification
(separate `expr`, `term`, `atom` rules).

LazyLR requires explicit precedence declarations for ambiguous grammars, but also accepts
stratified grammars without any `precedence` section.

**Option A: Keep stratified grammar (no precedence section needed):**

```text
grammar {
  Expr : Expr '+' Term
  Expr : Term
  Term : Term '*' Atom
  Term : Atom
  Atom : NUMBER
  Atom : '(' Expr ')'
}
```

**Option B: Flatten with explicit precedence (matches Earley/ambiguous Lark grammars):**

```text
precedence {
  left: '+'
  left: '*'
}
grammar {
  Expr : Expr '+' Expr
  Expr : Expr '*' Expr
  Expr : NUMBER
  Expr : '(' Expr ')'
}
```

Option B is more compact but should be tested with `mg.verify()` to confirm there are no unresolved conflicts.

### 5) Tree construction and semantic actions

Lark automatically builds a `Tree` with named children that you traverse using a `Transformer`
or `Visitor`. LazyLR requires you to define your own AST types and wire them up through
an `Evaluator<V>` or `Visitor<V>`.

**Lark Transformer:**
```python
from lark import Transformer

class CalcTransformer(Transformer):
    def NUMBER(self, token):
        return int(token)

    def add(self, args):
        return args[0] + args[1]

    def mul(self, args):
        return args[0] * args[1]
```

**LazyLR Visitor:**
```java
final class CalcVisitor implements Visitor<Integer> {
  public int NUMBER(Terminal t) {
    return Integer.parseInt(t.value());
  }

  @ProductionName("Expr : Expr + Expr")
  public int add(int left, int right) {
    return left + right;
  }

  @ProductionName("Expr : Expr * Expr")
  public int mul(int left, int right) {
    return left * right;
  }
}
```

Migration notes:
- Lark's `Transformer` methods receive a list of already-transformed children; LazyLR
  production methods receive each child as a typed parameter directly.
- Lark's `Tree.data` (rule alias) corresponds to the `@ProductionName` string in LazyLR.
- Lark's `Token` (a terminal with a type and value) corresponds to LazyLR's `Terminal`
  (with `name()` and `value()`).
- Lark's `Discard` return value (to suppress a node) has no direct equivalent; in LazyLR,
  omit the terminal method and the terminal will be filtered from production parameters.
- For building an AST, define your node types as Java `sealed interface` and `record`
  and return them from visitor methods.

### 6) Inline rules and anonymous terminals

Lark supports anonymous string literals directly inside rules (e.g., `"if"`, `"+"`).
LazyLR does the same: quoted literals in the `grammar` section are automatically registered
as terminals and added to the token list before named tokens.

Lark:
```python
if_stmt: "if" expr "then" stmt
```

LazyLR:
```text
grammar {
  IfStmt : 'if' Expr 'then' Stmt
}
```

In LazyLR, the quoted literals `'if'` and `'then'` are matched before any named token
(e.g., before `NAME`), so keyword promotion is automatic as long as the grammar uses
quoted forms.

### 7) Error handling migration

| Lark mechanism                           | LazyLR equivalent                             |
|------------------------------------------|-----------------------------------------------|
| `UnexpectedToken` exception              | `ParsingException` (includes line + column)   |
| `UnexpectedCharacters` exception         | `ParsingException` with "Lexing error" prefix |
| `UnexpectedEOF` exception                | `ParsingException` with `<end of file>`       |
| `on_error` callback in `UnexpectedToken` | Not built-in; catch and handle at call site   |
| `ambiguity='resolve'` (Earley)           | Add a `precedence` section; run `mg.verify()` |
| `ambiguity='explicit'` (Earley)          | No equivalent; explicit precedence required   |

### 8) Parser backend selection

Lark lets you choose between Earley and LALR(1) backends at instantiation time.
LazyLR is always LR(1) (strictly more powerful than LALR(1)).

| Lark backend      | Notes when migrating to LazyLR                                                                 |
|-------------------|------------------------------------------------------------------------------------------------|
| `parser="lalr"`   | Straightforward migration; run `mg.verify()` to check for conflicts                            |
| `parser="earley"` | Earley handles ambiguous grammars silently.                                                    |
|                   | LazyLR requires you to resolve ambiguities via the `precedence` section or grammar refactoring |

If you relied on Earley's ability to parse ambiguous grammars, start by running
`mg.verify(errors::add)` and work through each reported conflict before going to production.

