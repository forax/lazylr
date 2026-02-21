package com.github.forax.lazylr;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// A lexical analyzer that transforms a character sequence into a stream of [Terminal] tokens.
///
/// ### Rule Priority
/// If multiple [Rule]s can match the same substring, the rule that appears **first**
/// in the list passed to [createLexer(List)] takes precedence.
///
public final class Lexer {
  private final Pattern pattern;
  private final List<Rule> rules;

  private Lexer(Pattern pattern, List<Rule> rules) {
    this.pattern = pattern;
    this.rules = rules;
    super();
  }

  /// Creates a new Lexer by compiling the provided rules.
  ///
  /// @param rules The list of rules to be used for tokenization.
  /// @return A configured Lexer instance.
  /// @throws java.util.regex.PatternSyntaxException if any rule contains an invalid regex.
  public static Lexer createLexer(List<Rule> rules) {
    rules = List.copyOf(rules);
    var regex = rules.stream()
        .map(rule -> "(" + rule.regex() + ")")
        .collect(Collectors.joining("|"));
    var pattern = Pattern.compile(regex);
    return new Lexer(pattern, rules);
  }

  /// Returns an iterator that lazily tokenizes the provided input.
  ///
  /// The iterator matches the input from the beginning, producing a [Terminal]
  /// for each match found. The process is "lazy"—it only scans the input as
  /// [Iterator#next()] is called.
  ///
  /// @param input The character sequence to tokenize.
  /// @return An [Iterator] of [Terminal]s.
  /// @throws NullPointerException if the input is null.
  public Iterator<Terminal> tokenize(CharSequence input) {
    Objects.requireNonNull(input);
    var matcher = pattern.matcher(input);
    return new Iterator<>() {
      private Terminal token = nextTerminal(0);

      private Terminal nextTerminal(int index) {
        if (!matcher.find(index)) {
          return null;
        }
        for (var i = 1; i <= matcher.groupCount(); i++) {
          if (matcher.start(i) != -1) {
            var rule = rules.get(i - 1);
            return new Terminal(rule.name(), matcher.group(i));
          }
        }
        throw new AssertionError();
      }

      @Override
      public boolean hasNext() {
        return token != null;
      }

      @Override
      public Terminal next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        var token = this.token;
        this.token = nextTerminal(matcher.end());
        return token;
      }
    };
  }
}