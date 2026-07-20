package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Represents a grammar specification parsed from a text that describes tokens,
/// operator precedence, and production rules of a context-free grammar.
///
/// ## Text Representation Structure
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
/// - **tokens** — defines named and unnamed terminal symbols as Java regular expressions.
///   The declaration order controls lexer token priority.
///   Named terminals (e.g. `ident: /[a-z]+/`) become lexer tokens; unnamed ones
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
/// ## End-to-end Example
///
/// ```java
/// MetaGrammar mg = MetaGrammar.load("""
///     tokens {
///       num: /[0-9]+/
///       /[ ]+/           // ignorable token
///     }
///     precedence {
///       left: '+'
///       left: '*'        // '*' is higher than '+'
///     }
///     grammar {
///       E : num
///       E : E '+' E
///       E : E '*' E
///     }
///     """);
///
/// mg.verify();   // Optional, check that the grammar is well-formed
///
/// int result = mg.parse("2 + 3 * 4", new Visitor<Integer>() {
///   public int num(Terminal terminal) { return Integer.parseInt(terminal.value()); }
///
///   @ProductionName("E : E + E")
///   public int add(int left, int right) { return left + right; }
///
///   @ProductionName("E : E * E")
///   public int mul(int left, int right) { return left * right; }
/// });
/// // result == 14
/// ```
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
  ///         terminals appear first, followed by named terminals, then unnamed ones.
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
      throw new IllegalStateException("no grammar section is defined");
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

  /// Verifies that the grammar is LALR(1), using the precedence map to resolve
  /// shift/reduce conflicts where possible. If unresolved conflicts remain,
  /// they are described on stderr along with the full LALR(1) automaton
  /// to help diagnose them.
  ///
  /// Use [#verify(Consumer)] instead if you want to handle conflict
  /// messages programmatically rather than printing them to stderr.
  ///
  /// @throws IllegalStateException if no grammar section is defined.
  public void verify() {
    if (grammar == null) {
      throw new IllegalStateException("no grammar section is defined");
    }
    LALRVerifier.verify(grammar, precedenceMap);
  }

  /// Verifies that the grammar is LALR(1), using the precedence map to resolve
  /// shift/reduce conflicts where possible.
  /// If unresolved conflicts remain, they are described on stderr.
  /// If `alwaysPrint` is `true`, the LALR(1) automaton is printed unconditionally
  /// on stdout; otherwise it is printed on stderr if there are conflicts.
  ///
  /// Use [#verify(Consumer)] instead if you want to handle conflict
  /// messages programmatically rather than printing them to stderr.
  ///
  /// @param alwaysPrint if `true`, the LALR(1) automaton is printed unconditionally.
  /// @throws IllegalStateException if no grammar section is defined.
  public void verify(boolean alwaysPrint) {
    if (grammar == null) {
      throw new IllegalStateException("no grammar section is defined");
    }
    LALRVerifier.verify(grammar, precedenceMap, alwaysPrint);
  }

  /// Verifies that the grammar is LALR(1), using the precedence map to resolve
  /// shift/reduce conflicts where possible. If unresolved conflicts remain,
  /// the error reporter is called once per conflict with a human-readable
  /// description.
  ///
  /// Use [#verify()] instead if you simply want conflicts printed to stderr.
  ///
  /// @param errorReporter called once per unresolved conflict with a
  ///                      human-readable description.
  /// @throws NullPointerException  if `errorReporter` is `null`.
  /// @throws IllegalStateException if no grammar section is defined.
  public void verify(Consumer<? super String> errorReporter) {
    if (grammar == null) {
      throw new IllegalStateException("no grammar section is defined");
    }
    LALRVerifier.verify(grammar, precedenceMap, errorReporter);
  }

  /// Parses the given input text using this meta-grammar verifying that
  /// the input text is syntactically valid.
  ///
  /// This is a convenience method for validation: it tokenizes and
  /// parses the input exactly as [#parse(CharSequence, Evaluator)] would,
  /// but uses a no-op evaluator.
  /// Use it when you only want to check that the input conforms to the grammar
  /// without computing a result.
  ///
  /// @param inputText the input text to tokenize and parse.
  /// @throws NullPointerException if {@code inputText} is {@code null}.
  /// @throws IllegalStateException if no grammar section is defined in this meta-grammar.
  /// @throws ParsingException if a lexing or parsing error occurs.
  ///
  /// @see #parse(CharSequence, Evaluator)
  public void parse(CharSequence inputText) throws ParsingException {
    Objects.requireNonNull(inputText);
    if (grammar == null) {
      throw new IllegalStateException("no grammar section is defined");
    }
    parse(inputText, new Evaluator<@Nullable Object>() {
      @Override
      public @Nullable Object evaluate(Terminal terminal) {
        return null;
      }

      @Override
      public @Nullable Object evaluate(Production production, List<@Nullable Object> arguments) {
        return null;
      }
    });
  }

  /// Parses the given input text using this meta-grammar and evaluates it using the provided evaluator.
  ///
  /// The parsing process is as follows:
  /// - A [Lexer] is created from the tokens section defined in this meta-grammar.
  /// - The input text is tokenized into an iterator of [Terminal].
  /// - A [Parser] is created from the grammar and precedence sections and
  ///   used to parse using the [Evaluator].
  ///
  /// This is equivalent to calling
  /// ```java
  /// var lexer = Lexer.createLexer(tokens);
  /// var parser = Parser.createParser(grammar, precedenceMap);
  /// parser.parse(lexer.tokenize(inputText), evaluator);
  /// ```
  ///
  /// @param inputText the input text to tokenize and parse.
  /// @param evaluator the evaluator used to compute semantic values during parsing.
  /// @param <V> the type of the evaluation result.
  /// @return the result produced by the evaluator.
  /// @throws IllegalStateException if no grammar section is defined in this meta-grammar.
  /// @throws ParsingException if a lexing, parsing or a conflict error occurs.
  ///
  /// @see Lexer#createLexer(List)
  /// @see Parser#createParser(Grammar, Map)
  public <V extends @Nullable Object> V parse(CharSequence inputText, Evaluator<V> evaluator) throws ParsingException{
    Objects.requireNonNull(inputText);
    Objects.requireNonNull(evaluator);
    if (grammar == null) {
      throw new IllegalStateException("no grammar section is defined");
    }
    var lexer = Lexer.createLexer(tokens);
    var parser = Parser.createParser(grammar, precedenceMap);
    return parser.parse(lexer.tokenize(inputText), evaluator);
  }

  private static StackWalker getStackWalker() {
    final class Holder {
      private static final StackWalker WALKER =
          StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    }
    return Holder.WALKER;
  }

  private static MethodHandles.Lookup teleport(Class<?> callerClass) {
    try {
      return MethodHandles.privateLookupIn(callerClass, MethodHandles.lookup());
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(
          "The caller class module is not open, you may use Visitor.reflect(MethodHandles.lookup(), visitor) instead", e);
    }
  }

  /// Parses the given input text using this meta-grammar and a reflection-based visitor.
  ///
  /// This is equivalent to calling `parse(input, Evaluator.reflect(MethodHandles.lookup(), visitor))`.
  ///
  /// @param inputText the input text to tokenize and parse.
  /// @param visitor an object defining the visit methods called during parsing
  ///        by reflection.
  /// @param <V> the type of the visitor result.
  /// @return the result produced by the visitor.
  /// @throws IllegalStateException if no grammar section is defined in this meta-grammar.
  /// @throws ParsingException if a lexing, parsing or a conflict error occurs.
  ///
  /// @see #parse(CharSequence, Evaluator)
  /// @see Visitor#reflect(java.lang.invoke.MethodHandles.Lookup, Visitor)
  public <V extends @Nullable Object> V parse(CharSequence inputText, Visitor<V> visitor) throws ParsingException {
    Objects.requireNonNull(inputText);
    Objects.requireNonNull(visitor);
    if (grammar == null) {
      throw new IllegalStateException("no grammar section is defined");
    }
    var callerClass = getStackWalker().getCallerClass();
    var lookup = teleport(callerClass);
    return parse(inputText, Visitor.reflect(lookup, visitor));
  }

  /// Parses the given input text using this meta-grammar and construct a reflection-based visitor
  /// from the input iterator.
  /// Use this method if you want to access the terminal position in the input inside
  /// the visitor's terminal methods.
  ///
  /// For example
  /// ```java
  /// public class ExprVisitor implements Visitor<Expr> {
  ///   private final Iterator<Terminal> inputIterator;
  ///
  ///   public ExprVisitor(Iterator<Terminal> inputIterator) {
  ///     this.inputIterator = inputIterator;
  ///     super();
  ///   }
  ///
  ///   public Expr number(Terminal terminal) {
  ///     // Get the terminal position in the input
  ///     var pos = Lexer.position(inputIterator);
  ///     return ...
  ///   }
  ///   ...
  /// }
  /// ...
  /// var expr = mg.parse(inputText, ExprVisitor::new);
  /// ```
  ///
  /// This is equivalent to calling
  /// ```java
  /// var lexer = Lexer.createLexer(tokens);
  /// var parser = Parser.createParser(grammar, precedenceMap);
  /// var iterator = lexer.tokenize(inputText);
  /// var visitor = visitorFactory.apply(iterator);
  /// parser.parse(iterator, Visitor.reflect(MethodHandles.lookup(), visitor));
  /// ```
  ///
  /// @param inputText the input text to tokenize and parse.
  /// @param visitorFactory a factory function that creates a visitor instance based on the input iterator.
  /// @param <V> the type of the visitor result.
  /// @return the result produced by the visitor.
  /// @throws IllegalStateException if no grammar section is defined in this meta-grammar.
  /// @throws ParsingException if a lexing, parsing or a conflict error occurs.
  ///
  /// @see #parse(CharSequence, Visitor)
  public <V extends @Nullable Object> V parse(CharSequence inputText,
                                              Function<? super Iterator<Terminal>, ? extends Visitor<V>> visitorFactory)
      throws ParsingException {

    Objects.requireNonNull(inputText);
    Objects.requireNonNull(visitorFactory);
    if (grammar == null) {
      throw new IllegalStateException("no grammar section is defined");
    }
    var callerClass = getStackWalker().getCallerClass();
    var lookup = teleport(callerClass);
    var lexer = Lexer.createLexer(tokens);
    var parser = Parser.createParser(grammar, precedenceMap);
    var iterator = lexer.tokenize(inputText);
    var visitor = visitorFactory.apply(iterator);
    return parser.parse(iterator, Visitor.reflect(lookup, visitor));
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

  private static final ParserFactory PARSER_FACTORY = ParserFactory.createFactory(createGrammar(), Map.of());

  private record RawToken(@Nullable String name, String regex) {}
  private record RawSymbol(String name, boolean quoted) {}
  private record RawProduction(String head, List<RawSymbol> symbols, @Nullable RawSymbol precSymbol) {}
  private record RawPrecedence(Precedence.Associativity associativity, List<RawSymbol> symbols) {}

  /// Parses a grammar specification.
  ///
  /// All the `grammar`, `tokens` and `precedence` sections are optional.
  /// The `tokens` and `precedence` sections can be empty, the `grammar`
  /// section, if present, must contain at least one production rule.
  /// Quoted literals in production rules are automatically promoted to terminals
  /// without requiring an explicit entry in the `tokens` section.
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
    var parser = PARSER_FACTORY.createParser();
    parser.parse(lexer.tokenize(input), new Evaluator<@Nullable Object>() {

      @Override
      public Object evaluate(Terminal t) {
        return t.value();
      }

      @Override
      public @Nullable Object evaluate(Production p, List<@Nullable Object> args) {
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
          case "Symbol : Name" -> {
            var name = Objects.requireNonNull((String) args.getFirst());
            yield new RawSymbol(name, false);
          }

          case "Symbol : quoted" -> {
            var raw = Objects.requireNonNull((String) args.getFirst());
            yield new RawSymbol(stripFirstAndLastCharacters(raw), true);
          }

          // -- Symbols
          case "Symbols : Symbol" -> {
            var list = new ArrayList<RawSymbol>();
            var rawSymbol = Objects.requireNonNull((RawSymbol) args.getFirst());
            list.add(rawSymbol);
            yield list;
          }
          case "Symbols : Symbols Symbol" -> {
            @SuppressWarnings("unchecked")
            var list = Objects.requireNonNull((ArrayList<RawSymbol>) args.get(0));
            var rawSymbol = Objects.requireNonNull((RawSymbol) args.get(1));
            list.add(rawSymbol);
            yield list;
          }

          // -- Literals
          case "Literals : Symbol" -> {
            var list = new ArrayList<RawSymbol>();
            var rawSymbol = Objects.requireNonNull((RawSymbol) args.getFirst());
            list.add(rawSymbol);
            yield list;
          }
          case "Literals : Literals , Symbol" -> {
            @SuppressWarnings("unchecked")
            var list = Objects.requireNonNull((ArrayList<RawSymbol>) args.get(0));
            var rawSymbol = Objects.requireNonNull((RawSymbol) args.get(2));
            list.add(rawSymbol);
            yield list;
          }

          // -- TokenRule
          case "TokenRule : regex eol" -> {
            var raw = Objects.requireNonNull((String) args.getFirst());
            rawTokens.add(new RawToken(null, stripFirstAndLastCharacters(raw)));
            yield null;
          }
          case "TokenRule : ident : regex eol" -> {
            var raw = Objects.requireNonNull((String) args.get(2));
            rawTokens.add(new RawToken((String) args.get(0), stripFirstAndLastCharacters(raw)));
            yield null;
          }

          // -- PrecLine
          case "PrecLine : left : Literals eol",
               "PrecLine : right : Literals eol"  -> {
            var associativity = "left".equals(args.get(0)) ?
                Precedence.Associativity.LEFT : Precedence.Associativity.RIGHT;
            @SuppressWarnings("unchecked")
            var symbols = Objects.requireNonNull((ArrayList<RawSymbol>) args.get(2));
            rawPrecedences.add(new RawPrecedence(associativity, symbols));
            yield null;
          }

          // -- PrecSymbol
          case "PrecSymbol : prec Symbol" ->
              Objects.requireNonNull(args.get(1));
          case "PrecSymbol : ε" ->
              null;

          // -- GrammarRule
          case "GrammarRule : Name : PrecSymbol eol" -> {
            var head = Objects.requireNonNull((String) args.getFirst());
            var precSymbol = (RawSymbol) args.get(2);
            rawProductions.add(new RawProduction(head, List.of(), precSymbol));
            yield null;
          }
          case "GrammarRule : Name : Symbols PrecSymbol eol" -> {
            var head = Objects.requireNonNull((String) args.get(0));
            @SuppressWarnings("unchecked")
            var symbols = Objects.requireNonNull((ArrayList<RawSymbol>) args.get(2));
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

          default -> throw new AssertionError("unhandled production: " + p.name(), null);
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

    // Rules ordering: quoted first, then named, then unnamed
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
      var productionMap = productions.stream()
          .collect(Collectors.groupingBy(Production::head, LinkedHashMap::new, Collectors.toUnmodifiableList()));
      try {
        grammar = new Grammar(startSymbol, List.copyOf(productions), productionMap);
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