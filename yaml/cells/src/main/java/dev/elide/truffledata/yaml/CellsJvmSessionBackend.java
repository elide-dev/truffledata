package dev.elide.truffledata.yaml;

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
 * {@code session_reset}, {@code stream_create}, {@code stream_next}, {@code stream_finish} and,
 * through {@link CellsJvmPlanBackend}, {@code typed_decode} — go through cells generated with
 * {@code truffle_backend=ffm}, and {@code stream_release} through the same cells class's FFM
 * binding (below). Everything else is the FFM backend's: this
 * wraps a {@link JvmSessionBackend}, which loaded and fingerprint-checked the library, owns the
 * session, result slot, staging buffer and library reference count, and creates and releases the
 * session; retained results release through the FFM binding, because they outlive this backend
 * and its cells. The control flow is the backend's, so tapes are identical and errors are the
 * same, in the same order. Nothing here catches a cell failure or falls back to the FFM calls.
 *
 * <p>Every cell call uses the cell's static {@code invoke} entry (Myna 0.16.0), a plain static call
 * with raw addresses: no cell instance, argument array, box, {@code NativePointer} or {@code
 * Borrow}. On this backend it wraps each address in {@code MemorySegment.ofAddress} for the FFM
 * downcall, and a failure surfaces exactly as through the cell's {@code execute} (which calls the
 * same {@code invoke}). It does not hold any caller's segment scope, so it is used only for memory
 * this backend owns and keeps alive for the call.
 *
 * <p>Since Myna 0.18.0 the cells seam declares the stream a resource (yaml/cells/ownership.seam),
 * whose destroy cell has no static {@code invoke}: its {@code execute} takes only the owner a
 * guest's {@code stream_create} cell returned. The route's streams are raw handles, so {@link
 * #streamRelease} calls {@link YamlCellsJvmFFM#stream_release}, the downcall the former {@code
 * invoke} made, over the same {@code MemorySegment.ofAddress} view.
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
 * silently called through the first.
 */
final class CellsJvmSessionBackend extends YamlSession.Backend {
  /**
   * The imports this route calls through cells or, for {@code stream_release} (a destroy without
   * {@code invoke}), through the cells' own FFM binding: each must bind in the verified library.
   */
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

  /** The session's handle and result slot: they live as long as {@link #backend}. */
  private final long session;

  private final long result;

  /** Called by {@link CellsRoute#jvm}, which releases {@code backend} if this throws. */
  CellsJvmSessionBackend(JvmSessionBackend backend, Path library) {
    this.backend = backend;
    this.options = backend.sessionOptions();
    bind(library.toAbsolutePath());
    session = backend.sessionHandle().address();
    result = backend.resultSlot().address();
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
    // text_out = NULL, text_capacity = 0: HotSpot has no shared-text arena.
    int status =
        YamlCellsJvmTruffle.session_decode_Cell.invoke(
            session, staged.address(), utf8.length, backend.decodeFlags(), 0L, 0L, result);
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
      int status = YamlCellsJvmTruffle.session_retain_Cell.invoke(session, owned.address());
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
    YamlCellsJvmTruffle.session_reset_Cell.invoke(session);
  }

  @Override
  protected void release() {
    backend.release();
  }

  @Override
  protected long streamCreate(int maxDocumentBytes, int growth) {
    // NULL for a failed allocation.
    long created = YamlCellsJvmTruffle.stream_create_Cell.invoke(session, maxDocumentBytes, growth);
    if (created == 0) throw new OutOfMemoryError("YAML stream allocation");
    return created;
  }

  @Override
  protected int streamFeed(long stream, byte[] bytes, int offset, int length) {
    MemorySegment staged = backend.stage(bytes, offset, length);
    return (int) YamlCellsJvmTruffle.stream_feed_Cell.invoke(stream, staged.address(), length);
  }

  /** The caller's segment: on the FFM binding, which holds its scope for the call (see above). */
  @Override
  protected long streamFeed(long stream, MemorySegment data) {
    return backend.streamFeed(stream, data);
  }

  @Override
  protected void streamFinish(long stream) {
    YamlCellsJvmTruffle.stream_finish_Cell.invoke(stream);
  }

  @Override
  protected int streamNext(long stream) {
    int status = YamlCellsJvmTruffle.stream_next_Cell.invoke(stream, backend.decodeFlags(), result);
    if (status != 8 && status != 9) JvmSessionBackend.check(status, backend.resultSlot());
    return status;
  }

  @Override
  protected void streamRelease(long stream) {
    // The destroy of the YamlStream resource: no invoke, so the cells' FFM binding (see above).
    try {
      YamlCellsJvmFFM.stream_release(MemorySegment.ofAddress(stream));
    } catch (RuntimeException | Error failure) {
      throw failure;
    } catch (Throwable failure) {
      throw new IllegalStateException("Native invocation failed", failure);
    }
  }
}
