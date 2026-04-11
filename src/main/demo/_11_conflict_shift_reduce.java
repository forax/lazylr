import com.github.forax.lazylr.*;

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \t]+/
      }
      grammar {
        E : number
        E : '(' E ')'
        E : E '+' E
      }
      """);

  //mg.verify();

  mg.parse("40 + 2 + 3", new PrintEvaluator());


  // ── State 6 ─────────────────────────────────
  //   E :  E + E •
  //   E :  E • + E
  //  ······································
  //   goto( +                    ) → 4 🔥
  //   reduce( E : E + E          ) on [$, ), + 🔥]
}
