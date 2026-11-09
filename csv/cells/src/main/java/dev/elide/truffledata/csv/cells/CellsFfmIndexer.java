package dev.elide.truffledata.csv.cells;

import dev.elide.myna.truffle.NativeCell;
import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;
import dev.elide.truffledata.TargetAbi;
import dev.elide.truffledata.csv.cells.generated.CsvCellsJvmFFM;
import dev.elide.truffledata.csv.cells.generated.CsvCellsJvmTruffle;
import dev.elide.truffledata.csv.kernel.CsvIndex;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import dev.elide.truffledata.csv.kernel.generated.CsvNativeAbi;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The JVM (FFM) indexer of the CSV cells route: every {@code td_csv_index} and {@code
 * td_csv_kernel} call goes through the generated cells, held as their concrete {@code *_Cell} types
 * and called through the specialized typed methods (Myna 0.14.0): {@code index} ({@code i64})
 * through {@code executeLong}, {@code kernel} ({@code i32}) through {@code executeInt}; no result
 * box and no {@code UnexpectedResultException} handler.
 *
 * <p>Heap input is copied into confined native buffers (the production FFM copy path) and the
 * entries copied back; the packed result and carry rule are the production {@code FfmIndexer}'s, so
 * index results are identical. Confined to the creating thread, like {@code FfmIndexer}.
 */
@NullMarked
final class CellsFfmIndexer extends CsvIndexer implements AutoCloseable {
  private static @Nullable Path loaded;

  private final CsvCellsJvmTruffle.index_Cell index;
  private final int format;
  private final int kernel;
  private final String name;
  private @Nullable Arena buffers;
  private MemorySegment input = MemorySegment.NULL;
  private MemorySegment output = MemorySegment.NULL;

  CellsFfmIndexer(byte delimiter, byte quote, boolean quoting, int kernel) {
    super(delimiter, quote, quoting);
    index = adopt(new CsvCellsJvmTruffle.index_Cell());
    this.kernel = kernel;
    format = (delimiter & 0xFF) | (quote & 0xFF) << 8 | (quoting ? 1 << 16 : 0);
    name = "native-" + CsvIndex.kernelName(kernel) + "-cells";
  }

  /** Loads {@code library} once per class loader and verifies the production fingerprint. */
  static synchronized void bind(Path library) {
    if (loaded == null) {
      System.load(library.toString());
      String expected = CsvNativeAbi.fingerprint(TargetAbi.HOST.charUnsigned());
      String actual;
      try {
        actual = CsvCellsJvmFFM.fingerprint().reinterpret(expected.length() + 1).getString(0);
      } catch (Throwable failure) {
        throw new IllegalStateException(failure);
      }
      // The cells' own constant folds in the cells options; the library carries the production ABI
      // of the host's class; never a fallback.
      if (!actual.equals(expected))
        throw new IllegalStateException(
            "Myna/CSV ABI fingerprint mismatch (expected the " + TargetAbi.HOST + " class)");
      loaded = library;
    } else if (!loaded.equals(library))
      throw new IllegalStateException(
          "CSV cells are bound to " + loaded + " in this class loader, not " + library);
  }

  /** {@code td_csv_kernel(0)} through its cell: the best kernel this CPU supports. */
  static int best() {
    CsvCellsJvmTruffle.kernel_Cell cell = adopt(new CsvCellsJvmTruffle.kernel_Cell());
    int best = cell.executeInt(new Object[] {0});
    if (best < 0) throw new IllegalStateException("No CSV native kernel: " + best);
    return best;
  }

  private static <T extends NativeCell> T adopt(T cell) {
    // A cell holds call-site profiles and is adopted once, under its own language-neutral root.
    new NativeCellRootNode(cell).adoptChildren();
    return cell;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public int index(byte[] input, int from, int length, int[] out) {
    CsvIndex.checkCall(input, from, length, out);
    ensure(length);
    MemorySegment.copy(input, from, this.input, ValueLayout.JAVA_BYTE, 0, length);
    long result =
        index.executeLong(
            new Object[] {
              NativePointer.of(this.input.address()),
              (long) length,
              NativePointer.of(output.address()),
              format,
              kernel,
              carry
            });
    if (result > 0)
      MemorySegment.copy(output, ValueLayout.JAVA_INT, 0, out, 0, (int) (result >>> 8));
    if (result < 0) throw new IllegalStateException("CSV native index failed: " + result);
    carry = result & 0xFF;
    return (int) (result >>> 8);
  }

  private void ensure(int length) {
    if (input.byteSize() >= Math.max(length, 1)) return;
    long size =
        Math.max(Math.max(length, 64), Math.min(2 * input.byteSize(), CsvIndex.MAX_CALL_BYTES));
    Arena grown = Arena.ofConfined();
    try {
      input = grown.allocate(size, 64);
      output = grown.allocate(4 * size, 64);
    } catch (RuntimeException | Error failure) {
      grown.close();
      throw failure;
    }
    Arena old = buffers;
    if (old != null) old.close();
    buffers = grown;
  }

  @Override
  public void close() {
    Arena b = buffers;
    if (b != null) b.close();
    buffers = null;
    input = output = MemorySegment.NULL;
  }
}
