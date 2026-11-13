package dev.elide.truffledata.bebop.tests;

import static dev.elide.truffledata.bebop.BebopType.Scalar.*;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import dev.elide.truffledata.ReadBooleanNode;
import dev.elide.truffledata.ReadDoubleNode;
import dev.elide.truffledata.ReadLongNode;
import dev.elide.truffledata.bebop.BebopCodec;
import dev.elide.truffledata.bebop.BebopLimits;
import dev.elide.truffledata.bebop.BebopProfile;
import dev.elide.truffledata.bebop.BebopRecord;
import dev.elide.truffledata.bebop.BebopRow;
import dev.elide.truffledata.bebop.BebopSchema;
import dev.elide.truffledata.bebop.BebopSchema.Field;
import dev.elide.truffledata.bebop.BebopSchema.TaggedField;
import dev.elide.truffledata.bebop.BebopType;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;

/**
 * Compiled gates: typed reads of message fields through the indexed lookup, and guest iteration of
 * an array of structs through interop. Each root must run compiled, stay within the AST budget,
 * and allocate at most 0.05 B per read.
 */
final class CompiledReads {
  static final BebopSchema SCHEMA =
      BebopSchema.builder()
          .struct(
              "Row",
              new Field("id", UINT32),
              new Field("score", FLOAT64),
              new Field("flag", BOOL))
          .message(
              "Reading",
              new TaggedField(1, "id", INT64),
              new TaggedField(2, "price", FLOAT32),
              new TaggedField(3, "active", BOOL),
              new TaggedField(40, "far", INT16))
          .message("Table", new TaggedField(1, "rows", BebopType.array(BebopType.ref("Row"))))
          .build();

  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  static BebopCodec codec(String root, BebopProfile profile) {
    return BebopCodec.compile(SCHEMA, BebopType.ref(root), profile, BebopLimits.DEFAULT);
  }

  static void run(BebopProfile strict) {
    try (var context =
        Context.newBuilder("bebop-test")
            .allowExperimentalOptions(true)
            .option("engine.BackgroundCompilation", "false")
            .option("engine.CompilationFailureAction", "Throw")
            .option("compiler.TreatPerformanceWarningsAsErrors", "all")
            .option("compiler.MaximumGraalGraphSize", "10000")
            .build()) {
      context.initialize("bebop-test");
      context.enter();
      try {
        typedReads(BebopProfile.GENERAL, "general");
        typedReads(strict, "strict");
        rowIteration();
      } finally {
        context.leave();
      }
    }
  }

  private static double measure(
      String label,
      RootNode root,
      java.util.function.BooleanSupplier compiled,
      Object[] warmup,
      Object[] measured,
      long expected,
      double units) {
    CallTarget target = root.getCallTarget();
    for (int i = 0; i < 200 && !compiled.getAsBoolean(); i++) target.call(warmup);
    check(compiled.getAsBoolean(), label + " must execute compiled code");
    long actual = (Long) target.call(measured);
    check(actual == expected, label + " compiled checksum " + actual + " != " + expected);
    check(NodeUtil.countNodes(root) <= 192, label + " AST budget: " + NodeUtil.countNodes(root));
    var bean =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    check(bean.isThreadAllocatedMemorySupported(), "Allocation counter required");
    bean.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(thread);
    target.call(measured);
    double bytes = (bean.getThreadAllocatedBytes(thread) - before) / units;
    check(bytes >= 0 && bytes <= 0.05, label + " compiled bytes budget: " + bytes);
    return bytes;
  }

  /** Four message rows; the last lacks {@code far}, so its tag directory differs from the others. */
  private static void typedReads(BebopProfile profile, String label) {
    BebopCodec codec = codec("Reading", profile);
    var rows = new BebopRow[4];
    long perRound = 0;
    for (int k = 0; k < 4; k++) {
      boolean active = (k & 1) == 0;
      Object far = k == 3 ? null : (Object) (k - 2);
      var value = new BebopRecord("Reading", 1000L * k - 5, k + 0.5f, active, far);
      rows[k] = codec.adopt(codec.encode(value)).row();
      perRound += 1000L * k - 5 + k + (active ? 1 : 0);
    }
    var root = new TypedReads();
    double bytes =
        measure(
            "Bebop typed reads (" + label + ")",
            root,
            () -> root.compiled,
            new Object[] {rows, 100_000},
            new Object[] {rows, 1_000_000},
            250_000L * perRound,
            1_000_000.0);
    System.out.println("Bebop typed " + label + " compiled=true bytes/read=" + bytes);
  }

  /** Guest iteration: interop readArrayElement over an array of structs, then typed reads. */
  private static void rowIteration() {
    BebopCodec codec = codec("Table", BebopProfile.GENERAL);
    List<Object> structs = new ArrayList<>();
    long perRound = 0;
    for (int k = 0; k < 8; k++) {
      structs.add(new BebopRecord("Row", 0xFFFF_FF00L + k, k * 2.0, (k & 1) == 1));
      perRound += 0xFFFF_FF00L + k + k * 2 + (k & 1);
    }
    BebopRow table = codec.adopt(codec.encode(new BebopRecord("Table", structs))).row();
    Object array = table.get(0);
    var root = new RowIteration();
    double bytes =
        measure(
            "Bebop row iteration",
            root,
            () -> root.compiled,
            new Object[] {array, 100_000},
            new Object[] {array, 1_000_000},
            125_000L * perRound,
            1_000_000.0);
    System.out.println("Bebop row iteration compiled=true bytes/read=" + bytes);
  }

  private static final class TypedReads extends RootNode {
    @Child private ReadLongNode longs = ReadLongNode.create();
    @Child private ReadDoubleNode doubles = ReadDoubleNode.create();
    @Child private ReadBooleanNode booleans = ReadBooleanNode.create();
    boolean compiled;

    TypedReads() {
      super(TestLanguage.current());
    }

    @Override
    public Object execute(VirtualFrame frame) {
      var rows = (BebopRow[]) frame.getArguments()[0];
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int i = 0; i < count; i++) {
        var row = rows[i & 3];
        sum +=
            longs.execute(row, 0)
                + (long) doubles.execute(row, 1)
                + (booleans.execute(row, 2) ? 1 : 0);
      }
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }

  private static final class RowIteration extends RootNode {
    @Child private InteropLibrary elements = InteropLibrary.getFactory().createDispatched(3);
    @Child private ReadLongNode longs = ReadLongNode.create();
    @Child private ReadDoubleNode doubles = ReadDoubleNode.create();
    @Child private ReadBooleanNode booleans = ReadBooleanNode.create();
    boolean compiled;

    RowIteration() {
      super(TestLanguage.current());
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object array = frame.getArguments()[0];
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      try {
        for (int i = 0; i < count; i++) {
          Object row = elements.readArrayElement(array, i & 7);
          sum +=
              longs.execute(row, 0)
                  + (long) doubles.execute(row, 1)
                  + (booleans.execute(row, 2) ? 1 : 0);
        }
      } catch (InteropException failure) {
        throw CompilerDirectives.shouldNotReachHere(failure);
      }
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }
}
