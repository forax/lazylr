import com.github.forax.lazylr.*;

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      grammar {
        E : number
      }
      """);

  var tree = mg.parse("32", new PrintEvaluator());
  IO.println(tree);
}
