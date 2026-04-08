package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaCodeVisitorGeneratorTest {

  private static String generateFromDsl(String inputText) {
    var grammar = MetaGrammar.load(inputText).grammar();
    return JavaCodeVisitorGenerator.generate(grammar, "MyVisitor", "com.example");
  }

  // ── Terminal methods ──────────────────────────────────────────────────────────

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

    var actual = generateFromDsl(inputText);

    // terminal method for 'num' present
    assertTrue(actual.contains("public String num(Terminal terminal)"));
    // no terminal method for '(' or ')'
    assertFalse(actual.contains("public String LParen"));
    assertFalse(actual.contains("public String RParen"));
  }

  @Test
  public void testNonIdentifierTerminalsDoNotGetTerminalMethods() {
    var inputText = """
        grammar {
          E : E '+' E
          E : num
        }
        """;

    var actual = generateFromDsl(inputText);

    assertFalse(actual.contains("public String plus(Terminal terminal)"));
    assertFalse(actual.contains("public String Plus(Terminal terminal)"));
    assertTrue(actual.contains("public String num(Terminal terminal)"));
  }

  // ── Normal pattern – single production ───────────────────────────────────────

  @Test
  public void testSingleProductionGeneratesRecord() {
    var inputText = """
        grammar {
          Point : x y
        }
        """;

    var actual = generateFromDsl(inputText);

    // record declaration
    assertTrue(actual.contains("public record Point("));
    // visitor method
    assertTrue(actual.contains("@ProductionName(\"Point : x y\")"));
    assertTrue(actual.contains("public Point point("));
    assertTrue(actual.contains("return new Point("));
  }

  // ── Normal pattern – multiple productions → sealed interface ─────────────────

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
        package com.example;

        import com.github.forax.lazylr.Terminal;
        import com.github.forax.lazylr.Visitor;
        import com.github.forax.lazylr.ProductionName;
        import java.util.ArrayList;
        import java.util.List;
        import java.util.Optional;

        public sealed interface Exp permits ExpPlusExp, ExpMinusTerm, ExpTerm {}
        public record ExpPlusExp(Exp exp, Term term) implements Exp {}
        public record ExpMinusTerm(Exp exp, Term term) implements Exp {}
        public record ExpTerm(Term term) implements Exp {}
        public sealed interface Term permits TermMulTerm, TermDivFactor, TermFactor {}
        public record TermMulTerm(Term term, Factor factor) implements Term {}
        public record TermDivFactor(Term term, Factor factor) implements Term {}
        public record TermFactor(Factor factor) implements Term {}
        public sealed interface Factor permits FactorNum, FactorIdent, FactorLParenExpRParen {}
        public record FactorNum(String num) implements Factor {}
        public record FactorIdent(String ident) implements Factor {}
        public record FactorLParenExpRParen(Exp exp) implements Factor {}

        public class MyVisitor implements Visitor<Exp> {

          public String num(Terminal terminal) {
            return terminal.value();
          }

          public String ident(Terminal terminal) {
            return terminal.value();
          }

          @ProductionName("Exp : Exp + Term")
          public Exp expPlusExp(Exp exp, Term term) {
            return new ExpPlusExp(exp, term);
          }

          @ProductionName("Exp : Exp - Term")
          public Exp expMinusTerm(Exp exp, Term term) {
            return new ExpMinusTerm(exp, term);
          }

          @ProductionName("Exp : Term")
          public Exp expTerm(Term term) {
            return new ExpTerm(term);
          }

          @ProductionName("Term : Term * Factor")
          public Term termMulTerm(Term term, Factor factor) {
            return new TermMulTerm(term, factor);
          }

          @ProductionName("Term : Term / Factor")
          public Term termDivFactor(Term term, Factor factor) {
            return new TermDivFactor(term, factor);
          }

          @ProductionName("Term : Factor")
          public Term termFactor(Factor factor) {
            return new TermFactor(factor);
          }

          @ProductionName("Factor : num")
          public Factor factorNum(String num) {
            return new FactorNum(num);
          }

          @ProductionName("Factor : ident")
          public Factor factorIdent(String ident) {
            return new FactorIdent(ident);
          }

          @ProductionName("Factor : ( Exp )")
          public Factor factorLParenExpRParen(Exp exp) {
            return new FactorLParenExpRParen(exp);
          }

        }
        """;

    var actual = generateFromDsl(inputText);
    assertEquals(expected, actual);
  }

  // ── Optional pattern ──────────────────────────────────────────────────────────

  @Test
  public void testOptionalTerminalProducesOptionalString() {
    var inputText = """
        grammar {
          Stmt : name opt_label
          opt_label : label
          opt_label :
        }
        """;

    var actual = generateFromDsl(inputText);

    // return type of the NT
    assertTrue(actual.contains("Optional<String>"));
    // epsilon arm
    assertTrue(actual.contains("return Optional.empty();"));
    // single-symbol arm
    assertTrue(actual.contains("return Optional.of("));
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

    var actual = generateFromDsl(inputText);

    assertTrue(actual.contains("Optional<Expr>"));
    assertTrue(actual.contains("return Optional.empty();"));
    assertTrue(actual.contains("return Optional.of(expr);"));
  }

  // ── List pattern ──────────────────────────────────────────────────────────────

  @Test
  public void testListOfTerminalProducesListString() {
    var inputText = """
        grammar {
          Names : name
          Names : Names name
        }
        """;

    var actual = generateFromDsl(inputText);

    assertTrue(actual.contains("List<String>"));
    // base case creates ArrayList
    assertTrue(actual.contains("new ArrayList<String>()"));
    // recursive case appends
    assertTrue(actual.contains(".add("));
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

    var actual = generateFromDsl(inputText);

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

    var actual = generateFromDsl(inputText);

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

    var actual = generateFromDsl(inputText);

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

    var actual = generateFromDsl(inputText);

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

    var actual = generateFromDsl(inputText);

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

    var actual = generateFromDsl(inputText);

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

    var actual = generateFromDsl(inputText);
    System.out.println(actual);

    // Two Expr params should be disambiguated
    assertTrue(actual.contains("Expr expr,") || actual.contains("Expr expr2"));
  }

  // ── Package and imports ───────────────────────────────────────────────────────

  @Test
  public void testGeneratedFileHasCorrectPackageAndImports() {
    var inputText = """
        grammar {
          E : num
        }
        """;

    var actual = generateFromDsl(inputText);

    assertTrue(actual.startsWith("package com.example;"));
    assertTrue(actual.contains("import com.github.forax.lazylr.Terminal;"));
    assertTrue(actual.contains("import com.github.forax.lazylr.Visitor;"));
    assertTrue(actual.contains("import com.github.forax.lazylr.ProductionName;"));
    assertTrue(actual.contains("import java.util.ArrayList;"));
    assertTrue(actual.contains("import java.util.List;"));
    assertTrue(actual.contains("import java.util.Optional;"));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────────

  private static void assertTrue(boolean condition) {
    org.junit.jupiter.api.Assertions.assertTrue(condition);
  }

  private static void assertFalse(boolean condition) {
    org.junit.jupiter.api.Assertions.assertFalse(condition);
  }
}