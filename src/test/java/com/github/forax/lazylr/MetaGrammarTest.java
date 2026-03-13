package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.github.forax.lazylr.Precedence.Associativity.LEFT;
import static com.github.forax.lazylr.Precedence.Associativity.RIGHT;
import static org.junit.jupiter.api.Assertions.*;

public final class MetaGrammarTest {

  private static List<String> productionNames(Grammar grammar) {
    return grammar.productions().stream()
        .map(Production::name)
        .toList();
  }

  private static List<String> tokenNames(List<Token> tokens) {
    return tokens.stream()
        .map(Token::name)
        .filter(Objects::nonNull)
        .toList();
  }


  @Test
  public void minimalEpsilonGrammar() {
    var mg = MetaGrammar.load("""
        grammar {
          Empty:
        }
        """);

    var grammar = mg.grammar();
    assertEquals(
        List.of("Empty : ε"),
        productionNames(grammar));

    var tokens = mg.tokens();
    assertEquals(List.of(), tokenNames(tokens));

    var precedenceMap = mg.precedenceMap();
    assertTrue(precedenceMap.isEmpty());
  }

  @Test
  public void oneProductionGrammar() {
    var mg = MetaGrammar.load("""
        grammar {
          Expr: Expr plus Term
        }
        """);

    var grammar = mg.grammar();
    assertEquals(
        List.of("Expr : Expr plus Term"),
        productionNames(grammar));
  }

  @Test
  public void simpleExpressionGrammar() {
    var mg = MetaGrammar.load("""
        grammar {
          Expr: Expr plus Term
          Expr: Term
          Term: num
        }
        """);

    var grammar = mg.grammar();
    assertEquals("Expr", grammar.startSymbol().name());
    assertEquals(List.of(
        "Expr : Expr plus Term",
        "Expr : Term",
        "Term : num"
    ), productionNames(grammar));
  }

  @Test
  public void anExpressionGrammarWithComments() {
    var mg = MetaGrammar.load("""
        grammar {      // a comment here
          Expr: Term   // another comment
          Term: num    // another another comment
        }              // a last comment
        """);

    var grammar = mg.grammar();
    assertEquals("Expr", grammar.startSymbol().name());
    assertEquals(List.of(
        "Expr : Term",
        "Term : num"
    ), productionNames(grammar));
  }

  @Test
  public void aGrammarWithEmptyLines() {
    var mg = MetaGrammar.load("""
        tokens {
        
        }
        
        precedence {
        
        }
        
        grammar {
          Expr: Term
    
        }
        """);

    var grammar = mg.grammar();
    assertEquals("Expr", grammar.startSymbol().name());
    assertEquals(List.of(
        "Expr : Term"
    ), productionNames(grammar));
  }

  @Test
  public void simpleExpressionGrammarWithTokens() {
    var mg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/
          plus: /\\+/
        }
        grammar {
          Expr: Expr plus Term
          Expr: Term
          Term: num
        }
        """);

    var tokens = mg.tokens();
    assertEquals(List.of(
            new Token("num", "[0-9]+"),
            new Token("plus", "\\+")),
        tokens);
  }

  @Test
  public void grammarWithQuotedLiterals() {
    var mg = MetaGrammar.load("""
        grammar {
          Stmt: 'if' Expr 'then' Stmt 'else' Stmt
          Stmt: 'if' Expr 'then' Stmt
          Stmt: Expr ';'
          Expr: ident
        }
        tokens {
          ident: /[a-z]+/
        }
        """);

    var grammar = mg.grammar();
    assertEquals(List.of(
        "Stmt : if Expr then Stmt else Stmt",
        "Stmt : if Expr then Stmt",
        "Stmt : Expr ;",
        "Expr : ident"
    ), productionNames(grammar));

    var tokens = mg.tokens();
    assertEquals(List.of(
            new Token("if", Pattern.quote("if")),
            new Token("then", Pattern.quote("then")),
            new Token("else", Pattern.quote("else")),
            new Token(";", Pattern.quote(";")),
            new Token("ident", "[a-z]+")),
        tokens);
  }

  @Test
  public void grammarWithQuotedLiteralsThatRequireEscaping() {
    var mg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/
        }
        grammar {
          Expr: Expr '+' Term
          Expr: Expr '*' Term
          Expr: num
        }
        """);

    var tokens = mg.tokens();
    assertEquals(List.of(
            new Token("+", Pattern.quote("+")),
            new Token("*", Pattern.quote("*")),
            new Token("num", "[0-9]+")),
        tokens);
  }

  @Test
  public void grammarWithPrecedence() {
    var mg = MetaGrammar.load("""
        tokens {
          num:  /[0-9]+/
          plus: /\\+/
          star: /\\*/
          pow:  /\\^/
        }
        precedence {
          left:  plus
          left:  star
          right: pow
        }
        grammar {
          Expr: Expr plus Expr
          Expr: Expr star Expr
          Expr: Expr pow  Expr
          Expr: num
        }
        """);

    var grammar = mg.grammar();
    assertEquals(List.of(
        "Expr : Expr plus Expr",
        "Expr : Expr star Expr",
        "Expr : Expr pow Expr",
        "Expr : num"
    ), productionNames(grammar));

    var tokens = mg.tokens();
    assertEquals(List.of("num", "plus", "star", "pow"), tokenNames(tokens));

    var precedenceMap = mg.precedenceMap();
    assertEquals(Map.of(
        new Terminal("plus"), new Precedence(1, LEFT),
        new Terminal("star"), new Precedence(2, LEFT),
        new Terminal("pow"), new Precedence(3, RIGHT)
    ), precedenceMap);
  }

  @Test
  public void unnamedSkipTokenIsAccepted() {
    var mg = MetaGrammar.load("""
        tokens {
          ident: /[a-zA-Z]+/
          /[ \\t\\n]+/
        }
        grammar {
          Program: ident
        }
        """);

    var grammar = mg.grammar();
    assertEquals(List.of("Program : ident"), productionNames(grammar));

    var tokens = mg.tokens();
    assertEquals(List.of("ident"), tokenNames(tokens));
  }

  @Test
  public void keywordsUsedAsSymbolNames() {
    var mg = MetaGrammar.load("""
        grammar {
          tokens: ident
          grammar: tokens
          left : right
        }
        """);

    var grammar = mg.grammar();
    assertEquals(List.of(
        "tokens : ident",
        "grammar : tokens",
        "left : right"
    ), productionNames(grammar));
  }

  @Test
  public void multipleSectionsOfSameKind() {
    var mg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/
        }
        tokens {
          id: /[a-z]+/
        }
        grammar {
          Expr: num
          Expr: id
        }
        """);

    var grammar = mg.grammar();
    assertEquals(List.of(
        "Expr : num",
        "Expr : id"
    ), productionNames(grammar));

    var tokens = mg.tokens();
    assertEquals(List.of("num", "id"), tokenNames(tokens));
  }

  @Test
  public void startSymbolIsFirstNonTerminal() {
    var mg = MetaGrammar.load("""
        grammar {
          A: B
          B: C
          C:
        }
        """);

    var grammar = mg.grammar();
    assertEquals("A", grammar.startSymbol().name());
    assertEquals(List.of(
        "A : B",
        "B : C",
        "C : ε"
    ), productionNames(grammar));
  }

  @Test
  public void precedenceWithQuotedLiterals() {
    var mg = MetaGrammar.load("""
        precedence {
          left:  '+', '-'
          left:  '*', '/'
        }
        grammar {
          E: E '+' E
          E: E '-' E
          E: E '*' E
          E: E '/' E
          E: num
        }
        tokens {
          num: /[0-9]+/
        }
        """);

    var grammar = mg.grammar();
    assertEquals(List.of(
        "E : E + E",
        "E : E - E",
        "E : E * E",
        "E : E / E",
        "E : num"
    ), productionNames(grammar));

    var tokens = mg.tokens();
    assertEquals(List.of("+", "-", "*", "/", "num"), tokenNames(tokens));

    var precedenceMap = mg.precedenceMap();
    assertEquals(Map.of(
        new Terminal("+"), new Precedence(1, LEFT),
        new Terminal("-"), new Precedence(1, LEFT),
        new Terminal("*"), new Precedence(2, LEFT),
        new Terminal("/"), new Precedence(2, LEFT)
    ), precedenceMap);
  }


  @Test
  public void precedenceInfoOnProductionWithNamedTerminal() {
    var mg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/
          minus: /\\-/
          plus: /\\+/
        }
        precedence {
          left:  plus
          right: minus
        }
        grammar {
          E: E plus E
          E: minus E    %prec minus
          E: num
        }
        """);

    var grammar = mg.grammar();
    var precedenceMap = mg.precedenceMap();

    assertEquals(Map.of(
        new Terminal("plus"), new Precedence(1, Precedence.Associativity.LEFT),
        new Terminal("minus"), new Precedence(2, Precedence.Associativity.RIGHT),
        grammar.productions().get(1), new Precedence(2, Precedence.Associativity.RIGHT)),
        precedenceMap);
    }

  @Test
  public void precedenceInfoOnProductionWithQuotedTerminal() {
    var mg = MetaGrammar.load("""
        tokens {
          num: /[0-9]+/
        }
        precedence {
          left:  '+'
          right: '-'
        }
        grammar {
          E: E '+' E
          E: '-' E    %prec '-'
          E: num
        }
        """);

    var grammar = mg.grammar();
    var precedenceMap = mg.precedenceMap();

    assertEquals(Map.of(
        new Terminal("+"), new Precedence(1, Precedence.Associativity.LEFT),
        new Terminal("-"), new Precedence(2, Precedence.Associativity.RIGHT),
        grammar.productions().get(1), new Precedence(2, Precedence.Associativity.RIGHT)),
    precedenceMap);
  }

  @Test
  public void precedenceInfoDoesNotAffectOtherProductions() {
    var mg = MetaGrammar.load("""
      tokens {
        num: /[0-9]+/
      }
      precedence {
        left:  '-'
        right: UNARY
      }
      grammar {
        E: E '-' E
        E: '-' E    %prec UNARY
        E: num
      }
      """);

    var grammar = mg.grammar();
    var precedenceMap = mg.precedenceMap();

    assertEquals(Map.of(
            new Terminal("-"), new Precedence(1, Precedence.Associativity.LEFT),
            grammar.productions().get(1), new Precedence(2, Precedence.Associativity.RIGHT)),
        precedenceMap);
  }

  @Test
  public void precedenceInfoReferencingUnknownTerminalThrows() {
    assertThrows(ParsingException.class, () -> MetaGrammar.load("""
      tokens {
        num: /[0-9]+/
      }
      precedence {
        left: '+'
      }
      grammar {
        E: '-' E    %prec NOSUCHTOKEN
        E: num
      }
      """));
  }

  @Test
  public void precedenceInfoReferencingTerminalWithNoPrecedenceThrows() {
    assertThrows(ParsingException.class, () -> MetaGrammar.load("""
      tokens {
        num:   /[0-9]+/
        minus: /\\-/
      }
      precedence {
        left: '+'
      }
      grammar {
        E: minus E    %prec minus
        E: num
      }
      """));
  }

  @Test
  public void jsonLikeGrammar() {
    var mg = MetaGrammar.load("""
        tokens {
          string: /"[^"]*"/
          number: /[0-9]+(?:\\.[0-9]+)?/
          /[ \\t\\r\\n]+/
        }
        grammar {
          Value:    string
          Value:    number
          Value:    'true'
          Value:    'false'
          Value:    'null'
          Value:    Array
          Value:    Object
          Array:    '[' Elements ']'
          Array:    '[' ']'
          Elements: Elements ',' Value
          Elements: Value
          Object:   '{' Members '}'
          Object:   '{' '}'
          Members:  Members ',' Member
          Members:  Member
          Member:   string ':' Value
        }
        """);

    var grammar = mg.grammar();
    assertEquals("Value", grammar.startSymbol().name());
    assertEquals(List.of(
        "Value : string",
        "Value : number",
        "Value : true",
        "Value : false",
        "Value : null",
        "Value : Array",
        "Value : Object",
        "Array : [ Elements ]",
        "Array : [ ]",
        "Elements : Elements , Value",
        "Elements : Value",
        "Object : { Members }",
        "Object : { }",
        "Members : Members , Member",
        "Members : Member",
        "Member : string : Value"
    ), productionNames(grammar));

    var tokens = mg.tokens();
    assertEquals(List.of("true", "false", "null", "[", "]", ",", "{", "}", ":", "string", "number"),
        tokenNames(tokens));
  }


  @Test
  public void nullInputThrowsNullPointerException() {
    assertThrows(NullPointerException.class, () -> MetaGrammar.load(null));
  }

  @Test
  public void emptyGrammarSectionThrowsParsingException() {
    var mg = MetaGrammar.load("""
        grammar {
        }
        """);

    var tokens = mg.tokens();
    assertTrue(tokens.isEmpty());

    var precedenceMap = mg.precedenceMap();
    assertTrue(precedenceMap.isEmpty());

    assertThrows(IllegalStateException.class, mg::grammar);
  }

  @Test
  public void noGrammarSectionThrowsIllegalStateException() {
    var mg = MetaGrammar.load("""
        """);

    var tokens = mg.tokens();
    assertTrue(tokens.isEmpty());

    var precedenceMap = mg.precedenceMap();
    assertTrue(precedenceMap.isEmpty());

    assertThrows(IllegalStateException.class, mg::grammar);
  }

  @Test
  public void invalidAssociativityThrowsParsingException() {
    assertThrows(ParsingException.class, () -> MetaGrammar.load("""
        precedence {
          none: num
        }
        """));
  }

  @Test
  public void constructorWithValidArguments() {
    var tokens = List.of(new Token("num", "[0-9]+"));
    var num = new Terminal("num");
    var expr = new NonTerminal("Expr");
    var production = new Production(expr, List.of(num));
    var grammar = new Grammar(expr, List.of(production));
    var precedenceMap = Map.of(num, new Precedence(1, LEFT));

    var mg = new MetaGrammar(tokens, precedenceMap, grammar);

    assertEquals(tokens, mg.tokens());
    assertEquals(grammar, mg.grammar());
    assertEquals(precedenceMap, mg.precedenceMap());
  }

  @Test
  public void constructorWithEmptyCollections() {
    var expr = new NonTerminal("Expr");
    var num = new Terminal("num");
    var production = new Production(expr, List.of(num));
    var grammar = new Grammar(expr, List.of(production));

    var mg = new MetaGrammar(List.of(), Map.of(), grammar);

    assertTrue(mg.tokens().isEmpty());
    assertTrue(mg.precedenceMap().isEmpty());
    assertEquals(grammar, mg.grammar());
  }

  @Test
  public void constructorNullTokensThrowsNullPointerException() {
    var expr = new NonTerminal("Expr");
    var num = new Terminal("num");
    var grammar = new Grammar(expr, List.of(new Production(expr, List.of(num))));

    assertThrows(NullPointerException.class,
        () -> new MetaGrammar(null, Map.of(), grammar));
  }

  @Test
  public void constructorNullPrecedenceMapThrowsNullPointerException() {
    var expr = new NonTerminal("Expr");
    var num = new Terminal("num");
    var grammar = new Grammar(expr, List.of(new Production(expr, List.of(num))));

    assertThrows(NullPointerException.class,
        () -> new MetaGrammar(List.of(), null, grammar));
  }

  @Test
  public void constructorNullGrammarThrowsNullPointerException() {
    assertThrows(NullPointerException.class,
        () -> new MetaGrammar(List.of(), Map.of(), null));
  }

  @Test
  public void constructorTokensListIsDefensivelyCopied() {
    var num = new Terminal("num");
    var tokens = new ArrayList<>(List.of(new Token("num", "[0-9]+")));
    var expr = new NonTerminal("Expr");
    var grammar = new Grammar(expr,
        List.of(new Production(expr, List.of(num))));

    var mg = new MetaGrammar(tokens, Map.of(), grammar);
    tokens.add(new Token("id", "[a-z]+"));

    assertEquals(List.of(new Token("num", "[0-9]+")), mg.tokens());
  }

  @Test
  public void constructorPrecedenceMapIsDefensivelyCopied() {
    var num = new Terminal("num");
    var plus = new Terminal("plus");
    var expr = new NonTerminal("Expr");
    var grammar = new Grammar(expr,
        List.of(new Production(expr, List.of(num))));
    var precedenceMap = new LinkedHashMap<PrecedenceEntity, Precedence>();
    precedenceMap.put(num, new Precedence(1, LEFT));

    var mg = new MetaGrammar(List.of(), precedenceMap, grammar);
    precedenceMap.put(plus, new Precedence(2, RIGHT));

    assertEquals(1, mg.precedenceMap().size());
  }

  @Test
  public void constructorReturnedTokensIsUnmodifiable() {
    var num = new Terminal("num");
    var expr = new NonTerminal("Expr");
    var grammar = new Grammar(expr,
        List.of(new Production(expr, List.of(num))));
    var tokens = List.of(new Token("num", "[0-9]+"));

    var mg = new MetaGrammar(tokens, Map.of(), grammar);

    assertThrows(UnsupportedOperationException.class,
        () -> mg.tokens().add(new Token("id", "[a-z]+")));
  }

  @Test
  public void constructorReturnedPrecedenceMapIsUnmodifiable() {
    var num = new Terminal("num");
    var expr = new NonTerminal("Expr");
    var grammar = new Grammar(expr,
        List.of(new Production(expr, List.of(num))));

    var mg = new MetaGrammar(List.of(), Map.of(), grammar);

    assertThrows(UnsupportedOperationException.class,
        () -> mg.precedenceMap().put(num, new Precedence(1, LEFT)));
  }

  @Test
  public void constructorPrecedenceMapPreservesInsertionOrder() {
    var plus = new Terminal("plus");
    var star = new Terminal("star");
    var pow  = new Terminal("pow");
    var expr = new NonTerminal("Expr");
    var grammar = new Grammar(expr,
        List.of(new Production(expr, List.of(plus))));
    var precedenceMap = new LinkedHashMap<PrecedenceEntity, Precedence>();
    precedenceMap.put(plus, new Precedence(1, LEFT));
    precedenceMap.put(star, new Precedence(2, LEFT));
    precedenceMap.put(pow,  new Precedence(3, RIGHT));

    var mg = new MetaGrammar(List.of(), precedenceMap, grammar);

    assertEquals(List.of(plus, star, pow), List.copyOf(mg.precedenceMap().keySet()));
  }
}