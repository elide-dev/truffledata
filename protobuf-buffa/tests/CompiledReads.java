package dev.elide.truffledata.buffa.tests;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.nodes.*;
import dev.elide.truffledata.*;
import dev.elide.truffledata.buffa.*;
import dev.elide.truffledata.protobuf.ProtoPlan;
import java.util.List;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.Context;

@SuppressWarnings("removal")
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
        cachedPlans(decoder);
        arrayViews(decoder);
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

  private static void slabSessionReads(BuffaWire decoder) {
    if (decoder.selected() == BuffaWire.Selected.JAVA) return;
    var contract = WellKnownAcceptance.model().type("google.protobuf.Int64Value");
    try (var plan = decoder.compile(contract, 65536);
        var input = decoder.allocate(2).put(new byte[] {8, 42});
        var session = plan.newSession(4096)) {
      var row = session.decode(input);
      var root = new ConstantWktRoot(row);
      var target = root.getCallTarget();
      for (int i = 0; i < 200 && !root.compiled; i++) target.call(100000);
      Acceptance.check(root.compiled, "session constant reads compiled");
      Acceptance.check((Long) target.call(1000) == 42000, "session compiled scalar");
      session.decode(input);
      Acceptance.rejects(() -> target.call(1), IllegalStateException.class);
      var current = session.decode(input);
      var variable = new WktRoot(false);
      var variableTarget = variable.getCallTarget();
      for (int i = 0; i < 200 && !variable.compiled; i++) variableTarget.call(current, 100000);
      Acceptance.check(variable.compiled, "session parameter reads compiled");
      session.close();
      Acceptance.rejects(() -> variableTarget.call(current, 1), IllegalStateException.class);
    }
  }

  private static void arrayViews(BuffaWire decoder) {
    var contract = TypedAcceptance.plan("RepeatedScalars");
    var root = new ArrayLengthRoot();
    var target = root.getCallTarget();
    var interop = InteropLibrary.getUncached();
    var data = DataLibrary.factory().getUncached();
    byte[] wire =
        contract.encode(
            contract
                .newBuilder()
                .set(0, new DataArray(new Object[] {1L, -2L}))
                .set(14, new DataArray(new Object[] {DataArray.copyOfBytes(new byte[] {7})}))
                .build());
    try (var plan = decoder.compile(contract, 65536);
        var input = decoder.allocate(wire.length).put(wire);
        var emptyInput = decoder.allocate(0);
        var message = plan.decodeView(input, 65536);
        var empty = plan.decodeView(emptyInput, 65536)) {
      Acceptance.check((Integer) target.call(message.row()) == 2, "shared native array length");
      Acceptance.check((Integer) target.call(empty.row()) == 0, "empty native array length");
      var successful = new java.util.concurrent.atomic.AtomicInteger();
      Object countedRow = message.row();
      var other =
          new Thread(
              () -> {
                if (((OwnedArray) data.read(countedRow, 0)).size() == 2)
                  successful.incrementAndGet();
              });
      other.start();
      try {
        other.join();
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        throw new AssertionError(failure);
      }
      Acceptance.check(successful.get() == 1, "durable array supports cross-thread reads");
      Acceptance.rejects(() -> message.row().get(-1), IndexOutOfBoundsException.class);
      Acceptance.rejects(() -> message.row().get(contract.size()), IndexOutOfBoundsException.class);
      Object array = data.read(message.row(), 0);
      try {
        Acceptance.check(interop.hasArrayElements(array), "native array interop");
        Acceptance.check(interop.getArraySize(array) == 2, "native interop count");
        Acceptance.check(
            !interop.isArrayElementReadable(array, -1)
                && interop.isArrayElementReadable(array, 1)
                && !interop.isArrayElementReadable(array, 2),
            "native array bounds");
        Acceptance.check(interop.readArrayElement(array, 1).equals(-2L), "native array element");
        try {
          interop.readArrayElement(array, 2);
          throw new AssertionError("Expected native array bounds rejection");
        } catch (InvalidArrayIndexException expected) {
          /* bounds */
        }
      } catch (UnsupportedMessageException failure) {
        throw new AssertionError(failure);
      } catch (InvalidArrayIndexException failure) {
        throw new AssertionError(failure);
      }
      OwnedArray detached = (OwnedArray) data.read(message.row(), 14);
      Object row = message.row();
      message.close();
      Acceptance.check((Integer) target.call(row) == 2, "durable cached array survives close");
      Acceptance.check(((OwnedArray) array).size() == 2, "durable array size after close");
      Acceptance.check(
          ((OwnedArray) detached.get(0)).get(0).equals(7L), "nested array snapshot survives close");
    }
  }

  private static final class ArrayLengthRoot extends RootNode {
    @Child private ReadLengthNode length = ReadLengthNode.create();

    ArrayLengthRoot() {
      super(null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return length.execute(frame.getArguments()[0], 0);
    }
  }

  private static void cachedPlans(BuffaWire decoder) {
    var root = new PlanFieldsRoot();
    var target = root.getCallTarget();
    for (int i = 0; i < 8; i++) {
      var kind = (i & 1) == 0 ? ProtoPlan.Kind.INT32 : ProtoPlan.Kind.UINT32;
      long fallback = 10L + i;
      boolean flag = (i & 1) == 0;
      var contract =
          new ProtoPlan.Model(
                  null,
                  List.of(
                      new ProtoPlan.Type(
                          "Guard" + i,
                          false,
                          List.of(
                              new ProtoPlan.FieldPlan(
                                  "n", 1, kind, false, false, true, false, false, -1, "", null,
                                  fallback),
                              new ProtoPlan.FieldPlan(
                                  "b",
                                  2,
                                  ProtoPlan.Kind.BOOL,
                                  false,
                                  false,
                                  true,
                                  false,
                                  false,
                                  -1,
                                  "",
                                  null,
                                  flag)))),
                  Limits.DEFAULT,
                  16,
                  65536)
              .type("Guard" + i);
      long value = kind == ProtoPlan.Kind.UINT32 ? 4294967295L : -123;
      byte[] wire = contract.encode(contract.newBuilder().set(0, value).set(1, !flag).build());
      try (var plan = decoder.compile(contract, 65536);
          var empty = decoder.allocate(0);
          var input = decoder.allocate(wire.length).put(wire);
          var defaults = plan.decodeView(empty, 65536);
          var message = plan.decodeView(input, 65536)) {
        for (int repeat = 0; repeat < 4; repeat++) {
          Acceptance.check(
              (Long) target.call(defaults.row()) == fallback + (flag ? 100 : 0),
              "cached plan defaults");
          Acceptance.check(
              (Long) target.call(message.row()) == value + (!flag ? 100 : 0),
              "cached plan integer kind");
        }
        Object closed = message.row();
        message.close();
        Acceptance.check(
            (Long) target.call(closed) == value + (!flag ? 100 : 0),
            "durable cached plan after close");
        Acceptance.check(
            (Long) target.call(defaults.row()) == fallback + (flag ? 100 : 0),
            "independent owner after close");
      }
    }
    Acceptance.check(NodeUtil.countNodes(root) <= 192, "plan cache AST budget");
  }

  private static final class PlanFieldsRoot extends RootNode {
    @Child private ReadLongNode number = ReadLongNode.create();
    @Child private ReadBooleanNode flag = ReadBooleanNode.create();

    PlanFieldsRoot() {
      super(null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object row = frame.getArguments()[0];
      return number.execute(row, 0) + (flag.execute(row, 1) ? 100 : 0);
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
        nativeViewReads(decoder);
        slabSessionReads(decoder);
        generatedReads(decoder);
        generatedOwnedReads();
        generatedProjection();
        nonconstantLayouts();
        try (var plan = decoder.compile(TypedAcceptance.plan("Sample"), 65536)) {
          for (int format = 0; format < 3; format++) {
            try (var typedInput = decoder.allocate(3).put(new byte[] {26, 1, (byte) 255});
                var typed =
                    format == 0
                        ? TypedAcceptance.decode(plan, typedInput, 4096)
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
              if (format == 0)
                Acceptance.check(
                    ((Long) typedTarget.call(typedRow, 1)) == 255,
                    "durable compiled value after close");
              else
                Acceptance.rejects(
                    () -> typedTarget.call(typedRow, 1), IllegalStateException.class);
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

  private static void generatedProjection() {
    var model =
        dev.elide.truffledata.generated.WellKnownContracts.create(
            TestLanguage.current(), Limits.DEFAULT, 16, 65536);
    var access =
        new dev.elide.truffledata.generated.WellKnownContracts.Access_google$protobuf$Timestamp(
            model.type("google.protobuf.Timestamp"));
    byte[][] wire = {
      access.newBuilder().set_seconds(1000).set_nanos(1).build().encode(),
      access.newBuilder().set_seconds(1002).set_nanos(3).build().encode()
    };
    var root = new ProjectionRoot(access);
    var target = root.getCallTarget();
    for (int i = 0; i < 10; i++) target.call(wire, 100000);
    Acceptance.check(root.compiled, "parse/project compiled");
    Acceptance.check(((Long) target.call(wire, 1000000)) == 1003000000L, "parse/project checksum");
    Acceptance.check(NodeUtil.countNodes(root) <= 192, "parse/project AST budget");
    var bean =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    Acceptance.check(bean.isThreadAllocatedMemorySupported(), "projection allocation counter");
    bean.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId();
    target.call(wire, 100000);
    long before = bean.getThreadAllocatedBytes(thread);
    target.call(wire, 100000);
    double allocated = (bean.getThreadAllocatedBytes(thread) - before) / 100000.0;
    System.out.println("ALLOCATION parse-project bytes/message=" + allocated);
    Acceptance.check(allocated >= 0 && allocated <= 1, "parse/project allocation budget");
    byte[] unknown = java.util.Arrays.copyOf(wire[0], wire[0].length + 2);
    unknown[wire[0].length] = 120;
    unknown[wire[0].length + 1] = 7;
    Acceptance.check(
        java.util.Arrays.equals(unknown, access.decodeForProjection(unknown).encode()),
        "projection unknown fallback preserves wire");
    Acceptance.rejects(() -> access.decodeForProjection(new byte[] {8}), ContractViolation.class);
  }

  private static final class ProjectionRoot extends RootNode {
    private final dev.elide.truffledata.generated.WellKnownContracts
            .Access_google$protobuf$Timestamp
        access;
    boolean compiled;

    ProjectionRoot(
        dev.elide.truffledata.generated.WellKnownContracts.Access_google$protobuf$Timestamp
            access) {
      super(TestLanguage.current());
      this.access = access;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      byte[][] wire = (byte[][]) frame.getArguments()[0];
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int i = 0; i < count; i++) {
        var row = access.decodeForProjection(wire[i & 1]);
        sum += row.get_seconds() + row.get_nanos();
      }
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }

  private static void generatedOwnedReads() {
    var model =
        dev.elide.truffledata.generated.TruffleDataContracts.create(
            TestLanguage.current(), Limits.DEFAULT, 16, 65536);
    var plan = model.type("truffledata.lossless.Flat");
    var access =
        new dev.elide.truffledata.generated.TruffleDataContracts.Access_truffledata$lossless$Flat(
            plan);
    var row =
        access.decode(plan.encode(plan.parseText("i32: 1000 single: 1.5 real: 2.5 flag: true")));
    for (boolean direct : new boolean[] {false, true}) {
      var root = new OwnedRoot(direct);
      var target = root.getCallTarget();
      for (int i = 0; i < 10; i++) target.call(row, 100000);
      Acceptance.check(root.compiled, "generated owned records compiled");
      Acceptance.check(((Long) target.call(row, 1000000)) == 1004000000L, "owned primitive values");
      Acceptance.check(NodeUtil.countNodes(root) <= 192, "owned getter AST budget");
    }
  }

  private static final class OwnedRoot extends RootNode {
    private final boolean direct;
    @Child private DataLibrary data = DataLibrary.factory().createDispatched(3);
    boolean compiled;

    OwnedRoot(boolean direct) {
      super(TestLanguage.current());
      this.direct = direct;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      var row =
          (dev.elide.truffledata.generated.TruffleDataContracts.Message_truffledata$lossless$Flat)
              frame.getArguments()[0];
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      try {
        for (int i = 0; i < count; i++)
          sum +=
              direct
                  ? row.get_i32()
                      + (long) row.get_single()
                      + (long) row.get_real()
                      + (row.get_flag() ? 1 : 0)
                  : data.readLong(row, 0)
                      + (long) data.readDouble(row, 11)
                      + (long) data.readDouble(row, 12)
                      + (data.readBoolean(row, 10) ? 1 : 0);
      } catch (UnexpectedResultException wrongType) {
        throw new AssertionError(wrongType);
      }
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
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

  private static void nativeViewReads(BuffaWire decoder) {
    var sample = TypedAcceptance.plan("Sample");
    var fields =
        java.util.List.of(sample.field(0), sample.field(1), sample.field(2), sample.field(22));
    var contract =
        new dev.elide.truffledata.protobuf.ProtoPlan.Model(
                null,
                java.util.List.of(
                    new dev.elide.truffledata.protobuf.ProtoPlan.Type("ViewBytes", false, fields)),
                Limits.DEFAULT,
                16,
                4096)
            .type("ViewBytes");
    try (var plan = decoder.compile(contract, 65536);
        var input = decoder.allocate(6).put(new byte[] {8, (byte) 0xe8, 7, 26, 1, (byte) 255});
        var message = plan.decodeView(input, 4096)) {
      Acceptance.check(message.slabBacked(), "compiled native view selected");
      Object row = message.row();
      Object originalBytes = message.row().get(2);
      var constant = new ConstantValueRoot(row);
      var constantTarget = constant.getCallTarget();
      for (int i = 0; i < 200 && !constant.compiled; i++) constantTarget.call(100_000);
      Acceptance.check(constant.compiled, "constant native value reader compiled");
      // Host access promotes the single-value cache after the guest captured this row.
      Acceptance.check(
          ((OwnedArray) message.row().get(3)).size() == 0, "empty repeated field promotes cache");
      constant.compiled = false;
      Acceptance.check(
          constantTarget.call(1000) == originalBytes,
          "host cache growth preserves compiled value identity");
      Acceptance.check(constant.compiled, "host cache growth remains compiled");
      var bytes = new ReadRoot();
      var byteTarget = bytes.getCallTarget();
      var integer = new WktRoot(false);
      var integerTarget = integer.getCallTarget();
      for (int i = 0; i < 200 && (!bytes.compiled || !integer.compiled); i++) {
        byteTarget.call(row, 100_000);
        integerTarget.call(row, 100_000);
      }
      Acceptance.check(bytes.compiled && integer.compiled, "native view reads compiled");
      Acceptance.check((Long) byteTarget.call(row, 1000) == 255_000L, "native view byte value");
      Acceptance.check(
          (Long) integerTarget.call(row, 1000) == 1_000_000L, "native view primitive value");
      Acceptance.check(
          NodeUtil.countNodes(bytes) <= 192 && NodeUtil.countNodes(integer) <= 192,
          "native view AST budget");
      message.close();
      Acceptance.check(constantTarget.call(1) == originalBytes, "durable constant after close");
      Acceptance.check((Long) byteTarget.call(row, 1) == 255, "durable byte after close");
      Acceptance.check((Long) integerTarget.call(row, 1) == 1000, "durable integer after close");
    }
  }

  private static final class ConstantValueRoot extends RootNode {
    private final Object row;
    @Child private DataLibrary data = DataLibrary.factory().createDispatched(3);
    boolean compiled;

    ConstantValueRoot(Object row) {
      super(null);
      this.row = row;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      int count = (Integer) frame.getArguments()[0];
      Object value = Missing.INSTANCE;
      for (int i = 0; i < count; i++) value = data.read(row, 2);
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return value;
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
