package dev.elide.truffledata.csv.cells;

import dev.elide.truffledata.csv.CsvIndexing;
import dev.elide.truffledata.csv.kernel.CsvIndex;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import dev.elide.truffledata.csv.kernel.jvm.FfmIndexer;
import java.nio.file.Path;
import org.jspecify.annotations.NullMarked;

/**
 * Route selection for the CSV Myna cells route ({@code -Dtruffledata.csv.cells=true}, default
 * off). The flag is read once into a {@code static final}, so on the JVM the untaken route folds
 * away; with it off {@link #provider} is exactly {@link FfmIndexer.Library#provider()}.
 */
@NullMarked
public final class CsvCells {
  /** {@code -Dtruffledata.csv.cells}, read once. */
  public static final boolean ENABLED = Boolean.getBoolean("truffledata.csv.cells");

  private CsvCells() {}

  /** The JVM provider for {@code library}: the cells route when {@link #ENABLED}, else FFM. */
  public static CsvIndexing.Provider provider(Path library) {
    if (!ENABLED) return FfmIndexer.Library.load(library).provider();
    return cells(library);
  }

  /** The cells route's provider, regardless of the flag (tests compare both routes). */
  public static CsvIndexing.Provider cells(Path library) {
    CellsFfmIndexer.bind(library.toAbsolutePath().normalize());
    int best = CellsFfmIndexer.best();
    return new CsvIndexing.Provider() {
      @Override
      public String name() {
        return "native-" + CsvIndex.kernelName(best) + "-cells";
      }

      @Override
      public CsvIndexer create(byte delimiter, byte quote, boolean quoting) {
        return new CellsFfmIndexer(delimiter, quote, quoting, best);
      }

      @Override
      public int capacity() {
        return FfmIndexer.PROVIDER_CAPACITY;
      }
    };
  }
}
