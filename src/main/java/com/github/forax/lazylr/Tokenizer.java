package com.github.forax.lazylr;

import java.util.Iterator;

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

    /// Generates a detailed lexing error message with position information.
    ///
    /// @param index The character index where the error occurred.
    /// @param input The input character sequence being tokenized.
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
    /// @param symbol The unexpected symbol encountered.
    /// @param index The character index where the error occurred.
    /// @param input The input character sequence being parsed.
    /// @return A formatted error message.
    public static String parsingErrorMessage(Symbol symbol, int index, CharSequence input) {
      var lineColumn = lineColumn(index, input);
      var line = lineColumn.line();
      var column = lineColumn.column();

      var errorMessage = new StringBuilder();
      errorMessage
          .append("Parsing error at line ").append(line)
          .append(", column ").append(column)
          .append(": unexpected symbol '").append(symbol.name()).append("'")
          .append('\n');
      appendLineContentAndCaret(errorMessage, index, input);
      return errorMessage.toString();
    }

    /// Generates a basic parsing error message without position information.
    ///
    /// @param symbol The unexpected symbol encountered.
    /// @return A formatted error message.
    public static String parsingErrorMessage(Symbol symbol) {
      return "Parsing error: unexpected symbol '" + symbol.name() + "'";
    }
  }
}