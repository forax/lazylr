package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public final class MetaGrammarTest {

  private static List<String> productionNames(Grammar grammar) {
    return grammar.productions().stream()
        .map(Production::name)
        .toList();
  }

  private static List<String> ruleNames(List<Rule> rules) {
    return rules.stream()
        .map(Rule::name)
        .filter(Objects::nonNull)
        .toList();
  }


  @Test
  public void minimalEpsilonGrammar() {
    var mg = MetaGrammar.create("""
        grammar {
          Empty:
        }
        """);

    var grammar = mg.grammar();
    assertEquals(
        List.of("Empty : ε"),
        productionNames(grammar));

    var rules = mg.rules();
    assertEquals(List.of(), ruleNames(rules));

    var precedenceMap = mg.precedenceMap();
    assertTrue(precedenceMap.isEmpty());
  }

  @Test
  public void oneProductionGrammar() {
    var mg = MetaGrammar.create("""
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
    var mg = MetaGrammar.create("""
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
  public void simpleExpressionGrammarWithTokens() {
    var mg = MetaGrammar.create("""
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

    var rules = mg.rules();
    assertEquals(List.of(
            new Rule("num", "[0-9]+"),
            new Rule("plus", "\\+")),
        rules);
  }

  @Test
  public void grammarWithQuotedLiterals() {
    var mg = MetaGrammar.create("""
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

    var rules = mg.rules();
    assertEquals(List.of(
            new Rule("if", "if"),
            new Rule("then", "then"),
            new Rule("else", "else"),
            new Rule(";", ";"),
            new Rule("ident", "[a-z]+")),
        rules);
  }

  @Test
  public void grammarWithQuotedLiteralsThatRequireEscaping() {
    var mg = MetaGrammar.create("""
        tokens {
          num: /[0-9]+/
        }
        grammar {
          Expr: Expr '+' Term
          Expr: Expr '*' Term
          Expr: num
        }
        """);

    var rules = mg.rules();
    assertEquals(List.of(
            new Rule("+", "\\+"),
            new Rule("*", "\\*"),
            new Rule("num", "[0-9]+")),
        rules);
  }

  @Test
  public void grammarWithPrecedence() {
    var mg = MetaGrammar.create("""
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

    var rules = mg.rules();
    assertEquals(List.of("num", "plus", "star", "pow"), ruleNames(rules));

    var precedenceMap = mg.precedenceMap();
    assertEquals(Map.of(
        new Terminal("plus"), new Precedence(0, Precedence.Associativity.LEFT),
        new Terminal("star"), new Precedence(1, Precedence.Associativity.LEFT),
        new Terminal("pow"), new Precedence(2, Precedence.Associativity.RIGHT)
    ), precedenceMap);
  }

  @Test
  public void unnamedSkipRuleIsAccepted() {
    var mg = MetaGrammar.create("""
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

    var rules = mg.rules();
    assertEquals(List.of("ident"), ruleNames(rules));
  }

  @Test
  public void keywordsUsedAsNonTerminalNames() {
    var mg = MetaGrammar.create("""
        grammar {
          tokens: ident
          grammar: tokens
        }
        tokens {
          ident: /[a-z]+/
        }
        """);

    var grammar = mg.grammar();
    assertEquals(List.of(
        "tokens : ident",
        "grammar : tokens"
    ), productionNames(grammar));

    var rules = mg.rules();
    assertEquals(List.of("ident"), ruleNames(rules));
  }

  @Test
  public void multipleSectionsOfSameKind() {
    var mg = MetaGrammar.create("""
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

    var rules = mg.rules();
    assertEquals(List.of("num", "id"), ruleNames(rules));
  }

  @Test
  public void startSymbolIsFirstNonTerminal() {
    var mg = MetaGrammar.create("""
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
    var mg = MetaGrammar.create("""
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

    var rules = mg.rules();
    assertEquals(List.of("+", "-", "*", "/", "num"), ruleNames(rules));

    var precedenceMap = mg.precedenceMap();
    assertEquals(Map.of(
        new Terminal("+"), new Precedence(0, Precedence.Associativity.LEFT),
        new Terminal("-"), new Precedence(0, Precedence.Associativity.LEFT),
        new Terminal("*"), new Precedence(1, Precedence.Associativity.LEFT),
        new Terminal("/"), new Precedence(1, Precedence.Associativity.LEFT)
    ), precedenceMap);
  }

  @Test
  public void jsonLikeGrammar() {
    var mg = MetaGrammar.create("""
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

    var rules = mg.rules();
    assertEquals(List.of("true", "false", "null", "[", "]", ",", "{", "}", ":", "string", "number"),
        ruleNames(rules));
  }


  @Test
  public void nullInputThrowsNullPointerException() {
    assertThrows(NullPointerException.class, () -> MetaGrammar.create(null));
  }

  @Test
  public void emptyGrammarSectionThrowsParsingException() {
    assertThrows(ParsingException.class, () -> MetaGrammar.create("""
        grammar {
        }
        """));
  }

  @Test
  public void noGrammarSectionThrowsParsingException() {
    assertThrows(ParsingException.class, () -> MetaGrammar.create("""
        """));
  }

  @Test
  public void invalidAssociativityThrowsParsingException() {
    assertThrows(ParsingException.class, () -> MetaGrammar.create("""
        precedence {
          none: num
        }
        grammar {
          E: num
        }
        tokens {
          num: /[0-9]+/
        }
        """));
  }
}