package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public final class LALRVerifierTest {

  private final Terminal PLUS = new Terminal("+");
  private final Terminal MUL = new Terminal("*");
  private final Terminal NUM = new Terminal("num");
  private final Terminal IF = new Terminal("if");
  private final Terminal ELSE = new Terminal("else");
  private final NonTerminal E = new NonTerminal("E");
  private final NonTerminal S = new NonTerminal("S");

  private static final Consumer<String> ERROR_REPORTER = error -> {
    throw new IllegalStateException(error);
  };

  @Test
  public void verifySimpleExpression() {
    // E -> num
    var p1 = new Production(E, List.of(NUM));
    var grammar = new Grammar(E, List.of(p1));

    LALRVerifier.verify(grammar, Map.of(), ERROR_REPORTER);
  }

  @Test
  public void verifyResolvedShiftReduceConflict() {
    // E -> E + E | num
    // Standard arithmetic ambiguity resolved with precedence
    var pPlus = new Production(E, List.of(E, PLUS, E));
    var pNum = new Production(E, List.of(NUM));
    var grammar = new Grammar(E, List.of(pPlus, pNum));

    // Define Left Associativity for PLUS
    var prec = new Precedence(1, Precedence.Associativity.LEFT);
    var precedenceMap = Map.of(PLUS, prec, pPlus, prec);

    LALRVerifier.verify(grammar, precedenceMap, ERROR_REPORTER);
  }

  @Test
  public void failOnUnresolvedShiftReduceConflict() {
    // E -> E + E | num (No precedence provided)
    var pPlus = new Production(E, List.of(E, PLUS, E));
    var pNum = new Production(E, List.of(NUM));
    var grammar = new Grammar(E, List.of(pPlus, pNum));

    assertThrows(IllegalStateException.class, () ->
        LALRVerifier.verify(grammar, Map.of(), ERROR_REPORTER)
    );
  }

  @Test
  public void failOnReduceReduceConflict() {
    // S -> A | B
    // A -> num
    // B -> num
    var A = new NonTerminal("A");
    var B = new NonTerminal("B");
    var pS1 = new Production(S, List.of(A));
    var pS2 = new Production(S, List.of(B));
    var pA = new Production(A, List.of(NUM));
    var pB = new Production(B, List.of(NUM));

    var grammar = new Grammar(S, List.of(pS1, pS2, pA, pB));

    assertThrows(IllegalStateException.class, () ->
        LALRVerifier.verify(grammar, Map.of(), ERROR_REPORTER)
    );
  }

  @Test
  public void verifyDanglingElseResolved() {
    // S -> if S | if S else S | num
    var pIf = new Production(S, List.of(IF, S));
    var pIfElse = new Production(S, List.of(IF, S, ELSE, S));
    var pNum = new Production(S, List.of(NUM));
    var grammar = new Grammar(S, List.of(pIf, pIfElse, pNum));

    // Traditionally, 'else' binds to the nearest 'if'.
    // This is a Shift/Reduce conflict on 'else'.
    // Shifting 'else' resolves it.
    var precHigh = new Precedence(2, Precedence.Associativity.RIGHT);
    var precLow = new Precedence(1, Precedence.Associativity.RIGHT);

    // Give ELSE higher precedence than the production S -> if S
    var precedenceMap = Map.of(
        ELSE, precHigh,
        pIf, precLow
    );

    LALRVerifier.verify(grammar, precedenceMap, ERROR_REPORTER);
  }

  @Test
  public void verifyOperatorPrecedenceLevels() {
    // E -> E + E | E * E | num
    var pPlus = new Production(E, List.of(E, PLUS, E));
    var pMul = new Production(E, List.of(E, MUL, E));
    var pNum = new Production(E, List.of(NUM));
    var grammar = new Grammar(E, List.of(pPlus, pMul, pNum));

    var low = new Precedence(1, Precedence.Associativity.LEFT);
    var high = new Precedence(2, Precedence.Associativity.LEFT);

    var precedenceMap = Map.of(
        PLUS, low,
        pPlus, low,
        MUL, high,
        pMul, high
    );

    LALRVerifier.verify(grammar, precedenceMap, ERROR_REPORTER);
  }

  @Test
  public void verifyShiftBeforeReduce() {
    // S -> A | B
    // A -> x y
    // B -> x
    var x = new Terminal("x");
    var y = new Terminal("y");
    var A = new NonTerminal("A");
    var B = new NonTerminal("B");

    var pS1 = new Production(S, List.of(A));
    var pS2 = new Production(S, List.of(B));
    var pB  = new Production(A, List.of(x, y));
    var pA  = new Production(B, List.of(x));

    var grammar = new Grammar(S, List.of(pS1, pS2, pB, pA));

    var precLow  = new Precedence(1, Precedence.Associativity.LEFT);
    var precHigh = new Precedence(2, Precedence.Associativity.LEFT);
    var precedenceMap = Map.of(pA, precLow, x, precHigh);

    LALRVerifier.verify(grammar, precedenceMap, ERROR_REPORTER);
  }

  @Test
  public void verifyReduceBeforeShift() {
    // S -> B | A
    // A -> x y
    // B -> x
    var x = new Terminal("x");
    var y = new Terminal("y");
    var A = new NonTerminal("A");
    var B = new NonTerminal("B");

    var pS1 = new Production(S, List.of(B));  // those two lines are swapped
    var pS2 = new Production(S, List.of(A));
    var pB  = new Production(A, List.of(x, y));
    var pA  = new Production(B, List.of(x));

    var grammar = new Grammar(S, List.of(pS1, pS2, pB, pA));

    var precLow  = new Precedence(1, Precedence.Associativity.LEFT);
    var precHigh = new Precedence(2, Precedence.Associativity.LEFT);
    var precedenceMap = Map.of(pA, precLow, x, precHigh);

    LALRVerifier.verify(grammar, precedenceMap, ERROR_REPORTER);
  }

  @Test
  public void verifyEmptyProduction() {
    // S -> A num
    // A -> ε | "+"
    // This tests that epsilon productions are handled correctly:
    // the verifier must correctly compute FIRST/FOLLOW through nullable symbols.
    var A = new NonTerminal("A");
    var pS  = new Production(S, List.of(A, NUM));
    var pAe = new Production(A, List.of());         // A -> ε
    var pAp = new Production(A, List.of(PLUS));     // A -> +

    var grammar = new Grammar(S, List.of(pS, pAe, pAp));

    LALRVerifier.verify(grammar, Map.of(), ERROR_REPORTER);
  }

  @Test
  public void verifyRightAssociativity() {
    // E -> E + E | num   (RIGHT associative +)
    // e.g. a + b + c is parsed as a + (b + c)
    var pPlus = new Production(E, List.of(E, PLUS, E));
    var pNum  = new Production(E, List.of(NUM));
    var grammar = new Grammar(E, List.of(pPlus, pNum));

    // Same precedence level as the production, but RIGHT associative:
    // on a tie the shift wins, so + binds to the right.
    var prec = new Precedence(1, Precedence.Associativity.RIGHT);
    var precedenceMap = Map.of(PLUS, prec, pPlus, prec);

    LALRVerifier.verify(grammar, precedenceMap, ERROR_REPORTER);
  }

  @Test
  public void verifyFullyNullableProduction() {
    // S -> A B
    // A -> ε
    // B -> ε
    // Both A and B are nullable, so FIRST(S) must contain ε.
    var A = new NonTerminal("A");
    var B = new NonTerminal("B");
    var pS  = new Production(S, List.of(A, B));
    var pAe = new Production(A, List.of());   // A -> ε
    var pBe = new Production(B, List.of());   // B -> ε

    var grammar = new Grammar(S, List.of(pS, pAe, pBe));

    LALRVerifier.verify(grammar, Map.of(),  ERROR_REPORTER);
  }

  @Test
  public void verifyLALRGrammar() {
    // Grammar (DeRemer 1971) — LALR(1) but not SLR(1):
    //   S → L = R  |  R
    //   L → * R    |  id
    //   R → L
    //
    // SLR(1) fails because '=' ∈ FOLLOW(R) globally (via R → L and S → L = R),
    // producing a spurious shift/reduce conflict on '=' in the state that contains
    // both [S → L • = R] and [R → L •].
    // LALR(1) succeeds because the per-state lookahead for [R → L •] in that
    // state is only {EOF} — '=' is never valid there.
    var S = new NonTerminal("S");
    var L = new NonTerminal("L");
    var R = new NonTerminal("R");
    var EQ  = new Terminal("=");
    var MUL = new Terminal("*");
    var ID  = new Terminal("id");
    var grammar = new Grammar(S, List.of(
        new Production(S, List.of(L, EQ, R)),
        new Production(S, List.of(R)),
        new Production(L, List.of(MUL, R)),
        new Production(L, List.of(ID)),
        new Production(R, List.of(L))
    ));

    LALRVerifier.verify(grammar, Map.of(), ERROR_REPORTER);
  }



  private static String verifyAndDump(Grammar grammar, Map<? extends PrecedenceEntity, Precedence> precedenceMap) {
    var buf = new ByteArrayOutputStream();
    var out = new PrintStream(buf);
    LALRVerifier.verify(grammar, precedenceMap, out, true, _ -> {});
    return buf.toString();
  }

  @Test
  public void verifyAndDumpSingleProduction() {
    // E → num
    var E   = new NonTerminal("E");
    var NUM = new Terminal("num");

    var pNum    = new Production(E, List.of(NUM));
    var grammar = new Grammar(E, List.of(pNum));

    var output = verifyAndDump(grammar, Map.of());

    assertEquals("""
        ── State 0 ─────────────────────────────────
           E' :  • E
           E :   • num
          ······································
           goto( num                  ) → 2
           goto( E                    ) → 1
        
        ── State 1 ─────────────────────────────────
           E' :  E •
          ······································
           accept()                     on [$]
        
        ── State 2 ─────────────────────────────────
           E :  num •
          ······································
           reduce( E : num            ) on [$]
        
        """, output);
  }

  @Test
  public void verifyAndDumpAdditionConflicts() {
    // E → E '+' E
    // E → num
    var E    = new NonTerminal("E");
    var NUM  = new Terminal("num");
    var PLUS = new Terminal("+");

    var pNum    = new Production(E, List.of(NUM));
    var pAdd    = new Production(E, List.of(E, PLUS, E));
    var grammar = new Grammar(E, List.of(pNum, pAdd));

    var output = verifyAndDump(grammar, Map.of());

    assertEquals("""
        ── State 0 ─────────────────────────────────
           E' :  • E
           E :   • num
           E :   • E + E
          ······································
           goto( num                  ) → 2
           goto( E                    ) → 1
        
        ── State 1 ─────────────────────────────────
           E' :  E •
           E :   E • + E
          ······································
           goto( +                    ) → 3
           accept()                     on [$]
        
        ── State 2 ─────────────────────────────────
           E :  num •
          ······································
           reduce( E : num            ) on [$, +]
        
        ── State 3 ─────────────────────────────────
           E :  E + • E
           E :  • num
           E :  • E + E
          ······································
           goto( num                  ) → 2
           goto( E                    ) → 4
        
        ── State 4 ─────────────────────────────────
           E :  E + E •
           E :  E • + E
          ······································
           goto( +                    ) → 3 🔥
           reduce( E : E + E          ) on [$, + 🔥]
        
        """, output);
  }

  @Test
  public void verifyAndDumpAdditionWithPrecedence() {
    // E → E '+' E
    // E → num
    var E    = new NonTerminal("E");
    var NUM  = new Terminal("num");
    var PLUS = new Terminal("+");

    var pNum    = new Production(E, List.of(NUM));
    var pAdd    = new Production(E, List.of(E, PLUS, E));
    var grammar = new Grammar(E, List.of(pNum, pAdd));

    var precLeft = new Precedence(1, Precedence.Associativity.LEFT);
    var precedenceMap = Map.of(PLUS, precLeft, pAdd, precLeft);

    var output = verifyAndDump(grammar, precedenceMap);

    assertEquals("""
        ── State 0 ─────────────────────────────────
           E' :  • E
           E :   • num
           E :   • E + E
          ······································
           goto( num                  ) → 2
           goto( E                    ) → 1
        
        ── State 1 ─────────────────────────────────
           E' :  E •
           E :   E • + E
          ······································
           goto( +                    ) → 3
           accept()                     on [$]
        
        ── State 2 ─────────────────────────────────
           E :  num •
          ······································
           reduce( E : num            ) on [$, +]
        
        ── State 3 ─────────────────────────────────
           E :  E + • E
           E :  • num
           E :  • E + E
          ······································
           goto( num                  ) → 2
           goto( E                    ) → 4
        
        ── State 4 ─────────────────────────────────
           E :  E + E •
           E :  E • + E
          ······································
           goto( +                    ) → 3 ❌
           reduce( E : E + E          ) on [$, +]
        
        """, output);
  }

  @Test
  public void verifyAndDumpMultiplicationConflicts() {
    // E → E '+' E
    // E → E '*' E
    // E → num
    var E    = new NonTerminal("E");
    var NUM  = new Terminal("num");
    var PLUS = new Terminal("+");
    var MUL  = new Terminal("*");

    var pNum    = new Production(E, List.of(NUM));
    var pAdd    = new Production(E, List.of(E, PLUS, E));
    var pMul    = new Production(E, List.of(E, MUL, E));
    var grammar = new Grammar(E, List.of(pNum, pAdd, pMul));

    var output = verifyAndDump(grammar, Map.of());

    assertEquals("""
        ── State 0 ─────────────────────────────────
           E' :  • E
           E :   • num
           E :   • E + E
           E :   • E * E
          ······································
           goto( num                  ) → 2
           goto( E                    ) → 1
        
        ── State 1 ─────────────────────────────────
           E' :  E •
           E :   E • + E
           E :   E • * E
          ······································
           goto( *                    ) → 4
           goto( +                    ) → 3
           accept()                     on [$]
        
        ── State 2 ─────────────────────────────────
           E :  num •
          ······································
           reduce( E : num            ) on [$, *, +]
        
        ── State 3 ─────────────────────────────────
           E :  E + • E
           E :  • num
           E :  • E + E
           E :  • E * E
          ······································
           goto( num                  ) → 2
           goto( E                    ) → 5
        
        ── State 4 ─────────────────────────────────
           E :  E * • E
           E :  • num
           E :  • E + E
           E :  • E * E
          ······································
           goto( num                  ) → 2
           goto( E                    ) → 6
        
        ── State 5 ─────────────────────────────────
           E :  E + E •
           E :  E • + E
           E :  E • * E
          ······································
           goto( *                    ) → 4 🔥
           goto( +                    ) → 3 🔥
           reduce( E : E + E          ) on [$, * 🔥, + 🔥]
        
        ── State 6 ─────────────────────────────────
           E :  E * E •
           E :  E • + E
           E :  E • * E
          ······································
           goto( *                    ) → 4 🔥
           goto( +                    ) → 3 🔥
           reduce( E : E * E          ) on [$, * 🔥, + 🔥]
        
        """, output);
  }

  @Test
  public void verifyAndDumpMultiplicationWithPrecedence() {
    // E → E '+' E
    // E → E '*' E
    // E → num
    var E    = new NonTerminal("E");
    var NUM  = new Terminal("num");
    var PLUS = new Terminal("+");
    var MUL  = new Terminal("*");

    var pNum    = new Production(E, List.of(NUM));
    var pAdd    = new Production(E, List.of(E, PLUS, E));
    var pMul    = new Production(E, List.of(E, MUL, E));
    var grammar = new Grammar(E, List.of(pNum, pAdd, pMul));

    var precPlus = new Precedence(1, Precedence.Associativity.LEFT);
    var precMul  = new Precedence(2, Precedence.Associativity.LEFT);
    var precedenceMap = Map.of(
        PLUS, precPlus, pAdd, precPlus,
        MUL,  precMul,  pMul, precMul
    );

    var output = verifyAndDump(grammar, precedenceMap);

    assertEquals("""
        ── State 0 ─────────────────────────────────
           E' :  • E
           E :   • num
           E :   • E + E
           E :   • E * E
          ······································
           goto( num                  ) → 2
           goto( E                    ) → 1
        
        ── State 1 ─────────────────────────────────
           E' :  E •
           E :   E • + E
           E :   E • * E
          ······································
           goto( *                    ) → 4
           goto( +                    ) → 3
           accept()                     on [$]
        
        ── State 2 ─────────────────────────────────
           E :  num •
          ······································
           reduce( E : num            ) on [$, *, +]
        
        ── State 3 ─────────────────────────────────
           E :  E + • E
           E :  • num
           E :  • E + E
           E :  • E * E
          ······································
           goto( num                  ) → 2
           goto( E                    ) → 5
        
        ── State 4 ─────────────────────────────────
           E :  E * • E
           E :  • num
           E :  • E + E
           E :  • E * E
          ······································
           goto( num                  ) → 2
           goto( E                    ) → 6
        
        ── State 5 ─────────────────────────────────
           E :  E + E •
           E :  E • + E
           E :  E • * E
          ······································
           goto( *                    ) → 4
           goto( +                    ) → 3 ❌
           reduce( E : E + E          ) on [$, *, +]
        
        ── State 6 ─────────────────────────────────
           E :  E * E •
           E :  E • + E
           E :  E • * E
          ······································
           goto( *                    ) → 4 ❌
           goto( +                    ) → 3 ❌
           reduce( E : E * E          ) on [$, *, +]
        
        """, output);
  }
}