package com.github.forax.lazylr;

import java.util.Iterator;

interface Tokenizer extends Iterator<Terminal> {
  int index();
  CharSequence input();

  final class ErrorHandler {
    private ErrorHandler() {
      throw new AssertionError();
    }

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

    // display the line content with the caret
    private static void lineContentAndCaret(StringBuilder builder, int index, CharSequence input) {
      var lineStart = lineStart(index, input);
      var lineEnd = lineEnd(index, input);
      var lineContent = input.subSequence(lineStart, lineEnd).toString();
      var caretPosition = index - lineStart;

      builder.append(lineContent)
          .append('\n')
          .repeat(" ", caretPosition)
          .append('^');
    }

    public static String lexingErrorMessage(int index, CharSequence input) {
      var lineColumn = lineColumn(index, input);
      var line = lineColumn.line();
      var column = lineColumn.column();
      var charDisplay = charDisplay(index, input);

      var errorMessage = new StringBuilder();
      errorMessage
          .append("Lexing error")
          .append(" at line ").append(line)
          .append(", column ").append(column)
          .append(", unexpected character ").append(charDisplay)
          .append('\n');
      lineContentAndCaret(errorMessage, index, input);

      return errorMessage.toString();
    }

    public static String parsingErrorMessage(Symbol symbol, String expectedLookaheads, int index, CharSequence input) {
      var lineColumn = lineColumn(index, input);
      var line = lineColumn.line();
      var column = lineColumn.column();

      var errorMessage = new StringBuilder();
      errorMessage
          .append("Parsing error")
          .append(" around line ").append(line)
          .append(", column ").append(column)
          .append(", unexpected symbol '").append(symbol.name()).append("'")
          .append('\n');
      lineContentAndCaret(errorMessage, index, input);
      errorMessage
          .append('\n')
          .append("  allowed terminals ").append(expectedLookaheads);
      return errorMessage.toString();
    }

    public static String parsingErrorMessage(Symbol symbol, String expectedLookaheads) {
      return "Parsing error unexpected symbol " + symbol.name() +
          "\n  allowed terminals " + expectedLookaheads;
    }
  }
}
