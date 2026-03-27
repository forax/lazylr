import com.github.forax.lazylr.Evaluator;
import com.github.forax.lazylr.Production;
import com.github.forax.lazylr.Terminal;

import java.util.List;

public final class TreeEvaluator implements Evaluator<TreeEvaluator.Tree> {
  public sealed interface Tree {}
  public record Node(List<Tree> children) implements Tree {}
  public record Value(Object value) implements Tree {}

  @Override
  public Tree evaluate(Terminal terminal) {
    return new Value(terminal.value());
  }

  @Override
  public Tree evaluate(Production production, List<Tree> arguments) {
    return new Node(arguments);
  }
}
