package dev.elide.truffledata.csv.cells;

import dev.elide.myna.truffle.NativeCell;
import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;
import dev.elide.truffledata.TargetAbi;
import dev.elide.truffledata.csv.kernel.cells.generated.CsvCellsJvmFFM;
import dev.elide.truffledata.csv.kernel.cells.generated.CsvCellsJvmTruffle;
import dev.elide.truffledata.csv.kernel.CsvIndex;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import dev.elide.truffledata.csv.kernel.generated.CsvNativeAbi;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Reference;
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
 * borrows), so heap input always goes through {@code FfmIndexer}'s copy path: native buffers, then
 * the entries copied back. Calls of at most {@code criticalBytes} use the leaf, the size split
 * {@code FfmIndexer} makes. The JVM is a correctness target.
 *
 * <p>The buffers come from an automatic arena, not a confined one. Scanners never close their
 * indexer (the FFM provider's indexers hold nothing: its calls are critical and copy-free), so a
 * confined arena here would leak its buffers on every parse and would tie the indexer to the thread
 * of its first call, which the FFM provider's indexers are not. The collector frees an automatic
 * arena once the indexer is unreachable; the buffers are kept reachable across each call.
 *
 * <p>The cells are shared by every indexer ({@link Cells}): a scanner creates its indexer per
 * parse, and with the argument types passed here a cell call mutates nothing (no coercion inserts
 * an interop library).
 *
 * <p>Myna's FFM cells bind their symbols through the loader lookup, so the library is loaded into
 * this class loader ({@link System#load}). One library per class loader: a second path is rejected
 * rather than silently called through the first, and a library that failed the fingerprint check
 * stays rejected.
 */
final class CellsFfmIndexer extends CsvIndexer implements AutoCloseable {
  /** Index entries per provider call, as {@code FfmIndexer.PROVIDER_CAPACITY}. */
  static final int PROVIDER_CAPACITY = 1 << 16;

  /** The process's cells, created on first use (after {@link #bind}). */
  static final class Cells {
    static final CsvCellsJvmTruffle.index_Cell INDEX = adopt(new CsvCellsJvmTruffle.index_Cell());
    static final CsvCellsJvmTruffle.index_leaf_Cell LEAF =
        adopt(new CsvCellsJvmTruffle.index_leaf_Cell());
    static final CsvCellsJvmTruffle.validate_utf8_Cell VALIDATE =
        adopt(new CsvCellsJvmTruffle.validate_utf8_Cell());
    static final CsvCellsJvmTruffle.kernel_Cell KERNEL = adopt(new CsvCellsJvmTruffle.kernel_Cell());

    private Cells() {}

    private static <T extends NativeCell> T adopt(T cell) {
      // A cell is adopted once, under its own language-neutral root.
      new NativeCellRootNode(cell).adoptChildren();
      return cell;
    }
  }

  /** The first library loaded into this class loader, verified or not. */
  private static @Nullable Path loaded;

  /** Whether {@link #loaded} passed the fingerprint check. */
  private static boolean verified;

  private final CsvCellsJvmTruffle.index_Cell index;
  private final CsvCellsJvmTruffle.index_leaf_Cell leaf;
  private final CsvCellsJvmTruffle.validate_utf8_Cell validate;
  private final int format;
  private final int kernel;
  private final int criticalBytes;
  private final String name;
  private MemorySegment input = MemorySegment.NULL;
  private MemorySegment output = MemorySegment.NULL;

  CellsFfmIndexer(byte delimiter, byte quote, boolean quoting, int kernel, int criticalBytes) {
    super(delimiter, quote, quoting);
    int resolved = resolve(kernel);
    if (resolved < 0) throw new IllegalArgumentException("CSV native kernel " + kernel);
    index = Cells.INDEX;
    leaf = Cells.LEAF;
    validate = Cells.VALIDATE;
    this.kernel = resolved;
    this.criticalBytes = criticalBytes;
    format = (delimiter & 0xFF) | (quote & 0xFF) << 8 | (quoting ? 1 << 16 : 0);
    name = "native-" + CsvIndex.kernelName(resolved) + "-ffm-cells";
  }

  /** Loads {@code library} once per class loader and verifies the production fingerprint. */
  static synchronized void bind(Path library) {
    if (loaded == null) {
      System.load(library.toString());
      // From here the loader lookup resolves the cells' symbols in this library, whatever the
      // check below finds: no other library may be bound.
      loaded = library;
    } else if (!loaded.equals(library))
      throw new IllegalStateException(
          "CSV cells are bound to " + loaded + " in this class loader, not " + library);
    if (verified) return;
    // The cells' own constant folds in the cells options; the library carries the production ABI
    // of the host's class (Myna 0.14.0: Linux AArch64 has its own); never a fallback.
    String expected = CsvNativeAbi.fingerprint(TargetAbi.HOST.charUnsigned());
    String actual;
    try {
      actual = CsvCellsJvmFFM.fingerprint().reinterpret(expected.length() + 1).getString(0);
    } catch (RuntimeException | Error failure) {
      throw failure;
    } catch (Throwable failure) {
      throw new IllegalStateException(failure);
    }
    if (!actual.equals(expected))
      throw new IllegalStateException(
          "Myna/CSV ABI fingerprint mismatch (expected the " + TargetAbi.HOST + " class)");
    verified = true;
  }

  /** {@code td_csv_kernel} through its cell: the id when this CPU supports it, else negative. */
  static int resolve(int kernel) {
    return Cells.KERNEL.executeInt(new Object[] {kernel});
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
    MemorySegment source = this.input;
    MemorySegment target = output;
    long result;
    try {
      MemorySegment.copy(input, from, source, ValueLayout.JAVA_BYTE, 0, length);
      Object[] arguments = {
        NativePointer.of(source.address()),
        (long) length,
        NativePointer.of(target.address()),
        format,
        kernel,
        carry
      };
      result =
          length <= criticalBytes ? leaf.executeLong(arguments) : index.executeLong(arguments);
      if (result > 0)
        MemorySegment.copy(target, ValueLayout.JAVA_INT, 0, out, 0, (int) (result >>> 8));
    } finally {
      // The cell takes raw addresses: the automatic arena must stay reachable across the call.
      Reference.reachabilityFence(source);
      Reference.reachabilityFence(target);
    }
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
    MemorySegment source = this.input;
    try {
      MemorySegment.copy(input, from, source, ValueLayout.JAVA_BYTE, 0, length);
      long r =
          validate.executeLong(new Object[] {NativePointer.of(source.address()), (long) length});
      return r < 0 ? -1 : r;
    } finally {
      Reference.reachabilityFence(source);
    }
  }

  private void ensure(int length) {
    if (input.byteSize() >= Math.max(length, 1)) return;
    long size =
        Math.max(Math.max(length, 64), Math.min(2 * input.byteSize(), CsvIndex.MAX_CALL_BYTES));
    // One automatic arena per size: the collector frees the previous buffers.
    Arena grown = Arena.ofAuto();
    MemorySegment in = grown.allocate(size, 64);
    output = grown.allocate(4 * size, 64);
    input = in;
  }

  /** Drops the buffers (the collector frees them); the indexer stays usable and reallocates. */
  @Override
  public void close() {
    input = output = MemorySegment.NULL;
  }
}
