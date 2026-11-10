package dev.elide.truffledata.csv.kernel.jvm;

import dev.elide.truffledata.csv.CsvIndexing;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

/**
 * The opt-in Myna Truffle cells route of the native CSV kernel, {@code
 * -Dtruffledata.csv.cells=true} (default off): the kernel's calls go through the cells of the
 * separate {@code truffledata-csv-cells} artifact. On HotSpot the flag is read once, when this
 * holder initializes (a {@code static final} the JIT folds), and selects the provider in {@link
 * FfmIndexer.Backend#load}; nothing on the index path tests it. In a Native Image the route is
 * chosen when the image is built ({@code CsvCellsFeature}).
 */
final class CellsRoute {
  static final String PROPERTY = "truffledata.csv.cells";
  static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
  static final String ROUTE = "dev.elide.truffledata.csv.cells.CsvCellsRoute";

  private CellsRoute() {}

  /** The cells provider for {@code library}; never the FFM provider (no silent fallback). */
  static CsvIndexing.Provider jvm(Path library) {
    Class<?> type;
    try {
      type = Class.forName(ROUTE, true, CellsRoute.class.getClassLoader());
    } catch (ClassNotFoundException missing) {
      throw new IllegalStateException(
          "-D" + PROPERTY + "=true needs truffledata-csv-cells on the class path", missing);
    }
    try {
      return (CsvIndexing.Provider) type.getMethod("jvm", Path.class).invoke(null, library);
    } catch (InvocationTargetException failure) {
      // The route's own failures keep their types, like the FFM backend's.
      if (failure.getCause() instanceof RuntimeException exception) throw exception;
      if (failure.getCause() instanceof Error error) throw error;
      throw new IllegalStateException("CSV cells provider", failure.getCause());
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("CSV cells provider", failure);
    }
  }
}
