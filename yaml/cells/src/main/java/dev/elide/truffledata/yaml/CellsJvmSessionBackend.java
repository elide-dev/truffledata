package dev.elide.truffledata.yaml;

import dev.elide.myna.truffle.NativePointer;
import dev.elide.truffledata.TargetAbi;
import dev.elide.truffledata.tape.SegmentTape;
import dev.elide.truffledata.tape.TapeBorrow;
import dev.elide.truffledata.tape.TapeTextArena;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.yaml.cells.generated.YamlCellsJvmFFM;
import dev.elide.truffledata.yaml.cells.generated.YamlCellsJvmTruffle;
import dev.elide.truffledata.yaml.generated.YamlAbi;
import dev.elide.truffledata.yaml.generated.YamlFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * The HotSpot backend with the Myna Truffle cells route ({@code -Dtruffledata.yaml.cells=true},
 * read once by {@link CellsRoute}): the session's own native calls over memory this backend owns —
 * {@code session_decode} and {@code stream_feed} of staged heap input, {@code session_retain},
 * {@code session_reset}, {@code stream_create}, {@code stream_next}, {@code stream_finish}, {@code
 * stream_release} and, through {@link CellsJvmPlanBackend}, {@code typed_decode} — go through
 * cells generated with {@code truffle_backend=ffm}. Everything else is the FFM backend's: this
 * wraps a {@link JvmSessionBackend}, which loaded and fingerprint-checked the library, owns the
 * session, result slot, staging buffer and library reference count, and creates and releases the
 * session; retained results release through the FFM binding, because they outlive this backend
 * and its cells. The control flow is the backend's, so tapes are identical and errors are the
 * same, in the same order. Nothing here catches a cell failure or falls back to the FFM calls.
 *
 * <p>Caller-supplied native segments ({@link #decode(MemorySegment, long)} and {@link
 * #streamFeed(long, MemorySegment)}) stay on the FFM binding, by design: a cell lends a raw
 * address, while the FFM downcall acquires the segment's scope for the call, so a closed arena or
 * another thread's confined segment is rejected, and a shared arena cannot be closed under the
 * decoder. Through a cell that check would be gone and the decoder could read freed memory. Every
 * other pointer a cell receives here is owned by this backend and outlives the call.
 *
 * <p>Myna's FFM cells bind their symbols through the loader lookup, so the library is also loaded
 * into this class loader ({@link System#load}) and must be the image the FFM backend verified: the
 * cells' symbols must resolve to the same addresses as that library's, and it must carry the
 * production fingerprint. One library per class loader: a second path is rejected rather than
 * silently called through the first. See {@link Cells} for the calling convention.
 */
final class CellsJvmSessionBackend extends YamlSession.Backend {
  /** The imports this route calls through cells: each must bind in the verified library. */
  private static final String[] SYMBOLS = {
    "td_yaml_fingerprint",
    "td_yaml_session_decode",
    "td_yaml_session_retain",
    "td_yaml_session_reset",
    "td_yaml_stream_create",
    "td_yaml_stream_feed",
    "td_yaml_stream_finish",
    "td_yaml_stream_next",
    "td_yaml_stream_release",
    "td_yaml_typed_decode",
  };

  private static @Nullable Path loaded;

  private final JvmSessionBackend backend;
  private final YamlOptions options;
  private final YamlCellsJvmTruffle.session_decode_Cell decode;
  private final YamlCellsJvmTruffle.session_retain_Cell retain;
  private final YamlCellsJvmTruffle.stream_feed_Cell feed;
  private final YamlCellsJvmTruffle.stream_next_Cell next;
  // Pointer and void results have no typed method: these four go through execute(Object[]).
  private final YamlCellsJvmTruffle.session_reset_Cell reset;
  private final YamlCellsJvmTruffle.stream_create_Cell streamCreate;
  private final YamlCellsJvmTruffle.stream_finish_Cell streamFinish;
  private final YamlCellsJvmTruffle.stream_release_Cell streamRelease;

  // One argument array per cell, in seam order; `*` slots are written per call (see Cells).
  /** session, data*, length*, flags*, text_out = NULL, text_capacity = 0, result. */
  private final @Nullable Object[] decodeArguments;

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

  /** Called by {@link CellsRoute#jvm}, which releases {@code backend} if this throws. */
  CellsJvmSessionBackend(JvmSessionBackend backend, Path library) {
    this.backend = backend;
    this.options = backend.sessionOptions();
    bind(library.toAbsolutePath());
    decode = Cells.adopt(new YamlCellsJvmTruffle.session_decode_Cell());
    retain = Cells.adopt(new YamlCellsJvmTruffle.session_retain_Cell());
    feed = Cells.adopt(new YamlCellsJvmTruffle.stream_feed_Cell());
    next = Cells.adopt(new YamlCellsJvmTruffle.stream_next_Cell());
    reset = Cells.adopt(new YamlCellsJvmTruffle.session_reset_Cell());
    streamCreate = Cells.adopt(new YamlCellsJvmTruffle.stream_create_Cell());
    streamFinish = Cells.adopt(new YamlCellsJvmTruffle.stream_finish_Cell());
    streamRelease = Cells.adopt(new YamlCellsJvmTruffle.stream_release_Cell());
    // The session and its result slot live as long as this backend: one carrier each.
    NativePointer session = NativePointer.of(backend.sessionHandle().address());
    NativePointer result = NativePointer.of(backend.resultSlot().address());
    decodeArguments = new Object[] {session, null, null, null, NativePointer.NULL, 0L, result};
    retainArguments = new Object[] {session, null};
    nextArguments = new Object[] {null, null, result};
    resetArguments = new Object[] {session};
    streamCreateArguments = new Object[] {session, null, null};
  }

  private static synchronized void bind(Path library) {
    if (loaded == null) {
      System.load(library.toString());
      // The loader lookup searches every library this class loader has loaded. If another copy of
      // the YAML natives was loaded first, the cells would call into that image with handles the
      // verified one created: require the very addresses of the library the FFM backend opened.
      SymbolLookup bound = SymbolLookup.loaderLookup();
      try (Arena probe = Arena.ofConfined()) {
        SymbolLookup verified = SymbolLookup.libraryLookup(library, probe);
        verifyBindings(library, verified, bound);
      }
      MemorySegment fingerprint;
      try {
        fingerprint = YamlCellsJvmFFM.fingerprint();
      } catch (Throwable failure) {
        throw YamlJvm.callFailure(failure);
      }
      // The cells' own constant folds in the cells options; the library carries the production ABI
      // of the host's class (Myna 0.14.0: Linux AArch64 has its own); never a fallback.
      String expected = YamlAbi.fingerprint(TargetAbi.HOST.charUnsigned());
      if (!fingerprint.reinterpret(expected.length() + 1).getString(0).equals(expected))
        throw new IllegalStateException(
            "Myna/YAML ABI fingerprint mismatch (expected the " + TargetAbi.HOST + " class)");
      loaded = library;
    } else if (!loaded.equals(library))
      throw new IllegalStateException(
          "YAML cells are bound to " + loaded + " in this class loader, not " + library);
  }

  /** Reject missing or redirected imports before constructing any cells with native handles. */
  static void verifyBindings(Path library, SymbolLookup verified, SymbolLookup bound) {
    for (String symbol : SYMBOLS) {
      long expected = verified.find(symbol).map(MemorySegment::address).orElse(0L);
      long actual = bound.find(symbol).map(MemorySegment::address).orElse(0L);
      if (expected == 0 || actual != expected)
        throw new IllegalStateException(
            "YAML cells would bind "
                + symbol
                + " outside "
                + library
                + ": another copy of the YAML natives is loaded in this class loader");
    }
  }

  @Override
  YamlPlan compile(TypedLayout layout, YamlOptions options) {
    return new YamlPlan(
        layout.records,
        new CellsJvmPlanBackend(
            new JvmPlanBackend(
                backend.calls(), layout, options, backend::reference, backend::dereference)),
        options.limits().maxInputBytes());
  }

  @Override
  protected void directories(boolean enabled) {
    backend.directories(enabled);
  }

  @Override
  protected void decode(
      byte[] bytes, int offset, int length, int encoding, @Nullable TapeTextArena text) {
    byte[] utf8 = YamlStatus.utf8(bytes, offset, length, encoding);
    // Staged exactly as the FFM backend stages it: the buffer is the backend's own.
    MemorySegment staged = backend.stage(utf8, 0, utf8.length);
    @Nullable Object[] arguments = decodeArguments;
    int status;
    try {
      arguments[1] = NativePointer.of(staged.address());
      arguments[2] = (long) utf8.length;
      arguments[3] = backend.decodeFlags();
      status = decode.executeInt(arguments);
    } finally {
      arguments[1] = null;
    }
    JvmSessionBackend.check(status, backend.resultSlot());
  }

  /** The caller's segment: on the FFM binding, which holds its scope for the call (see above). */
  @Override
  protected void decode(MemorySegment data, long length) {
    backend.decode(data, length);
  }

  @Override
  protected @Nullable TapeTextArena textArena(byte[] bytes) {
    return null;
  }

  @Override
  protected TapeBorrow borrow() {
    return backend.borrow();
  }

  @Override
  protected YamlMessage retain() {
    YamlFFM.LibraryBinding calls = backend.calls();
    Arena ownedArena = Arena.ofConfined();
    MemorySegment owned = ownedArena.allocate(YamlFFM.YamlResult_LAYOUT);
    boolean retained = false;
    try {
      @Nullable Object[] arguments = retainArguments;
      int status;
      try {
        arguments[1] = NativePointer.of(owned.address());
        status = retain.executeInt(arguments);
      } finally {
        arguments[1] = null;
      }
      JvmSessionBackend.check(status, owned);
      SegmentTape tape = JvmSessionBackend.tape(owned);
      YamlMetadata meta =
          ResultMetadata.read(
              JvmSessionBackend.MEMORY,
              owned.address(),
              tape.nodeCount(),
              options.limits().maxAliasExpansion());
      YamlMessage message =
          YamlMessage.create(
              tape,
              new JvmSessionBackend.Storage(tape, owned) {
                @Override
                public void run() {
                  try {
                    calls.release(owned);
                  } catch (Throwable failure) {
                    throw YamlJvm.callFailure(failure);
                  } finally {
                    ownedArena.close();
                    backend.dereference();
                  }
                }
              },
              meta);
      backend.reference();
      retained = true;
      return message;
    } finally {
      if (!retained) {
        try {
          calls.release(owned);
          reset();
        } catch (Throwable failure) {
          throw YamlJvm.callFailure(failure);
        } finally {
          ownedArena.close();
        }
      }
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
    MemorySegment staged = backend.stage(bytes, offset, length);
    @Nullable Object[] arguments = feedArguments;
    try {
      arguments[0] = this.stream.of(stream);
      arguments[1] = NativePointer.of(staged.address());
      arguments[2] = (long) length;
      return (int) feed.executeLong(arguments);
    } finally {
      arguments[1] = null;
    }
  }

  /** The caller's segment: on the FFM binding, which holds its scope for the call (see above). */
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
    if (status != 8 && status != 9) JvmSessionBackend.check(status, backend.resultSlot());
    return status;
  }

  @Override
  protected void streamRelease(long stream) {
    @Nullable Object[] arguments = streamArguments;
    arguments[0] = this.stream.of(stream);
    streamRelease.execute(arguments);
  }
}
