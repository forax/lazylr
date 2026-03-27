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

  // Unknown character -> lexing error
  mg.parse("@", new PrintEvaluator());

  // bad terminal -> parser error
  mg.parse("()", new PrintEvaluator());

  // end of file reached -> parser error
  mg.parse("(32", new PrintEvaluator());
}
