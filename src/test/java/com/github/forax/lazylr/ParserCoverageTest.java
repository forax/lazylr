package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public final class ParserCoverageTest {

  private static final ParserListener NOOP = new ParserListener() {
    @Override public void onShift(Terminal token, int position) {}
    @Override public void onReduce(Production production) {}
  };

  private static void parse(Parser parser, List<Terminal> tokens) {
    parser.parse(tokens.iterator(), NOOP);
  }



  @Test
  public void emptyBeforeAnyParse() {
    var E   = new NonTerminal("E");
    var num = new Terminal("num");
    var grammar = new Grammar(E, List.of(new Production(E, List.of(num))));
    var parser = Parser.createParser(grammar, Map.of());

    assertEquals(Set.of(), parser.coverage());
  }

  @Test
  public void stableBeforeAnyParse() {
    var E   = new NonTerminal("E");
    var num = new Terminal("num");
    var grammar = new Grammar(E, List.of(new Production(E, List.of(num))));
    var parser = Parser.createParser(grammar, Map.of());

    assertEquals(parser.coverage(), parser.coverage());
  }

  @Test
  public void singleProductionCoveredAfterParse() {
    // E -> num : parsing a single number must cover the only production.
    var E    = new NonTerminal("E");
    var num  = new Terminal("num");
    var pNum = new Production(E, List.of(num));
    var grammar = new Grammar(E, List.of(pNum));
    var parser = Parser.createParser(grammar, Map.of());

    parse(parser, List.of(new Terminal("num", "1")));

    assertEquals(Set.of(pNum), parser.coverage());
  }

  @Test
  public void unusedAlternativeNotCovered() {
    // E -> num | str
    var E    = new NonTerminal("E");
    var num  = new Terminal("num");
    var str  = new Terminal("str");
    var pNum = new Production(E, List.of(num));
    var pStr = new Production(E, List.of(str));
    var grammar = new Grammar(E, List.of(pNum, pStr));
    var parser = Parser.createParser(grammar, Map.of());

    parse(parser, List.of(new Terminal("num", "42")));

    assertTrue(parser.coverage().contains(pNum));
    assertFalse(parser.coverage().contains(pStr));
  }

  @Test
  public void bothAlternativesCoveredAcrossParses() {
    // E -> num | str
    var E    = new NonTerminal("E");
    var num  = new Terminal("num");
    var str  = new Terminal("str");
    var pNum = new Production(E, List.of(num));
    var pStr = new Production(E, List.of(str));
    var grammar = new Grammar(E, List.of(pNum, pStr));
    var parser = Parser.createParser(grammar, Map.of());

    parse(parser, List.of(new Terminal("num", "1")));
    parse(parser, List.of(new Terminal("str", "hello")));

    assertEquals(Set.of(pNum, pStr), parser.coverage());
  }

  @Test
  public void coveredSetGrowsMonotonically() {
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var num  = new Terminal("num");
    var pAdd = new Production(E, List.of(E, plus, E));
    var pMul = new Production(E, List.of(E, mul,  E));
    var pNum = new Production(E, List.of(num));
    var grammar = new Grammar(E, List.of(pAdd, pMul, pNum));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        mul,  new Precedence(20, Precedence.Associativity.LEFT));
    var parser = Parser.createParser(grammar, precedence);

    parse(parser, List.of(new Terminal("num", "1")));
    var after1 = Set.copyOf(parser.coverage());

    parse(parser, List.of(
        new Terminal("num", "1"), new Terminal("+", "+"), new Terminal("num", "2")));
    var after2 = Set.copyOf(parser.coverage());

    parse(parser, List.of(
        new Terminal("num", "1"), new Terminal("*", "*"), new Terminal("num", "2")));
    var after3 = Set.copyOf(parser.coverage());

    assertTrue(after2.containsAll(after1), "set must not shrink after second parse");
    assertTrue(after3.containsAll(after2), "set must not shrink after third parse");
  }

  @Test
  public void epsilonProductionCoveredWhenReduced() {
    // E -> A num, A -> ε
    var E      = new NonTerminal("E");
    var A      = new NonTerminal("A");
    var num    = new Terminal("num");
    var pEAnum = new Production(E, List.of(A, num));
    var pAeps  = new Production(A, List.of());
    var grammar = new Grammar(E, List.of(pEAnum, pAeps));
    var parser = Parser.createParser(grammar, Map.of());

    parse(parser, List.of(new Terminal("num", "1")));

    assertTrue(parser.coverage().contains(pAeps),
        "A -> ε must be covered when reduced");
    assertTrue(parser.coverage().contains(pEAnum),
        "E -> A num must be covered when reduced");
  }

  @Test
  public void fullyNullableGrammarAllProductionsCovered() {
    // E -> A B, A -> ε, B -> ε
    var E   = new NonTerminal("E");
    var A   = new NonTerminal("A");
    var B   = new NonTerminal("B");
    var pEAB = new Production(E, List.of(A, B));
    var pAe  = new Production(A, List.of());
    var pBe  = new Production(B, List.of());
    var grammar = new Grammar(E, List.of(pEAB, pAe, pBe));
    var parser = Parser.createParser(grammar, Map.of());

    parse(parser, List.of());

    assertEquals(Set.of(pEAB, pAe, pBe), parser.coverage());
  }

  @Test
  public void leftRecursiveProductionAppearsOnceEvenAfterMultipleReductions() {
    // E -> E + E | num
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var num  = new Terminal("num");
    var pAdd = new Production(E, List.of(E, plus, E));
    var pNum = new Production(E, List.of(num));
    var grammar = new Grammar(E, List.of(pAdd, pNum));
    var parser = Parser.createParser(grammar,
        Map.of(plus, new Precedence(1, Precedence.Associativity.LEFT)));

    parse(parser, List.of(
        new Terminal("num", "1"), new Terminal("+", "+"),
        new Terminal("num", "2"), new Terminal("+", "+"),
        new Terminal("num", "3")));

    assertEquals(Set.of(pAdd, pNum), parser.coverage());
  }

  @Test
  public void productionsReducedBeforeFailureAreStillCovered() {
    // E -> E + E | num
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var num  = new Terminal("num");
    var pAdd = new Production(E, List.of(E, plus, E));
    var pNum = new Production(E, List.of(num));
    var grammar = new Grammar(E, List.of(pAdd, pNum));
    var parser = Parser.createParser(grammar,
        Map.of(plus, new Precedence(1, Precedence.Associativity.LEFT)));

    assertThrows(ParsingException.class, () ->
        parse(parser, List.of(
            new Terminal("num", "1"),
            new Terminal("+", "+"),
            new Terminal("+", "+"))));

    assertTrue(parser.coverage().contains(pNum),
        "E -> num was reduced before the error and must be covered");
  }

  @Test
  public void coverageAccumulatesAcrossMultipleParseCalls() {
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var num  = new Terminal("num");
    var pAdd = new Production(E, List.of(E, plus, E));
    var pNum = new Production(E, List.of(num));
    var grammar = new Grammar(E, List.of(pAdd, pNum));
    var parser = Parser.createParser(grammar,
        Map.of(plus, new Precedence(1, Precedence.Associativity.LEFT)));

    parse(parser, List.of(new Terminal("num", "1")));
    assertFalse(parser.coverage().contains(pAdd),
        "pAdd not yet covered after first parse");

    parse(parser, List.of(
        new Terminal("num", "1"), new Terminal("+", "+"), new Terminal("num", "2")));
    assertTrue(parser.coverage().contains(pAdd),
        "pAdd must be covered after second parse");
    assertTrue(parser.coverage().contains(pNum),
        "pNum must still be covered after second parse");
  }

  // Each Parser instance has its own action table; parsing on one must not
  // affect the covered set of the other.
  @Test
  public void twoParserInstancesHaveIndependentCoverage() {
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var num  = new Terminal("num");
    var pAdd = new Production(E, List.of(E, plus, E));
    var pNum = new Production(E, List.of(num));
    var grammar = new Grammar(E, List.of(pAdd, pNum));
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(1, Precedence.Associativity.LEFT));

    var parser1 = Parser.createParser(grammar, precedence);
    var parser2 = Parser.createParser(grammar, precedence);

    // Parser1 parses an addition; parser2 parses only a number.
    parse(parser1, List.of(
        new Terminal("num", "1"), new Terminal("+", "+"), new Terminal("num", "2")));
    parse(parser2, List.of(new Terminal("num", "1")));

    assertTrue(parser1.coverage().contains(pAdd));
    assertFalse(parser2.coverage().contains(pAdd));
  }

  @Test
  public void coverageSetIsUnmodifiable() {
    var E    = new NonTerminal("E");
    var num  = new Terminal("num");
    var pNum = new Production(E, List.of(num));
    var grammar = new Grammar(E, List.of(pNum));
    var parser = Parser.createParser(grammar, Map.of());

    parse(parser, List.of(new Terminal("num", "1")));

    assertThrows(UnsupportedOperationException.class,
        () -> parser.coverage().add(pNum));
  }

  @Test
  public void fullCoverageAfterExpressionUsingAllProductions() {
    // E -> E + E | E * E | num  (+ and * with proper precedence)
    var E    = new NonTerminal("E");
    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var num  = new Terminal("num");
    var pAdd = new Production(E, List.of(E, plus, E));
    var pMul = new Production(E, List.of(E, mul,  E));
    var pNum = new Production(E, List.of(num));
    var grammar = new Grammar(E, List.of(pAdd, pMul, pNum));
    var parser = Parser.createParser(grammar, Map.<PrecedenceEntity, Precedence>of(
        plus, new Precedence(10, Precedence.Associativity.LEFT),
        mul,  new Precedence(20, Precedence.Associativity.LEFT)));

    // "1 + 2 * 3": reduces num (×3), then E * E, then E + E
    parse(parser, List.of(
        new Terminal("num", "1"), new Terminal("+", "+"),
        new Terminal("num", "2"), new Terminal("*", "*"),
        new Terminal("num", "3")));

    assertEquals(Set.of(pAdd, pMul, pNum), parser.coverage());
  }

  @Test
  public void chainOfNonTerminalsAllCovered() {
    // E -> A, A -> B, B -> num
    var E   = new NonTerminal("E");
    var A   = new NonTerminal("A");
    var B   = new NonTerminal("B");
    var num = new Terminal("num");
    var pEA  = new Production(E, List.of(A));
    var pAB  = new Production(A, List.of(B));
    var pBnum = new Production(B, List.of(num));
    var grammar = new Grammar(E, List.of(pEA, pAB, pBnum));
    var parser = Parser.createParser(grammar, Map.of());

    parse(parser, List.of(new Terminal("num", "1")));

    assertEquals(Set.of(pEA, pAB, pBnum), parser.coverage());
  }

  @Test
  public void rightAssociativeOperatorCovered() {
    // E -> E ^ E | num  (right-associative ^)
    var E   = new NonTerminal("E");
    var pow = new Terminal("^");
    var num = new Terminal("num");
    var pPow = new Production(E, List.of(E, pow, E));
    var pNum = new Production(E, List.of(num));
    var grammar = new Grammar(E, List.of(pPow, pNum));
    var parser = Parser.createParser(grammar,
        Map.of(pow, new Precedence(1, Precedence.Associativity.RIGHT)));

    parse(parser, List.of(
        new Terminal("num", "1"), new Terminal("^", "^"),
        new Terminal("num", "2"), new Terminal("^", "^"),
        new Terminal("num", "3")));

    assertEquals(Set.of(pPow, pNum), parser.coverage());
  }

  @Test
  public void nullableAlternativeCoveredWhenTaken() {
    // E -> A num, A -> id | ε
    var E      = new NonTerminal("E");
    var A      = new NonTerminal("A");
    var num    = new Terminal("num");
    var id     = new Terminal("id");
    var pEAnum = new Production(E, List.of(A, num));
    var pAid   = new Production(A, List.of(id));
    var pAeps  = new Production(A, List.of());
    var grammar = new Grammar(E, List.of(pEAnum, pAid, pAeps));
    var parser = Parser.createParser(grammar, Map.of());

    // First parse: A -> ε path
    parse(parser, List.of(new Terminal("num", "1")));
    assertTrue(parser.coverage().contains(pAeps));
    assertFalse(parser.coverage().contains(pAid));

    // Second parse: A -> id path
    parse(parser, List.of(new Terminal("id", "x"), new Terminal("num", "2")));
    assertTrue(parser.coverage().contains(pAid));
    assertEquals(Set.of(pEAnum, pAid, pAeps), parser.coverage());
  }
}