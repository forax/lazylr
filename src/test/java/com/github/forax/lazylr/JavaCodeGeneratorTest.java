package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public final class JavaCodeGeneratorTest {

  @Test
  public void generateThrowsOnNullMetaGrammar() {
    assertThrows(NullPointerException.class, () -> JavaCodeGenerator.generate(null));
  }

  @Test
  public void singleNumberGrammar() {
    var mg = MetaGrammar.create("""
        tokens {
          num: /[0-9]+/
        }
        grammar {
          E: num
        }
        """);

    var code = JavaCodeGenerator.generate(mg);

    assertEquals("""
        import com.github.forax.lazylr.*;
        
        public static MetaGrammar createGrammar() {
          // Non-terminals
          var nt_E = new NonTerminal("E");
        
          // Terminals
          var t_num = new Terminal("num");
        
          // Productions
          var p0 = new Production(nt_E, List.of(t_num));
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p0));
        
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
          LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), System.err::println);
        }
        """, code);
  }

  @Test
  public void epsilonProduction() {
    var mg = MetaGrammar.create("""
        tokens {
          num: /[0-9]+/
        }
        grammar {
          E: num
          E:
        }
        """);

    var code = JavaCodeGenerator.generate(mg);

    assertEquals("""
        import com.github.forax.lazylr.*;
        
        public static MetaGrammar createGrammar() {
          // Non-terminals
          var nt_E = new NonTerminal("E");
        
          // Terminals
          var t_num = new Terminal("num");
        
          // Productions
          var p0 = new Production(nt_E, List.of(t_num));
          var p1 = new Production(nt_E, List.of());
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p0, p1));
        
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
          LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), System.err::println);
        }
        """, code);
  }

  @Test
  public void anonymousToken() {
    var mg = MetaGrammar.create("""
        tokens {
          num: /[0-9]+/
          /[ ]+/
        }
        grammar {
          E: num
        }
        """);

    var code = JavaCodeGenerator.generate(mg);

    assertEquals("""
        import com.github.forax.lazylr.*;
        
        public static MetaGrammar createGrammar() {
          // Non-terminals
          var nt_E = new NonTerminal("E");
        
          // Terminals
          var t_num = new Terminal("num");
        
          // Productions
          var p0 = new Production(nt_E, List.of(t_num));
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p0));
        
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
          LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), System.err::println);
        }
        """, code);
  }

  @Test
  public void additionLeftAssociative() {
    var mg = MetaGrammar.create("""
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

    var code = JavaCodeGenerator.generate(mg);

    assertEquals("""
        import com.github.forax.lazylr.*;
        
        public static MetaGrammar createGrammar() {
          // Non-terminals
          var nt_E = new NonTerminal("E");
        
          // Terminals
          var t_num = new Terminal("num");
          var t__ = new Terminal("+");
        
          // Productions
          var p0 = new Production(nt_E, List.of(t_num));
          var p1 = new Production(nt_E, List.of(nt_E, t__, nt_E));
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p0, p1));
        
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
          LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), System.err::println);
        }
        """, code);
  }

  @Test
  public void additionAndMultiplicationPrecedence() {
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
          E: num
          E: E '+' E
          E: E '*' E
        }
        """);

    var code = JavaCodeGenerator.generate(mg);

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
          var p0 = new Production(nt_E, List.of(t_num));
          var p1 = new Production(nt_E, List.of(nt_E, t__, nt_E));
          var p2 = new Production(nt_E, List.of(nt_E, t__1, nt_E));
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p0, p1, p2));
        
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
          LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), System.err::println);
        }
        """, code);
  }

  @Test
  public void exponentiationRightAssociative() {
    var mg = MetaGrammar.create("""
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

    var code = JavaCodeGenerator.generate(mg);

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
          var p0 = new Production(nt_E, List.of(t_num));
          var p1 = new Production(nt_E, List.of(nt_E, t__, nt_E));
          var p2 = new Production(nt_E, List.of(nt_E, t__1, nt_E));
          var p3 = new Production(nt_E, List.of(nt_E, t__2, nt_E));
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p0, p1, p2, p3));
        
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
          LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), System.err::println);
        }
        """, code);
  }

  @Test
  public void functionCallGrammar() {
    var mg = MetaGrammar.create("""
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

    var code = JavaCodeGenerator.generate(mg);

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
          var p0 = new Production(nt_E, List.of(t_num));
          var p1 = new Production(nt_E, List.of(t_sum, t__, nt_ARGS, t__1));
          var p2 = new Production(nt_ARGS, List.of(nt_E));
          var p3 = new Production(nt_ARGS, List.of(nt_ARGS, t__2, nt_E));
          var p4 = new Production(nt_ARGS, List.of());
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p0, p1, p2, p3, p4));
        
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
          LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), System.err::println);
        }
        """, code);
  }

  @Test
  public void danglingElseGrammar() {
    var mg = MetaGrammar.create("""
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

    var code = JavaCodeGenerator.generate(mg);

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
          var p0 = new Production(nt_E, List.of(t_num));
          var p1 = new Production(nt_E, List.of(nt_E, t__, nt_E));
          var p2 = new Production(nt_E, List.of(t_if, nt_E, t_then, nt_E));
          var p3 = new Production(nt_E, List.of(t_if, nt_E, t_then, nt_E, t_else, nt_E));
        
          // Grammar
          var startSymbol = nt_E;
          var grammar = new Grammar(startSymbol, List.of(p0, p1, p2, p3));
        
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
          LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), System.err::println);
        }
        """, code);
  }
}