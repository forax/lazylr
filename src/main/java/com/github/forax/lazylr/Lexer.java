package com.github.forax.lazylr;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class Lexer {
  private final Pattern pattern;
  private final List<Rule> rules;

  private Lexer(Pattern pattern, List<Rule> rules) {
    this.pattern = pattern;
    this.rules = rules;
    super();
  }

  public static Lexer createLexer(List<Rule> rules) {
    rules = List.copyOf(rules);
    var regex = rules.stream()
        .map(rule -> "(" + rule.regex() + ")")
        .collect(Collectors.joining("|"));
    var pattern = Pattern.compile(regex);
    return new Lexer(pattern, rules);
  }

  public Iterator<Terminal> tokenize(CharSequence input) {
    Objects.requireNonNull(input);
    var matcher = pattern.matcher(input);
    return new Iterator<>() {
      private Terminal token = nextTerminal(0);

      private Terminal nextTerminal(int index) {
        if (!matcher.find(index)) {
          return null;
        }
        for(var i = 1; i <= matcher.groupCount(); i++) {
          if(matcher.start(i) != -1) {
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
