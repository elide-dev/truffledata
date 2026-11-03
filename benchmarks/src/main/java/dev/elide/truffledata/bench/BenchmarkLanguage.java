package dev.elide.truffledata.bench;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import dev.elide.truffledata.*;

@TruffleLanguage.Registration(
    id = "td-bench",
    name = "TruffleData Benchmark",
    version = "1",
    characterMimeTypes = "application/x-td-bench")
public final class BenchmarkLanguage extends TruffleLanguage<BenchmarkLanguage.State> {
  private static final ContextReference<State> REFERENCE =
      ContextReference.create(BenchmarkLanguage.class);

  public record State(BenchmarkLanguage language, StaticLayout layout) {}

  public static State current() {
    return REFERENCE.get(null);
  }

  @Override
  protected State createContext(Env env) {
    return new State(
        this, new StaticLayout(this, Schema.of(Field.requiredLong("a")), Limits.DEFAULT));
  }

  @Override
  protected CallTarget parse(ParsingRequest request) {
    return new RootNode(this) {
      @Override
      public Object execute(VirtualFrame frame) {
        return current().layout().create(1000L);
      }
    }.getCallTarget();
  }
}
