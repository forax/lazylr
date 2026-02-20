package com.github.forax.lazylr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class LarkGrammarParserTest {
//
// This test encodes a simplified but representative BNF for Lark's own
// grammar language (https://lark-parser.readthedocs.io/en/latest/grammar.html),
// covering:
//   start      : definition_list
//   definition_list : definition
//                   | definition_list definition
//   definition : rule_def
//              | terminal_def
//              | EOL
//   rule_def   : RULE_NAME COLON expansion_list
//   terminal_def : TERM_NAME COLON expansion_list
//   expansion_list : expansion EOL
//                  | expansion_list PIPE expansion EOL
//   expansion  : expr_list
//   expr_list  : expr
//              | expr_list expr
//   expr       : atom
//              | atom PLUS
//              | atom STAR
//              | atom QUESTION
//   atom       : RULE_NAME
//              | TERM_NAME
//              | STRING
//
// The precedence table resolves the one genuine shift/reduce tension:
// PIPE (alternation) has lower precedence than juxtaposition (sequence),
// so "a b | c" binds as "(a b) | c" rather than "a (b | c)".
//

  @Test
  public void larkGrammarSyntax() {
    // -- Non-terminals
    var start = new NonTerminal("start");
    var definition_list = new NonTerminal("definition_list");
    var definition = new NonTerminal("definition");
    var rule_def = new NonTerminal("rule_def");
    var terminal_def = new NonTerminal("terminal_def");
    var expansion_list = new NonTerminal("expansion_list");
    var expansion = new NonTerminal("expansion");
    var expr_list = new NonTerminal("expr_list");
    var expr = new NonTerminal("expr");
    var atom = new NonTerminal("atom");

    // -- Terminals
    var RULE_NAME = new Terminal("RULE_NAME"); // lowercase name  e.g. rule_def
    var TERM_NAME = new Terminal("TERM_NAME"); // UPPERCASE name  e.g. STRING
    var STRING = new Terminal("STRING");       // "literal"
    var COLON = new Terminal("COLON");         // :
    var PIPE = new Terminal("PIPE");           // |
    var PLUS = new Terminal("PLUS");           // +
    var STAR = new Terminal("STAR");           // *
    var QUESTION = new Terminal("QUESTION");   // ?
    var EOL = new Terminal("EOL");             // end of line

    // -- Grammar
    var grammar = new Grammar(start, List.of(
        // start : definition_list
        new Production(start, List.of(definition_list)),

        // definition_list : definition
        //                 | definition_list definition
        new Production(definition_list, List.of(definition)),
        new Production(definition_list, List.of(definition_list, definition)),

        // definition : rule_def
        //            | terminal_def
        //            | EOL
        new Production(definition, List.of(rule_def)),
        new Production(definition, List.of(terminal_def)),
        new Production(definition, List.of(EOL)),

        // rule_def : RULE_NAME : expansion_list EOL
        new Production(rule_def, List.of(RULE_NAME, COLON, expansion_list)),

        // terminal_def : TERM_NAME : expansion_list
        new Production(terminal_def, List.of(TERM_NAME, COLON, expansion_list)),

        // expansion_list : expansion
        //                | expansion_list | expansion
        new Production(expansion_list, List.of(expansion, EOL)),
        new Production(expansion_list, List.of(expansion_list, PIPE, expansion, EOL)),

        // expansion : expr_list
        new Production(expansion, List.of(expr_list)),

        // expr_list : expr | expr_list expr
        new Production(expr_list, List.of(expr)),
        new Production(expr_list, List.of(expr_list, expr)),

        // expr : atom
        //      | atom+
        //      | atom*
        //      | atom?
        new Production(expr, List.of(atom)),
        new Production(expr, List.of(atom, PLUS)),
        new Production(expr, List.of(atom, STAR)),
        new Production(expr, List.of(atom, QUESTION)),

        // atom : RULE_NAME
        //      | TERM_NAME
        //      | STRING
        new Production(atom, List.of(RULE_NAME)),
        new Production(atom, List.of(TERM_NAME)),
        new Production(atom, List.of(STRING))
    ));

    // -- Precedence
    // PIPE is the lowest-precedence operator (alternation).
    // Quantifiers bind tightest (they are postfix on a single atom).
    // Juxtaposition (sequence) sits between the two; it has no explicit
    // terminal so we only need to declare PIPE as lower than quantifiers.
    var precedence = Map.<PrecedenceEntity, Precedence>of(
        PIPE, new Precedence(10, Precedence.Associativity.LEFT),
        PLUS, new Precedence(20, Precedence.Associativity.LEFT),
        STAR, new Precedence(20, Precedence.Associativity.LEFT),
        QUESTION, new Precedence(20, Precedence.Associativity.LEFT)
    );

    // -- Sample input: two definitions
    //
    //   rule_def:     RULE_NAME : RULE_NAME TERM_NAME+
    //   terminal_def: TERM_NAME : STRING
    //                           | STRING STRING?
    //
    // Token stream (whitespace is not a terminal in this grammar model):
    //
    //   RULE_NAME COLON RULE_NAME TERM_NAME PLUS EOL
    //   TERM_NAME COLON STRING EOL PIPE STRING STRING QUESTION EOL
    //   EOL
    //
    var input = List.of(
        RULE_NAME, COLON, RULE_NAME, TERM_NAME, PLUS, EOL,  // rule_def
        TERM_NAME, COLON, STRING, EOL, PIPE, STRING, STRING, QUESTION, EOL,  // terminal_def
        EOL   // empty line
    );

    var parser = Parser.createParser(grammar, precedence);
    var result = new StringBuilder();

    parser.parse(input.iterator(), new ParserListener() {
      @Override
      public void onShift(Terminal token) {
        //IO.println("Shift " + token.name());
        result.append("Shift ").append(token.name()).append('\n');
      }

      @Override
      public void onReduce(Production production) {
        //IO.println("Reduce " + production.name());
        result.append("Reduce ").append(production.name()).append('\n');
      }
    });

    assertEquals("""
        Shift RULE_NAME
        Shift COLON
        Shift RULE_NAME
        Reduce atom : RULE_NAME
        Reduce expr : atom
        Reduce expr_list : expr
        Shift TERM_NAME
        Reduce atom : TERM_NAME
        Shift PLUS
        Reduce expr : atom PLUS
        Reduce expr_list : expr_list expr
        Reduce expansion : expr_list
        Shift EOL
        Reduce expansion_list : expansion EOL
        Reduce rule_def : RULE_NAME COLON expansion_list
        Reduce definition : rule_def
        Reduce definition_list : definition
        Shift TERM_NAME
        Shift COLON
        Shift STRING
        Reduce atom : STRING
        Reduce expr : atom
        Reduce expr_list : expr
        Reduce expansion : expr_list
        Shift EOL
        Reduce expansion_list : expansion EOL
        Shift PIPE
        Shift STRING
        Reduce atom : STRING
        Reduce expr : atom
        Reduce expr_list : expr
        Shift STRING
        Reduce atom : STRING
        Shift QUESTION
        Reduce expr : atom QUESTION
        Reduce expr_list : expr_list expr
        Reduce expansion : expr_list
        Shift EOL
        Reduce expansion_list : expansion_list PIPE expansion EOL
        Reduce terminal_def : TERM_NAME COLON expansion_list
        Reduce definition : terminal_def
        Reduce definition_list : definition_list definition
        Shift EOL
        Reduce definition : EOL
        Reduce definition_list : definition_list definition
        Reduce start : definition_list
        Reduce start' : start
        """, result.toString());
  }
}
