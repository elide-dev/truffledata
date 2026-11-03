package dev.elide.truffledata.buffa.tests;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.nodes.*;
import dev.elide.truffledata.*;
import dev.elide.truffledata.buffa.*;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.Context;

final class CompiledReads {
  private CompiledReads() {}

  static void run(BuffaWire decoder) {
    try (var context =
            Context.newBuilder("buffa-test")
                .allowExperimentalOptions(true)
                .option("engine.BackgroundCompilation", "false")
                .option("engine.CompilationFailureAction", "Throw")
                .build();
        var input = decoder.allocate(3).put(new byte[] {18, 1, (byte) 255});
        var message = decoder.decode(input, Limits.DEFAULT, 4, 3, 128)) {
      context.initialize("buffa-test");
      context.enter();
      try {
        var root = new ReadRoot();
        var target = root.getCallTarget();
        Object row = message.fields().get(0);
        for (int i = 0; i < 200 && !root.compiled; i++) target.call(row, 100_000);
        Acceptance.check(root.compiled, "borrowed reads executed compiled code");
        Acceptance.check(((Long) target.call(row, 1_000_000)) == 255_000_000L, "compiled value");
        Acceptance.check(NodeUtil.countNodes(root) <= 192, "borrowed access AST budget");
        if (!ImageInfo.inImageCode()) {
          var bean =
              (com.sun.management.ThreadMXBean)
                  java.lang.management.ManagementFactory.getThreadMXBean();
          bean.setThreadAllocatedMemoryEnabled(true);
          long id = Thread.currentThread().threadId();
          long before = bean.getThreadAllocatedBytes(id);
          target.call(row, 1_000_000);
          double bytes = (bean.getThreadAllocatedBytes(id) - before) / 1_000_000.0;
          System.out.println("BUFFA compiled bytes/read=" + bytes);
          Acceptance.check(bytes <= 0.05, "compiled allocation budget");
        }
        if (decoder.selected() != BuffaWire.Selected.JAVA) {
          message.close();
          Acceptance.rejects(() -> target.call(row, 1), IllegalStateException.class);
        }
        System.out.println("BUFFA JIT compiled=true nodes=" + NodeUtil.countNodes(root));
      } finally {
        context.leave();
      }
    }
  }

  private static final class ReadRoot extends RootNode {
    @Child private DataLibrary rows = DataLibrary.factory().createDispatched(3);
    @Child private InteropLibrary bytes = InteropLibrary.getFactory().createDispatched(3);
    @Child private InteropLibrary numbers = InteropLibrary.getFactory().createDispatched(3);
    boolean compiled;

    ReadRoot() {
      super(null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object value = rows.read(frame.getArguments()[0], 2);
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      try {
        for (int i = 0; i < count; i++) sum += numbers.asLong(bytes.readArrayElement(value, 0));
      } catch (InteropException e) {
        throw new AssertionError(e);
      }
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }
}
