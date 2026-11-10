package dev.elide.truffledata.buffa;

import dev.elide.truffledata.protobuf.CellsFixtures;
import dev.elide.truffledata.protobuf.Projection;
import dev.elide.truffledata.protobuf.ProtoPlan;
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
 * projection, short arenas, depth limits) runs on both backends over the same wires; outcomes must
 * be equal: the same slab bytes and retained capacity, or the same exception class and message.
 * Runs on HotSpot (FFM backend vs FFM cells) and in a Native Image (static backend vs native cells).
 * On HotSpot it also hands both backends adopted regions whose scope is closed or owned by another
 * thread: the FFM downcall rejects them, and the cells route, which lends raw addresses, must too.
 */
@NullMarked
public final class CellsParity {
  private static final int WIRES = 400;

  /** Outcomes a depth limit changed: the depth comparisons are not vacuous. */
  private static long depthSensitive;

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
    depthSensitive = 0;
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
          if (!ImageInfo.inImageCode())
            compared +=
                scopes(reference, left, cells, right, plan, CellsFixtures.randomWire(random, plan));
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
    check(depthSensitive > 0, "no depth limit changed an outcome");
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
    // Depth limits, alternating with the default above on the same backends: every limit reaches
    // the producer as given (the cells route caches the boxed limits between calls).
    for (int depth : new int[] {0, 1, 2}) {
      var shallow = new SlabLimits(depth, 1 << 20);
      String expected = heap(reference, left, plan, wire, shallow, 4 * wire.length + 256);
      if (!expected.equals(first)) depthSensitive++;
      equal(
          cell + " depth " + depth,
          expected,
          heap(cells, right, plan, wire, shallow, 4 * wire.length + 256));
      equal(
          cell + " native depth " + depth,
          wire(reference, left, plan, wire, shallow, 4 * wire.length + 256),
          wire(cells, right, plan, wire, shallow, 4 * wire.length + 256));
      equal(
          cell + " arena depth " + depth,
          arena(reference, left, wire, shallow, 1 << 16, null),
          arena(cells, right, wire, shallow, 1 << 16, null));
      compared += 3;
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
            backend.writeSlab(backend.prepareSlab(compiled), input, output, limits, flags, program);
        byte[] bytes = new byte[Math.max(0, written)];
        output.lease.bytes.duplicate().get(bytes);
        return "ok " + written + " " + HexFormat.of().formatHex(bytes);
      } catch (RuntimeException failure) {
        return "error " + failure.getClass().getName() + ": " + failure.getMessage();
      }
    }
  }

  /**
   * HotSpot only. An adopted region is the caller's memory; {@link BuffaJvm#adopt} promises its
   * scope stays checked on every access. The FFM downcall refuses a segment whose scope is closed
   * ({@code IllegalStateException}) or confined to another thread ({@code WrongThreadException})
   * before any native code runs; the cells route must refuse the same way, never lend the address.
   */
  private static int scopes(
      BuffaWire.Backend reference,
      long left,
      BuffaWire.Backend cells,
      long right,
      ProtoPlan plan,
      byte[] wire) {
    byte[] bytes = wire.length == 0 ? new byte[1] : wire;
    var limits = new SlabLimits(32, 1 << 20);
    int compared = 0;
    for (boolean foreign : new boolean[] {false, true}) {
      String type = foreign ? "java.lang.WrongThreadException" : "java.lang.IllegalStateException";
      var outcomes = new ArrayList<String>();
      for (BuffaWire.Backend backend : List.of(reference, cells)) {
        long compiled = backend == reference ? left : right;
        try (var input = unusable(bytes, foreign);
            var output = backend.allocate(1 << 16)) {
          outcomes.add(outcome(() -> backend.slab(compiled, input, plan, limits, 1 << 12)));
          try {
            int written =
                backend.writeSlab(backend.prepareSlab(compiled), input, output, limits, 1, null);
            outcomes.add("ok " + written);
          } catch (RuntimeException failure) {
            outcomes.add("error " + failure.getClass().getName() + ": " + failure.getMessage());
          }
        }
      }
      String cell = plan.name() + (foreign ? " foreign-thread region" : " closed region");
      for (int entry = 0; entry < 2; entry++) {
        check(
            outcomes.get(entry).startsWith("error " + type + ":"),
            cell + ": the FFM backend accepted it: " + outcomes.get(entry));
        equal(cell + " entry " + entry, outcomes.get(entry), outcomes.get(entry + 2));
        compared++;
      }
    }
    return compared;
  }

  /** An adopted input over a region whose arena is closed, or confined to a finished thread. */
  private static WireInput unusable(byte[] bytes, boolean foreign) {
    if (!foreign) {
      var arena = java.lang.foreign.Arena.ofConfined();
      var region = arena.allocate(bytes.length);
      region.copyFrom(java.lang.foreign.MemorySegment.ofArray(bytes));
      WireInput input = BuffaJvm.adopt(region, () -> {});
      arena.close();
      return input;
    }
    // The owner thread ends without closing its arena: nobody else may touch or close the region.
    var region = new java.lang.foreign.MemorySegment[1];
    var thread =
        new Thread(
            () -> {
              region[0] = java.lang.foreign.Arena.ofConfined().allocate(bytes.length);
              region[0].copyFrom(java.lang.foreign.MemorySegment.ofArray(bytes));
            });
    thread.start();
    try {
      thread.join();
    } catch (InterruptedException interrupted) {
      throw new AssertionError(interrupted);
    }
    return BuffaJvm.adopt(Objects.requireNonNull(region[0]), () -> {});
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
          "Cells route differs at "
              + cell
              + "\n  reference "
              + expected
              + "\n  cells     "
              + actual);
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
