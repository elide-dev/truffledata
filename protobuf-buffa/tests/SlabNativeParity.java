package dev.elide.truffledata.protobuf;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.DataArray;
import dev.elide.truffledata.buffa.NativeTapeAcceptance;
import dev.elide.truffledata.buffa.generated.BuffaFFM;
import java.lang.foreign.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Live cross-language producer agreement, including overwritten/orphaned record bytes. */
final class SlabNativeParity {
  private static Path library() throws Exception {
    Path root =
        Path.of(
            System.getenv("TEST_SRCDIR"), System.getenv("TEST_WORKSPACE"), "protobuf-buffa/native");
    try (var files = Files.list(root)) {
      return files
          .filter(p -> p.toString().endsWith(".so") || p.toString().endsWith(".dylib"))
          .findFirst()
          .orElseThrow();
    }
  }

  @Test
  void projectedProducersAgreeByteForByte() throws Throwable {
    try (Arena arena = Arena.ofConfined()) {
      var symbols = SymbolLookup.libraryLookup(library(), arena);
      var compile = BuffaFFM.compileLayout(symbols);
      var release = BuffaFFM.releaseLayout(symbols);
      var decode = BuffaFFM.decodeSlab(symbols);
      var result = arena.allocate(32, 8);
      var input = arena.allocate(1 << 20, 8);
      var output = arena.allocate(1 << 20, 8);
      var random = new Random(0x51ab0e1);
      var limits = new SlabLimits(32, 1 << 20);
      for (var plan :
          List.of(
              LayoutTest.choice(),
              LayoutTest.unknowns(),
              LayoutTest.merge(),
              LayoutTest.maps(),
              LayoutTest.lists(),
              LayoutTest.texts(),
              LayoutTest.legacy())) {
        byte[] program = NativeTapeAcceptance.program(plan, List.of(), 1 << 20);
        var schema = arena.allocate(program.length, 8);
        schema.copyFrom(MemorySegment.ofArray(program));
        assertEquals(0, (int) compile.invokeExact(schema, (long) program.length, result));
        var compiled = result.get(ValueLayout.ADDRESS, 0);
        try {
          var sparse = Projection.builder(plan).build();
          var all = Projection.all(plan);
          var selection = Projection.builder(plan);
          for (int type = 0; type < plan.layout().types(); type++) {
            var selectedPlan = plan.layout().plan(type);
            for (int ordinal = 0; ordinal < selectedPlan.size(); ordinal += 2)
              selection.selectType(selectedPlan.name(), selectedPlan.field(ordinal).name());
          }
          var partial = selection.build();
          var modes =
              List.of(
                  all,
                  sparse,
                  sparse.lazyChildren(),
                  all.borrowBytes(1),
                  sparse.lazyChildren().borrowBytes(1),
                  sparse.lax(),
                  partial,
                  partial.lazyChildren().borrowBytes(1),
                  partial.lazyChildren().lax());
          var masks = new ArrayList<MemorySegment>();
          for (var projection : modes) {
            byte[] mask = projection.program();
            var segment = arena.allocate(mask.length, 8);
            segment.copyFrom(MemorySegment.ofArray(mask));
            masks.add(segment);
          }
          for (int i = 0; i < 2000; i++) {
            byte[] wire = LayoutTest.randomWire(random, plan, 0);
            if (wire.length > 0 && i % 7 == 0)
              wire = Arrays.copyOf(wire, random.nextInt(wire.length));
            input.asSlice(0, wire.length).copyFrom(MemorySegment.ofArray(wire));
            for (int mode = 0; mode < modes.size(); mode++) {
              var projection = modes.get(mode);
              byte[] expected = null;
              try {
                expected =
                    SlabCodec.decode(plan.layout(), wire, 0, wire.length, limits, projection);
              } catch (ContractViolation rejected) {
                /* paired native rejection below */
              }
              int flags =
                  (projection.requiresBorrow() ? 8 : 1) | (projection.laxValidation() ? 2 : 0);
              int status =
                  (int)
                      decode.invokeExact(
                          compiled,
                          input,
                          (long) wire.length,
                          flags,
                          masks.get(mode),
                          output,
                          1L << 20,
                          32,
                          1L << 20,
                          result);
              String cell =
                  plan.name() + "/" + i + "/" + mode + " wire=" + HexFormat.of().formatHex(wire);
              if (expected == null) assertNotEquals(0, status, cell);
              else {
                assertEquals(0, status, cell);
                long bytes = result.get(ValueLayout.JAVA_LONG, 0);
                assertArrayEquals(
                    expected, output.asSlice(0, bytes).toArray(ValueLayout.JAVA_BYTE), cell);
              }
            }
          }
        } finally {
          release.invokeExact(compiled);
        }
      }
    }
  }

  @Test
  void producersAgreeByteForByte() throws Throwable {
    Path root =
        Path.of(
            System.getenv("TEST_SRCDIR"), System.getenv("TEST_WORKSPACE"), "protobuf-buffa/native");
    Path library;
    try (var files = Files.list(root)) {
      library =
          files
              .filter(p -> p.toString().endsWith(".so") || p.toString().endsWith(".dylib"))
              .findFirst()
              .orElseThrow();
    }
    try (Arena arena = Arena.ofConfined();
        var decoder =
            dev.elide.truffledata.buffa.BuffaWire.open(
                dev.elide.truffledata.buffa.BuffaWire.Mode.NATIVE, library)) {
      var symbols = SymbolLookup.libraryLookup(library, arena);
      var compile = BuffaFFM.compileLayout(symbols);
      var release = BuffaFFM.releaseLayout(symbols);
      var decode = BuffaFFM.decodeSlab(symbols);
      var result = arena.allocate(32, 8);
      var input = arena.allocate(1 << 20, 8);
      var output = arena.allocate(1 << 20, 8);
      var random = new Random(0x51ab);
      for (var plan :
          List.of(
              LayoutTest.choice(),
              LayoutTest.unknowns(),
              LayoutTest.merge(),
              LayoutTest.maps(),
              LayoutTest.lists(),
              LayoutTest.texts(),
              LayoutTest.legacy())) {
        byte[] program = NativeTapeAcceptance.program(plan, List.of(), 1 << 20);
        var schema = arena.allocate(program.length, 8);
        schema.copyFrom(MemorySegment.ofArray(program));
        assertEquals(0, (int) compile.invokeExact(schema, (long) program.length, result));
        var compiled = result.get(ValueLayout.ADDRESS, 0);
        try (var bridge = decoder.compile(plan, 1 << 20)) {
          var layout = plan.layout();
          for (int i = 0; i < 8000; i++) {
            byte[] wire = LayoutTest.randomWire(random, plan, 0);
            if (wire.length > 0 && i % 7 == 0)
              wire = Arrays.copyOf(wire, random.nextInt(wire.length));
            input.asSlice(0, wire.length).copyFrom(MemorySegment.ofArray(wire));
            byte[] expected = null;
            try {
              expected =
                  SlabCodec.decode(layout, wire, 0, wire.length, new SlabLimits(32, 1 << 20));
            } catch (ContractViolation rejected) {
              // The native call must also reject this malformed input.
            }
            int status =
                (int)
                    decode.invokeExact(
                        compiled,
                        input,
                        (long) wire.length,
                        1,
                        MemorySegment.NULL,
                        output,
                        1L << 20,
                        32,
                        1L << 20,
                        result);
            if (expected == null) assertNotEquals(0, status, plan.name() + "/" + i);
            else {
              assertEquals(0, status, plan.name() + "/" + i);
              long bytes = result.get(ValueLayout.JAVA_LONG, 0);
              assertEquals(expected.length, bytes);
              // Exercise the reusable production bridge as well as the raw seam oracle.
              if (i < 32) {
                byte[] bridgeWire = wire;
                ProtoRow oracle = null;
                try {
                  oracle = plan.decode(wire);
                } catch (ContractViolation required) {
                  // Low-level producers flag missing required fields; owning rows reject them.
                }
                if (oracle == null)
                  assertThrows(
                      ContractViolation.class, () -> bridge.decodeSlab(bridgeWire, 1 << 20));
                else {
                  var message = bridge.decodeSlab(wire, 1 << 20);
                  assertArrayEquals(expected, message.slabCopy());
                  assertArrayEquals(plan.encode(oracle), message.encode());
                }
              }
              assertArrayEquals(
                  expected,
                  output.asSlice(0, bytes).toArray(ValueLayout.JAVA_BYTE),
                  plan.name() + "/" + i);
            }
          }
        } finally {
          release.invokeExact(compiled);
        }
      }
    }
  }

  // ---- WS2: trusted in-process publication (seam ABI 12 logical totals) ----

  private static final SlabLimits LIMITS = new SlabLimits(32, 1 << 20);
  private static final int CERTIFIED = SlabFormat.CERTIFIED;
  private static final int UNKNOWN_TOTAL = -1;

  /** The native producer's report for one decode: header flags and logical totals. */
  private record Report(int status, int extent, int flags, int cells, int stringBytes) {
    boolean exact() {
      return (flags & CERTIFIED) != 0 && cells != UNKNOWN_TOTAL && stringBytes != UNKNOWN_TOTAL;
    }
  }

  private static Report decodeNative(
      java.lang.invoke.MethodHandle decode,
      MemorySegment compiled,
      MemorySegment input,
      MemorySegment output,
      MemorySegment result,
      byte[] wire)
      throws Throwable {
    input.asSlice(0, wire.length).copyFrom(MemorySegment.ofArray(wire));
    int status =
        (int)
            decode.invokeExact(
                compiled,
                input,
                (long) wire.length,
                1,
                MemorySegment.NULL,
                output,
                1L << 20,
                32,
                1L << 20,
                result);
    return new Report(
        status,
        Math.toIntExact(result.get(ValueLayout.JAVA_LONG, BuffaFFM.SlabResult_bytes_OFFSET)),
        result.get(ValueLayout.JAVA_INT, BuffaFFM.SlabResult_flags_OFFSET),
        result.get(ValueLayout.JAVA_INT, BuffaFFM.SlabResult_cells_OFFSET),
        result.get(ValueLayout.JAVA_INT, BuffaFFM.SlabResult_string_bytes_OFFSET));
  }

  /** The Java reference: header check plus the exact {@code validate} walk (the INTERNAL path). */
  private static String internal(ProtoPlan plan, byte[] slab) {
    try {
      return "accepted: "
          + new SlabMessage(plan.layout(), slab, slab.length, SlabMessage.INTERNAL).logicalUsage();
    } catch (ContractViolation rejected) {
      return "rejected: " + rejected.getMessage();
    }
  }

  private static String produced(ProtoPlan plan, byte[] slab, Report report, SlabLimits limits) {
    try {
      return "accepted: "
          + SlabMessage.produced(
                  plan,
                  slab.clone(),
                  slab.length,
                  report.flags(),
                  report.cells(),
                  report.stringBytes(),
                  limits)
              .logicalUsage();
    } catch (ContractViolation rejected) {
      return "rejected: " + rejected.getMessage();
    }
  }

  private static String taken(ProtoPlan plan, byte[] slab, SlabLimits limits) {
    try {
      return "accepted: "
          + SlabMessage.takeOwnership(plan, slab.clone(), slab.length, limits).logicalUsage();
    } catch (ContractViolation rejected) {
      return "rejected: " + rejected.getMessage();
    }
  }

  private static ProtoPlan withLimits(ProtoPlan original, dev.elide.truffledata.Limits limits) {
    var definitions = new ArrayList<ProtoPlan.Type>();
    for (int i = 0; i < original.layout().types(); i++)
      definitions.add(original.layout().plan(i).definition());
    return new ProtoPlan.Model(null, definitions, limits, original.maxDepth(), 1 << 24)
        .type(original.name());
  }

  /** proto2-style non-empty string and bytes defaults, which absent fields charge to the budget. */
  private static ProtoPlan defaults() {
    var greeting =
        new ProtoPlan.FieldPlan(
            "greeting", 1, ProtoPlan.Kind.STRING, false, false, true, false, false, -1, "", null,
            "héllo 😀");
    var blob =
        new ProtoPlan.FieldPlan(
            "blob", 2, ProtoPlan.Kind.BYTES, false, false, true, false, false, -1, "", null,
            DataArray.copyOfBytes(new byte[] {1, 2, 3}));
    return LayoutTest.model(
            LayoutTest.type(
                "Defaults",
                greeting,
                blob,
                LayoutTest.scalar("count", 3, ProtoPlan.Kind.INT32, false),
                LayoutTest.message("child", 4, "Defaults", false),
                LayoutTest.repeated("names", 5, ProtoPlan.Kind.STRING, false)))
        .type("Defaults");
  }

  /**
   * The native producer's logical totals (seam ABI 12 {@code SlabResult.cells}/{@code
   * string_bytes}) equal the Java {@code validate} totals for every slab it certifies as exact, on
   * the golden wires, the differential corpus of every fixture plan and the fuzz harness's plans;
   * when it reports them unknown (an orphaned record subtree) or clears {@code CERTIFIED}, the
   * trusted publication falls back to the Java path and observes exactly what it observed before.
   * Failure injection: budgets one cell or byte below the totals reject through {@code produced}
   * with the same message as {@code takeOwnership}; budgets at the totals accept on both.
   */
  @Test
  void producedTotalsMatchJavaValidation() throws Throwable {
    var node =
        LayoutTest.model(
                LayoutTest.type("Node", LayoutTest.message("children", 1, "Node", true)))
            .type("Node");
    var plans =
        List.of(
            LayoutTest.timestamp(),
            LayoutTest.choice(),
            LayoutTest.unknowns(),
            LayoutTest.merge(),
            LayoutTest.maps(),
            LayoutTest.lists(),
            LayoutTest.texts(),
            LayoutTest.wide(),
            LayoutTest.legacy(),
            LayoutTest.googleType(),
            node,
            defaults());
    var goldens = LayoutTest.goldens();
    try (Arena arena = Arena.ofConfined()) {
      var symbols = SymbolLookup.libraryLookup(library(), arena);
      var compile = BuffaFFM.compileLayout(symbols);
      var release = BuffaFFM.releaseLayout(symbols);
      var decode = BuffaFFM.decodeSlab(symbols);
      var result = arena.allocate(32, 8);
      var input = arena.allocate(1 << 20, 8);
      var output = arena.allocate(1 << 20, 8);
      var random = new Random(0x7a11e5);
      int exactSlabs = 0, unknownSlabs = 0, uncertified = 0, injected = 0;
      for (var plan : plans) {
        byte[] program = NativeTapeAcceptance.program(plan, List.of(), 1 << 20);
        var schema = arena.allocate(program.length, 8);
        schema.copyFrom(MemorySegment.ofArray(program));
        assertEquals(0, (int) compile.invokeExact(schema, (long) program.length, result));
        var compiled = result.get(ValueLayout.ADDRESS, 0);
        int planExact = 0;
        try {
          var wires = new ArrayList<byte[]>();
          for (var golden : goldens)
            if (golden.plan().layout().fingerprint() == plan.layout().fingerprint())
              wires.add(golden.wire());
          if (plan == plans.get(plans.size() - 1)) {
            // Absent defaults: an empty message, a present greeting, a child without either.
            wires.add(new byte[0]);
            wires.add(LayoutTest.wire().string(1, "hi").bytes());
            wires.add(LayoutTest.wire().message(4, LayoutTest.wire().field(3, 1)).bytes());
            wires.add(LayoutTest.wire().bytes(2, new byte[] {9}).string(5, "x").bytes());
          }
          for (int i = 0; i < 6000; i++) {
            byte[] wire = LayoutTest.randomWire(random, plan, 0);
            if (wire.length > 0 && i % 7 == 0)
              wire = Arrays.copyOf(wire, random.nextInt(wire.length));
            wires.add(wire);
          }
          for (int i = 0; i < wires.size(); i++) {
            byte[] wire = wires.get(i);
            Report report = decodeNative(decode, compiled, input, output, result, wire);
            if (report.status() != 0) continue;
            byte[] slab = output.asSlice(0, report.extent()).toArray(ValueLayout.JAVA_BYTE);
            String label = plan.name() + "/" + i;
            String expected = internal(plan, slab);
            if (report.exact()) {
              assertEquals(
                  "accepted: Usage[cells="
                      + Integer.toUnsignedLong(report.cells())
                      + ", stringBytes="
                      + Integer.toUnsignedLong(report.stringBytes())
                      + "]",
                  expected,
                  label);
              exactSlabs++;
              planExact++;
            } else if ((report.flags() & CERTIFIED) == 0) {
              uncertified++;
            } else {
              // Unknown totals only arise from orphaned record subtrees: a oneof clearing a
              // message member, a folded or dropped map entry.
              assertTrue(
                  plan.name().equals("Choice") || plan.name().equals("MapHolder")
                      || plan.name().equals("google.protobuf.Type"),
                  label + ": unknown totals on a plan without orphaning constructs");
              unknownSlabs++;
            }
            // The trusted entry observes exactly what the Java path observed.
            assertEquals(expected, produced(plan, slab, report, LIMITS), label);
            if (report.exact() && i % 41 == 0 && expected.startsWith("accepted")) {
              long cells = Integer.toUnsignedLong(report.cells());
              long bytes = Integer.toUnsignedLong(report.stringBytes());
              var limits = plan.limits();
              for (long[] pair :
                  new long[][] {{cells, bytes}, {cells - 1, bytes}, {cells, bytes - 1}}) {
                // Limits require a positive cell budget and a non-negative byte budget.
                if (pair[0] <= 0 || pair[1] < 0) continue;
                var bound =
                    new dev.elide.truffledata.Limits(
                        limits.maxRows(), limits.maxColumns(), pair[0], pair[1]);
                ProtoPlan bounded;
                try {
                  bounded = withLimits(plan, bound);
                } catch (ContractViolation descriptorBudget) {
                  continue; // the model itself needs more cells than this bound (descriptor fields)
                }
                String viaTrusted = produced(bounded, slab, report, LIMITS);
                assertEquals(taken(bounded, slab, LIMITS), viaTrusted, label + " " + bound);
                assertEquals(internal(bounded, slab), viaTrusted, label + " " + bound);
                if (bound.maxCells() == cells && bound.maxStringBytes() == bytes)
                  assertTrue(viaTrusted.startsWith("accepted"), label + " exact budget");
                else assertTrue(viaTrusted.startsWith("rejected: Protobuf "), label + " " + bound);
                injected++;
              }
            }
          }
        } finally {
          release.invokeExact(compiled);
        }
        assertTrue(planExact > 0, plan.name() + ": no exact native totals");
      }
      assertTrue(exactSlabs > 5_000 && injected > 100, exactSlabs + " exact, " + injected);
      assertTrue(uncertified > 0 && unknownSlabs > 0, uncertified + " uncertified, " + unknownSlabs);
    }
  }

  /**
   * The trust boundary is the in-process array, not its bytes: a native-certified slab whose
   * header is corrupted still fails the O(1) header check through {@code produced}, and a body
   * corruption that {@code produced} cannot see is caught by the external path ({@code copyOf}),
   * which every externally supplied slab takes.
   */
  @Test
  void corruptedCertifiedSlabFailsTheHeaderCheckOrTheExternalPath() throws Throwable {
    var plan = LayoutTest.texts();
    try (Arena arena = Arena.ofConfined()) {
      var symbols = SymbolLookup.libraryLookup(library(), arena);
      var compile = BuffaFFM.compileLayout(symbols);
      var release = BuffaFFM.releaseLayout(symbols);
      var decode = BuffaFFM.decodeSlab(symbols);
      var result = arena.allocate(32, 8);
      var input = arena.allocate(1 << 20, 8);
      var output = arena.allocate(1 << 20, 8);
      byte[] program = NativeTapeAcceptance.program(plan, List.of(), 1 << 20);
      var schema = arena.allocate(program.length, 8);
      schema.copyFrom(MemorySegment.ofArray(program));
      assertEquals(0, (int) compile.invokeExact(schema, (long) program.length, result));
      var compiled = result.get(ValueLayout.ADDRESS, 0);
      try {
        byte[] wire =
            LayoutTest.wire().string(1, "ascii").string(3, "中文").string(6, "many")
                .bytes(7, new byte[] {1, 2, 3}).bytes();
        Report report = decodeNative(decode, compiled, input, output, result, wire);
        assertEquals(0, report.status());
        assertTrue(report.exact(), "fixture must be certified with exact totals");
        byte[] slab = output.asSlice(0, report.extent()).toArray(ValueLayout.JAVA_BYTE);
        assertTrue(produced(plan, slab, report, LIMITS).startsWith("accepted"));
        // Header corruptions: magic, extent, root, layout fingerprint, ownership flags.
        for (int offset :
            new int[] {
              SlabFormat.HEADER_MAGIC,
              SlabFormat.HEADER_BYTES_USED,
              SlabFormat.HEADER_ROOT,
              SlabFormat.HEADER_LAYOUT,
              SlabFormat.HEADER_FLAGS
            }) {
          byte[] broken = slab.clone();
          broken[offset] ^= 0x5A;
          String viaTrusted = produced(plan, broken, report, LIMITS);
          assertTrue(viaTrusted.startsWith("rejected: "), offset + ": " + viaTrusted);
          assertThrows(
              ContractViolation.class,
              () -> SlabMessage.copyOf(plan, java.nio.ByteBuffer.wrap(broken), LIMITS));
          assertThrows(
              ContractViolation.class,
              () -> SlabMessage.takeOwnership(plan, broken.clone(), broken.length, LIMITS));
        }
        // A body corruption (the root's first string slot pointing past the slab): the trusted
        // entry does not walk the bytes, so only the external path reports it.
        byte[] broken = slab.clone();
        int slot = SlabFormat.ROOT + plan.layout().slot(0, 0);
        java.nio.ByteBuffer.wrap(broken)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(slot, slab.length + 64);
        assertThrows(
            ContractViolation.class,
            () -> SlabMessage.copyOf(plan, java.nio.ByteBuffer.wrap(broken), LIMITS));
        assertThrows(
            ContractViolation.class,
            () -> SlabMessage.takeOwnership(plan, broken.clone(), broken.length, LIMITS));
        // Totals that the producer did not certify route the same bytes through the Java walk.
        var unknown = new Report(0, report.extent(), report.flags(), UNKNOWN_TOTAL, UNKNOWN_TOTAL);
        assertEquals(internal(plan, slab), produced(plan, slab, unknown, LIMITS));
        var uncertified =
            new Report(0, report.extent(), report.flags() & ~CERTIFIED, report.cells(),
                report.stringBytes());
        assertEquals(internal(plan, slab), produced(plan, slab, uncertified, LIMITS));
      } finally {
        release.invokeExact(compiled);
      }
    }
  }
}
