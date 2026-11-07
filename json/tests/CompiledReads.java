package dev.elide.truffledata.json.tests;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.ReadBooleanNode;
import dev.elide.truffledata.ReadDoubleNode;
import dev.elide.truffledata.ReadLongNode;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.json.JsonAccess;
import dev.elide.truffledata.json.JsonPlan;
import dev.elide.truffledata.json.JsonRecord;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;

final class CompiledReads {
  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  static void run() {
    var schema =
        Schema.of(
            Field.requiredLong("n"),
            new Field("d", Field.Kind.FLOAT64, false, false),
            new Field("b", Field.Kind.BOOLEAN, false, false));
    try (var context =
            Context.newBuilder("json-test")
                .allowExperimentalOptions(true)
                .option("engine.BackgroundCompilation", "false")
                .option("engine.CompilationFailureAction", "Throw")
                .option("compiler.TreatPerformanceWarningsAsErrors", "all")
                .option("compiler.MaximumGraalGraphSize", "10000")
                .build();
        var plan = JsonPlan.compile(schema);
        var first =
            plan.decode("{\"n\":1000,\"d\":1.5,\"b\":true}".getBytes(StandardCharsets.UTF_8));
        var second =
            plan.decode("{\"n\":2000,\"d\":2.5,\"b\":false}".getBytes(StandardCharsets.UTF_8))) {
      context.initialize("json-test");
      context.enter();
      try {
        plan.close();
        var rows = new JsonRecord[] {first, second};
        var data = DataLibrary.factory().getUncached();
        try {
          check(
              data.readDouble(first, 1) == 1.5 && data.readBoolean(first, 2),
              "shared uncached primitives");
          try {
            data.readDouble(first, 0);
            throw new AssertionError("Shared wrong-type read");
          } catch (com.oracle.truffle.api.nodes.UnexpectedResultException expected) {
            check(expected.getResult().equals(1000L), "shared rejected value preserved");
          }
        } catch (com.oracle.truffle.api.nodes.UnexpectedResultException failure) {
          throw new AssertionError(failure);
        }
        var dynamic = new Reads(null, schema, false, false);
        var constant = new Reads(rows, schema, false, false);
        var specialized = new Reads(null, schema, true, false);
        var specializedConstant = new Reads(rows, schema, true, false);
        var shared = new Reads(null, schema, false, true);
        var sharedConstant = new Reads(rows, schema, false, true);
        var roots =
            new Reads[] {
              dynamic, constant, specialized, specializedConstant, shared, sharedConstant
            };
        for (var root : roots) {
          var target = root.getCallTarget();
          for (int i = 0; i < 200 && !root.compiled; i++) target.call(rows, 100000);
          check(root.compiled, "JSON reads must execute compiled code");
          check(((Long) target.call(rows, 1000000)) == 1502000000L, "JSON compiled checksum");
          check(NodeUtil.countNodes(root) <= 192, "JSON AST budget");
          var bean =
              (com.sun.management.ThreadMXBean)
                  java.lang.management.ManagementFactory.getThreadMXBean();
          check(bean.isThreadAllocatedMemorySupported(), "JSON allocation counter required");
          bean.setThreadAllocatedMemoryEnabled(true);
          long thread = Thread.currentThread().threadId();
          long before = bean.getThreadAllocatedBytes(thread);
          target.call(rows, 1000000);
          double bytes = (bean.getThreadAllocatedBytes(thread) - before) / 1000000.0;
          check(bytes >= 0 && bytes <= 0.05, "JSON compiled bytes/read budget: " + bytes);
          System.out.println(
              "JSON compiled=true constant="
                  + (root.constant != null)
                  + " specialized="
                  + root.specialized
                  + " shared="
                  + root.shared
                  + " bytes/read="
                  + bytes);
        }
        first.close();
        for (var root : roots) {
          try {
            root.getCallTarget().call(rows, 1);
            throw new AssertionError("Compiled closed read");
          } catch (IllegalStateException expected) {
          }
        }
        check(second.longValue(0) == 2000, "Sibling survives owner close");
        // A single call site sees more schema identities than either bounded PIC can retain.
        var churn = new Reads(null, schema, false, true);
        var churnTarget = churn.getCallTarget();
        for (int i = 0; i < 8; i++) {
          var nextSchema =
              Schema.of(
                  Field.integer("n", (i & 1) == 0 ? 32 : 64, true, false, false),
                  new Field("d", Field.Kind.FLOAT64, false, false),
                  new Field("b", Field.Kind.BOOLEAN, false, false));
          try (var nextPlan = JsonPlan.compile(nextSchema);
              var next =
                  nextPlan.decode(
                      ("{\"n\":" + (1000 + i) + ",\"d\":1.5,\"b\":true}")
                          .getBytes(StandardCharsets.UTF_8))) {
            var nextRows = new JsonRecord[] {next, next};
            churn.compiled = false;
            for (int warmup = 0; warmup < 200 && !churn.compiled; warmup++)
              churnTarget.call(nextRows, 100000);
            check(churn.compiled, "Shared JSON schema churn remains compilable");
            check(
                (Long) churnTarget.call(nextRows, 1) == 1002 + i, "Shared JSON schema churn value");
          }
        }
        check(NodeUtil.countNodes(churn) <= 192, "Shared JSON schema cache remains bounded");
        generic();
        nested();
      } finally {
        context.leave();
      }
    }
  }

  /** Nested primitives read through constant JsonAccess paths, with no intermediate records. */
  private static void nested() {
    var inner =
        new Schema(
            "Inner",
            java.util.List.of(
                Field.requiredLong("n"),
                new Field("d", Field.Kind.FLOAT64, false, false),
                new Field("b", Field.Kind.BOOLEAN, false, false)));
    var mid =
        new Schema(
            "Mid",
            java.util.List.of(
                new Field(
                    "inner",
                    Field.Kind.RECORD,
                    false,
                    false,
                    dev.elide.truffledata.Constraints.NONE.withRecordType("Inner"))));
    var schema =
        Schema.of(
            Field.requiredLong("id"),
            new Field(
                "mid",
                Field.Kind.RECORD,
                false,
                false,
                dev.elide.truffledata.Constraints.NONE.withRecordType("Mid")));
    var types = java.util.Map.of("Inner", inner, "Mid", mid);
    try (var plan = JsonPlan.compile(schema, types);
        var first =
            plan.decode(
                "{\"id\":1,\"mid\":{\"inner\":{\"n\":1000,\"d\":1.5,\"b\":true}}}"
                    .getBytes(StandardCharsets.UTF_8));
        var second =
            plan.decode(
                "{\"mid\":{\"inner\":{\"b\":false,\"d\":2.5,\"n\":2000}},\"id\":2}"
                    .getBytes(StandardCharsets.UTF_8))) {
      var rows = new JsonRecord[] {first, second};
      // Constant accessors take the exploded walk; dynamic ones (loaded from a mutable array) the loop.
      var roots =
          new NestedReads[] {new NestedReads(schema, types, false), new NestedReads(schema, types, true)};
      for (var root : roots) {
        var target = root.getCallTarget();
        for (int i = 0; i < 200 && !root.compiled; i++) target.call(rows, 100000);
        check(root.compiled, "Nested JSON reads must execute compiled code");
        check(((Long) target.call(rows, 1000000)) == 1502000000L, "Nested JSON compiled checksum");
        check(NodeUtil.countNodes(root) <= 192, "Nested JSON AST budget");
        var bean =
            (com.sun.management.ThreadMXBean)
                java.lang.management.ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(thread);
        target.call(rows, 1000000);
        double bytes = (bean.getThreadAllocatedBytes(thread) - before) / 1000000.0;
        check(bytes >= 0 && bytes <= 0.05, "Nested JSON compiled bytes/read budget: " + bytes);
        System.out.println("JSON nested compiled=true dynamic=" + root.dynamic + " bytes/read=" + bytes);
      }
      var target = roots[1].getCallTarget();
      first.close();
      try {
        target.call(rows, 1);
        throw new AssertionError("Compiled closed nested read");
      } catch (IllegalStateException expected) {
      }
      check(second.record(1).record(0).longValue(0) == 2000, "Nested sibling survives close");
    }
  }

  private static final class NestedReads extends RootNode {
    final JsonAccess n, d, b;
    // Not compilation-final: loads from it are not partial-evaluation constants.
    private final JsonAccess[] paths;
    final boolean dynamic;
    boolean compiled;

    NestedReads(Schema schema, java.util.Map<String, Schema> types, boolean dynamic) {
      super(TestLanguage.current());
      n = new JsonAccess(schema, types, 1, 0, 0);
      d = new JsonAccess(schema, types, 1, 0, 1);
      b = new JsonAccess(schema, types, 1, 0, 2);
      paths = new JsonAccess[] {n, d, b};
      this.dynamic = dynamic;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      var rows = (JsonRecord[]) frame.getArguments()[0];
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int i = 0; i < count; i++) {
        var row = rows[i & 1];
        if (dynamic) {
          sum +=
              paths[0].longValue(row)
                  + (long) paths[1].doubleValue(row)
                  + (paths[2].booleanValue(row) ? 1 : 0);
        } else {
          sum += n.longValue(row) + (long) d.doubleValue(row) + (b.booleanValue(row) ? 1 : 0);
        }
      }
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }

  /** Retained generic values read through compiled code with an assumption-cached owner check. */
  private static void generic() {
    var first =
        dev.elide.truffledata.json.JsonSvm.decode(
            "{\"n\":1000,\"d\":1.5,\"b\":true}".getBytes(StandardCharsets.UTF_8));
    var second =
        dev.elide.truffledata.json.JsonSvm.decode(
            "{\"n\":2000,\"d\":2.5,\"b\":false}".getBytes(StandardCharsets.UTF_8));
    var messages = new dev.elide.truffledata.json.JsonMessage[] {first, second};
    var root = new GenericReads(messages);
    var target = root.getCallTarget();
    for (int i = 0; i < 200 && !root.compiled; i++) target.call(100000);
    check(root.compiled, "Generic JSON reads must execute compiled code");
    check(((Long) target.call(1000000)) == 1502000000L, "Generic JSON compiled checksum");
    check(NodeUtil.countNodes(root) <= 192, "Generic JSON AST budget");
    var bean =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    bean.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(thread);
    target.call(1000000);
    double bytes = (bean.getThreadAllocatedBytes(thread) - before) / 1000000.0;
    check(bytes >= 0 && bytes <= 0.05, "Generic JSON compiled bytes/read budget: " + bytes);
    System.out.println("JSON generic compiled=true bytes/read=" + bytes);
    first.close();
    try {
      target.call(1);
      throw new AssertionError("Compiled closed generic read");
    } catch (IllegalStateException expected) {
    }
    check(second.root().member("n").longValue() == 2000, "Sibling message survives close");
    interop(second);
    borrowed();
    export(second);
    second.close();
    CompiledArrayReads.run();
  }

  /** Guest-style reads through a cached InteropLibrary compile with the same budgets. */
  private static void interop(dev.elide.truffledata.json.JsonMessage message) {
    interop(message.root(), "Interop");
  }

  /**
   * The interop gate over a root borrowed from a JsonSession: same checksum, graph and allocation
   * budgets, and the compiled read fails once the borrow ends.
   */
  private static void borrowed() {
    byte[] input = "{\"n\":2000,\"d\":2.5,\"b\":false}".getBytes(StandardCharsets.UTF_8);
    var compiled = new InteropReads[1];
    try (var session = dev.elide.truffledata.json.JsonSession.open()) {
      session.with(
          input,
          root -> {
            compiled[0] = interop(root, "Borrowed interop");
            return Boolean.TRUE;
          });
      try {
        compiled[0].getCallTarget().call(1);
        throw new AssertionError("Compiled read after the borrow ended");
      } catch (IllegalStateException expected) {
      }
      check(
          session.with(input, root -> root.member("n").longValue()) == 2000,
          "Session reusable after borrowed compiled reads");
    }
  }

  private static InteropReads interop(Object value, String label) {
    var root = new InteropReads(value);
    var target = root.getCallTarget();
    for (int i = 0; i < 200 && !root.compiled; i++) target.call(100000);
    check(root.compiled, label + " JSON reads must execute compiled code");
    check(((Long) target.call(1000000)) == 2002000000L, label + " JSON compiled checksum");
    check(NodeUtil.countNodes(root) <= 192, label + " JSON AST budget");
    var bean =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    bean.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(thread);
    target.call(1000000);
    double bytes = (bean.getThreadAllocatedBytes(thread) - before) / 1000000.0;
    check(bytes >= 0 && bytes <= 0.05, label + " JSON compiled bytes/read budget: " + bytes);
    System.out.println("JSON " + label.toLowerCase(java.util.Locale.ROOT) + " compiled=true bytes/read=" + bytes);
    return root;
  }

  /** Single-pass export into a constant, non-materializing sink: the walk itself must not allocate. */
  private static void export(dev.elide.truffledata.json.JsonMessage message) {
    var root = new ExportReads(message.root());
    var target = root.getCallTarget();
    for (int i = 0; i < 200 && !root.compiled; i++) target.call(100000);
    check(root.compiled, "Export JSON loop must execute compiled code");
    // {"n":2000,"d":2.5,"b":false}: 2000 + 2 + 0 values, 3 keys at 1000 each, one object at 1.
    check(((Long) target.call(1000000)) == 5003000000L, "Export JSON compiled checksum");
    check(NodeUtil.countNodes(root) <= 192, "Export JSON AST budget");
    var bean =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    bean.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(thread);
    target.call(1000000);
    double bytes = (bean.getThreadAllocatedBytes(thread) - before) / 1000000.0;
    check(bytes >= 0 && bytes <= 0.05, "Export JSON compiled bytes/export budget: " + bytes);
    System.out.println("JSON export compiled=true bytes/export=" + bytes + " depth=" + root.export.maxDepthSeen());
    check(root.export.maxDepthSeen() == 1, "Export JSON stack depth");
  }

  private static final class SummingSink implements dev.elide.truffledata.json.JsonSink {
    long sum;

    public void beginObject(long members) {
      sum += 1;
    }

    public void key(dev.elide.truffledata.json.JsonText key) {
      sum += 1000;
    }

    public void beginArray(long elements) {
      sum += 1;
    }

    public void end() {}

    public void nullValue() {}

    public void booleanValue(boolean value) {
      sum += value ? 1 : 0;
    }

    public void longValue(long value) {
      sum += value;
    }

    public void doubleValue(double value) {
      sum += (long) value;
    }

    public void unsignedValue(long bits) {
      sum += bits;
    }

    public void decimalValue(dev.elide.truffledata.json.JsonText token) {
      sum += 7;
    }

    public void stringValue(dev.elide.truffledata.json.JsonText text) {
      sum += 11;
    }
  }

  private static final class ExportReads extends RootNode {
    private final dev.elide.truffledata.json.JsonValue value;
    private final SummingSink sink = new SummingSink();
    @Child dev.elide.truffledata.json.JsonExportNode export =
        dev.elide.truffledata.json.JsonExportNode.create();
    boolean compiled;

    ExportReads(dev.elide.truffledata.json.JsonValue value) {
      super(TestLanguage.current());
      this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      int count = (Integer) frame.getArguments()[0];
      sink.sum = 0;
      for (int i = 0; i < count; i++) export.execute(value, sink);
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sink.sum;
    }
  }

  private static final class InteropReads extends RootNode {
    private final Object value;
    @Child private com.oracle.truffle.api.interop.InteropLibrary objects =
        com.oracle.truffle.api.interop.InteropLibrary.getFactory().createDispatched(3);
    @Child private com.oracle.truffle.api.interop.InteropLibrary numbers =
        com.oracle.truffle.api.interop.InteropLibrary.getFactory().createDispatched(3);
    boolean compiled;

    InteropReads(Object value) {
      super(TestLanguage.current());
      this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      int count = (Integer) frame.getArguments()[0];
      long sum = 0;
      try {
        for (int i = 0; i < count; i++) {
          Object n = objects.readMember(value, "n");
          Object d = objects.readMember(value, "d");
          Object b = objects.readMember(value, "b");
          sum +=
              numbers.asLong(n)
                  + (long) numbers.asDouble(d)
                  + (numbers.asBoolean(b) ? 1 : 0)
                  + (objects.isNull(value) ? 1 : 0);
        }
      } catch (com.oracle.truffle.api.interop.InteropException failure) {
        throw CompilerDirectives.shouldNotReachHere(failure);
      }
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }

  private static final class GenericReads extends RootNode {
    @CompilerDirectives.CompilationFinal(dimensions = 1)
    private final dev.elide.truffledata.json.JsonOwner[] owners;

    @CompilerDirectives.CompilationFinal(dimensions = 1)
    private final dev.elide.truffledata.json.JsonValue[] n, d, b;

    @Child private dev.elide.truffledata.json.JsonOwner.CheckOpenNode check =
        dev.elide.truffledata.json.JsonOwner.CheckOpenNode.create();

    boolean compiled;

    GenericReads(dev.elide.truffledata.json.JsonMessage[] messages) {
      super(TestLanguage.current());
      owners = messages;
      n = new dev.elide.truffledata.json.JsonValue[messages.length];
      d = new dev.elide.truffledata.json.JsonValue[messages.length];
      b = new dev.elide.truffledata.json.JsonValue[messages.length];
      for (int i = 0; i < messages.length; i++) {
        var root = messages[i].root();
        n[i] = root.member("n");
        d[i] = root.member("d");
        b[i] = root.member("b");
      }
    }

    @Override
    public Object execute(VirtualFrame frame) {
      int count = (Integer) frame.getArguments()[0];
      long sum = 0;
      for (int i = 0; i < count; i++) {
        int row = i & 1;
        check.execute(owners[row]);
        sum += n[row].longValue() + (long) d[row].doubleValue() + (b[row].booleanValue() ? 1 : 0);
      }
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }

  private static final class Reads extends RootNode {
    @CompilerDirectives.CompilationFinal(dimensions = 1)
    final JsonRecord @org.jspecify.annotations.Nullable [] constant;

    boolean compiled;
    final boolean specialized, shared;
    @Child private ReadLongNode sharedLong = ReadLongNode.create();
    @Child private ReadDoubleNode sharedDouble = ReadDoubleNode.create();
    @Child private ReadBooleanNode sharedBoolean = ReadBooleanNode.create();
    final JsonAccess n, d, b;

    Reads(
        JsonRecord @org.jspecify.annotations.Nullable [] constant,
        Schema schema,
        boolean specialized,
        boolean shared) {
      super(TestLanguage.current());
      this.constant = constant;
      this.specialized = specialized;
      this.shared = shared;
      n = new JsonAccess(schema, 0);
      d = new JsonAccess(schema, 1);
      b = new JsonAccess(schema, 2);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      var rows = constant == null ? (JsonRecord[]) frame.getArguments()[0] : constant;
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int i = 0; i < count; i++) {
        var row = rows[i & 1];
        sum +=
            shared
                ? sharedLong.execute(row, 0)
                    + (long) sharedDouble.execute(row, 1)
                    + (sharedBoolean.execute(row, 2) ? 1 : 0)
                : specialized
                    ? n.longValue(row) + (long) d.doubleValue(row) + (b.booleanValue(row) ? 1 : 0)
                    : row.longValue(0) + (long) row.doubleValue(1) + (row.booleanValue(2) ? 1 : 0);
      }
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }
}
