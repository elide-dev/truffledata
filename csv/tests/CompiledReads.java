package dev.elide.truffledata.csv.tests;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.ReadBooleanNode;
import dev.elide.truffledata.ReadDoubleNode;
import dev.elide.truffledata.ReadLongNode;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.csv.CsvBatch;
import dev.elide.truffledata.csv.CsvFormat;
import dev.elide.truffledata.csv.CsvLimits;
import dev.elide.truffledata.csv.CsvPlan;
import dev.elide.truffledata.csv.CsvReader;
import dev.elide.truffledata.csv.CsvRow;
import dev.elide.truffledata.csv.CsvSink;
import dev.elide.truffledata.csv.CsvText;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.graalvm.polyglot.Context;

/**
 * Compiled CSV gates: typed reads over rows of two batches from one reader, guest row iteration
 * through interop {@code readArrayElement}, and the one-pass export walk. Each root must run
 * compiled, stay within the AST budget, and allocate at most 0.05 B per read (per row for export).
 */
final class CompiledReads {
  static final int RECORDS = 40;
  static final int EXPORT_ROWS = 10_000;

  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  static Schema schema() {
    return Schema.of(
        Field.requiredLong("id"),
        new Field("price", Field.Kind.FLOAT64, false, false),
        new Field("active", Field.Kind.BOOLEAN, false, false));
  }

  /**
   * Record k is {@code id = 1000k, price = k + 0.5, active = k even}, every record 22 bytes and the
   * header 16, so batchBytes 192 cuts the stream deterministically: the first buffer holds the
   * header and records 0..7, the first batch records 0..15 and the second records 16..24.
   */
  static byte[] typedInput() {
    var text = new StringBuilder("id,price,active\n");
    for (int k = 0; k < RECORDS; k++) {
      boolean active = (k & 1) == 0;
      text.append(String.format("%06d", k * 1000))
          .append(',')
          .append(String.format(active ? "%07d" : "%06d", k))
          .append(".5,")
          .append(active ? "true" : "false")
          .append('\n');
    }
    return text.toString().getBytes(StandardCharsets.UTF_8);
  }

  static void run() {
    var schema = schema();
    var plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
    CsvBatch first, second;
    try (CsvReader reader =
        plan.open(new ByteArrayInputStream(typedInput()), CsvLimits.DEFAULT, 192)) {
      first = Objects.requireNonNull(reader.next());
      second = Objects.requireNonNull(reader.next());
      check(reader.schema() == schema, "Reader keeps the plan schema");
    } catch (IOException failure) {
      throw new AssertionError(failure);
    }
    check(first.schema() == second.schema(), "Batches of one reader share the schema");
    check(first.firstRecord() == 0 && first.size() == 16, "First batch cut: " + first.size());
    check(second.firstRecord() == 16 && second.size() == 9, "Second batch cut: " + second.size());
    try (var context =
        Context.newBuilder("csv-test")
            .allowExperimentalOptions(true)
            .option("engine.BackgroundCompilation", "false")
            .option("engine.CompilationFailureAction", "Throw")
            .option("compiler.TreatPerformanceWarningsAsErrors", "all")
            .option("compiler.MaximumGraalGraphSize", "10000")
            .build()) {
      context.initialize("csv-test");
      context.enter();
      try {
        typedReads(first, second);
        rowIteration(first, second);
        exportWalk();
      } finally {
        context.leave();
      }
    }
  }

  /** Runs {@code target} until compiled, checks the checksum and AST, and returns bytes per unit. */
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
    check(((Long) target.call(measured)) == expected, label + " compiled checksum");
    check(NodeUtil.countNodes(root) <= 192, label + " AST budget: " + NodeUtil.countNodes(root));
    var bean =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    check(bean.isThreadAllocatedMemorySupported(), "CSV allocation counter required");
    bean.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(thread);
    target.call(measured);
    double bytes = (bean.getThreadAllocatedBytes(thread) - before) / units;
    check(bytes >= 0 && bytes <= 0.05, label + " compiled bytes budget: " + bytes);
    return bytes;
  }

  /** ReadLong/Double/BooleanNode over rows 0 and 1 of each batch (records 0, 1, 16, 17). */
  private static void typedReads(CsvBatch first, CsvBatch second) {
    var rows = new CsvRow[] {first.row(0), first.row(1), second.row(0), second.row(1)};
    var root = new TypedReads();
    // Per 4 iterations: ids 34000, truncated prices 0 + 1 + 16 + 17 = 34, records 0 and 16 active.
    long expected = 250_000L * (34_000 + 34 + 2);
    double bytes =
        measure(
            "CSV typed reads",
            root,
            () -> root.compiled,
            new Object[] {rows, 100_000},
            new Object[] {rows, 1_000_000},
            expected,
            1_000_000.0);
    System.out.println("CSV typed compiled=true bytes/read=" + bytes);
  }

  /** Guest iteration: interop readArrayElement on alternating batches, then ReadLongNode. */
  private static void rowIteration(CsvBatch first, CsvBatch second) {
    var batches = new CsvBatch[] {first, second};
    var root = new RowIteration();
    // Every 16 iterations read rows 0..7 of both batches once: ids 1000 * (0..7 + 16..23).
    long expected = 62_500L * 1000 * (28 + 156);
    double bytes =
        measure(
            "CSV row iteration",
            root,
            () -> root.compiled,
            new Object[] {batches, 100_000},
            new Object[] {batches, 1_000_000},
            expected,
            1_000_000.0);
    System.out.println("CSV row iteration compiled=true bytes/read=" + bytes);
  }

  /**
   * Row k of the export batch: {@code id = k}, {@code price} null when k % 10 == 0 else k + 0.5,
   * {@code active = k even}, and {@code name} the escaped {@code "q""k"""} (reads {@code q"k"})
   * when k % 3 == 0, else {@code nk}.
   */
  private static void exportWalk() {
    var schema =
        Schema.of(
            Field.requiredLong("id"),
            new Field("price", Field.Kind.FLOAT64, true, false),
            new Field("active", Field.Kind.BOOLEAN, false, false),
            new Field("name", Field.Kind.STRING, false, false));
    var text = new StringBuilder("id,price,active,name\n");
    long expected = 0;
    for (int k = 0; k < EXPORT_ROWS; k++) {
      String digits = Integer.toString(k);
      text.append(k).append(',');
      if (k % 10 == 0) expected += 7; // null
      else {
        text.append(k).append(".5");
        expected += k;
      }
      text.append(',').append((k & 1) == 0 ? "true" : "false").append(',');
      if (k % 3 == 0) text.append("\"q\"\"").append(digits).append("\"\"\"");
      else text.append('n').append(digits);
      text.append('\n');
      // id, active, text length (q"k" or nk) and one row.
      expected += k + ((k & 1) == 0 ? 1 : 0) + (k % 3 == 0 ? 3 : 1) + digits.length() + 1_000_000;
    }
    var batch =
        CsvPlan.compile(schema, CsvFormat.RFC4180)
            .parse(text.toString().getBytes(StandardCharsets.UTF_8));
    check(batch.size() == EXPORT_ROWS, "Export batch rows");
    var root = new ExportWalk(batch);
    double bytes =
        measure(
            "CSV export walk",
            root,
            () -> root.compiled,
            new Object[] {10},
            new Object[] {200},
            200 * expected,
            200.0 * EXPORT_ROWS);
    System.out.println("CSV export compiled=true bytes/row=" + bytes);
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
      var rows = (CsvRow[]) frame.getArguments()[0];
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
    boolean compiled;

    RowIteration() {
      super(TestLanguage.current());
    }

    @Override
    public Object execute(VirtualFrame frame) {
      var batches = (CsvBatch[]) frame.getArguments()[0];
      int count = (Integer) frame.getArguments()[1];
      long sum = 0;
      try {
        for (int i = 0; i < count; i++) {
          Object row = elements.readArrayElement(batches[i & 1], (i >> 1) & 7);
          sum += longs.execute(row, 0);
        }
      } catch (InteropException failure) {
        throw CompilerDirectives.shouldNotReachHere(failure);
      }
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }

  /** Non-allocating sink: sums values, text lengths, null markers and rows. */
  static final class CountingSink implements CsvSink {
    long sum;

    @Override
    public void beginRow(long record, int fields) {
      sum += 1_000_000;
    }

    @Override
    public void text(int column, CsvText value) {
      sum += value.length();
    }

    @Override
    public void longValue(int column, long value) {
      sum += value;
    }

    @Override
    public void doubleValue(int column, double value) {
      sum += (long) value;
    }

    @Override
    public void booleanValue(int column, boolean value) {
      sum += value ? 1 : 0;
    }

    @Override
    public void nullValue(int column) {
      sum += 7;
    }

    @Override
    public void missing(int column) {
      sum += 11;
    }

    @Override
    public void endRow() {}
  }

  private static final class ExportWalk extends RootNode {
    private final CsvBatch batch;
    private final CountingSink sink = new CountingSink();
    boolean compiled;

    ExportWalk(CsvBatch batch) {
      super(TestLanguage.current());
      this.batch = batch;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      int count = (Integer) frame.getArguments()[0];
      sink.sum = 0;
      for (int i = 0; i < count; i++) batch.export(sink);
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count * EXPORT_ROWS);
      compiled |= CompilerDirectives.inCompiledCode();
      return sink.sum;
    }
  }
}
