import com.github.forax.lazylr.*;

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \t]+/            // ignorable
      }
      grammar {
        E : number
      }
      """);

  var tokens = mg.tokens();
  IO.println(tokens);

  var lexer = Lexer.createLexer(tokens);
  var iterator = lexer.tokenize("32 12 14");

  while (iterator.hasNext()) {
    var terminal = iterator.next();
    //IO.println(terminal.name() + " " + terminal.value());
  }
}
