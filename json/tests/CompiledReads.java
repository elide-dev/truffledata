package dev.elide.truffledata.json.tests;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.json.JsonScalarAccess;
import dev.elide.truffledata.json.JsonScalarPlan;
import dev.elide.truffledata.json.JsonScalarRecord;
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
        var plan = JsonScalarPlan.compile(schema);
        var first =
            plan.decode("{\"n\":1000,\"d\":1.5,\"b\":true}".getBytes(StandardCharsets.UTF_8));
        var second =
            plan.decode("{\"n\":2000,\"d\":2.5,\"b\":false}".getBytes(StandardCharsets.UTF_8))) {
      context.initialize("json-test");
      context.enter();
      try {
        plan.close();
        var rows = new JsonScalarRecord[] {first, second};
        var dynamic = new Reads(null, schema, false);
        var constant = new Reads(rows, schema, false);
        var specialized = new Reads(null, schema, true);
        var specializedConstant = new Reads(rows, schema, true);
        for (var root : new Reads[] {dynamic, constant, specialized, specializedConstant}) {
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
                  + " bytes/read="
                  + bytes);
        }
        first.close();
        for (var root : new Reads[] {dynamic, constant, specialized, specializedConstant}) {
          try {
            root.getCallTarget().call(rows, 1);
            throw new AssertionError("Compiled closed read");
          } catch (IllegalStateException expected) {
          }
        }
        check(second.longValue(0) == 2000, "Sibling survives owner close");
      } finally {
        context.leave();
      }
    }
  }

  private static final class Reads extends RootNode {
    @CompilerDirectives.CompilationFinal(dimensions = 1)
    final JsonScalarRecord @org.jspecify.annotations.Nullable [] constant;

    boolean compiled;
    final boolean specialized;
    final JsonScalarAccess n, d, b;

    Reads(
        JsonScalarRecord @org.jspecify.annotations.Nullable [] constant,
        Schema schema,
        boolean specialized) {
      super(TestLanguage.current());
      this.constant = constant;
      this.specialized = specialized;
      n = new JsonScalarAccess(schema, 0);
      d = new JsonScalarAccess(schema, 1);
      b = new JsonScalarAccess(schema, 2);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      var rows = constant == null ? (JsonScalarRecord[]) frame.getArguments()[0] : constant;
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int i = 0; i < count; i++) {
        var row = rows[i & 1];
        sum +=
            specialized
                ? n.longValue(row) + (long) d.doubleValue(row) + (b.booleanValue(row) ? 1 : 0)
                : row.longValue(0) + (long) row.doubleValue(1) + (row.booleanValue(2) ? 1 : 0);
      }
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }
}
