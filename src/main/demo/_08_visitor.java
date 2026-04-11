import com.github.forax.lazylr.*;

class IntVisitor implements Visitor<Integer> {
  public int number(Terminal terminal) { return Integer.parseInt(terminal.value()); }

  @ProductionName("E : ( E )")
  public int parens(int value) { return value; }
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

  var value = mg.parse("(32)", new IntVisitor());
  IO.println(value);
}
