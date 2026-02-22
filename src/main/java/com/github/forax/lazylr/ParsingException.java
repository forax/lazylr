package com.github.forax.lazylr;

import java.util.Iterator;

/// Exception thrown by the [Parser] during parsing.
/// 
/// @see Parser#parse(Iterator, ParserListener)
/// @see Parser#parse(Iterator, Evaluator)
public final class ParsingException extends RuntimeException {
  /// Creates a new ParsingException with a message.
  /// @param message The error message.
  public ParsingException(String message) {
    super(message);
  }

  /// Create a new ParsingException with a message and a cause.
  /// @param message The error message.
  /// @param cause The cause of the exception.
  public ParsingException(String message, Throwable cause) {
    super(message, cause);
  }
}
