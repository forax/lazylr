import com.github.forax.lazylr.*;

sealed interface Expr {
  int pos();
}
record Value(int value, int pos) implements Expr {}
record Binary(char op, Expr left, Expr right, int pos) implements Expr {}
record Unary(char op, Expr expr, int pos) implements Expr {}

class ExprVisitor implements Visitor<Expr> {
  final Iterator<Terminal> input;

  ExprVisitor(Iterator<Terminal> input) {
    this.input = input;
    super();
  }

  public Expr number(Terminal terminal) {
    var pos = Lexer.position(input);
    return new Value(Integer.parseInt(terminal.value()), pos);
  }

  public int minus(Terminal unusedTerminal) {
    return Lexer.position(input);
  }

  @ProductionName("E : ( E )")
  public Expr parens(Expr expr) { return expr; }

  @ProductionName("E : E + E")
  public Expr add(Expr left, Expr right) { return new Binary('+', left, right, left.pos()); }

  @ProductionName("E : E * E")
  public Expr mul(Expr left, Expr right) { return new Binary('*', left, right, left.pos()); }

  @ProductionName("E : E ^ E")
  public Expr pow(Expr left, Expr right) { return new Binary('^', left, right, left.pos()); }

  @ProductionName("E : E minus E")
  public Expr sub(Expr left, int unusedPos, Expr right) { return new Binary('-', left, right, left.pos()); }

  @ProductionName("E : minus E")
  public Expr unary(int minusPos, Expr expr) { return new Unary('-', expr, minusPos); }
}

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        minus: /\\-/
        number: /[0-9]+/
        /[ \t]+/
      }
      precedence {
        left : '+', minus
        left : '*'
        right : '^'
        left : UNARY
      }
      grammar {
        E : number
        E : '(' E ')'
        E : E '+' E
        E : E minus E
        E : E '*' E
        E : E '^' E
        E : minus E    %prec UNARY
      }
      """);

  mg.verify();

  var expr = mg.parse("3 + - 2 * 4", ExprVisitor::new);
  IO.println(expr);


  // Binary[op=+, left=Value[value=3, pos=0], right=Binary[op=*, left=Unary[op=-, expr=Value[value=2, pos=6], pos=4], right=Value[value=4, pos=10], pos=4], pos=0]
}
