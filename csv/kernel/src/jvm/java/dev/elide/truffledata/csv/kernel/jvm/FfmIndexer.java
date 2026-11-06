package dev.elide.truffledata.csv.kernel.jvm;

import dev.elide.truffledata.csv.kernel.CsvIndex;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import dev.elide.truffledata.csv.kernel.generated.CsvNativeFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * JVM backend for the native indexer (csv/native) over FFM.
 *
 * <p>Two call shapes, chosen per call by size:
 *
 * <ul>
 *   <li>Heap arrays through a critical downcall of {@code td_csv_index_leaf} with heap access
 *       ({@code Linker.Option.critical(true)}): no copy, but the GC cannot run during the call,
 *       so it is used only for calls of at most {@link #criticalBytes} bytes (the seam declares
 *       that symbol a bounded native leaf). The handle is built from Myna's descriptor because
 *       Myna's generated critical factories refuse heap segments.
 *   <li>Larger calls copy the input into a confined native buffer, make an ordinary downcall of
 *       {@code td_csv_index}, and copy the entries back.
 * </ul>
 *
 * <p>{@link #index(MemorySegment, long, int, MemorySegment)} indexes native memory (for example a
 * mapped file) without copies. Confined to the creating thread; close releases the buffers and
 * the library.
 */
public final class FfmIndexer extends CsvIndexer implements AutoCloseable {
  private final Arena arena = Arena.ofConfined();
  private Arena buffers = Arena.ofConfined();
  private final MethodHandle index;
  private final MethodHandle critical;
  private final MethodHandle validate;
  private final int format;
  private final int kernel;
  private final String name;
  private final int criticalBytes;
  private MemorySegment input = MemorySegment.NULL;
  private MemorySegment output = MemorySegment.NULL;

  /**
   * Loads {@code library}; {@code kernel} is a csv/native Kernel id (0 for the best supported)
   * and {@code criticalBytes} the largest heap call made without copying (0 never).
   */
  public FfmIndexer(
      Path library, byte delimiter, byte quote, boolean quoting, int kernel, int criticalBytes) {
    super(delimiter, quote, quoting);
    try {
      SymbolLookup symbols = SymbolLookup.libraryLookup(library.toAbsolutePath(), arena);
      MemorySegment fingerprint = (MemorySegment) CsvNativeFFM.fingerprint(symbols).invokeExact();
      String actual =
          fingerprint.reinterpret(CsvNativeFFM.ABI_FINGERPRINT.length() + 1).getString(0);
      if (!actual.equals(CsvNativeFFM.ABI_FINGERPRINT))
        throw new IllegalStateException("Myna/CSV ABI fingerprint mismatch");
      int resolved = (int) CsvNativeFFM.kernel(symbols).invokeExact(kernel);
      if (resolved < 0) throw new IllegalArgumentException("CSV native kernel " + kernel);
      this.kernel = resolved;
      index = CsvNativeFFM.index(symbols);
      validate = CsvNativeFFM.validate_utf8(symbols);
      critical =
          Linker.nativeLinker()
              .downcallHandle(
                  symbols.find("td_csv_index_leaf").orElseThrow(),
                  CsvNativeFFM.index_leaf_DESCRIPTOR,
                  Linker.Option.critical(true));
    } catch (RuntimeException | Error failure) {
      arena.close();
      throw failure;
    } catch (Throwable failure) {
      arena.close();
      throw new IllegalStateException(failure);
    }
    format = (delimiter & 0xFF) | (quote & 0xFF) << 8 | (quoting ? 1 << 16 : 0);
    name = "native-" + CsvIndex.kernelName(this.kernel) + "-ffm";
    this.criticalBytes = criticalBytes;
  }

  @Override
  public String name() {
    return name;
  }

  public int kernel() {
    return kernel;
  }

  @Override
  public int index(byte[] input, int from, int length, int[] out) {
    CsvIndex.checkCall(input, from, length, out);
    long result;
    try {
      if (length <= criticalBytes) {
        result =
            (long)
                critical.invokeExact(
                    MemorySegment.ofArray(input).asSlice(from, length),
                    (long) length,
                    MemorySegment.ofArray(out),
                    format,
                    kernel,
                    carry);
      } else {
        ensure(length);
        MemorySegment.copy(input, from, this.input, ValueLayout.JAVA_BYTE, 0, length);
        result = (long) index.invokeExact(this.input, (long) length, output, format, kernel, carry);
        if (result > 0)
          MemorySegment.copy(output, ValueLayout.JAVA_INT, 0, out, 0, (int) (result >>> 8));
      }
    } catch (Throwable failure) {
      throw new IllegalStateException(failure);
    }
    return unpack(result);
  }

  /** Indexes native memory: {@code length} bytes at {@code input + from} into {@code out}. */
  public int index(MemorySegment input, long from, int length, MemorySegment out) {
    if (length > CsvIndex.MAX_CALL_BYTES || out.byteSize() < 4L * length)
      throw new IllegalArgumentException("CSV index: call too long or output too small");
    long result;
    try {
      result =
          (long) index.invokeExact(input.asSlice(from, length), (long) length, out, format, kernel, carry);
    } catch (Throwable failure) {
      throw new IllegalStateException(failure);
    }
    return unpack(result);
  }

  /** Strict UTF-8 validation with simdutf: -1, or the offset of the first invalid sequence. */
  public long invalidUtf8(byte[] input, int from, int length) {
    if (from < 0 || length < 0 || length > input.length - from)
      throw new IndexOutOfBoundsException("CSV UTF-8 range");
    ensure(length);
    MemorySegment.copy(input, from, this.input, ValueLayout.JAVA_BYTE, 0, length);
    try {
      long r = (long) validate.invokeExact(this.input, (long) length);
      return r < 0 ? -1 : r;
    } catch (Throwable failure) {
      throw new IllegalStateException(failure);
    }
  }

  private int unpack(long result) {
    if (result < 0) throw new IllegalStateException("CSV native index failed: " + result);
    carry = result & 0xFF;
    return (int) (result >>> 8);
  }

  private void ensure(int length) {
    if (input.byteSize() >= Math.max(length, 1)) return;
    long size = Math.max(Math.max(length, 64), Math.min(2 * input.byteSize(), CsvIndex.MAX_CALL_BYTES));
    Arena grown = Arena.ofConfined();
    try {
      input = grown.allocate(size, 64);
      output = grown.allocate(4 * size, 64);
    } catch (RuntimeException | Error failure) {
      grown.close();
      throw failure;
    }
    buffers.close();
    buffers = grown;
  }

  @Override
  public void close() {
    buffers.close();
    arena.close();
  }
}
