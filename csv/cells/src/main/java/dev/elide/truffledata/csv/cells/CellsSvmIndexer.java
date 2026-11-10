package dev.elide.truffledata.csv.cells;

import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;
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
 * td_csv_validate_utf8} and {@code td_csv_kernel} call goes through the generated cell ({@code
 * truffle_backend=native}, direct transitions) instead of the hand-written {@code @CFunction} call.
 * Each scalar cell is held as its concrete {@code *_Cell} type and called through its specialized
 * method ({@code executeLong} for the {@code i64} results, {@code executeInt} for {@code
 * td_csv_kernel}): no result box and no {@code UnexpectedResultException} handler.
 *
 * <p>Everything else is {@link SvmIndexer}'s: the same checks, the same leaf/transition split at
 * {@code leafBytes} (the leaf cell has no transition, the seam's {@code native_leaf}), the input and
 * output arrays pinned around the call (plain {@code try/finally}: javac's try-with-resources
 * reaches {@code Throwable.addSuppressed}, which the runtime-compilation blocklist rejects), the
 * packed result, the carry rule and the error messages, so results and errors are identical.
 *
 * <p>The cells are adopted once per indexer (an indexer is confined to its scanner's thread), under
 * their own language-neutral roots. Called from host code, outside partial evaluation, each call
 * builds the cell's {@code Object[]} argument array with boxed scalars and two {@link
 * NativePointer}s: Myna's cell entry ({@code execute*(Object[])}) has no other shape. Partial
 * evaluation would scalar-replace them; this host call site does not, so that is the flag-on
 * route's per-call allocation over the baseline (measured separately).
 */
final class CellsSvmIndexer extends CsvIndexer {
  /** The linked library's ABI was checked through the fingerprint cell (benign race). */
  private static boolean verified;

  private final CsvCellsTruffle.index_Cell index;
  private final CsvCellsTruffle.index_leaf_Cell leaf;
  private final CsvCellsTruffle.validate_utf8_Cell validate;
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
      // A pointer result: the generic execute is the only entry (no typed method exists).
      NativePointer fingerprint =
          (NativePointer) cell(new CsvCellsTruffle.fingerprint_Cell()).execute(new Object[0]);
      CCharPointer text = WordFactory.pointer(fingerprint.address());
      if (!CTypeConversion.toJavaString(text).equals(expected))
        throw new IllegalStateException("Myna/CSV ABI fingerprint mismatch");
      verified = true;
    }
    int resolved = cell(new CsvCellsTruffle.kernel_Cell()).executeInt(new Object[] {kernel});
    if (resolved < 0) throw new IllegalArgumentException("CSV native kernel " + kernel);
    index = cell(new CsvCellsTruffle.index_Cell());
    leaf = cell(new CsvCellsTruffle.index_leaf_Cell());
    validate = cell(new CsvCellsTruffle.validate_utf8_Cell());
    this.kernel = resolved;
    this.leafBytes = leafBytes;
    format = (delimiter & 0xFF) | (quote & 0xFF) << 8 | (quoting ? 1 << 16 : 0);
    name =
        "native-"
            + CsvIndex.kernelName(resolved)
            + (leafBytes > 0 ? "-svm-leaf-cells" : "-svm-cells");
  }

  private static <T extends dev.elide.myna.truffle.NativeCell> T cell(T cell) {
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
    long result;
    PinnedObject in = PinnedObject.create(input);
    try {
      PinnedObject entries = PinnedObject.create(out);
      try {
        Object[] arguments = {
          NativePointer.of(in.addressOfArrayElement(from).rawValue()),
          (long) length,
          NativePointer.of(entries.addressOfArrayElement(0).rawValue()),
          format,
          kernel,
          carry
        };
        result =
            length <= leafBytes ? leaf.executeLong(arguments) : index.executeLong(arguments);
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
          validate.executeLong(
              new Object[] {
                NativePointer.of(in.addressOfArrayElement(from).rawValue()), (long) length
              });
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
