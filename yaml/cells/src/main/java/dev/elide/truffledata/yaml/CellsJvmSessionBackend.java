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
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * The HotSpot backend with the Myna Truffle cells route ({@code -Dtruffledata.yaml.cells=true},
 * read once by {@link CellsRoute}): the decode-path native calls — {@code session_decode}, {@code
 * session_retain}, {@code stream_feed}, {@code stream_next} and, through {@link
 * CellsJvmPlanBackend}, {@code typed_decode} — go through cells generated with {@code
 * truffle_backend=ffm}. Everything else is the FFM backend's: this wraps a {@link
 * JvmSessionBackend}, which loaded and fingerprint-checked the library, owns the session, result
 * slot, staging buffer and library reference count, and keeps its lifecycle calls; the control
 * flow is the backend's, so tapes are identical and errors are the same, in the same order. Inputs
 * are staged exactly as the FFM backend stages them and lent to the cells as pointers.
 *
 * <p>Myna's FFM cells bind their symbols through the loader lookup, so the library is also loaded
 * into this class loader ({@link System#load}), after the same fingerprint check against the
 * production ABI. One library per class loader: a second path is rejected rather than silently
 * called through the first.
 */
final class CellsJvmSessionBackend extends YamlSession.Backend {
  private static @Nullable Path loaded;

  private final JvmSessionBackend backend;
  private final YamlOptions options;
  private final YamlCellsJvmTruffle.session_decode_Cell decode;
  private final YamlCellsJvmTruffle.session_retain_Cell retain;
  private final YamlCellsJvmTruffle.stream_feed_Cell feed;
  private final YamlCellsJvmTruffle.stream_next_Cell next;

  /** Called by {@link CellsRoute#jvm}, which releases {@code backend} if this throws. */
  CellsJvmSessionBackend(JvmSessionBackend backend, Path library) {
    this.backend = backend;
    this.options = backend.sessionOptions();
    bind(library.toAbsolutePath());
    decode = CellsSvmSessionBackend.adopt(new YamlCellsJvmTruffle.session_decode_Cell());
    retain = CellsSvmSessionBackend.adopt(new YamlCellsJvmTruffle.session_retain_Cell());
    feed = CellsSvmSessionBackend.adopt(new YamlCellsJvmTruffle.stream_feed_Cell());
    next = CellsSvmSessionBackend.adopt(new YamlCellsJvmTruffle.stream_next_Cell());
  }

  private static synchronized void bind(Path library) {
    if (loaded == null) {
      System.load(library.toString());
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

  private static NativePointer pointer(MemorySegment segment) {
    return CellsSvmSessionBackend.pointer(segment.address());
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
    call(backend.stage(utf8, 0, utf8.length), utf8.length);
  }

  @Override
  protected void decode(MemorySegment data, long length) {
    if (!data.isNative() || data.address() == 0 || length < 0 || length > data.byteSize())
      throw new IllegalArgumentException("Expected a bounded native YAML segment");
    call(data, length);
  }

  private void call(MemorySegment data, long length) {
    MemorySegment result = backend.resultSlot();
    int status =
        decode.executeInt(
            new Object[] {
              pointer(backend.sessionHandle()),
              pointer(data),
              length,
              backend.decodeFlags(),
              NativePointer.NULL,
              0L,
              pointer(result)
            });
    JvmSessionBackend.check(status, result);
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
    MemorySegment session = backend.sessionHandle();
    Arena ownedArena = Arena.ofConfined();
    MemorySegment owned = ownedArena.allocate(YamlFFM.YamlResult_LAYOUT);
    boolean retained = false;
    try {
      int status = retain.executeInt(new Object[] {pointer(session), pointer(owned)});
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
          calls.session_reset(session);
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
    return (int)
        feed.executeLong(
            new Object[] {
              CellsSvmSessionBackend.pointer(stream),
              pointer(backend.stage(bytes, offset, length)),
              (long) length
            });
  }

  @Override
  protected long streamFeed(long stream, MemorySegment data) {
    if (!data.isNative() || data.address() == 0)
      throw new IllegalArgumentException("Expected native YAML input");
    return feed.executeLong(
        new Object[] {CellsSvmSessionBackend.pointer(stream), pointer(data), data.byteSize()});
  }

  @Override
  protected void streamFinish(long stream) {
    backend.streamFinish(stream);
  }

  @Override
  protected int streamNext(long stream) {
    MemorySegment result = backend.resultSlot();
    int status =
        next.executeInt(
            new Object[] {
              CellsSvmSessionBackend.pointer(stream), backend.decodeFlags(), pointer(result)
            });
    if (status != 8 && status != 9) JvmSessionBackend.check(status, result);
    return status;
  }

  @Override
  protected void streamRelease(long stream) {
    backend.streamRelease(stream);
  }
}
