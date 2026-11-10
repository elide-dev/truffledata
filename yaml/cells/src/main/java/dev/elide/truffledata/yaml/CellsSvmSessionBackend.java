package dev.elide.truffledata.yaml;

import dev.elide.myna.truffle.NativeCell;
import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;
import dev.elide.truffledata.tape.TapeBorrow;
import dev.elide.truffledata.tape.TapeTextArena;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.yaml.cells.generated.YamlCellsTruffle;
import dev.elide.truffledata.yaml.generated.Yaml;
import java.lang.foreign.MemorySegment;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;
import org.jspecify.annotations.Nullable;

/**
 * The static backend with the Myna Truffle cells route ({@code -Dtruffledata.yaml.cells=true} at
 * image build time, {@link YamlFeature}): the decode-path native calls — {@code session_decode},
 * {@code session_write_text}, {@code session_retain}, {@code stream_feed} and {@code stream_next}
 * — go through cells generated with {@code truffle_backend=native truffle_transitions=direct}.
 * Everything else is the static backend's: this wraps an {@link SvmSessionBackend} (which owns the
 * native session and result slot and keeps its lifecycle calls), reads its state through its hooks
 * and keeps its exact control flow, so tapes are identical and errors are the same, in the same
 * order.
 *
 * <p>Each scalar cell is held as its concrete generated type and called through its specialized
 * {@code executeInt}/{@code executeLong} (Myna 0.14.0): no result box, no {@code
 * UnexpectedResultException} handler. Heap arrays are pinned here, around the call, exactly as the
 * static backend pins them, with plain {@code try/finally} (try-with-resources reaches {@code
 * Throwable.addSuppressed}, which the runtime-compilation blocklist rejects), and lent to the cell
 * as pointers: no copy. A backend is confined to its session's thread, so each cell is adopted once
 * per backend under its own language-neutral root.
 */
final class CellsSvmSessionBackend extends YamlSession.Backend {
  private final SvmSessionBackend backend;
  private final YamlOptions options;
  private final YamlCellsTruffle.session_decode_Cell decode;
  private final YamlCellsTruffle.session_write_text_Cell writeText;
  private final YamlCellsTruffle.session_retain_Cell retain;
  private final YamlCellsTruffle.stream_feed_Cell feed;
  private final YamlCellsTruffle.stream_next_Cell next;

  CellsSvmSessionBackend(YamlOptions options) {
    this.options = options;
    backend = new SvmSessionBackend(options);
    try {
      decode = adopt(new YamlCellsTruffle.session_decode_Cell());
      writeText = adopt(new YamlCellsTruffle.session_write_text_Cell());
      retain = adopt(new YamlCellsTruffle.session_retain_Cell());
      feed = adopt(new YamlCellsTruffle.stream_feed_Cell());
      next = adopt(new YamlCellsTruffle.stream_next_Cell());
    } catch (RuntimeException | Error failure) {
      backend.release();
      throw failure;
    }
  }

  static <T extends NativeCell> T adopt(T cell) {
    new NativeCellRootNode(cell).adoptChildren();
    return cell;
  }

  static NativePointer pointer(long address) {
    return address == 0 ? NativePointer.NULL : NativePointer.of(address);
  }

  @Override
  YamlPlan compile(TypedLayout layout, YamlOptions options) {
    return CellsSvmPlanBackend.create(layout, options);
  }

  @Override
  protected void directories(boolean enabled) {
    backend.directories(enabled);
  }

  @Override
  protected void decode(
      byte[] data, int offset, int length, int encoding, @Nullable TapeTextArena text) {
    byte[] input = YamlStatus.utf8(data, offset, length, encoding);
    // A non-null address is required even when YAML's input is empty.
    byte[] pinnedBytes = input.length == 0 ? new byte[1] : input;
    VoidPointer session = backend.sessionHandle();
    VoidPointer result = backend.resultSlot();
    int status;
    PinnedObject pin = PinnedObject.create(pinnedBytes);
    try {
      status =
          decode.executeInt(
              new Object[] {
                pointer(session.rawValue()),
                pointer(pin.addressOfArrayElement(0).rawValue()),
                (long) input.length,
                backend.decodeFlags(),
                NativePointer.NULL,
                0L,
                pointer(result.rawValue())
              });
    } finally {
      pin.close();
    }
    SvmSessionBackend.check(status, result);
    if (text != null) ((CellsText) text).write(session);
  }

  @Override
  protected void decode(MemorySegment data, long length) {
    backend.decode(data, length);
  }

  @Override
  protected TapeTextArena textArena(byte[] array) {
    return new CellsText(array, writeText);
  }

  /** The static backend's shared-text arena, with {@code session_write_text} through a cell. */
  private static final class CellsText extends TapeTextArena {
    private final YamlCellsTruffle.session_write_text_Cell writeText;
    private Pointer table = WordFactory.nullPointer();

    CellsText(byte[] bytes, YamlCellsTruffle.session_write_text_Cell writeText) {
      super(bytes);
      this.writeText = writeText;
    }

    void write(VoidPointer session) {
      VoidPointer output = StackValue.get(Yaml.YamlTextResult.SIZE);
      for (; ; ) {
        int status;
        byte[] bytes = array();
        PinnedObject pin = PinnedObject.create(bytes);
        try {
          status =
              writeText.executeInt(
                  new Object[] {
                    pointer(session.rawValue()),
                    pointer(pin.addressOfArrayElement(0).rawValue()),
                    (long) bytes.length,
                    pointer(output.rawValue())
                  });
        } finally {
          pin.close();
        }
        if (status == 10) {
          long required = Yaml.YamlTextResult.required_capacity(output);
          if (required > Integer.MAX_VALUE || required <= bytes.length)
            throw new IllegalArgumentException("YAML text arena capacity");
          ensureCapacity((int) required);
          continue;
        }
        YamlStatus.checkStatus(status);
        table = (Pointer) Yaml.YamlTextResult.table(output);
        return;
      }
    }

    @Override
    protected long entry(long node) {
      return table.readLong(WordFactory.signed(node * Long.BYTES));
    }
  }

  @Override
  protected TapeBorrow borrow() {
    return backend.borrow();
  }

  @Override
  protected YamlMessage retain() {
    VoidPointer owned = UnmanagedMemory.calloc(Yaml.YamlResult.SIZE);
    if (owned.isNull()) {
      reset();
      throw new OutOfMemoryError("YAML retained result allocation");
    }
    try {
      YamlStatus.checkStatus(
          retain.executeInt(
              new Object[] {
                pointer(backend.sessionHandle().rawValue()), pointer(owned.rawValue())
              }));
      Pointer descriptor = (Pointer) Yaml.YamlResult.view(owned);
      long count = SvmYamlMessage.validate(descriptor);
      return SvmYamlMessage.create(
          descriptor,
          owned.rawValue(),
          ResultMetadata.read(
              SvmSessionBackend.MEMORY,
              owned.rawValue(),
              count,
              options.limits().maxAliasExpansion()));
    } catch (RuntimeException | Error failure) {
      Yaml.release(owned);
      UnmanagedMemory.free(owned);
      reset();
      throw failure;
    }
  }

  @Override
  protected void reset() {
    backend.reset();
  }

  @Override
  protected void release() {
    backend.release();
  }

  @Override
  protected long streamCreate(int maxDocumentBytes, int growth) {
    return backend.streamCreate(maxDocumentBytes, growth);
  }

  @Override
  protected int streamFeed(long stream, byte[] bytes, int offset, int length) {
    PinnedObject pin = PinnedObject.create(bytes);
    try {
      return (int)
          feed.executeLong(
              new Object[] {
                pointer(stream), pointer(pin.addressOfArrayElement(offset).rawValue()), (long) length
              });
    } finally {
      pin.close();
    }
  }

  @Override
  protected long streamFeed(long stream, MemorySegment data) {
    return backend.streamFeed(stream, data);
  }

  @Override
  protected void streamFinish(long stream) {
    backend.streamFinish(stream);
  }

  @Override
  protected int streamNext(long stream) {
    VoidPointer result = backend.resultSlot();
    int status =
        next.executeInt(
            new Object[] {pointer(stream), backend.decodeFlags(), pointer(result.rawValue())});
    if (status != 8 && status != 9) SvmSessionBackend.check(status, result);
    return status;
  }

  @Override
  protected void streamRelease(long stream) {
    backend.streamRelease(stream);
  }
}
