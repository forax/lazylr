package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class LRAlgorithmClosureTest {

  // ── helpers ───────────────────────────────────────────────────────────────

  private static LRAlgorithm algorithm(Grammar grammar) {
    var firstSets = LRAlgorithm.computeFirstSets(grammar);
    return new LRAlgorithm(grammar, firstSets);
  }

  private static Item item(Production p, int dot, Terminal lookahead) {
    return new Item(p, dot, lookahead);
  }

  // ── 1. Dot at end: no new items added ────────────────────────────────────
  // [E -> id ., $]  is a reduce item; closure adds nothing

  @Test
  public void dotAtEnd_noNewItems() {
    var E  = new NonTerminal("E");
    var id = new Terminal("id");
    var $  = Terminal.EOF;

    var prod    = new Production(E, List.of(id));
    var grammar = new Grammar(E, List.of(prod));

    var seed = Set.of(item(prod, 1, $));

    assertEquals(
        Set.of(
            item(prod, 1, $)
        ),
        algorithm(grammar).computeClosure(seed));
  }

  // ── 2. Dot before a terminal: no new items added ─────────────────────────
  // [E -> . id, $]  — next symbol is a terminal, nothing to expand

  @Test
  public void dotBeforeTerminal_noNewItems() {
    var E  = new NonTerminal("E");
    var id = new Terminal("id");
    var $  = Terminal.EOF;

    var prod    = new Production(E, List.of(id));
    var grammar = new Grammar(E, List.of(prod));

    var seed = Set.of(item(prod, 0, $));

    assertEquals(
        Set.of(
            item(prod, 0, $)
        ),
        algorithm(grammar).computeClosure(seed));
  }

  // ── 3. Simple expansion: dot before a non-terminal ───────────────────────
  // Grammar:  E -> A,  A -> id
  // Seed:     [E -> . A, $]
  // Expected: [E -> . A, $]  +  [A -> . id, $]

  @Test
  public void simpleExpansion() {
    var E  = new NonTerminal("E");
    var A  = new NonTerminal("A");
    var id = new Terminal("id");
    var $  = Terminal.EOF;

    var prodEA  = new Production(E, List.of(A));
    var prodAid = new Production(A, List.of(id));
    var grammar = new Grammar(E, List.of(prodEA, prodAid));

    var seed = Set.of(item(prodEA, 0, $));

    assertEquals(
        Set.of(
            item(prodEA,  0, $),
            item(prodAid, 0, $)
        ),
        algorithm(grammar).computeClosure(seed));
  }

  // ── 4. Lookahead is FIRST of the β suffix ────────────────────────────────
  // Grammar:  E -> A id,  A -> num
  // Seed:     [E -> . A id, $]
  // FIRST(id $) = {id}, so the expanded item is [A -> . num, id]

  @Test
  public void lookaheadComputedFromSuffix() {
    var E   = new NonTerminal("E");
    var A   = new NonTerminal("A");
    var id  = new Terminal("id");
    var num = new Terminal("num");
    var $   = Terminal.EOF;

    var prodEAid = new Production(E, List.of(A, id));
    var prodAnum = new Production(A, List.of(num));
    var grammar  = new Grammar(E, List.of(prodEAid, prodAnum));

    var seed = Set.of(item(prodEAid, 0, $));

    assertEquals(
        Set.of(
            item(prodEAid, 0, $),
            item(prodAnum, 0, id)   // lookahead is id, not $
        ),
        algorithm(grammar).computeClosure(seed));
  }

  // ── 5. Nullable suffix: lookahead includes parent lookahead ──────────────
  // Grammar:  E -> A B,  B -> ε,  A -> id
  // Seed:     [E -> . A B, $]
  // FIRST(B $) = {$} since B ->* ε, so ε is stripped and $ flows through
  // Expanded:  [A -> . id, $]

  @Test
  public void nullableSuffixPassesParentLookahead() {
    var E  = new NonTerminal("E");
    var A  = new NonTerminal("A");
    var B  = new NonTerminal("B");
    var id = new Terminal("id");
    var $  = Terminal.EOF;

    var prodEAB = new Production(E, List.of(A, B));
    var prodBe  = new Production(B, List.of());
    var prodAid = new Production(A, List.of(id));
    var grammar = new Grammar(E, List.of(prodEAB, prodBe, prodAid));

    var seed = Set.of(item(prodEAB, 0, $));

    assertEquals(
        Set.of(
            item(prodEAB, 0, $),
            item(prodAid, 0, $)    // $ flows through nullable B
        ),
        algorithm(grammar).computeClosure(seed));
  }

  // ── 6. Multiple productions for the same non-terminal ────────────────────
  // Grammar:  E -> A,  A -> id | num
  // Seed:     [E -> . A, $]
  // Both A-productions must appear in closure

  @Test
  public void multipleProductionsSameNonTerminal() {
    var E   = new NonTerminal("E");
    var A   = new NonTerminal("A");
    var id  = new Terminal("id");
    var num = new Terminal("num");
    var $   = Terminal.EOF;

    var prodEA   = new Production(E, List.of(A));
    var prodAid  = new Production(A, List.of(id));
    var prodAnum = new Production(A, List.of(num));
    var grammar  = new Grammar(E, List.of(prodEA, prodAid, prodAnum));

    var seed = Set.of(item(prodEA, 0, $));

    assertEquals(
        Set.of(
            item(prodEA,   0, $),
            item(prodAid,  0, $),
            item(prodAnum, 0, $)
        ),
        algorithm(grammar).computeClosure(seed));
  }

  // ── 7. Transitive expansion ───────────────────────────────────────────────
  // Grammar:  E -> A,  A -> B,  B -> id
  // Seed:     [E -> . A, $]
  // Closure must reach B -> . id transitively

  @Test
  public void transitiveExpansion() {
    var E  = new NonTerminal("E");
    var A  = new NonTerminal("A");
    var B  = new NonTerminal("B");
    var id = new Terminal("id");
    var $  = Terminal.EOF;

    var prodEA  = new Production(E, List.of(A));
    var prodAB  = new Production(A, List.of(B));
    var prodBid = new Production(B, List.of(id));
    var grammar = new Grammar(E, List.of(prodEA, prodAB, prodBid));

    var seed = Set.of(item(prodEA, 0, $));

    assertEquals(
        Set.of(
            item(prodEA,  0, $),
            item(prodAB,  0, $),
            item(prodBid, 0, $)
        ),
        algorithm(grammar).computeClosure(seed));
  }

  // ── 8. Left-recursive grammar does not loop ───────────────────────────────
  // Grammar:  E -> E + id | id
  // Seed:     [E -> . E + id, $]
  // E expands to itself again; must terminate with exactly 2 items

  @Test
  public void leftRecursiveGrammar_terminates() {
    var E    = new NonTerminal("E");
    var id   = new Terminal("id");
    var plus = new Terminal("+");
    var $    = Terminal.EOF;

    var prodEEid = new Production(E, List.of(E, plus, id));
    var prodEid  = new Production(E, List.of(id));
    var grammar  = new Grammar(E, List.of(prodEEid, prodEid));

    var seed = Set.of(item(prodEEid, 0, $));

    assertEquals(
        Set.of(
            item(prodEEid, 0, plus),
            item(prodEEid, 0, $),
            item(prodEid,  0, plus)
        ),
        algorithm(grammar).computeClosure(seed));
  }

  // ── 9. Lookaheads union-ed across multiple seed items ─────────────────────
  // Grammar:  S -> E x | E y,  E -> A | B,  A -> id,  B -> id
  // Seed:     [S -> . E x, $]  and  [S -> . E y, $]
  // FIRST(x $) = {x},  FIRST(y $) = {y}
  // A -> . id and B -> . id must each carry lookahead {x, y}

  @Test
  public void lookaheadsUnionedAcrossPaths() {
    var S  = new NonTerminal("S");
    var E  = new NonTerminal("E");
    var A  = new NonTerminal("A");
    var B  = new NonTerminal("B");
    var id = new Terminal("id");
    var x  = new Terminal("x");
    var y  = new Terminal("y");
    var $  = Terminal.EOF;

    var prodSEx = new Production(S, List.of(E, x));
    var prodSEy = new Production(S, List.of(E, y));
    var prodEA  = new Production(E, List.of(A));
    var prodEB  = new Production(E, List.of(B));
    var prodAid = new Production(A, List.of(id));
    var prodBid = new Production(B, List.of(id));
    var grammar = new Grammar(S, List.of(prodSEx, prodSEy, prodEA, prodEB, prodAid, prodBid));

    var seed = Set.of(item(prodSEx, 0, $), item(prodSEy, 0, $));

    assertEquals(
        Set.of(
            item(prodSEx, 0, $),
            item(prodSEy, 0, $),
            item(prodEA,  0, x),
            item(prodEA,  0, y),
            item(prodEB,  0, x),
            item(prodEB,  0, y),
            item(prodAid, 0, x),
            item(prodAid, 0, y),
            item(prodBid, 0, x),
            item(prodBid, 0, y)
        ),
        algorithm(grammar).computeClosure(seed));
  }

  // ── 10. Seed with dot in the middle ──────────────────────────────────────
  // Grammar:  E -> id + A,  A -> num
  // Seed:     [E -> id + . A, $]   (dot after two symbols, before A)
  // Closure:  same item  +  [A -> . num, $]

  @Test
  public void seedWithDotInMiddle() {
    var E    = new NonTerminal("E");
    var A    = new NonTerminal("A");
    var id   = new Terminal("id");
    var num  = new Terminal("num");
    var plus = new Terminal("+");
    var $    = Terminal.EOF;

    var prodEidA = new Production(E, List.of(id, plus, A));
    var prodAnum = new Production(A, List.of(num));
    var grammar  = new Grammar(E, List.of(prodEidA, prodAnum));

    var seed = Set.of(item(prodEidA, 2, $));

    assertEquals(
        Set.of(
            item(prodEidA, 2, $),
            item(prodAnum, 0, $)
        ),
        algorithm(grammar).computeClosure(seed));
  }

  // ── 11. Classic arithmetic grammar: initial closure ──────────────────────
  // E  -> T E'     E' -> + T E' | ε
  // T  -> F T'     T' -> * F T' | ε
  // F  -> ( E ) | id
  //
  // Seed:  [E -> . T E', $]
  //
  // FIRST(E' $) = {+, $}  (E' is nullable so $ included)
  // FIRST(T' {+,$}) = {*, +, $}  (T' is nullable so + and $ flow through)

  @Test
  public void classicArithmeticGrammar_initialClosure() {
    var E  = new NonTerminal("E");
    var Ep = new NonTerminal("E'");
    var T  = new NonTerminal("T");
    var Tp = new NonTerminal("T'");
    var F  = new NonTerminal("F");

    var id   = new Terminal("id");
    var plus = new Terminal("+");
    var mul  = new Terminal("*");
    var lp   = new Terminal("(");
    var rp   = new Terminal(")");
    var $    = Terminal.EOF;

    var prodETEp   = new Production(E,  List.of(T, Ep));
    var prodEpTEp  = new Production(Ep, List.of(plus, T, Ep));
    var prodEpe    = new Production(Ep, List.of());
    var prodTFTp   = new Production(T,  List.of(F, Tp));
    var prodTpFTp  = new Production(Tp, List.of(mul, F, Tp));
    var prodTpe    = new Production(Tp, List.of());
    var prodFparen = new Production(F,  List.of(lp, E, rp));
    var prodFid    = new Production(F,  List.of(id));

    var grammar = new Grammar(E, List.of(
        prodETEp, prodEpTEp, prodEpe,
        prodTFTp, prodTpFTp, prodTpe,
        prodFparen, prodFid
    ));

    var seed = Set.of(item(prodETEp, 0, $));

    assertEquals(
        Set.of(
            item(prodETEp,   0, $),
            item(prodTFTp,   0, plus),    // FIRST(E' $) = {+, $}
            item(prodTFTp,   0, $),
            item(prodFparen, 0, mul),     // FIRST(T' {+,$}) = {*, +, $}
            item(prodFparen, 0, plus),
            item(prodFparen, 0, $),
            item(prodFid,    0, mul),     // FIRST(F {+,$}) = {*, +, $}
            item(prodFid,    0, plus),
            item(prodFid,    0, $)
        ),
        algorithm(grammar).computeClosure(seed));
  }
}