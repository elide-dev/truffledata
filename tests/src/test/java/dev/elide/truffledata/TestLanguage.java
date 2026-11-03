package dev.elide.truffledata;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

@TruffleLanguage.Registration(
    id = "td-test",
    name = "TruffleData Test",
    version = "1",
    characterMimeTypes = "application/x-td-test")
public final class TestLanguage extends TruffleLanguage<TestLanguage.State> {
  private static final ContextReference<State> REFERENCE =
      ContextReference.create(TestLanguage.class);

  public record State(TestLanguage language, StaticLayout layout) {}

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
