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

  var input = List.of(new Terminal("number", "42"));

  var parser = Parser.createParser(mg.grammar(), Map.of());
  parser.parse(input.iterator(), new PrintEvaluator());
}
