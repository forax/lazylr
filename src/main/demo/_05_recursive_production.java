import com.github.forax.lazylr.*;

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

  var tree = mg.parse("(32)", new TreeEvaluator());
  IO.println(tree);

  var tree2 = mg.parse("((32))", new TreeEvaluator());
  //IO.println(tree2);
}
