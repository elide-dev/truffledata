package dev.elide.truffledata.probe;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;

/** Isolated eligibility probe: stock parsing must not be hidden behind a benchmark boundary. */
public final class StockParseProbe {
  private StockParseProbe() {}

  public static void main(String[] args) {
    try (var context =
        Context.newBuilder("td-probe")
            .allowExperimentalOptions(true)
            .option("engine.BackgroundCompilation", "false")
            .option("engine.CompilationFailureAction", "Throw")
            .option("compiler.TreatPerformanceWarningsAsErrors", "all")
            .option("compiler.MaximumGraalGraphSize", "10000")
            .build()) {
      context.initialize("td-probe");
      context.enter();
      try {
        var root = new ParseRoot();
        var target = (OptimizedCallTarget) root.getCallTarget();
        byte[] wire = {8, 123, 16, 1};
        for (int i = 0; i < 10; i++) target.call(wire, 10000);
        target.compile(true);
        target.waitForCompilation();
        if (((Long) target.call(wire, 100000)) != 12400000L
            || !root.compiled
            || !target.isValidLastTier())
          throw new AssertionError("Stock parser did not execute at peak tier");
        System.out.println("STOCK_PARSE_ROOT_COMPILED_NO_EXPLICIT_BOUNDARY");
      } finally {
        context.leave();
      }
    }
  }

  private static final class ParseRoot extends RootNode {
    boolean compiled;

    ParseRoot() {
      super(ProbeLanguage.current());
    }

    @Override
    public Object execute(VirtualFrame frame) {
      byte[] wire = (byte[]) frame.getArguments()[0];
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int i = 0; i < count; i++) {
        sum += parse(wire);
      }
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }

    private static long parse(byte[] wire) {
      try {
        var message = Timestamp.parseFrom(wire);
        return message.getSeconds() + message.getNanos();
      } catch (InvalidProtocolBufferException invalid) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw invalidInput(invalid);
      }
    }

    @CompilerDirectives.TruffleBoundary
    private static AssertionError invalidInput(InvalidProtocolBufferException invalid) {
      return new AssertionError(invalid);
    }
  }
}
