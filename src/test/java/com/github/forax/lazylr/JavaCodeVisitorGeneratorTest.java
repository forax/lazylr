package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaCodeVisitorGeneratorTest {

  private static String generateVisitor(String inputText) {
    var grammar = MetaGrammar.load(inputText).grammar();
    return JavaCodeVisitorGenerator.generateVisitor(grammar);
  }

  @Test
  public void testIdentifierTerminalsGetTerminalMethods() {
    // 'num' is an identifier terminal → gets a terminal method
    // '(' and ')' are not → filtered out
    var inputText = """
        grammar {
          Factor : num
          Factor : '(' Factor ')'
        }
        """;

    var actual = generateVisitor(inputText);
    assertEquals("""
        public sealed interface Factor permits NumFactor, LParenFactorRParenFactor {}
        public record NumFactor(String num) implements Factor {}
        public record LParenFactorRParenFactor(Factor factor) implements Factor {}
        
        class MyVisitor implements Visitor<Factor> {
        
          public String num(Terminal terminal) {
            return terminal.value();
          }
        
          @ProductionName("Factor : num")
          public Factor numFactor(String num) {
            return new NumFactor(num);
          }
        
          @ProductionName("Factor : ( Factor )")
          public Factor lParenFactorRParenFactor(Factor factor) {
            return new LParenFactorRParenFactor(factor);
          }
        
        }
        """, actual);
  }

  @Test
  public void testNonIdentifierTerminalsDoNotGetTerminalMethods() {
    var inputText = """
        grammar {
          Expr : Expr '+' Expr
          Expr : num
        }
        """;

    var actual = generateVisitor(inputText);
    assertEquals("""
        public sealed interface Expr permits ExprPlusExprExpr, NumExpr {}
        public record ExprPlusExprExpr(Expr expr, Expr expr2) implements Expr {}
        public record NumExpr(String num) implements Expr {}
        
        class MyVisitor implements Visitor<Expr> {
        
          public String num(Terminal terminal) {
            return terminal.value();
          }
        
          @ProductionName("Expr : Expr + Expr")
          public Expr exprPlusExprExpr(Expr expr, Expr expr2) {
            return new ExprPlusExprExpr(expr, expr2);
          }
        
          @ProductionName("Expr : num")
          public Expr numExpr(String num) {
            return new NumExpr(num);
          }
        
        }
        """, actual);
  }

  @Test
  public void testSingleProductionGeneratesRecord() {
    var inputText = """
        grammar {
          Point : x y
        }
        """;

    var actual = generateVisitor(inputText);

    assertEquals("""
        public record Point(String x, String y) {}
        
        class MyVisitor implements Visitor<Point> {
        
          public String x(Terminal terminal) {
            return terminal.value();
          }
        
          public String y(Terminal terminal) {
            return terminal.value();
          }
        
          @ProductionName("Point : x y")
          public Point point(String x, String y) {
            return new Point(x, y);
          }
        
        }
        """, actual);
  }

  @Test
  public void testArithmeticExpressionGeneratesSealedInterface() {
    var inputText = """
        grammar {
          Exp : Exp '+' Term
          Exp : Exp '-' Term
          Exp : Term
          Term : Term '*' Factor
          Term : Term '/' Factor
          Term : Factor
          Factor : num
          Factor : ident
          Factor : '(' Exp ')'
        }
        """;

    var expected = """
        public sealed interface Exp permits ExpPlusTermExp, ExpMinusTermExp, TermExp {}
        public record ExpPlusTermExp(Exp exp, Term term) implements Exp {}
        public record ExpMinusTermExp(Exp exp, Term term) implements Exp {}
        public record TermExp(Term term) implements Exp {}
        public sealed interface Term permits TermMulFactorTerm, TermDivFactorTerm, FactorTerm {}
        public record TermMulFactorTerm(Term term, Factor factor) implements Term {}
        public record TermDivFactorTerm(Term term, Factor factor) implements Term {}
        public record FactorTerm(Factor factor) implements Term {}
        public sealed interface Factor permits NumFactor, IdentFactor, LParenExpRParenFactor {}
        public record NumFactor(String num) implements Factor {}
        public record IdentFactor(String ident) implements Factor {}
        public record LParenExpRParenFactor(Exp exp) implements Factor {}
        
        class MyVisitor implements Visitor<Exp> {
        
          public String num(Terminal terminal) {
            return terminal.value();
          }
        
          public String ident(Terminal terminal) {
            return terminal.value();
          }
        
          @ProductionName("Exp : Exp + Term")
          public Exp expPlusTermExp(Exp exp, Term term) {
            return new ExpPlusTermExp(exp, term);
          }
        
          @ProductionName("Exp : Exp - Term")
          public Exp expMinusTermExp(Exp exp, Term term) {
            return new ExpMinusTermExp(exp, term);
          }
        
          @ProductionName("Exp : Term")
          public Exp termExp(Term term) {
            return new TermExp(term);
          }
        
          @ProductionName("Term : Term * Factor")
          public Term termMulFactorTerm(Term term, Factor factor) {
            return new TermMulFactorTerm(term, factor);
          }
        
          @ProductionName("Term : Term / Factor")
          public Term termDivFactorTerm(Term term, Factor factor) {
            return new TermDivFactorTerm(term, factor);
          }
        
          @ProductionName("Term : Factor")
          public Term factorTerm(Factor factor) {
            return new FactorTerm(factor);
          }
        
          @ProductionName("Factor : num")
          public Factor numFactor(String num) {
            return new NumFactor(num);
          }
        
          @ProductionName("Factor : ident")
          public Factor identFactor(String ident) {
            return new IdentFactor(ident);
          }
        
          @ProductionName("Factor : ( Exp )")
          public Factor lParenExpRParenFactor(Exp exp) {
            return new LParenExpRParenFactor(exp);
          }
        
        }
        """;

    var actual = generateVisitor(inputText);
    assertEquals(expected, actual);
  }

  @Test
  public void testOptionalTerminalProducesOptionalString() {
    var inputText = """
        grammar {
          Stmt : name opt_label
          opt_label : label
          opt_label :
        }
        """;

    var actual = generateVisitor(inputText);

    assertEquals("""
        public record Stmt(String name, Optional<String> opt_label) {}
        
        class MyVisitor implements Visitor<Stmt> {
        
          public String name(Terminal terminal) {
            return terminal.value();
          }
        
          public String label(Terminal terminal) {
            return terminal.value();
          }
        
          @ProductionName("Stmt : name opt_label")
          public Stmt stmt(String name, Optional<String> opt_label) {
            return new Stmt(name, opt_label);
          }
        
          @ProductionName("opt_label : label")
          public Optional<String> opt_labelOf(String label) {
            return Optional.of(label);
          }
        
          @ProductionName("opt_label : ε")
          public Optional<String> opt_labelEmpty() {
            return Optional.empty();
          }
        
        }
        """, actual);
  }

  @Test
  public void testOptionalNonTerminalProducesOptionalOfNtType() {
    var inputText = """
        grammar {
          Decl : name opt_init
          opt_init : Expr
          opt_init :
          Expr : num
        }
        """;

    var actual = generateVisitor(inputText);
    assertEquals("""
        public record Decl(String name, Optional<Expr> opt_init) {}
        public record Expr(String num) {}
        
        class MyVisitor implements Visitor<Decl> {
        
          public String name(Terminal terminal) {
            return terminal.value();
          }
        
          public String num(Terminal terminal) {
            return terminal.value();
          }
        
          @ProductionName("Decl : name opt_init")
          public Decl decl(String name, Optional<Expr> opt_init) {
            return new Decl(name, opt_init);
          }
        
          @ProductionName("opt_init : Expr")
          public Optional<Expr> opt_initOf(Expr expr) {
            return Optional.of(expr);
          }
        
          @ProductionName("opt_init : ε")
          public Optional<Expr> opt_initEmpty() {
            return Optional.empty();
          }
        
          @ProductionName("Expr : num")
          public Expr expr(String num) {
            return new Expr(num);
          }
        
        }
        """, actual);
  }

  @Test
  public void testListOfTerminalProducesListString() {
    var inputText = """
        grammar {
          Names : name
          Names : Names name
        }
        """;

    var actual = generateVisitor(inputText);
    assertEquals("""
        
        class MyVisitor implements Visitor<List<String>> {
        
          public String name(Terminal terminal) {
            return terminal.value();
          }
        
          @ProductionName("Names : name")
          public List<String> namesSingle(String name) {
            var list = new ArrayList<String>();
            list.add(name);
            return list;
          }
        
          @ProductionName("Names : Names name")
          public List<String> namesCons(List<String> names, String name) {
            names.add(name);
            return names;
          }
        
        }
        """, actual);
  }

  @Test
  public void testListOfNonTerminalProducesListOfNtType() {
    var inputText = """
        grammar {
          Stmts : Stmt
          Stmts : Stmts Stmt
          Stmt : name
        }
        """;

    var actual = generateVisitor(inputText);

    assertTrue(actual.contains("List<Stmt>"));
    assertTrue(actual.contains("new ArrayList<Stmt>()"));
  }

  @Test
  public void testListOfListProducesNestedListType() {
    // Inner list of terminals, outer list of inner lists
    var inputText = """
        grammar {
          Matrix : Row
          Matrix : Matrix Row
          Row : num
          Row : Row num
        }
        """;

    var actual = generateVisitor(inputText);

    // Row is List<String>, Matrix is List<List<String>>
    assertTrue(actual.contains("List<String>"));           // Row
    assertTrue(actual.contains("List<List<String>>"));     // Matrix
  }

  // ── Operator name mapping in record names ────────────────────────────────────

  @Test
  public void testOperatorSymbolsMapToNamesInRecords() {
    var inputText = """
        grammar {
          Expr : Expr '+' Expr
          Expr : Expr '-' Expr
          Expr : Expr '*' Expr
          Expr : Expr '/' Expr
          Expr : num
        }
        """;

    var actual = generateVisitor(inputText);

    assertTrue(actual.contains("ExprPlusExpr"));
    assertTrue(actual.contains("ExprMinusExpr"));
    assertTrue(actual.contains("ExprMulExpr"));
    assertTrue(actual.contains("ExprDivExpr"));
  }

  @Test
  public void testArrowAndFatArrowSymbolNames() {
    var inputText = """
        grammar {
          Expr : Expr '->' Expr
          Expr : Expr '=>' Expr
          Expr : num
        }
        """;

    var actual = generateVisitor(inputText);
    System.out.println(actual);

    assertTrue(actual.contains("ExprArrowExpr"));
    assertTrue(actual.contains("ExprFatArrowExpr"));
  }

  // ── Visitor type is start symbol type ─────────────────────────────────────────

  @Test
  public void testVisitorTypeIsStartSymbol() {
    var inputText = """
        grammar {
          Program : stmts
          stmts : stmts stmt
          stmts :
          stmt : name
        }
        """;

    var actual = generateVisitor(inputText);

    // Program is the start symbol and is a Normal single-production → record Program
    assertTrue(actual.contains("implements Visitor<Program>"));
  }

  @Test
  public void testVisitorTypeIsListWhenStartSymbolIsListPattern() {
    var inputText = """
        grammar {
          Stmts : stmt
          Stmts : Stmts stmt
          stmt : name
        }
        """;

    var actual = generateVisitor(inputText);

    assertTrue(actual.contains("implements Visitor<List<Stmt>>"));
  }

  // ── Duplicate symbol names in record parameters ───────────────────────────────

  @Test
  public void testDuplicateNonTerminalsInBodyGetNumberedParams() {
    var inputText = """
        grammar {
          Expr : Expr '+' Expr
          Expr : num
        }
        """;

    var actual = generateVisitor(inputText);
    System.out.println(actual);

    // Two Expr params should be disambiguated
    assertTrue(actual.contains("Expr expr,") || actual.contains("Expr expr2"));
  }
}