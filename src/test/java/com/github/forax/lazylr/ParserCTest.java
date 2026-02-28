package com.github.forax.lazylr;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class ParserCTest {
  public static Lexer createCLexer() {
    return Lexer.createLexer(List.of(
        // Keywords
        new Rule("if", "if"),
        new Rule("else", "else"),
        new Rule("while", "while"),
        new Rule("return", "return"),
        new Rule("type", "int|void"),

        // Multi-character Operators
        new Rule("==", "=="),
        new Rule("!=", "!="),
        new Rule("<=", "<="),
        new Rule(">=", ">="),
        new Rule("->", "->"),

        // Single-character Operators & Punctuation
        new Rule("=", "="),
        new Rule("<", "<"),
        new Rule(">", ">"),
        new Rule("+", "\\+"),
        new Rule("-", "-"),
        new Rule("*", "\\*"),
        new Rule("/", "/"),
        new Rule("%", "%"),
        new Rule("&", "&"),
        new Rule(".", "\\."),
        new Rule(",", ","),
        new Rule(";", ";"),
        new Rule("(", "\\("),
        new Rule(")", "\\)"),
        new Rule("{", "\\{"),
        new Rule("}", "\\}"),

        // Identifiers and Literals
        new Rule("id", "[a-zA-Z_][a-zA-Z0-9_]*"),
        new Rule("num", "[0-9]+"),

        // Whitespaces
        new Rule("[ |\\t|\\r|\\n]+")
    ));
  }

  private static Parser createCParser() {
    // Symbols
    var translationUnit = new NonTerminal("translationUnit");
    var functionDef = new NonTerminal("functionDef");
    var decl = new NonTerminal("decl");
    var typeSpec = new NonTerminal("typeSpec");
    var compoundStmt = new NonTerminal("compoundStmt");
    var stmtList = new NonTerminal("stmtList");
    var stmt = new NonTerminal("stmt");
    var expr = new NonTerminal("expr");
    var parameterList = new NonTerminal("parameterList");
    var parameterDecl = new NonTerminal("parameterDecl");
    var argumentList = new NonTerminal("argumentList");

    // Terminals
    var type = new Terminal("type");
    var id = new Terminal("id");
    var num = new Terminal("num");
    var ifTerm = new Terminal("if");
    var elseTerm = new Terminal("else");
    var whileTerm = new Terminal("while");
    var returnTerm = new Terminal("return");

    var plus = new Terminal("+");
    var minus = new Terminal("-");
    var mul = new Terminal("*");
    var div = new Terminal("/");
    var mod = new Terminal("%");
    var assign = new Terminal("=");

    // Comparison Terminals
    var eq = new Terminal("==");
    var neq = new Terminal("!=");
    var lt = new Terminal("<");
    var gt = new Terminal(">");
    var lte = new Terminal("<=");
    var gte = new Terminal(">=");

    var comma = new Terminal(",");
    var lParen = new Terminal("(");
    var rParen = new Terminal(")");
    var lBrace = new Terminal("{");
    var rBrace = new Terminal("}");
    var semi = new Terminal(";");

    // Labels used by production for Precedence
    Production pUnaryMinus;
    Production pDereference;

    var grammar = new Grammar(translationUnit, List.of(
        new Production(translationUnit, List.of(stmt)),
        new Production(translationUnit, List.of(translationUnit, stmt)),
        new Production(translationUnit, List.of(functionDef)),
        new Production(translationUnit, List.of(translationUnit, functionDef)),

        new Production(functionDef, List.of(typeSpec, id, lParen, parameterList, rParen, compoundStmt)),
        new Production(functionDef, List.of(typeSpec, id, lParen, rParen, compoundStmt)),
        new Production(parameterList, List.of(parameterDecl)),
        new Production(parameterList, List.of(parameterList, comma, parameterDecl)),
        new Production(parameterDecl, List.of(typeSpec, id)),

        new Production(compoundStmt, List.of(lBrace, rBrace)),
        new Production(compoundStmt, List.of(lBrace, stmtList, rBrace)),
        new Production(stmtList, List.of(stmt)),
        new Production(stmtList, List.of(stmtList, stmt)),
        new Production(stmt, List.of(decl)),
        new Production(stmt, List.of(expr, semi)),
        new Production(stmt, List.of(ifTerm, lParen, expr, rParen, stmt)),
        new Production(stmt, List.of(ifTerm, lParen, expr, rParen, stmt, elseTerm, stmt)),
        new Production(stmt, List.of(whileTerm, lParen, expr, rParen, stmt)),
        new Production(stmt, List.of(returnTerm, expr, semi)),
        new Production(stmt, List.of(returnTerm, semi)),
        new Production(stmt, List.of(compoundStmt)),

        new Production(expr, List.of(num)),
        new Production(expr, List.of(id)),
        new Production(expr, List.of(lParen, expr, rParen)),

        pUnaryMinus = new Production(expr, List.of(minus, expr)),
        pDereference = new Production(expr, List.of(mul, expr)),

        new Production(expr, List.of(expr, plus, expr)),
        new Production(expr, List.of(expr, minus, expr)),
        new Production(expr, List.of(expr, mul, expr)),
        new Production(expr, List.of(expr, div, expr)),
        new Production(expr, List.of(expr, mod, expr)),

        // Comparison Productions
        new Production(expr, List.of(expr, eq, expr)),
        new Production(expr, List.of(expr, neq, expr)),
        new Production(expr, List.of(expr, lt, expr)),
        new Production(expr, List.of(expr, gt, expr)),
        new Production(expr, List.of(expr, lte, expr)),
        new Production(expr, List.of(expr, gte, expr)),

        new Production(expr, List.of(expr, assign, expr)),
        new Production(argumentList, List.of(expr)),
        new Production(argumentList, List.of(argumentList, comma, expr)),
        new Production(expr, List.of(expr, lParen, argumentList, rParen)),
        new Production(expr, List.of(expr, lParen, rParen)),

        new Production(decl, List.of(typeSpec, id, semi)),
        new Production(decl, List.of(typeSpec, id, assign, expr, semi)),
        new Production(typeSpec, List.of(type))
    ));

    // Precedence Map (using Terminals and specific Productions)
    var precedence = Map.ofEntries(
        Map.entry(elseTerm,    new Precedence(10, Precedence.Associativity.RIGHT)),
        Map.entry(assign,      new Precedence(20, Precedence.Associativity.RIGHT)),
        Map.entry(eq,          new Precedence(30, Precedence.Associativity.LEFT)),
        Map.entry(neq,         new Precedence(30, Precedence.Associativity.LEFT)),
        Map.entry(lt,          new Precedence(40, Precedence.Associativity.LEFT)),
        Map.entry(gt,          new Precedence(40, Precedence.Associativity.LEFT)),
        Map.entry(lte,         new Precedence(40, Precedence.Associativity.LEFT)),
        Map.entry(gte,         new Precedence(40, Precedence.Associativity.LEFT)),
        Map.entry(plus,        new Precedence(50, Precedence.Associativity.LEFT)),
        Map.entry(minus,       new Precedence(50, Precedence.Associativity.LEFT)),
        Map.entry(mul,         new Precedence(60, Precedence.Associativity.LEFT)),
        Map.entry(div,         new Precedence(60, Precedence.Associativity.LEFT)),
        Map.entry(mod,         new Precedence(60, Precedence.Associativity.LEFT)),
        // Unary Productions bind tighter than Binary Multiplication
        Map.entry(pUnaryMinus, new Precedence(70, Precedence.Associativity.RIGHT)),
        Map.entry(pDereference,new Precedence(70, Precedence.Associativity.RIGHT)),
        Map.entry(lParen,      new Precedence(80, Precedence.Associativity.LEFT)) // Function Calls
    );

    return Parser.createParser(grammar, precedence);
  }

  // Define the AST node types
  sealed interface Node {}

  // Expressions
  sealed interface Expr extends Node {}
  record NumLit(int value) implements Expr {}
  record IdRef(String name) implements Expr {}
  record BinaryOp(String op, Expr left, Expr right) implements Expr {}
  record UnaryOp(String op, Expr operand) implements Expr {}
  record Call(Expr callee, List<Expr> args) implements Expr {}
  record Assign(Expr target, Expr value) implements Expr {}

  // Declarations & Types
  sealed interface Stmt extends Node {}
  record TypeSpec(String name) implements Node {}
  record Decl(TypeSpec type, String name, Expr init) implements Stmt {}  // init can be null

  // Statements
  record ExprStmt(Expr expr) implements Stmt {}
  record ReturnStmt(Expr expr) implements Stmt {}  // value may be null
  record IfStmt(Expr condition, Stmt then, Stmt else_) implements Stmt {}  // else_ may be null
  record WhileStmt(Expr condition, Stmt body) implements Stmt {}
  record Block(List<Stmt> stmts) implements Stmt {}
  record FunctionDef(TypeSpec returnType, String name, List<Decl> params, Block body) implements Node {}

  // Top-level
  record TranslationUnit(List<Node> items) implements Node {}

  // Argument list and parameter list intermediary holders
  // (These are not real AST nodes — just parse-time accumulators)
  record ArgList(List<Expr> exprs) implements Node {}
  record ParamList(List<Decl> params) implements Node {}

  private static class NodeEvaluator implements Evaluator<Node> {
    @Override
    public Node evaluate(Terminal terminal) {
      return switch (terminal.name()) {
        case "num" -> new NumLit(Integer.parseInt(terminal.value()));
        case "id"  -> new IdRef(terminal.value());
        case "type" -> new TypeSpec(terminal.value());
        // All other terminals (operators, keywords, punctuation)
        // return null — they are recovered from the production name
        default -> null;
      };
    }

    @Override
    public Node evaluate(Production production, List<Node> args) {
      return switch (production.name()) {

        // -- typeSpec
        case "typeSpec : type" ->
            args.get(0); // already a TypeSpec from terminal eval

        // -- parameterList
        case "parameterDecl : typeSpec id" ->
            new Decl((TypeSpec) args.get(0), ((IdRef) args.get(1)).name(), null);
        case "parameterList : parameterDecl" ->
            new ParamList(List.of((Decl) args.get(0)));
        case "parameterList : parameterList , parameterDecl" -> {
          var list = new ArrayList<>(((ParamList) args.get(0)).params());
          list.add((Decl) args.get(2));
          yield new ParamList(list);
        }

        // -- argumentList
        case "argumentList : expr" ->
            new ArgList(List.of((Expr) args.get(0)));
        case "argumentList : argumentList , expr" -> {
          var list = new ArrayList<>(((ArgList) args.get(0)).exprs());
          list.add((Expr) args.get(2));
          yield new ArgList(list);
        }

        // -- expr
        case "expr : num"           -> args.get(0);
        case "expr : id"            -> args.get(0);
        case "expr : ( expr )"      -> args.get(1);
        case "expr : - expr"        -> new UnaryOp("-",  (Expr) args.get(1));
        case "expr : * expr"        -> new UnaryOp("*",  (Expr) args.get(1));
        case "expr : expr + expr"   -> new BinaryOp("+", (Expr) args.get(0), (Expr) args.get(2));
        case "expr : expr - expr"   -> new BinaryOp("-", (Expr) args.get(0), (Expr) args.get(2));
        case "expr : expr * expr"   -> new BinaryOp("*", (Expr) args.get(0), (Expr) args.get(2));
        case "expr : expr / expr"   -> new BinaryOp("/", (Expr) args.get(0), (Expr) args.get(2));
        case "expr : expr % expr"   -> new BinaryOp("%", (Expr) args.get(0), (Expr) args.get(2));
        case "expr : expr == expr"  -> new BinaryOp("==", (Expr) args.get(0), (Expr) args.get(2));
        case "expr : expr != expr"  -> new BinaryOp("!=", (Expr) args.get(0), (Expr) args.get(2));
        case "expr : expr < expr"   -> new BinaryOp("<",  (Expr) args.get(0), (Expr) args.get(2));
        case "expr : expr > expr"   -> new BinaryOp(">",  (Expr) args.get(0), (Expr) args.get(2));
        case "expr : expr <= expr"  -> new BinaryOp("<=", (Expr) args.get(0), (Expr) args.get(2));
        case "expr : expr >= expr"  -> new BinaryOp(">=", (Expr) args.get(0), (Expr) args.get(2));
        case "expr : expr = expr"   -> new Assign((Expr) args.get(0), (Expr) args.get(2));
        case "expr : expr ( argumentList )" ->
            new Call((Expr) args.get(0), ((ArgList) args.get(2)).exprs());
        case "expr : expr ( )" ->
            new Call((Expr) args.get(0), List.of());

        // -- decl
        case "decl : typeSpec id ;" ->
            new Decl((TypeSpec) args.get(0), ((IdRef) args.get(1)).name(), null);
        case "decl : typeSpec id = expr ;" ->
            new Decl((TypeSpec) args.get(0), ((IdRef) args.get(1)).name(), (Expr) args.get(3));

        // -- stmt
        case "stmt : decl"          -> args.get(0);
        case "stmt : expr ;"        -> new ExprStmt((Expr) args.get(0));
        case "stmt : return expr ;" -> new ReturnStmt((Expr) args.get(1));
        case "stmt : return ;"      -> new ReturnStmt(null);
        case "stmt : compoundStmt"  -> args.get(0);
        case "stmt : if ( expr ) stmt" ->
            new IfStmt((Expr) args.get(2), (Stmt) args.get(4), null);
        case "stmt : if ( expr ) stmt else stmt" ->
            new IfStmt((Expr) args.get(2), (Stmt) args.get(4), (Stmt) args.get(6));
        case "stmt : while ( expr ) stmt" ->
            new WhileStmt((Expr) args.get(2), (Stmt) args.get(4));

        // -- compoundStmt
        case "compoundStmt : { }" ->
            new Block(List.of());
        case "compoundStmt : { stmtList }" ->
            args.get(1);

        // -- stmtList
        case "stmtList : stmt" ->
            new Block(List.of((Stmt) args.get(0)));
        case "stmtList : stmtList stmt" -> {
          var list = new ArrayList<>(((Block) args.get(0)).stmts());
          list.add((Stmt) args.get(1));
          yield new Block(list);
        }

        // -- functionDef
        case "functionDef : typeSpec id ( parameterList ) compoundStmt" ->
            new FunctionDef(
                (TypeSpec) args.get(0),
                ((IdRef) args.get(1)).name(),
                ((ParamList) args.get(3)).params(),
                (Block) args.get(5));
        case "functionDef : typeSpec id ( ) compoundStmt" ->
            new FunctionDef(
                (TypeSpec) args.get(0),
                ((IdRef) args.get(1)).name(),
                List.of(),
                (Block) args.get(4));

        // -- translationUnit
        case "translationUnit : stmt" ->
            new TranslationUnit(List.of(args.get(0)));
        case "translationUnit : functionDef" ->
            new TranslationUnit(List.of(args.get(0)));
        case "translationUnit : translationUnit stmt" -> {
          var list = new ArrayList<>(((TranslationUnit) args.get(0)).items());
          list.add(args.get(1));
          yield new TranslationUnit(list);
        }
        case "translationUnit : translationUnit functionDef" -> {
          var list = new ArrayList<>(((TranslationUnit) args.get(0)).items());
          list.add(args.get(1));
          yield new TranslationUnit(list);
        }

        default -> throw new AssertionError("unknown production: " + production.name());
      };
    }
  }

  private static String toText(Node node) {
    return switch (node) {
      case TranslationUnit(List<Node> items) ->
          items.stream().map(ParserCTest::toText).collect(joining("\n"));
      case FunctionDef(TypeSpec returnType, String name, List<Decl> params, Block body) -> {
        var paramStr = params.stream()
            .map(p -> toText(p.type()) + " " + p.name())
            .collect(joining(", "));
        yield toText(returnType) + " " + name + "(" + paramStr + ") " + toText(body);
      }
      case TypeSpec(String name) -> name;
      case Block(List<Stmt> stmts) ->
          stmts.stream().map(ParserCTest::toText).collect(joining(" ", "{ "," }"));
      case Decl(TypeSpec type, String name, Expr init) when init == null ->
          toText(type) + " " + name + ";";
      case Decl(TypeSpec type, String name, Expr init) ->
          toText(type) + " " + name + " = " + toText(init) + ";";
      case ExprStmt(Expr expr) -> toText(expr) + ";";
      case ReturnStmt(Expr expr) when expr == null -> "return;";
      case ReturnStmt(Expr expr) -> "return " + toText(expr) + ";";
      case IfStmt(Expr cond, Stmt then, Stmt _else) when _else == null ->
          "if (" + toText(cond) + ") " + toText(then);
      case IfStmt(Expr cond, Stmt then, var else_) ->
          "if (" + toText(cond) + ") " + toText(then) + " else " + toText(else_);
      case WhileStmt(Expr cond, Stmt body) ->
          "while (" + toText(cond) + ") " + toText(body);
      case NumLit(int value) -> String.valueOf(value);
      case IdRef(String name) -> name;
      case BinaryOp(String op, Expr left, Expr right) ->
          toText(left) + " " + op + " " + toText(right);
      case UnaryOp(String op, Expr operand) -> op + toText(operand);
      case Assign(Expr target, Expr value) -> toText(target) + " = " + toText(value);
      case Call(Expr callee, List<Expr> args) ->
          toText(callee) + args.stream().map(ParserCTest::toText).collect(joining(", ", "(", ")"));
      // parse-time accumulators — should never appear in a finished tree
      case ArgList _, ParamList _ -> throw new AssertionError("unexpected parse-time node: " + node);
    };
  }

  @Nested
  public class ControlFlow {
    @Test
    public void testWhileLoopWithComparison() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        void loop() {
            int i = 0;
            while (i < 10) {
                i = i + 1;
            }
        }
        """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=void], \
        name=loop, params=[], \
        body=Block[stmts=[\
        Decl[type=TypeSpec[name=int], name=i, init=NumLit[value=0]], \
        WhileStmt[\
        condition=BinaryOp[op=<, left=IdRef[name=i], right=NumLit[value=10]], \
        body=Block[stmts=[\
        ExprStmt[expr=\
        Assign[target=IdRef[name=i], value=\
        BinaryOp[op=+, left=IdRef[name=i], right=NumLit[value=1]]\
        ]]]]]]]]]]\
        """,
          result.toString());
    }

    @Test
    public void testDanglingElseResolution() {
      var lexer = createCLexer();
      var parser = createCParser();
      // Tests if 'else' correctly binds to the inner 'if'
      var code = """
        void check() {
            if (a == 1)
                if (b == 1) return 1;
                else return 0;
        }
        """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=void], \
        name=check, params=[], \
        body=Block[stmts=[\
        IfStmt[\
        condition=BinaryOp[op===, left=IdRef[name=a], right=NumLit[value=1]], \
        then=IfStmt[\
        condition=BinaryOp[op===, left=IdRef[name=b], right=NumLit[value=1]], \
        then=ReturnStmt[expr=NumLit[value=1]], \
        else_=ReturnStmt[expr=NumLit[value=0]]], \
        else_=null]]]]]]\
        """,
          result.toString());
    }

    @Test
    public void testIfElseChain() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        void f() {
            if (x == 0) return 0;
            else if (x == 1) return 1;
            else return 2;
        }
        """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=void], \
        name=f, params=[], \
        body=Block[stmts=[\
        IfStmt[\
        condition=BinaryOp[op===, left=IdRef[name=x], right=NumLit[value=0]], \
        then=ReturnStmt[expr=NumLit[value=0]], \
        else_=IfStmt[\
        condition=BinaryOp[op===, left=IdRef[name=x], right=NumLit[value=1]], \
        then=ReturnStmt[expr=NumLit[value=1]], \
        else_=ReturnStmt[expr=NumLit[value=2]]]]\
        ]]]]]\
        """,
          result.toString());
    }

    @Test
    public void testNestedWhile() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        void f() {
            while (i < 10) {
                while (j < 10) {
                    j = j + 1;
                }
                i = i + 1;
            }
        }
        """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=void], \
        name=f, params=[], \
        body=Block[stmts=[\
        WhileStmt[\
        condition=BinaryOp[op=<, left=IdRef[name=i], right=NumLit[value=10]], \
        body=Block[stmts=[\
        WhileStmt[\
        condition=BinaryOp[op=<, left=IdRef[name=j], right=NumLit[value=10]], \
        body=Block[stmts=[\
        ExprStmt[expr=Assign[target=IdRef[name=j], value=BinaryOp[op=+, left=IdRef[name=j], right=NumLit[value=1]]]]\
        ]]], \
        ExprStmt[expr=Assign[target=IdRef[name=i], value=BinaryOp[op=+, left=IdRef[name=i], right=NumLit[value=1]]]]\
        ]]]]]]]]\
        """,
          result.toString());
    }

    @Test
    public void testEmptyFunction() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        void f() {}
        """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=void], \
        name=f, params=[], \
        body=Block[stmts=[]]\
        ]]]\
        """,
          result.toString());
    }
  }

  @Nested
  public class Arithmetic {
    @Test
    public void testUnaryAndBinaryPrecedence() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
      void math(int v) {
          int x = -n * 5 + v / 2;
      }
      """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=void], \
        name=math, params=[Decl[type=TypeSpec[name=int], name=v, init=null]], \
        body=Block[stmts=[\
        Decl[type=TypeSpec[name=int], name=x, init=\
        BinaryOp[op=+, \
        left=BinaryOp[op=*, left=UnaryOp[op=-, operand=IdRef[name=n]], right=NumLit[value=5]], \
        right=BinaryOp[op=/, left=IdRef[name=v], right=NumLit[value=2]]\
        ]]]]]]]\
        """,
          result.toString());
    }

    @Test
    public void testRelativePrecedence() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
      void logic() {
          if (x + 1 <= y == z > 0) return;
      }
      """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=void], \
        name=logic, params=[], \
        body=Block[stmts=[\
        IfStmt[\
        condition=BinaryOp[op===, \
        left=BinaryOp[op=<=, left=BinaryOp[op=+, left=IdRef[name=x], right=NumLit[value=1]], right=IdRef[name=y]], \
        right=BinaryOp[op=>, left=IdRef[name=z], right=NumLit[value=0]]], \
        then=ReturnStmt[expr=null], \
        else_=null]]]]]]\
        """,
          result.toString());
    }
  }

  @Nested
  public class FunctionCall {
    @Test
    public void testNestedFunctionCalls() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        void main() { int res = add(get_x(), multiply(y, 2)); }
        """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=void], \
        name=main, params=[], \
        body=Block[stmts=[\
        Decl[type=TypeSpec[name=int], name=res, init=\
        Call[callee=IdRef[name=add], args=[\
        Call[callee=IdRef[name=get_x], args=[]], \
        Call[callee=IdRef[name=multiply], args=[IdRef[name=y], NumLit[value=2]]]\
        ]]]]]]]]\
        """,
          result.toString());
    }

    @Test
    public void testFactorialFunction() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
      int factorial(int n) {
          if (n <= 1) {
              return 1;
          }
          return n * factorial(n - 1);
      }
      """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=int], \
        name=factorial, params=[Decl[type=TypeSpec[name=int], name=n, init=null]], \
        body=Block[stmts=[\
        IfStmt[\
        condition=BinaryOp[op=<=, left=IdRef[name=n], right=NumLit[value=1]], \
        then=Block[stmts=[ReturnStmt[expr=NumLit[value=1]]]], \
        else_=null], \
        ReturnStmt[expr=\
        BinaryOp[op=*, left=IdRef[name=n], right=\
        Call[callee=IdRef[name=factorial], args=[\
        BinaryOp[op=-, left=IdRef[name=n], right=NumLit[value=1]]\
        ]]]]]]]]]\
        """,
          result.toString());
    }
  }

  @Nested
  public class Declarations {
    @Test
    public void testMultipleParameters() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        int add(int a, int b) { return a + b; }
        """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=int], \
        name=add, params=[Decl[type=TypeSpec[name=int], name=a, init=null], Decl[type=TypeSpec[name=int], name=b, init=null]], \
        body=Block[stmts=[\
        ReturnStmt[expr=BinaryOp[op=+, left=IdRef[name=a], right=IdRef[name=b]]]\
        ]]]]]\
        """,
          result.toString());
    }

    @Test
    public void testMultipleDeclarations() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        void f() {
            int a = 1;
            int b = 2;
            int c = 3;
        }
        """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=void], \
        name=f, params=[], \
        body=Block[stmts=[\
        Decl[type=TypeSpec[name=int], name=a, init=NumLit[value=1]], \
        Decl[type=TypeSpec[name=int], name=b, init=NumLit[value=2]], \
        Decl[type=TypeSpec[name=int], name=c, init=NumLit[value=3]]\
        ]]]]]\
        """,
          result.toString());
    }
  }

  @Nested
  public class Expressions {
    @Test
    public void testChainedAssignment() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        void f() { a = b = c = 0; }
        """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      // assignment is RIGHT associative so a = (b = (c = 0))
      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=void], \
        name=f, params=[], \
        body=Block[stmts=[\
        ExprStmt[expr=\
        Assign[target=IdRef[name=a], value=\
        Assign[target=IdRef[name=b], value=\
        Assign[target=IdRef[name=c], value=NumLit[value=0]]]]]\
        ]]]]]\
        """,
          result.toString());
    }

    @Test
    public void testParenthesesOverridePrecedence() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        void f() { int x = (a + b) * (c + d); }
        """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=void], \
        name=f, params=[], \
        body=Block[stmts=[\
        Decl[type=TypeSpec[name=int], name=x, init=\
        BinaryOp[op=*, \
        left=BinaryOp[op=+, left=IdRef[name=a], right=IdRef[name=b]], \
        right=BinaryOp[op=+, left=IdRef[name=c], right=IdRef[name=d]]]\
        ]]]]]]\
        """,
          result.toString());
    }

    @Test
    public void testAllComparisonOperators() {
      var lexer = createCLexer();
      var parser = createCParser();
      // each expression is a standalone statement to test each op in isolation
      var code = """
        void f() {
            a == b;
            a != b;
            a < b;
            a > b;
            a <= b;
            a >= b;
        }
        """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=void], \
        name=f, params=[], \
        body=Block[stmts=[\
        ExprStmt[expr=BinaryOp[op===, left=IdRef[name=a], right=IdRef[name=b]]], \
        ExprStmt[expr=BinaryOp[op=!=, left=IdRef[name=a], right=IdRef[name=b]]], \
        ExprStmt[expr=BinaryOp[op=<, left=IdRef[name=a], right=IdRef[name=b]]], \
        ExprStmt[expr=BinaryOp[op=>, left=IdRef[name=a], right=IdRef[name=b]]], \
        ExprStmt[expr=BinaryOp[op=<=, left=IdRef[name=a], right=IdRef[name=b]]], \
        ExprStmt[expr=BinaryOp[op=>=, left=IdRef[name=a], right=IdRef[name=b]]]\
        ]]]]]\
        """,
          result.toString());
    }

    @Test
    public void testAllArithmeticOperators() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        void f() {
            a + b;
            a - b;
            a * b;
            a / b;
            a % b;
        }
        """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=void], \
        name=f, params=[], \
        body=Block[stmts=[\
        ExprStmt[expr=BinaryOp[op=+, left=IdRef[name=a], right=IdRef[name=b]]], \
        ExprStmt[expr=BinaryOp[op=-, left=IdRef[name=a], right=IdRef[name=b]]], \
        ExprStmt[expr=BinaryOp[op=*, left=IdRef[name=a], right=IdRef[name=b]]], \
        ExprStmt[expr=BinaryOp[op=/, left=IdRef[name=a], right=IdRef[name=b]]], \
        ExprStmt[expr=BinaryOp[op=%, left=IdRef[name=a], right=IdRef[name=b]]]\
        ]]]]]\
        """,
          result.toString());
    }
  }

  @Nested
  public class MultipleTopLevel {
    @Test
    public void testMultipleFunctions() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        int square(int n) { return n * n; }
        int cube(int n) { return n * square(n); }
        """;
      var tokens = lexer.tokenize(code);
      var result = parser.parse(tokens, new NodeEvaluator());

      assertEquals("""
        TranslationUnit[items=[\
        FunctionDef[\
        returnType=TypeSpec[name=int], \
        name=square, params=[Decl[type=TypeSpec[name=int], name=n, init=null]], \
        body=Block[stmts=[\
        ReturnStmt[expr=BinaryOp[op=*, left=IdRef[name=n], right=IdRef[name=n]]]\
        ]]], \
        FunctionDef[\
        returnType=TypeSpec[name=int], \
        name=cube, params=[Decl[type=TypeSpec[name=int], name=n, init=null]], \
        body=Block[stmts=[\
        ReturnStmt[expr=BinaryOp[op=*, left=IdRef[name=n], right=Call[callee=IdRef[name=square], args=[IdRef[name=n]]]]]\
        ]]]]]\
        """,
          result.toString());
    }
  }

  @Nested
  public class InvalidTest {
    @Test
    public void testInvalid_MissingSemicolon() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        void f() { int x = 5 }
        """;
      var tokens = lexer.tokenize(code);
      assertThrows(Exception.class, () ->
          parser.parse(tokens, new NodeEvaluator()));
    }

    @Test
    public void testInvalid_MismatchedParens() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        void f() { if (x == 1 { return 0; } }
        """; // Error: Missing ')'
      var tokens = lexer.tokenize(code);
      assertThrows(Exception.class, () ->
          parser.parse(tokens, new NodeEvaluator()));
    }

    @Test
    public void testInvalid_MissingClosingBrace() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        void f() { int x = 5;
        """;
      var tokens = lexer.tokenize(code);
      assertThrows(Exception.class, () ->
          parser.parse(tokens, new NodeEvaluator()));
    }

    @Test
    public void testInvalid_ReturnWithoutSemicolon() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        void f() { return 1 }
        """;
      var tokens = lexer.tokenize(code);
      assertThrows(Exception.class, () ->
          parser.parse(tokens, new NodeEvaluator()));
    }

    @Test
    public void testInvalid_ExpressionWithoutSemicolon() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        void f() { a + b }
        """;
      var tokens = lexer.tokenize(code);
      assertThrows(Exception.class, () ->
          parser.parse(tokens, new NodeEvaluator()));
    }

    @Test
    public void testInvalid_EmptyParentheses() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        void f() { int x = (); }
        """;
      var tokens = lexer.tokenize(code);
      assertThrows(Exception.class, () ->
          parser.parse(tokens, new NodeEvaluator()));
    }
  }

  @Nested
  public class ToTextTests {
    @Test
    public void testWhileLoopWithComparisonRoundTrip() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = "void loop() { int i = 0; while (i < 10) { i = i + 1; } }";
      var tokens = lexer.tokenize(code);
      var node = parser.parse(tokens, new NodeEvaluator());

      assertEquals(code, toText(node));
    }

    @Test
    public void testDanglingElseResolutionRoundTrip() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = "void check() { if (a == 1) if (b == 1) return 1; else return 0; }";
      var tokens = lexer.tokenize(code);
      var node = parser.parse(tokens, new NodeEvaluator());

      assertEquals(code, toText(node));
    }

    @Test
    public void testChainedAssignmentIsRightAssociativeRoundTrip() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = "void f() { a = b = c = 0; }";
      var tokens = lexer.tokenize(code);
      var node = parser.parse(tokens, new NodeEvaluator());

      assertEquals(code, toText(node));
    }

    @Test
    public void testArithmeticPrecedenceRoundTrip() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = "void math(int ptr) { int x = -n * 5 + *ptr / 2; }";
      var tokens = lexer.tokenize(code);
      var node = parser.parse(tokens, new NodeEvaluator());

      assertEquals(code, toText(node));
    }

    @Test
    public void testFactorialRoundTrip() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = "int factorial(int n) { if (n <= 1) { return 1; } return n * factorial(n - 1); }";
      var tokens = lexer.tokenize(code);
      var node = parser.parse(tokens, new NodeEvaluator());

      assertEquals(code, toText(node));
    }

    @Test
    public void testMultipleFunctionsRoundTrip() {
      var lexer = createCLexer();
      var parser = createCParser();
      var code = """
        int square(int n) { return n * n; }
        int cube(int n) { return n * square(n); }\
        """;
      var tokens = lexer.tokenize(code);
      var node = parser.parse(tokens, new NodeEvaluator());

      assertEquals(code, toText(node));
    }
  }
}
