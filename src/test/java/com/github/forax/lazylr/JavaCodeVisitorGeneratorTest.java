package com.github.forax.lazylr;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JavaCodeVisitorGeneratorTest {

  private static String generateFromDsl(String inputText) {
    var meta = MetaGrammar.load(inputText);
    return JavaCodeVisitorGenerator.generateVisitor(meta.grammar());
  }

  /* ----------------------------------------------------------------------- */
  /* Test: Simple precedence ladder unification (Exp/Term/Factor)            */
  /* ----------------------------------------------------------------------- */
  @Test
  public void testPrecedenceLadderUnification() {
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
        import com.github.forax.lazylr.*;
        import java.util.*;
        
        sealed interface Exp permits PlusExp, MinusExp, MulTerm, DivTerm, NumFactor, IdentFactor {}
        record PlusExp(Exp exp, Exp term) implements Exp {}
        record MinusExp(Exp exp, Exp term) implements Exp {}
        record MulTerm(Exp term, Exp factor) implements Exp {}
        record DivTerm(Exp term, Exp factor) implements Exp {}
        record NumFactor(String num) implements Exp {}
        record IdentFactor(String ident) implements Exp {}
        
        class GeneratedVisitor implements Visitor<Exp> {
        
          public String num(Terminal terminal) {
            return terminal.value();
          }
        
          public String ident(Terminal terminal) {
            return terminal.value();
          }
        
          @ProductionName("Exp : Exp + Term")
          public Exp plusExp(Exp exp, Exp term) {
            return new PlusExp(exp, term);
          }
        
          @ProductionName("Exp : Exp - Term")
          public Exp minusExp(Exp exp, Exp term) {
            return new MinusExp(exp, term);
          }
        
          @ProductionName("Term : Term * Factor")
          public Exp mulTerm(Exp term, Exp factor) {
            return new MulTerm(term, factor);
          }
        
          @ProductionName("Term : Term / Factor")
          public Exp divTerm(Exp term, Exp factor) {
            return new DivTerm(term, factor);
          }
        
          @ProductionName("Factor : num")
          public Exp numFactor(String num) {
            return new NumFactor(num);
          }
        
          @ProductionName("Factor : ident")
          public Exp identFactor(String ident) {
            return new IdentFactor(ident);
          }
        
          @ProductionName("Factor : ( Exp )")
          public Exp lParenFactor(Exp exp) {
            return exp;
          }
        
        }
        """;

    var actual = generateFromDsl(inputText);
    assertEquals(expected, actual);
  }

    // ---------------------------------------------------------------------------
    // 1. Single terminal leaf
    //    Exp : num
    // ---------------------------------------------------------------------------

    @Test
    public void singleTerminalLeaf() {
      var code = generateFromDsl("""
        grammar {
          Exp : num
        }
        """);
      assertEquals("""
       import com.github.forax.lazylr.*;
       import java.util.*;

       class GeneratedVisitor implements Visitor<Num> {
       
         public String num(Terminal terminal) {
           return terminal.value();
         }
       
       }
       """, code);
    }

    @Test
    @Disabled
    public void twoAlternatives() {
      var code = generateFromDsl("""
        grammar {
          Exp : num
          Exp : ident
        }
        """);
      assertEquals("""
        import com.github.forax.lazylr.*;
        import java.util.*;

        sealed interface Exp permits NumExp, IdentExp {}
        record NumExp(String num) implements Exp {}
        record IdentExp(String ident) implements Exp {}

        class GeneratedVisitor implements Visitor<Exp> {

          @ProductionName("Exp : num")
          public Exp numExp(String num) {
            return new NumExp(num);
          }

          @ProductionName("Exp : ident")
          public Exp identExp(String ident) {
            return new IdentExp(ident);
          }

          public String num(Terminal terminal) {
            return terminal.value();
          }

          public String ident(Terminal terminal) {
            return terminal.value();
          }

        }
        """, code);
    }

    @Test
    @Disabled
    public void simpleBinaryOperator() {
      var code = generateFromDsl("""
        grammar {
          Exp : Exp '+' Exp
          Exp : num
        }
        """);
      assertEquals("""
        import com.github.forax.lazylr.*;
        import java.util.*;

        sealed interface Exp permits PlusExp, NumExp {}
        record PlusExp(Exp exp, Exp exp2) implements Exp {}
        record NumExp(String num) implements Exp {}

        class GeneratedVisitor implements Visitor<Exp> {

          @ProductionName("Exp : Exp + Exp")
          public Exp plusExp(Exp exp, Exp exp2) {
            return new PlusExp(exp, exp2);
          }

          @ProductionName("Exp : num")
          public Exp numExp(String num) {
            return new NumExp(num);
          }

          public String num(Terminal terminal) {
            return terminal.value();
          }

        }
        """, code);
    }

    @Test
    public void transparentParenWrapper() {
      var code = generateFromDsl("""
        grammar {
          Exp : Exp '+' Exp
          Exp : '(' Exp ')'
          Exp : num
        }
        """);
      assertEquals("""
        import com.github.forax.lazylr.*;
        import java.util.*;

        sealed interface Exp permits PlusExp, NumExp {}
        record PlusExp(Exp exp, Exp exp2) implements Exp {}
        record NumExp(String num) implements Exp {}

        class GeneratedVisitor implements Visitor<Exp> {
        
          public String num(Terminal terminal) {
            return terminal.value();
          }

          @ProductionName("Exp : Exp + Exp")
          public Exp plusExp(Exp exp, Exp exp2) {
            return new PlusExp(exp, exp2);
          }

          @ProductionName("Exp : ( Exp )")
          public Exp lParenExp(Exp exp) {
            return exp;
          }

          @ProductionName("Exp : num")
          public Exp numExp(String num) {
            return new NumExp(num);
          }

        }
        """, code);
    }

    @Test
    public void precedenceLadderUnification() {
      var code = generateFromDsl("""
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
        """);
      assertEquals("""
        import com.github.forax.lazylr.*;
        import java.util.*;

        sealed interface Exp permits PlusExp, MinusExp, MulTerm, DivTerm, NumFactor, IdentFactor {}
        record PlusExp(Exp exp, Exp term) implements Exp {}
        record MinusExp(Exp exp, Exp term) implements Exp {}
        record MulTerm(Exp term, Exp factor) implements Exp {}
        record DivTerm(Exp term, Exp factor) implements Exp {}
        record NumFactor(String num) implements Exp {}
        record IdentFactor(String ident) implements Exp {}

        class GeneratedVisitor implements Visitor<Exp> {
        
          public String num(Terminal terminal) {
            return terminal.value();
          }
        
          public String ident(Terminal terminal) {
            return terminal.value();
          }

          @ProductionName("Exp : Exp + Term")
          public Exp plusExp(Exp exp, Exp term) {
            return new PlusExp(exp, term);
          }

          @ProductionName("Exp : Exp - Term")
          public Exp minusExp(Exp exp, Exp term) {
            return new MinusExp(exp, term);
          }

          @ProductionName("Term : Term * Factor")
          public Exp mulTerm(Exp term, Exp factor) {
            return new MulTerm(term, factor);
          }

          @ProductionName("Term : Term / Factor")
          public Exp divTerm(Exp term, Exp factor) {
            return new DivTerm(term, factor);
          }

          @ProductionName("Factor : num")
          public Exp numFactor(String num) {
            return new NumFactor(num);
          }

          @ProductionName("Factor : ident")
          public Exp identFactor(String ident) {
            return new IdentFactor(ident);
          }

          @ProductionName("Factor : ( Exp )")
          public Exp lParenFactor(Exp exp) {
            return exp;
          }

        }
        """, code);
    }

    @Test
    @Disabled
    public void optionalWrapper() {
      var code = generateFromDsl("""
        grammar {
          Stmt : Label ';'
          Label :
          Label : ident
        }
        """);

      assertEquals("""
          import com.github.forax.lazylr.*;
          import java.util.*;
          
          sealed interface Stmt permits LabelStmt {}
          record LabelStmt(Optional<Ident> label) implements Stmt {}
          
          class GeneratedVisitor implements Visitor<Stmt> {
          
            public String ident(Terminal terminal) {
              return terminal.value();
            }
          
            @ProductionName("Stmt : Label ;")
            public Stmt labelStmt(Optional<Ident> label) {
              return new LabelStmt(label);
            }
          
            @ProductionName("Label : ε")
            public Optional<Ident> emptyLabel() {
              return Optional.empty();
            }
          
            @ProductionName("Label : ident")
            public Optional<Ident> identLabel(String ident) {
              return Optional.of(ident);
            }
          }
          """, code);
    }

    @Test
    @Disabled
    public void listPattern() {
      var code = generateFromDsl("""
        grammar {
          Args : Args ident
          Args : ident
        }
        """);

      assertEquals("""
          import com.github.forax.lazylr.*;
          import java.util.*;
          
          class GeneratedVisitor implements Visitor<List<Ident>> {
          
            public String ident(Terminal terminal) {
              return terminal.value();
            }
          
            @ProductionName("Args : Args ident")
            public List<Ident> identArgsArgs(List<Ident> args, String ident) {
              args.add(ident);
              return args;
            }
          
            @ProductionName("Args : ident")
            public List<Ident> identArgs(String ident) {
              var identList = new ArrayList<Ident>();
              identList.add(ident);
              return identList;
            }
          
          }
          """, code);
    }

    @Test
    public void knownOperatorSymbolNames() {
      var code = generateFromDsl("""
        grammar {
          Exp : Exp '==' Exp
          Exp : Exp '!=' Exp
          Exp : Exp '<=' Exp
          Exp : Exp '>=' Exp
          Exp : Exp '->' Exp
          Exp : num
        }
        """);

      assertTrue(code.contains("EqExp"),      "== should map to Eq");
      assertTrue(code.contains("NeExp"),      "!= should map to Ne");
      assertTrue(code.contains("LeExp"),      "<= should map to Le");
      assertTrue(code.contains("GeExp"),      ">= should map to Ge");
      assertTrue(code.contains("ArrowExp"),   "-> should map to Arrow");
    }

    @Test
    @Disabled
    public void quotedPunctuationNoTerminalMethod() {
      var code = generateFromDsl("""
        grammar {
          Exp : Exp '+' Exp
          Exp : num
        }
        """);

      assertEquals("""
          import com.github.forax.lazylr.*;
          import java.util.*;
          
          sealed interface Exp permits PlusExp, NumExp {}
          record PlusExp(Exp exp, Exp exp2) implements Exp {}
          record NumExp(String num) implements Exp {}
          
          class GeneratedVisitor implements Visitor<Exp> {
          
            public String num(Terminal terminal) {
              return terminal.value();
            }
          
            @ProductionName("Exp : Exp + Exp")
            public Exp plusExp(Exp exp, Exp exp2) {
              return new PlusExp(exp, exp2);
            }
          
            @ProductionName("Exp : Num")
            public Exp numExp(String num) {
              return new NumExp(num);
            }
          }
          """, code);
    }

    @Test
    public void ladderPassThroughsProduceNoMethod() {
      var code = generateFromDsl("""
        grammar {
          Exp  : Exp '+' Term
          Exp  : Term
          Term : Term '*' Factor
          Term : Factor
          Factor : num
        }
        """);

      assertEquals("""
          import com.github.forax.lazylr.*;
          import java.util.*;
          
          sealed interface Exp permits PlusExp, MulTerm, FactorTerm {}
          record PlusExp(Exp exp, Exp term) implements Exp {}
          record MulTerm(Exp term, Num factor) implements Exp {}
          record FactorTerm(Num factor) implements Exp {}
          
          class GeneratedVisitor implements Visitor<Exp> {
          
            public String num(Terminal terminal) {
              return terminal.value();
            }
          
            @ProductionName("Exp : Exp + Term")
            public Exp plusExp(Exp exp, Exp term) {
              return new PlusExp(exp, term);
            }
          
            @ProductionName("Term : Term * Factor")
            public Exp mulTerm(Exp term, Num factor) {
              return new MulTerm(term, factor);
            }
          
            @ProductionName("Term : Factor")
            public Exp factorTerm(Num factor) {
              return new FactorTerm(factor);
            }
          
          }
          """, code);
    }

  /*
  @Test
  public void testOptionalPattern() {
    var inputText = """
        grammar {
          MaybeExpr : Expr
          MaybeExpr :
          Expr : ident
        }
        """;

    var expected = """
        import java.util.*;
        import com.github.forax.lazylr.*;
        
        @SuppressWarnings("unchecked")
        public class GeneratedVisitor implements Visitor<Object> {
        @Override
        public Object ident(Terminal t) {
            return t.value();
        }
        // Type alias: MaybeExpr -> Optional<Expr>
        public sealed interface Expr permits Expr_ident {}
        
        public record Expr_ident(String arg0) implements Expr {}
        
            // --- Production Handlers ---
            @Override
            @ProductionName("MaybeExpr : Expr")
            public Object MaybeExpr_handler(Object arg0) {
                return arg0 == null ? Optional.empty() : Optional.of(arg0);
            }
            @Override
            @ProductionName("MaybeExpr : ε")
            public Object MaybeExpr_handler() {
                return Optional.empty();
            }
            @Override
            @ProductionName("Expr : ident")
            public Object Expr_ident(Expr_ident arg0) {
                return new Expr_ident(arg0);
            }
        }""";

    var actual = generateFromDsl(inputText);
    assertEquals(expected.strip(), actual.strip());
  }

  @Test
  public void testListPlusPattern() {
    var inputText = """
        grammar {
          StmtList : StmtList Stmt
          StmtList : Stmt
          Stmt : stmt
        }
        """;

    var expected = """
        import java.util.*;
        import com.github.forax.lazylr.*;
        
        @SuppressWarnings("unchecked")
        public class GeneratedVisitor implements Visitor<Object> {
        @Override
        public Object stmt(Terminal t) {
            return t.value();
        }
        // Type alias: StmtList -> List<Stmt>
        public sealed interface Stmt permits Stmt_stmt {}
        
        public record Stmt_stmt(String arg0) implements Stmt {}
        
            // --- Production Handlers ---
            @Override
            @ProductionName("StmtList : StmtList Stmt")
            public Object StmtList_handler(Object arg0) {
                return List.of(arg0);
            }
            @Override
            @ProductionName("StmtList : Stmt")
            public Object StmtList_handler(List<Object> acc, Object arg1) {
                var list = new ArrayList<>(acc);
                list.add(arg1);
                return list;
            }
            @Override
            @ProductionName("Stmt : stmt")
            public Object Stmt_stmt(Stmt_stmt arg0) {
                return new Stmt_stmt(arg0);
            }
        }""";

    var actual = generateFromDsl(inputText);
    assertEquals(expected.strip(), actual.strip());
  }

  @Test
  public void testPassThroughParentheses() {
    var inputText = """
        grammar {
          Expr : Expr
          Expr : '(' Expr ')'
          Expr : num
        }
        """;

    var expected = """
        import java.util.*;
        import com.github.forax.lazylr.*;
        
        @SuppressWarnings("unchecked")
        public class GeneratedVisitor implements Visitor<Object> {
        @Override
        public Object num(Terminal t) {
            return t.value();
        }
        public sealed interface Expr permits Expr_num {}
        
        public record Expr_num(String arg0) implements Expr {}
        
            // --- Production Handlers ---
            @Override
            @ProductionName("Expr : Expr")
            public Object Expr_Expr(Expr arg0) {
                return arg0;
            }
            @Override
            @ProductionName("Expr : ( Expr )")
            public Object Expr____Expr__(Expr arg0) {
                return arg0;
            }
            @Override
            @ProductionName("Expr : num")
            public Object Expr_num(Expr_num arg0) {
                return new Expr_num(arg0);
            }
        }""";

    var actual = generateFromDsl(inputText);
    assertEquals(expected.strip(), actual.strip());
  }

  @Test
  public void testQuotedTerminalFiltering() {
    var inputText = """
        grammar {
          Call : id '(' Args ')'
          Args : Args ',' id
          Args : id
          Args :
        }
        """;

    var expected = """
        import java.util.*;
        import com.github.forax.lazylr.*;
        
        @SuppressWarnings("unchecked")
        public class GeneratedVisitor implements Visitor<Object> {
        @Override
        public Object id(Terminal t) {
            return t.value();
        }
        // Type alias: Args -> List<String>
        public sealed interface Call permits Call_id_____Args__ {}
        
        public record Call_id_____Args__(String arg0, Args arg1) implements Call {}
        
            // --- Production Handlers ---
            @Override
            @ProductionName("Call : id ( Args )")
            public Object Call_id_____Args__(Call_id_____Args__ arg0) {
                return new Call_id_____Args__(arg0);
            }
            @Override
            @ProductionName("Args : Args , id")
            public Object Args_handler(Object arg0) {
                return List.of(arg0);
            }
            @Override
            @ProductionName("Args : id")
            public Object Args_handler(List<Object> acc, Object arg1) {
                var list = new ArrayList<>(acc);
                list.add(arg1);
                return list;
            }
            @Override
            @ProductionName("Args : ε")
            public Object Args_handler() {
                return List.of();
            }
        }""";

    var actual = generateFromDsl(inputText);
    assertEquals(expected.strip(), actual.strip());
  }

  @Test
  public void testComplexExpressionGrammar() {
    var inputText = """
        precedence {
          right: '='
          left: '||'
          left: '&&'
          left: '==', '!='
          left: '<', '<=', '>', '>='
          left: '+', '-'
          left: '*', '/', '%'
          right: '!', '-'
        }
        grammar {
          Expr : Expr '=' Expr
          Expr : Expr '||' Expr
          Expr : Expr '&&' Expr
          Expr : Expr '==' Expr
          Expr : Expr '!=' Expr
          Expr : Expr '<' Expr
          Expr : Expr '<=' Expr
          Expr : Expr '>' Expr
          Expr : Expr '>=' Expr
          Expr : Expr '+' Expr
          Expr : Expr '-' Expr
          Expr : Expr '*' Expr
          Expr : Expr '/' Expr
          Expr : Expr '%' Expr
          Expr : '!' Expr
          Expr : '-' Expr
          Expr : Primary
          Primary : id
          Primary : num
          Primary : '(' Expr ')'
          Primary : id '(' Args ')'
          Args : Args ',' Expr
          Args : Expr
          Args :
        }
        """;

    // We test that the output contains key structural elements
    var actual = generateFromDsl(inputText);

    // Should unify all Expr/Primary into one type
    assertTrue(actual.contains("// Type alias: Expr -> List<"));
    assertTrue(actual.contains("// Type alias: Primary -> List<"));

    // Should generate pass-through for parenthesized expression
    assertTrue(actual.contains("@ProductionName(\"Primary : ( Expr )\")"));
    assertTrue(actual.contains("return arg0;"));

    // Should filter quoted operators from parameters
    assertFalse(actual.contains("String arg0, String arg1, String arg2")); // shouldn't have operator params
    assertTrue(actual.contains("Expr arg0, Expr arg1")); // should have Expr params

    // Should generate sealed interface for non-unified types
    assertTrue(actual.contains("public sealed interface") || actual.contains("// Type alias:"));
  }

  @Test
  public void testEpsilonOnlyGrammar() {
    var inputText = """
        grammar {
          Empty :
          Empty : Empty
        }
        """;

    var actual = generateFromDsl(inputText);

    // Should generate minimal visitor with no terminal methods
    assertTrue(actual.contains("public class GeneratedVisitor implements Visitor<Object>"));
    // Empty productions should be handled gracefully
    assertTrue(actual.contains("@ProductionName(\"Empty : ε\")") || actual.contains("Optional.empty()"));
  }

  @Test
  public void testStructuralUnification() {
    var inputText = """
        grammar {
          A : id
          A : B
          B : id
          B : A
          C : '(' A ')'
          C : '(' B ')'
        }
        """;

    var actual = generateFromDsl(inputText);

    // A and B should be unified (they reference each other)
    assertTrue(actual.contains("// Type alias: A ->") || actual.contains("// Type alias: B ->"));

    // C should have pass-through methods for both productions
    assertTrue(actual.contains("@ProductionName(\"C : ( A )\")"));
    assertTrue(actual.contains("@ProductionName(\"C : ( B )\")"));
  }

  @Test
  public void testRecordNameSanitization() {
    var inputText = """
        grammar {
          Expr : id '+' id
        }
        """;

    var actual = generateFromDsl(inputText);

    // Record name should not contain spaces or special chars
    assertTrue(actual.contains("public record Expr_id___id("));
    // Method name should also be sanitized
    assertTrue(actual.contains("public Object Expr_id___id("));
  }

  @Test
  public void testTerminalValueExtraction() {
    var inputText = """
        grammar {
          Literal : number
          Literal : string
        }
        """;

    var actual = generateFromDsl(inputText);

    // Should generate methods for both terminals
    assertTrue(actual.contains("public Object number(Terminal t)"));
    assertTrue(actual.contains("return t.value();"));
    assertTrue(actual.contains("public Object string(Terminal t)"));

    // Should not generate duplicate methods for same terminal name
    var count = actual.split("public Object number\\(Terminal t\\)").length - 1;
    assertEquals(1, count, "Should generate exactly one method per terminal");
  }

  @Test
  public void testFixpointPropagation() {
    var inputText = """
        grammar {
          A : B
          B : C
          C : D
          D : D id
          D : id
        }
        """;

    var actual = generateFromDsl(inputText);

    // D should be detected as List<String>
    assertTrue(actual.contains("// Type alias: D -> List<String>"));

    // A, B, C should inherit the List type via fixpoint propagation
    assertTrue(actual.contains("// Type alias: A -> List<") || actual.contains("// Type alias: B -> List<"));
  }

  @Test
  public void testNamedTerminalsOnly() {
    var inputText = """
        grammar {
          Stmt : KEYWORD IDENT
          Stmt : KEYWORD '(' Expr ')'
          Expr : NUM
          Expr : IDENT
        }
        """;

    var actual = generateFromDsl(inputText);

    // Should generate methods for all named terminals
    assertTrue(actual.contains("public Object KEYWORD(Terminal t)"));
    assertTrue(actual.contains("public Object IDENT(Terminal t)"));
    assertTrue(actual.contains("public Object NUM(Terminal t)"));

    // Should not filter out any terminals (all are named/identifier-like)
    assertTrue(actual.contains("String arg0, String arg1") || actual.contains("String arg0"));
  }

  @Test
  public void testMixedParameterOrdering() {
    var inputText = """
        grammar {
          BinOp : id op id
        }
        """;

    var actual = generateFromDsl(inputText);

    // Should have 3 parameters: id(String), op(String), id(String)
    // But op is quoted-like (single char), so should be filtered
    // Actually 'op' matches [a-zA-Z_][a-zA-Z0-9_]* so it's NOT quoted
    // So we should have: String arg0, String arg1, String arg2
    assertTrue(actual.contains("String arg0, String arg1, String arg2") ||
               actual.contains("String arg0, String arg1"));

    // Record should implement the non-terminal interface
    assertTrue(actual.contains("implements BinOp"));
  }*/
}