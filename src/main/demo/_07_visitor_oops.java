import com.github.forax.lazylr.*;

class IntVisitor implements Visitor<Integer> {
  public int number(Terminal terminal) { return Integer.parseInt(terminal.value()); }
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

  
  // Exception in thread "main" java.lang.IllegalStateException: production "E : ( E )" has no evaluator method,  proposed code:
  // @ProductionName("E : ( E )")
  // public int method(int param0) {
  //   throw new UnsupportedOperationException("TODO");
  // }
}
