package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public final class JavaCodeGeneratorTest {

  /// Asserts that the given generated code snippet compiles without errors.
  /// @param className simple class name used for the in-memory compilation unit.
  /// @param code      the raw output of {@link JavaCodeGenerator#generate}.
  private static void assertCompilesSuccessfully(String className, String code) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler);

    var classpath = System.getProperty("java.class.path");
    var diagnosticCollector = new DiagnosticCollector<JavaFileObject>();

    var delegate = compiler.getStandardFileManager(diagnosticCollector, null, null);
    try (var fileManager = new ForwardingJavaFileManager<>(delegate) {
      @Override
      public JavaFileObject getJavaFileForOutput(Location location,
                                                 String className,
                                                 JavaFileObject.Kind kind,
                                                 FileObject sibling) {
        var uri = URI.create("mem:///" + className + ".class");
        return new SimpleJavaFileObject(uri, JavaFileObject.Kind.CLASS) {
          @Override
          public OutputStream openOutputStream() {
            return OutputStream.nullOutputStream();
          }
        };
      }
    }) {

      var uri = URI.create("mem:///" + className + ".java");
      var source = new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
          return code;
        }
      };

      var ok = compiler
          .getTask(null, fileManager, diagnosticCollector, List.of("-cp", classpath), null, List.of(source))
          .call();
      if (!ok) {
        var diagnostics = List.copyOf(diagnosticCollector.getDiagnostics());
        var errors = diagnostics.stream()
            .map(d -> "  line " + d.getLineNumber() + ": " + d.getMessage(null))
            .collect(Collectors.joining("\n"));
        var codeLines = code.lines().toList();
        var listing = IntStream.range(0, codeLines.size())
            .mapToObj(i -> String.format("%4d | %s", i + 1, codeLines.get(i)))
            .collect(Collectors.joining("\n"));
        fail("Generated code for '" + className + "' did not compile:\n" + errors
            + "\n\n--- code ---\n" + listing);
      }
    }
  }


  @Test
  @SuppressWarnings("DataFlowIssue")
  public void generateThrowsOnNullMetaGrammar() {
    assertThrows(NullPointerException.class, () -> JavaCodeGenerator.generate(null, false));
  }

  @Test
  public void singleNumberGrammar() throws IOException {
    var mg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/
        }
        grammar {
          E: num
        }
        """);

    var code = JavaCodeGenerator.generate(mg, false);

    assertCompilesSuccessfully("SingleNumberGrammar", code);
    assertEquals("""
        import com.github.forax.lazylr.*;
        
        public static MetaGrammar createGrammar() {
          // Non-terminals
          var nt_E = new NonTerminal("E");
        
          // Terminals
          var t_num = new Terminal("num");
        
          // Productions
          var p_0 = new Production(nt_E, List.of(t_num));
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p_0));
        
          // Tokens
          var tokens = List.of(
            new Token("num", "[0-9]+")
          );
        
          // Precedence map
          var precedenceMap = Map.<PrecedenceEntity, Precedence>of();
        
          return new MetaGrammar(tokens, precedenceMap, grammar);
        }
        
        static void main() {
          var mg = createGrammar();
          mg.verify();
        }
        """, code);
  }

  @Test
  public void epsilonProduction() throws IOException {
    var mg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/
        }
        grammar {
          E: num
          E:
        }
        """);

    var code = JavaCodeGenerator.generate(mg, false);

    assertCompilesSuccessfully("EpsilonProduction", code);
    assertEquals("""
        import com.github.forax.lazylr.*;
        
        public static MetaGrammar createGrammar() {
          // Non-terminals
          var nt_E = new NonTerminal("E");
        
          // Terminals
          var t_num = new Terminal("num");
        
          // Productions
          var p_0 = new Production(nt_E, List.of(t_num));
          var p_1 = new Production(nt_E, List.of());
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p_0, p_1));
        
          // Tokens
          var tokens = List.of(
            new Token("num", "[0-9]+")
          );
        
          // Precedence map
          var precedenceMap = Map.<PrecedenceEntity, Precedence>of();
        
          return new MetaGrammar(tokens, precedenceMap, grammar);
        }
        
        static void main() {
          var mg = createGrammar();
          mg.verify();
        }
        """, code);
  }

  @Test
  public void unnamedToken() throws IOException {
    var mg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/
          /[ ]+/
        }
        grammar {
          E: num
        }
        """);

    var code = JavaCodeGenerator.generate(mg, false);
    assertCompilesSuccessfully("UnnamedToken", code);

    assertEquals("""
        import com.github.forax.lazylr.*;
        
        public static MetaGrammar createGrammar() {
          // Non-terminals
          var nt_E = new NonTerminal("E");
        
          // Terminals
          var t_num = new Terminal("num");
        
          // Productions
          var p_0 = new Production(nt_E, List.of(t_num));
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p_0));
        
          // Tokens
          var tokens = List.of(
            new Token("num", "[0-9]+"),
            new Token("[ ]+")
          );
        
          // Precedence map
          var precedenceMap = Map.<PrecedenceEntity, Precedence>of();
        
          return new MetaGrammar(tokens, precedenceMap, grammar);
        }
        
        static void main() {
          var mg = createGrammar();
          mg.verify();
        }
        """, code);
  }

  @Test
  public void additionLeftAssociative() throws IOException {
    var mg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/
          /[ ]+/
        }
        precedence {
          left: '+'
        }
        grammar {
          E: num
          E: E '+' E
        }
        """);

    var code = JavaCodeGenerator.generate(mg, false);

    assertCompilesSuccessfully("AdditionLeftAssociative", code);
    assertEquals("""
        import com.github.forax.lazylr.*;
        
        public static MetaGrammar createGrammar() {
          // Non-terminals
          var nt_E = new NonTerminal("E");
        
          // Terminals
          var t_num = new Terminal("num");
          var t__ = new Terminal("+");
        
          // Productions
          var p_0 = new Production(nt_E, List.of(t_num));
          var p_1 = new Production(nt_E, List.of(nt_E, t__, nt_E));
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p_0, p_1));
        
          // Tokens
          var tokens = List.of(
            new Token("+", Pattern.quote("+")),
            new Token("num", "[0-9]+"),
            new Token("[ ]+")
          );
        
          // Precedence map
          var precedenceMap = new LinkedHashMap<PrecedenceEntity, Precedence>();
          precedenceMap.put(t__, new Precedence(1, Precedence.Associativity.LEFT));
        
          return new MetaGrammar(tokens, precedenceMap, grammar);
        }
        
        static void main() {
          var mg = createGrammar();
          mg.verify();
        }
        """, code);
  }

  @Test
  public void additionAndMultiplicationPrecedence() throws IOException {
    var mg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/
          /[ ]+/
        }
        precedence {
          left: '+'
          left: '*'
        }
        grammar {
          E: num
          E: E '+' E
          E: E '*' E
        }
        """);

    var code = JavaCodeGenerator.generate(mg, false);

    assertCompilesSuccessfully("AdditionAndMultiplicationPrecedence", code);
    assertEquals("""
        import com.github.forax.lazylr.*;
        
        public static MetaGrammar createGrammar() {
          // Non-terminals
          var nt_E = new NonTerminal("E");
        
          // Terminals
          var t_num = new Terminal("num");
          var t__ = new Terminal("+");
          var t__1 = new Terminal("*");
        
          // Productions
          var p_0 = new Production(nt_E, List.of(t_num));
          var p_1 = new Production(nt_E, List.of(nt_E, t__, nt_E));
          var p_2 = new Production(nt_E, List.of(nt_E, t__1, nt_E));
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p_0, p_1, p_2));
        
          // Tokens
          var tokens = List.of(
            new Token("+", Pattern.quote("+")),
            new Token("*", Pattern.quote("*")),
            new Token("num", "[0-9]+"),
            new Token("[ ]+")
          );
        
          // Precedence map
          var precedenceMap = new LinkedHashMap<PrecedenceEntity, Precedence>();
          precedenceMap.put(t__, new Precedence(1, Precedence.Associativity.LEFT));
          precedenceMap.put(t__1, new Precedence(2, Precedence.Associativity.LEFT));
        
          return new MetaGrammar(tokens, precedenceMap, grammar);
        }
        
        static void main() {
          var mg = createGrammar();
          mg.verify();
        }
        """, code);
  }

  @Test
  public void exponentiationRightAssociative() throws IOException {
    var mg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/
          /[ ]+/
        }
        precedence {
          left:  '+'
          left:  '*'
          right: '^'
        }
        grammar {
          E: num
          E: E '+' E
          E: E '*' E
          E: E '^' E
        }
        """);

    var code = JavaCodeGenerator.generate(mg, false);

    assertCompilesSuccessfully("ExponentiationRightAssociative", code);
    assertEquals("""
        import com.github.forax.lazylr.*;
        
        public static MetaGrammar createGrammar() {
          // Non-terminals
          var nt_E = new NonTerminal("E");
        
          // Terminals
          var t_num = new Terminal("num");
          var t__ = new Terminal("+");
          var t__1 = new Terminal("*");
          var t__2 = new Terminal("^");
        
          // Productions
          var p_0 = new Production(nt_E, List.of(t_num));
          var p_1 = new Production(nt_E, List.of(nt_E, t__, nt_E));
          var p_2 = new Production(nt_E, List.of(nt_E, t__1, nt_E));
          var p_3 = new Production(nt_E, List.of(nt_E, t__2, nt_E));
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p_0, p_1, p_2, p_3));
        
          // Tokens
          var tokens = List.of(
            new Token("+", Pattern.quote("+")),
            new Token("*", Pattern.quote("*")),
            new Token("^", Pattern.quote("^")),
            new Token("num", "[0-9]+"),
            new Token("[ ]+")
          );
        
          // Precedence map
          var precedenceMap = new LinkedHashMap<PrecedenceEntity, Precedence>();
          precedenceMap.put(t__, new Precedence(1, Precedence.Associativity.LEFT));
          precedenceMap.put(t__1, new Precedence(2, Precedence.Associativity.LEFT));
          precedenceMap.put(t__2, new Precedence(3, Precedence.Associativity.RIGHT));
        
          return new MetaGrammar(tokens, precedenceMap, grammar);
        }
        
        static void main() {
          var mg = createGrammar();
          mg.verify();
        }
        """, code);
  }

  @Test
  public void functionCallGrammar() throws IOException {
    var mg = MetaGrammar.load("""
        tokens {
          sum: /sum/
          num: /[0-9]+/
          /[ ]+/
        }
        grammar {
          E:    num
          E:    sum '(' ARGS ')'
          ARGS: E
          ARGS: ARGS ',' E
          ARGS:
        }
        """);

    var code = JavaCodeGenerator.generate(mg, false);

    assertCompilesSuccessfully("FunctionCallGrammar", code);
    assertEquals("""
        import com.github.forax.lazylr.*;
        
        public static MetaGrammar createGrammar() {
          // Non-terminals
          var nt_E = new NonTerminal("E");
          var nt_ARGS = new NonTerminal("ARGS");
        
          // Terminals
          var t_num = new Terminal("num");
          var t_sum = new Terminal("sum");
          var t__ = new Terminal("(");
          var t__1 = new Terminal(")");
          var t__2 = new Terminal(",");
        
          // Productions
          var p_0 = new Production(nt_E, List.of(t_num));
          var p_1 = new Production(nt_E, List.of(t_sum, t__, nt_ARGS, t__1));
          var p_2 = new Production(nt_ARGS, List.of(nt_E));
          var p_3 = new Production(nt_ARGS, List.of(nt_ARGS, t__2, nt_E));
          var p_4 = new Production(nt_ARGS, List.of());
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p_0, p_1, p_2, p_3, p_4));
        
          // Tokens
          var tokens = List.of(
            new Token("(", Pattern.quote("(")),
            new Token(")", Pattern.quote(")")),
            new Token(",", Pattern.quote(",")),
            new Token("sum", "sum"),
            new Token("num", "[0-9]+"),
            new Token("[ ]+")
          );
        
          // Precedence map
          var precedenceMap = Map.<PrecedenceEntity, Precedence>of();
        
          return new MetaGrammar(tokens, precedenceMap, grammar);
        }
        
        static void main() {
          var mg = createGrammar();
          mg.verify();
        }
        """, code);
  }

  @Test
  public void danglingElseGrammar() throws IOException {
    var mg = MetaGrammar.load("""
        tokens {
          if:   /if/
          then: /then/
          else: /else/
          num:  /[0-9]+/
          /[ ]+/
        }
        precedence {
          right: if
          left:  '+'
          right: else
        }
        grammar {
          E: num
          E: E '+' E
          E: if E then E
          E: if E then E else E
        }
        """);

    var code = JavaCodeGenerator.generate(mg, false);

    assertCompilesSuccessfully("DanglingElseGrammar", code);
    assertEquals("""
        import com.github.forax.lazylr.*;
        
        public static MetaGrammar createGrammar() {
          // Non-terminals
          var nt_E = new NonTerminal("E");
        
          // Terminals
          var t_num = new Terminal("num");
          var t__ = new Terminal("+");
          var t_if = new Terminal("if");
          var t_then = new Terminal("then");
          var t_else = new Terminal("else");
        
          // Productions
          var p_0 = new Production(nt_E, List.of(t_num));
          var p_1 = new Production(nt_E, List.of(nt_E, t__, nt_E));
          var p_2 = new Production(nt_E, List.of(t_if, nt_E, t_then, nt_E));
          var p_3 = new Production(nt_E, List.of(t_if, nt_E, t_then, nt_E, t_else, nt_E));
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p_0, p_1, p_2, p_3));
        
          // Tokens
          var tokens = List.of(
            new Token("+", Pattern.quote("+")),
            new Token("if", "if"),
            new Token("then", "then"),
            new Token("else", "else"),
            new Token("num", "[0-9]+"),
            new Token("[ ]+")
          );
        
          // Precedence map
          var precedenceMap = new LinkedHashMap<PrecedenceEntity, Precedence>();
          precedenceMap.put(t_if, new Precedence(1, Precedence.Associativity.RIGHT));
          precedenceMap.put(t__, new Precedence(2, Precedence.Associativity.LEFT));
          precedenceMap.put(t_else, new Precedence(3, Precedence.Associativity.RIGHT));
        
          return new MetaGrammar(tokens, precedenceMap, grammar);
        }
        
        static void main() {
          var mg = createGrammar();
          mg.verify();
        }
        """, code);
  }

  @Test
  public void generateWithVisitor() throws IOException {
    var mg = MetaGrammar.load("""
        grammar {
          Expr : num
          Expr : Expr '+' Expr
        }
        """);

    var code = JavaCodeGenerator.generate(mg, true);

    assertCompilesSuccessfully("WithVisitor", code);
    assertEquals("""
        import com.github.forax.lazylr.*;
        import java.util.*;
        
        public sealed interface Expr permits NumExpr, ExprPlusExprExpr {}
        public record NumExpr(String num) implements Expr {}
        public record ExprPlusExprExpr(Expr expr, Expr expr2) implements Expr {}
        
        class MyVisitor implements Visitor<Expr> {
        
          public String num(Terminal terminal) {
            return terminal.value();
          }
        
          @ProductionName("Expr : num")
          public Expr numExpr(String num) {
            return new NumExpr(num);
          }
        
          @ProductionName("Expr : Expr + Expr")
          public Expr exprPlusExprExpr(Expr expr, Expr expr2) {
            return new ExprPlusExprExpr(expr, expr2);
          }
        
        }
        
        public static MetaGrammar createGrammar() {
          // Non-terminals
          var nt_Expr = new NonTerminal("Expr");
        
          // Terminals
          var t_num = new Terminal("num");
          var t__ = new Terminal("+");
        
          // Productions
          var p_0 = new Production(nt_Expr, List.of(t_num));
          var p_1 = new Production(nt_Expr, List.of(nt_Expr, t__, nt_Expr));
        
          // Grammar
          var startSymbol = nt_Expr;
          var grammar = new Grammar(startSymbol, List.of(p_0, p_1));
        
          // Tokens
          var tokens = List.of(
            new Token("+", Pattern.quote("+"))
          );
        
          // Precedence map
          var precedenceMap = Map.<PrecedenceEntity, Precedence>of();
        
          return new MetaGrammar(tokens, precedenceMap, grammar);
        }
        
        static void main() {
          var mg = createGrammar();
          mg.verify();
        }
        """, code);
  }
}