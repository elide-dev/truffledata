package dev.elide.truffledata.probe;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import java.math.BigDecimal;
import org.graalvm.polyglot.Context;

/** Intentionally invalid. Its Native Image build must fail with a blocklist diagnostic. */
public final class BlocklistProbe {
  public static void main(String[] args) {
    try (Context context = Context.newBuilder("td-probe").build()) {
      context.initialize("td-probe");
      context.enter();
      try {
        Object result = new UnsafeRoot().getCallTarget().call(new BigDecimal("1"));
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
      return ((BigDecimal) frame.getArguments()[0]).add(BigDecimal.ONE);
    }
  }
}
