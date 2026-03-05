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
            └─[num]─┘
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
            ├─[num]─┤
            └─[(]───┘
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
            ├─[num]────┤
            └─[(]──[)]─┘
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
            └─[ε]──┘
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
            └─[num]─┘
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
            └─[num]─┘
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
            └─[num]─┘
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
            └─[num]─┘
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
            └─[id]──[id]─┘
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
            └─[id]──────────┘
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
            ├─<E>──[+]──<E>─┤
            └─<E>──[*]──<E>─┘
          T:
          ○─┌─[id]──┐─►
            └─[num]─┘
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
            │ └─[num]─┘     │
            ├─<E>──[+]──<E>─┤
            └─<E>──[*]──<E>─┘
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
            └─[ε]───────┘
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
            └─<T>───────────┘
          T:
          ○─┌─[id]──┐─►
            └─[num]─┘
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
            ├─<E>──[*]──<E>─┤
            └─[id]──────────┘
          """, result);
    }
  }

  @Nested
  public class JSONTests {
    @Test
    public void jsonGrammar() {
      var mg = MetaGrammar.create("""
          tokens {
            string: /"(?:\\\\.|[^"\\\\])*"/
            number: /-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?/
            true:   /true/
            false:  /false/
            null:   /null/
          
            /[ \\t\\n\\r]+/
          }
          
          grammar {
            JSON: VALUE
          
            VALUE: string
            VALUE: number
            VALUE: OBJECT
            VALUE: ARRAY
            VALUE: true
            VALUE: false
            VALUE: null
          
            OBJECT: '{' '}'
            OBJECT: '{' MEMBERS '}'
          
            MEMBERS: PAIR
            MEMBERS: MEMBERS ',' PAIR
          
            PAIR: string ':' VALUE
          
            ARRAY: '[' ']'
            ARRAY: '[' ELEMENTS ']'
          
            ELEMENTS: VALUE
            ELEMENTS: ELEMENTS ',' VALUE
          }
          """);

      //LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), error -> fail(error));
      var result = RailroadDiagram.generate(mg.grammar(), false);
      assertEquals("""
          JSON:
          ○─<VALUE>─►
          VALUE:
          ○─┌─[string]─┐─►
            ├─[number]─┤
            ├─<OBJECT>─┤
            ├─<ARRAY>──┤
            ├─[true]───┤
            ├─[false]──┤
            └─[null]───┘
          OBJECT:
          ○─┌─[{]──[}]────────────┐─►
            └─[{]──<MEMBERS>──[}]─┘
          MEMBERS:
          ○─┌─<PAIR>─────────────────┐─►
            └─<MEMBERS>──[,]──<PAIR>─┘
          PAIR:
          ○─[string]──[:]──<VALUE>─►
          ARRAY:
          ○─┌─[[]──[]]─────────────┐─►
            └─[[]──<ELEMENTS>──[]]─┘
          ELEMENTS:
          ○─┌─<VALUE>──────────────────┐─►
            └─<ELEMENTS>──[,]──<VALUE>─┘
          """, result);
    }
  }


  @Nested
  public class JavaScript {
    @Test
    public void javaScriptGrammar() {
      var mg = MetaGrammar.create("""
          tokens {
            identifier: /[a-zA-Z_$][a-zA-Z0-9_$]*/
            number:     /[0-9]+(?:\\.[0-9]+)?/
            string:     /"(?:\\\\.|[^"\\\\])*"/
          
            true:  /true/
            false: /false/
            null:  /null/
          
            var: /var/
            function: /function/
            return: /return/
            if: /if/
            else: /else/
            while: /while/
            for: /for/
            break: /break/
            continue: /continue/
          
            /[ \\t\\n\\r]+/
          }
          
          precedence {
            right: if
            right: else
          
            right: '='
            left:  '||'
            left:  '&&'
            left:  '==', '!='
            left:  '<', '>', '<=', '>='
            left:  '+', '-'
            left:  '*', '/', '%'
            right: '!'
          }
          
          grammar {
          
          PROGRAM:
          PROGRAM: PROGRAM STATEMENT
          
          STATEMENT: ';'
          STATEMENT: EXPRESSION ';'
          STATEMENT: VARIABLE_DECL ';'
          STATEMENT: RETURN_STMT ';'
          STATEMENT: BREAK_STMT ';'
          STATEMENT: CONTINUE_STMT ';'
          STATEMENT: IF_STMT
          STATEMENT: WHILE_STMT
          STATEMENT: FOR_STMT
          STATEMENT: FUNCTION_DECL
          STATEMENT: BLOCK
          
          BLOCK: '{' STATEMENTS '}'
          
          STATEMENTS: STATEMENT
          STATEMENTS: STATEMENTS STATEMENT
          
          VARIABLE_DECL: var VAR_LIST
          
          VAR_LIST: VAR_ITEM
          VAR_LIST: VAR_LIST ',' VAR_ITEM
          
          VAR_ITEM: identifier
          VAR_ITEM: identifier '=' EXPRESSION
          
          FUNCTION_DECL: function identifier '(' PARAMETERS ')' BLOCK
          
          PARAMETERS:
          PARAMETERS: identifier
          PARAMETERS: PARAMETERS ',' identifier
          
          RETURN_STMT: return
          RETURN_STMT: return EXPRESSION
          
          BREAK_STMT: break
          CONTINUE_STMT: continue
          
          IF_STMT: if '(' EXPRESSION ')' STATEMENT
          IF_STMT: if '(' EXPRESSION ')' STATEMENT else STATEMENT
          
          WHILE_STMT: while '(' EXPRESSION ')' STATEMENT
          
          FOR_STMT: for '(' FOR_INIT ';' FOR_COND ';' FOR_UPDATE ')' STATEMENT
          
          FOR_INIT:
          FOR_INIT: VARIABLE_DECL
          FOR_INIT: EXPRESSION
          
          FOR_COND:
          FOR_COND: EXPRESSION
          
          FOR_UPDATE:
          FOR_UPDATE: EXPRESSION
          
          EXPRESSION: PRIMARY
          EXPRESSION: LEFT '=' EXPRESSION
          
          EXPRESSION: '!' EXPRESSION
          EXPRESSION: '-' EXPRESSION
          
          EXPRESSION: EXPRESSION '||' EXPRESSION
          EXPRESSION: EXPRESSION '&&' EXPRESSION
          
          EXPRESSION: EXPRESSION '==' EXPRESSION
          EXPRESSION: EXPRESSION '!=' EXPRESSION
          
          EXPRESSION: EXPRESSION '<' EXPRESSION
          EXPRESSION: EXPRESSION '>' EXPRESSION
          EXPRESSION: EXPRESSION '<=' EXPRESSION
          EXPRESSION: EXPRESSION '>=' EXPRESSION
          
          EXPRESSION: EXPRESSION '+' EXPRESSION
          EXPRESSION: EXPRESSION '-' EXPRESSION
          
          EXPRESSION: EXPRESSION '*' EXPRESSION
          EXPRESSION: EXPRESSION '/' EXPRESSION
          EXPRESSION: EXPRESSION '%' EXPRESSION
          
          EXPRESSION: CALL
          
          LEFT: identifier
          LEFT: LEFT '.' identifier
          LEFT: LEFT '[' EXPRESSION ']'
          
          CALL: LEFT '(' ARGUMENTS ')'
          
          ARGUMENTS:
          ARGUMENTS: EXPRESSION
          ARGUMENTS: ARGUMENTS ',' EXPRESSION
          
          PRIMARY: identifier
          PRIMARY: number
          PRIMARY: string
          PRIMARY: true
          PRIMARY: false
          PRIMARY: null
          PRIMARY: '(' EXPRESSION ')'
          PRIMARY: ARRAY
          PRIMARY: OBJECT
          
          ARRAY: '[' ']'
          ARRAY: '[' ELEMENTS ']'
          
          ELEMENTS: EXPRESSION
          ELEMENTS: ELEMENTS ',' EXPRESSION
          
          OBJECT: '{' '}'
          OBJECT: '{' MEMBERS '}'
          
          MEMBERS: MEMBER
          MEMBERS: MEMBERS ',' MEMBER
          
          MEMBER: identifier ':' EXPRESSION
          MEMBER: string ':' EXPRESSION
          
          }
          """);

      //LALRVerifier.verify(mg.grammar(), mg.precedenceMap(), error -> fail(error));
      var result = RailroadDiagram.generate(mg.grammar(), false);
      assertEquals("""
        PROGRAM:
        ○─┌─[ε]────────────────────┐─►
          └─<PROGRAM>──<STATEMENT>─┘
        STATEMENT:
        ○─┌─[;]──────────────────┐─►
          ├─<EXPRESSION>──[;]────┤
          ├─<VARIABLE_DECL>──[;]─┤
          ├─<RETURN_STMT>──[;]───┤
          ├─<BREAK_STMT>──[;]────┤
          ├─<CONTINUE_STMT>──[;]─┤
          ├─<IF_STMT>────────────┤
          ├─<WHILE_STMT>─────────┤
          ├─<FOR_STMT>───────────┤
          ├─<FUNCTION_DECL>──────┤
          └─<BLOCK>──────────────┘
        BLOCK:
        ○─[{]──<STATEMENTS>──[}]─►
        STATEMENTS:
        ○─┌─<STATEMENT>───────────────┐─►
          └─<STATEMENTS>──<STATEMENT>─┘
        VARIABLE_DECL:
        ○─[var]──<VAR_LIST>─►
        VAR_LIST:
        ○─┌─<VAR_ITEM>──────────────────┐─►
          └─<VAR_LIST>──[,]──<VAR_ITEM>─┘
        VAR_ITEM:
        ○─┌─[identifier]────────────────────┐─►
          └─[identifier]──[=]──<EXPRESSION>─┘
        FUNCTION_DECL:
        ○─[function]──[identifier]──[(]──<PARAMETERS>──[)]──<BLOCK>─►
        PARAMETERS:
        ○─┌─[ε]─────────────────────────────┐─►
          ├─[identifier]────────────────────┤
          └─<PARAMETERS>──[,]──[identifier]─┘
        RETURN_STMT:
        ○─┌─[return]───────────────┐─►
          └─[return]──<EXPRESSION>─┘
        BREAK_STMT:
        ○─[break]─►
        CONTINUE_STMT:
        ○─[continue]─►
        IF_STMT:
        ○─┌─[if]──[(]──<EXPRESSION>──[)]──<STATEMENT>──────────────────────┐─►
          └─[if]──[(]──<EXPRESSION>──[)]──<STATEMENT>──[else]──<STATEMENT>─┘
        WHILE_STMT:
        ○─[while]──[(]──<EXPRESSION>──[)]──<STATEMENT>─►
        FOR_STMT:
        ○─[for]──[(]──<FOR_INIT>──[;]──<FOR_COND>──[;]──<FOR_UPDATE>──[)]──<STATEMENT>─►
        FOR_INIT:
        ○─┌─[ε]─────────────┐─►
          ├─<VARIABLE_DECL>─┤
          └─<EXPRESSION>────┘
        FOR_COND:
        ○─┌─[ε]──────────┐─►
          └─<EXPRESSION>─┘
        FOR_UPDATE:
        ○─┌─[ε]──────────┐─►
          └─<EXPRESSION>─┘
        EXPRESSION:
        ○─┌─<PRIMARY>────────────────────────┐─►
          ├─<LEFT>──[=]──<EXPRESSION>────────┤
          ├─[!]──<EXPRESSION>────────────────┤
          ├─[-]──<EXPRESSION>────────────────┤
          ├─<EXPRESSION>──[||]──<EXPRESSION>─┤
          ├─<EXPRESSION>──[&&]──<EXPRESSION>─┤
          ├─<EXPRESSION>──[==]──<EXPRESSION>─┤
          ├─<EXPRESSION>──[!=]──<EXPRESSION>─┤
          ├─<EXPRESSION>──[<]──<EXPRESSION>──┤
          ├─<EXPRESSION>──[>]──<EXPRESSION>──┤
          ├─<EXPRESSION>──[<=]──<EXPRESSION>─┤
          ├─<EXPRESSION>──[>=]──<EXPRESSION>─┤
          ├─<EXPRESSION>──[+]──<EXPRESSION>──┤
          ├─<EXPRESSION>──[-]──<EXPRESSION>──┤
          ├─<EXPRESSION>──[*]──<EXPRESSION>──┤
          ├─<EXPRESSION>──[/]──<EXPRESSION>──┤
          ├─<EXPRESSION>──[%]──<EXPRESSION>──┤
          └─<CALL>───────────────────────────┘
        LEFT:
        ○─┌─[identifier]───────────────────┐─►
          ├─<LEFT>──[.]──[identifier]──────┤
          └─<LEFT>──[[]──<EXPRESSION>──[]]─┘
        CALL:
        ○─<LEFT>──[(]──<ARGUMENTS>──[)]─►
        ARGUMENTS:
        ○─┌─[ε]────────────────────────────┐─►
          ├─<EXPRESSION>───────────────────┤
          └─<ARGUMENTS>──[,]──<EXPRESSION>─┘
        PRIMARY:
        ○─┌─[identifier]───────────┐─►
          ├─[number]───────────────┤
          ├─[string]───────────────┤
          ├─[true]─────────────────┤
          ├─[false]────────────────┤
          ├─[null]─────────────────┤
          ├─[(]──<EXPRESSION>──[)]─┤
          ├─<ARRAY>────────────────┤
          └─<OBJECT>───────────────┘
        ARRAY:
        ○─┌─[[]──[]]─────────────┐─►
          └─[[]──<ELEMENTS>──[]]─┘
        ELEMENTS:
        ○─┌─<EXPRESSION>──────────────────┐─►
          └─<ELEMENTS>──[,]──<EXPRESSION>─┘
        OBJECT:
        ○─┌─[{]──[}]────────────┐─►
          └─[{]──<MEMBERS>──[}]─┘
        MEMBERS:
        ○─┌─<MEMBER>─────────────────┐─►
          └─<MEMBERS>──[,]──<MEMBER>─┘
        MEMBER:
        ○─┌─[identifier]──[:]──<EXPRESSION>─┐─►
          └─[string]──[:]──<EXPRESSION>─────┘
        """, result);
    }

  }
}