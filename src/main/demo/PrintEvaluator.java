import com.github.forax.lazylr.Evaluator;
import com.github.forax.lazylr.Production;
import com.github.forax.lazylr.Terminal;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class PrintEvaluator implements Evaluator<Object> {
  @Override
  public Object evaluate(@NonNull Terminal terminal) {
    IO.println("terminal " + terminal.name() + "=" + terminal.value());
    return terminal;
  }
  @Override
  public Object evaluate(@NonNull Production production, @NonNull List<Object> args) {
    IO.println("production " + production.name() + " args " + args);
    return null;
  }
}
