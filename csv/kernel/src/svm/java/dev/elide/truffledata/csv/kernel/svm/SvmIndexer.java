package dev.elide.truffledata.csv.kernel.svm;

import dev.elide.truffledata.csv.CsvIndexing;
import dev.elide.truffledata.csv.kernel.CsvIndex;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import dev.elide.truffledata.csv.kernel.generated.CsvNative;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

/**
 * Native Image backend for the native indexer (csv/native linked statically). The input and output
 * arrays are pinned for the call. Calls of at most {@code leafBytes} bytes use the seam's native
 * leaf ({@code td_csv_index_leaf}, no thread transition); larger calls use {@code td_csv_index}
 * with a normal transition, so a long call never delays a safepoint.
 */
public final class SvmIndexer extends CsvIndexer {
  /** The linked library's ABI was checked (benign race: the check is idempotent). */
  private static boolean verified;

  private final int format;
  private final int kernel;
  private final int leafBytes;
  private final String name;

  /** {@code kernel} is a csv/native Kernel id (0 for the best this CPU supports). */
  public SvmIndexer(byte delimiter, byte quote, boolean quoting, int kernel, int leafBytes) {
    super(delimiter, quote, quoting);
    if (!verified) {
      if (!CTypeConversion.toJavaString(CsvNative.fingerprint()).equals(CsvNative.ABI_FINGERPRINT))
        throw new IllegalStateException("Myna/CSV ABI fingerprint mismatch");
      verified = true;
    }
    int resolved = CsvNative.kernel(kernel);
    if (resolved < 0) throw new IllegalArgumentException("CSV native kernel " + kernel);
    this.kernel = resolved;
    this.leafBytes = leafBytes;
    format = (delimiter & 0xFF) | (quote & 0xFF) << 8 | (quoting ? 1 << 16 : 0);
    name = "native-" + CsvIndex.kernelName(resolved) + (leafBytes > 0 ? "-svm-leaf" : "-svm");
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
    try (PinnedObject in = PinnedObject.create(input);
        PinnedObject entries = PinnedObject.create(out)) {
      CCharPointer data = in.addressOfArrayElement(from);
      CIntPointer target = entries.addressOfArrayElement(0);
      result =
          length <= leafBytes
              ? CsvNative.index_leaf(data, length, target, format, kernel, carry)
              : CsvNative.index(data, length, target, format, kernel, carry);
    }
    if (result < 0) throw new IllegalStateException("CSV native index failed: " + result);
    carry = result & 0xFF;
    return (int) (result >>> 8);
  }

  /** simdutf validation of the pinned array (see {@link #invalidUtf8}). */
  @Override
  public long validateUtf8(byte[] input, int from, int length) {
    return invalidUtf8(input, from, length);
  }

  /** Leaf calls up to this many bytes (the measured crossover: leaf calls win up to 1 MiB). */
  public static final int LEAF_BYTES = 1 << 20;

  /** Index entries per call of {@link #provider()} indexers (256 KiB, L2-resident; the sweep shows native rates flat from 16 KiB calls). */
  public static final int PROVIDER_CAPACITY = 1 << 16;

  /**
   * The provider for {@link CsvIndexing#install}: the best kernel this CPU supports, as a leaf
   * call for chunks of at most {@link #LEAF_BYTES} and a normal transition above, and simdutf
   * validation. Call it at image run time (it checks the ABI of the linked library).
   */
  public static CsvIndexing.Provider provider() {
    String name = new SvmIndexer((byte) ',', (byte) '"', true, 0, LEAF_BYTES).name();
    return new CsvIndexing.Provider() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public CsvIndexer create(byte delimiter, byte quote, boolean quoting) {
        return new SvmIndexer(delimiter, quote, quoting, 0, LEAF_BYTES);
      }

      @Override
      public int capacity() {
        return PROVIDER_CAPACITY;
      }
    };
  }

  /** Strict UTF-8 validation with simdutf: -1, or the offset of the first invalid sequence. */
  public static long invalidUtf8(byte[] input, int from, int length) {
    if (from < 0 || length < 0 || length > input.length - from)
      throw new IndexOutOfBoundsException("CSV UTF-8 range");
    try (PinnedObject in = PinnedObject.create(input)) {
      long r = CsvNative.validate_utf8(in.addressOfArrayElement(from), length);
      return r < 0 ? -1 : r;
    }
  }
}
