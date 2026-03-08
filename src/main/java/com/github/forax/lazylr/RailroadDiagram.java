package com.github.forax.lazylr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class RailroadDiagram {
  private RailroadDiagram() {
    throw new AssertionError();
  }

  // Box-drawing characters
  private static final String H = "─";
  private static final String TL = "┌";
  private static final String TR = "┐";
  private static final String BL = "└";
  private static final String BR = "┘";
  private static final String LJ = "├";
  private static final String RJ = "┤";
  private static final String START = "○";
  private static final String END = "►";

  private record Fragment(List<String> lines) {
    public int width() {
      return lines.getFirst().length();
    }

    @Override
    public String toString() {
      return String.join("\n", lines);
    }
  }

  /// Stacks fragments vertically into a branching block:
  ///
  ///   ┌──<first>──┐
  ///   ├──<mid>────┤
  ///   └──<last>───┘
  private static Fragment mergeVertically(List<Fragment> fragments) {
    if (fragments.size() == 1) {
      return fragments.getFirst();
    }

    var max = fragments.stream().mapToInt(Fragment::width).max().orElse(0);

    var newLines = new ArrayList<String>();
    for (var i = 0; i < fragments.size(); i++) {
      var fragment = fragments.get(i);
      if (i == 0) {
        addAllLines(fragment, max, TL, TR, false, newLines);
      } else if (i == fragments.size() - 1) {
        addAllLines(fragment, max, BL, BR, true, newLines);
      } else {
        addAllLines(fragment, max, LJ, RJ, false, newLines);
      }
    }
    return new Fragment(newLines);
  }

  /// Appends all lines of `fragment` into `newLines`, padded to
  /// `max` width. The first line gets `leftJ`/`rightJ`
  /// junction characters; later lines get a vertical bar on the left
  /// (unless this is the last branch) and a space on the right.
  private static void addAllLines(Fragment fragment, int max,
                                  String leftJ, String rightJ,
                                  boolean last,
                                  List<String> newLines) {
    var lines = fragment.lines;
    var firstLine = lines.getFirst();
    newLines.add(leftJ + H + pad(firstLine, max, H) + H + rightJ);
    for (var j = 1; j < lines.size(); j++) {
      var line = lines.get(j);
      // Vertical bar keeps the junction column connected downward
      var leftMargin  = last ? "  " : "│ ";
      var rightMargin = last ? "  " : " │";
      newLines.add(leftMargin + pad(line, max, " ") + rightMargin);
    }
  }

  private static String pad(String line, int width, String padChar) {
    return line + padChar.repeat(width - line.length());
  }

  /// Joins two fragments side-by-side on the rail, with "──" between them.
  /// Extra lines from each side are padded with spaces to maintain column alignment.
  private static Fragment mergeHorizontally(Fragment left, Fragment right) {
    var ll = left.lines;
    var rl = right.lines;

    var newLines = new ArrayList<String>();
    // Rail row: connect with ──
    newLines.add(ll.getFirst() + H + H + rl.getFirst());

    var min = Math.min(ll.size(), rl.size());
    for (var i = 1; i < min; i++) {
      newLines.add(ll.get(i) + "  " + rl.get(i));
    }
    for (var i = min; i < ll.size(); i++) {
      newLines.add(ll.get(i) + "  " + " ".repeat(right.width()));
    }
    for (var i = min; i < rl.size(); i++) {
      newLines.add(" ".repeat(left.width()) + "  " + rl.get(i));
    }
    return new Fragment(newLines);
  }

  /// Wraps a rendered fragment in the top-level rail decoration, producing the
  /// final diagram for one non-terminal.
  /// The trailing spaces of all the lines are stripped.
  private static String topLevel(Fragment fragment) {
    var newLines = new ArrayList<String>();
    addAllLines(fragment, fragment.width(), START, END, true, newLines);
    newLines.replaceAll(String::stripTrailing);
    return String.join("\n", newLines);
  }


  private record Renderer(Grammar grammar, Set<NonTerminal> recursive) {

    /// Renders all productions of a non-terminal as a vertically merged block.
    public Fragment render(NonTerminal nt) {
      var productions = grammar.productionsFor(nt);
      return mergeVertically(productions.stream()
          .map(this::render)
          .toList());
    }

    /// Renders a single production body as a (possibly multi-line) fragment.
    private Fragment render(Production production) {
      var body = production.body();
      if (body.isEmpty()) {
        return new Fragment(List.of("[ε]"));
      }
      if (body.size() == 1) {
        return render(body.getFirst());
      }
      return body.stream()
          .map(this::render)
          .reduce(RailroadDiagram::mergeHorizontally)
          .orElseThrow();
    }

    /// Renders a single symbol as a fragment.
    private Fragment render(Symbol symbol) {
      return switch (symbol) {
        case Terminal t -> new Fragment(List.of("[" + t.name() + "]"));
        case NonTerminal nt when !recursive.contains(nt) -> render(nt);
        case NonTerminal nt -> new Fragment(List.of("<" + nt.name() + ">"));
      };
    }
  }


  private static Set<NonTerminal> computeRecursiveNonTerminals(Grammar grammar) {
    var deps = new HashMap<NonTerminal, Set<NonTerminal>>();
    for (var nt : grammar.nonTerminals()) {
      var refs = new HashSet<NonTerminal>();
      for(var production : grammar.productionsFor(nt)) {
        for(var symbol : production.body()) {
          switch (symbol) {
            case NonTerminal nonTerminal -> refs.add(nonTerminal);
            case Terminal _ -> {}
          }
        }
      }
      deps.put(nt, refs);
    }

    var recursive = new HashSet<NonTerminal>();
    for (var nt : grammar.nonTerminals()) {
      if (isReachable(nt, nt, deps, new HashSet<>())) {
        recursive.add(nt);
      }
    }
    return recursive;
  }

  private static boolean isReachable(NonTerminal current, NonTerminal target,
                                     Map<NonTerminal, Set<NonTerminal>> deps,
                                     Set<NonTerminal> visited) {
    for (var neighbor : deps.get(current)) {
      if (neighbor.equals(target)) {
        return true;
      }
      if (visited.add(neighbor) && isReachable(neighbor, target, deps, visited)) {
        return true;
      }
    }
    return false;
  }


  /// Generates railroad diagrams for all recursive non-terminals
  /// (starting with the start symbol) in the grammar.
  /// @param grammar the grammar to generate diagrams for.
  /// @param inlineNonRecursive when true, non-recursive non-terminals
  ///        are inlined rather than emitted as separate diagrams.
  public static String generate(Grammar grammar, boolean inlineNonRecursive) {
    var recursive = inlineNonRecursive ? computeRecursiveNonTerminals(grammar) : grammar.nonTerminals();
    var renderer = new Renderer(grammar, recursive);

    var builder = new StringBuilder();
    for (var nt : grammar.nonTerminals()) {
      if (!recursive.contains(nt) && !nt.equals(grammar.startSymbol())) {
        continue;
      }
      builder.append(nt.name()).append(":").append('\n');
      builder.append(topLevel(renderer.render(nt))).append('\n');
    }
    return builder.toString();
  }
}