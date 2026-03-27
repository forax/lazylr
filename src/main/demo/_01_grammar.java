import com.github.forax.lazylr.*;

void main() {
  var mg = MetaGrammar.load("""
      grammar {
        E : number
      }
      """);

  var grammar = mg.grammar();
  IO.println(grammar);

  var productions = grammar.productions();
  var production = productions.getFirst();
  var nonTerminal = production.head();
  //IO.println(nonTerminal);

  var terminal = production.body().getFirst();
  //IO.println(terminal);
}
