package dev.elide.truffledata.comparison;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.sun.management.ThreadMXBean;
import dev.elide.truffledata.tape.*;
import dev.elide.truffledata.yaml.*;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.Context;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** YAML ingestion comparison and independently checked compiled retained-read/export gates. */
@NullMarked
public final class YamlComparison {
  static final List<String> OURS =
      List.of(
          "truffledata-yaml-java",
          "truffledata-yaml-truffle",
          "truffledata-yaml-interop",
          "truffledata-yaml-typed",
          "truffledata-yaml-snapshot");
  static final List<String> FOREIGN =
      List.of(
          "snakeyaml-engine-load",
          "snakeyaml-engine-events",
          "snakeyaml-1.1-load",
          "jackson-yaml-tree");
  static @Nullable Function<String, Engine> foreign;
  private static volatile Object retained = new Object();
  private static volatile long blackhole;
  private static final long COMPILED = 1L << 48;
  private static final int ROWS = 32;

  interface Engine extends AutoCloseable {
    long ingest(YamlFixture.Input input) throws Exception;

    default void close() {}
  }

  static void retain(Object value) {
    retained = value;
  }

  static YamlSession session() {
    return ImageInfo.inImageCode()
        ? YamlSession.open()
        : YamlJvm.openSession(
            Path.of(Objects.requireNonNull(System.getProperty("yaml.library"), "yaml.library")));
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 1 && args[0].equals("--smoke")) {
      for (String workload : List.of("yaml-config", "yaml-records", "yaml-flow", "yaml-stream"))
        for (String engine : OURS)
          if (!(workload.equals("yaml-stream") && engine.equals("truffledata-yaml-typed")))
            measure(workload, engine, true);
      if (foreign != null)
        for (String workload : List.of("yaml-config", "yaml-records", "yaml-flow", "yaml-stream"))
          for (String engine : FOREIGN) measure(workload, engine, true);
      if (ImageInfo.inImageCode()) measure("yaml-flow", "truffledata-json-interop", true);
      if (ImageInfo.inImageCode()) compiled();
    } else if (args.length == 1 && args[0].equals("--compiled")) {
      compiled();
    } else if (args.length == 2) measure(args[0], args[1], false);
    else throw new IllegalArgumentException("--smoke | workload engine");
  }

  private static void measure(String workload, String name, boolean smoke) throws Exception {
    var input = YamlFixture.input(workload);
    long expected = name.equals("snakeyaml-engine-events") ? input.events() : input.checksum();
    try (Engine engine =
        name.equals("truffledata-json-interop")
            ? new JsonEngine()
            : OURS.contains(name)
                ? new NativeEngine(name, input)
                : Objects.requireNonNull(foreign, "JVM competitor entry point required")
                    .apply(name)) {
      var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
      if (!bean.isThreadAllocatedMemorySupported())
        throw new AssertionError("Allocation counter unavailable");
      bean.setThreadAllocatedMemoryEnabled(true);
      long thread = Thread.currentThread().threadId();
      long calibration = bean.getThreadAllocatedBytes(thread);
      retained = new byte[1 << 20];
      if (calibration < 0 || bean.getThreadAllocatedBytes(thread) - calibration < 1 << 20)
        throw new AssertionError("Allocation calibration failed");
      long until = System.nanoTime() + (smoke ? 1_000_000L : 3_000_000_000L);
      do {
        check(engine.ingest(input), expected);
      } while (System.nanoTime() < until);
      double[] times = new double[3], bytes = new double[3];
      for (int sample = 0; sample < 3; sample++) {
        long start = System.nanoTime(),
            before = bean.getThreadAllocatedBytes(thread),
            operations = 0,
            elapsed;
        do {
          blackhole = engine.ingest(input);
          check(blackhole, expected);
          operations++;
          elapsed = System.nanoTime() - start;
        } while (elapsed < (smoke ? 1_000_000L : 200_000_000L));
        times[sample] = elapsed / (double) operations;
        bytes[sample] = (bean.getThreadAllocatedBytes(thread) - before) / (double) operations;
      }
      System.out.printf(
          Locale.ROOT,
          "{\"workload\":\"%s\",\"engine\":\"%s\",\"stage\":\"ingest\",\"runtime\":\"%s\",\"checksum\":%d,\"records\":%d,\"input_bytes\":%d,\"ns_per_operation\":%s,\"allocated_bytes_per_operation\":%s}%n",
          workload,
          name,
          ImageInfo.inImageCode() ? "svm" : "jvm",
          expected,
          input.records(),
          input.bytes().length,
          Arrays.toString(times),
          Arrays.toString(bytes));
    }
  }

  private static void check(long actual, long expected) {
    if (actual != expected) throw new AssertionError("Checksum: " + actual + " != " + expected);
  }

  /** Same flow bytes and ten reads, providing the same-run JSON overhead reference. */
  private static final class JsonEngine implements Engine {
    private final dev.elide.truffledata.json.JsonSession session =
        dev.elide.truffledata.json.JsonSession.open();
    private final JsonFixture.InteropReader reader = new JsonFixture.InteropReader();

    public long ingest(YamlFixture.Input input) {
      if (!input.name().equals("yaml-flow"))
        throw new IllegalArgumentException("JSON reference requires flow input");
      try (var message = session.decode(input.bytes())) {
        var rows = message.root().member("rows");
        long sum = 0;
        for (long i = 0; i < rows.size(); i++) sum += reader.read(rows.element(i));
        return sum;
      }
    }

    public void close() {
      session.close();
    }
  }

  private static final class NativeEngine implements Engine {
    private final String name;
    private final YamlSession session = session();
    private final @Nullable YamlPlan plan;
    private final JavaSink.KeyCache javaKeys = new JavaSink.KeyCache();
    private final TruffleSink.KeyCache truffleKeys = new TruffleSink.KeyCache();
    private final JsonFixture.InteropReader reader = new JsonFixture.InteropReader();

    NativeEngine(String name, YamlFixture.Input input) {
      this.name = name;
      if (name.equals("truffledata-yaml-typed") && input.name().equals("yaml-stream")) {
        session.close();
        throw new IllegalArgumentException("Typed streaming is not exposed by YamlPlan");
      }
      plan =
          name.equals("truffledata-yaml-typed")
              ? session.compile(YamlFixture.ROOT, YamlFixture.TYPES)
              : null;
    }

    public long ingest(YamlFixture.Input input) {
      if (plan != null) {
        try (TypedRecord root = plan.decode(input.bytes())) {
          var rows = root.array(0);
          long sum = 0;
          for (int i = 0; i < rows.size(); i++) {
            var row = rows.record(i);
            var dims = row.record(5);
            sum +=
                row.longValue(0)
                    + (long) row.doubleValue(2)
                    + (row.booleanValue(3) ? 1 : 0)
                    + dims.longValue(0)
                    + (long) dims.doubleValue(1)
                    + row.array(4).size()
                    + row.array(6).size()
                    + (row.isPresent(7) && !row.isNull(7) ? 1 : 0)
                    + (row.isNull(8) ? 1 : 0);
          }
          return sum;
        }
      }
      boolean stream = input.name().equals("yaml-stream");
      if (stream) {
        long sum = 0;
        try (YamlStream documents = session.documents(input.bytes())) {
          TapeValue root;
          while ((root = documents.next()) != null) sum += consume(root, false);
        }
        return sum;
      }
      if (name.equals("truffledata-yaml-java")) {
        var sink = new JavaSink(javaKeys);
        session.parse(input.bytes(), sink);
        retained = Objects.requireNonNull(sink.result());
        return javaRows(retained, false);
      }
      if (name.equals("truffledata-yaml-truffle")) {
        var sink = new TruffleSink(truffleKeys);
        session.parse(input.bytes(), sink);
        retained = Objects.requireNonNull(sink.result());
        return javaRows(retained, true);
      }
      try (var message = session.decode(input.bytes())) {
        return consume(message.root(), true);
      }
    }

    private long consume(TapeValue root, boolean wrapper) {
      if (name.equals("truffledata-yaml-java")) {
        var sink = new JavaSink(javaKeys);
        root.export(sink);
        retained = Objects.requireNonNull(sink.result());
        return wrapper ? javaRows(retained, false) : JsonFixture.javaRead(retained);
      }
      if (name.equals("truffledata-yaml-truffle")) {
        var sink = new TruffleSink(truffleKeys);
        root.export(sink);
        retained = Objects.requireNonNull(sink.result());
        return wrapper ? javaRows(retained, true) : JsonFixture.truffleRead(retained);
      }
      if (name.equals("truffledata-yaml-snapshot")) {
        TapeSnapshot snapshot = root.snapshot();
        retained = snapshot;
        root = snapshot.root();
      }
      if (!wrapper) return reader.read(root);
      TapeValue rows = root.member("rows");
      long sum = 0;
      for (long i = 0; i < rows.size(); i++) sum += reader.read(rows.element(i));
      return sum;
    }

    public void close() {
      if (plan != null) plan.close();
      session.close();
    }
  }

  private static final com.oracle.truffle.api.strings.TruffleString ROWS_KEY =
      com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached(
          "rows", com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16);

  private static long javaRows(Object tree, boolean truffle) {
    var rows =
        (List<?>) Objects.requireNonNull(((Map<?, ?>) tree).get(truffle ? ROWS_KEY : "rows"));
    long sum = 0;
    for (Object row : rows)
      sum += truffle ? JsonFixture.truffleRead(row) : JsonFixture.javaRead(row);
    return sum;
  }

  private static final class GenericReads extends RootNode {
    private final String mode;
    private final JsonFixture.CountingSink counting = new JsonFixture.CountingSink();
    @Child private JsonFixture.InteropReader reader = new JsonFixture.InteropReader();
    @Child private TapeExportNode export = TapeExportNode.create();

    GenericReads(String mode) {
      super(ComparisonLanguage.current());
      this.mode = mode;
    }

    public String getName() {
      return "GenericReads[" + mode + "]";
    }

    public String toString() {
      return getName();
    }

    public Object execute(VirtualFrame frame) {
      Object[] rows = (Object[]) frame.getArguments()[0];
      long sum = 0;
      for (int i = 0; i < ROWS; i++) {
        if (mode.equals("truffledata-yaml-export")) {
          counting.sum = 0;
          export.execute((TapeValue) rows[i], counting);
          sum += counting.sum;
        } else sum += reader.read(rows[i]);
      }
      return sum | (CompilerDirectives.inCompiledCode() ? COMPILED : 0);
    }
  }

  private static void compiled() {
    try (var context =
            Context.newBuilder("td-compare")
                .allowExperimentalOptions(true)
                .option("engine.BackgroundCompilation", "false")
                .option("engine.CompilationFailureAction", "Throw")
                .option("compiler.TreatPerformanceWarningsAsErrors", "all")
                .option("compiler.MaximumGraalGraphSize", "10000")
                .build();
        var session = session()) {
      context.initialize("td-compare");
      context.enter();
      var messages = new ArrayList<YamlMessage>();
      try {
        Object[] rows = new Object[ROWS];
        long checksum = 0, walk = 0;
        for (int i = 0; i < ROWS; i++) {
          var message = session.decode(YamlFixture.document(i));
          messages.add(message);
          rows[i] = message.root();
          checksum += JsonFixture.checksum(i);
          walk += JsonFixture.walkChecksum(i);
        }
        byte[][] inputs = new byte[ROWS][];
        for (int i = 0; i < ROWS; i++) inputs[i] = YamlFixture.document(i);
        var keys = new TruffleSink.KeyCache();
        var counter = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        counter.setThreadAllocatedMemoryEnabled(true);
        for (int round = 0; round < 128; round++)
          for (byte[] input : inputs) {
            var sink = new TruffleSink(keys);
            session.parse(input, sink);
            retained = Objects.requireNonNull(sink.result());
          }
        double[] sinkBytes = new double[3];
        for (int sample = 0; sample < 3; sample++) {
          long before = counter.getThreadAllocatedBytes(Thread.currentThread().threadId());
          for (int round = 0; round < 256; round++)
            for (byte[] input : inputs) {
              var sink = new TruffleSink(keys);
              session.parse(input, sink);
              retained = Objects.requireNonNull(sink.result());
            }
          sinkBytes[sample] =
              (counter.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before)
                  / (double) (256 * ROWS);
        }
        System.out.printf(
            Locale.ROOT,
            "{\"workload\":\"yaml-sink\",\"engine\":\"truffledata-yaml-truffle\",\"stage\":\"ingest\",\"runtime\":\"%s\",\"records\":32,\"allocated_bytes_per_record\":%s}%n",
            ImageInfo.inImageCode() ? "svm" : "jvm",
            Arrays.toString(sinkBytes));
        for (String mode : List.of("truffledata-yaml-interop", "truffledata-yaml-export")) {
          var root = new GenericReads(mode);
          var target = (OptimizedCallTarget) root.getCallTarget();
          long expected = mode.equals("truffledata-yaml-export") ? walk : checksum;
          for (int attempt = 0; attempt < 8 && !target.isValidLastTier(); attempt++) {
            for (int i = 0; i < 1024; i++)
              check(((Long) target.call((Object) rows)) & ~COMPILED, expected);
            target.compile(true);
            target.waitForCompilation();
          }
          check((Long) target.call((Object) rows), expected | COMPILED);
          if (!target.isValidLastTier() || NodeUtil.countNodes(root) > 192)
            throw new AssertionError("Compiled YAML tier/AST gate");
          var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
          bean.setThreadAllocatedMemoryEnabled(true);
          double[] bytes = new double[3];
          for (int sample = 0; sample < 3; sample++) {
            long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
            for (int i = 0; i < 1024; i++)
              check((Long) target.call((Object) rows), expected | COMPILED);
            bytes[sample] =
                (bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before)
                    / (double) (1024 * ROWS);
          }
          System.out.printf(
              Locale.ROOT,
              "{\"workload\":\"yaml-retained\",\"engine\":\"%s\",\"stage\":\"peak\",\"runtime\":\"%s\",\"guest_compiled\":true,\"checksum\":%d,\"records\":32,\"allocated_bytes_per_record\":%s}%n",
              mode,
              ImageInfo.inImageCode() ? "svm" : "jvm",
              expected,
              Arrays.toString(bytes));
          messages.get(0).close();
          try {
            target.call((Object) rows);
            throw new AssertionError("Closed retained YAML accepted");
          } catch (IllegalStateException expectedClosed) {
          }
          check(
              new JsonFixture.InteropReader().read(messages.get(1).root()),
              JsonFixture.checksum(1));
          var replacement = session.decode(YamlFixture.document(0));
          messages.set(0, replacement);
          rows[0] = replacement.root();
        }
      } finally {
        for (var message : messages) message.close();
        context.leave();
      }
    }
  }
}
