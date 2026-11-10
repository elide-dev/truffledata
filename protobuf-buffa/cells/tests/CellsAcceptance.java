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
 * slab producer call of both backends, and holds the cells route to its own ceiling.
 */
@NullMarked
public final class CellsAcceptance {
  /**
   * Java bytes per {@code writeSlab} call the cells route may add over the static backend in this
   * image. The cell call runs in AOT-compiled host code below a {@code @TruffleBoundary}, where
   * nothing guarantees scalar replacement, so the ceiling covers everything that call may allocate
   * when nothing is scalar-replaced (CellsSvmBackend): a NativePointer each for the input and
   * output addresses (16 B each), a box for the arena capacity (16 B; the length here is below the
   * JDK's small-value cache), and the three Borrow objects Myna's BytesCoercion builds for the
   * {@code compiled}, {@code data} and {@code out} byte pointers (48 B each): 192 B, rounded up to
   * 256 B for object alignment. Without the reuse in CellsSvmBackend the same call also allocated
   * the argument array (56 B) and the compiled-layout and result-slot pointers.
   */
  static final double CELLS_EXTRA_BYTES_PER_CALL = 256;

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
    double[] referenceRounds = perCall(bean, new SvmBackend());
    double[] cellsRounds = perCall(bean, new CellsSvmBackend(new SvmBackend()));
    System.out.println(
        "CELLS SVM writeSlab Java bytes/call reference="
            + Arrays.toString(referenceRounds)
            + " cells="
            + Arrays.toString(cellsRounds));
    double reference = referenceRounds[0], cells = cellsRounds[0];
    if (cells - reference > CELLS_EXTRA_BYTES_PER_CALL)
      throw new AssertionError(
          "Cells writeSlab allocates "
              + (cells - reference)
              + " B/call over the static backend; ceiling "
              + CELLS_EXTRA_BYTES_PER_CALL);
  }

  private static double[] perCall(com.sun.management.ThreadMXBean bean, BuffaWire.Backend backend) {
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
            var output = backend.allocate(1 << 16)) {
          long sum = 0;
          for (int i = 0; i < calls; i++)
            sum += backend.writeSlab(prepared, input, output, limits, 1, null);
          double[] rounds = new double[3];
          for (int round = 0; round < rounds.length; round++) {
            long before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < calls; i++)
              sum += backend.writeSlab(prepared, input, output, limits, 1, null);
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
