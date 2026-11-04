package dev.elide.truffledata.comparison;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import com.mysql.cj.protocol.a.MysqlTextValueDecoder;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
    "proto-scalar",
    "proto-bytes",
    "timestamp-json",
    "mysql-int4",
    "postgres-int4",
    "proto-mixed",
    "proto-mixed-full"
  };
  private static volatile @Nullable Object retained;
  private static volatile long checksum;

  private Comparison() {}

  public static void main(String[] args) throws Exception {
    if (args.length == 1 && args[0].equals("--smoke")) {
      for (String workload : WORKLOADS) {
        for (String engine : engines(workload)) {
          for (String stage : stages(workload, engine)) {
            try {
              run(workload, engine, stage, true, false);
            } catch (com.oracle.truffle.api.OptimizationFailedException failure) {
              // A capability result for HotSpot only. Every SVM comparison must compile.
              if (ImageInfo.inImageRuntimeCode()
                  || !engine.equals("stock")
                  || !stage.equals("project-peak")
                  || !failure.toString().contains("GraphTooBigBailoutException")) throw failure;
              System.out.println(
                  "JVM_CAPABILITY stock/project-peak supported=false graph-budget=10000");
            }
          }
        }
      }
    } else {
      if (args.length != 4 || !(args[3].equals("time") || args[3].equals("memory")))
        throw new IllegalArgumentException("workload engine stage time|memory, or --smoke");
      run(args[0], args[1], args[2], false, args[3].equals("memory"));
    }
  }

  static List<String> engines(String workload) {
    if (workload.equals("proto-bytes"))
      return List.of("stock", "stock-aliased", "owned", "buffa", "generated-owned");
    if (workload.equals("proto-scalar") || workload.startsWith("proto-mixed"))
      return List.of("stock", "owned", "buffa", "generated-owned");
    return workload.startsWith("proto-") || workload.equals("timestamp-json")
        ? List.of("stock", "owned", "buffa")
        : List.of("stock", "static");
  }

  static List<String> stages(String workload, String engine) {
    if (engine.equals("generated-owned")) {
      if (workload.equals("proto-mixed-full")) return List.of("ingest");
      var result = new ArrayList<>(List.of("ingest", "read-aot", "interpret", "peak", "serialize"));
      if (workload.equals("proto-scalar"))
        result.addAll(List.of("project-aot", "project-interpret", "project-peak"));
      return result;
    }
    List<String> result = new ArrayList<>(baseStages(workload, engine));
    if (engine.equals("stock") && workload.equals("proto-scalar"))
      result.addAll(List.of("project-aot", "project-interpret", "project-peak"));
    if ((engine.equals("owned") || engine.equals("buffa"))
        && !workload.equals("proto-mixed-full")) {
      result.add("ingest-generated");
      if (!workload.equals("timestamp-json"))
        result.addAll(List.of("read-generated-aot", "interpret-generated", "peak-generated"));
    }
    if (workload.equals("proto-scalar")
        || workload.equals("proto-bytes")
        || workload.equals("proto-mixed")) result.add("serialize");
    return result;
  }

  private static List<String> baseStages(String workload, String engine) {
    if (workload.equals("proto-mixed-full")) return List.of("ingest");
    if (workload.equals("timestamp-json"))
      return engine.startsWith("stock") ? List.of("ingest") : List.of("ingest", "ingest-typed");
    return engine.startsWith("stock")
        ? workload.startsWith("proto-mixed")
            ? List.of("ingest", "read-aot", "interpret-boundary", "peak-boundary")
            : List.of("ingest", "read-aot", "interpret", "peak")
        : workload.equals("proto-bytes")
            ? List.of("ingest", "interpret", "peak")
            : List.of("ingest", "ingest-typed", "interpret", "peak");
  }

  private static void run(
      String workload, String engine, String stage, boolean smoke, boolean memory)
      throws Exception {
    if (!Arrays.asList(WORKLOADS).contains(workload)
        || !engines(workload).contains(engine)
        || !stages(workload, engine).contains(stage))
      throw new IllegalArgumentException("Unknown comparison cell");
    boolean peak = stage.contains("peak");
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
      try (Fixture fixture =
          new Fixture(
              workload,
              engine,
              stage.contains("interpret") || peak,
              stage.contains("generated"),
              stage.equals("serialize"),
              stage.startsWith("project-"))) {
        boolean ingest = stage.startsWith("ingest");
        Operation operation =
            stage.startsWith("project-")
                ? fixture::project
                : stage.equals("serialize")
                    ? fixture::serialize
                    : stage.equals("ingest-typed")
                        ? fixture::ingestTyped
                        : ingest ? fixture::ingest : fixture::read;
        for (int i = 0; i < 4; i++) fixture.check(operation.run(i));
        // Calibrate before warmup: initialization must not disturb measured guest code.
        ThreadMXBean bean = memory || smoke ? allocationCounter() : null;
        if (peak) {
          // An initial compilation can be a deoptimization stub until all runtime profiles have
          // been exercised. Execute the actual measurement loop, then require a stable last tier.
          boolean stable = false;
          for (int attempt = 0; attempt < 8 && !stable; attempt++) {
            measure(operation, 1024, fixture.expected);
            fixture.target.compile(true);
            fixture.target.waitForCompilation();
            measure(operation, 1024, fixture.expected);
            stable = fixture.target.isValidLastTier();
          }
          if (!stable) throw new AssertionError("Peak warmup did not stabilize");
        }
        fixture.tier(peak);
        if (smoke) {
          measure(operation, 128, fixture.expected);
          fixture.tier(peak);
          if (ImageInfo.inImageRuntimeCode()
              && workload.equals("proto-scalar")
              && (engine.equals("owned") || engine.equals("buffa"))
              && ingest) {
            var counter = Objects.requireNonNull(bean);
            long thread = Thread.currentThread().threadId();
            long before = counter.getThreadAllocatedBytes(thread);
            measure(operation, 8, fixture.expected);
            double bytes = (counter.getThreadAllocatedBytes(thread) - before) / (8.0 * ROWS);
            double budget =
                engine.equals("buffa")
                    ? (stage.equals("ingest") ? 128 : 96)
                    : (stage.equals("ingest") ? 96 : 64);
            if (bytes < 0 || bytes > budget)
              throw new AssertionError(
                  "Compact scalar allocation budget: " + bytes + " > " + budget);
            System.out.println(
                "ALLOCATION compact-scalar " + engine + " " + stage + " bytes/message=" + bytes);
          }
          if (ImageInfo.inImageRuntimeCode()
              && !ingest
              && !stage.equals("serialize")
              && (!stage.startsWith("project-") || (engine.equals("generated-owned") && peak))) {
            var counter = Objects.requireNonNull(bean);
            long thread = Thread.currentThread().threadId();
            long before = counter.getThreadAllocatedBytes(thread);
            measure(operation, 128, fixture.expected);
            double bytes = (counter.getThreadAllocatedBytes(thread) - before) / (128.0 * ROWS);
            // Includes the boxed per-batch checksum and guest call arguments. Primitive
            // field boxing would exceed this allowance by at least 16 bytes per row.
            if (bytes < 0 || bytes > 4)
              throw new AssertionError("Retained-read allocation budget: " + bytes);
          }
          return;
        }
        double[] samples = new double[3];
        int batch = ingest ? 8 : 128;
        if (memory) {
          measure(operation, batch, fixture.expected);
          long thread = Thread.currentThread().threadId();
          for (int s = 0; s < samples.length; s++) {
            fixture.tier(peak);
            long before = Objects.requireNonNull(bean).getThreadAllocatedBytes(thread);
            measure(operation, batch, fixture.expected);
            long allocated = bean.getThreadAllocatedBytes(thread) - before;
            if (allocated < 0) throw new AssertionError("Allocation counter regressed");
            samples[s] = (double) allocated / (batch * ROWS);
            fixture.tier(peak);
          }
        } else {
          long until = System.nanoTime() + 100_000_000;
          while (System.nanoTime() < until) measure(operation, batch, fixture.expected);
          for (int s = 0; s < samples.length; s++) {
            fixture.tier(peak);
            long count = 0, start = System.nanoTime(), elapsed;
            do {
              measure(operation, batch, fixture.expected);
              count += batch;
              elapsed = System.nanoTime() - start;
            } while (elapsed < 50_000_000);
            samples[s] = (double) elapsed / (count * ROWS);
            fixture.tier(peak);
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
    final boolean guestRead;
    final byte[][] wire = new byte[ROWS][];
    final String[] json = new String[ROWS];
    final ByteString[] immutableWire = new ByteString[ROWS];
    final Object[] rows = new Object[ROWS];
    final @Nullable TypedMessage[] messages = new TypedMessage[ROWS];
    final @Nullable WireInput[] inputs = new WireInput[ROWS];
    final JsonFormat.Parser jsonParser = JsonFormat.parser();
    final ProtoPlan plan;
    final StaticLayout layout;
    final @Nullable BuffaWire decoder;
    final @Nullable BuffaPlan nativePlan;
    final OptimizedCallTarget target;
    final @Nullable ProjectRoot projectRoot;
    final long expected;
    final @Nullable Projection generated;

    Fixture(String workload, String engine, boolean guestRead) throws Exception {
      this(workload, engine, guestRead, false, false, false);
    }

    Fixture(
        String workload,
        String engine,
        boolean guestRead,
        boolean generatedReads,
        boolean serialize,
        boolean project)
        throws Exception {
      this.guestRead = guestRead;
      this.workload = workload;
      this.engine = engine;
      boolean bytes = workload.equals("proto-bytes");
      boolean mixed = workload.startsWith("proto-mixed");
      boolean jdbc = workload.endsWith("int4");
      var language = ComparisonLanguage.current();
      plan =
          ComparisonContracts.create(language, Limits.DEFAULT, 16, 1 << 20)
              .type(
                  mixed
                      ? "google.protobuf.Type"
                      : bytes ? "google.protobuf.BytesValue" : "google.protobuf.Timestamp");
      layout =
          new StaticLayout(
              language, Schema.of(Field.integer("id", 32, true, false, false)), Limits.DEFAULT);
      decoder = engine.equals("buffa") ? BuffaWire.open(BuffaWire.Mode.NATIVE, library()) : null;
      nativePlan = decoder == null ? null : decoder.compile(plan, 65536);
      int kind = mixed ? 3 : bytes ? 1 : jdbc ? 2 : 0;
      generated =
          engine.equals("generated-owned")
              ? new OwnedProjection(plan, kind)
              : generatedReads ? new GeneratedProjection(plan, kind) : null;
      projectRoot = project ? new ProjectRoot(plan, engine.equals("stock")) : null;
      RootNode root =
          projectRoot != null
              ? projectRoot
              : engine.equals("generated-owned")
                  ? ownedReadRoot(kind)
                  : generated != null
                      ? new GeneratedReadRoot(generated)
                      : engine.startsWith("stock") ? new StockReadRoot(kind) : new ReadRoot(kind);
      target = (OptimizedCallTarget) root.getCallTarget();
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
        } else if (mixed) {
          var builder =
              com.google.protobuf.Type.newBuilder()
                  .setName("user-" + i + "-λ")
                  .setSyntax(com.google.protobuf.Syntax.SYNTAX_PROTO3)
                  .addOneofs("identity-" + i)
                  .addOneofs("contact-" + i)
                  .setSourceContext(
                      com.google.protobuf.SourceContext.newBuilder().setFileName("user.proto"));
          for (int j = 0; j < 4 + i % 5; j++) {
            builder.addFields(
                com.google.protobuf.Field.newBuilder()
                    .setName("field-" + j)
                    .setNumber(j + 1)
                    .setKind(com.google.protobuf.Field.Kind.TYPE_INT64)
                    .setCardinality(com.google.protobuf.Field.Cardinality.CARDINALITY_REPEATED)
                    .setPacked(true));
          }
          var value = builder.build();
          wire[i] = value.toByteArray();
          sum +=
              value.getName().length()
                  + value.getFieldsCount()
                  + value.getOneofsCount()
                  + value.getSyntaxValue();
          if (workload.equals("proto-mixed-full")) {
            // Independent fixture checksum: all populated nested and repeated fields.
            sum += "user.proto".length() + ("identity-" + i).length() + ("contact-" + i).length();
            for (int j = 0; j < 4 + i % 5; j++) sum += ("field-" + j).length() + j + 1 + 3 + 3 + 1;
          }
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
        if (engine.equals("stock-aliased")) immutableWire[i] = ByteString.copyFrom(wire[i]);
        if (decoder != null) {
          inputs[i] = decoder.allocate(wire[i].length).put(wire[i]);
          messages[i] = parseNative(i);
          rows[i] = Objects.requireNonNull(messages[i]).row();
        } else rows[i] = parse(i);
      }
      expected = serialize ? Arrays.stream(wire).mapToLong(w -> w.length).sum() : sum;
      check(project ? project(0) : serialize ? serialize(0) : read(0));
    }

    Object parse(int i) throws Exception {
      if (workload.endsWith("int4")) {
        long value =
            workload.equals("mysql-int4")
                ? MysqlTextValueDecoder.getInt(wire[i], 0, wire[i].length)
                : ByteConverter.int4(wire[i], 0);
        return engine.startsWith("stock") ? value : layout.createLong(value);
      }
      if (generated instanceof OwnedProjection owned) return owned.decode(wire[i]);
      if (engine.equals("owned"))
        return workload.equals("timestamp-json") ? plan.parseJson(json[i]) : plan.decode(wire[i]);
      if (workload.equals("proto-bytes")) {
        if (engine.equals("stock-aliased")) {
          var input = immutableWire[i].newCodedInput();
          input.enableAliasing(true);
          return BytesValue.parseFrom(input);
        }
        return BytesValue.parseFrom(wire[i]);
      }
      if (workload.equals("timestamp-json")) {
        var builder = Timestamp.newBuilder();
        jsonParser.merge(json[i], builder);
        return builder.build();
      }
      if (workload.startsWith("proto-mixed")) return com.google.protobuf.Type.parseFrom(wire[i]);
      return Timestamp.parseFrom(wire[i]);
    }

    TypedMessage parseNative(int i) {
      return workload.equals("timestamp-json")
          ? Objects.requireNonNull(nativePlan).parseJson(json[i], 1 << 20)
          : Objects.requireNonNull(nativePlan).decode(Objects.requireNonNull(inputs[i]), 1 << 20);
    }

    long project(int rotation) {
      return guestRead
          ? (Long) target.call(wire, rotation)
          : Objects.requireNonNull(projectRoot).project(wire, rotation);
    }

    long serialize(int rotation) {
      long sum = 0;
      for (int n = 0; n < ROWS; n++) {
        int i = (n + rotation) & (ROWS - 1);
        byte[] encoded;
        if (nativePlan != null) encoded = Objects.requireNonNull(messages[i]).encode();
        else if (rows[i] instanceof dev.elide.truffledata.protobuf.GeneratedMessage message)
          encoded = message.encode();
        else if (rows[i] instanceof ProtoRow row) encoded = plan.encode(row);
        else encoded = ((com.google.protobuf.MessageLite) rows[i]).toByteArray();
        retained = encoded;
        sum += encoded.length;
      }
      return sum;
    }

    long ingest(int rotation) throws Exception {
      return ingest(rotation, false);
    }

    long ingestTyped(int rotation) throws Exception {
      return ingest(rotation, true);
    }

    private long ingest(int rotation, boolean typed) throws Exception {
      long sum = 0;
      for (int n = 0; n < ROWS; n++) {
        int i = (n + rotation) & (ROWS - 1);
        if (nativePlan != null) {
          try (var message = parseNative(i)) {
            retained = message;
            sum += readOne(message.row(), typed);
          }
        } else {
          Object row = parse(i);
          retained = row;
          sum += readOne(row, typed);
        }
      }
      return sum;
    }

    long readOne(Object row, boolean typed)
        throws com.oracle.truffle.api.nodes.UnexpectedResultException {
      if (workload.equals("proto-mixed-full")) return readAll(row);
      if (generated != null) return generated.read((ProtoAccess) row);
      if (engine.startsWith("stock")) {
        if (row instanceof com.google.protobuf.Type t)
          return t.getName().length()
              + t.getFieldsCount()
              + t.getOneofsCount()
              + t.getSyntaxValue();
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
      if (workload.startsWith("proto-mixed")) {
        return ((String) data.read(row, 0)).length()
            + arraySize(data.read(row, 1))
            + arraySize(data.read(row, 2))
            + (typed ? data.readLong(row, 5) : (Long) data.read(row, 5));
      }
      long value = typed ? data.readLong(row, 0) : (Long) data.read(row, 0);
      return workload.endsWith("int4")
          ? value
          : value + (typed ? data.readLong(row, 1) : (Long) data.read(row, 1));
    }

    /** Consume every populated field, including nested strings, without allocating iterators. */
    long readAll(Object row) {
      if (engine.startsWith("stock")) {
        var value = (com.google.protobuf.Type) row;
        int count = value.getFieldsCount(), oneofs = value.getOneofsCount();
        long sum =
            value.getName().length()
                + count
                + oneofs
                + value.getSyntaxValue()
                + value.getSourceContext().getFileName().length();
        for (int i = 0; i < count; i++) {
          var field = value.getFields(i);
          sum +=
              field.getName().length()
                  + field.getNumber()
                  + field.getKindValue()
                  + field.getCardinalityValue()
                  + (field.getPacked() ? 1 : 0);
        }
        for (int i = 0; i < oneofs; i++) sum += value.getOneofs(i).length();
        return sum;
      }
      if (engine.equals("generated-owned")) {
        var value = (ComparisonContracts.Message_google$protobuf$Type) row;
        var fields = value.get_fields();
        var oneofs = value.get_oneofs();
        var source =
            (ComparisonContracts.Message_google$protobuf$SourceContext) value.get_source_context();
        long sum =
            value.get_name().length()
                + fields.size()
                + oneofs.size()
                + value.get_syntax()
                + source.get_file_name().length();
        for (int i = 0; i < fields.size(); i++) {
          var field = (ComparisonContracts.Message_google$protobuf$Field) fields.get(i);
          sum +=
              field.get_name().length()
                  + field.get_number()
                  + field.get_kind()
                  + field.get_cardinality()
                  + (field.get_packed() ? 1 : 0);
        }
        for (int i = 0; i < oneofs.size(); i++) sum += ((String) oneofs.get(i)).length();
        return sum;
      }
      var data = DataLibrary.factory().getUncached();
      Object fields = data.read(row, 1), oneofs = data.read(row, 2);
      int count = Math.toIntExact(arraySize(fields)), groups = Math.toIntExact(arraySize(oneofs));
      long sum =
          ((String) data.read(row, 0)).length()
              + count
              + groups
              + (Long) data.read(row, 5)
              + ((String) data.read(data.read(row, 4), 0)).length();
      for (int i = 0; i < count; i++) {
        Object field = arrayElement(fields, i);
        sum +=
            ((String) data.read(field, 3)).length()
                + (Long) data.read(field, 2)
                + (Long) data.read(field, 0)
                + (Long) data.read(field, 1)
                + ((Boolean) data.read(field, 6) ? 1 : 0);
      }
      for (int i = 0; i < groups; i++) sum += ((String) arrayElement(oneofs, i)).length();
      return sum;
    }

    static Object arrayElement(Object value, int index) {
      try {
        return com.oracle.truffle.api.interop.InteropLibrary.getUncached()
            .readArrayElement(value, index);
      } catch (com.oracle.truffle.api.interop.InteropException failure) {
        throw new AssertionError(failure);
      }
    }

    static long arraySize(Object value) {
      try {
        return com.oracle.truffle.api.interop.InteropLibrary.getUncached().getArraySize(value);
      } catch (com.oracle.truffle.api.interop.UnsupportedMessageException e) {
        throw new AssertionError(e);
      }
    }

    long read(int rotation) {
      if (engine.equals("generated-owned") && !guestRead) {
        long sum = 0;
        if (workload.equals("proto-bytes")) {
          for (int n = 0; n < ROWS; n++)
            sum +=
                ((ComparisonContracts.Message_google$protobuf$BytesValue)
                        rows[(n + rotation) & (ROWS - 1)])
                    .get_value()
                    .size();
        } else if (workload.startsWith("proto-mixed")) {
          for (int n = 0; n < ROWS; n++) {
            Object row = rows[(n + rotation) & (ROWS - 1)];
            sum +=
                workload.equals("proto-mixed-full")
                    ? readAll(row)
                    : ownedMixedProjection((ComparisonContracts.Message_google$protobuf$Type) row);
          }
        } else {
          for (int n = 0; n < ROWS; n++) {
            var row =
                (ComparisonContracts.Message_google$protobuf$Timestamp)
                    rows[(n + rotation) & (ROWS - 1)];
            sum += row.get_seconds() + row.get_nanos();
          }
        }
        return sum;
      }
      if (generated != null && !guestRead) {
        long sum = 0;
        for (int n = 0; n < ROWS; n++)
          sum += generated.read((ProtoAccess) rows[(n + rotation) & (ROWS - 1)]);
        return sum;
      }
      if (workload.equals("proto-mixed-full")) {
        long sum = 0;
        for (int n = 0; n < ROWS; n++) sum += readAll(rows[(n + rotation) & (ROWS - 1)]);
        return sum;
      }
      if (guestRead || !engine.startsWith("stock")) return (Long) target.call(rows, rotation);
      long sum = 0;
      if (workload.equals("proto-bytes")) {
        for (int n = 0; n < ROWS; n++)
          sum += ((BytesValue) rows[(n + rotation) & (ROWS - 1)]).getValue().size();
      } else if (workload.startsWith("proto-mixed")) {
        for (int n = 0; n < ROWS; n++) {
          var value = (com.google.protobuf.Type) rows[(n + rotation) & (ROWS - 1)];
          sum +=
              value.getName().length()
                  + value.getFieldsCount()
                  + value.getOneofsCount()
                  + value.getSyntaxValue();
        }
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

  /** Stock getters under the same guest call, loop, checksum, and tier checks as TD reads. */
  private static final class StockReadRoot extends RootNode {
    private final int kind;
    private final com.oracle.truffle.api.profiles.ValueProfile bytesClass =
        com.oracle.truffle.api.profiles.ValueProfile.createClassProfile();

    StockReadRoot(int kind) {
      super(ComparisonLanguage.current());
      this.kind = kind;
    }

    // Stock collection getters are not SVM runtime-compilation safe under the shared
    // blocklist. Only this mixed projection crosses a boundary; primitive getters stay inline.
    @TruffleBoundary
    private static long mixedProjection(com.google.protobuf.Type value) {
      return value.getName().length()
          + value.getFieldsCount()
          + value.getOneofsCount()
          + value.getSyntaxValue();
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object[] rows = (Object[]) frame.getArguments()[0];
      int rotation = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int n = 0; n < ROWS; n++) {
        Object row = rows[(n + rotation) & (ROWS - 1)];
        if (kind == 3) {
          sum += mixedProjection((com.google.protobuf.Type) row);
        } else if (kind == 1) {
          sum += bytesClass.profile(((BytesValue) row).getValue()).size();
        } else if (kind == 2) {
          sum += (Long) row;
        } else {
          var value = (Timestamp) row;
          sum += value.getSeconds() + value.getNanos();
        }
      }
      return sum;
    }
  }

  private static final class ProjectRoot extends RootNode {
    private final boolean stock;
    private final ComparisonContracts.Access_google$protobuf$Timestamp access;

    ProjectRoot(ProtoPlan plan, boolean stock) {
      super(ComparisonLanguage.current());
      this.stock = stock;
      access = new ComparisonContracts.Access_google$protobuf$Timestamp(plan);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return project((byte[][]) frame.getArguments()[0], (Integer) frame.getArguments()[1]);
    }

    long project(byte[][] wire, int rotation) {
      long sum = 0;
      for (int n = 0; n < ROWS; n++) {
        byte[] input = wire[(n + rotation) & (ROWS - 1)];
        if (stock) sum += stockProject(input);
        else {
          var row = access.decodeForProjection(input);
          sum += row.get_seconds() + row.get_nanos();
        }
      }
      return sum;
    }

    private static long stockProject(byte[] wire) {
      try {
        var row = Timestamp.parseFrom(wire);
        return row.getSeconds() + row.getNanos();
      } catch (com.google.protobuf.InvalidProtocolBufferException invalid) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw invalidStockInput(invalid);
      }
    }

    @TruffleBoundary
    private static AssertionError invalidStockInput(
        com.google.protobuf.InvalidProtocolBufferException invalid) {
      return new AssertionError(invalid);
    }
  }

  private interface Projection {
    long read(ProtoAccess row);
  }

  private static final class OwnedProjection implements Projection {
    private final int kind;
    private final ComparisonContracts.@Nullable Access_google$protobuf$Timestamp timestamp;
    private final ComparisonContracts.@Nullable Access_google$protobuf$BytesValue bytes;
    private final ComparisonContracts.@Nullable Access_google$protobuf$Type mixed;

    OwnedProjection(ProtoPlan plan, int kind) {
      this.kind = kind;
      timestamp = kind == 0 ? new ComparisonContracts.Access_google$protobuf$Timestamp(plan) : null;
      bytes = kind == 1 ? new ComparisonContracts.Access_google$protobuf$BytesValue(plan) : null;
      mixed = kind == 3 ? new ComparisonContracts.Access_google$protobuf$Type(plan) : null;
    }

    ProtoAccess decode(byte[] wire) {
      if (kind == 3) return Objects.requireNonNull(mixed).decode(wire);
      return kind == 0
          ? Objects.requireNonNull(timestamp).decode(wire)
          : Objects.requireNonNull(bytes).decode(wire);
    }

    @Override
    public long read(ProtoAccess row) {
      if (kind == 0) {
        var value = (ComparisonContracts.Message_google$protobuf$Timestamp) row;
        return value.get_seconds() + value.get_nanos();
      }
      if (kind == 3)
        return ownedMixedProjection((ComparisonContracts.Message_google$protobuf$Type) row);
      return ((ComparisonContracts.Message_google$protobuf$BytesValue) row).get_value().size();
    }
  }

  private static final class GeneratedProjection implements Projection {
    private final int kind;
    private final ComparisonContracts.@Nullable Access_google$protobuf$Timestamp timestamp;
    private final ComparisonContracts.@Nullable Access_google$protobuf$BytesValue bytes;
    private final ComparisonContracts.@Nullable Access_google$protobuf$Type mixed;

    GeneratedProjection(ProtoPlan plan, int kind) {
      this.kind = kind;
      timestamp = kind == 0 ? new ComparisonContracts.Access_google$protobuf$Timestamp(plan) : null;
      bytes = kind == 1 ? new ComparisonContracts.Access_google$protobuf$BytesValue(plan) : null;
      mixed = kind == 3 ? new ComparisonContracts.Access_google$protobuf$Type(plan) : null;
    }

    public long read(ProtoAccess row) {
      if (kind == 0) {
        var access = Objects.requireNonNull(timestamp);
        return access.get_seconds(row) + access.get_nanos(row);
      }
      if (kind == 1) return Objects.requireNonNull(bytes).size_value(row);
      var access = Objects.requireNonNull(mixed);
      return access.get_name(row).length()
          + access.size_fields(row)
          + access.size_oneofs(row)
          + access.get_syntax(row);
    }
  }

  private static long ownedMixedProjection(ComparisonContracts.Message_google$protobuf$Type value) {
    return value.get_name().length()
        + value.get_fields().size()
        + value.get_oneofs().size()
        + value.get_syntax();
  }

  /** Small shape-specific roots keep frame-bearing entry points eligible for SVM inlining. */
  private static RootNode ownedReadRoot(int kind) {
    return switch (kind) {
      case 0 -> new OwnedScalarReadRoot();
      case 1 -> new OwnedBytesReadRoot();
      case 3 -> new OwnedMixedReadRoot();
      default -> throw new IllegalArgumentException("Unknown generated shape");
    };
  }

  private static final class OwnedScalarReadRoot extends RootNode {
    OwnedScalarReadRoot() {
      super(ComparisonLanguage.current());
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object[] rows = (Object[]) frame.getArguments()[0];
      int rotation = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int n = 0; n < ROWS; n++) {
        var value =
            (ComparisonContracts.Message_google$protobuf$Timestamp)
                rows[(n + rotation) & (ROWS - 1)];
        sum += value.get_seconds() + value.get_nanos();
      }
      return sum;
    }
  }

  private static final class OwnedBytesReadRoot extends RootNode {
    OwnedBytesReadRoot() {
      super(ComparisonLanguage.current());
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object[] rows = (Object[]) frame.getArguments()[0];
      int rotation = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int n = 0; n < ROWS; n++) {
        var value =
            (ComparisonContracts.Message_google$protobuf$BytesValue)
                rows[(n + rotation) & (ROWS - 1)];
        sum += value.get_value().size();
      }
      return sum;
    }
  }

  private static final class OwnedMixedReadRoot extends RootNode {
    OwnedMixedReadRoot() {
      super(ComparisonLanguage.current());
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object[] rows = (Object[]) frame.getArguments()[0];
      int rotation = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int n = 0; n < ROWS; n++) {
        var value =
            (ComparisonContracts.Message_google$protobuf$Type) rows[(n + rotation) & (ROWS - 1)];
        sum +=
            value.get_name().length()
                + value.get_fields().size()
                + value.get_oneofs().size()
                + value.get_syntax();
      }
      return sum;
    }
  }

  private static final class GeneratedReadRoot extends RootNode {
    private final Projection projection;
    private final com.oracle.truffle.api.profiles.ValueProfile rowClass =
        com.oracle.truffle.api.profiles.ValueProfile.createClassProfile();

    GeneratedReadRoot(Projection projection) {
      super(ComparisonLanguage.current());
      this.projection = projection;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object[] rows = (Object[]) frame.getArguments()[0];
      int rotation = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int n = 0; n < ROWS; n++)
        sum += projection.read((ProtoAccess) rowClass.profile(rows[(n + rotation) & (ROWS - 1)]));
      return sum;
    }
  }

  private static final class ReadRoot extends RootNode {
    private final int kind;
    @Child private ReadLongNode first = ReadLongNode.create();
    @Child private ReadLongNode second = ReadLongNode.create();
    @Child private ReadLengthNode length = ReadLengthNode.create();
    @Child private ReadLengthNode fieldsLength = ReadLengthNode.create();
    @Child private ReadLengthNode oneofsLength = ReadLengthNode.create();

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
            kind == 3
                ? length.execute(row, 0)
                    + fieldsLength.execute(row, 1)
                    + oneofsLength.execute(row, 2)
                    + first.execute(row, 5)
                : kind == 1
                    ? length.execute(row, 0)
                    : first.execute(row, 0) + (kind == 0 ? second.execute(row, 1) : 0);
      }
      return sum;
    }
  }
}
