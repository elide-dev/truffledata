package dev.elide.truffledata.csv.cells;

import dev.elide.truffledata.csv.CsvIndexing;
import dev.elide.truffledata.csv.kernel.cells.generated.CsvCellsTruffle;
import dev.elide.truffledata.csv.kernel.CsvIndex;
import dev.elide.truffledata.csv.kernel.CsvIndexer;
import dev.elide.truffledata.csv.kernel.generated.CsvNativeAbi;
import dev.elide.truffledata.csv.kernel.svm.SvmIndexer;
import java.nio.file.Path;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;
import org.jspecify.annotations.Nullable;

/**
 * The Native Image indexer of the CSV cells route ({@code -Dtruffledata.csv.cells=true} at image
 * build time, {@link CsvCellsFeature}): every {@code td_csv_index_leaf}, {@code td_csv_index}, {@code
 * td_csv_validate_utf8}, {@code td_csv_kernel} and {@code td_csv_fingerprint} call goes through the
 * generated cell ({@code truffle_backend=native}, direct transitions) instead of the hand-written
 * {@code @CFunction} call.
 *
 * <p>The indexer is host code, compiled ahead of time and called below Truffle boundaries, so it
 * uses each cell's static {@code invoke} entry (Myna 0.16.0): native carriers and raw addresses,
 * with no argument array, box, {@code NativePointer} or {@code Borrow}. That entry allocates
 * nothing and costs the direct call; the cells' {@code execute} methods, which coerce guest values
 * and then call the same {@code invoke}, are for guest code and are not used here.
 *
 * <p>Everything else is {@link SvmIndexer}'s: the same checks, the same leaf/transition split at
 * {@code leafBytes} (the leaf cell has no transition, the seam's {@code native_leaf}), the input and
 * output arrays pinned around the call (plain {@code try/finally}: javac's try-with-resources
 * reaches {@code Throwable.addSuppressed}, which the runtime-compilation blocklist rejects), the
 * packed result, the carry rule and the error messages, so results and errors are identical. The
 * caller keeps each address valid for the call: both arrays stay pinned until it returns.
 */
final class CellsSvmIndexer extends CsvIndexer {
  /** The linked library's ABI was checked through the cell (benign race: the check is idempotent). */
  private static boolean verified;

  private final int format;
  private final int kernel;
  private final int leafBytes;
  private final String name;

  CellsSvmIndexer(byte delimiter, byte quote, boolean quoting, int kernel, int leafBytes) {
    super(delimiter, quote, quoting);
    if (!verified) {
      // The image's ABI class (Platform.includedIn folds at image build time): one constant.
      String expected =
          CsvNativeAbi.fingerprint(Platform.includedIn(Platform.LINUX_AARCH64.class));
      CCharPointer text = WordFactory.pointer(CsvCellsTruffle.fingerprint_Cell.invoke());
      if (!CTypeConversion.toJavaString(text).equals(expected))
        throw new IllegalStateException("Myna/CSV ABI fingerprint mismatch");
      verified = true;
    }
    int resolved = CsvCellsTruffle.kernel_Cell.invoke(kernel);
    if (resolved < 0) throw new IllegalArgumentException("CSV native kernel " + kernel);
    this.kernel = resolved;
    this.leafBytes = leafBytes;
    format = (delimiter & 0xFF) | (quote & 0xFF) << 8 | (quoting ? 1 << 16 : 0);
    name =
        "native-"
            + CsvIndex.kernelName(resolved)
            + (leafBytes > 0 ? "-svm-leaf-cells" : "-svm-cells");
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
    long result;
    PinnedObject in = PinnedObject.create(input);
    try {
      PinnedObject entries = PinnedObject.create(out);
      try {
        long data = in.addressOfArrayElement(from).rawValue();
        long target = entries.addressOfArrayElement(0).rawValue();
        result =
            length <= leafBytes
                ? CsvCellsTruffle.index_leaf_Cell.invoke(data, length, target, format, kernel, carry)
                : CsvCellsTruffle.index_Cell.invoke(data, length, target, format, kernel, carry);
      } finally {
        entries.close();
      }
    } finally {
      in.close();
    }
    if (result < 0) throw new IllegalStateException("CSV native index failed: " + result);
    carry = result & 0xFF;
    return (int) (result >>> 8);
  }

  /** simdutf validation of the pinned array through the cell ({@link SvmIndexer#invalidUtf8}). */
  @Override
  public long validateUtf8(byte[] input, int from, int length) {
    if (from < 0 || length < 0 || length > input.length - from)
      throw new IndexOutOfBoundsException("CSV UTF-8 range");
    long r;
    PinnedObject in = PinnedObject.create(input);
    try {
      r =
          CsvCellsTruffle.validate_utf8_Cell.invoke(
              in.addressOfArrayElement(from).rawValue(), length);
    } finally {
      in.close();
    }
    return r < 0 ? -1 : r;
  }

  /**
   * The cells provider: {@link SvmIndexer#provider()}'s kernel choice, leaf split and capacity.
   * Call it at image run time (it checks the ABI of the linked library).
   */
  static CsvIndexing.Provider provider() {
    String name =
        new CellsSvmIndexer((byte) ',', (byte) '"', true, 0, SvmIndexer.LEAF_BYTES).name();
    return new CsvIndexing.Provider() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public CsvIndexer create(byte delimiter, byte quote, boolean quoting) {
        return new CellsSvmIndexer(delimiter, quote, quoting, 0, SvmIndexer.LEAF_BYTES);
      }

      @Override
      public int capacity() {
        return SvmIndexer.PROVIDER_CAPACITY;
      }
    };
  }

  /**
   * The backend {@link CsvCellsFeature} registers as the image's {@code CsvIndexing.NativeBackend}
   * singleton; {@code SvmIndexer.Backend.load} defers to it.
   */
  static final class Backend implements CsvIndexing.NativeBackend {
    @Override
    public boolean available() {
      return org.graalvm.nativeimage.ImageInfo.inImageRuntimeCode();
    }

    @Override
    public CsvIndexing.Provider load(@Nullable Path library) {
      return provider();
    }
  }
}
