package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
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
///   /ignored-regex/                         // will not generate a terminal
/// }
/// precedence {
///   left:  '->', tokenName                  // level 1
///   right: '%'                              // level 2
/// }
/// grammar {
///   StartRule : StartRule '->' Item         // StartRole is the start symbol
///   StartRule : StartRule tokenName Item
///   StartRule : Item '%' Item
///   Item : tokenName
///   Item :
/// }                                         // empty right-hand side is epsilon
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
/// - **grammar** — defines BNF-style production rules.
///   The first rule's non-terminal becomes the start symbol.
///   Empty right-hand sides (epsilon rules) are written as a bare `Name:` line.
///   Any symbol written in single quotes (e.g. `'+'`) is automatically extracted
///   from the productions, converted to an escaped regex, and registered as a terminal.
///   No explicit declaration in the `tokens` section is required.
///
/// This class is immutable, thus thread-safe.
public final class MetaGrammar {
  private final List<Token> tokens;
  private final @Nullable Grammar grammar;
  private final Map<PrecedenceEntity, Precedence> precedenceMap;

  // Parameter order is intentionally different from the public constructor,
  // allowing overload resolution to distinguish them without a dummy parameter.
  private MetaGrammar(List<Token> tokens, @Nullable Grammar grammar, Map<PrecedenceEntity, Precedence> precedenceMap) {
    this.tokens = tokens;
    this.grammar = grammar;
    this.precedenceMap = precedenceMap;
    super();
  }

  /// Creates a new {@code MetaGrammar} from its three constituent parts.
  ///
  /// @param tokens        the tokens (named and unamed).
  /// @param precedenceMap the operator precedence table mapping each
  ///                      {@link PrecedenceEntity} to its {@link Precedence} level and
  ///                      associativity.
  /// @param grammar       the context-free grammar built from production rules, rooted at
  ///                      its start symbol.
  /// @throws NullPointerException if any argument is {@code null}.
  public MetaGrammar(List<Token> tokens, Map<? extends PrecedenceEntity, Precedence> precedenceMap, Grammar grammar) {
    Objects.requireNonNull(tokens);
    Objects.requireNonNull(precedenceMap);
    Objects.requireNonNull(grammar);
    this(List.copyOf(tokens), grammar, Collections.unmodifiableSequencedMap(new LinkedHashMap<>(precedenceMap)));
  }

  /// The lexer rules derived from the `tokens` section, in priority order.
  ///
  /// @return a list of [Token] objects, ordered so that quoted (implicit)
  ///         terminals appear first, followed by named terminals, then anonymous ones.
  public List<Token> tokens() {
    return tokens;
  }

  /// Return true if the grammar section is present.
  ///
  /// @return `true` if the grammar is present, `false` otherwise.
  public boolean hasGrammar() {
    return grammar != null;
  }

  /// The grammar derived from the `grammar` section, rooted at the first declared
  /// non-terminal.
  ///
  /// @return the [Grammar] built from all production in the specification.
  /// @throws IllegalStateException if the grammar section is empty
  public Grammar grammar() {
    // Design note: we do not use Optional here because an empty grammar is a corner
    // case (you may want to use a Lexer without a Parser, in which case no grammar
    // section is needed), and forcing all callers to unwrap an Optional would add
    // noise for no benefit.
    if (grammar == null) {
      throw new IllegalStateException("no grammar section was defined");
    }
    return grammar;
  }

  /// The operator precedence table derived from the `precedence` section.
  ///
  /// Keys are [PrecedenceEntity] instances (terminals) and values carry the precedence
  /// level (lower index = lower precedence) and associativity direction.
  ///
  /// @return the precedence map, in declaration order.
  public Map<PrecedenceEntity, Precedence> precedenceMap() {
    return precedenceMap;
  }

  // grammar definition
  private static Grammar createGrammar() {
    var ident      = new Terminal("ident");
    var regex      = new Terminal("regex");
    var quoted     = new Terminal("quoted");
    var tokens     = new Terminal("tokens");
    var precedence = new Terminal("precedence");
    var grammar    = new Terminal("grammar");
    var left       = new Terminal("left");
    var right      = new Terminal("right");
    var prec       = new Terminal("prec");
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
    var precSymbol   = new NonTerminal("PrecSymbol");
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

        new Production(precLine,     List.of(left, colon, literals, eol)),
        new Production(precLine,     List.of(right, colon, literals, eol)),
        new Production(precLine,     List.of(eol)),

        new Production(literals,     List.of(literals, comma, symbol)),
        new Production(literals,     List.of(symbol)),

        new Production(grammarRules, List.of(grammarRules, grammarRule)),
        new Production(grammarRules, List.of()),

        new Production(precSymbol,   List.of(prec, symbol)),
        new Production(precSymbol,   List.of()),

        new Production(grammarRule, List.of(name, colon, symbols, precSymbol, eol)),
        new Production(grammarRule, List.of(name, colon, precSymbol, eol)),
        new Production(grammarRule,  List.of(eol)),

        new Production(symbols,      List.of(symbols, symbol)),
        new Production(symbols,      List.of(symbol)),

        new Production(symbol,       List.of(name)),
        new Production(symbol,       List.of(quoted)),

        new Production(name,         List.of(ident))
    ));
  }

  private static final List<Token> TOKENS = List.of(
      new Token("tokens",     "tokens"),
      new Token("precedence", "precedence"),
      new Token("grammar",    "grammar"),
      new Token("left",       "left"),
      new Token("right",      "right"),
      new Token("prec",       "\\%prec"),
      new Token("{",          "\\{"),
      new Token("}",          "\\}"),
      new Token(":",          ":"),
      new Token(",",          ","),
      new Token("regex",      "/(?:[^/\\\\\n]|\\\\.)+/"),
      new Token("quoted",     "'(?:[^'\\\\\n]|\\\\.)*'"),
      new Token("ident",      "[A-Za-z_][A-Za-z0-9_]*"),
      new Token("eol",        "[\\r]?\\n"),
      new Token("\\/\\/[^\\n]*"),  // comment ignored
      new Token("[ \\t]+")         // whitespace ignored
  );

  private static final Grammar GRAMMAR = createGrammar();

  private record RawToken(@Nullable String name, String regex) {}
  private record RawSymbol(String name, boolean quoted) {}
  private record RawProduction(String head, List<RawSymbol> symbols, @Nullable RawSymbol precSymbol) {}
  private record RawPrecedence(Precedence.Associativity associativity, List<RawSymbol> symbols) {}

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
  public static MetaGrammar load(String input) {
    Objects.requireNonNull(input);

    var rawTokens = new ArrayList<RawToken>();
    var rawPrecedences = new ArrayList<RawPrecedence>();
    var rawProductions = new ArrayList<RawProduction>();

    var lexer = Lexer.createLexer(TOKENS);
    var parser = Parser.createParser(GRAMMAR, Map.of());
    parser.parse(lexer.tokenize(input), new Evaluator<>() {

      @Override
      public Object evaluate(Terminal t) {
        return t.value();
      }

      @Override
      public @Nullable Object evaluate(Production p, @SuppressWarnings("NullableProblems") List<Object> args) {
        return switch (p.name()) {

          // -- Name
          case "Name : ident",
               "Name : tokens",
               "Name : precedence",
               "Name : grammar",
               "Name : left",
               "Name : right" ->
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
            rawTokens.add(new RawToken(null, stripFirstAndLastCharacters((String) args.getFirst())));
            yield null;
          }
          case "TokenRule : ident : regex eol" -> {
            rawTokens.add(new RawToken((String) args.get(0), stripFirstAndLastCharacters((String) args.get(2))));
            yield null;
          }

          // -- PrecLine
          case "PrecLine : left : Literals eol",
               "PrecLine : right : Literals eol"  -> {
            var associativity = "left".equals(args.get(0)) ?
                Precedence.Associativity.LEFT : Precedence.Associativity.RIGHT;
            @SuppressWarnings("unchecked")
            var symbols = (ArrayList<RawSymbol>) args.get(2);
            rawPrecedences.add(new RawPrecedence(associativity, symbols));
            yield null;
          }

          // -- PrecSymbol
          case "PrecSymbol : prec Symbol" ->
              args.get(1);
          case "PrecSymbol : ε" ->
              null;

          // -- GrammarRule
          case "GrammarRule : Name : PrecSymbol eol" -> {
            var head = (String) args.getFirst();
            var precSymbol = (RawSymbol) args.get(2);
            rawProductions.add(new RawProduction(head, List.of(), precSymbol));
            yield null;
          }
          case "GrammarRule : Name : Symbols PrecSymbol eol" -> {
            String head = (String) args.get(0);
            @SuppressWarnings("unchecked")
            var symbols = (ArrayList<RawSymbol>) args.get(2);
            var precSymbol = (RawSymbol) args.get(3);
            rawProductions.add(new RawProduction(head, symbols, precSymbol));
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

    return build(rawTokens, rawPrecedences, rawProductions);
  }

  // Post-processing, the evaluator should never fail, the build method should check the coherence
  private static MetaGrammar build(ArrayList<RawToken> rawTokens,
                                   ArrayList<RawPrecedence> rawPrecedences,
                                   ArrayList<RawProduction> rawProductions) {
    // Extract implicit quoted symbols from productions (do not use %prec terminal)
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
            .map(name -> new Token(name, Pattern.quote(name))),
        rawTokens.stream()
            .filter(r -> r.name != null)
            .map(r -> new Token(r.name, r.regex)),
        rawTokens.stream()
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

    // Productions
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

    // Terminal precedence
    var precedenceMap = new LinkedHashMap<PrecedenceEntity, Precedence>();
    var freeSymbolPrecedenceMap = new LinkedHashMap<RawSymbol, Precedence>();
    for(var i = 0; i < rawPrecedences.size(); i++) {
      var rawPrecedence = rawPrecedences.get(i);
      var precedence = new Precedence(i + 1, rawPrecedence.associativity);
      for (var symbol : rawPrecedence.symbols) {
        var name = symbol.name;
        var terminal = symbol.quoted ? quotedTerminalMap.get(name) : terminalMap.get(name);
        if (terminal == null) {
          freeSymbolPrecedenceMap.put(symbol, precedence);
        } else {
          precedenceMap.put(terminal, precedence);
        }
      }
    }

    // Production precedence
    for (var i = 0; i < productions.size(); i++) {
      var symbol = rawProductions.get(i).precSymbol();
      if (symbol == null) {
        continue;
      }
      var name = symbol.name;
      var terminal = symbol.quoted ? quotedTerminalMap.get(name) : terminalMap.get(name);
      var precedence = terminal != null ? precedenceMap.get(terminal) : freeSymbolPrecedenceMap.get(symbol);
      if (precedence == null) {
        throw new ParsingException("%prec references terminal with no declared precedence: " + name);
      }
      precedenceMap.put(productions.get(i), precedence);
    }

    // Grammar
    Grammar grammar;
    if (!productions.isEmpty()) {
      var startSymbol = nonTerminalMap.values().iterator().next();
      try {
        grammar = new Grammar(startSymbol, productions);
      } catch (IllegalArgumentException e) {
        throw new ParsingException("Invalid grammar: " + e.getMessage(), e);
      }
    } else {
      grammar = null;
    }

    return new MetaGrammar(List.copyOf(rules), grammar, Collections.unmodifiableSequencedMap(precedenceMap));
  }


  private static String stripFirstAndLastCharacters(String raw) {
    return raw.substring(1, raw.length() - 1);
  }
}