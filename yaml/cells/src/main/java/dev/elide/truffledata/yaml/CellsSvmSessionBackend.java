package dev.elide.truffledata.yaml;

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
 * image build time, {@link YamlFeature}): the session's own native calls — {@code session_decode},
 * {@code session_write_text}, {@code session_retain}, {@code session_reset} (still a leaf), {@code
 * stream_create}, {@code stream_feed}, {@code stream_next}, {@code stream_finish} and {@code
 * stream_release} — go through cells generated with {@code truffle_backend=native
 * truffle_transitions=direct}. Everything else is the static backend's: this wraps an {@link
 * SvmSessionBackend}, which owns the native session and result slot and creates and releases
 * them; retained results release through the static binding, because they outlive this backend
 * and its cells. It reads the backend's state through its hooks and keeps its exact control flow,
 * so tapes are identical and errors are the same, in the same order. Nothing here catches a cell
 * failure or falls back to the static calls.
 *
 * <p>Heap arrays are pinned here, around the call, exactly as the static backend pins them, with
 * plain {@code try/finally} (try-with-resources reaches {@code Throwable.addSuppressed}, which the
 * runtime-compilation blocklist rejects), and lent to the cell as pointers: no copy. The calling
 * convention, the reused argument arrays and what still allocates per call are described on {@link
 * Cells}.
 */
final class CellsSvmSessionBackend extends YamlSession.Backend {
  private final SvmSessionBackend backend;
  private final YamlOptions options;
  private final YamlCellsTruffle.session_decode_Cell decode;
  private final YamlCellsTruffle.session_write_text_Cell writeText;
  private final YamlCellsTruffle.session_retain_Cell retain;
  private final YamlCellsTruffle.stream_feed_Cell feed;
  private final YamlCellsTruffle.stream_next_Cell next;
  // Pointer and void results have no typed method: these four go through execute(Object[]).
  private final YamlCellsTruffle.session_reset_Cell reset;
  private final YamlCellsTruffle.stream_create_Cell streamCreate;
  private final YamlCellsTruffle.stream_finish_Cell streamFinish;
  private final YamlCellsTruffle.stream_release_Cell streamRelease;

  // One argument array per cell, in seam order; `*` slots are written per call (see Cells).
  /** session, data*, length*, flags*, text_out = NULL, text_capacity = 0, result. */
  private final @Nullable Object[] decodeArguments;

  /** session, text_out*, text_capacity*, result*. */
  private final @Nullable Object[] writeTextArguments;

  /** session, result*. */
  private final @Nullable Object[] retainArguments;

  /** stream*, data*, length*. */
  private final @Nullable Object[] feedArguments = new Object[3];

  /** stream*, flags*, result. */
  private final @Nullable Object[] nextArguments;

  /** session. */
  private final Object[] resetArguments;

  /** session, max_document_bytes*, buffer_bytes*. */
  private final @Nullable Object[] streamCreateArguments;

  /** stream*: stream_finish and stream_release. */
  private final @Nullable Object[] streamArguments = new Object[1];

  private final Cells.Handle stream = new Cells.Handle();

  CellsSvmSessionBackend(YamlOptions options) {
    this.options = options;
    backend = new SvmSessionBackend(options);
    try {
      decode = Cells.adopt(new YamlCellsTruffle.session_decode_Cell());
      writeText = Cells.adopt(new YamlCellsTruffle.session_write_text_Cell());
      retain = Cells.adopt(new YamlCellsTruffle.session_retain_Cell());
      feed = Cells.adopt(new YamlCellsTruffle.stream_feed_Cell());
      next = Cells.adopt(new YamlCellsTruffle.stream_next_Cell());
      reset = Cells.adopt(new YamlCellsTruffle.session_reset_Cell());
      streamCreate = Cells.adopt(new YamlCellsTruffle.stream_create_Cell());
      streamFinish = Cells.adopt(new YamlCellsTruffle.stream_finish_Cell());
      streamRelease = Cells.adopt(new YamlCellsTruffle.stream_release_Cell());
      // The session and its result slot live as long as this backend: one carrier each.
      NativePointer session = NativePointer.of(backend.sessionHandle().rawValue());
      NativePointer result = NativePointer.of(backend.resultSlot().rawValue());
      decodeArguments = new Object[] {session, null, null, null, NativePointer.NULL, 0L, result};
      writeTextArguments = new Object[] {session, null, null, null};
      retainArguments = new Object[] {session, null};
      nextArguments = new Object[] {null, null, result};
      resetArguments = new Object[] {session};
      streamCreateArguments = new Object[] {session, null, null};
    } catch (RuntimeException | Error failure) {
      backend.release();
      throw failure;
    }
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
    @Nullable Object[] arguments = decodeArguments;
    int status;
    PinnedObject pin = PinnedObject.create(pinnedBytes);
    try {
      arguments[1] = NativePointer.of(pin.addressOfArrayElement(0).rawValue());
      arguments[2] = (long) input.length;
      arguments[3] = backend.decodeFlags();
      status = decode.executeInt(arguments);
    } finally {
      arguments[1] = null;
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
      @Nullable Object[] arguments = writeTextArguments;
      try {
        arguments[3] = NativePointer.of(output.rawValue());
        for (; ; ) {
          int status;
          byte[] bytes = array();
          PinnedObject pin = PinnedObject.create(bytes);
          try {
            arguments[1] = NativePointer.of(pin.addressOfArrayElement(0).rawValue());
            arguments[2] = (long) bytes.length;
            status = writeText.executeInt(arguments);
          } finally {
            arguments[1] = null;
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
      } finally {
        arguments[3] = null;
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
      @Nullable Object[] arguments = retainArguments;
      int status;
      try {
        arguments[1] = NativePointer.of(owned.rawValue());
        status = retain.executeInt(arguments);
      } finally {
        arguments[1] = null;
      }
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
    reset.execute(resetArguments);
  }

  @Override
  protected void release() {
    backend.release();
  }

  @Override
  protected long streamCreate(int maxDocumentBytes, int growth) {
    @Nullable Object[] arguments = streamCreateArguments;
    arguments[1] = (long) maxDocumentBytes;
    arguments[2] = (long) growth;
    // A pointer result: the cell returns its NativePointer carrier (NULL for a failed allocation).
    NativePointer created = (NativePointer) streamCreate.execute(arguments);
    if (created.address() == 0) throw new OutOfMemoryError("YAML stream allocation");
    stream.set(created);
    return created.address();
  }

  @Override
  protected int streamFeed(long stream, byte[] bytes, int offset, int length) {
    @Nullable Object[] arguments = feedArguments;
    PinnedObject pin = PinnedObject.create(bytes);
    try {
      arguments[0] = this.stream.of(stream);
      arguments[1] = NativePointer.of(pin.addressOfArrayElement(offset).rawValue());
      arguments[2] = (long) length;
      return (int) feed.executeLong(arguments);
    } finally {
      arguments[1] = null;
      pin.close();
    }
  }

  @Override
  protected long streamFeed(long stream, MemorySegment data) {
    return backend.streamFeed(stream, data);
  }

  @Override
  protected void streamFinish(long stream) {
    @Nullable Object[] arguments = streamArguments;
    arguments[0] = this.stream.of(stream);
    streamFinish.execute(arguments);
  }

  @Override
  protected int streamNext(long stream) {
    @Nullable Object[] arguments = nextArguments;
    arguments[0] = this.stream.of(stream);
    arguments[1] = backend.decodeFlags();
    int status = next.executeInt(arguments);
    if (status != 8 && status != 9) SvmSessionBackend.check(status, backend.resultSlot());
    return status;
  }

  @Override
  protected void streamRelease(long stream) {
    @Nullable Object[] arguments = streamArguments;
    arguments[0] = this.stream.of(stream);
    streamRelease.execute(arguments);
  }
}
