package dev.elide.truffledata.yaml.tests;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TapeValue;
import dev.elide.truffledata.tape.TypedAccess;
import dev.elide.truffledata.tape.TypedRecord;
import dev.elide.truffledata.yaml.YamlSession;
import java.util.Map;
import org.graalvm.polyglot.Context;

/** Required compiled execution and allocation gates for generic YAML and alias-resolving reads. */
final class CompiledReads {
  private CompiledReads() {}

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  static void run() {
    try (YamlSession session = YamlSession.open()) {
      run(session);
    }
  }

  static void run(YamlSession session) {
    try (Context context =
            Context.newBuilder("yaml-test")
                .allowExperimentalOptions(true)
                .option("engine.BackgroundCompilation", "false")
                .option("engine.CompilationFailureAction", "Throw")
                .option("compiler.TreatPerformanceWarningsAsErrors", "all")
                .option("compiler.MaximumGraalGraphSize", "10000")
                .build();
        var first = session.decode(SvmAcceptance.bytes("a: &a {n: 1000}\nb: *a\n"));
        var second = session.decode(SvmAcceptance.bytes("a: &a {n: 2000}\nb: *a\n"))) {
      context.initialize("yaml-test");
      context.enter();
      try {
        typed(session);
        TapeValue value = first.root();
        Reads[] roots = {new Reads(value, false), new Reads(value, true)};
        for (Reads root : roots) {
          var target = root.getCallTarget();
          for (int i = 0; i < 200 && !root.compiled; i++) target.call(100000);
          check(root.compiled, "YAML reads must execute compiled code");
          check(((Long) target.call(1000000)) == 1000000000L, "YAML compiled checksum");
          check(NodeUtil.countNodes(root) <= 192, "YAML AST budget");
          var bean =
              (com.sun.management.ThreadMXBean)
                  java.lang.management.ManagementFactory.getThreadMXBean();
          check(bean.isThreadAllocatedMemorySupported(), "YAML allocation counter required");
          bean.setThreadAllocatedMemoryEnabled(true);
          long thread = Thread.currentThread().threadId();
          long before = bean.getThreadAllocatedBytes(thread);
          target.call(1000000);
          double bytes = (bean.getThreadAllocatedBytes(thread) - before) / 1000000.0;
          check(bytes >= 0 && bytes <= 0.05, "YAML compiled bytes/read budget: " + bytes);
          System.out.println("YAML compiled=true aliases=" + root.aliases + " bytes/read=" + bytes);
        }
        export(first.root());
        session.with(SvmAcceptance.bytes("a: &a {n: 1000}\nb: *a\n"), borrowed -> {
          export(borrowed); return 0;
        });
        first.close();
        for (Reads root : roots) {
          try {
            root.getCallTarget().call(1);
            throw new AssertionError("Compiled closed YAML read");
          } catch (IllegalStateException expected) {
          }
        }
        check(
            second.root().member("b").member("n").longValue() == 2000,
            "YAML sibling survives close");
      } finally {
        context.leave();
      }
    }
  }

  private static void typed(YamlSession session) {
    Schema schema = Schema.of(Field.requiredLong("n"));
    try (var plan = session.compile(schema, Map.of());
        var row = plan.decode(SvmAcceptance.bytes("n: 1000"))) {
      TypedReads root = new TypedReads(row, new TypedAccess(schema, 0));
      var target = root.getCallTarget();
      for (int i = 0; i < 200 && !root.compiled; i++) target.call(100000);
      check(root.compiled, "YAML typed reads must execute compiled code");
      check(((Long) target.call(1000000)) == 1000000000L, "YAML typed checksum");
      check(NodeUtil.countNodes(root) <= 192, "YAML typed AST budget");
      var bean =
          (com.sun.management.ThreadMXBean)
              java.lang.management.ManagementFactory.getThreadMXBean();
      check(bean.isThreadAllocatedMemorySupported(), "YAML allocation counter required");
      bean.setThreadAllocatedMemoryEnabled(true);
      long thread = Thread.currentThread().threadId();
      long before = bean.getThreadAllocatedBytes(thread);
      target.call(1000000);
      double bytes = (bean.getThreadAllocatedBytes(thread) - before) / 1000000.0;
      check(bytes >= 0 && bytes <= 0.05, "YAML typed bytes/read budget: " + bytes);
      System.out.println("YAML compiled=true typed=true bytes/read=" + bytes);
      row.close();
      try {
        target.call(1);
        throw new AssertionError("Compiled closed typed YAML read");
      } catch (IllegalStateException expected) {
      }
    }
  }

  private static void export(TapeValue value) {
    ExportReads root = new ExportReads(value);
    var target = root.getCallTarget();
    for (int i = 0; i < 200 && !root.compiled; i++) target.call(10000);
    check(root.compiled, "YAML export must execute compiled code");
    check(((Long) target.call(100000)) == 600300000L, "YAML export checksum");
    check(NodeUtil.countNodes(root) <= 192, "YAML export AST budget");
    var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    bean.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(thread);
    target.call(100000);
    double bytes = (bean.getThreadAllocatedBytes(thread) - before) / 100000.0;
    check(bytes >= 0 && bytes <= 1.0, "YAML export bytes/document budget: " + bytes);
    check(root.export.maxDepthSeen() == 2, "YAML export depth");
    System.out.println("YAML compiled=true export=true bytes/document=" + bytes);
  }

  private static final class CountingSink implements dev.elide.truffledata.tape.TapeSink {
    long sum;
    public void beginObject(long count) { sum++; }
    public void beginArray(long count) { sum++; }
    public void key(dev.elide.truffledata.tape.TapeText key) { sum += 1000; }
    public void end() {}
    public void nullValue() { sum++; }
    public void booleanValue(boolean value) { sum += value ? 1 : 0; }
    public void longValue(long value) { sum += value; }
    public void unsignedValue(long value) { sum += value; }
    public void doubleValue(double value) { sum += (long) value; }
    public void decimalValue(dev.elide.truffledata.tape.TapeText value) { sum++; }
    public void stringValue(dev.elide.truffledata.tape.TapeText value) { sum++; }
  }

  private static final class ExportReads extends RootNode {
    private final TapeValue value;
    private final CountingSink sink = new CountingSink();
    @Child private dev.elide.truffledata.tape.TapeExportNode export =
        dev.elide.truffledata.tape.TapeExportNode.create();
    boolean compiled;
    ExportReads(TapeValue value) { super(TestLanguage.current()); this.value = value; }
    @Override public Object execute(VirtualFrame frame) {
      int count = (int) frame.getArguments()[0]; sink.sum = 0;
      for (int i = 0; i < count; i++) export.execute(value, sink);
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sink.sum;
    }
  }

  private static final class TypedReads extends RootNode {
    private final TypedRecord row;
    private final TypedAccess access;
    boolean compiled;

    TypedReads(TypedRecord row, TypedAccess access) {
      super(TestLanguage.current());
      this.row = row;
      this.access = access;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      int count = (int) frame.getArguments()[0];
      long sum = 0;
      for (int i = 0; i < count; i++) {
        sum += access.longValue(row);
        if (CompilerDirectives.inCompiledCode()) compiled = true;
      }
      LoopNode.reportLoopCount(this, count);
      return sum;
    }
  }

  private static final class Reads extends RootNode {
    @Child private InteropLibrary objects = InteropLibrary.getFactory().createDispatched(3);
    @Child private InteropLibrary children = InteropLibrary.getFactory().createDispatched(3);
    @Child private InteropLibrary numbers = InteropLibrary.getFactory().createDispatched(3);
    private final boolean aliases;
    private final TapeValue value;
    boolean compiled;

    Reads(TapeValue value, boolean aliases) {
      super(TestLanguage.current());
      this.value = value;
      this.aliases = aliases;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      int count = (int) frame.getArguments()[0];
      long sum = 0;
      try {
        for (int i = 0; i < count; i++) {
          Object child = objects.readMember(value, aliases ? "b" : "a");
          sum += numbers.asLong(children.readMember(child, "n"));
          if (CompilerDirectives.inCompiledCode()) compiled = true;
        }
      } catch (InteropException failure) {
        throw new AssertionError(failure);
      }
      LoopNode.reportLoopCount(this, count);
      return sum;
    }
  }
}
