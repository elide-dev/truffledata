package dev.elide.truffledata.buffa;

import dev.elide.truffledata.buffa.tests.Acceptance;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * The Native Image acceptance suite on an image built with {@code -Dtruffledata.buffa.cells=true}:
 * the static backend's slab producer calls go through the generated cells, the full shared
 * acceptance (semantics, budgets, ownership, randomized parity) passes unchanged, and the cells
 * route is byte-identical to the static backend it wraps.
 */
@NullMarked
public final class CellsAcceptance {
  private CellsAcceptance() {}

  public static void main(String[] args) throws Exception {
    try (var decoder = BuffaWire.open(BuffaWire.Mode.NATIVE, null)) {
      var backend = Objects.requireNonNull(decoder.backend);
      if (!(backend instanceof CellsSvmBackend))
        throw new AssertionError("Image built with the cells flag selected " + backend.getClass());
    }
    Acceptance.main(args);
    System.out.println("CELLS parity outcomes=" + CellsParity.run(null));
  }
}
