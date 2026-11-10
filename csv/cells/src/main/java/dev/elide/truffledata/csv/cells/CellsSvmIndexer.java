package dev.elide.truffledata.csv.cells;

import dev.elide.myna.truffle.NativeCell;
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
 * <p>The cells are created once per image run ({@link Cells}) and shared by every indexer, because
 * a scanner creates its indexer per parse: per-indexer cells would add four cells, their coercion
 * nodes and four root nodes to every parse. Sharing is safe: with the argument types passed here
 * ({@link NativePointer}, {@code Integer}, {@code Long}) every coercion takes a path that reads only
 * final state and never inserts an interop library, so a cell call mutates nothing.
 *
 * <p>Called from host code, outside partial evaluation, Myna's cell entry ({@code
 * execute*(Object[])}) forces per-call allocation the baseline does not have: two {@link
 * NativePointer}s, the boxed length, and the {@code Borrow} the generated cell makes for the {@code
 * data} byte pointer. The argument arrays are the indexer's own (an indexer is confined to its
 * scanner's thread and the calls do not re-enter), with the constant format and kernel boxed once,
 * so no array and no constant box is allocated per call. The pointer slots are cleared before the
 * arrays are unpinned: no address outlives its pin.
 */
final class CellsSvmIndexer extends CsvIndexer {
  /**
   * The image's cells, created at run time on first use, after the linked library's ABI was checked
   * through the fingerprint cell. A benign race: the check is idempotent, a second set of cells is
   * equivalent, and the final fields publish each set safely.
   */
  static final class Cells {
    private static @Nullable Cells shared;

    final CsvCellsTruffle.index_Cell index;
    final CsvCellsTruffle.index_leaf_Cell leaf;
    final CsvCellsTruffle.validate_utf8_Cell validate;
    final CsvCellsTruffle.kernel_Cell kernel;

    private Cells() {
      // The image's ABI class (Platform.includedIn folds at image build time): one constant.
      String expected =
          CsvNativeAbi.fingerprint(Platform.includedIn(Platform.LINUX_AARCH64.class));
      // A pointer result: the generic execute is the only entry (no typed method exists).
      CsvCellsTruffle.fingerprint_Cell fingerprint = adopt(new CsvCellsTruffle.fingerprint_Cell());
      NativePointer address = (NativePointer) fingerprint.execute(new Object[0]); // fingerprint_Cell
      CCharPointer text = WordFactory.pointer(address.address());
      if (!CTypeConversion.toJavaString(text).equals(expected))
        throw new IllegalStateException("Myna/CSV ABI fingerprint mismatch");
      kernel = adopt(new CsvCellsTruffle.kernel_Cell());
      index = adopt(new CsvCellsTruffle.index_Cell());
      leaf = adopt(new CsvCellsTruffle.index_leaf_Cell());
      validate = adopt(new CsvCellsTruffle.validate_utf8_Cell());
    }

    static Cells get() {
      Cells cells = shared;
      if (cells == null) shared = cells = new Cells();
      return cells;
    }

    private static <T extends NativeCell> T adopt(T cell) {
      // A cell is adopted once, under its own language-neutral root.
      new NativeCellRootNode(cell).adoptChildren();
      return cell;
    }
  }

  private final CsvCellsTruffle.index_Cell index;
  private final CsvCellsTruffle.index_leaf_Cell leaf;
  private final CsvCellsTruffle.validate_utf8_Cell validate;

  /** {@code data, length, out, format, kernel, carry}: format and kernel are boxed once. */
  private final @Nullable Object[] indexArguments = new Object[6];

  /** {@code data, length}. */
  private final @Nullable Object[] validateArguments = new Object[2];

  private final int kernel;
  private final int leafBytes;
  private final String name;

  CellsSvmIndexer(byte delimiter, byte quote, boolean quoting, int kernel, int leafBytes) {
    super(delimiter, quote, quoting);
    Cells cells = Cells.get();
    int resolved = cells.kernel.executeInt(new Object[] {kernel});
    if (resolved < 0) throw new IllegalArgumentException("CSV native kernel " + kernel);
    index = cells.index;
    leaf = cells.leaf;
    validate = cells.validate;
    this.kernel = resolved;
    this.leafBytes = leafBytes;
    indexArguments[3] = (delimiter & 0xFF) | (quote & 0xFF) << 8 | (quoting ? 1 << 16 : 0);
    indexArguments[4] = resolved;
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
    @Nullable Object[] arguments = indexArguments;
    long result;
    PinnedObject in = PinnedObject.create(input);
    try {
      PinnedObject entries = PinnedObject.create(out);
      try {
        arguments[0] = NativePointer.of(in.addressOfArrayElement(from).rawValue());
        arguments[1] = (long) length;
        arguments[2] = NativePointer.of(entries.addressOfArrayElement(0).rawValue());
        arguments[5] = carry;
        result =
            length <= leafBytes ? leaf.executeLong(arguments) : index.executeLong(arguments);
      } finally {
        // Neither address stays reachable once its array is unpinned.
        arguments[0] = null;
        arguments[2] = null;
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
    @Nullable Object[] arguments = validateArguments;
    long r;
    PinnedObject in = PinnedObject.create(input);
    try {
      arguments[0] = NativePointer.of(in.addressOfArrayElement(from).rawValue());
      arguments[1] = (long) length;
      r = validate.executeLong(arguments);
    } finally {
      arguments[0] = null;
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
