package dev.elide.truffledata.comparison;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import com.mysql.cj.protocol.a.MysqlTextValueDecoder;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.sun.management.ThreadMXBean;
import dev.elide.truffledata.*;
import dev.elide.truffledata.buffa.*;
import dev.elide.truffledata.generated.ComparisonContracts;
import dev.elide.truffledata.protobuf.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.Context;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.ByteConverter;

/** Fixed-work comparisons; orchestration forks each cell and separates timing from allocations. */
@NullMarked
public final class Comparison {
  static final int ROWS = 32;
  static final String[] WORKLOADS = {
    "proto-scalar", "proto-bytes", "timestamp-json", "mysql-int4", "postgres-int4"
  };
  private static volatile @Nullable Object retained;
  private static volatile long checksum;

  private Comparison() {}

  public static void main(String[] args) throws Exception {
    if (args.length == 1 && args[0].equals("--smoke")) {
      for (String workload : WORKLOADS) {
        for (String engine : engines(workload)) {
          for (String stage : stages(workload, engine)) run(workload, engine, stage, true, false);
        }
      }
    } else {
      if (args.length != 4 || !(args[3].equals("time") || args[3].equals("memory")))
        throw new IllegalArgumentException("workload engine stage time|memory, or --smoke");
      run(args[0], args[1], args[2], false, args[3].equals("memory"));
    }
  }

  static List<String> engines(String workload) {
    return workload.startsWith("proto-") || workload.equals("timestamp-json")
        ? List.of("stock", "owned", "buffa")
        : List.of("stock", "static");
  }

  static List<String> stages(String workload, String engine) {
    if (workload.equals("timestamp-json")) return List.of("ingest");
    return engine.equals("stock")
        ? List.of("ingest", "read-aot")
        : List.of("ingest", "interpret", "peak");
  }

  private static void run(
      String workload, String engine, String stage, boolean smoke, boolean memory)
      throws Exception {
    if (!Arrays.asList(WORKLOADS).contains(workload)
        || !engines(workload).contains(engine)
        || !stages(workload, engine).contains(stage))
      throw new IllegalArgumentException("Unknown comparison cell");
    boolean peak = stage.equals("peak");
    try (Context context =
        Context.newBuilder("td-compare")
            .allowExperimentalOptions(true)
            .option("engine.Compilation", Boolean.toString(peak))
            .option("engine.BackgroundCompilation", "false")
            .option("engine.CompilationFailureAction", "Throw")
            .option("compiler.TreatPerformanceWarningsAsErrors", "all")
            .option("compiler.MaximumGraalGraphSize", "10000")
            .build()) {
      context.initialize("td-compare");
      context.enter();
      try (Fixture fixture = new Fixture(workload, engine)) {
        boolean ingest = stage.equals("ingest");
        Operation operation = ingest ? fixture::ingest : fixture::read;
        for (int i = 0; i < 4; i++) fixture.check(operation.run(i));
        if (peak) {
          for (int i = 0; i < 1000; i++) fixture.check(operation.run(i));
          fixture.target.compile(true);
          fixture.target.waitForCompilation();
        }
        fixture.tier(peak);
        // Verify the allocation counter even in smoke, on JVM and SVM.
        ThreadMXBean bean = memory || smoke ? allocationCounter() : null;
        if (smoke) {
          fixture.check(operation.run(1));
          return;
        }
        double[] samples = new double[3];
        int batch = ingest ? 8 : 128;
        if (memory) {
          measure(operation, batch, fixture.expected);
          long thread = Thread.currentThread().threadId();
          for (int s = 0; s < samples.length; s++) {
            long before = Objects.requireNonNull(bean).getThreadAllocatedBytes(thread);
            measure(operation, batch, fixture.expected);
            long allocated = bean.getThreadAllocatedBytes(thread) - before;
            if (allocated < 0) throw new AssertionError("Allocation counter regressed");
            samples[s] = (double) allocated / (batch * ROWS);
          }
        } else {
          long until = System.nanoTime() + 100_000_000;
          while (System.nanoTime() < until) measure(operation, batch, fixture.expected);
          for (int s = 0; s < samples.length; s++) {
            long count = 0, start = System.nanoTime(), elapsed;
            do {
              measure(operation, batch, fixture.expected);
              count += batch;
              elapsed = System.nanoTime() - start;
            } while (elapsed < 50_000_000);
            samples[s] = (double) elapsed / (count * ROWS);
          }
        }
        fixture.tier(peak);
        System.out.printf(
            Locale.ROOT,
            "{\"workload\":\"%s\",\"engine\":\"%s\",\"stage\":\"%s\",\"runtime\":\"%s\",\"backend\":\"%s\",\"guest_compiled\":%s,\"rows\":%d,\"checksum\":%d,\"%s\":%s}%n",
            workload,
            engine,
            stage,
            ImageInfo.inImageRuntimeCode() ? "svm" : "jvm",
            fixture.decoder == null ? "none" : fixture.decoder.selected(),
            peak,
            ROWS,
            fixture.expected,
            memory ? "allocated_bytes_per_row" : "ns_per_row",
            Arrays.toString(samples));
      } finally {
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
    if (sum != expected * count) throw new AssertionError("Measured checksum mismatch");
    checksum = sum;
  }

  private static ThreadMXBean allocationCounter() {
    if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean)
        || !bean.isThreadAllocatedMemorySupported())
      throw new AssertionError("Allocation accounting required");
    bean.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(thread);
    retained = new byte[1024 * 1024];
    if (before < 0 || bean.getThreadAllocatedBytes(thread) - before < 1024 * 1024)
      throw new AssertionError("Allocation counter failed escaped-array calibration");
    retained = null;
    return bean;
  }

  private static @Nullable Path library() throws Exception {
    if (ImageInfo.inImageRuntimeCode()) return null;
    String explicit = System.getenv("TRUFFLEDATA_BUFFA_LIBRARY");
    if (explicit != null) return Path.of(explicit);
    Path dir =
        Path.of(
            Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
            Objects.requireNonNull(System.getenv("TEST_WORKSPACE")),
            "protobuf-buffa/native");
    try (var files = Files.list(dir)) {
      return files
          .filter(p -> p.toString().endsWith(".so") || p.toString().endsWith(".dylib"))
          .findFirst()
          .orElseThrow();
    }
  }

  private static final class Fixture implements AutoCloseable {
    final String workload, engine;
    final byte[][] wire = new byte[ROWS][];
    final String[] json = new String[ROWS];
    final Object[] rows = new Object[ROWS];
    final @Nullable TypedMessage[] messages = new TypedMessage[ROWS];
    final @Nullable WireInput[] inputs = new WireInput[ROWS];
    final JsonFormat.Parser jsonParser = JsonFormat.parser();
    final ProtoPlan plan;
    final StaticLayout layout;
    final @Nullable BuffaWire decoder;
    final @Nullable BuffaPlan nativePlan;
    final OptimizedCallTarget target;
    final long expected;

    Fixture(String workload, String engine) throws Exception {
      this.workload = workload;
      this.engine = engine;
      boolean bytes = workload.equals("proto-bytes");
      boolean jdbc = workload.endsWith("int4");
      var language = ComparisonLanguage.current();
      plan =
          ComparisonContracts.create(language, Limits.DEFAULT, 16, 1 << 20)
              .type(bytes ? "google.protobuf.BytesValue" : "google.protobuf.Timestamp");
      layout =
          new StaticLayout(
              language, Schema.of(Field.integer("id", 32, true, false, false)), Limits.DEFAULT);
      decoder = engine.equals("buffa") ? BuffaWire.open(BuffaWire.Mode.NATIVE, library()) : null;
      nativePlan = decoder == null ? null : decoder.compile(plan, 65536);
      target = (OptimizedCallTarget) new ReadRoot(bytes ? 1 : jdbc ? 2 : 0).getCallTarget();
      long sum = 0;
      for (int i = 0; i < ROWS; i++) {
        if (jdbc) {
          int value = 1000 + i * 7;
          wire[i] =
              workload.equals("mysql-int4")
                  ? Integer.toString(value).getBytes(StandardCharsets.US_ASCII)
                  : new byte[4];
          if (workload.equals("postgres-int4")) ByteConverter.int4(wire[i], 0, value);
          sum += value;
        } else if (bytes) {
          byte[] payload = new byte[65536 + i];
          Arrays.fill(payload, (byte) (i + 1));
          wire[i] = BytesValue.of(ByteString.copyFrom(payload)).toByteArray();
          sum += payload.length;
        } else {
          var timestamp =
              Timestamp.newBuilder().setSeconds(1700000000L + i).setNanos(1000 + i).build();
          wire[i] = timestamp.toByteArray();
          json[i] = JsonFormat.printer().print(timestamp);
          sum += timestamp.getSeconds() + timestamp.getNanos();
        }
        if (decoder != null) {
          inputs[i] = decoder.allocate(wire[i].length).put(wire[i]);
          messages[i] = parseNative(i);
          rows[i] = Objects.requireNonNull(messages[i]).row();
        } else rows[i] = parse(i);
      }
      expected = sum;
      check(read(0));
    }

    Object parse(int i) throws Exception {
      if (workload.endsWith("int4")) {
        long value =
            workload.equals("mysql-int4")
                ? MysqlTextValueDecoder.getInt(wire[i], 0, wire[i].length)
                : ByteConverter.int4(wire[i], 0);
        return engine.equals("stock") ? value : layout.create(value);
      }
      if (engine.equals("owned"))
        return workload.equals("timestamp-json") ? plan.parseJson(json[i]) : plan.decode(wire[i]);
      if (workload.equals("proto-bytes")) return BytesValue.parseFrom(wire[i]);
      if (workload.equals("timestamp-json")) {
        var builder = Timestamp.newBuilder();
        jsonParser.merge(json[i], builder);
        return builder.build();
      }
      return Timestamp.parseFrom(wire[i]);
    }

    TypedMessage parseNative(int i) {
      return workload.equals("timestamp-json")
          ? Objects.requireNonNull(nativePlan).parseJson(json[i], 1 << 20)
          : Objects.requireNonNull(nativePlan).decode(Objects.requireNonNull(inputs[i]), 1 << 20);
    }

    long ingest(int rotation) throws Exception {
      long sum = 0;
      for (int n = 0; n < ROWS; n++) {
        int i = (n + rotation) & (ROWS - 1);
        if (nativePlan != null) {
          try (var message = parseNative(i)) {
            retained = message;
            sum += readOne(message.row());
          }
        } else {
          Object row = parse(i);
          retained = row;
          sum += readOne(row);
        }
      }
      return sum;
    }

    long readOne(Object row) {
      if (engine.equals("stock")) {
        if (row instanceof Timestamp t) return t.getSeconds() + t.getNanos();
        if (row instanceof BytesValue b) return b.getValue().size();
        return (Long) row;
      }
      var data = DataLibrary.factory().getUncached();
      if (workload.equals("proto-bytes")) {
        Object value = data.read(row, 0);
        try {
          return com.oracle.truffle.api.interop.InteropLibrary.getUncached().getArraySize(value);
        } catch (com.oracle.truffle.api.interop.UnsupportedMessageException e) {
          throw new AssertionError(e);
        }
      }
      long value = (Long) data.read(row, 0);
      return workload.endsWith("int4") ? value : value + (Long) data.read(row, 1);
    }

    long read(int rotation) {
      if (!engine.equals("stock")) return (Long) target.call(rows, rotation);
      long sum = 0;
      if (workload.equals("proto-bytes")) {
        for (int n = 0; n < ROWS; n++)
          sum += ((BytesValue) rows[(n + rotation) & (ROWS - 1)]).getValue().size();
      } else if (workload.endsWith("int4")) {
        for (int n = 0; n < ROWS; n++) sum += (Long) rows[(n + rotation) & (ROWS - 1)];
      } else {
        for (int n = 0; n < ROWS; n++) {
          Timestamp value = (Timestamp) rows[(n + rotation) & (ROWS - 1)];
          sum += value.getSeconds() + value.getNanos();
        }
      }
      return sum;
    }

    void check(long actual) {
      if (actual != expected)
        throw new AssertionError("Fixture checksum mismatch: " + actual + " != " + expected);
    }

    void tier(boolean peak) {
      if (peak ? !target.isValidLastTier() : target.isValid())
        throw new AssertionError("Wrong guest execution tier");
    }

    @Override
    public void close() {
      for (var message : messages) if (message != null) message.close();
      for (var input : inputs) if (input != null) input.close();
      if (nativePlan != null) nativePlan.close();
      if (decoder != null) decoder.close();
    }
  }

  private static final class ReadRoot extends RootNode {
    private final int kind;
    @Child private ReadLongNode first = ReadLongNode.create();
    @Child private ReadLongNode second = ReadLongNode.create();
    @Child private ReadLengthNode length = ReadLengthNode.create();

    ReadRoot(int kind) {
      super(ComparisonLanguage.current());
      this.kind = kind;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object[] rows = (Object[]) frame.getArguments()[0];
      int rotation = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int n = 0; n < ROWS; n++) {
        Object row = rows[(n + rotation) & (ROWS - 1)];
        sum +=
            kind == 1
                ? length.execute(row, 0)
                : first.execute(row, 0) + (kind == 0 ? second.execute(row, 1) : 0);
      }
      return sum;
    }
  }
}
