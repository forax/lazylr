package com.github.forax.lazylr;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/// A lexical analyzer that transforms a character sequence into a stream of [com.github.forax.lazylr.Terminal]s.
///
/// ### Lexing Behavior
/// - **Terminal Creation**: When a match is found and the rule has a 'name', a
///   new [Terminal] is created using that name and the matched text.
/// - **Ignorable Tokens**: If a rule has no name (is `null`), it is considered
///   an "ignorable token." The matched text is consumed by the [Lexer], but
///   no [Terminal] is produced for the output stream.
/// - **Priority**: If multiple rules match at the same position, the rule whose
///   match is the longest wins. If two rules match the same number of characters,
///   the rule that appears earlier in the list provided to the lexer wins.
///
/// ### Example
/// ```java
/// var lexer = Lexer.createLexer(List.of(
///     new Token("NUMBER", "[0-9]+"),
///     new Token("PLUS", "\\+"),
///     new Token("\\s+")           // ignorable
/// ));
///
/// var tokens = lexer.tokenize("12 + 34");
/// while (tokens.hasNext()) {
///   var terminal = tokens.next();
///   System.out.println(terminal.name() + " -> " + terminal.value() +
///       " at " + Lexer.position(tokens));
/// }
/// // NUMBER -> 12 at 0
/// // PLUS -> + at 3
/// // NUMBER -> 34 at 5
/// ```
///
/// This class is thread-safe and can be safely shared between multiple threads.
public final class Lexer {
  private final List<Token> tokens;

  private Lexer(List<Token> tokens) {
    this.tokens = tokens;
    super();
  }

  /// Creates a new Lexer by compiling the provided rules.
  ///
  /// @param tokens The list of tokens to be used for tokenization.
  /// @return A configured Lexer instance.
  /// @throws NullPointerException if `tokens` is null.
  public static Lexer createLexer(List<Token> tokens) {
    tokens = List.copyOf(tokens);
    return new Lexer(tokens);
  }

  /// Returns an iterator that lazily tokenizes the provided input.
  ///
  /// The iterator matches input based on the order of the [Token]s
  /// provided to [#createLexer(List)].
  ///
  /// ### Match Outcomes:
  /// * **Standard Match:** The lexer finds all tokens that match at the current
  ///   position and selects the one with the longest match. Ties are broken
  ///   by declaration order (earlier token wins).
  /// * **Ignorable Match:** If a token has no name ([Token#isIgnorable()] is `true`),
  ///   the matched text is skipped, and the lexer immediately attempts to find
  ///   the next match starting from the end of the skipped segment.
  /// * **No Match:** If no token matches at the current index, a [Terminal#ERROR]
  ///   is returned with the first invalid character and the lexer stops.
  ///
  /// ### Context-Sensitive Lexing
  /// When used together with a [Parser], the lexer operates in a context-sensitive
  /// mode: only the token patterns that are syntactically valid
  /// in the current parser state are considered as candidates.
  ///
  /// The process is lazy, the input is only scanned when
  /// [Iterator#hasNext()]/[Iterator#next()] is called.
  ///
  /// @param input The character sequence to tokenize.
  /// @return An [Iterator] of [Terminal]s.
  /// @throws NullPointerException if the input is null.
  public Iterator<Terminal> tokenize(CharSequence input) {
    Objects.requireNonNull(input);
    return new Tokenizer(input, tokens);
  }

  /// Returns the start position of the last terminal returned by [Iterator#next()]
  /// in the input or -1 if unknown.
  ///
  /// @param iterator An iterator produced by [Lexer#tokenize(CharSequence)].
  /// @return The position of the iterator in the input or -1 if unknown.
  public static int position(Iterator<Terminal> iterator) {
    Objects.requireNonNull(iterator);
    if (iterator instanceof Tokenizer tokenizer) {
      return tokenizer.index();
    }
    return -1;
  }
}