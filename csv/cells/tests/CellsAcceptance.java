package dev.elide.truffledata.csv.cells;

import dev.elide.truffledata.csv.CsvIndexing;
import dev.elide.truffledata.csv.kernel.svm.SvmIndexer;

/**
 * Native Image built with {@code -Dtruffledata.csv.cells=true}: the published opt-in path ({@code
 * CsvIndexing.loadNative()}) is the cells route, and it matches the static backend exactly.
 */
public final class CellsAcceptance {
  private CellsAcceptance() {}

  public static void main(String[] args) throws Exception {
    CsvIndexing.Provider routed = CsvIndexing.loadNative();
    CellsParity.require(routed.name().endsWith("-svm-leaf-cells"), "route " + routed.name());
    CellsParity.require(
        routed.create((byte) ',', (byte) '"', true) instanceof CellsSvmIndexer, "indexer class");
    CsvIndexing.Provider baseline = SvmIndexer.provider();
    CellsParity.require(baseline.capacity() == routed.capacity(), "capacity");
    int compared =
        CellsParity.check(
            (d, q, quoting, kernel, split) -> new SvmIndexer(d, q, quoting, kernel, split),
            (d, q, quoting, kernel, split) -> new CellsSvmIndexer(d, q, quoting, kernel, split),
            baseline,
            routed);
    CellsParity.Route base = (d, q, quoting, kernel, split) -> new SvmIndexer(d, q, quoting, kernel, split);
    CellsParity.Route cells =
        (d, q, quoting, kernel, split) -> new CellsSvmIndexer(d, q, quoting, kernel, split);
    CellsParity.handOff(base, cells);
    CellsParity.concurrent(base, cells, 4);
    // Every indexer shares the image's one set of cells.
    CellsParity.require(CellsSvmIndexer.Cells.get() == CellsSvmIndexer.Cells.get(), "shared cells");
    System.out.println(
        "CSV cells acceptance: OK, "
            + CellsParity.describe(routed)
            + " vs "
            + baseline.name()
            + ", "
            + compared
            + " kernel/split pairs");
  }
}
