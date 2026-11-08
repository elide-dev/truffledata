package dev.elide.truffledata.comparison;

import com.google.protobuf.SourceContext;
import com.google.protobuf.Syntax;
import com.google.protobuf.Type;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.sun.management.ThreadMXBean;
import dev.elide.truffledata.*;
import dev.elide.truffledata.buffa.*;
import dev.elide.truffledata.generated.ComparisonContracts;
import dev.elide.truffledata.protobuf.Projection;
import dev.elide.truffledata.protobuf.ProtoPlan;
import dev.elide.truffledata.protobuf.SlabLimits;
import dev.elide.truffledata.protobuf.SlabMessage;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Consumer-level schema audit; all TD representations execute identical shared access nodes. */
@NullMarked
@SuppressWarnings("removal")
public final class ConsumerComparison {
  private static final int ROWS = 32;
  private static final long COMPILED = 1L << 60;
  private static volatile Object retained = new Object();
  private static volatile long sink;
  static Runnable beforeSamples = () -> {};
  static long sampleNanos = 50_000_000;

  public static void main(String[] args) throws Exception {
    if (args.length == 1 && args[0].equals("--smoke")) {
      for (String engine : ENGINES)
        for (String mode : MODES)
          for (String stage : STAGES) {
            if (!cell(engine, mode, stage)) continue;
            System.out.println("SMOKE " + engine + " " + mode + " " + stage);
            run(engine, mode, stage, "time", true);
          }
      return;
    }
    if (args.length != 4)
      throw new IllegalArgumentException(
          "engine sparse|full|holdout|holdout-sparse|large stage time|memory");
    run(args[0], args[1], args[2], args[3], false);
  }

  static final String[] ENGINES = {
    "stock",
    "stock-interop",
    "generated-owned",
    "slab",
    "slab-native",
    "slab-projected",
    "slab-lazy",
    "slab-session",
    "slab-session-projected"
  };
  static final String[] MODES = {"sparse", "full", "holdout", "holdout-sparse", "large"};
  static final String[] STAGES = {
    "decode", "resident", "heap", "detach", "first", "interpret", "peak"
  };

  /** Holdout and large-message data sets measure a deliberately smaller matrix. */
  static boolean cell(String mode, String stage) {
    return switch (mode) {
      case "holdout", "holdout-sparse" -> stage.equals("resident") || stage.equals("peak");
      case "large" -> stage.equals("decode") || stage.equals("resident") || stage.equals("peak");
      default -> true;
    };
  }

  static boolean cell(String engine, String mode, String stage) {
    return cell(mode, stage)
        && (!sessionEngine(engine) || stage.equals("decode") || stage.equals("resident"))
        && (!engine.equals("stock-interop") || stage.equals("interpret") || stage.equals("peak"))
        && (!(engine.equals("slab-projected") || engine.equals("slab-session-projected"))
            || mode.endsWith("sparse"));
  }

  private static boolean sessionEngine(String engine) {
    return engine.startsWith("slab-session");
  }

  private static boolean owningSlabEngine(String engine) {
    return engine.startsWith("slab") && !sessionEngine(engine);
  }

  private static boolean projectedEngine(String engine) {
    return engine.equals("slab-projected")
        || engine.equals("slab-lazy")
        || engine.equals("slab-session-projected");
  }

  private static void run(String engine, String mode, String stage, String metric, boolean smoke)
      throws Exception {
    if (!Arrays.asList(ENGINES).contains(engine)
        || !Arrays.asList(MODES).contains(mode)
        || !Arrays.asList(STAGES).contains(stage)
        || !cell(engine, mode, stage)
        || !Arrays.asList("time", "memory").contains(metric))
      throw new IllegalArgumentException("Unknown consumer cell");
    // Decode-only cells run no guest code; their parsing is host code on both runtimes.
    boolean peak = !stage.equals("interpret") && !stage.equals("decode");
    // HotSpot needs much longer host warmup than a closed-world image; both are recorded.
    long warmupNanos = Long.getLong("consumer.warmupMillis", 100) * 1_000_000;
    long sampleWindow = Long.getLong("consumer.sampleMillis", sampleNanos / 1_000_000) * 1_000_000;
    try (var context =
        Context.newBuilder("td-compare")
            .allowExperimentalOptions(true)
            .allowHostAccess(HostAccess.ALL)
            .option("engine.Compilation", Boolean.toString(peak))
            .option("engine.BackgroundCompilation", "false")
            .option("engine.CompilationFailureAction", "Throw")
            .option("compiler.TreatPerformanceWarningsAsErrors", "all")
            .option("compiler.MaximumGraalGraphSize", "10000")
            .build()) {
      context.initialize("td-compare");
      context.enter();
      try (var fixture = new Fixture(engine, mode, stage)) {
        var counter = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!counter.isThreadAllocatedMemorySupported())
          throw new AssertionError("Allocation counter");
        counter.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long before = counter.getThreadAllocatedBytes(thread);
        retained = new byte[1 << 20];
        if (before < 0 || counter.getThreadAllocatedBytes(thread) - before < 1 << 20)
          throw new AssertionError("Allocation calibration");
        for (int i = 0; i < 128; i++) fixture.operation();
        if (peak) {
          for (int attempt = 0; attempt < 8; attempt++) {
            fixture.target.compile(true);
            fixture.target.waitForCompilation();
            for (int i = 0; i < 128; i++) fixture.operation();
            if (fixture.target.isValidLastTier()) break;
          }
        }
        fixture.tier(peak);
        if (NodeUtil.countNodes(fixture.root) > 256)
          throw new AssertionError("Consumer AST budget");
        if (smoke) {
          fixture.operation();
          fixture.tier(peak);
          fixture.closedReadCheck();
          return;
        }
        long until = System.nanoTime() + warmupNanos;
        while (System.nanoTime() < until) fixture.operation();
        fixture.tier(peak);
        beforeSamples.run();
        double[] samples = new double[3];
        for (int s = 0; s < samples.length; s++) {
          long count = 0, total = 0, start = System.nanoTime();
          do {
            // First-access preparation and release deliberately live outside its timed/counted
            // window.
            if (stage.equals("first")) fixture.prepare();
            long from =
                metric.equals("memory")
                    ? counter.getThreadAllocatedBytes(thread)
                    : System.nanoTime();
            if (stage.equals("first")) fixture.consume();
            else fixture.operation();
            total +=
                (metric.equals("memory")
                        ? counter.getThreadAllocatedBytes(thread)
                        : System.nanoTime())
                    - from;
            if (stage.equals("first")) fixture.release();
            count++;
          } while (metric.equals("memory") ? count < 64 : System.nanoTime() - start < sampleWindow);
          samples[s] = (double) total / (count * ROWS);
          fixture.tier(peak);
        }
        System.out.printf(
            Locale.ROOT,
            "{\"engine\":\"%s\",\"mode\":\"%s\",\"stage\":\"%s\",\"runtime\":\"%s\",\"rows\":%d,\"checksum\":%d,\"guest_compiled\":%s,\"shared_access\":%s,\"stock_boundary\":%s,\"native_index_retained\":%s,\"input_bytes_per_row\":%.3f,\"copied_input_bytes_per_row\":%.3f,\"decoded_native_index_capacity_bytes_per_row\":%.3f,\"decoded_native_result_header_bytes_per_row\":%d,\"slab_bytes_per_row\":%.3f,\"retained_slab_capacity_bytes_per_row\":%.3f,\"session_arena_capacity_bytes_per_row\":%.3f,\"retained_input_bytes_per_row\":%.3f,\"host_interop\":%s,\"host_interop_boundary\":%s,\"producer_backend\":\"%s\",\"native_input\":\"%s\",\"native_transient_peak_bytes\":null,\"native_allocation_count\":null,\"%s\":%s}%n",
            engine,
            mode,
            stage,
            ImageInfo.inImageRuntimeCode() ? "svm" : "jvm",
            ROWS,
            fixture.expected,
            peak,
            !engine.equals("stock") && !stage.equals("decode"),
            engine.equals("stock") && !stage.equals("decode"),
            false,
            fixture.inputBytes / (double) ROWS,
            fixture.copiedSlabInputBytes / (double) ROWS,
            fixture.indexBytes / (double) ROWS,
            fixture.headerBytes,
            fixture.slabBytes / (double) ROWS,
            fixture.retainedSlabBytes / (double) ROWS,
            fixture.sessionArenaBytes / (double) ROWS,
            fixture.retainedInputBytes / (double) ROWS,
            engine.equals("stock-interop"),
            engine.equals("stock-interop") && ImageInfo.inImageCode(),
            fixture.producerBackend(),
            engine.equals("slab-native") ? (stage.equals("heap") ? "heap" : "native") : "",
            metric.equals("memory") ? "java_bytes_per_row" : "ns_per_row",
            Arrays.toString(samples));
      } finally {
        context.leave();
      }
    }
  }

  private static @Nullable Path library() throws Exception {
    if (ImageInfo.inImageCode()) return null;
    String explicit = System.getenv("TRUFFLEDATA_BUFFA_LIBRARY");
    if (explicit != null) return Path.of(explicit);
    var path =
        Path.of(
            java.util.Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
            java.util.Objects.requireNonNull(System.getenv("TEST_WORKSPACE")),
            "protobuf-buffa/native");
    try (var files = Files.list(path)) {
      return files
          .filter(p -> p.toString().endsWith(".so") || p.toString().endsWith(".dylib"))
          .findFirst()
          .orElseThrow();
    }
  }

  private static final class Fixture implements AutoCloseable {
    final String engine, stage;
    final byte[][] wires = new byte[ROWS][];
    final @Nullable WireInput[] inputs;
    final @Nullable SlabSession[] sessions = new SlabSession[ROWS];
    final Object[] rows = new Object[ROWS];
    final BuffaWire decoder;
    final BuffaPlan nativePlan;
    final @Nullable BuffaPlan selectedPlan;
    final @Nullable Projection selection;
    final ProtoPlan plan;
    final ComparisonContracts.Access_google$protobuf$Type generated;
    final ConsumerRoot root;
    final OptimizedCallTarget target;
    final long expected;
    long inputBytes,
        indexBytes,
        headerBytes,
        slabBytes,
        retainedSlabBytes,
        copiedSlabInputBytes,
        sessionArenaBytes,
        retainedInputBytes;
    int rotation;

    Fixture(String engine, String mode, String stage) throws Exception {
      this.engine = engine;
      this.stage = stage;
      var model =
          ComparisonContracts.create(ComparisonLanguage.current(), Limits.DEFAULT, 16, 1 << 20);
      plan = model.type("google.protobuf.Type");
      generated = new ComparisonContracts.Access_google$protobuf$Type(plan);
      // Native setup is excluded for every engine; keeping it identical also controls image
      // reachability.
      decoder = BuffaWire.open(BuffaWire.Mode.NATIVE, library());
      nativePlan = decoder.compile(plan, 65536);
      Projection wanted =
          Projection.builder(plan)
              .select("name", "oneofs", "source_context", "syntax")
              .selectType("google.protobuf.SourceContext", "file_name")
              .build();
      if (engine.equals("slab-lazy") || engine.equals("slab-session-projected"))
        wanted = wanted.lazyChildren();
      selection = projectedEngine(engine) ? wanted : null;
      selectedPlan = selection == null ? null : nativePlan.project(selection);
      inputs = new WireInput[ROWS];
      root =
          engine.equals("stock")
              ? new StockRoot(!mode.endsWith("sparse"))
              : new SharedRoot(!mode.endsWith("sparse"));
      target = (OptimizedCallTarget) root.getCallTarget();
      long sum = 0;
      for (int i = 0; i < ROWS; i++) {
        var b = Type.newBuilder().setName("catalog-" + i + "-λ").setSyntax(Syntax.SYNTAX_PROTO3);
        int count =
            switch (mode) {
              case "holdout", "holdout-sparse" -> 12 + i % 13;
              // About 50 KB per message: a throughput cohort for large repeated payloads.
              case "large" -> 2048 + (i % 8) * 64;
              default -> 3 + i % 6;
            };
        // Missing source_context is a real absent record, represented as Missing by both TD routes.
        if (i % 3 != 0)
          b.setSourceContext(SourceContext.newBuilder().setFileName("schema-" + i + ".proto"));
        b.addOneofs("identity");
        if ((i & 1) == 0) b.addOneofs("contact-😀");
        for (int j = 0; j < count; j++) {
          b.addFields(
              com.google.protobuf.Field.newBuilder()
                  .setName("column-" + j + (mode.startsWith("holdout") ? "-λ😀" : ""))
                  .setNumber(j + 1)
                  .setKind(com.google.protobuf.Field.Kind.TYPE_INT64)
                  .setCardinality(com.google.protobuf.Field.Cardinality.CARDINALITY_REPEATED)
                  .setPacked((i + j) % 3 != 0));
        }
        Type value = b.build();
        wires[i] = value.toByteArray();
        inputBytes += wires[i].length;
        inputs[i] = decoder.allocate(wires[i].length).put(wires[i]);
        sum += expected(value, !mode.endsWith("sparse"));
      }
      expected = sum;
      if (sessionEngine(engine))
        for (int i = 0; i < ROWS; i++) {
          var selected = selectedPlan == null ? nativePlan : selectedPlan;
          sessions[i] = selected.newSession(1 << 20);
        }
      long copiedBefore = decoder.copiedSlabInputBytes();
      prepare();
      if (!stage.equals("decode")) consume();
      for (int i = 0; i < ROWS; i++) {
        if (rows[i] instanceof SlabMessage slab) {
          slabBytes += slab.slabBytes();
          retainedSlabBytes += slab.retainedSlabBytes();
          retainedInputBytes += slab.retainedInputBytes();
        }
        var session = sessions[i];
        if (session != null) {
          slabBytes += ((BorrowedSlabMessage) rows[i]).slabBytes();
          sessionArenaBytes += session.arenaBytes();
          retainedInputBytes += session.retainedInputBytes();
        }
      }
      copiedSlabInputBytes = decoder.copiedSlabInputBytes() - copiedBefore;
      if (!stage.equals("interpret") && !stage.equals("peak")) release();
    }

    private SlabMessage slab(int index) {
      if (selection != null) {
        if (!ImageInfo.inImageCode())
          return SlabMessage.decode(
              plan,
              wires[index],
              0,
              wires[index].length,
              SlabLimits.forInput(plan.layout(), wires[index].length),
              selection);
        var selected = java.util.Objects.requireNonNull(selectedPlan);
        return stage.equals("heap")
            ? selected.decode(wires[index], 1 << 20)
            : selected.decode(java.util.Objects.requireNonNull(inputs[index]), 1 << 20);
      }
      return ImageInfo.inImageCode() || engine.equals("slab-native")
          ? (stage.equals("heap")
              ? nativePlan.decodeSlab(wires[index], 1 << 20)
              : nativePlan.decodeSlab(java.util.Objects.requireNonNull(inputs[index]), 1 << 20))
          : plan.decodeSlab(wires[index]);
    }

    String producerBackend() {
      if (engine.equals("stock") || engine.equals("stock-interop")) return "protobuf-java";
      if (sessionEngine(engine)
          || engine.equals("slab-native")
          || (owningSlabEngine(engine) && ImageInfo.inImageCode()))
        return decoder.selected().name();
      return "JAVA";
    }

    void prepare() throws Exception {
      for (int i = 0; i < ROWS; i++) {
        if (sessionEngine(engine))
          rows[i] =
              java.util.Objects.requireNonNull(sessions[i])
                  .decode(java.util.Objects.requireNonNull(inputs[i]));
        else if (owningSlabEngine(engine)) rows[i] = slab(i);
        else if (engine.equals("stock-interop"))
          rows[i] = StockInteropRow.create(Type.parseFrom(wires[i]), plan);
        else
          rows[i] = engine.equals("stock") ? Type.parseFrom(wires[i]) : generated.decode(wires[i]);
      }
    }

    void consume() {
      long result = (Long) target.call(rows, rotation++ & (ROWS - 1));
      if ((result & ~COMPILED) != expected) throw new AssertionError("Consumer result mismatch");
      sink = result;
      retained = rows[0];
    }

    void operation() throws Exception {
      if (stage.equals("interpret") || stage.equals("peak")) consume();
      else {
        prepare();
        try {
          if (!stage.equals("decode")) consume();
        } finally {
          release();
        }
      }
    }

    void tier(boolean peak) {
      if (stage.equals("decode")) return;
      if (target.isValidLastTier() != peak) throw new AssertionError("Wrong guest tier");
      // SharedRoot returns this bit inside the actual shared-access helper, not its caller.
      if (!engine.equals("stock") && ((sink & COMPILED) != 0) != peak)
        throw new AssertionError("Shared-access helper did not execute in the requested tier");
    }

    void release() {
      for (int i = 0; i < ROWS; i++) {
        var session = sessions[i];
        if (session != null) session.reset();
        rows[i] = Missing.INSTANCE;
      }
    }

    void closedReadCheck() throws Exception {
      if (!sessionEngine(engine)) return;
      release();
      prepare();
      for (var session : sessions) java.util.Objects.requireNonNull(session).reset();
      try {
        consume();
        throw new AssertionError("Closed session was readable");
      } catch (IllegalStateException expected) {
        /* scoped epoch rejection */
      }
    }

    @Override
    public void close() {
      release();
      for (var session : sessions) if (session != null) session.close();
      for (var input : inputs) if (input != null) input.close();
      if (selectedPlan != null) selectedPlan.close();
      nativePlan.close();
      decoder.close();
    }
  }

  // Independent reference implementation; stock uses it at the required SVM collection boundary.
  @TruffleBoundary
  private static long expected(Type row, boolean full) {
    long sum =
        row.getName().length()
            + row.getFieldsCount() * 17L
            + row.getOneofsCount() * 3L
            + row.getSyntaxValue()
            + (row.hasSourceContext() ? row.getSourceContext().getFileName().length() : 7);
    if (full) {
      for (int i = 0; i < row.getFieldsCount(); i++) {
        var f = row.getFields(i);
        sum += f.getName().length();
        if (f.getPacked()) sum += f.getNumber() * 31L + f.getKindValue() + f.getCardinalityValue();
      }
      for (int i = 0; i < row.getOneofsCount(); i++) sum += row.getOneofs(i).length();
    }
    return sum;
  }

  private abstract static class ConsumerRoot extends RootNode {
    final boolean full;

    ConsumerRoot(boolean full) {
      super(ComparisonLanguage.current());
      this.full = full;
    }
  }

  private static final class StockRoot extends ConsumerRoot {
    StockRoot(boolean full) {
      super(full);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      var rows = (Object[]) frame.getArguments()[0];
      int rotation = (Integer) frame.getArguments()[1];
      long sum = 0;
      for (int i = 0; i < ROWS; i++)
        sum += expected((Type) rows[(i + rotation) & (ROWS - 1)], full);
      return sum;
    }
  }

  private static final class SharedRoot extends ConsumerRoot {
    @Child private DataLibrary data = DataLibrary.factory().createDispatched(3);
    @Child private DataLibrary source = DataLibrary.factory().createDispatched(3);
    @Child private ReadLengthNode names = ReadLengthNode.create();
    @Child private ReadLengthNode fieldsCount = ReadLengthNode.create();
    @Child private ReadLengthNode groupsCount = ReadLengthNode.create();
    @Child private ReadLongNode syntax = ReadLongNode.create();
    @Child private FieldAudit field = new FieldAudit();
    @Child private InteropLibrary arrays = InteropLibrary.getFactory().createDispatched(3);

    SharedRoot(boolean full) {
      super(full);
    }

    long score(Object row) throws Exception {
      long sum =
          names.execute(row, 0)
              + fieldsCount.execute(row, 1) * 17L
              + groupsCount.execute(row, 2) * 3L
              + syntax.execute(row, 5);
      Object context = data.read(row, 4);
      sum += context == Missing.INSTANCE ? 7 : ((String) source.read(context, 0)).length();
      if (full) {
        Object fields = data.read(row, 1), groups = data.read(row, 2);
        int n = Math.toIntExact(arrays.getArraySize(fields));
        for (int i = 0; i < n; i++) sum += field.score(arrays.readArrayElement(fields, i));
        int m = Math.toIntExact(arrays.getArraySize(groups));
        for (int i = 0; i < m; i++) sum += ((String) arrays.readArrayElement(groups, i)).length();
      }
      return sum | (CompilerDirectives.inCompiledCode() ? COMPILED : 0);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      var rows = (Object[]) frame.getArguments()[0];
      int rotation = (Integer) frame.getArguments()[1];
      long sum = 0, compiled = COMPILED;
      try {
        for (int i = 0; i < ROWS; i++) {
          long score = score(rows[(i + rotation) & (ROWS - 1)]);
          sum += score & ~COMPILED;
          compiled &= score;
        }
      } catch (RuntimeException failure) {
        throw failure;
      } catch (Exception failure) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("Consumer access failed", failure);
      }
      return sum | compiled;
    }
  }

  private static final class FieldAudit extends Node {
    @Child private ReadLengthNode name = ReadLengthNode.create();
    @Child private ReadBooleanNode packed = ReadBooleanNode.create();
    @Child private ReadLongNode number = ReadLongNode.create();
    @Child private ReadLongNode kind = ReadLongNode.create();
    @Child private ReadLongNode cardinality = ReadLongNode.create();

    long score(Object row) {
      long sum = name.execute(row, 3);
      if (packed.execute(row, 6))
        sum += number.execute(row, 2) * 31L + kind.execute(row, 0) + cardinality.execute(row, 1);
      return sum;
    }
  }
}
