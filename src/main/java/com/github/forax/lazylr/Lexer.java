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
    return new Iterator<>() {
      private Terminal terminal = nextTerminal(0);

      private Terminal nextTerminal(int index) {
        loop: for(;;) {
          if (!matcher.find(index)) {
            if (index != input.length()) {
              return ErrorHandler.error(index, input);
            }
            return null;
          }
          for (var i = 1; i <= matcher.groupCount(); i++) {
            var start = matcher.start(i);
            if (start != -1) {
              if (start != index) {
                matcher.reset();  // no current match
                return ErrorHandler.error(index, input);
              }
              var token = tokens.get(i - 1);
              if (token.isIgnorable()) {
                index = matcher.end();
                continue loop;
              }
              return new Terminal(token.name(), matcher.group(i));
            }
          }
          throw new AssertionError();
        }
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

  private static final class ErrorHandler {
    private record LineColumn(int line, int column) {}

    private static LineColumn lineColumn(int index, CharSequence input) {
      var line = 1;
      var column = 1;
      for (var i = 0; i < index; i++) {
        if (input.charAt(i) == '\n') {
          line++;
          column = 1;
        } else {
          column++;
        }
      }
      return new LineColumn(line, column);
    }

    private static String charDisplay(int index, CharSequence input) {
      var invalidChar = input.charAt(index);
      return switch (invalidChar) {
        case '\n' -> "\\n";
        case '\r' -> "\\r";
        case '\t' -> "\\t";
        case ' '  -> "' '";
        default -> invalidChar < 32 || invalidChar == 127
            ? String.format("\\u%04x", (int) invalidChar)
            : "'" + invalidChar + "'";
      };
    }

    private static int lineStart(int index, CharSequence input) {
      var lineStart = index;
      while (lineStart > 0 && input.charAt(lineStart - 1) != '\n') {
        lineStart--;
      }
      return lineStart;
    }

    private static int lineEnd(int index, CharSequence input) {
      var lineEnd = index;
      while (lineEnd < input.length() && input.charAt(lineEnd) != '\n') {
        lineEnd++;
      }
      return lineEnd;
    }

    public static Terminal error(int index, CharSequence input) {
      var lineColumn = lineColumn(index, input);
      var line = lineColumn.line();
      var column = lineColumn.column();
      var charDisplay = charDisplay(index, input);
      var lineStart = lineStart(index, input);
      var lineEnd = lineEnd(index, input);
      var lineContent = input.subSequence(lineStart, lineEnd).toString();
      var caretPosition = index - lineStart;

      var errorMessage = new StringBuilder();
      errorMessage.append("Lexical error at line ").append(line)
          .append(", column ").append(column)
          .append(": unexpected character ").append(charDisplay)
          .append("\n")
          .append(lineContent)  // display the line content with the caret
          .append("\n")
          .repeat(" ", caretPosition)
          .append("^");

      return new Terminal(Terminal.ERROR.name(), errorMessage.toString());
    }
  }
}