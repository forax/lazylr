import com.github.forax.lazylr.*;

void main() {
  var mg = MetaGrammar.load("""
      tokens {
        number: /[0-9]+/
        /[ \\t]+/
      }
      grammar {
        E : A
        E : B
        A : number
        B : number
      }
      """);

  mg.verify();


  // ── State 4 ─────────────────────────────────
  //   A :  number •
  //   B :  number •
  //  ······································
  //   reduce( A : number         ) on [$ 🔥]
  //   reduce( B : number         ) on [$ 🔥]
}
