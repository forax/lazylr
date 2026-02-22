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
  /// The iterator matches input based on the order of the [Rule]s
  /// provided to [#createLexer(List)].
  ///
  /// ### Match Outcomes:
  /// * **Standard Match:** Returns a [Terminal] with the rule's name and matched text.
  /// * **Ignorable Match:** If a rule has no name ([Rule#isIgnorable()] is `true`),
  ///    the matched text is skipped, and the lexer immediately attempts to find
  ///    the next match starting from the end of the skipped segment.
  /// * **No Match:** If no rule matches at the current index, a [Terminal#ERROR]
  ///    is returned with the first invalid character and the lexer stops.
  ///
  /// The process is lazy, the input is only scanned as [Iterator#next()] is called.
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
        loop: for(;;) {
          if (!matcher.find(index)) {
            if (index != input.length()) {
              return error(index, input);
            }
            return null;
          }
          for (var i = 1; i <= matcher.groupCount(); i++) {
            var start = matcher.start(i);
            if (start != -1) {
              if (start != index) {
                matcher.reset();  // no current match
                return error(index, input);
              }
              var rule = rules.get(i - 1);
              if (rule.isIgnorable()) {
                index = matcher.end();
                continue loop;
              }
              return new Terminal(rule.name(), matcher.group(i));
            }
          }
          throw new AssertionError();
        }
      }

      private static Terminal error(int index, CharSequence input) {
        return new Terminal(Terminal.ERROR.name(), "invalid character " + input.charAt(index));
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
        this.token = matcher.hasMatch() ? nextTerminal(matcher.end()) : null;
        return token;
      }
    };
  }
}