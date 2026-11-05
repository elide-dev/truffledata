package dev.elide.truffledata.comparison;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.sun.management.ThreadMXBean;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.json.JsonScalarAccess;
import dev.elide.truffledata.json.JsonScalarPlan;
import dev.elide.truffledata.json.JsonScalarRecord;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.graalvm.polyglot.Context;
import org.jspecify.annotations.NullMarked;

/** SVM scalar JSON fixture, with separate ingestion and retained-read measurements. */
@NullMarked
public final class JsonComparison {
  private static final int ROWS = 32;
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

  public static void main(String[] args) throws Exception {
    if (args.length == 1 && args[0].equals("--smoke")) {
      for (String engine : new String[] {"gson-stream", "simdjson-typed", "simdjson-specialized"})
        for (String stage : new String[] {"ingest", "read-aot", "interpret", "peak"})
          run(engine, stage, true, true);
    } else {
      if (args.length != 3) throw new IllegalArgumentException("engine stage time|memory");
      run(args[0], args[1], args[2].equals("memory"), false);
    }
  }

  private static void run(String engine, String stage, boolean memory, boolean smoke)
      throws Exception {
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
    try (var context =
            Context.newBuilder("td-compare")
                .allowExperimentalOptions(true)
                .option("engine.Compilation", Boolean.toString(peak))
                .option("engine.BackgroundCompilation", "false")
                .option("engine.CompilationFailureAction", "Throw")
                .option("compiler.TreatPerformanceWarningsAsErrors", "all")
                .option("compiler.MaximumGraalGraphSize", "10000")
                .build();
        var plan = JsonScalarPlan.compile(schema)) {
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
                return guest ? (Long) target.call(rows, rotation) : root.read(rows, rotation);
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
        tier(target, peak);
        int batch = ingest ? 8 : 128;
        long until = System.nanoTime() + (smoke ? 1_000_000 : 100_000_000);
        do {
          measure(operation, batch, expected);
        } while (System.nanoTime() < until);
        beforeSamples.run();
        double[] samples = new double[3];
        for (int s = 0; s < samples.length; s++) {
          tier(target, peak);
          if (memory) {
            long before = counter.getThreadAllocatedBytes(thread);
            measure(operation, batch, expected);
            samples[s] =
                (counter.getThreadAllocatedBytes(thread) - before) / (double) (batch * ROWS);
          } else {
            long start = System.nanoTime(), elapsed, count = 0;
            do {
              measure(operation, batch, expected);
              count += batch;
              elapsed = System.nanoTime() - start;
            } while (elapsed < sampleNanos);
            samples[s] = elapsed / (double) (count * ROWS);
          }
          tier(target, peak);
        }
        System.out.printf(
            Locale.ROOT,
            "{\"workload\":\"json-scalar\",\"engine\":\"%s\",\"stage\":\"%s\",\"runtime\":\"svm\",\"guest_compiled\":%s,\"checksum\":%d,\"rows\":32,\"%s\":%s}%n",
            engine,
            stage,
            peak,
            expected,
            memory ? "allocated_bytes_per_row" : "ns_per_row",
            Arrays.toString(samples));
      } finally {
        for (Object row : rows) if (row instanceof JsonScalarRecord record) record.close();
        context.leave();
      }
    }
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

  private static void tier(OptimizedCallTarget target, boolean peak) {
    if (peak ? !target.isValidLastTier() : target.isValid())
      throw new AssertionError("Execution tier");
  }

  private static long nativeRead(JsonScalarRecord r) {
    return r.longValue(0) + (long) r.doubleValue(1) + (r.booleanValue(2) ? 1 : 0);
  }

  private static long stockRead(Stock r) {
    return r.n() + (long) r.d() + (r.b() ? 1 : 0);
  }

  private static final class Reads extends RootNode {
    private final boolean nativeEngine;

    private final boolean specialized;
    private final JsonScalarAccess n, d, b;

    Reads(boolean nativeEngine, boolean specialized, Schema schema) {
      super(ComparisonLanguage.current());
      this.nativeEngine = nativeEngine;
      this.specialized = specialized;
      n = new JsonScalarAccess(schema, 0);
      d = new JsonScalarAccess(schema, 1);
      b = new JsonScalarAccess(schema, 2);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return read((Object[]) frame.getArguments()[0], (Integer) frame.getArguments()[1]);
    }

    long nativeValue(JsonScalarRecord row) {
      return specialized
          ? n.longValue(row) + (long) d.doubleValue(row) + (b.booleanValue(row) ? 1 : 0)
          : nativeRead(row);
    }

    long read(Object[] rows, int rotation) {
      long sum = 0;
      for (int i = 0; i < ROWS; i++) {
        Object row = rows[(i + rotation) & (ROWS - 1)];
        sum += nativeEngine ? nativeValue((JsonScalarRecord) row) : stockRead((Stock) row);
      }
      return sum;
    }
  }
}
