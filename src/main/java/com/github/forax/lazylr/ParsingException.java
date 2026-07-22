package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

/// Exception thrown by the [Parser] during parsing.
///
/// All runtime parse failures surface as this exception type:
/// - Lexing error: no token pattern matches.
/// - Parsing error: a token exists but is not valid in the current grammar state.
/// - Unexpected end of input: the input ends before the grammar is satisfied.
///
/// Example:
/// ```java
/// var mg = MetaGrammar.load("""
///     tokens {
///       number: /[0-9]+/
///       /[ \\t]+/
///     }
///     grammar {
///       E : number
///       E : '(' E ')'
///     }
///     """);
///
/// // 1) Lexing error, unexpected character 'f'
/// mg.parse("foo", new PrintEvaluator());
///
/// // 2) Parsing error, unexpected terminal ')', expected number
/// mg.parse("( )", new PrintEvaluator());
///
/// // 3) Parsing error, unexpected end of input, expected ')'
/// mg.parse("( 32", new PrintEvaluator());
/// ```
///
/// @see Parser#parse(java.util.Iterator, Evaluator)
public final class ParsingException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = -7572674787733662298L;

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
