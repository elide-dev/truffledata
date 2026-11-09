package dev.elide.truffledata.comparison;

import com.google.protobuf.SourceContext;
import com.google.protobuf.Syntax;
import com.google.protobuf.Type;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.sun.management.ThreadMXBean;
import dev.elide.myna.truffle.NativeCell;
import dev.elide.myna.truffle.NativeFunction;
import dev.elide.truffledata.Limits;
import dev.elide.truffledata.buffa.BuffaPlan;
import dev.elide.truffledata.buffa.BuffaWire;
import dev.elide.truffledata.buffa.CellProbe;
import dev.elide.truffledata.comparison.cells.generated.BuffaCellsTruffle;
import dev.elide.truffledata.generated.ComparisonContracts;
import dev.elide.truffledata.protobuf.ProtoPlan;
import dev.elide.truffledata.protobuf.SlabMessage;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.Context;
import org.jspecify.annotations.NullMarked;

/**
 * WS11 gate probe (plan 2026-11-08-beat-stock-svm §4): one TruffleData decode entry called from a
 * force-compiled Truffle root on SVM, by route. One process measures one route over the consumer
 * harness's full fixture (32 {@code google.protobuf.Type} messages, 3–8 repeated int64 fields, the
 * heap-input shape a guest program hands over); every decoded slab is checked byte for byte against
 * the production decode before timing, and every measured sample must observe compiled code.
 *
 * <ul>
 *   <li>{@code boundary}: today's guest-facing entry, {@code BuffaPlan.decodeSlab(byte[])}, a
 *       {@code @TruffleBoundary} host call.
 *   <li>{@code boundary-body}: the probe's copy of that body behind a {@code @TruffleBoundary}
 *       (isolates production plumbing from the boundary itself).
 *   <li>{@code direct}: the probe body partial-evaluation visible; {@code Buffa.decodeSlab} is an
 *       ordinary {@code @CFunction} transition compiled as a direct AOT-stub call (graal#196).
 *   <li>{@code cell}: the same body with the native call through the generated Myna cell
 *       {@code BuffaCellsTruffle.decodeSlab_Cell} adopted as a child node (language glue).
 *   <li>{@code cell-interop}: the cell's {@code NativeFunction} wrapper through a dispatched
 *       {@code InteropLibrary} (the generic path a language without glue takes).
 * </ul>
 */
@NullMarked
public final class CellsProbe {
  private static final int ROWS = 32;
  private static volatile Object retained = new Object();

  private CellsProbe() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 1 || !CellProbe.ROUTES.contains(args[0]))
      throw new IllegalArgumentException("Usage: cells-probe " + CellProbe.ROUTES);
    if (!ImageInfo.inImageRuntimeCode())
      throw new UnsupportedOperationException("The cell probe is SVM-only");
    String route = args[0];
    long warmupNanos = Long.getLong("cellsProbe.warmupMillis", 500) * 1_000_000;
    long sampleNanos = Long.getLong("cellsProbe.sampleMillis", 200) * 1_000_000;
    try (var context =
        Context.newBuilder("td-compare")
            .allowExperimentalOptions(true)
            .option("engine.BackgroundCompilation", "false")
            .option("engine.SingleTierCompilationThreshold", "100000000")
            .option("engine.MultiTier", "false")
            .option("engine.CompilationFailureAction", "Throw")
            .option("engine.TraceCompilation", "true")
            .option("engine.CompilationStatisticDetails", "true")
            .option("compiler.TreatPerformanceWarningsAsErrors", "all")
            .option("compiler.MaximumGraalGraphSize", "10000")
            .build()) {
      context.initialize("td-compare");
      context.enter();
      try (var fixture = new Fixture(route)) {
        var counter = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!counter.isThreadAllocatedMemorySupported())
          throw new AssertionError("Allocation counter");
        counter.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long before = counter.getThreadAllocatedBytes(thread);
        retained = new byte[1 << 20];
        if (before < 0 || counter.getThreadAllocatedBytes(thread) - before < 1 << 20)
          throw new AssertionError("Allocation calibration");
        for (int i = 0; i < 128; i++) fixture.operation(false);
        boolean compiled = false;
        String failure = "";
        try {
          for (int attempt = 0; attempt < 8 && !compiled; attempt++) {
            fixture.target.compile(true);
            fixture.target.waitForCompilation();
            for (int i = 0; i < 128; i++) fixture.operation(false);
            compiled = fixture.target.isValidLastTier();
          }
        } catch (RuntimeException failed) {
          failure = failed.getClass().getName() + ": " + failed.getMessage();
          failed.printStackTrace(System.err);
        }
        int astNodes = NodeUtil.countNodes(fixture.root);
        if (!compiled) {
          System.out.printf(
              Locale.ROOT,
              "{\"route\":\"%s\",\"runtime\":\"svm\",\"rows\":%d,\"compiled\":false,\"ast_nodes\":%d,\"graph_budget\":10000,\"failure\":\"%s\"}%n",
              route,
              ROWS,
              astNodes,
              failure.replace("\"", "'"));
          return;
        }
        fixture.operation(true);
        fixture.check();
        long until = System.nanoTime() + warmupNanos;
        while (System.nanoTime() < until) fixture.operation(true);
        double[] time = new double[5];
        for (int s = 0; s < time.length; s++) {
          long count = 0, start = System.nanoTime(), end;
          do {
            for (int i = 0; i < 8; i++) fixture.operation(true);
            count += 8;
            end = System.nanoTime();
          } while (end - start < sampleNanos);
          time[s] = (double) (end - start) / (count * ROWS);
        }
        double[] memory = new double[3];
        for (int s = 0; s < memory.length; s++) {
          long from = counter.getThreadAllocatedBytes(thread);
          for (int i = 0; i < 64; i++) fixture.operation(true);
          memory[s] = (counter.getThreadAllocatedBytes(thread) - from) / (64.0 * ROWS);
        }
        if (!fixture.target.isValidLastTier()) throw new AssertionError("Lost compiled tier");
        System.out.printf(
            Locale.ROOT,
            "{\"route\":\"%s\",\"runtime\":\"svm\",\"rows\":%d,\"compiled\":true,\"ast_nodes\":%d,\"graph_budget\":10000,\"checksum\":%d,\"input_bytes_per_row\":%.3f,\"slab_bytes_per_row\":%.3f,\"capacity_bytes\":%d,\"ns_per_row\":%s,\"java_bytes_per_row\":%s}%n",
            route,
            ROWS,
            astNodes,
            fixture.checksum,
            fixture.inputBytes / (double) ROWS,
            fixture.slabBytes / (double) ROWS,
            fixture.probe.capacity(),
            Arrays.toString(time),
            Arrays.toString(memory));
      } finally {
        context.leave();
      }
    }
  }

  /** The consumer harness's full fixture and one route's compiled root. */
  private static final class Fixture implements AutoCloseable {
    final byte[][] wires = new byte[ROWS][];
    final Object[] rows = new Object[ROWS];
    final BuffaWire decoder;
    final BuffaPlan nativePlan;
    final ProtoPlan plan;
    final CellProbe probe;
    final ProbeRoot root;
    final OptimizedCallTarget target;
    final long checksum;
    long inputBytes, slabBytes;

    Fixture(String route) {
      var model =
          ComparisonContracts.create(ComparisonLanguage.current(), Limits.DEFAULT, 16, 1 << 20);
      plan = model.type("google.protobuf.Type");
      decoder = BuffaWire.open(BuffaWire.Mode.NATIVE, null);
      nativePlan = decoder.compile(plan, 65536);
      for (int i = 0; i < ROWS; i++) {
        // ConsumerComparison.Fixture, mode "full": same messages, same wire bytes.
        var b = Type.newBuilder().setName("catalog-" + i + "-λ").setSyntax(Syntax.SYNTAX_PROTO3);
        if (i % 3 != 0)
          b.setSourceContext(SourceContext.newBuilder().setFileName("schema-" + i + ".proto"));
        b.addOneofs("identity");
        if ((i & 1) == 0) b.addOneofs("contact-😀");
        int count = 3 + i % 6;
        for (int j = 0; j < count; j++)
          b.addFields(
              com.google.protobuf.Field.newBuilder()
                  .setName("column-" + j)
                  .setNumber(j + 1)
                  .setKind(com.google.protobuf.Field.Kind.TYPE_INT64)
                  .setCardinality(com.google.protobuf.Field.Cardinality.CARDINALITY_REPEATED)
                  .setPacked((i + j) % 3 != 0));
        wires[i] = b.build().toByteArray();
        inputBytes += wires[i].length;
      }
      // The production steady state: the plan's learned capacity is the largest extent.
      int capacity = 0;
      long sum = 0;
      for (int i = 0; i < ROWS; i++) {
        SlabMessage production = nativePlan.decodeSlab(wires[i], 1 << 20);
        capacity = Math.max(capacity, production.slabBytes());
        slabBytes += production.slabBytes();
        sum = sum * 31 + Arrays.hashCode(production.slabCopy());
      }
      checksum = sum;
      probe = new CellProbe(decoder, plan, 1 << 20, capacity);
      Route body =
          switch (route) {
            case "boundary" -> new Boundary(nativePlan);
            case "boundary-body" -> new BoundaryBody(probe);
            case "direct" -> new Direct(probe);
            case "cell" -> new Cell(probe, new Glue(new BuffaCellsTruffle.decodeSlab_Cell()));
            case "cell-interop" -> new Cell(probe, new Interop(BuffaCellsTruffle.decodeSlab()));
            default -> throw new IllegalArgumentException(route);
          };
      root = new ProbeRoot(body, wires, rows);
      target = (OptimizedCallTarget) root.getCallTarget();
    }

    void operation(boolean requireCompiled) {
      boolean compiled = (Boolean) target.call();
      if (requireCompiled && !compiled) throw new AssertionError("Probe ran outside compiled code");
      retained = rows[0];
    }

    /** Every route's slabs equal the production decode byte for byte. */
    void check() {
      long sum = 0;
      for (int i = 0; i < ROWS; i++) {
        var message = (SlabMessage) rows[i];
        if (message.slabBytes() != nativePlan.decodeSlab(wires[i], 1 << 20).slabBytes())
          throw new AssertionError("Probe slab extent differs from the production slab");
        sum = sum * 31 + Arrays.hashCode(message.slabCopy());
      }
      if (sum != checksum) throw new AssertionError("Probe slab differs from the production slab");
    }

    @Override
    public void close() {
      probe.close();
      nativePlan.close();
      decoder.close();
    }
  }

  private static final class ProbeRoot extends RootNode {
    @Child private Route route;

    @CompilationFinal(dimensions = 1)
    private final byte[][] wires;

    private final Object[] rows;

    ProbeRoot(Route route, byte[][] wires, Object[] rows) {
      super(ComparisonLanguage.current());
      this.route = route;
      this.wires = wires;
      this.rows = rows;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      for (int i = 0; i < ROWS; i++) rows[i] = route.decode(wires[i]);
      return CompilerDirectives.inCompiledCode();
    }
  }

  private abstract static class Route extends Node {
    abstract Object decode(byte[] wire);
  }

  /** Today's entry: the production plan's boundary decode, as a guest node calls it. */
  private static final class Boundary extends Route {
    private final BuffaPlan plan;

    Boundary(BuffaPlan plan) {
      this.plan = plan;
    }

    @Override
    Object decode(byte[] wire) {
      return plan.decodeSlab(wire, 1 << 20);
    }
  }

  private static final class BoundaryBody extends Route {
    private final CellProbe probe;

    BoundaryBody(CellProbe probe) {
      this.probe = probe;
    }

    @Override
    Object decode(byte[] wire) {
      return body(probe, wire);
    }

    @TruffleBoundary
    private static Object body(CellProbe probe, byte[] wire) {
      return probe.decode(wire);
    }
  }

  private static final class Direct extends Route {
    private final CellProbe probe;

    Direct(CellProbe probe) {
      this.probe = probe;
    }

    @Override
    Object decode(byte[] wire) {
      return probe.decode(wire);
    }
  }

  private static final class Cell extends Route {
    private final CellProbe probe;
    @Child private CellProbe.Invoker invoker;

    Cell(CellProbe probe, CellProbe.Invoker invoker) {
      this.probe = probe;
      this.invoker = invoker;
    }

    @Override
    Object decode(byte[] wire) {
      return probe.decode(invoker, wire);
    }
  }

  /** Language glue: the generated cell adopted as a child and executed directly. */
  private static final class Glue extends CellProbe.Invoker {
    @Child private NativeCell cell;

    Glue(NativeCell cell) {
      this.cell = cell;
    }

    @Override
    public int invoke(Object[] arguments) {
      return (Integer) cell.execute(arguments);
    }
  }

  /** The generic path: the cell's executable wrapper through a dispatched interop library. */
  private static final class Interop extends CellProbe.Invoker {
    @Child private InteropLibrary interop = InteropLibrary.getFactory().createDispatched(1);
    private final NativeFunction function;

    Interop(NativeFunction function) {
      this.function = function;
    }

    @Override
    public int invoke(Object[] arguments) {
      try {
        return (Integer) interop.execute(function, arguments);
      } catch (InteropException failure) {
        CompilerDirectives.transferToInterpreter();
        throw new AssertionError(failure);
      }
    }
  }
}
