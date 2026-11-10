package dev.elide.truffledata.yaml;

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
 * image build time, {@link YamlFeature}): the session's own native calls — {@code session_decode},
 * {@code session_write_text}, {@code session_retain}, {@code session_reset} (still a leaf), {@code
 * stream_create}, {@code stream_feed}, {@code stream_next}, {@code stream_finish} and {@code
 * stream_release} — go through cells generated with {@code truffle_backend=native
 * truffle_transitions=direct}. Everything else is the static backend's: this wraps an {@link
 * SvmSessionBackend}, which owns the native session and result slot and creates and releases
 * them; retained results release through the static binding, because they outlive this backend.
 * It reads the backend's state through its hooks and keeps its exact control flow, so tapes are
 * identical and errors are the same, in the same order. Nothing here catches a cell failure or
 * falls back to the static calls.
 *
 * <p>This is host code, compiled ahead of time and called below Truffle boundaries, so every call
 * uses the cell's static {@code invoke} entry (Myna 0.16.0): native carriers and raw addresses, with
 * no cell instance, argument array, box, {@code NativePointer} or {@code Borrow}; it allocates
 * nothing and costs the direct call. The cells' {@code execute} methods, which coerce guest values
 * and then call the same {@code invoke}, are for guest code and are not used here. The caller keeps
 * every address valid for the call: heap arrays are pinned around it, exactly as the static
 * backend pins them, with plain {@code try/finally} (try-with-resources reaches {@code
 * Throwable.addSuppressed}, which the runtime-compilation blocklist rejects), and lent without a
 * copy; the session, the result slot and a stream are the wrapped backend's and outlive the call.
 */
final class CellsSvmSessionBackend extends YamlSession.Backend {
  private final SvmSessionBackend backend;
  private final YamlOptions options;

  /** The session's handle and result slot: they live as long as {@link #backend}. */
  private final long session;

  private final long result;

  CellsSvmSessionBackend(YamlOptions options) {
    this.options = options;
    backend = new SvmSessionBackend(options);
    session = backend.sessionHandle().rawValue();
    result = backend.resultSlot().rawValue();
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
    int status;
    PinnedObject pin = PinnedObject.create(pinnedBytes);
    try {
      // text_out = NULL, text_capacity = 0: shared text is written by session_write_text.
      status =
          YamlCellsTruffle.session_decode_Cell.invoke(
              session,
              pin.addressOfArrayElement(0).rawValue(),
              input.length,
              backend.decodeFlags(),
              0L,
              0L,
              result);
    } finally {
      pin.close();
    }
    SvmSessionBackend.check(status, backend.resultSlot());
    if (text != null) ((CellsText) text).write();
  }

  @Override
  protected void decode(MemorySegment data, long length) {
    backend.decode(data, length);
  }

  @Override
  protected TapeTextArena textArena(byte[] array) {
    return new CellsText(array);
  }

  /** The static backend's shared-text arena, with {@code session_write_text} through a cell. */
  private final class CellsText extends TapeTextArena {
    private Pointer table = WordFactory.nullPointer();

    CellsText(byte[] bytes) {
      super(bytes);
    }

    void write() {
      VoidPointer output = StackValue.get(Yaml.YamlTextResult.SIZE);
      for (; ; ) {
        int status;
        byte[] bytes = array();
        PinnedObject pin = PinnedObject.create(bytes);
        try {
          status =
              YamlCellsTruffle.session_write_text_Cell.invoke(
                  session, pin.addressOfArrayElement(0).rawValue(), bytes.length, output.rawValue());
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
      int status = YamlCellsTruffle.session_retain_Cell.invoke(session, owned.rawValue());
      YamlStatus.checkStatus(status);
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
    YamlCellsTruffle.session_reset_Cell.invoke(session);
  }

  @Override
  protected void release() {
    backend.release();
  }

  @Override
  protected long streamCreate(int maxDocumentBytes, int growth) {
    // NULL for a failed allocation.
    long created = YamlCellsTruffle.stream_create_Cell.invoke(session, maxDocumentBytes, growth);
    if (created == 0) throw new OutOfMemoryError("YAML stream allocation");
    return created;
  }

  @Override
  protected int streamFeed(long stream, byte[] bytes, int offset, int length) {
    PinnedObject pin = PinnedObject.create(bytes);
    try {
      return (int)
          YamlCellsTruffle.stream_feed_Cell.invoke(
              stream, pin.addressOfArrayElement(offset).rawValue(), length);
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
    YamlCellsTruffle.stream_finish_Cell.invoke(stream);
  }

  @Override
  protected int streamNext(long stream) {
    int status = YamlCellsTruffle.stream_next_Cell.invoke(stream, backend.decodeFlags(), result);
    if (status != 8 && status != 9) SvmSessionBackend.check(status, backend.resultSlot());
    return status;
  }

  @Override
  protected void streamRelease(long stream) {
    YamlCellsTruffle.stream_release_Cell.invoke(stream);
  }
}
