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
  void nestedAccessAndDynamicMutationRemainCorrectAfterCompilation() {
    try (Context context = Context.newBuilder("td-test").allowExperimentalOptions(true).build()) {
      context.initialize("td-test");
      context.enter();
      try {
        DynamicObject object = new DynamicObject(Shape.newBuilder().build()) {};
        var put = DynamicObject.PutNode.getUncached();
        put.execute(object, "value", 1000L);
        DynamicRecord leaf =
            new DynamicRecord(object, Schema.of(Field.integer("value", 16, true, false, false)));
        var layout =
            new StaticLayout(
                TestLanguage.current().language(),
                Schema.of(new Field("child", Field.Kind.RECORD, false, false)),
                Limits.DEFAULT);
        Object outer = layout.create(leaf);
        NestedRoot root = new NestedRoot();
        var target = root.getCallTarget();
        for (int i = 0; i < 30; i++) assertEquals(100000000L, target.call(outer, 100000));
        if (Boolean.getBoolean("truffledata.requireCompiled")) assertTrue(root.compiled);
        put.execute(object, "extra", true);
        put.execute(object, "value", 2000L);
        assertEquals(200000000L, target.call(outer, 100000));
        put.execute(object, "value", 40000L);
        assertThrows(ContractViolation.class, () -> target.call(outer, 1));
        put.execute(object, "value", DataNull.INSTANCE);
        assertThrows(ContractViolation.class, () -> target.call(outer, 1));
        put.execute(object, "value", 3.5);
        assertThrows(ContractViolation.class, () -> target.call(outer, 1));
        DynamicObject.RemoveKeyNode.getUncached().execute(object, "value");
        assertThrows(ContractViolation.class, () -> target.call(outer, 1));
        put.execute(object, "value", 3000L);
        root.compiled = false;
        for (int i = 0; i < 30; i++) target.call(outer, 100000);
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        assertEquals(3000000000L, target.call(outer, 1000000));
        double bytes =
            (bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before) / 1000000.0;
        System.out.printf(
            "ALLOCATION nested-mutation %.6f bytes/read compiled=%s%n", bytes, root.compiled);
        assertTrue(bytes <= 0.05, "Nested retained access allocation budget exceeded");
        if (Boolean.getBoolean("truffledata.requireCompiled")) assertTrue(root.compiled);
        assertTrue(com.oracle.truffle.api.nodes.NodeUtil.countNodes(root) <= 256);
      } finally {
        context.leave();
      }
    }
  }

  @Test
  void nativeProtobufReadsAndBinaryIngestionStayWithinAllocationBudgets() {
    try (Context context = Context.newBuilder("td-test").allowExperimentalOptions(true).build()) {
      context.initialize("td-test");
      context.enter();
      try {
        for (boolean useStatic : new boolean[] {false, true}) {
          var plan =
              dev.elide.truffledata.generated.TruffleDataContracts.create(
                      useStatic ? TestLanguage.current().language() : null,
                      Limits.DEFAULT,
                      16,
                      1024 * 1024)
                  .type("truffledata.lossless.Sample");
          check(
              "protobuf-" + (useStatic ? "static" : "batch"),
              plan.newBuilder().set(0, 1000L).build());
          byte[] wire =
              plan.encode(plan.newBuilder().set(2, DataArray.copyOfBytes(new byte[65536])).build());
          ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
          bean.setThreadAllocatedMemoryEnabled(true);
          for (int i = 0; i < 100; i++) retained = plan.decode(wire);
          long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
          for (int i = 0; i < 100; i++) retained = plan.decode(wire);
          double bytes =
              (bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before) / 100.0;
          System.out.printf(
              "ALLOCATION protobuf-wire static=%s %.3f bytes/message payload=%d%n",
              useStatic, bytes, wire.length);
          assertTrue(
              bytes <= wire.length + 16384,
              "Binary ingestion must not expand or repeatedly copy byte payloads");
        }
      } finally {
        context.leave();
      }
    }
  }

  private static final class NestedRoot extends RootNode {
    @Child private DataLibrary outer = DataLibrary.factory().createDispatched(3);
    @Child private ReadLongNode leaf = ReadLongNode.create();
    boolean compiled;

    NestedRoot() {
      super(null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object row = frame.getArguments()[0];
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int i = 0; i < count; i++) sum += leaf.execute(outer.read(row, 0), 0);
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }

  @Test
  void mixedRepresentationsSaturateCachesWithoutUnboundedGrowth() {
    Object[] rows = new Object[8];
    try (var engine =
        org.graalvm.polyglot.Engine.newBuilder().allowExperimentalOptions(true).build()) {
      // Independent contexts deliberately use distinct schema/layout identities with equal names.
      for (int contextIndex = 0; contextIndex < 2; contextIndex++) {
        try (Context context = Context.newBuilder("td-test").engine(engine).build()) {
          context.initialize("td-test");
          context.enter();
          try {
            for (int j = 0; j < 4; j++) {
              int index = contextIndex * 4 + j;
              Schema schema = Schema.of(Field.requiredLong("n"));
              long value = 1000L + index;
              if (j == 0 || j == 3) {
                rows[index] =
                    new StaticLayout(TestLanguage.current().language(), schema, Limits.DEFAULT)
                        .create(value);
              } else if (j == 1) {
                rows[index] =
                    Batch.copyOf(
                            schema, Collections.singletonList(new Object[] {value}), Limits.DEFAULT)
                        .row(0);
              } else {
                DynamicObject object = new DynamicObject(Shape.newBuilder().build()) {};
                DynamicObject.PutNode.getUncached().execute(object, "n", value);
                rows[index] = new DynamicRecord(object, schema);
              }
            }
          } finally {
            context.leave();
          }
        }
      }
      try (Context consumer = Context.newBuilder("td-test").engine(engine).build()) {
        consumer.initialize("td-test");
        consumer.enter();
        try {
          MatrixRoot root = new MatrixRoot();
          var target = root.getCallTarget();
          ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
          bean.setThreadAllocatedMemoryEnabled(true);
          for (int width : new int[] {1, 2, 4, 8}) {
            Object[] active = Arrays.copyOf(rows, width);
            long expected = (1000L * width + (long) width * (width - 1) / 2) * (1000000 / width);
            root.compiled = false;
            for (int i = 0; i < 30; i++) target.call(active, 100000);
            long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
            assertEquals(expected, target.call(active, 1000000));
            double allocated =
                (bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before)
                    / 1000000.0;
            int nodes = com.oracle.truffle.api.nodes.NodeUtil.countNodes(root);
            System.out.printf(
                "ALLOCATION mixed-width-%d %.6f bytes/read nodes=%d compiled=%s%n",
                width, allocated, nodes, root.compiled);
            assertTrue(
                allocated <= (width <= 4 ? 0.05 : 16), "Mixed dispatch allocation budget exceeded");
            assertTrue(nodes <= 256, "Mixed representation AST budget exceeded");
            if (Boolean.getBoolean("truffledata.requireCompiled")) assertTrue(root.compiled);
          }
        } finally {
          consumer.leave();
        }
      }
    }
  }

  private static final class MatrixRoot extends RootNode {
    @Child private ReadLongNode read = ReadLongNode.create();
    boolean compiled;

    MatrixRoot() {
      super(null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object[] rows = (Object[]) frame.getArguments()[0];
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int i = 0; i < count; i++) sum += read.execute(rows[i & (rows.length - 1)], 0);
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }

  @Test
  void nullableStaticPresentReadsStayPrimitiveAfterNullAndMissing() {
    try (Context context = Context.newBuilder("td-test").allowExperimentalOptions(true).build()) {
      context.initialize("td-test");
      context.enter();
      try {
        Schema schema = Schema.of(Field.integer("n", 32, true, true, true));
        StaticLayout layout =
            new StaticLayout(TestLanguage.current().language(), schema, Limits.DEFAULT);
        Object row = layout.create(1000L);
        check("nullable-static", row);
        LoopRoot root = new LoopRoot();
        var target = root.getCallTarget();
        for (int i = 0; i < 20; i++) assertEquals(100000000L, target.call(row, 100000));
        assertThrows(
            ContractViolation.class, () -> target.call(layout.create(DataNull.INSTANCE), 1));
        assertThrows(
            ContractViolation.class, () -> target.call(layout.create(Missing.INSTANCE), 1));
        assertEquals(1000000L, target.call(row, 1000));
      } finally {
        context.leave();
      }
    }
  }

  @Test
  void directColumnIngestionAvoidsIntermediateRowArrays() {
    Schema schema = Schema.of(Field.requiredLong("n"));
    var source =
        new Batch.Source<RuntimeException>() {
          final Object value = 1000L;

          public boolean next() {
            return true;
          }

          public Object read(int column) {
            return value;
          }
        };
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    bean.setThreadAllocatedMemoryEnabled(true);
    for (boolean cumulative : new boolean[] {false, true}) {
      var parent = cumulative ? new PayloadBudget(new Limits(1, 1, Long.MAX_VALUE, 0)) : null;
      for (int i = 0; i < 2000; i++)
        retained = Batch.read(schema, 1024, Limits.DEFAULT, source, parent);
      long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
      for (int i = 0; i < 1000; i++)
        retained = Batch.read(schema, 1024, Limits.DEFAULT, source, parent);
      double perBatch =
          (bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before) / 1000.0;
      System.out.printf(
          "ALLOCATION direct-column batch %.3f bytes/1024 longs cumulative=%s%n",
          perBatch, cumulative);
      assertTrue(
          perBatch <= 9216, "Direct ingestion allocated intermediate rows or copied columns");
    }
  }

  @Test
  void scalarJdbcDecodingDoesNotAllocatePerCellBudgets() throws Exception {
    try (var connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:");
        var statement = connection.prepareStatement("select cast(1000 as bigint)");
        var result = statement.executeQuery()) {
      assertTrue(result.next());
      var decoder =
          dev.elide.truffledata.jdbc.JdbcValues.decoder(
              Schema.of(Field.requiredLong("n")), Limits.DEFAULT);
      var baseline = dev.elide.truffledata.jdbc.JdbcSource.DEFAULT_DECODER;
      ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
      bean.setThreadAllocatedMemoryEnabled(true);
      // Both implementations use the same call site through warmup and measurement. A real
      // driver avoids measuring proxy argument-array elimination and return-value boxing.
      for (int warmup = 0; warmup < 20; warmup++) {
        scalarBytes(baseline, result, bean);
        scalarBytes(decoder, result, bean);
      }
      long baselineBytes = scalarBytes(baseline, result, bean);
      long decodedBytes = scalarBytes(decoder, result, bean);
      double extra = (decodedBytes - baselineBytes) / 100000.0;
      System.out.printf("ALLOCATION JDBC scalar %.6f extra bytes/cell%n", extra);
      assertTrue(extra <= 8, "Scalar conversion allocated a per-cell budget");
    }
  }

  private static long scalarBytes(
      dev.elide.truffledata.jdbc.JdbcSource.Decoder decoder,
      java.sql.ResultSet result,
      ThreadMXBean bean)
      throws java.sql.SQLException {
    long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
    for (int i = 0; i < 100000; i++) retained = Objects.requireNonNull(decoder.read(result, 1));
    return bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
  }

  @Test
  void binarySnapshotsAllocateOneCompactPayload() {
    byte[] input = new byte[65536];
    Arrays.fill(input, (byte) 255);
    Field field =
        new Field("binary", Field.Kind.ARRAY, false, false)
            .withConstraints(
                Constraints.NONE.withElement(Field.integer("byte", 8, false, false, false)));
    for (int i = 0; i < 1000; i++) retained = Values.validate(field, DataArray.copyOfBytes(input));
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    bean.setThreadAllocatedMemoryEnabled(true);
    long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
    for (int i = 0; i < 1000; i++) retained = Values.validate(field, DataArray.copyOfBytes(input));
    double perSnapshot =
        (bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before) / 1000.0;
    System.out.printf("ALLOCATION binary-snapshot %.3f bytes/65536-byte payload%n", perSnapshot);
    assertTrue(perSnapshot <= input.length + 128, "Binary snapshot expanded or copied twice");
  }

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

  @Test
  void narrowTypedReadsAndExactLengthCompileWithoutAllocation() {
    try (Context context = Context.newBuilder("td-test").allowExperimentalOptions(true).build()) {
      context.initialize("td-test");
      context.enter();
      try {
        Schema schema =
            Schema.of(
                Field.integer("n", 16, true, false, false),
                new Field(
                    "f", Field.Kind.FLOAT64, false, false, Constraints.NONE.withFloatBits(32)),
                new Field("b", Field.Kind.BOOLEAN, false, false),
                new Field(
                    "s",
                    Field.Kind.STRING,
                    false,
                    false,
                    Constraints.NONE.withLength(
                        new Constraints.Length(3, 3, Constraints.LengthUnit.UTF16))));
        StaticLayout layout =
            new StaticLayout(TestLanguage.current().language(), schema, Limits.DEFAULT);
        var row = layout.create(1000L, 1.5, true, "abc");
        MixedRoot root = new MixedRoot();
        var target = root.getCallTarget();
        for (int i = 0; i < 20; i++) target.call(row, 100000);
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        long sum = (Long) target.call(row, 1000000);
        double perRead =
            (bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before) / 4000000.0;
        assertEquals(1005000000L, sum);
        System.out.printf(
            "ALLOCATION static-typed %.6f bytes/read compiled=%s%n", perRead, root.compiled);
        assertTrue(perRead <= 0.05, "Typed static read allocation budget exceeded");
        assertTrue(
            com.oracle.truffle.api.nodes.NodeUtil.countNodes(root) <= 256,
            "Mixed PIC node budget exceeded");
        if (Boolean.getBoolean("truffledata.requireCompiled")) assertTrue(root.compiled);
        Object[] input = {1000L, 1.5, true, "abc"};
        for (int i = 0; i < 10000; i++) retained = layout.create(input);
        before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        for (int i = 0; i < 10000; i++) retained = layout.create(input);
        double perRow =
            (bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before) / 10000.0;
        System.out.printf("ALLOCATION static-ingestion %.3f bytes/row%n", perRow);
        assertTrue(perRow <= 256, "Static ingestion allocation budget exceeded");
        StaticLayout.Source<RuntimeException> source = column -> input[column];
        for (int i = 0; i < 10000; i++) retained = layout.read(source, null);
        before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        for (int i = 0; i < 10000; i++) retained = layout.read(source, null);
        double directPerRow =
            (bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before) / 10000.0;
        System.out.printf("ALLOCATION static-direct-ingestion %.3f bytes/row%n", directPerRow);
        assertTrue(directPerRow <= 192, "Direct static ingestion allocation budget exceeded");
      } finally {
        context.leave();
      }
    }
  }

  private static final class MixedRoot extends RootNode {
    @Child private ReadLongNode integer = ReadLongNode.create();
    @Child private ReadDoubleNode floating = ReadDoubleNode.create();
    @Child private ReadBooleanNode bool = ReadBooleanNode.create();
    @Child private ReadLengthNode length = ReadLengthNode.create();
    boolean compiled;

    MixedRoot() {
      super(null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object row = frame.getArguments()[0];
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int i = 0; i < count; i++) {
        int exactLength = length.execute(row, 3);
        com.oracle.truffle.api.CompilerAsserts.partialEvaluationConstant(exactLength);
        sum +=
            integer.execute(row, 0)
                + (long) floating.execute(row, 1)
                + (bool.execute(row, 2) ? 1 : 0)
                + exactLength;
      }
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
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
