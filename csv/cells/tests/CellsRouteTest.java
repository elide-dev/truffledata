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

  /**
   * The borrows of csv/cells/ownership.seam (Myna 0.18.0), as a guest calls the cells: each byte
   * buffer is one argument whose length the cell derives, heap bytes are copied for the call,
   * native views are lent in place, and a segment whose scope is closed is refused before native
   * code runs.
   */
  @Test
  void guestCallsBorrowTheirBuffers() {
    CsvCellsRoute.jvm(library());
    var validate = CellsFfmIndexer.Cells.VALIDATE;
    byte[] valid = "a,b,\u00e9\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] invalid = {'a', ',', (byte) 0xC3, '\n'};
    assertEquals(-1, validate.executeLong(new Object[] {new dev.elide.myna.truffle.HeapBytes(valid, false)}));
    assertEquals(2, validate.executeLong(new Object[] {new dev.elide.myna.truffle.HeapBytes(invalid, false)}));
    // The length is no longer a guest argument.
    assertThrows(
        RuntimeException.class,
        () -> validate.executeLong(new Object[] {new dev.elide.myna.truffle.HeapBytes(valid, false), (long) valid.length}));
    try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
      var segment = arena.allocate(invalid.length);
      java.lang.foreign.MemorySegment.copy(invalid, 0, segment, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, invalid.length);
      assertEquals(2, validate.executeLong(new Object[] {dev.elide.myna.truffle.NativeBytes.of(segment, false)}));
      // The index cell: `data` borrowed, `out` a raw address, the packed count << 8 | carry.
      var out = arena.allocate(4L * invalid.length, 4);
      long packed =
          CellsFfmIndexer.Cells.INDEX.executeLong(
              new Object[] {
                new dev.elide.myna.truffle.HeapBytes(invalid, false),
                dev.elide.myna.truffle.NativePointer.of(out.address()),
                ',' | '"' << 8 | 1 << 16,
                0,
                0L
              });
      assertTrue(packed >= 0 && (packed >>> 8) == 2, Long.toString(packed));
    }
    java.lang.foreign.Arena closed = java.lang.foreign.Arena.ofShared();
    var stale = closed.allocate(8);
    closed.close();
    assertThrows(
        RuntimeException.class,
        () -> validate.executeLong(new Object[] {dev.elide.myna.truffle.NativeBytes.of(stale, false)}));
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
  void cellsAreSharedAndSafeAcrossThreads() throws Exception {
    Path library = library();
    CsvCellsRoute.jvm(library);
    FfmIndexer.Library baseline = FfmIndexer.Library.load(library);
    CellsParity.Route base =
        (d, q, quoting, kernel, split) -> new FfmIndexer(baseline, d, q, quoting, kernel, split);
    CellsParity.Route cells =
        (d, q, quoting, kernel, split) -> new CellsFfmIndexer(d, q, quoting, kernel, split);
    // The copy buffers are not confined to the thread of the first call (and so are collectable:
    // scanners never close their indexer).
    CellsParity.handOff(base, cells);
    CellsParity.concurrent(base, cells, 4);
  }

  /** An indexer that was never closed, and one that was, both keep working and hold no arena. */
  @Test
  void buffersNeedNoClose() throws Exception {
    CsvCellsRoute.jvm(library());
    for (Field field : CellsFfmIndexer.class.getDeclaredFields())
      assertFalse(
          java.lang.foreign.Arena.class.isAssignableFrom(field.getType()),
          "an arena field needs a close no scanner makes: " + field);
    CellsFfmIndexer indexer = new CellsFfmIndexer((byte) ',', (byte) '"', true, 0, 1 << 20);
    byte[] input = "a,b\n1,2\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    int[] out = new int[64];
    int first = indexer.index(input, 0, input.length, out);
    assertTrue(first > 0, "entries " + first);
    indexer.close();
    indexer.carry(0);
    assertEquals(first, indexer.index(input, 0, input.length, out));
    assertEquals(-1, indexer.validateUtf8(input, 0, input.length));
  }

  @Test
  void secondLibraryIsRejected() {
    CsvCellsRoute.jvm(library());
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> CellsFfmIndexer.bind(runfile("csv/native/other_abi_class.so").toAbsolutePath()));
    assertTrue(failure.getMessage().contains("bound to"), failure.getMessage());
    // The bound library still binds.
    CellsFfmIndexer.bind(library());
  }

  /**
   * The call shape of each route. HotSpot: every scalar-result cell is held as its concrete
   * generated type, which overrides the typed method, and no call site routes a scalar cell through
   * the generic execute. Native Image: host code calls each cell's static {@code invoke} (Myna
   * 0.16.0: native carriers and raw addresses, nothing allocated) and holds no cell, argument
   * array, box or {@code NativePointer} at all.
   */
  @Test
  void cellCallsHaveTheRoutesShape() throws Exception {
    // HotSpot: index, leaf and validate in the indexer; those and kernel in the shared holder.
    int cells = 0;
    List<Class<?>> holders = new java.util.ArrayList<>(List.of(CellsFfmIndexer.class.getDeclaredClasses()));
    holders.add(CellsFfmIndexer.class);
    for (Class<?> holder : holders)
      for (Field field : holder.getDeclaredFields()) {
        if (!NativeCell.class.isAssignableFrom(field.getType())) continue;
        cells++;
        assertNotEquals(NativeCell.class, field.getType(), field.toString());
        String typed = field.getType().getSimpleName().equals("kernel_Cell") ? "executeInt" : "executeLong";
        assertEquals(
            field.getType(),
            field.getType().getDeclaredMethod(typed, Object[].class).getDeclaringClass(),
            field + " must override " + typed);
      }
    assertEquals(7, cells, "HotSpot cells");
    String jvm = "csv/cells/src/main/java/dev/elide/truffledata/csv/cells/CellsFfmIndexer.java";
    int typed = 0;
    for (String line : Files.readAllLines(runfile(jvm))) {
      assertFalse(line.contains(".execute(") || line.contains(".execute ("), jvm + ": generic execute: " + line);
      if (line.contains(".executeLong(") || line.contains(".executeInt(")) typed++;
    }
    assertTrue(typed >= 3, jvm + ": typed calls " + typed);

    // Native Image: no cell instance and nothing a cell's execute would need.
    for (Field field : CellsSvmIndexer.class.getDeclaredFields())
      assertFalse(NativeCell.class.isAssignableFrom(field.getType()), "host code holds no cell: " + field);
    assertEquals(0, CellsSvmIndexer.class.getDeclaredClasses().length - 1, "only the Backend is nested");
    String svm = "csv/cells/src/main/java/dev/elide/truffledata/csv/cells/CellsSvmIndexer.java";
    String text = Files.readString(runfile(svm));
    // Comments describe execute; only code is scanned.
    String code = text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    for (String forbidden : List.of(".execute", "new Object[", "NativePointer", "NativeCell", "Long.valueOf", "Integer.valueOf"))
      assertFalse(code.contains(forbidden), svm + " uses " + forbidden);
    for (String cell : List.of("index_Cell", "index_leaf_Cell", "validate_utf8_Cell", "kernel_Cell", "fingerprint_Cell"))
      assertTrue(code.contains("CsvCellsTruffle." + cell + ".invoke("), svm + " must call " + cell + ".invoke");
    // Each generated cell declares the static entry the route calls.
    Class<?> generated =
        Class.forName(
            "dev.elide.truffledata.csv.kernel.cells.generated.CsvCellsTruffle",
            false,
            CellsSvmIndexer.class.getClassLoader());
    for (Class<?> cell : generated.getDeclaredClasses()) {
      if (!NativeCell.class.isAssignableFrom(cell)) continue;
      boolean entry = false;
      for (var method : cell.getDeclaredMethods())
        entry |= method.getName().equals("invoke") && java.lang.reflect.Modifier.isStatic(method.getModifiers());
      assertTrue(entry, cell + " has no static invoke");
    }
  }
}
