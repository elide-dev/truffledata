package dev.elide.truffledata.csv;

import dev.elide.truffledata.csv.kernel.CsvIndexer;
import dev.elide.truffledata.csv.kernel.SwarIndexer;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Which structural-index kernel the CSV scanners use (see {@code csv/native/README.md}).
 *
 * <p>Every parse path ({@code Csv.parse}, {@code Csv.open}, {@code CsvPlan.parse} and {@code open},
 * heap and mapped input) frames records over a structural index when the format allows it (every
 * strict format, and Commons formats without per-byte features; see {@link CsvFormat}), and falls
 * back to the per-byte scanners for the rest and for any record the index path declines. Results
 * are identical either way; only speed differs.
 *
 * <p>The built-in kernel is {@link #JAVA} (pure Java SWAR, portable to every JVM and image). A
 * faster provider, such as the native AVX-512/AVX2/NEON kernel from {@code truffledata-csv}'s
 * native backends, is installed explicitly with {@link #install}; nothing is loaded
 * automatically. Inputs shorter than {@link #MIN_INDEX_BYTES} use the per-byte scanners, where
 * setting up an index costs more than it saves.
 *
 * <p>The provider is read when a parse or reader starts; installing one does not affect readers
 * already open.
 */
public final class CsvIndexing {
  /**
   * Creates indexers for one format. Each scanner owns its indexer (indexers are not
   * thread-safe), so {@link #create} is called once per parse or reader.
   */
  public interface Provider {
    /** A short name for reports, such as {@code java-swar} or {@code native}. */
    String name();

    /** An indexer for the format's delimiter, quote and quoting. */
    CsvIndexer create(byte delimiter, byte quote, boolean quoting);

    /** Index entries per kernel call: the largest call is this many bytes. */
    int capacity();
  }

  /** Pure Java SWAR: eight bytes per step, 64-byte bitmap blocks, 64 Ki entries per call. */
  public static final Provider JAVA =
      new Provider() {
        @Override
        public String name() {
          return "java-swar";
        }

        @Override
        public CsvIndexer create(byte delimiter, byte quote, boolean quoting) {
          return new SwarIndexer(delimiter, quote, quoting);
        }

        @Override
        public int capacity() {
          return 1 << 16;
        }
      };

  /**
   * Inputs (or stream buffers) below this many bytes use the per-byte scanners: the measured
   * crossover (benchmarks/results/csv-1d2e9ab) lies between 4 and 16 KiB on SVM.
   */
  public static final int MIN_INDEX_BYTES = 16 << 10;

  private static volatile @Nullable Provider provider = JAVA;

  /** {@link #MIN_INDEX_BYTES}, lowered only by tests that force the index path at every size. */
  static volatile int minIndexBytes = MIN_INDEX_BYTES;

  private CsvIndexing() {}

  /**
   * Installs the kernel for parses and readers started from now on; {@code null} disables the
   * index path, so only the per-byte scanners run.
   */
  public static void install(@Nullable Provider kernel) {
    provider = kernel;
  }

  /** The installed provider, or null when the index path is disabled. */
  public static @Nullable Provider provider() {
    return provider;
  }

  /** The installed provider's name, or {@code "per-byte"} when the index path is disabled. */
  public static String kernel() {
    Provider p = provider;
    return p == null ? "per-byte" : p.name();
  }

  /** The index path of one scanner over a provider read when the scanner was created. */
  static IndexScan scanner(Provider p, Dialect dialect, CsvLimits limits, int inputBytes) {
    CsvIndexer indexer =
        Objects.requireNonNull(p.create(dialect.delimiter[0], dialect.quote, dialect.quoting));
    // A call never needs more entries than the input has bytes (rounded to whole blocks).
    long fit = ((long) inputBytes + 63) & ~63L;
    return new IndexScan(dialect, limits, indexer, (int) Math.min(p.capacity(), fit));
  }
}
