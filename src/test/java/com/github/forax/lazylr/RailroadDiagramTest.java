package com.github.forax.lazylr;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public final class RailroadDiagramTest {

  @Nested
  public class SingleTerminalTests {

    @Test
    public void singleTerminalProduction() {
      var S = new NonTerminal("S");
      var id = new Terminal("id");
      var grammar = new Grammar(S, List.of(
          new Production(S, List.of(id))
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          S:
          ○─[id]─►
          """, result);
    }

    @Test
    public void singleNonTerminalProduction() {
      var E = new NonTerminal("E");
      var grammar = new Grammar(E, List.of(
          new Production(E, List.of(E))
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          E:
          ○─<E>─►
          """, result);
    }

    @Test
    public void singleEpsilonProduction() {
      var S = new NonTerminal("S");
      var grammar = new Grammar(S, List.of(
          new Production(S, List.of())
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          S:
          ○─[ε]─►
          """, result);
    }
  }


  @Nested
  public class SequenceTests {

    @Test
    public void twoTerminalSequence() {
      var S = new NonTerminal("S");
      var id = new Terminal("id");
      var num = new Terminal("num");
      var grammar = new Grammar(S, List.of(
          new Production(S, List.of(id, num))
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          S:
          ○─[id]──[num]─►
          """, result);
    }

    @Test
    public void threeTerminalSequence() {
      var S = new NonTerminal("S");
      var lp = new Terminal("(");
      var id = new Terminal("id");
      var rp = new Terminal(")");
      var grammar = new Grammar(S, List.of(
          new Production(S, List.of(lp, id, rp))
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          S:
          ○─[(]──[id]──[)]─►
          """, result);
    }
  }


  @Nested
  public class AlternativeTests {

    @Test
    public void twoAlternativesProduceMultipleLines() {
      var S = new NonTerminal("S");
      var id = new Terminal("id");
      var num = new Terminal("num");
      var grammar = new Grammar(S, List.of(
          new Production(S, List.of(id)),
          new Production(S, List.of(num))
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          S:
          ○─┌─[id]──┐─►
            └─[num]─┘ \s
          """, result);
    }

    @Test
    public void threeAlternativesUseMidJunctions() {
      var S = new NonTerminal("S");
      var id = new Terminal("id");
      var num = new Terminal("num");
      var lp = new Terminal("(");
      var grammar = new Grammar(S, List.of(
          new Production(S, List.of(id)),
          new Production(S, List.of(num)),
          new Production(S, List.of(lp))
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          S:
          ○─┌─[id]──┐─►
            ├─[num]─┤ \s
            └─[(]───┘ \s
          """, result);
    }

    @Test
    public void alternativesAlignedToSameWidth() {
      var S = new NonTerminal("S");
      var id = new Terminal("id");
      var num = new Terminal("num");
      var lp = new Terminal("(");
      var rp = new Terminal(")");
      var grammar = new Grammar(S, List.of(
          new Production(S, List.of(id)),
          new Production(S, List.of(num)),
          new Production(S, List.of(lp, rp))
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          S:
          ○─┌─[id]─────┐─►
            ├─[num]────┤ \s
            └─[(]──[)]─┘ \s
          """, result);
    }

    @Test
    public void epsilonProductionRenderedAsEpsilonSymbol() {
      var S = new NonTerminal("S");
      var id = new Terminal("id");
      var grammar = new Grammar(S, List.of(
          new Production(S, List.of(id)),
          new Production(S, List.of())
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          S:
          ○─┌─[id]─┐─►
            └─[ε]──┘ \s
          """, result);
    }
  }


  @Nested
  public class NonTerminalReferenceTests {

    @Test
    public void recursiveNonTerminalRenderedAsAngleBrackets() {
      var E = new NonTerminal("E");
      var plus = new Terminal("+");
      var grammar = new Grammar(E, List.of(
          new Production(E, List.of(E, plus, E))
      ));
      var result = RailroadDiagram.generate(grammar, true);
      assertEquals("""
          E:
          ○─<E>──[+]──<E>─►
          """, result);
    }

    @Test
    public void nonRecursiveNonTerminalInlinedWhenFlagTrue() {
      var E = new NonTerminal("E");
      var T = new NonTerminal("T");
      var id = new Terminal("id");
      var num = new Terminal("num");
      var grammar = new Grammar(E, List.of(
          new Production(E, List.of(T)),
          new Production(T, List.of(id)),
          new Production(T, List.of(num))
      ));
      var result = RailroadDiagram.generate(grammar, true);
      assertEquals("""
          E:
          ○─┌─[id]──┐─►
            └─[num]─┘ \s
          """, result);
    }

    @Test
    public void nonRecursiveNonTerminalNotInlinedWhenFlagFalse() {
      var E = new NonTerminal("E");
      var T = new NonTerminal("T");
      var id = new Terminal("id");
      var num = new Terminal("num");
      var grammar = new Grammar(E, List.of(
          new Production(E, List.of(T)),
          new Production(T, List.of(id)),
          new Production(T, List.of(num))
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          E:
          ○─<T>─►
          T:
          ○─┌─[id]──┐─►
            └─[num]─┘ \s
          """, result);
    }

    @Test
    public void inlinedNonRecursiveNonTerminalNotPrintedSeparately() {
      var E = new NonTerminal("E");
      var T = new NonTerminal("T");
      var id = new Terminal("id");
      var num = new Terminal("num");
      var grammar = new Grammar(E, List.of(
          new Production(E, List.of(T)),
          new Production(T, List.of(id)),
          new Production(T, List.of(num))
      ));
      var result = RailroadDiagram.generate(grammar, true);
      assertEquals("""
          E:
          ○─┌─[id]──┐─►
            └─[num]─┘ \s
          """, result);
    }

    @Test
    public void nonInlinedNonTerminalPrintedSeparately() {
      var E = new NonTerminal("E");
      var T = new NonTerminal("T");
      var id = new Terminal("id");
      var num = new Terminal("num");
      var grammar = new Grammar(E, List.of(
          new Production(E, List.of(T)),
          new Production(T, List.of(id)),
          new Production(T, List.of(num))
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          E:
          ○─<T>─►
          T:
          ○─┌─[id]──┐─►
            └─[num]─┘ \s
          """, result);
    }
  }


  @Nested
  public class RecursionDetectionTests {

    @Test
    public void directlyRecursiveNonTerminalRenderedAsRef() {
      var E = new NonTerminal("E");
      var plus = new Terminal("+");
      var grammar = new Grammar(E, List.of(
          new Production(E, List.of(E, plus, E))
      ));
      var result = RailroadDiagram.generate(grammar, true);
      assertEquals("""
          E:
          ○─<E>──[+]──<E>─►
          """, result);
    }

    @Test
    public void mutuallyRecursiveNonTerminalsRenderedAsRefs() {
      var A = new NonTerminal("A");
      var B = new NonTerminal("B");
      var id = new Terminal("id");
      var num = new Terminal("num");
      var grammar = new Grammar(A, List.of(
          new Production(A, List.of(B, id)),
          new Production(B, List.of(A, num))
      ));
      var result = RailroadDiagram.generate(grammar, true);
      assertEquals("""
          A:
          ○─<B>──[id]─►
          B:
          ○─<A>──[num]─►
          """, result);
    }

    @Test
    public void indirectlyRecursiveNonTerminalRenderedAsRef() {
      var E = new NonTerminal("E");
      var T = new NonTerminal("T");
      var plus = new Terminal("+");
      var id = new Terminal("id");
      var grammar = new Grammar(E, List.of(
          new Production(E, List.of(T)),
          new Production(T, List.of(E, plus, id))
      ));
      var result = RailroadDiagram.generate(grammar, true);
      assertEquals("""
          E:
          ○─<T>─►
          T:
          ○─<E>──[+]──[id]─►
          """, result);
    }

    @Test
    public void nonRecursiveGrammarNoAngleBrackets() {
      var S = new NonTerminal("S");
      var A = new NonTerminal("A");
      var id = new Terminal("id");
      var grammar = new Grammar(S, List.of(
          new Production(S, List.of(A)),
          new Production(A, List.of(id))
      ));
      var result = RailroadDiagram.generate(grammar, true);
      assertEquals("""
          S:
          ○─[id]─►
          """, result);
    }
  }


  @Nested
  public class TopLevelTests {

    @Test
    public void startAndEndOnSameLineForSingleProduction() {
      var S = new NonTerminal("S");
      var id = new Terminal("id");
      var grammar = new Grammar(S, List.of(new Production(S, List.of(id))));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          S:
          ○─[id]─►
          """, result);
    }

    @Test
    public void outputContainsStartAndEndMarker() {
      var S = new NonTerminal("S");
      var id = new Terminal("id");
      var grammar = new Grammar(S, List.of(
          new Production(S, List.of(id)),
          new Production(S, List.of(id, id))
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          S:
          ○─┌─[id]───────┐─►
            └─[id]──[id]─┘ \s
          """, result);
    }

    @Test
    public void nonTerminalHeaderPrecedesItsBlock() {
      var E = new NonTerminal("E");
      var plus = new Terminal("+");
      var id = new Terminal("id");
      var grammar = new Grammar(E, List.of(
          new Production(E, List.of(E, plus, E)),
          new Production(E, List.of(id))
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          E:
          ○─┌─<E>──[+]──<E>─┐─►
            └─[id]──────────┘ \s
          """, result);
    }
  }


  @Nested
  public class IntegrationTests {

    @Test
    public void classicExpressionGrammarNoInlining() {
      var E = new NonTerminal("E");
      var T = new NonTerminal("T");
      var plus = new Terminal("+");
      var mul = new Terminal("*");
      var id = new Terminal("id");
      var num = new Terminal("num");
      var grammar = new Grammar(E, List.of(
          new Production(E, List.of(T)),
          new Production(E, List.of(E, plus, E)),
          new Production(E, List.of(E, mul, E)),
          new Production(T, List.of(id)),
          new Production(T, List.of(num))
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          E:
          ○─┌─<T>───────────┐─►
            ├─<E>──[+]──<E>─┤ \s
            └─<E>──[*]──<E>─┘ \s
          T:
          ○─┌─[id]──┐─►
            └─[num]─┘ \s
          """, result);
    }

    @Test
    public void classicExpressionGrammarWithInlining() {
      var E = new NonTerminal("E");
      var T = new NonTerminal("T");
      var plus = new Terminal("+");
      var mul = new Terminal("*");
      var id = new Terminal("id");
      var num = new Terminal("num");
      var grammar = new Grammar(E, List.of(
          new Production(E, List.of(T)),
          new Production(E, List.of(E, plus, E)),
          new Production(E, List.of(E, mul, E)),
          new Production(T, List.of(id)),
          new Production(T, List.of(num))
      ));
      var result = RailroadDiagram.generate(grammar, true);
      assertEquals("""
          E:
          ○─┌─┌─[id]──┐─────┐─►
            │ └─[num]─┘     │ \s
            ├─<E>──[+]──<E>─┤ \s
            └─<E>──[*]──<E>─┘ \s
          """, result);
    }

    @Test
    public void grammarWithEpsilonProduction() {
      var S = new NonTerminal("S");
      var id = new Terminal("id");
      var grammar = new Grammar(S, List.of(
          new Production(S, List.of(id, S)),
          new Production(S, List.of())
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          S:
          ○─┌─[id]──<S>─┐─►
            └─[ε]───────┘ \s
          """, result);
    }

    @Test
    public void multipleNonTerminalsEachGetHeader() {
      var E = new NonTerminal("E");
      var T = new NonTerminal("T");
      var plus = new Terminal("+");
      var id = new Terminal("id");
      var num = new Terminal("num");
      var grammar = new Grammar(E, List.of(
          new Production(E, List.of(E, plus, T)),
          new Production(E, List.of(T)),
          new Production(T, List.of(id)),
          new Production(T, List.of(num))
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          E:
          ○─┌─<E>──[+]──<T>─┐─►
            └─<T>───────────┘ \s
          T:
          ○─┌─[id]──┐─►
            └─[num]─┘ \s
          """, result);
    }

    @Test
    public void allBranchLinesHaveSameLength() {
      var E = new NonTerminal("E");
      var plus = new Terminal("+");
      var mul = new Terminal("*");
      var id = new Terminal("id");
      var grammar = new Grammar(E, List.of(
          new Production(E, List.of(E, plus, E)),
          new Production(E, List.of(E, mul, E)),
          new Production(E, List.of(id))
      ));
      var result = RailroadDiagram.generate(grammar, false);
      assertEquals("""
          E:
          ○─┌─<E>──[+]──<E>─┐─►
            ├─<E>──[*]──<E>─┤ \s
            └─[id]──────────┘ \s
          """, result);
    }
  }
}