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

  static void cachedLifetimes(BuffaWire decoder) {
    try (var context =
            Context.newBuilder("buffa-test")
                .allowExperimentalOptions(true)
                .option("engine.Compilation", "false")
                .build();
        var plan =
            decoder.compile(
                WellKnownAcceptance.model().type("google.protobuf.Int64Value"), 65536)) {
      context.initialize("buffa-test");
      context.enter();
      try {
        var cachedRoot = new WktRoot(false);
        var cachedTarget = cachedRoot.getCallTarget();
        try (var message = plan.parseJson("7", 4096)) {
          Object row = message.row();
          Acceptance.check(((Long) cachedTarget.call(row, 1)) == 7, "cached owner value");
          message.close();
          Acceptance.rejects(() -> cachedTarget.call(row, 1), IllegalStateException.class);
        }
        var root = new WktRoot(false);
        var target = root.getCallTarget();
        var messages = new java.util.ArrayList<TypedMessage>();
        try {
          // Keep all owners alive so invalidated entries cannot be reclaimed before saturation.
          for (int i = 1; i <= 8; i++) {
            var message = plan.parseJson(Integer.toString(i), 4096);
            messages.add(message);
            Acceptance.check(((Long) target.call(message.row(), 1)) == i, "saturated owner value");
          }
          for (var message : messages) {
            Object row = message.row();
            message.close();
            Acceptance.rejects(() -> target.call(row, 1), IllegalStateException.class);
          }
          Acceptance.check(NodeUtil.countNodes(root) <= 192, "owner cache AST budget");
        } finally {
          for (var message : messages) message.close();
        }
      } finally {
        context.leave();
      }
    }
  }

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
        generatedReads(decoder);
        nonconstantLayouts();
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
            // Constant receivers may fold scalar values, but must still invalidate on close.
            var constantRoot = new ConstantWktRoot(row);
            var constantTarget = constantRoot.getCallTarget();
            for (int i = 0; i < 200 && !constantRoot.compiled; i++) constantTarget.call(100000);
            Acceptance.check(constantRoot.compiled, "constant WKT reads compiled " + type);
            long firstValue = type.equals("Timestamp") ? 1 : type.equals("Duration") ? -1 : 7;
            Acceptance.check(
                ((Long) constantTarget.call(1000000)) == firstValue * 1000000,
                "constant WKT value");
            typed.close();
            Acceptance.rejects(() -> typedTarget.call(row, 1), IllegalStateException.class);
            Acceptance.rejects(() -> constantTarget.call(1), IllegalStateException.class);
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
        var constantBytes = new ReadRoot(row);
        var constantByteTarget = constantBytes.getCallTarget();
        for (int i = 0; i < 200 && !constantBytes.compiled; i++)
          constantByteTarget.call(row, 100_000);
        Acceptance.check(constantBytes.compiled, "constant byte reads compiled");
        Acceptance.check(
            ((Long) constantByteTarget.call(row, 1_000_000)) == 255_000_000L,
            "constant borrowed byte value");
        if (decoder.selected() != BuffaWire.Selected.JAVA) {
          message.close();
          Acceptance.rejects(() -> target.call(row, 1), IllegalStateException.class);
          Acceptance.rejects(() -> constantByteTarget.call(row, 1), IllegalStateException.class);
        }
        System.out.println("BUFFA JIT compiled=true nodes=" + NodeUtil.countNodes(root));
      } finally {
        context.leave();
      }
    }
  }

  private static void nonconstantLayouts() {
    var schema =
        Schema.of(
            Field.integer("n", 32, true, false, false),
            new Field("d", Field.Kind.FLOAT64, false, false),
            new Field("b", Field.Kind.BOOLEAN, false, false));
    var first = new StaticLayout(TestLanguage.current(), schema, Limits.DEFAULT);
    var second = new StaticLayout(TestLanguage.current(), schema, Limits.DEFAULT);
    var firstRow = first.create(1000L, 1.5, true);
    var secondRow = second.create(2000L, 2.5, false);
    var root = new LayoutRoot();
    var target = root.getCallTarget();
    for (int i = 0; i < 200 && !root.compiled; i++) {
      target.call(first, firstRow, 100000);
      target.call(second, secondRow, 100000);
    }
    Acceptance.check(root.compiled, "nonconstant static layouts compiled");
    Acceptance.check(
        ((Long) target.call(first, firstRow, 1000)) == 2002000L, "first runtime layout");
    Acceptance.check(
        ((Long) target.call(second, secondRow, 1000)) == 4002000L, "second runtime layout");
    Acceptance.rejects(() -> target.call(first, secondRow, 1), ContractViolation.class);
    Acceptance.check(NodeUtil.countNodes(root) <= 192, "runtime layout AST budget");
  }

  private static final class LayoutRoot extends RootNode {
    boolean compiled;

    LayoutRoot() {
      super(TestLanguage.current());
    }

    @Override
    public Object execute(VirtualFrame frame) {
      var layout = (StaticLayout) frame.getArguments()[0];
      var row = (StaticLayout.Record) frame.getArguments()[1];
      int count = (Integer) frame.getArguments()[2];
      long sum = 0;
      for (int i = 0; i < count; i++)
        sum +=
            (Long) layout.get(row, 0)
                + layout.getLong(row, 0)
                + (long) layout.getDouble(row, 1)
                + (layout.getBoolean(row, 2) ? 1 : 0);
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }

  private static void generatedReads(BuffaWire decoder) {
    var plan =
        dev.elide.truffledata.generated.TruffleDataContracts.create(
                TestLanguage.current(), Limits.DEFAULT, 16, 65536)
            .type("truffledata.lossless.Sample");
    String text = "count: 1000 f: 1.5 d: 2.5 flag: true";
    try (var nativePlan = decoder.compile(plan, 65536);
        var message = nativePlan.parseText(text, 65536)) {
      for (dev.elide.truffledata.protobuf.ProtoAccess row :
          java.util.List.of(plan.parseText(text), message.row())) {
        var root = new GeneratedRoot(plan);
        var target = root.getCallTarget();
        for (int i = 0; i < 200 && !root.compiled; i++) target.call(row, 100000);
        Acceptance.check(root.compiled, "generated primitive getters compiled");
        Acceptance.check(
            ((Long) target.call(row, 1000000)) == 1004000000L, "generated primitive values");
        Acceptance.check(NodeUtil.countNodes(root) <= 192, "generated getter AST budget");
        if (row instanceof TypedRow) {
          message.close();
          Acceptance.rejects(() -> target.call(row, 1), IllegalStateException.class);
        }
      }
    }
  }

  private static final class GeneratedRoot extends RootNode {
    private final dev.elide.truffledata.generated.TruffleDataContracts
            .Access_truffledata$lossless$Sample
        access;
    private final com.oracle.truffle.api.profiles.ValueProfile rowClass =
        com.oracle.truffle.api.profiles.ValueProfile.createClassProfile();
    boolean compiled;

    GeneratedRoot(dev.elide.truffledata.protobuf.ProtoPlan plan) {
      super(TestLanguage.current());
      access =
          new dev.elide.truffledata.generated.TruffleDataContracts
              .Access_truffledata$lossless$Sample(plan);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      var row =
          (dev.elide.truffledata.protobuf.ProtoAccess) rowClass.profile(frame.getArguments()[0]);
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int i = 0; i < count; i++)
        sum +=
            access.get_count(row)
                + (long) access.get_f(row)
                + (long) access.get_d(row)
                + (access.get_flag(row) ? 1 : 0);
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }

  private static final class ConstantWktRoot extends RootNode {
    private final Object row;
    @Child private ReadLongNode read = ReadLongNode.create();
    boolean compiled;

    ConstantWktRoot(Object row) {
      super(null);
      this.row = row;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      int count = (Integer) frame.getArguments()[0];
      long sum = 0;
      for (int i = 0; i < count; i++) sum += read.execute(row, 0);
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
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
    private final @org.jspecify.annotations.Nullable Object constantRow;
    @Child private ReadLengthNode length = ReadLengthNode.create();
    @Child private DataLibrary rows = DataLibrary.factory().createDispatched(3);
    @Child private InteropLibrary bytes = InteropLibrary.getFactory().createDispatched(3);
    @Child private InteropLibrary numbers = InteropLibrary.getFactory().createDispatched(3);
    boolean compiled;

    ReadRoot() {
      this(null);
    }

    ReadRoot(@org.jspecify.annotations.Nullable Object constantRow) {
      super(null);
      this.constantRow = constantRow;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object row = constantRow == null ? frame.getArguments()[0] : constantRow;
      Object value = rows.read(row, 2);
      if (length.execute(row, 2) != 1) throw new AssertionError("compiled length");
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
