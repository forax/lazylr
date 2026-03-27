import com.github.forax.lazylr.*;

sealed interface Expr {}
record Value(int value) implements Expr {}
record Binary(char op, Expr left, Expr right) implements Expr {}

class ExprVisitor implements Visitor<Expr> {
  public Expr number(Terminal terminal) {
    return new Value(Integer.parseInt(terminal.value()));
  }

  @ProductionName("E : ( E )")
  public Expr parens(Expr expr) { return expr; }

  @ProductionName("E : E + E")
  public Expr add(Expr left, Expr right) { return new Binary('+', left, right); }

  @ProductionName("E : E * E")
  public Expr mul(Expr left, Expr right) { return new Binary('*', left, right); }
}

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      precedence {
        left : '+'
        left : '*'
      }
      grammar {
        E : number
        E : '(' E ')'
        E : E '+' E
        E : E '*' E
      }
      """);

  mg.verify();

  var expr = mg.parse("40 + 2 * 3", new ExprVisitor());
  IO.println(expr);
}
