package com.github.forax.lazylr;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Matcher;

/// A lexical analyzer that transforms a character sequence into a stream of [Terminal]s.
///
/// ### Lexing Behavior
/// - **Terminal Creation**: When a match is found and the rule has a 'name', a
///   new [Terminal] is created using that name and the matched text.
/// - **Ignorable Tokens**: If a rule has no name (is `null`), it is considered
///   an "ignorable token." The matched text is consumed by the [Lexer] but
///   no [Terminal] is produced for the output stream.
/// - **Priority**: If multiple rules match at the same position, the rule whose
///   match is the longest wins. If two rules match the same number of characters,
///   the rule that appears earlier in the list provided to the lexer wins.
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
  /// The process is lazy, the input is only scanned as [Iterator#next()] is called.
  ///
  /// @param input The character sequence to tokenize.
  /// @return An [Iterator] of [Terminal]s.
  /// @throws NullPointerException if the input is null.
  public Iterator<Terminal> tokenize(CharSequence input) {
    Objects.requireNonNull(input);
    var matchers = tokens.stream()
        .map(token -> token.pattern.matcher(input))
        .toArray(Matcher[]::new);
    return new Tokenizer() {
      private int matchIndex;
      private int terminalIndex;
      private Terminal terminal = nextTerminal(0);

      private record Match(Token token, String value) {}

      private Match nextMatch(int index) {
        if (index == input.length()) {
          return null;
        }
        var longuest = (String) null;
        var tokenIndex = 0;
        for (var i = 0; i < matchers.length; i++) {
          var matcher = matchers[i];
          if (matcher.find(index) && matcher.start() == index) {
            var group = matcher.group();
            if (longuest == null || longuest.length() < group.length()) {
              longuest = group;
              tokenIndex = i;
            }
          }
        }
        return longuest == null ? null : new Match(tokens.get(tokenIndex), longuest);
      }

      private Terminal nextTerminal(int index) {
        for(;;) {
          var match = nextMatch(index);
          if (match == null) {
            if (index == input.length()) {
              return null;
            }
            matchIndex = index;  // next match
            return error(index, input);
          }
          var token = match.token;
          var value = match.value;
          if (token.isIgnorable()) {
            index += value.length();
            continue;
          }
          matchIndex = index;  // next match
          return new Terminal(token.name(), value);
        }
      }

      private static Terminal error(int index, CharSequence input) {
        var errorMessage = ErrorHandler.lexingErrorMessage(index, input);
        return new Terminal(Terminal.ERROR.name(), errorMessage);
      }

      @Override
      public int index() {
        return terminalIndex;
      }
      @Override
      public CharSequence input() {
        return input;
      }

      @Override
      public boolean hasNext() {
        return terminal != null;
      }

      @Override
      public Terminal next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        var terminal = this.terminal;
        terminalIndex = matchIndex;  // for error message
        if (terminal.name().equals(Terminal.ERROR.name())) {
          this.terminal = null;
          return terminal;
        }
        matchIndex += terminal.value().length();
        this.terminal = nextTerminal(matchIndex);
        return terminal;
      }
    };
  }
}