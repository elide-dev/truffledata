package dev.elide.truffledata.comparison;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.sun.management.ThreadMXBean;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.json.JavaSink;
import dev.elide.truffledata.json.JsonAccess;
import dev.elide.truffledata.json.JsonExportNode;
import dev.elide.truffledata.json.JsonMessage;
import dev.elide.truffledata.json.JsonPlan;
import dev.elide.truffledata.json.JsonRecord;
import dev.elide.truffledata.json.JsonSnapshot;
import dev.elide.truffledata.json.JsonSvm;
import dev.elide.truffledata.json.JsonValue;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.graalvm.polyglot.Context;
import org.jspecify.annotations.NullMarked;

/**
 * SVM JSON fixtures, with separate ingestion and retained-read measurements: a three-field scalar
 * record (typed plans against Gson streaming) and a mixed generic document (index, export, interop
 * and snapshots against Gson trees).
 */
@NullMarked
public final class JsonComparison {
  private static final int ROWS = 32;
  // Emitted in the read helper itself; a compiled wrapper cannot satisfy this probe.
  private static final long COMPILED_READ = 1L << 48;
  static Runnable beforeSamples = () -> {};
  static long sampleNanos = 50_000_000L;
  private static volatile long sink;
  private static volatile Object retained = new Object();

  private record Stock(long n, double d, boolean b) {}

  private static Stock parse(byte[] input) throws Exception {
    try (var reader =
        new JsonReader(
            new InputStreamReader(new ByteArrayInputStream(input), StandardCharsets.UTF_8))) {
      reader.setLenient(false);
      reader.beginObject();
      long n = 0;
      double d = 0;
      boolean b = false;
      int seen = 0;
      while (reader.hasNext()) {
        int bit;
        switch (reader.nextName()) {
          case "n" -> {
            bit = 1;
            if (reader.peek() != JsonToken.NUMBER) throw new IllegalArgumentException();
            n = reader.nextLong();
          }
          case "d" -> {
            bit = 2;
            if (reader.peek() != JsonToken.NUMBER) throw new IllegalArgumentException();
            d = reader.nextDouble();
            if (!Double.isFinite(d)) throw new IllegalArgumentException();
          }
          case "b" -> {
            bit = 4;
            b = reader.nextBoolean();
          }
          default -> throw new IllegalArgumentException("Unknown field");
        }
        if ((seen & bit) != 0) throw new IllegalArgumentException("Duplicate field");
        seen |= bit;
      }
      reader.endObject();
      if (seen != 7 || reader.peek() != JsonToken.END_DOCUMENT)
        throw new IllegalArgumentException("Incomplete or trailing JSON");
      return new Stock(n, d, b);
    }
  }

  static final List<String> SCALAR =
      List.of("gson-stream", "simdjson-typed", "simdjson-specialized");
  static final List<String> GENERIC =
      List.of("gson-tree", "simdjson-generic-java", "simdjson-generic-interop", "simdjson-snapshot");
  // Smoke-only: export into a constant non-materializing sink, so the graph holds only the walk.
  static final String WALK = "simdjson-export-walk";
  static final List<String> STAGES = List.of("ingest", "read-aot", "interpret", "peak");

  public static void main(String[] args) throws Exception {
    if (args.length == 1 && args[0].equals("--smoke")) {
      for (String engine : SCALAR) for (String stage : STAGES) run(engine, stage, true, true);
      for (String engine : GENERIC) for (String stage : STAGES) run(engine, stage, true, true);
      run(WALK, "peak", true, true);
    } else {
      if (args.length != 3) throw new IllegalArgumentException("engine stage time|memory");
      run(args[0], args[1], args[2].equals("memory"), false);
    }
  }

  private static void run(String engine, String stage, boolean memory, boolean smoke)
      throws Exception {
    if (GENERIC.contains(engine) || engine.equals(WALK)) {
      generic(engine, stage, memory, smoke);
      return;
    }
    boolean specialized = engine.equals("simdjson-specialized");
    boolean nativeEngine = engine.equals("simdjson-typed") || specialized;
    if (!nativeEngine && !engine.equals("gson-stream"))
      throw new IllegalArgumentException("engine");
    if (!Arrays.asList("ingest", "read-aot", "interpret", "peak").contains(stage))
      throw new IllegalArgumentException("stage");
    boolean peak = stage.equals("peak"), ingest = stage.equals("ingest");
    boolean guest = peak || stage.equals("interpret");
    var schema =
        Schema.of(
            Field.requiredLong("n"),
            new Field("d", Field.Kind.FLOAT64, false, false),
            new Field("b", Field.Kind.BOOLEAN, false, false));
    try (var context = context(peak);
        var plan = JsonPlan.compile(schema)) {
      context.initialize("td-compare");
      context.enter();
      Object[] rows = new Object[ROWS];
      try {
        byte[][] inputs = new byte[ROWS][];
        long expected = 0;
        for (int i = 0; i < ROWS; i++) {
          long n = 1000 + 7 * i;
          double d = i + 0.5;
          boolean b = (i & 1) == 0;
          String json =
              i % 2 == 0
                  ? "{\"n\":" + n + ",\"d\":" + d + ",\"b\":" + b + "}"
                  : "{\"b\":" + b + ",\"n\":" + n + ",\"d\":" + d + "}";
          inputs[i] = json.getBytes(StandardCharsets.UTF_8);
          rows[i] = nativeEngine ? plan.decode(inputs[i]) : parse(inputs[i]);
          expected += n + (long) d + (b ? 1 : 0);
        }
        var root = new Reads(nativeEngine, specialized, schema);
        var target = (OptimizedCallTarget) root.getCallTarget();
        Operation operation =
            rotation -> {
              if (!ingest)
                return guest
                    ? ((Long) target.call(rows, rotation)) & ~COMPILED_READ
                    : root.read(rows, rotation);
              long sum = 0;
              for (int i = 0; i < ROWS; i++) {
                byte[] input = inputs[(i + rotation) & (ROWS - 1)];
                if (nativeEngine) {
                  try (var record = plan.decode(input)) {
                    retained = record;
                    sum += root.nativeValue(record);
                  }
                } else {
                  var record = parse(input);
                  retained = record;
                  sum += stockRead(record);
                }
              }
              return sum;
            };
        sample("json-scalar", engine, stage, memory, smoke, target, rows, operation, expected);
      } finally {
        for (Object row : rows) if (row instanceof JsonRecord record) record.close();
        context.leave();
      }
    }
  }

  /** Mixed generic documents: decode, export, interop reads and snapshots against Gson trees. */
  private static void generic(String engine, String stage, boolean memory, boolean smoke)
      throws Exception {
    if (!STAGES.contains(stage)) throw new IllegalArgumentException("stage");
    boolean peak = stage.equals("peak"), ingest = stage.equals("ingest");
    boolean guest = peak || stage.equals("interpret");
    int mode = engine.equals(WALK) ? GenericReads.WALK : GENERIC.indexOf(engine);
    if (mode < 0) throw new IllegalArgumentException("engine");
    try (var context = context(peak)) {
      context.initialize("td-compare");
      context.enter();
      List<JsonMessage> messages = new ArrayList<>();
      try {
        byte[][] inputs = new byte[ROWS][];
        Object[] rows = new Object[ROWS];
        long expected = 0;
        for (int i = 0; i < ROWS; i++) {
          inputs[i] = JsonFixture.document(i);
          expected += mode == GenericReads.WALK ? JsonFixture.walkChecksum(i) : JsonFixture.checksum(i);
          if (mode == GenericReads.GSON) {
            rows[i] = JsonFixture.gsonParse(inputs[i]);
          } else if (mode == GenericReads.SNAPSHOT) {
            try (JsonMessage message = JsonSvm.decode(inputs[i])) {
              rows[i] = message.snapshot().root();
            }
          } else {
            JsonMessage message = JsonSvm.decode(inputs[i]);
            messages.add(message);
            rows[i] = message.root();
          }
        }
        var root = new GenericReads(engine, mode);
        var target = (OptimizedCallTarget) root.getCallTarget();
        Operation operation =
            rotation -> {
              if (!ingest)
                return guest
                    ? ((Long) target.call(rows, rotation)) & ~COMPILED_READ
                    : root.read(rows, rotation);
              long sum = 0;
              for (int i = 0; i < ROWS; i++) sum += root.ingest(inputs[(i + rotation) & (ROWS - 1)]);
              return sum;
            };
        sample("json-generic", engine, stage, memory, smoke, target, rows, operation, expected);
      } finally {
        for (JsonMessage message : messages) message.close();
        context.leave();
      }
    }
  }

  private static Context context(boolean peak) {
    return Context.newBuilder("td-compare")
        .allowExperimentalOptions(true)
        .option("engine.Compilation", Boolean.toString(peak))
        .option("engine.BackgroundCompilation", "false")
        .option("engine.CompilationFailureAction", "Throw")
        .option("compiler.TreatPerformanceWarningsAsErrors", "all")
        .option("compiler.MaximumGraalGraphSize", "10000")
        .build();
  }

  /** Calibrates the allocation counter, reaches the stage's tier, then prints three samples. */
  private static void sample(
      String workload,
      String engine,
      String stage,
      boolean memory,
      boolean smoke,
      OptimizedCallTarget target,
      Object[] rows,
      Operation operation,
      long expected)
      throws Exception {
    boolean peak = stage.equals("peak"), ingest = stage.equals("ingest");
    ThreadMXBean counter = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!counter.isThreadAllocatedMemorySupported())
      throw new AssertionError("Allocation counter required");
    counter.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId();
    long calibration = counter.getThreadAllocatedBytes(thread);
    retained = new byte[1 << 20];
    if (calibration < 0 || counter.getThreadAllocatedBytes(thread) - calibration < 1 << 20)
      throw new AssertionError("Allocation calibration");
    if (peak) {
      for (int attempt = 0; attempt < 8; attempt++) {
        measure(operation, 1024, expected);
        target.compile(true);
        target.waitForCompilation();
        measure(operation, 1024, expected);
        if (target.isValidLastTier()) break;
      }
    }
    tier(target, peak, rows);
    int batch = ingest ? 8 : 128;
    long until = System.nanoTime() + (smoke ? 1_000_000 : 100_000_000);
    do {
      measure(operation, batch, expected);
    } while (System.nanoTime() < until);
    beforeSamples.run();
    double[] samples = new double[3];
    for (int s = 0; s < samples.length; s++) {
      tier(target, peak, rows);
      if (memory) {
        long before = counter.getThreadAllocatedBytes(thread);
        measure(operation, batch, expected);
        samples[s] = (counter.getThreadAllocatedBytes(thread) - before) / (double) (batch * ROWS);
      } else {
        long start = System.nanoTime(), elapsed, count = 0;
        do {
          measure(operation, batch, expected);
          count += batch;
          elapsed = System.nanoTime() - start;
        } while (elapsed < sampleNanos);
        samples[s] = elapsed / (double) (count * ROWS);
      }
      tier(target, peak, rows);
    }
    System.out.printf(
        Locale.ROOT,
        "{\"workload\":\"%s\",\"engine\":\"%s\",\"stage\":\"%s\",\"runtime\":\"svm\",\"guest_compiled\":%s,\"checksum\":%d,\"rows\":32,\"%s\":%s}%n",
        workload,
        engine,
        stage,
        peak,
        expected,
        memory ? "allocated_bytes_per_row" : "ns_per_row",
        Arrays.toString(samples));
  }

  private interface Operation {
    long run(int rotation) throws Exception;
  }

  private static void measure(Operation operation, int count, long expected) throws Exception {
    long sum = 0;
    for (int i = 0; i < count; i++) sum += operation.run(i);
    if (sum != expected * count) throw new AssertionError("Checksum");
    sink = sum;
  }

  private static void tier(OptimizedCallTarget target, boolean peak, Object[] rows) {
    if (peak ? !target.isValidLastTier() : target.isValid())
      throw new AssertionError("Execution tier");
    if (peak && (((Long) target.call(rows, 0)) & COMPILED_READ) == 0)
      throw new AssertionError("Read helper did not execute as compiled guest code");
  }

  private static long nativeRead(JsonRecord r) {
    return r.longValue(0) + (long) r.doubleValue(1) + (r.booleanValue(2) ? 1 : 0);
  }

  private static long stockRead(Stock r) {
    return r.n() + (long) r.d() + (r.b() ? 1 : 0);
  }

  private static final class Reads extends RootNode {
    private final boolean nativeEngine;

    private final boolean specialized;
    private final JsonAccess n, d, b;

    Reads(boolean nativeEngine, boolean specialized, Schema schema) {
      super(ComparisonLanguage.current());
      this.nativeEngine = nativeEngine;
      this.specialized = specialized;
      n = new JsonAccess(schema, 0);
      d = new JsonAccess(schema, 1);
      b = new JsonAccess(schema, 2);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return read((Object[]) frame.getArguments()[0], (Integer) frame.getArguments()[1]);
    }

    long nativeValue(JsonRecord row) {
      return specialized
          ? n.longValue(row) + (long) d.doubleValue(row) + (b.booleanValue(row) ? 1 : 0)
          : nativeRead(row);
    }

    long read(Object[] rows, int rotation) {
      long sum = 0;
      for (int i = 0; i < ROWS; i++) {
        Object row = rows[(i + rotation) & (ROWS - 1)];
        sum += nativeEngine ? nativeValue((JsonRecord) row) : stockRead((Stock) row);
      }
      return sum | (CompilerDirectives.inCompiledCode() ? COMPILED_READ : 0);
    }
  }

  /**
   * Generic retained reads. Every engine consumes the same members per document; the guest loop is
   * the measured read for interpret and peak, and the same method runs directly for read-aot.
   */
  private static final class GenericReads extends RootNode {
    static final int GSON = 0, JAVA = 1, INTEROP = 2, SNAPSHOT = 3, WALK = 4;
    private final String name;
    private final int mode;
    private final JsonFixture.CountingSink counting = new JsonFixture.CountingSink();
    @Child private JsonFixture.InteropReader reader = new JsonFixture.InteropReader();
    @Child private JsonExportNode export = JsonExportNode.create();

    GenericReads(String engine, int mode) {
      super(ComparisonLanguage.current());
      this.name = "GenericReads[" + engine + "]";
      this.mode = mode;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return read((Object[]) frame.getArguments()[0], (Integer) frame.getArguments()[1]);
    }

    long read(Object[] rows, int rotation) {
      long sum = 0;
      for (int i = 0; i < ROWS; i++) sum += value(rows[(i + rotation) & (ROWS - 1)]);
      return sum | (CompilerDirectives.inCompiledCode() ? COMPILED_READ : 0);
    }

    /** One retained document: a Gson tree, or the root view of a message or snapshot. */
    private long value(Object doc) {
      switch (mode) {
        case GSON:
          return JsonFixture.gsonRead(doc);
        case JAVA:
          {
            // Export the retained index into fresh host objects, as a JSON.parse sink would.
            JavaSink sink = new JavaSink();
            export.execute((JsonValue) doc, sink);
            return JsonFixture.javaRead(Objects.requireNonNull(sink.result()));
          }
        case WALK:
          counting.sum = 0;
          export.execute((JsonValue) doc, counting);
          return counting.sum;
        default:
          return reader.read(doc);
      }
    }

    /** Parse or decode one document, consume it like {@link #value}, and release native storage. */
    long ingest(byte[] input) {
      if (mode == GSON) {
        Object tree = JsonFixture.gsonParse(input);
        retained = tree;
        return JsonFixture.gsonRead(tree);
      }
      JsonMessage message = JsonSvm.decode(input);
      if (mode == SNAPSHOT) {
        JsonSnapshot snapshot;
        try {
          snapshot = message.snapshot();
        } finally {
          message.close();
        }
        retained = snapshot;
        return reader.read(snapshot.root());
      }
      try {
        switch (mode) {
          case JAVA:
            {
              JavaSink sink = new JavaSink();
              message.export(sink);
              Object tree = Objects.requireNonNull(sink.result());
              retained = tree;
              return JsonFixture.javaRead(tree);
            }
          case WALK:
            counting.sum = 0;
            message.export(counting);
            return counting.sum;
          default:
            retained = message;
            return reader.read(message.root());
        }
      } finally {
        message.close();
      }
    }
  }
}
