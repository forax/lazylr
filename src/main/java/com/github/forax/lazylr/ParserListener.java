package com.github.forax.lazylr;

/// A listener interface for observing the step-by-step transitions of the [Parser].
///
/// `ParserListener` provides a low-level hook into the LR parsing process. It is
/// notified every time the parser decides to consume a token (shift) or
/// apply a grammatical rule (reduce).
///
/// The listener methods are called in the order the parser makes its decisions.
/// Because this is an LR (bottom-up) parser, [#onReduce] is called only after
/// all components of that production have been shifted or reduced.
///
/// Refer to [Evaluator] for a more high level functional interface.
///
/// @see Parser#parse(java.util.Iterator, ParserListener)
public interface ParserListener {

  /// Invoked when the parser matches a [Terminal] from the input.
  ///
  /// @param token The terminal token currently being shifted.
  void onShift(Terminal token);

  /// Invoked when the parser identifies a completed [Production].
  ///
  /// @param production The rule that has been successfully matched and reduced.
  void onReduce(Production production);
}