package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.json.generated.Json;
import dev.elide.truffledata.tape.TapeBorrow;
import dev.elide.truffledata.tape.TapeSession;
import dev.elide.truffledata.tape.TapeTextArena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;
import org.jspecify.annotations.Nullable;

/**
 * Thread-confined JSON decode context: one native parser, padded input buffer and arena, reused
 * across documents, so a small document costs no native allocation in steady state. Sessions are
 * explicit: open one per thread that decodes ({@link #open()} in a static image, {@code
 * JsonJvm.openSession(Path, JsonLimits)} on the JVM), and close it when done. {@link
 * JsonSvm#decode} does not use a session.
 *
 * <ul>
 *   <li>{@link #parse} decodes into the session and exports through a sink; nothing is retained.
 *   <li>{@link #with} lends the root to a reader; views must not escape the call and throw if used
 *       afterwards.
 *   <li>{@link #decode} moves the decoded storage into a {@link JsonMessage} that outlives the
 *       session (no copy of the tape).
 *   <li>{@link #stream} decodes whitespace-separated documents one at a time.
 * </ul>
 *
 * <p>While a borrow is in progress (inside a sink event or a reader) every other call on the
 * session throws {@link IllegalStateException}. The borrow, cursor, text-arena and stream
 * bookkeeping is the shared {@link TapeSession}'s.
 */
public final class JsonSession extends TapeSession<JsonMessage> {
  /**
   * Inputs of at most this many bytes may decode through a bounded critical (native-leaf) call on
   * the JVM (performance plan Task E2; {@code TD_JSON_CRITICAL_LIMIT}).
   */
  public static final int CRITICAL_LIMIT = 4096;

  // TD_JSON_UTF16_INPUT and TD_JSON_LATIN1_INPUT.
  static final int UTF16_INPUT = 2, LATIN1_INPUT = 8;

  /** The td_json_session_decode input flag of a {@link TapeSession} input encoding. */
  static int inputFlag(int encoding) {
    return encoding == UTF16 ? UTF16_INPUT : encoding == LATIN1 ? LATIN1_INPUT : 0;
  }

  /**
   * One runtime's native JSON session calls: static bindings on SVM, Myna FFM bindings on the JVM.
   * Adds the JSON-only stream calls to the shared backend.
   */
  abstract static class Backend extends TapeSession.Backend<JsonMessage> {
    private int directoryFlag = 32;
    @Override protected final void directories(boolean enabled) { directoryFlag = enabled ? 32 : 0; }
    final int directoryFlag() { return directoryFlag; }

    /** td_json_stream_skip: resumes after a document-level failure; returns the native status. */
    abstract int streamSkip(long stream);

    /** td_json_stream_truncated_bytes. */
    abstract long streamTruncatedBytes(long stream);
  }

  JsonSession(JsonLimits limits, Backend backend) {
    super("JSON", limits.maxInputBytes(), backend);
  }

  /** Opens a static-image session with {@link JsonLimits#DEFAULT}. */
  @TruffleBoundary
  public static JsonSession open() {
    return open(JsonLimits.DEFAULT);
  }

  /**
   * Opens a static-image session confined to the calling thread; {@code limits} apply to each
   * document. On the JVM use {@code JsonJvm.openSession}.
   */
  @TruffleBoundary
  public static JsonSession open(JsonLimits limits) {
    JsonSvm.requireRuntime();
    Objects.requireNonNull(limits);
    return new JsonSession(limits, SvmBackend.create(limits));
  }

  @Override
  protected void checkStatus(int status) {
    JsonSvm.checkStatus(status);
  }

  /**
   * Opens a stream of whitespace-separated documents (NDJSON, JSON Lines, concatenated JSON) over
   * this session. The stream is confined to this thread and closed with the session; each document
   * is decoded into the session, so the session's limits and borrowing rules apply to it.
   */
  @TruffleBoundary
  public JsonStream stream(JsonStreamOptions options) {
    Objects.requireNonNull(options);
    long handle = createStream(options.maxDocumentBytes(), options.batchBytes());
    return register(new JsonStream(this, handle, options));
  }

  /** Static-image backend: generated @CFunction bindings, pinned arrays, one unmanaged result. */
  private static final class SvmBackend extends Backend {
    // TD_JSON_WANT_TEXT.
    private static final int TEXT = 1;
    private final VoidPointer session;
    // Reusable JsonResult for borrowed decodes.
    private final VoidPointer result;
    // The tape reader and lexical accessor every borrow of this session rebinds.
    private final JsonSvm.Borrows borrows = new JsonSvm.Borrows();

    private SvmBackend(VoidPointer session, VoidPointer result) {
      this.session = session;
      this.result = result;
    }

    static SvmBackend create(JsonLimits limits) {
      VoidPointer struct = UnmanagedMemory.calloc(Json.JsonLimitsStruct.SIZE);
      if (struct.isNull()) throw new OutOfMemoryError("JSON session limits allocation");
      VoidPointer result = WordFactory.nullPointer();
      try {
        result = UnmanagedMemory.calloc(Json.JsonResult.SIZE);
        if (result.isNull()) throw new OutOfMemoryError("JSON session result allocation");
        Json.JsonLimitsStruct.max_input_bytes(struct, limits.maxInputBytes());
        Json.JsonLimitsStruct.max_index_bytes(struct, limits.maxIndexBytes());
        Json.JsonLimitsStruct.max_nodes(struct, limits.maxNodes());
        Json.JsonLimitsStruct.max_text_bytes(struct, limits.maxTextBytes());
        Json.JsonLimitsStruct.max_depth(struct, limits.maxDepth());
        VoidPointer created = Json.session_create(struct);
        if (created.isNull()) throw new OutOfMemoryError("JSON session allocation");
        return new SvmBackend(created, result);
      } catch (RuntimeException | Error failure) {
        if (result.isNonNull()) UnmanagedMemory.free(result);
        throw failure;
      } finally {
        UnmanagedMemory.free(struct);
      }
    }

    @Override
    protected void decode(
        byte[] data, int offset, int length, int encoding, @Nullable TapeTextArena shared) {
      SvmTextArena text = (SvmTextArena) shared;
      byte[] textArray = text == null ? null : text.bytes();
      int status;
      try (PinnedObject pinned = PinnedObject.create(data);
          PinnedObject out = textArray == null ? null : PinnedObject.create(textArray)) {
        status =
            Json.session_decode(
                session,
                pinned.addressOfArrayElement(offset),
                length,
                inputFlag(encoding) | directoryFlag() | (textArray == null ? 0 : TEXT),
                out == null ? WordFactory.<CCharPointer>nullPointer() : out.addressOfArrayElement(0),
                textArray == null ? 0 : textArray.length,
                result);
      }
      JsonSvm.checkStatus(status);
      if (text != null) text.table = (Pointer) Json.JsonResult.text_table(result);
    }

    @Override
    protected void decode(MemorySegment data, long length) {
      throw new UnsupportedOperationException("Static-image sessions decode byte arrays");
    }

    @Override
    protected TapeTextArena textArena(byte[] array) {
      return new SvmTextArena(array);
    }

    @Override
    protected TapeBorrow borrow() {
      return borrows.borrow(result);
    }

    @Override
    protected JsonMessage retain() {
      VoidPointer owned = UnmanagedMemory.calloc(Json.JsonResult.SIZE);
      if (owned.isNull()) {
        Json.session_reset(session);
        throw new OutOfMemoryError("JSON result allocation");
      }
      int status = Json.session_retain(session, owned);
      if (status != 0) {
        UnmanagedMemory.free(owned);
        Json.session_reset(session);
        JsonSvm.checkStatus(status);
      }
      return JsonSvm.adoptResult(owned);
    }

    @Override
    protected void reset() {
      Json.session_reset(session);
    }

    @Override
    protected void release() {
      Json.session_release(session);
      UnmanagedMemory.free(result);
    }

    @Override
    protected long streamCreate(int maxDocumentBytes, int batchBytes) {
      VoidPointer stream = Json.stream_create(session, maxDocumentBytes, batchBytes);
      if (stream.isNull()) throw new OutOfMemoryError("JSON stream allocation");
      return stream.rawValue();
    }

    @Override
    protected int streamFeed(long stream, byte[] data, int offset, int length) {
      try (PinnedObject pinned = PinnedObject.create(data)) {
        return (int) Json.stream_feed(WordFactory.pointer(stream), pinned.addressOfArrayElement(offset), length);
      }
    }

    @Override
    protected long streamFeed(long stream, MemorySegment data) {
      throw new UnsupportedOperationException("Static-image streams take byte arrays");
    }

    @Override
    protected void streamFinish(long stream) {
      Json.stream_finish(WordFactory.pointer(stream));
    }

    @Override
    protected int streamNext(long stream) {
      return Json.stream_next(WordFactory.pointer(stream), directoryFlag(), result);
    }

    @Override
    protected int streamNext(long stream, boolean byKey) {
      directories(byKey);
      return streamNext(stream);
    }

    @Override
    int streamSkip(long stream) {
      return Json.stream_skip(WordFactory.pointer(stream));
    }

    @Override
    long streamTruncatedBytes(long stream) {
      return Json.stream_truncated_bytes(WordFactory.pointer(stream));
    }

    @Override
    protected void streamRelease(long stream) {
      Json.stream_release(WordFactory.pointer(stream));
    }
  }

  /** Text arena whose per-node table lives in the session's native arena (Task D2). */
  private static final class SvmTextArena extends TapeTextArena {
    Pointer table = WordFactory.nullPointer();

    SvmTextArena(byte[] array) {
      super(array);
    }

    byte[] bytes() {
      return array();
    }

    @Override
    protected long entry(long node) {
      return table.readLong(WordFactory.signed(node * 8));
    }
  }
}
