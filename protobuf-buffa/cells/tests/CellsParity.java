package dev.elide.truffledata.buffa;

import dev.elide.truffledata.protobuf.CellsFixtures;
import dev.elide.truffledata.protobuf.ProtoPlan;
import dev.elide.truffledata.protobuf.Projection;
import dev.elide.truffledata.protobuf.SlabLimits;
import dev.elide.truffledata.protobuf.SlabMessage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import org.graalvm.nativeimage.ImageInfo;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Test-only (split package): the Myna cells route against the backend it wraps, entry by entry.
 * Every slab producer entry ({@code slab} over heap and native input, the critical-size scratch
 * fallback, the learned-capacity retry, budget failures, {@code writeSlab} with and without a
 * projection, short arenas) runs on both backends over the same wires; outcomes must be equal: the
 * same slab bytes and retained capacity, or the same exception class and message. Runs on HotSpot
 * (FFM backend vs FFM cells) and in a Native Image (static backend vs native cells).
 */
@NullMarked
public final class CellsParity {
  private static final int WIRES = 400;

  private CellsParity() {}

  /** Returns the number of compared outcomes. */
  public static long run(@Nullable Path library) {
    BuffaWire.Backend reference, cells;
    if (ImageInfo.inImageCode()) {
      reference = new SvmBackend();
      cells = new CellsSvmBackend(new SvmBackend());
    } else {
      Path path = Objects.requireNonNull(library);
      reference = new FfmBackend(path);
      cells = new CellsFfmBackend(path);
    }
    long compared = 0;
    try {
      var random = new Random(0xce11);
      for (ProtoPlan plan : CellsFixtures.plans()) {
        byte[] program =
            NativeSchema.encode(NativeSchema.types(plan, List.of(), 1 << 20), List.of(), 1 << 20);
        long left = compile(reference, program), right = compile(cells, program);
        try {
          var projections = new ArrayList<@Nullable Projection>();
          projections.add(null);
          projections.add(Projection.all(plan));
          projections.add(Projection.builder(plan).build().lazyChildren().borrowBytes(1));
          projections.add(Projection.builder(plan).build().lax());
          for (int i = 0; i < WIRES; i++) {
            byte[] wire = CellsFixtures.randomWire(random, plan);
            if (wire.length > 0 && i % 7 == 0)
              wire = Arrays.copyOf(wire, random.nextInt(wire.length));
            if (i % 50 == 0) wire = new byte[0];
            String cell = plan.name() + "/" + i + " wire=" + HexFormat.of().formatHex(wire);
            compared += compare(cell, reference, left, cells, right, plan, wire, projections);
          }
        } finally {
          reference.releaseLayout(left);
          cells.releaseLayout(right);
        }
      }
      check(
          reference.copiedSlabInputBytes() == cells.copiedSlabInputBytes(),
          "copied input accounting");
    } finally {
      reference.close();
      cells.close();
    }
    if (compared < 10_000) throw new AssertionError("Too few cells parity outcomes: " + compared);
    return compared;
  }

  private static long compile(BuffaWire.Backend backend, byte[] program) {
    try (var schema = backend.allocate(program.length).put(program)) {
      return backend.compileLayout(schema);
    }
  }

  private static int compare(
      String cell,
      BuffaWire.Backend reference,
      long left,
      BuffaWire.Backend cells,
      long right,
      ProtoPlan plan,
      byte[] wire,
      List<@Nullable Projection> projections) {
    int compared = 0;
    var limits = new SlabLimits(32, 1 << 20);
    String first = heap(reference, left, plan, wire, limits, 4 * wire.length + 256);
    equal(cell + " heap", first, heap(cells, right, plan, wire, limits, 4 * wire.length + 256));
    compared++;
    int extent = first.startsWith("ok ") ? Integer.parseInt(first.split(" ")[1]) : -1;
    // The learned-capacity retry (status 6), an empty first capacity, the scratch fallback.
    for (int capacity : new int[] {0, 1, Math.max(0, extent - 1), extent}) {
      if (capacity < 0) continue;
      equal(
          cell + " heap capacity " + capacity,
          heap(reference, left, plan, wire, limits, capacity),
          heap(cells, right, plan, wire, limits, capacity));
      equal(
          cell + " native capacity " + capacity,
          wire(reference, left, plan, wire, limits, capacity),
          wire(cells, right, plan, wire, limits, capacity));
      compared += 2;
    }
    var scratch = new SlabLimits(32, 1 << 20, 0);
    equal(
        cell + " scratch",
        heap(reference, left, plan, wire, scratch, 4 * wire.length + 256),
        heap(cells, right, plan, wire, scratch, 4 * wire.length + 256));
    compared++;
    if (extent > 0) {
      // Budgets at and one below the producer's extent.
      for (int budget : new int[] {extent, extent - 1}) {
        var tight = new SlabLimits(32, budget);
        equal(
            cell + " budget " + budget,
            heap(reference, left, plan, wire, tight, budget),
            heap(cells, right, plan, wire, tight, budget));
        equal(
            cell + " native budget " + budget,
            wire(reference, left, plan, wire, tight, budget),
            wire(cells, right, plan, wire, tight, budget));
        compared += 2;
      }
    }
    for (var projection : projections)
      for (int arena : new int[] {1 << 16, 1, Math.max(1, extent)}) {
        equal(
            cell + " arena " + arena + " projection " + projection,
            arena(reference, left, wire, limits, arena, projection),
            arena(cells, right, wire, limits, arena, projection));
        compared++;
      }
    return compared;
  }

  private static String heap(
      BuffaWire.Backend backend,
      long compiled,
      ProtoPlan plan,
      byte[] wire,
      SlabLimits limits,
      int capacity) {
    return outcome(() -> backend.slab(compiled, wire.clone(), plan, limits, capacity));
  }

  private static String wire(
      BuffaWire.Backend backend,
      long compiled,
      ProtoPlan plan,
      byte[] wire,
      SlabLimits limits,
      int capacity) {
    try (var input = backend.allocate(wire.length).put(wire)) {
      return outcome(() -> backend.slab(compiled, input, plan, limits, capacity));
    }
  }

  private static String arena(
      BuffaWire.Backend backend,
      long compiled,
      byte[] wire,
      SlabLimits limits,
      int size,
      @Nullable Projection projection) {
    byte[] mask = projection == null ? null : projection.program();
    try (var input = backend.allocate(wire.length).put(wire);
        var output = backend.allocate(size);
        var program =
            mask == null ? null : backend.allocate(mask.length).put(Objects.requireNonNull(mask))) {
      int flags = projection == null ? 1 : projection.decodeFlags();
      try {
        int written =
            backend.writeSlab(
                backend.prepareSlab(compiled), input, output, limits, flags, program);
        byte[] bytes = new byte[Math.max(0, written)];
        output.lease.bytes.duplicate().get(bytes);
        return "ok " + written + " " + HexFormat.of().formatHex(bytes);
      } catch (RuntimeException failure) {
        return "error " + failure.getClass().getName() + ": " + failure.getMessage();
      }
    }
  }

  private static String outcome(Supplier<SlabMessage> decode) {
    SlabMessage message;
    try {
      message = decode.get();
    } catch (RuntimeException failure) {
      return "error " + failure.getClass().getName() + ": " + failure.getMessage();
    }
    try {
      return "ok "
          + message.slabBytes()
          + " retained="
          + message.retainedSlabBytes()
          + " "
          + HexFormat.of().formatHex(message.slabCopy());
    } finally {
      message.close();
    }
  }

  private static void equal(String cell, String expected, String actual) {
    if (!expected.equals(actual))
      throw new AssertionError(
          "Cells route differs at " + cell + "\n  reference " + expected + "\n  cells     " + actual);
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
