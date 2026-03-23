package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// An iterator over terminals that tracks position information for better error reporting.
///
/// This interface extends [Iterator] to provide additional context about where
/// in the input the current terminal was matched. The [Lexer] returns
/// an implementation of this interface that allows the [Parser] to generate
/// detailed error messages with line and column information.
///
/// @see Lexer#tokenize(CharSequence)
final class Tokenizer implements Iterator<Terminal> {
  private final CharSequence input;
  private final List<Token> tokens;
  private final Matcher[] matchers;
  private final HashMap<LRTransitionEngine.State, BitSet> activatedCache;

  private int matchIndex;
  private int terminalIndex;
  private @Nullable Terminal terminal;
  private boolean computed;   // true means terminal is up to date

  Tokenizer(CharSequence input, List<Token> tokens) {
    this.input = input;
    this.tokens = tokens;
    this.matchers = tokens.stream()
        .map(token -> token.pattern.matcher(input))
        .toArray(Matcher[]::new);
    activatedCache = new HashMap<>();
    super();
  }

  private record Match(Token token, String value) {}

  private @Nullable Match nextMatch(int index, @Nullable BitSet activated) {
    if (index == input.length()) {
      return null;
    }
    var longuest = (String) null;
    var tokenIndex = 0;
    for (var i = 0; i < matchers.length; i++) {
      var matcher = matchers[i];
      if (activated != null && !activated.get(i)) {
        continue;
      }
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

  private @Nullable Terminal nextTerminal(int index, @Nullable BitSet activated) {
    for(;;) {
      var match = nextMatch(index, activated);
      if (match == null) {
        if (index == input.length()) {
          return null;
        }
        if (activated != null) {  // retry with all tokens activated
                                  // so we get a proper parsing error message
          match = nextMatch(index, null);
        }
        if (match == null) {
          matchIndex = index;  // next match
          return error(index, input);
        }
      }
      var token = match.token;
      var value = match.value;
      if (token.name == null) {
        index += value.length();
        continue;
      }
      matchIndex = index;  // next match
      return new Terminal(token.name, value);
    }
  }

  private static Terminal error(int index, CharSequence input) {
    var errorMessage = ErrorHandler.lexingErrorMessage(index, input);
    return new Terminal(Terminal.ERROR.name(), errorMessage);
  }

  /// Returns the original input character sequence.
  /// @return The original input character sequence.
  public CharSequence input() {
    return input;
  }

  /// Returns the current character index in the input.
  /// @return The current character index in the input.
  public int index() {
    return terminalIndex;
  }

  @Override
  public boolean hasNext() {
    if (!computed) {
      terminal = nextTerminal(matchIndex, null);
      computed = true;
    }
    return terminal != null;
  }

  @Override
  public Terminal next() {
    if (!computed) {
      terminal = nextTerminal(matchIndex, null);
      computed = true;
    }
    var terminal = this.terminal;
    if (terminal == null) {
      throw new NoSuchElementException();
    }
    terminalIndex = matchIndex;  // for error message
    if (Terminal.ERROR.name().equals(terminal.name())) {
      this.terminal = null;
      return terminal;
    }
    matchIndex += terminal.value().length();
    computed = false;
    return terminal;
  }

  private BitSet computeActivated(LRTransitionEngine.State state) {
    var terminals = Parser.expectedTerminals(state);
    var activated = new BitSet(tokens.size());
    for (var i = 0; i < tokens.size(); i++) {
      var token = tokens.get(i);
      var name = token.name;
      if (name == null) {
        activated.set(i);
        continue;
      }
      activated.set(i, terminals.contains(new Terminal(name)));
    }
    return activated;
  }

  public @Nullable Terminal pollTerminal(LRTransitionEngine.State state) {
    if (!computed) {
      var activated = activatedCache.computeIfAbsent(state, this::computeActivated);
      terminal = nextTerminal(matchIndex, activated);
      computed = true;
    }
    var terminal = this.terminal;
    if (terminal == null) {
      return null;
    }
    terminalIndex = matchIndex;  // for error message
    if (Terminal.ERROR.name().equals(terminal.name())) {
      this.terminal = null;
      return terminal;
    }
    matchIndex += terminal.value().length();
    computed = false;
    return terminal;
  }

  /// Utility class for generating lexing/parsing error messages.
  static final class ErrorHandler {
    private ErrorHandler() {
      throw new AssertionError();
    }

    private record LineColumn(int line, int column) {}

    /// Computes the line and column number for a given character index.
    ///
    /// This method scans the input from the beginning, which is O(n) in the input length.
    /// This is acceptable because it is only called when a lexing or parsing error occurs,
    /// and the parser stops at the first error.
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

    /// Formats a character for display in error messages.
    /// Special characters are escaped, unprintable characters use Unicode escapes.
    private static String charDisplay(char invalidChar) {
      return switch (invalidChar) {
        case '\n' -> "'\\n'";
        case '\r' -> "'\\r'";
        case '\t' -> "'\\t'";
        case ' '  -> "' '";
        default -> invalidChar < 32 || invalidChar == 127
            ? String.format("'\\u%04x'", (int) invalidChar)
            : "'" + invalidChar + "'";
      };
    }

    /// Finds the start of the line containing the given index.
    private static int lineStart(int index, CharSequence input) {
      var lineStart = index;
      while (lineStart > 0 && input.charAt(lineStart - 1) != '\n') {
        lineStart--;
      }
      return lineStart;
    }

    /// Finds the end of the line containing the given index.
    private static int lineEnd(int index, CharSequence input) {
      var lineEnd = index;
      while (lineEnd < input.length() && input.charAt(lineEnd) != '\n') {
        lineEnd++;
      }
      return lineEnd;
    }

    /// Appends the line content with a caret (^) pointing to the error position.
    private static void appendLineContentAndCaret(StringBuilder builder, int index, CharSequence input) {
      var lineStart = lineStart(index, input);
      var lineEnd = lineEnd(index, input);
      var lineContent = input.subSequence(lineStart, lineEnd).toString();
      var caretPosition = index - lineStart;

      builder
          .append(lineContent)
          .append('\n')
          .repeat(" ", caretPosition)
          .append('^');
    }

    /// Formats a set of expected terminals for display in error messages.
    private static String expectedTerminals(Set<Terminal> expected) {
      return Stream.concat(
          expected.stream()
              .filter(Predicate.not(Terminal.EOF::equals))
              .map(terminal -> {
                var name = terminal.name();
                return Character.isJavaIdentifierPart(name.charAt(0)) ? name : "'" + name + "'";
              })
              .sorted(),
             expected.contains(Terminal.EOF) ? Stream.of("<end of file>") : Stream.empty())
          .collect(Collectors.joining(", "));
    }

    /// Generates a detailed lexing error message with position information.
    ///
    /// @param index    The character index where the error occurred.
    /// @param input    The input character sequence being tokenized.
    /// @return A formatted error message.
    public static String lexingErrorMessage(int index, CharSequence input) {
      var lineColumn = lineColumn(index, input);
      var line = lineColumn.line();
      var column = lineColumn.column();
      var charDisplay = charDisplay(input.charAt(index));

      var errorMessage = new StringBuilder();
      errorMessage
          .append("Lexing error at line ").append(line)
          .append(", column ").append(column)
          .append(": unexpected character ").append(charDisplay)
          .append('\n');
      appendLineContentAndCaret(errorMessage, index, input);

      return errorMessage.toString();
    }

    /// Generates a detailed parsing error message with position information.
    ///
    /// @param terminal The unexpected terminal encountered.
    /// @param expected The set of expected terminals.
    /// @param index    The character index where the error occurred.
    /// @param input    The input character sequence being parsed.
    /// @return A formatted error message.
    public static String parsingErrorMessage(Terminal terminal, Set<Terminal> expected, int index, CharSequence input) {
      var lineColumn = lineColumn(index, input);
      var line = lineColumn.line();
      var column = lineColumn.column();

      var errorMessage = new StringBuilder();
      errorMessage
          .append("Parsing error at line ").append(line)
          .append(", column ").append(column)
          .append(": unexpected terminal '").append(terminal.name()).append("'")
          .append(", expected ").append(expectedTerminals(expected))
          .append('\n');
      appendLineContentAndCaret(errorMessage, index, input);
      return errorMessage.toString();
    }

    /// Generates a basic parsing error message without position information.
    ///
    /// @param terminal The unexpected terminal encountered.
    /// @param expected The set of expected terminals.
    /// @return A formatted error message.
    public static String parsingErrorMessage(Terminal terminal, Set<Terminal> expected) {
      return "Parsing error: unexpected terminal '" + terminal.name() + "', expected " + expectedTerminals(expected);
    }
  }
}