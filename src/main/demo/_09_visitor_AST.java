import com.github.forax.lazylr.*;

sealed interface Expr {}
record Value(int value) implements Expr {}

class ExprVisitor implements Visitor<Expr> {
  public Expr number(Terminal terminal) {
    return new Value(Integer.parseInt(terminal.value()));
  }

  @ProductionName("E : ( E )")
  public Expr parens(Expr expr) { return expr; }
}

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      grammar {
        E : number
        E : '(' E ')'
      }
      """);
  mg.verify();

  var expr = mg.parse("( 32 )", new ExprVisitor());
  IO.println(expr);
}
