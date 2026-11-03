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
                .option("compiler.TreatPerformanceWarningsAsErrors", "all")
                .option("compiler.MaximumGraalGraphSize", "10000")
                .build();
        var input = decoder.allocate(3).put(new byte[] {18, 1, (byte) 255});
        var message = decoder.decode(input, Limits.DEFAULT, 4, 3, 128)) {
      context.initialize("buffa-test");
      context.enter();
      try {
        try (var plan = decoder.compile(TypedAcceptance.plan("Sample"), 65536)) {
          for (int format = 0; format < 3; format++) {
            try (var typedInput = decoder.allocate(3).put(new byte[] {26, 1, (byte) 255});
                var typed =
                    format == 0
                        ? plan.decode(typedInput, 4096)
                        : format == 1
                            ? plan.parseText("blob: '\\377'", 4096)
                            : plan.parseJson("{\"blob\":\"/w==\"}", 4096)) {
              var typedRoot = new ReadRoot();
              var typedTarget = typedRoot.getCallTarget();
              Object typedRow = typed.row();
              for (int i = 0; i < 200 && !typedRoot.compiled; i++)
                typedTarget.call(typedRow, 100_000);
              Acceptance.check(typedRoot.compiled, "typed reads compiled format " + format);
              Acceptance.check(
                  ((Long) typedTarget.call(typedRow, 1_000_000)) == 255_000_000L,
                  "typed compiled value");
              Acceptance.check(NodeUtil.countNodes(typedRoot) <= 192, "typed access AST budget");
              typed.close();
              Acceptance.rejects(() -> typedTarget.call(typedRow, 1), IllegalStateException.class);
            }
          }
        }
        for (String type : java.util.List.of("Timestamp", "Duration", "Int64Value")) {
          var contract = WellKnownAcceptance.model().type("google.protobuf." + type);
          String json =
              type.equals("Timestamp")
                  ? "\"1970-01-01T00:00:01.000000002Z\""
                  : type.equals("Duration") ? "\"-1.000000002s\"" : "7";
          long expected = type.equals("Timestamp") ? 3 : type.equals("Duration") ? -3 : 7;
          try (var plan = decoder.compile(contract, 65536);
              var typed = plan.parseJson(json, 4096)) {
            var typedRoot = new WktRoot(!type.equals("Int64Value"));
            var typedTarget = typedRoot.getCallTarget();
            Object row = typed.row();
            for (int i = 0; i < 200 && !typedRoot.compiled; i++) typedTarget.call(row, 100000);
            Acceptance.check(typedRoot.compiled, "WKT compiled " + type);
            Acceptance.check(
                ((Long) typedTarget.call(row, 1000000)) == expected * 1000000, "WKT scalar reads");
            Acceptance.check(NodeUtil.countNodes(typedRoot) <= 192, "WKT AST budget");
            if (!ImageInfo.inImageCode()) {
              var bean =
                  (com.sun.management.ThreadMXBean)
                      java.lang.management.ManagementFactory.getThreadMXBean();
              bean.setThreadAllocatedMemoryEnabled(true);
              long thread = Thread.currentThread().threadId(),
                  before = bean.getThreadAllocatedBytes(thread);
              typedTarget.call(row, 1000000);
              double bytes = (bean.getThreadAllocatedBytes(thread) - before) / 1000000.0;
              Acceptance.check(bytes <= 0.05, "WKT compiled allocation budget");
            }
            typed.close();
            Acceptance.rejects(() -> typedTarget.call(row, 1), IllegalStateException.class);
          }
        }
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

  private static final class WktRoot extends RootNode {
    @Child private ReadLongNode first = ReadLongNode.create();
    @Child private ReadLongNode second = ReadLongNode.create();
    private final boolean paired;
    boolean compiled;

    WktRoot(boolean paired) {
      super(null);
      this.paired = paired;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object row = frame.getArguments()[0];
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int i = 0; i < count; i++)
        sum += first.execute(row, 0) + (paired ? second.execute(row, 1) : 0);
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }

  private static final class ReadRoot extends RootNode {
    @Child private ReadLengthNode length = ReadLengthNode.create();
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
      if (length.execute(frame.getArguments()[0], 2) != 1)
        throw new AssertionError("compiled length");
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
