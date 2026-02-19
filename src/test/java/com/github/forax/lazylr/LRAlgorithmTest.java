package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class LRAlgorithmTest {

  // Single terminal production: E -> id
  // FIRST(E) = {id}
  @Test
  public void singleTerminalProduction() {
    var E = new NonTerminal("E");
    var id = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(id))
    ));

    var firstSets = LRAlgorithm.computeFirstSets(grammar);

    assertEquals(Set.of(id), firstSets.get(E));
  }

  // Multiple alternatives: E -> id | num | str
  // FIRST(E) = {id, num, str}
  @Test
  public void multipleTerminalAlternatives() {
    var E = new NonTerminal("E");
    var id = new Terminal("id");
    var num = new Terminal("num");
    var str = new Terminal("str");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(id)),
        new Production(E, List.of(num)),
        new Production(E, List.of(str))
    ));

    var firstSets = LRAlgorithm.computeFirstSets(grammar);

    assertEquals(Set.of(id, num, str), firstSets.get(E));
  }

  // Terminal is its own FIRST set
  // For any Terminal t, FIRST(t) = {t}
  @Test
  public void firstOfTerminalIsSelf() {
    var E = new NonTerminal("E");
    var id = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(id))
    ));

    var firstSets = LRAlgorithm.computeFirstSets(grammar);

    assertEquals(Set.of(id), firstSets.get(id));
  }

  // Chain of non-terminals: E -> A, A -> B, B -> id
  // FIRST(E) = FIRST(A) = FIRST(B) = {id}
  @Test
  public void chainOfNonTerminals() {
    var E = new NonTerminal("E");
    var A = new NonTerminal("A");
    var B = new NonTerminal("B");
    var id = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(A)),
        new Production(A, List.of(B)),
        new Production(B, List.of(id))
    ));

    var firstSets = LRAlgorithm.computeFirstSets(grammar);

    assertEquals(Set.of(id), firstSets.get(E));
    assertEquals(Set.of(id), firstSets.get(A));
    assertEquals(Set.of(id), firstSets.get(B));
  }

  // Nullable non-terminal: A -> ε, E -> A id
  // FIRST(A) = {ε}
  // FIRST(E) = FIRST(A) ∪ {id} = {id}   (ε is consumed, id follows)
  @Test
  public void nullableNonTerminalPropagatesThrough() {
    var E = new NonTerminal("E");
    var A = new NonTerminal("A");
    var id = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(A, id)),
        new Production(A, List.of())        // A -> ε
    ));

    var firstSets = LRAlgorithm.computeFirstSets(grammar);

    assertEquals(Set.of(Terminal.EPSILON), firstSets.get(A));
    assertEquals(Set.of(id), firstSets.get(E));
  }

  // All-nullable prefix: E -> A B id, A -> ε, B -> ε
  // FIRST(A) = {ε}, FIRST(B) = {ε}
  // FIRST(E) = {id}  (skip through both nullable symbols to reach id)
  @Test
  public void allPrefixSymbolsNullable() {
    var E = new NonTerminal("E");
    var A = new NonTerminal("A");
    var B = new NonTerminal("B");
    var id = new Terminal("id");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(A, B, id)),
        new Production(A, List.of()),       // A -> ε
        new Production(B, List.of())        // B -> ε
    ));

    var firstSets = LRAlgorithm.computeFirstSets(grammar);

    assertEquals(Set.of(id), firstSets.get(E));
  }

  // Nullable non-terminal with alternatives
  // A -> id | ε
  // E -> A num
  // FIRST(A) = {id, ε}
  // FIRST(E) = {id, num}  (id from A's non-empty alt; num when A -> ε)
  @Test
  public void nullableWithAlternatives() {
    var E = new NonTerminal("E");
    var A = new NonTerminal("A");
    var id = new Terminal("id");
    var num = new Terminal("num");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(A, num)),
        new Production(A, List.of(id)),
        new Production(A, List.of())        // A -> ε
    ));

    var firstSets = LRAlgorithm.computeFirstSets(grammar);

    assertEquals(Set.of(id, num), firstSets.get(E));
    assertEquals(Set.of(id, Terminal.EPSILON), firstSets.get(A));
  }

  // Entirely nullable production: E -> A B, A -> ε, B -> ε
  // FIRST(E) should contain ε because the entire body can vanish
  @Test
  public void entireProductionBodyNullable() {
    var E = new NonTerminal("E");
    var A = new NonTerminal("A");
    var B = new NonTerminal("B");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(A, B)),
        new Production(A, List.of()),       // A -> ε
        new Production(B, List.of())        // B -> ε
    ));

    var firstSets = LRAlgorithm.computeFirstSets(grammar);

    assertEquals(Set.of(Terminal.EPSILON), firstSets.get(E));
  }

  // Left-recursive rule: E -> E + id | id
  // FIRST(E) = {id}  (left recursion must not cause infinite loop)
  @Test
  public void leftRecursiveGrammar() {
    var E = new NonTerminal("E");
    var id = new Terminal("id");
    var plus = new Terminal("+");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(E, plus, id)),
        new Production(E, List.of(id))
    ));

    var firstSets = LRAlgorithm.computeFirstSets(grammar);

    assertEquals(Set.of(id), firstSets.get(E));
  }

  // Mutual recursion: E -> A id, A -> E num | ε
  // FIRST(A) = FIRST(E) ∪ {ε}
  // FIRST(E) starts with FIRST(A); since A is nullable, id is also in FIRST(E)
  // Fixed point: FIRST(E) = {id}, FIRST(A) = {id, ε}
  @Test
  public void mutuallyRecursiveNonTerminals() {
    var E = new NonTerminal("E");
    var A = new NonTerminal("A");
    var id = new Terminal("id");
    var num = new Terminal("num");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(A, id)),
        new Production(A, List.of(E, num)),
        new Production(A, List.of())        // A -> ε
    ));

    var firstSets = LRAlgorithm.computeFirstSets(grammar);

    assertEquals(Set.of(id), firstSets.get(E));
    assertEquals(Set.of(id, Terminal.EPSILON), firstSets.get(A));
  }

  // Classic arithmetic grammar
  // E  -> T E'
  // E' -> + T E' | ε
  // T  -> F T'
  // T' -> * F T' | ε
  // F  -> ( E ) | id
  //
  // FIRST(F)  = {(, id}
  // FIRST(T)  = FIRST(F)           = {(, id}
  // FIRST(T') = {*, ε}
  // FIRST(E)  = FIRST(T)           = {(, id}
  // FIRST(E') = {+, ε}
  @Test
  public void classicArithmeticGrammar() {
    var E = new NonTerminal("E");
    var Eprime = new NonTerminal("E'");
    var T = new NonTerminal("T");
    var Tprime = new NonTerminal("T'");
    var F = new NonTerminal("F");

    var id = new Terminal("id");
    var plus = new Terminal("+");
    var mul = new Terminal("*");
    var lparen = new Terminal("(");
    var rparen = new Terminal(")");

    var grammar = new Grammar(E, List.of(
        new Production(E,      List.of(T, Eprime)),
        new Production(Eprime, List.of(plus, T, Eprime)),
        new Production(Eprime, List.of()),                  // E' -> ε
        new Production(T,      List.of(F, Tprime)),
        new Production(Tprime, List.of(mul, F, Tprime)),
        new Production(Tprime, List.of()),                  // T' -> ε
        new Production(F,      List.of(lparen, E, rparen)),
        new Production(F,      List.of(id))
    ));

    var firstSets = LRAlgorithm.computeFirstSets(grammar);

    assertEquals(Set.of(lparen, id), firstSets.get(E));
    assertEquals(Set.of(plus, Terminal.EPSILON), firstSets.get(Eprime));
    assertEquals(Set.of(lparen, id), firstSets.get(T));
    assertEquals(Set.of(mul, Terminal.EPSILON), firstSets.get(Tprime));
    assertEquals(Set.of(lparen, id), firstSets.get(F));
  }

  // FIRST of a sequence (used internally by item-set construction)
  // Grammar: E -> A B C, A -> x | ε, B -> y | ε, C -> z
  // FIRST(A B C):
  //   A contributes x (and ε allows looking further)
  //   B contributes y (and ε allows looking further)
  //   C contributes z
  //   result = {x, y, z}  (no ε because C is not nullable)
  @Test
  public void firstOfSequenceWithIntermediateNullables() {
    var E = new NonTerminal("E");
    var A = new NonTerminal("A");
    var B = new NonTerminal("B");
    var C = new NonTerminal("C");
    var x = new Terminal("x");
    var y = new Terminal("y");
    var z = new Terminal("z");

    var grammar = new Grammar(E, List.of(
        new Production(E, List.of(A, B, C)),
        new Production(A, List.of(x)),
        new Production(A, List.of()),       // A -> ε
        new Production(B, List.of(y)),
        new Production(B, List.of()),       // B -> ε
        new Production(C, List.of(z))
    ));

    var firstSets = LRAlgorithm.computeFirstSets(grammar);

    assertEquals(Set.of(x, y, z), firstSets.get(E));
  }
}