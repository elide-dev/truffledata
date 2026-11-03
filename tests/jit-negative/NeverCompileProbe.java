package dev.elide.truffledata.probe;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.polyglot.Context;

/** Intentionally invalid. Its Native Image build must fail with a blocklist diagnostic. */
public final class NeverCompileProbe {
  public static void main(String[] args) {
    try (Context context = Context.newBuilder("td-probe").build()) {
      context.initialize("td-probe");
      context.enter();
      try {
        Object result = new UnsafeRoot().getCallTarget().call(1L);
        System.out.println(result);
      } finally {
        context.leave();
      }
    }
  }

  private static final class UnsafeRoot extends RootNode {
    UnsafeRoot() {
      super(ProbeLanguage.current());
    }

    @Override
    public Object execute(VirtualFrame frame) {
      // Deliberately missing @TruffleBoundary: prove the runtime's blocklist is enforced.
      CompilerAsserts.neverPartOfCompilation("TruffleData negative control");
      return frame.getArguments()[0];
    }
  }
}
