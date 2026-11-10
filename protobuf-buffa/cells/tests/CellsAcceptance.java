package dev.elide.truffledata.buffa;

import dev.elide.truffledata.buffa.tests.Acceptance;
import dev.elide.truffledata.protobuf.CellsFixtures;
import dev.elide.truffledata.protobuf.ProtoPlan;
import dev.elide.truffledata.protobuf.SlabLimits;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.graalvm.nativeimage.ImageInfo;
import org.jspecify.annotations.NullMarked;

/**
 * The Native Image acceptance suite on an image built with {@code -Dtruffledata.buffa.cells=true}:
 * the static backend's slab producer calls go through the generated cells, the full shared
 * acceptance (semantics, budgets, ownership, randomized parity) passes unchanged, and the cells
 * route is byte-identical to the static backend it wraps. It also measures the Java allocation per
 * slab producer call of both backends, and holds the cells route to its own ceilings.
 */
@NullMarked
public final class CellsAcceptance {
  /**
   * Java bytes per {@code writeSlab} call the cells route may add over the static backend in this
   * image (the static backend: 0 B). The cell call runs in AOT-compiled host code below a {@code
   * @TruffleBoundary}, where nothing is scalar-replaced, so this is everything the call allocates
   * (CellsSvmBackend). With a session's usual shape, the same input buffer and arena on every
   * call, only Myna's own objects remain: the three Borrow objects its BytesCoercion builds for
   * the {@code compiled}, {@code data} and {@code out} byte pointers (48 B each).
   */
  static final double CELLS_EXTRA_BYTES_PER_CALL = 144;

  /**
   * The same when the input and the arena change on every call: a NativePointer for each new
   * address (16 B each) on top of the three Borrow objects. A length or capacity that changes and
   * lies outside the JDK's small-value cache would add a 16 B box each; here they repeat.
   */
  static final double CELLS_EXTRA_BYTES_PER_VARYING_CALL = 176;

  private CellsAcceptance() {}

  public static void main(String[] args) throws Exception {
    try (var decoder = BuffaWire.open(BuffaWire.Mode.NATIVE, null)) {
      var backend = Objects.requireNonNull(decoder.backend);
      if (!(backend instanceof CellsSvmBackend))
        throw new AssertionError("Image built with the cells flag selected " + backend.getClass());
    }
    Acceptance.main(args);
    System.out.println("CELLS parity outcomes=" + CellsParity.run(null));
    allocation();
  }

  /** Bytes per {@code writeSlab} call: the static backend, then the cells route, same inputs. */
  static void allocation() {
    if (!ImageInfo.inImageCode()) throw new AssertionError("SVM-only probe");
    var bean =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported())
      throw new AssertionError("No per-thread allocation counter in this image");
    bean.setThreadAllocatedMemoryEnabled(true);
    // Three measured rounds each, after a warm-up; the lowest round of each is compared.
    for (boolean varying : new boolean[] {false, true}) {
      double[] referenceRounds = perCall(bean, new SvmBackend(), varying);
      double[] cellsRounds = perCall(bean, new CellsSvmBackend(new SvmBackend()), varying);
      System.out.println(
          "CELLS SVM writeSlab Java bytes/call"
              + (varying ? " (input and arena alternate)" : " (same input and arena)")
              + " reference="
              + Arrays.toString(referenceRounds)
              + " cells="
              + Arrays.toString(cellsRounds));
      double extra = cellsRounds[0] - referenceRounds[0];
      double ceiling = varying ? CELLS_EXTRA_BYTES_PER_VARYING_CALL : CELLS_EXTRA_BYTES_PER_CALL;
      if (extra > ceiling)
        throw new AssertionError(
            "Cells writeSlab allocates "
                + extra
                + " B/call over the static backend; ceiling "
                + ceiling
                + (varying ? " (input and arena alternate)" : " (same input and arena)"));
    }
  }

  private static double[] perCall(
      com.sun.management.ThreadMXBean bean, BuffaWire.Backend backend, boolean varying) {
    final int calls = 20_000;
    long thread = Thread.currentThread().threadId();
    try {
      ProtoPlan plan = CellsFixtures.plans().get(0);
      byte[] program =
          NativeSchema.encode(NativeSchema.types(plan, List.of(), 1 << 20), List.of(), 1 << 20);
      long compiled;
      try (var schema = backend.allocate(program.length).put(program)) {
        compiled = backend.compileLayout(schema);
      }
      try {
        byte[] wire = CellsFixtures.randomWire(new Random(0xa110c), plan);
        var limits = new SlabLimits(32, 1 << 20);
        Object prepared = backend.prepareSlab(compiled);
        try (var input = backend.allocate(wire.length).put(wire);
            var output = backend.allocate(1 << 16);
            var otherInput = backend.allocate(wire.length).put(wire);
            var otherOutput = backend.allocate(1 << 16)) {
          long sum = 0;
          for (int i = 0; i < calls; i++)
            sum += backend.writeSlab(prepared, input, output, limits, 1, null);
          double[] rounds = new double[3];
          for (int round = 0; round < rounds.length; round++) {
            long before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < calls; i++) {
              boolean other = varying && (i & 1) != 0;
              sum +=
                  backend.writeSlab(
                      prepared,
                      other ? otherInput : input,
                      other ? otherOutput : output,
                      limits,
                      1,
                      null);
            }
            long after = bean.getThreadAllocatedBytes(thread);
            if (before < 0 || after < 0)
              throw new AssertionError("Per-thread allocation counter unavailable");
            rounds[round] = (after - before) / (double) calls;
          }
          if (sum <= 0) throw new AssertionError("writeSlab produced nothing");
          Arrays.sort(rounds);
          return rounds;
        }
      } finally {
        backend.releaseLayout(compiled);
      }
    } finally {
      backend.close();
    }
  }
}
