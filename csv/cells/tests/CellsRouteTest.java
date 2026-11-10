package dev.elide.truffledata.csv.cells;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.myna.truffle.NativeCell;
import dev.elide.truffledata.csv.CsvIndexing;
import dev.elide.truffledata.csv.kernel.jvm.FfmIndexer;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** HotSpot: the cells flag selects the route once; the route matches the FFM baseline exactly. */
final class CellsRouteTest {
  static Path runfile(String path) {
    return Path.of(
        Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
        Objects.requireNonNull(System.getenv("TEST_WORKSPACE")),
        path);
  }

  static Path library() {
    return runfile("csv/native/csv_shared.so").toAbsolutePath().normalize();
  }

  @Test
  void flagSelectsTheRouteOnce() {
    boolean expected = Boolean.parseBoolean(System.getProperty("truffledata.csv.cells.expected"));
    assertEquals(expected, Boolean.getBoolean(CsvCellsRoute.PROPERTY));
    CsvIndexing.Provider provider = CsvIndexing.loadNative(library());
    assertTrue(
        provider.name().endsWith(expected ? "-ffm-cells" : "-ffm"), provider.name());
    assertEquals(FfmIndexer.PROVIDER_CAPACITY, provider.capacity());
    // The indexers it creates are the route's own.
    var indexer = provider.create((byte) ',', (byte) '"', true);
    assertEquals(expected ? CellsFfmIndexer.class : FfmIndexer.class, indexer.getClass());
    // A property change after the first load does not move the route.
    String previous = System.getProperty(CsvCellsRoute.PROPERTY);
    try {
      System.setProperty(CsvCellsRoute.PROPERTY, Boolean.toString(!expected));
      assertTrue(
          CsvIndexing.loadNative(library()).name().endsWith(expected ? "-ffm-cells" : "-ffm"));
    } finally {
      if (previous == null) System.clearProperty(CsvCellsRoute.PROPERTY);
      else System.setProperty(CsvCellsRoute.PROPERTY, previous);
    }
  }

  @Test
  void cellsMatchTheFfmBaseline() throws Exception {
    Path library = library();
    CsvIndexing.Provider routed = CsvCellsRoute.jvm(library);
    FfmIndexer.Library baseline = FfmIndexer.Library.load(library);
    assertEquals(FfmIndexer.PROVIDER_CAPACITY, routed.capacity());
    assertEquals(CellsFfmIndexer.PROVIDER_CAPACITY, FfmIndexer.PROVIDER_CAPACITY);
    int compared =
        CellsParity.check(
            (d, q, quoting, kernel, split) -> new FfmIndexer(baseline, d, q, quoting, kernel, split),
            (d, q, quoting, kernel, split) -> new CellsFfmIndexer(d, q, quoting, kernel, split),
            baseline.provider(),
            routed);
    assertTrue(compared >= 2, "kernels compared: " + compared);
  }

  @Test
  void secondLibraryIsRejected() {
    CsvCellsRoute.jvm(library());
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> CellsFfmIndexer.bind(runfile("csv/native/other_abi_class.so").toAbsolutePath()));
    assertTrue(failure.getMessage().contains("bound to"), failure.getMessage());
  }

  /**
   * Brief item 2: every scalar-result cell is held as its concrete generated type, which overrides
   * the typed method, and no call site routes a scalar cell through the generic execute (only the
   * pointer-result fingerprint cell may use it).
   */
  @Test
  void scalarCellsUseTypedCalls() throws Exception {
    for (Class<?> route : List.of(CellsFfmIndexer.class, CellsSvmIndexer.class)) {
      int cells = 0;
      for (Field field : route.getDeclaredFields()) {
        if (!NativeCell.class.isAssignableFrom(field.getType())) continue;
        cells++;
        assertNotEquals(NativeCell.class, field.getType(), field.toString());
        assertEquals(
            field.getType(),
            field.getType().getDeclaredMethod("executeLong", Object[].class).getDeclaringClass(),
            field + " must override executeLong");
      }
      assertEquals(3, cells, route + " cells");
    }
    for (String source :
        List.of(
            "csv/cells/src/main/java/dev/elide/truffledata/csv/cells/CellsFfmIndexer.java",
            "csv/cells/src/main/java/dev/elide/truffledata/csv/cells/CellsSvmIndexer.java")) {
      List<String> lines = Files.readAllLines(runfile(source));
      int typed = 0;
      for (String line : lines) {
        if (line.contains(".execute(") || line.contains(".execute (")) {
          assertTrue(line.contains("fingerprint_Cell"), source + ": generic execute: " + line);
        }
        if (line.contains(".executeLong(") || line.contains(".executeInt(")) typed++;
      }
      assertTrue(typed >= 3, source + ": typed calls " + typed);
    }
  }
}
