package dev.elide.truffledata.csv.cells;

import dev.elide.truffledata.csv.CsvIndexing;
import dev.elide.truffledata.csv.kernel.CsvIndex;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The CSV Myna Truffle cells route ({@code -Dtruffledata.csv.cells=true}, default off). {@code
 * FfmIndexer.Backend} calls {@link #jvm} by name when the flag is on; in a Native Image {@link
 * CsvCellsFeature} registers {@link CellsSvmIndexer.Backend} at image build time. Tests construct
 * both routes directly to compare them.
 */
public final class CsvCellsRoute {
  /** The route flag, {@code truffledata.csv.cells}. */
  public static final String PROPERTY = "truffledata.csv.cells";

  private static final Map<Path, CsvIndexing.Provider> LOADED = new ConcurrentHashMap<>();

  private CsvCellsRoute() {}

  /**
   * The HotSpot cells provider for {@code library} ({@code csv_shared}), loaded once per path for
   * the life of the process like the FFM backend's: the best kernel, every call through the FFM
   * cells, with the FFM backend's capacity.
   */
  public static CsvIndexing.Provider jvm(Path library) {
    return LOADED.computeIfAbsent(library.toAbsolutePath().normalize(), CsvCellsRoute::load);
  }

  private static CsvIndexing.Provider load(Path library) {
    CellsFfmIndexer.bind(library);
    int best = CellsFfmIndexer.resolve(0);
    if (best < 0) throw new IllegalStateException("No CSV native kernel: " + best);
    String name = "native-" + CsvIndex.kernelName(best) + "-ffm-cells";
    return new CsvIndexing.Provider() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public CsvIndexer create(byte delimiter, byte quote, boolean quoting) {
        return new CellsFfmIndexer(delimiter, quote, quoting, best, CsvIndex.MAX_CALL_BYTES);
      }

      @Override
      public int capacity() {
        return CellsFfmIndexer.PROVIDER_CAPACITY;
      }
    };
  }
}
