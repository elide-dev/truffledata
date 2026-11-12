package dev.elide.truffledata.buffa;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.myna.truffle.HeapBytes;
import dev.elide.myna.truffle.NativeBytes;
import dev.elide.myna.truffle.NativeCell;
import dev.elide.myna.truffle.NativeCellException;
import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;
import dev.elide.myna.truffle.NativeResource;
import dev.elide.myna.truffle.NativeStatusException;
import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.Limits;
import dev.elide.truffledata.buffa.cells.generated.BuffaCellsJvmTruffle;
import dev.elide.truffledata.protobuf.CellsFixtures;
import dev.elide.truffledata.protobuf.ProtoPlan;
import dev.elide.truffledata.protobuf.SlabLimits;
import dev.elide.truffledata.protobuf.SlabMessage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** HotSpot: the cells flag selects the route once, and the route is byte-identical. */
final class CellsRouteTest {
  static Path library() throws Exception {
    Path root =
        Path.of(
            Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
            Objects.requireNonNull(System.getenv("TEST_WORKSPACE")),
            "protobuf-buffa/native");
    try (var files = Files.list(root)) {
      // The production library, not the test-only other-ABI-class stub beside it.
      return files
          .filter(p -> p.toString().endsWith(".so") || p.toString().endsWith(".dylib"))
          .filter(p -> !p.getFileName().toString().startsWith("other_abi_class"))
          .findFirst()
          .orElseThrow();
    }
  }

  @Test
  void flagSelectsTheBackendOnce() throws Exception {
    boolean expected = Boolean.parseBoolean(System.getProperty("truffledata.buffa.cells.expected"));
    assertEquals(expected, CellsRoute.ENABLED, "flag read at class initialization");
    try (var decoder = BuffaWire.open(BuffaWire.Mode.NATIVE, library())) {
      assertEquals(BuffaWire.Selected.JVM_FFM, decoder.selected());
      var backend = Objects.requireNonNull(decoder.backend);
      assertEquals(expected ? CellsFfmBackend.class : FfmBackend.class, backend.getClass());
      // The guest-facing entries run on the selected backend.
      var plan = CellsFixtures.plans().get(1);
      byte[] wire = CellsFixtures.randomWire(new Random(7), plan);
      try (var compiled = decoder.compile(plan, 1 << 20);
          var input = decoder.allocate(wire.length).put(wire);
          SlabMessage heap = compiled.decodeSlab(wire, 1 << 20);
          SlabMessage nativeInput = compiled.decodeSlab(input, 1 << 20)) {
        assertArrayEquals(heap.slabCopy(), nativeInput.slabCopy());
      }
    }
    // A property change after initialization does not move the route.
    String previous = System.getProperty(CellsRoute.PROPERTY);
    try {
      System.setProperty(CellsRoute.PROPERTY, Boolean.toString(!expected));
      try (var decoder = BuffaWire.open(BuffaWire.Mode.NATIVE, library())) {
        assertEquals(
            expected ? CellsFfmBackend.class : FfmBackend.class,
            Objects.requireNonNull(decoder.backend).getClass());
      }
    } finally {
      if (previous == null) System.clearProperty(CellsRoute.PROPERTY);
      else System.setProperty(CellsRoute.PROPERTY, previous);
    }
  }

  private static <T extends NativeCell> T adopt(T cell) {
    new NativeCellRootNode(cell).adoptChildren();
    return cell;
  }

  private static MemorySegment copy(Arena arena, byte[] bytes) {
    MemorySegment segment = arena.allocate(Math.max(1, bytes.length), 8).asSlice(0, bytes.length);
    MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, 0, bytes.length);
    return segment;
  }

  private static long word(byte[] struct, int offset) {
    return MemorySegment.ofArray(struct).get(ValueLayout.JAVA_LONG_UNALIGNED, offset);
  }

  /**
   * The ownership of protobuf-buffa/cells/ownership.seam (Myna 0.18.0), as a guest calls the
   * cells: compileLayout returns a {@code Layout} owner (its schema a call borrow, its struct
   * copied to the guest's buffer), decodeSlabSmall takes that owner and two borrowed buffers whose
   * lengths the cell derives, releaseLayout closes the owner once, and the slabs equal the FFM
   * backend's. A closed scope and a closed owner are refused before native code runs; a bad schema
   * makes no owner and surfaces the native status.
   */
  @Test
  void guestCallsOwnLayoutsAndBorrowTheirBuffers() throws Exception {
    Path library = library();
    ProtoPlan plan = CellsFixtures.plans().get(1);
    byte[] program =
        NativeSchema.encode(NativeSchema.types(plan, List.of(), 1 << 20), List.of(), 1 << 20);
    byte[] wire = CellsFixtures.randomWire(new Random(11), plan);
    var limits = new SlabLimits(32, 1 << 20);
    int capacity = 4 * wire.length + 256;
    try (var cells = new CellsFfmBackend(library); // binds the library for the cells
        var reference = new FfmBackend(library);
        Arena arena = Arena.ofConfined()) {
      // The production route's slab of the same wire.
      byte[] expected;
      long left;
      try (var schema = reference.allocate(program.length).put(program)) {
        left = reference.compileLayout(schema);
      }
      try (var input = reference.allocate(wire.length).put(wire);
          var output = reference.allocate(capacity)) {
        int extent =
            reference.writeSlab(reference.prepareSlab(left), input, output, limits, 1, null);
        assertTrue(extent > 0, Integer.toString(extent));
        expected =
            ((MemorySegment) Objects.requireNonNull(output.lease.attachment))
                .asSlice(0, extent)
                .toArray(ValueLayout.JAVA_BYTE);
      } finally {
        reference.releaseLayout(left);
      }

      var compile = adopt(new BuffaCellsJvmTruffle.compileLayout_Cell());
      var release = adopt(new BuffaCellsJvmTruffle.releaseLayout_Cell());
      var slab = adopt(new BuffaCellsJvmTruffle.decodeSlabSmall_Cell());
      MemorySegment result = arena.allocate(32, 8);
      // Owners from heap bytes and from a NativeBytes view; the guest's struct is a copy.
      byte[] struct = new byte[24];
      var fromHeap =
          (NativeResource)
              compile.execute(
                  new Object[] {new HeapBytes(program, false), new HeapBytes(struct, true)});
      assertEquals(fromHeap.address(), word(struct, 0), "handle copied to the guest's struct");
      assertTrue(word(struct, 8) > 0, "type count");
      var fromNative =
          (NativeResource)
              compile.execute(
                  new Object[] {
                    NativeBytes.of(copy(arena, program), false),
                    NativeBytes.of(arena.allocate(24, 8), true)
                  });
      // The length is no longer a guest argument.
      assertThrows(
          RuntimeException.class,
          () ->
              compile.execute(
                  new Object[] {
                    new HeapBytes(program, false),
                    (long) program.length,
                    new HeapBytes(new byte[24], true)
                  }));
      for (var owner : new NativeResource[] {fromHeap, fromNative}) {
        assertEquals("Layout", owner.type());
        for (Object data :
            new Object[] {new HeapBytes(wire, false), NativeBytes.of(copy(arena, wire), false)}) {
          byte[] out = new byte[capacity];
          int status =
              slab.executeInt(
                  new Object[] {
                    owner,
                    data,
                    1,
                    null,
                    new HeapBytes(out, true),
                    32,
                    (long) (1 << 20),
                    NativePointer.of(result.address())
                  });
          assertEquals(0, status);
          long bytes = result.get(ValueLayout.JAVA_LONG, 0);
          assertArrayEquals(
              expected, Arrays.copyOf(out, (int) bytes), "slab equals the FFM backend's");
        }
        // Exactly once: the destroy cell closes it; a second destroy or close releases nothing
        // (Myna: a no-op, not an error).
        release.execute(new Object[] {owner});
        assertTrue(owner.isClosed());
        release.execute(new Object[] {owner});
        owner.close();
        // Use after close is refused before native code runs.
        assertThrows(NativeCellException.class, owner::address);
        assertThrows(
            NativeCellException.class,
            () ->
                slab.executeInt(
                    new Object[] {
                      owner,
                      new HeapBytes(wire, false),
                      1,
                      null,
                      new HeapBytes(new byte[capacity], true),
                      32,
                      (long) (1 << 20),
                      NativePointer.of(result.address())
                    }));
      }
      // The destroy takes only a Layout owner, never a bare pointer.
      assertThrows(
          NativeCellException.class,
          () -> release.execute(new Object[] {NativePointer.of(arena.allocate(8).address())}));

      // A schema in a closed scope is refused; nothing is created.
      Arena closed = Arena.ofShared();
      MemorySegment stale = copy(closed, program);
      closed.close();
      assertThrows(
          RuntimeException.class,
          () ->
              compile.execute(
                  new Object[] {NativeBytes.of(stale, false), new HeapBytes(new byte[24], true)}));

      // A bad schema: no owner, the native status (1, malformed) on NativeStatusException; the
      // production route maps the same status to "Malformed protobuf input".
      byte[] bad = {1, 2, 3, 4, 5, 6, 7, 8, 9};
      byte[] failed = new byte[24];
      Arrays.fill(failed, (byte) 0x5a);
      var failure =
          assertThrows(
              NativeStatusException.class,
              () ->
                  compile.execute(
                      new Object[] {new HeapBytes(bad, false), new HeapBytes(failed, true)}));
      assertEquals(1, failure.status());
      assertArrayEquals(new byte[24], failed, "the struct as the failed create left it");
      try (var schema = reference.allocate(bad.length).put(bad)) {
        var production =
            assertThrows(ContractViolation.class, () -> reference.compileLayout(schema));
        assertEquals("Malformed protobuf input", production.getMessage());
      }
    }
  }

  /**
   * The in-place {@code Result} of ownership.seam: decode initializes the runtime's struct and
   * returns its owner, release frees the field index once, and the index equals the FFM backend's.
   * A failing decode makes no owner and carries its status.
   */
  @Test
  void guestDecodeOwnsItsResultInPlace() throws Exception {
    Path library = library();
    ProtoPlan plan = CellsFixtures.plans().get(1);
    byte[] wire = CellsFixtures.randomWire(new Random(13), plan);
    try (var cells = new CellsFfmBackend(library);
        var reference = new FfmBackend(library);
        Arena arena = Arena.ofConfined()) {
      byte[] expected;
      try (var input = reference.allocate(wire.length).put(wire);
          var tape = reference.decode(input, Limits.DEFAULT, 32, 1 << 20)) {
        var bytes = tape.bytes().duplicate();
        expected = new byte[bytes.remaining()];
        bytes.get(expected);
      }
      var decode = adopt(new BuffaCellsJvmTruffle.decode_Cell());
      var release = adopt(new BuffaCellsJvmTruffle.release_Cell());
      for (Object data :
          new Object[] {new HeapBytes(wire, false), NativeBytes.of(copy(arena, wire), false)}) {
        byte[] struct = new byte[24];
        var owner =
            (NativeResource)
                decode.execute(
                    new Object[] {
                      data,
                      Limits.DEFAULT.maxCells(),
                      32,
                      (long) (1 << 20),
                      new HeapBytes(struct, true)
                    });
        assertEquals("Result", owner.type());
        // The owner is the runtime's struct; the guest's buffer holds a copy of it.
        MemorySegment live = MemorySegment.ofAddress(owner.address()).reinterpret(24);
        assertArrayEquals(live.toArray(ValueLayout.JAVA_BYTE), struct);
        long count = word(struct, 8);
        byte[] fields =
            count == 0
                ? new byte[0]
                : MemorySegment.ofAddress(word(struct, 0))
                    .reinterpret(24 * count)
                    .toArray(ValueLayout.JAVA_BYTE);
        assertArrayEquals(expected, fields, "index equals the FFM backend's");
        release.execute(new Object[] {owner});
        assertTrue(owner.isClosed());
        release.execute(new Object[] {owner});
        assertThrows(NativeCellException.class, owner::address);
      }
      // Depth 0 is a budget failure (status 2): no owner.
      var failure =
          assertThrows(
              NativeStatusException.class,
              () ->
                  decode.execute(
                      new Object[] {
                        new HeapBytes(wire, false),
                        Limits.DEFAULT.maxCells(),
                        0,
                        (long) (1 << 20),
                        new HeapBytes(new byte[24], true)
                      }));
      assertEquals(2, failure.status());
      // utf8_to_utf16: the input is borrowed, the ptr<u16> output stays an address.
      byte[] text = "aé中".getBytes(StandardCharsets.UTF_8);
      MemorySegment units = arena.allocate(2L * text.length, 2);
      long written =
          adopt(new BuffaCellsJvmTruffle.utf8_to_utf16_Cell())
              .executeLong(
                  new Object[] {
                    new HeapBytes(text, false),
                    NativePointer.of(units.address()),
                    (long) text.length
                  });
      assertEquals(3, written);
      assertEquals(
          "aé中", new String(units.asSlice(0, 6).toArray(ValueLayout.JAVA_CHAR)));
    }
  }

  @Test
  void cellsRouteIsByteIdentical() throws Exception {
    long compared = CellsParity.run(library());
    System.out.println("CELLS parity outcomes=" + compared);
  }

  @Test
  void cellsRouteRejectsAWrongAbi() {
    Path wrong =
        Path.of(
            Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
            Objects.requireNonNull(System.getenv("TEST_WORKSPACE")),
            "protobuf-buffa/tests/wrong_abi.so");
    assertThrows(IllegalStateException.class, () -> new CellsFfmBackend(wrong));
  }
}
