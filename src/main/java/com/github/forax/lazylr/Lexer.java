package com.github.forax.lazylr;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// A lexical analyzer that transforms a character sequence into a stream of [Terminal]s.
///
/// ### Token Priority
/// If multiple [Token]s can match the same substring, the token that appears **first**
/// in the list passed to [createLexer(List)] takes precedence.
///
/// This class is thread-safe and can be safely shared between multiple threads.
public final class Lexer {
  private final Pattern pattern;
  private final List<Token> tokens;

  private Lexer(Pattern pattern, List<Token> tokens) {
    this.pattern = pattern;
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
    var regex = tokens.stream()
        .map(token -> "(" + token.regex() + ")")
        .collect(Collectors.joining("|"));
    var pattern = Pattern.compile(regex);
    return new Lexer(pattern, tokens);
  }

  /// Returns an iterator that lazily tokenizes the provided input.
  ///
  /// The iterator matches input based on the order of the [Token]s
  /// provided to [#createLexer(List)].
  ///
  /// ### Match Outcomes:
  /// * **Standard Match:** Returns a [Terminal] with the token's name and matched text.
  /// * **Ignorable Match:** If a token has no name ([Token#isIgnorable()] is `true`),
  ///    the matched text is skipped, and the lexer immediately attempts to find
  ///    the next match starting from the end of the skipped segment.
  /// * **No Match:** If no token matches at the current index, a [Terminal#ERROR]
  ///    is returned with the first invalid character and the lexer stops.
  ///
  /// The process is lazy, the input is only scanned as [Iterator#next()] is called.
  ///
  /// @param input The character sequence to tokenize.
  /// @return An [Iterator] of [Terminal]s.
  /// @throws NullPointerException if the input is null.
  public Iterator<Terminal> tokenize(CharSequence input) {
    Objects.requireNonNull(input);
    var matcher = pattern.matcher(input);
    return new Tokenizer() {
      private int index;
      private Terminal terminal = nextTerminal(0);

      private Terminal nextTerminal(int index) {
        loop: for(;;) {
          if (!matcher.find(index)) {
            if (index != input.length()) {
              this.index = index;
              return error(index, input);
            }
            return null;
          }
          for (var i = 1; i <= matcher.groupCount(); i++) {
            var start = matcher.start(i);
            if (start != -1) {
              if (start != index) {
                matcher.reset();  // no current match
                this.index = index;
                return error(index, input);
              }
              var token = tokens.get(i - 1);
              if (token.isIgnorable()) {
                index = matcher.end();
                continue loop;
              }
              this.index = index;
              return new Terminal(token.name(), matcher.group(i));
            }
          }
          throw new AssertionError();
        }
      }

      private static Terminal error(int index, CharSequence input) {
        var errorMessage = ErrorHandler.lexingErrorMessage(index, input);
        return new Terminal(Terminal.ERROR.name(), errorMessage);
      }

      @Override
      public int index() {
        return index;
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
        this.terminal = matcher.hasMatch() ? nextTerminal(matcher.end()) : null;
        return terminal;
      }
    };
  }
}