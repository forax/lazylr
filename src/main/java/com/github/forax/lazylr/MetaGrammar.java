package com.github.forax.lazylr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Represents a grammar specification parsed from a DSL that describes tokens,
/// operator precedence, and production rules of a context-free grammar.
///
/// ## DSL Structure
///
/// The input is divided into three named sections, each enclosed in braces:
///
/// ```
/// tokens {
///   tokenName: /regex/
///   /ignored-regex/
/// }
/// precedence {
///   left:  '->', tokenName
///   right: '%'
/// }
/// grammar {
///   StartRule: StartRule '->' Item
///   StartRule: StartRule tokenName Item
///   StartRule: Item '%' Item
///   Item: tokenName
///   Item:
/// }
/// ```
///
/// - **tokens** — defines named and anonymous terminal symbols as regular expressions.
///   The declaration order controls lexer token priority.
///   Named terminals (e.g. `ident: /[a-z]+/`) become lexer tokens; anonymous ones
///   (e.g. `/[ \t]+/`) are matched and silently discarded.
///
/// - **precedence** — declares operator associativity and relative precedence.
///   Each line lists terminals (quoted literals or named tokens) at the same precedence level.
///   Earlier lines have lower precedence than later ones. Only `left` and `right`
///   associativity are supported.
///
/// - **grammar** — defines BNF-style production rules. The `grammar` section must contain
///   at least one production. The first rule's non-terminal becomes the start symbol.
///   Empty right-hand sides (epsilon rules) are written as a bare `Name:` line.
///   Any symbol written in single quotes (e.g. `'+'`) is automatically extracted from the
///   productions, converted to an escaped regex, and registered as a terminal.
///   No explicit declaration in the `tokens` section is required.
public final class MetaGrammar {
  private final List<Token> tokens;
  private final Grammar grammar;
  private final LinkedHashMap<PrecedenceEntity, Precedence> precedenceMap;

  private MetaGrammar(List<Token> tokens, Grammar grammar, LinkedHashMap<PrecedenceEntity, Precedence> precedenceMap) {
    this.tokens = List.copyOf(tokens);
    this.grammar = grammar;
    this.precedenceMap = precedenceMap;
    super();
  }

  /// The lexer rules derived from the `tokens` section, in priority order.
  ///
  /// @return a list of [Token] objects, ordered so that quoted (implicit)
  ///         terminals appear first, followed by named terminals, then anonymous ones.
  public List<Token> tokens() {
    return tokens;
  }

  /// The grammar derived from the `grammar` section, rooted at the first declared
  /// non-terminal.
  ///
  /// @return the [Grammar] built from all production in the specification.
  public Grammar grammar() {
    return grammar;
  }

  /// The operator precedence table derived from the `precedence` section.
  ///
  /// Keys are [PrecedenceEntity] instances (terminals) and values carry the precedence
  /// level (lower index = lower precedence) and associativity direction.
  ///
  /// @return the precedence map, in declaration order.
  public Map<PrecedenceEntity, Precedence> precedenceMap() {
    return Collections.unmodifiableMap(precedenceMap);
  }

  // grammar definition
  private static Grammar createGrammar() {
    var ident      = new Terminal("ident");
    var regex      = new Terminal("regex");
    var quoted     = new Terminal("quoted");
    var tokens     = new Terminal("tokens");
    var precedence = new Terminal("precedence");
    var grammar    = new Terminal("grammar");
    var lbrace     = new Terminal("{");
    var rbrace     = new Terminal("}");
    var colon      = new Terminal(":");
    var comma      = new Terminal(",");
    var eol        = new Terminal("eol");

    var spec         = new NonTerminal("Spec");
    var sections     = new NonTerminal("Sections");
    var section      = new NonTerminal("Section");
    var tokenRules   = new NonTerminal("TokenRules");
    var tokenRule    = new NonTerminal("TokenRule");
    var precLines    = new NonTerminal("PrecLines");
    var precLine     = new NonTerminal("PrecLine");
    var literals     = new NonTerminal("Literals");
    var grammarRules = new NonTerminal("GrammarRules");
    var grammarRule  = new NonTerminal("GrammarRule");
    var symbols      = new NonTerminal("Symbols");
    var symbol       = new NonTerminal("Symbol");
    var name         = new NonTerminal("Name");

    return new Grammar(spec, List.of(
        new Production(spec,         List.of(sections)),

        new Production(sections,     List.of(sections, section)),
        new Production(sections,     List.of()),

        new Production(section,      List.of(tokens,     lbrace, eol, tokenRules,   rbrace, eol)),
        new Production(section,      List.of(precedence, lbrace, eol, precLines,    rbrace, eol)),
        new Production(section,      List.of(grammar,    lbrace, eol, grammarRules, rbrace, eol)),
        new Production(section,      List.of(eol)),

        new Production(tokenRules,   List.of(tokenRules, tokenRule)),
        new Production(tokenRules,   List.of()),

        new Production(tokenRule,    List.of(ident, colon, regex, eol)),
        new Production(tokenRule,    List.of(regex, eol)),
        new Production(tokenRule,    List.of(eol)),

        new Production(precLines,    List.of(precLines, precLine)),
        new Production(precLines,    List.of()),

        new Production(precLine,     List.of(ident, colon, literals, eol)),
        new Production(precLine,     List.of(eol)),

        new Production(literals,     List.of(literals, comma, symbol)),
        new Production(literals,     List.of(symbol)),

        new Production(grammarRules, List.of(grammarRules, grammarRule)),
        new Production(grammarRules, List.of()),

        new Production(grammarRule,  List.of(name, colon, symbols, eol)),
        new Production(grammarRule,  List.of(name, colon, eol)),
        new Production(grammarRule,  List.of(eol)),

        new Production(symbols,      List.of(symbols, symbol)),
        new Production(symbols,      List.of(symbol)),

        new Production(symbol,       List.of(name)),
        new Production(symbol,       List.of(quoted)),

        new Production(name,         List.of(ident)),
        new Production(name,         List.of(tokens)),
        new Production(name,         List.of(precedence)),
        new Production(name,         List.of(grammar))
    ));
  }

  private static final List<Token> TOKENS = List.of(
      new Token("tokens",     "tokens"),
      new Token("precedence", "precedence"),
      new Token("grammar",    "grammar"),
      new Token("{",          "\\{"),
      new Token("}",          "\\}"),
      new Token(":",          ":"),
      new Token(",",          ","),
      new Token("regex",      "/[^/]+/"),
      new Token("quoted",     "'[^']+'"),
      new Token("ident",      "[A-Za-z_][A-Za-z0-9_]*"),
      new Token("eol",        "[\\r]?\\n"),
      new Token("\\/\\/[^\\n]*"),  // comment ignored
      new Token("[ \\t]+")         // whitespace ignored
  );

  private static final Grammar GRAMMAR = createGrammar();

  private record RawRule(String name, String regex) {}
  private record RawSymbol(String name, boolean quoted) {}
  private record RawProduction(String head, List<RawSymbol> symbols) {}
  private record RawPrecedence(String associativity, List<RawSymbol> symbols) {}

  /// Parses a grammar specification.
  ///
  /// The input must contain at least one production rule in a `grammar` section.
  /// `tokens` and `precedence` sections are optional. Quoted literals in production rules
  /// are automatically promoted to terminals without requiring an explicit entry in the
  /// `tokens` section.
  ///
  /// @param input the full text of the grammar specification; must not be `null`
  /// @return a new `MetaGrammar` reflecting the tokens, precedence rules, and productions
  ///         declared in `input`.
  /// @throws NullPointerException if `input` is `null`.
  /// @throws ParsingException if the input is syntactically or semantically invalid.
  public static MetaGrammar create(String input) {
    Objects.requireNonNull(input);

    var rules = new ArrayList<RawRule>();
    var precedences = new ArrayList<RawPrecedence>();
    var rawProductions = new ArrayList<RawProduction>();

    var lexer = Lexer.createLexer(TOKENS);
    var parser = Parser.createParser(GRAMMAR, Map.of());
    parser.parse(lexer.tokenize(input), new Evaluator<>() {

      @Override
      public Object evaluate(Terminal t) {
        return t.value();
      }

      @Override
      public Object evaluate(Production p, List<Object> args) {
        return switch (p.name()) {

          // -- Name
          case "Name : ident",
               "Name : tokens",
               "Name : precedence",
               "Name : grammar" ->
              args.getFirst();

          // -- Symbol
          case "Symbol : Name" ->
              new RawSymbol((String) args.getFirst(), false);

          case "Symbol : quoted" ->
              new RawSymbol(stripFirstAndLastCharacters((String) args.get(0)), true);

          // -- Symbols
          case "Symbols : Symbol" -> {
            var list = new ArrayList<RawSymbol>();
            list.add((RawSymbol) args.getFirst());
            yield list;
          }
          case "Symbols : Symbols Symbol" -> {
            @SuppressWarnings("unchecked")
            var list = (ArrayList<RawSymbol>) args.get(0);
            list.add((RawSymbol) args.get(1));
            yield list;
          }

          // -- Literals
          case "Literals : Symbol" -> {
            var list = new ArrayList<RawSymbol>();
            list.add((RawSymbol) args.getFirst());
            yield list;
          }
          case "Literals : Literals , Symbol" -> {
            @SuppressWarnings("unchecked")
            var list = (ArrayList<RawSymbol>) args.get(0);
            list.add((RawSymbol) args.get(2));
            yield list;
          }

          // -- TokenRule
          case "TokenRule : regex eol" -> {
            rules.add(new RawRule(null, stripFirstAndLastCharacters((String) args.getFirst())));
            yield null;
          }
          case "TokenRule : ident : regex eol" -> {
            rules.add(new RawRule((String) args.get(0), stripFirstAndLastCharacters((String) args.get(2))));
            yield null;
          }

          // -- PrecLine
          case "PrecLine : ident : Literals eol" -> {
            var associativity = (String) args.get(0);
            @SuppressWarnings("unchecked")
            var symbols = (ArrayList<RawSymbol>) args.get(2);
            precedences.add(new RawPrecedence(associativity, symbols));
            yield null;
          }

          // -- GrammarRule
          case "GrammarRule : Name : eol" -> {
            var head = (String) args.getFirst();
            rawProductions.add(new RawProduction(head, List.of()));
            yield null;
          }
          case "GrammarRule : Name : Symbols eol" -> {
            String lhs = (String) args.get(0);
            @SuppressWarnings("unchecked")
            var symbols = (ArrayList<RawSymbol>) args.get(2);
            rawProductions.add(new RawProduction(lhs, symbols));
            yield null;
          }

          // -- Void productions
          case "TokenRules : ε",
               "TokenRules : TokenRules TokenRule",
               "TokenRule : eol",
               "PrecLines : ε",
               "PrecLines : PrecLines PrecLine",
               "PrecLine : eol",
               "GrammarRules : ε",
               "GrammarRules : GrammarRules GrammarRule",
               "GrammarRule : eol",
               "Section : tokens { eol TokenRules } eol",
               "Section : precedence { eol PrecLines } eol",
               "Section : grammar { eol GrammarRules } eol",
               "Section : eol",
               "Sections : ε",
               "Sections : Sections Section",
               "Spec : Sections" ->
              null;

          default -> throw new MatchException("unhandled production: " + p.name(), null);
        };
      }
    });

    return build(rules, precedences, rawProductions);
  }

  // Post-processing, the evaluator should never fail, the build method should check the coherence
  private static MetaGrammar build(ArrayList<RawRule> rawRules,
                                   ArrayList<RawPrecedence> precedences,
                                   ArrayList<RawProduction> rawProductions) {

    if (rawProductions.isEmpty()) {
      throw new ParsingException("empty production list");
    }

    // Extract implicit quoted symbols
    var quotedTerminalMap = rawProductions.stream()
        .flatMap(p -> p.symbols.stream())
        .filter(RawSymbol::quoted)
        .map(RawSymbol::name)
        .distinct()
        .collect(Collectors.toMap(name -> name,
            Terminal::new,
            (_, _) -> { throw new AssertionError(); },
            LinkedHashMap::new));

    // Rules ordering: implicit quoted first, then named, then unnamed
    var rules = Stream.of(
        quotedTerminalMap.keySet().stream()
            .map(name -> new Token(name, quoteRegex(name))),
        rawRules.stream()
            .filter(r -> r.name != null)
            .map(r -> new Token(r.name, r.regex)),
        rawRules.stream()
            .filter(r -> r.name == null)
            .map(r -> new Token(r.regex))
        )
        .flatMap(r -> r)
        .toList();

    // NonTerminals
    var nonTerminalMap = rawProductions.stream()
        .map(p -> p.head)
        .distinct()
        .collect(Collectors.toMap(name -> name,
            NonTerminal::new,
            (_, _) -> { throw new AssertionError(); },
            LinkedHashMap::new));

    // Productions and Grammar
    var terminalMap = new HashMap<String, Terminal>();
    var productions = new ArrayList<Production>();
    for (var rawProduction : rawProductions) {
      var body = rawProduction.symbols.stream()
          .map(symbol -> {
            if (symbol.quoted) {
              return quotedTerminalMap.get(symbol.name);
            }
            var nonTerminal = nonTerminalMap.get(symbol.name);
            if (nonTerminal != null) {
              return nonTerminal;
            }
            return terminalMap.computeIfAbsent(symbol.name, Terminal::new);
          })
          .toList();
      var head = nonTerminalMap.get(rawProduction.head);
      productions.add(new Production(head, body));
    }

    var startSymbol = nonTerminalMap.values().iterator().next();
    var grammar = new Grammar(startSymbol, productions);

    // Precedence
    var precedenceMap = new LinkedHashMap<PrecedenceEntity, Precedence>();
    for(var i = 0; i < precedences.size(); i++) {
      var precedence = precedences.get(i);
      var associativity = switch (precedence.associativity) {
        case "left"  -> Precedence.Associativity.LEFT;
        case "right" -> Precedence.Associativity.RIGHT;
        default -> throw new ParsingException(
            "Expected 'left' or 'right' associativity, got: '" + precedence.associativity + "'");
      };
      for(var symbol : precedence.symbols) {
        var name = symbol.name;
        var terminal = symbol.quoted ? quotedTerminalMap.get(name) : terminalMap.get(name);
        precedenceMap.put(terminal, new Precedence(i, associativity));
      }
    }

    //LALRVerifier.verify(grammar, precedenceMap, error -> {
    //  throw new AssertionError(error);
    //});

    return new MetaGrammar(rules, grammar, precedenceMap);
  }


  private static String stripFirstAndLastCharacters(String raw) {
    return raw.substring(1, raw.length() - 1);
  }

  private static final Set<Character> SPECIAL_CHARACTERS =
      Set.of('\\', '^', '$', '.', '|', '?', '*', '+', '(', ')', '[', ']', '{', '}');

  private static String quoteRegex(String literal) {
    var builder = new StringBuilder();
    for (var i = 0; i < literal.length(); i++) {
      var c = literal.charAt(i);
      if (SPECIAL_CHARACTERS.contains(c)) {
        builder.append('\\');
      }
      builder.append(c);
    }
    return builder.toString();
  }
}