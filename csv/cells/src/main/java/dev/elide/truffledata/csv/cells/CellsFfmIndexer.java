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
import org.jspecify.annotations.Nullable;

/**
 * The HotSpot indexer of the CSV cells route: every {@code td_csv_index_leaf}, {@code td_csv_index}
 * and {@code td_csv_validate_utf8} call goes through a cell generated with {@code
 * truffle_backend=ffm}, held as its concrete {@code *_Cell} type and called through the specialized
 * {@code executeLong} (Myna 0.14.0: no result box, no {@code UnexpectedResultException} handler).
 *
 * <p>The checks, the packed result, the carry rule and the error messages are {@code FfmIndexer}'s,
 * so results and errors are identical. Myna's FFM cells take no heap segments (HotSpot copies heap
 * borrows), so heap input always goes through {@code FfmIndexer}'s copy path: confined native
 * buffers, then the entries copied back. Calls of at most {@code criticalBytes} use the leaf, the
 * size split {@code FfmIndexer} makes. The JVM is a correctness target. Confined to the creating
 * thread, like {@code FfmIndexer}.
 *
 * <p>Myna's FFM cells bind their symbols through the loader lookup, so the library is loaded into
 * this class loader ({@link System#load}). One library per class loader: a second path is rejected
 * rather than silently called through the first.
 */
final class CellsFfmIndexer extends CsvIndexer implements AutoCloseable {
  /** Index entries per provider call, as {@code FfmIndexer.PROVIDER_CAPACITY}. */
  static final int PROVIDER_CAPACITY = 1 << 16;

  private static @Nullable Path loaded;

  private final CsvCellsJvmTruffle.index_Cell index;
  private final CsvCellsJvmTruffle.index_leaf_Cell leaf;
  private final CsvCellsJvmTruffle.validate_utf8_Cell validate;
  private final int format;
  private final int kernel;
  private final int criticalBytes;
  private final String name;
  private @Nullable Arena buffers;
  private MemorySegment input = MemorySegment.NULL;
  private MemorySegment output = MemorySegment.NULL;

  CellsFfmIndexer(byte delimiter, byte quote, boolean quoting, int kernel, int criticalBytes) {
    super(delimiter, quote, quoting);
    int resolved = resolve(kernel);
    if (resolved < 0) throw new IllegalArgumentException("CSV native kernel " + kernel);
    index = adopt(new CsvCellsJvmTruffle.index_Cell());
    leaf = adopt(new CsvCellsJvmTruffle.index_leaf_Cell());
    validate = adopt(new CsvCellsJvmTruffle.validate_utf8_Cell());
    this.kernel = resolved;
    this.criticalBytes = criticalBytes;
    format = (delimiter & 0xFF) | (quote & 0xFF) << 8 | (quoting ? 1 << 16 : 0);
    name = "native-" + CsvIndex.kernelName(resolved) + "-ffm-cells";
  }

  /** Loads {@code library} once per class loader and verifies the production fingerprint. */
  static synchronized void bind(Path library) {
    if (loaded == null) {
      System.load(library.toString());
      // The cells' own constant folds in the cells options; the library carries the production ABI
      // of the host's class (Myna 0.14.0: Linux AArch64 has its own); never a fallback.
      String expected = CsvNativeAbi.fingerprint(TargetAbi.HOST.charUnsigned());
      String actual;
      try {
        actual = CsvCellsJvmFFM.fingerprint().reinterpret(expected.length() + 1).getString(0);
      } catch (Throwable failure) {
        throw new IllegalStateException(failure);
      }
      if (!actual.equals(expected))
        throw new IllegalStateException(
            "Myna/CSV ABI fingerprint mismatch (expected the " + TargetAbi.HOST + " class)");
      loaded = library;
    } else if (!loaded.equals(library))
      throw new IllegalStateException(
          "CSV cells are bound to " + loaded + " in this class loader, not " + library);
  }

  /** {@code td_csv_kernel} through its cell: the id when this CPU supports it, else negative. */
  static int resolve(int kernel) {
    CsvCellsJvmTruffle.kernel_Cell cell = adopt(new CsvCellsJvmTruffle.kernel_Cell());
    return cell.executeInt(new Object[] {kernel});
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

  int kernel() {
    return kernel;
  }

  @Override
  public int index(byte[] input, int from, int length, int[] out) {
    CsvIndex.checkCall(input, from, length, out);
    ensure(length);
    MemorySegment.copy(input, from, this.input, ValueLayout.JAVA_BYTE, 0, length);
    Object[] arguments = {
      NativePointer.of(this.input.address()),
      (long) length,
      NativePointer.of(output.address()),
      format,
      kernel,
      carry
    };
    long result =
        length <= criticalBytes ? leaf.executeLong(arguments) : index.executeLong(arguments);
    if (result > 0)
      MemorySegment.copy(output, ValueLayout.JAVA_INT, 0, out, 0, (int) (result >>> 8));
    if (result < 0) throw new IllegalStateException("CSV native index failed: " + result);
    carry = result & 0xFF;
    return (int) (result >>> 8);
  }

  /** simdutf through the cell, over a native copy (FfmIndexer's checks and result rule). */
  @Override
  public long validateUtf8(byte[] input, int from, int length) {
    if (from < 0 || length < 0 || length > input.length - from)
      throw new IndexOutOfBoundsException("CSV UTF-8 range");
    ensure(length);
    MemorySegment.copy(input, from, this.input, ValueLayout.JAVA_BYTE, 0, length);
    long r =
        validate.executeLong(new Object[] {NativePointer.of(this.input.address()), (long) length});
    return r < 0 ? -1 : r;
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
