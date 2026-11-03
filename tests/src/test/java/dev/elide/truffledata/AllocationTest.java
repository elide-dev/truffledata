package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.*;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * Measures retained primitive reads, including dispatch. Ingestion and row creation are separate.
 */
class AllocationTest {
  @Test
  void primitiveReadsStayWithinAllocationBudget() {
    var row =
        Batch.copyOf(
                Schema.of(Field.requiredLong("a")),
                Collections.singletonList(new Object[] {1000L}),
                Limits.DEFAULT)
            .row(0);
    check("batch", row);
    DynamicObject object = new DynamicObject(Shape.newBuilder().build()) {};
    DynamicObject.PutNode.getUncached().execute(object, "a", 1000L);
    check("dynamic", new DynamicRecord(object, Schema.of(Field.requiredLong("a"))));
    try (Context context = Context.newBuilder("td-test").allowExperimentalOptions(true).build()) {
      context.initialize("td-test");
      context.enter();
      try {
        check("static", TestLanguage.current().layout().create(1000L));
      } finally {
        context.leave();
      }
    }
  }

  private static volatile Object retained = DataNull.INSTANCE;

  @Test
  void ingestionAndRowAllocationBudgets() {
    Schema schema = Schema.of(Field.requiredLong("a"));
    List<Object[]> inputs = new ArrayList<>();
    for (int i = 0; i < 512; i++) inputs.add(new Object[] {1000L + i});
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    bean.setThreadAllocatedMemoryEnabled(true);
    for (int i = 0; i < 1000; i++) retained = Batch.copyOf(schema, inputs, Limits.DEFAULT);
    long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
    for (int i = 0; i < 1000; i++) retained = Batch.copyOf(schema, inputs, Limits.DEFAULT);
    double perCell =
        (bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before) / 512000.0;
    System.out.printf("ALLOCATION ingestion %.3f bytes/cell%n", perCell);
    assertTrue(perCell <= 64.0, "Batch ingestion allocation budget exceeded: " + perCell);
    Batch batch = (Batch) retained;
    before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
    for (int i = 0; i < 100000; i++) retained = batch.row(i & 511);
    double perRow =
        (bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before) / 100000.0;
    System.out.printf("ALLOCATION row-view %.3f bytes/row%n", perRow);
    assertTrue(perRow <= 48.0, "Retained row allocation budget exceeded: " + perRow);
  }

  private static void check(String label, Object row) {
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    assertTrue(
        bean.isThreadAllocatedMemorySupported(),
        "Allocation counters required for this acceptance check");
    bean.setThreadAllocatedMemoryEnabled(true);
    LoopRoot root = new LoopRoot();
    var target = root.getCallTarget();
    for (int i = 0; i < 20; i++) target.call(row, 100000);
    long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
    long result = (Long) target.call(row, 1000000);
    long bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
    assertEquals(1000000000L, result);
    double perRead = bytes / 1000000.0;
    System.out.printf("ALLOCATION %s %.6f bytes/read compiled=%s%n", label, perRead, root.compiled);
    assertTrue(perRead <= 0.05, label + " allocation budget exceeded: " + perRead + " bytes/read");
    if (Boolean.getBoolean("truffledata.requireCompiled"))
      assertTrue(root.compiled, "Compiled execution required: " + label);
  }

  private static final class LoopRoot extends RootNode {
    @Child private ReadLongNode read = ReadLongNode.create();
    boolean compiled;

    LoopRoot() {
      super(null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object row = frame.getArguments()[0];
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int i = 0; i < count; i++) sum += read.execute(row, 0);
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }
}
