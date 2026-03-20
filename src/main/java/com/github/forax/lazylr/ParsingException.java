package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

/// Exception thrown by the [Parser] during parsing.
/// 
/// @see Parser#parse(java.util.Iterator, ParserListener)
/// @see Parser#parse(java.util.Iterator, Evaluator)
public final class ParsingException extends RuntimeException {
  /// Creates a new ParsingException with a message.
  /// @param message The error message.
  public ParsingException(@Nullable String message) {
    super(message);
  }

  /// Create a new ParsingException with a message and a cause.
  /// @param message The error message.
  /// @param cause The cause of the exception.
  public ParsingException(@Nullable String message, @Nullable Throwable cause) {
    super(message, cause);
  }
}
