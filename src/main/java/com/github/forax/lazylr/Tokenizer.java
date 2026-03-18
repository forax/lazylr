package com.github.forax.lazylr;

import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;
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
interface Tokenizer extends Iterator<Terminal> {
  /// Returns the current character index in the input.
  /// @return The current character index in the input.
  int index();

  /// Returns the original input character sequence.
  /// @return The original input character sequence.
  CharSequence input();

  /// Utility class for generating lexing/parsing error messages.
  final class ErrorHandler {
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