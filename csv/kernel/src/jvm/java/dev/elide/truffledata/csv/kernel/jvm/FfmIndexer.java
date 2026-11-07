package dev.elide.truffledata.csv.kernel.jvm;

import dev.elide.truffledata.csv.CsvIndexing;
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
import org.jspecify.annotations.Nullable;

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
 * mapped file) without copies. An indexer built by the public constructor loads its own library
 * and is confined to the creating thread; close releases its buffers and the library. Indexers
 * from a {@link Library} share that library's handles and need no closing unless they used the
 * copy path.
 */
public final class FfmIndexer extends CsvIndexer implements AutoCloseable {
  /**
   * A loaded {@code csv_shared} library: the verified ABI and the downcall handles, shared by every
   * indexer created from it. Thread-safe; it stays loaded for the life of the process.
   */
  public static final class Library {
    final MethodHandle select;
    final MethodHandle index;
    final MethodHandle critical;
    final MethodHandle validate;
    final MethodHandle validateCritical;
    final int best;
    private final Arena arena;

    private Library(Path library, Arena arena) {
      this.arena = arena;
      try {
        SymbolLookup symbols = SymbolLookup.libraryLookup(library.toAbsolutePath(), arena);
        MemorySegment fingerprint =
            (MemorySegment) CsvNativeFFM.fingerprint(symbols).invokeExact();
        String actual =
            fingerprint.reinterpret(CsvNativeFFM.ABI_FINGERPRINT.length() + 1).getString(0);
        if (!actual.equals(CsvNativeFFM.ABI_FINGERPRINT))
          throw new IllegalStateException("Myna/CSV ABI fingerprint mismatch");
        select = CsvNativeFFM.kernel(symbols);
        best = (int) select.invokeExact(0);
        index = CsvNativeFFM.index(symbols);
        validate = CsvNativeFFM.validate_utf8(symbols);
        Linker linker = Linker.nativeLinker();
        critical =
            linker.downcallHandle(
                symbols.find("td_csv_index_leaf").orElseThrow(),
                CsvNativeFFM.index_leaf_DESCRIPTOR,
                Linker.Option.critical(true));
        validateCritical =
            linker.downcallHandle(
                symbols.find("td_csv_validate_utf8").orElseThrow(),
                CsvNativeFFM.validate_utf8_DESCRIPTOR,
                Linker.Option.critical(true));
      } catch (RuntimeException | Error failure) {
        arena.close();
        throw failure;
      } catch (Throwable failure) {
        arena.close();
        throw new IllegalStateException(failure);
      }
    }

    /** Loads {@code library} (for example {@code csv_shared.so}) for the life of the process. */
    public static Library load(Path library) {
      return new Library(library, Arena.ofShared());
    }

    /** The csv/native kernel the CPU supports best ({@link CsvIndex#kernelName}). */
    public int kernel() {
      return best;
    }

    /** td_csv_kernel: the id when this CPU supports it (0 for the best), else negative. */
    int resolve(int kernel) {
      try {
        return (int) select.invokeExact(kernel);
      } catch (Throwable failure) {
        throw new IllegalStateException(failure);
      }
    }

    /**
     * The provider for {@link CsvIndexing#install}: the best kernel, heap calls through the
     * critical downcall (calls never exceed the provider's capacity of 1 Mi entries, 1 MiB of
     * input), and simdutf validation.
     */
    public CsvIndexing.Provider provider() {
      Library library = this;
      return new CsvIndexing.Provider() {
        @Override
        public String name() {
          return "native-" + CsvIndex.kernelName(best) + "-ffm";
        }

        @Override
        public CsvIndexer create(byte delimiter, byte quote, boolean quoting) {
          return new FfmIndexer(library, delimiter, quote, quoting, best, CsvIndex.MAX_CALL_BYTES);
        }

        @Override
        public int capacity() {
          return PROVIDER_CAPACITY;
        }
      };
    }
  }

  /** Index entries per call of {@link Library#provider()} indexers (the measured native capacity). */
  public static final int PROVIDER_CAPACITY = 1 << 20;

  private final Library library;
  private final boolean ownsLibrary;
  /** Copy-path buffers, created on first use (confined to that thread). */
  private @Nullable Arena buffers;
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
    this(new Library(library, Arena.ofConfined()), true, delimiter, quote, quoting, kernel, criticalBytes);
  }

  /** An indexer over a shared {@link Library}. */
  public FfmIndexer(
      Library library, byte delimiter, byte quote, boolean quoting, int kernel, int criticalBytes) {
    this(library, false, delimiter, quote, quoting, kernel, criticalBytes);
  }

  private FfmIndexer(
      Library library,
      boolean ownsLibrary,
      byte delimiter,
      byte quote,
      boolean quoting,
      int kernel,
      int criticalBytes) {
    super(delimiter, quote, quoting);
    int resolved;
    try {
      resolved = library.resolve(kernel);
    } catch (RuntimeException | Error failure) {
      if (ownsLibrary) library.arena.close();
      throw failure;
    }
    if (resolved < 0) {
      if (ownsLibrary) library.arena.close();
      throw new IllegalArgumentException("CSV native kernel " + kernel);
    }
    this.library = library;
    this.ownsLibrary = ownsLibrary;
    this.kernel = resolved;
    format = (delimiter & 0xFF) | (quote & 0xFF) << 8 | (quoting ? 1 << 16 : 0);
    name = "native-" + CsvIndex.kernelName(resolved) + "-ffm";
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
                library.critical.invokeExact(
                    MemorySegment.ofArray(input).asSlice(from, length),
                    (long) length,
                    MemorySegment.ofArray(out),
                    format,
                    kernel,
                    carry);
      } else {
        ensure(length);
        MemorySegment.copy(input, from, this.input, ValueLayout.JAVA_BYTE, 0, length);
        result =
            (long)
                library.index.invokeExact(this.input, (long) length, output, format, kernel, carry);
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
          (long)
              library.index.invokeExact(
                  input.asSlice(from, length), (long) length, out, format, kernel, carry);
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
      long r = (long) library.validate.invokeExact(this.input, (long) length);
      return r < 0 ? -1 : r;
    } catch (Throwable failure) {
      throw new IllegalStateException(failure);
    }
  }

  /** simdutf over the heap array through a critical downcall (no copy). */
  @Override
  public long validateUtf8(byte[] input, int from, int length) {
    if (from < 0 || length < 0 || length > input.length - from)
      throw new IndexOutOfBoundsException("CSV UTF-8 range");
    if (length > criticalBytes) return invalidUtf8(input, from, length);
    try {
      long r =
          (long)
              library.validateCritical.invokeExact(
                  MemorySegment.ofArray(input).asSlice(from, length), (long) length);
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
    if (ownsLibrary) library.arena.close();
  }
}
