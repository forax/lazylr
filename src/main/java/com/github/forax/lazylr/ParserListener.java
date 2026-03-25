package com.github.forax.lazylr;

/// A listener interface for observing the step-by-step transitions of the [Parser].
///
/// `ParserListener` provides a low-level hook into the LR parsing process. It is
/// notified every time the parser decides to consume a token (shift) or
/// apply a grammatical rule (reduce).
///
/// ### Ordering guarantees
/// Because this is a bottom-up parser, [#onReduce] always fires after all symbols
/// in the production body have already been shifted or reduced.
///
/// ### Example
/// Given the grammar `E : E '+' E` and input `1 + 2`, the events are:
/// ```
/// onShift(num "1" pos=0)  // shift the first number
/// onReduce(E : num)       // immediately reduce it to E
/// onShift('+' pos=2)      // shift the operator
/// onShift(num "2" pos=4)  // shift the second number
/// onReduce(E : num)       // immediately reduce it to E
/// onReduce(E : E + E)     // finally reduce the whole expression
/// ```
///
/// Refer to [Evaluator] for a more high-level functional interface.
///
/// @see Parser#parse(java.util.Iterator, ParserListener)
public interface ParserListener {

  /// Invoked when the parser matches a [Terminal] from the input stream.
  ///
  /// This event fires immediately when the token is consumed.
  ///
  /// @param token The terminal token currently being shifted.
  /// @param position The position of the terminal in the input or -1 if unknown.
  void onShift(Terminal token, int position);

  /// Invoked when the parser completes a [Production].
  ///
  /// By the time this method is called, [#onShift] (and any nested [#onReduce])
  /// has already fired for every symbol in `production.body()`, in left-to-right order.
  ///
  /// @param production The rule that has been successfully matched and reduced.
  void onReduce(Production production);
}