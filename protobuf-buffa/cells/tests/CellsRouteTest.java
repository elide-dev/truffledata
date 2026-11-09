package dev.elide.truffledata.buffa;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.protobuf.CellsFixtures;
import dev.elide.truffledata.protobuf.SlabMessage;
import java.nio.file.Files;
import java.nio.file.Path;
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
      return files
          .filter(p -> p.toString().endsWith(".so") || p.toString().endsWith(".dylib"))
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
