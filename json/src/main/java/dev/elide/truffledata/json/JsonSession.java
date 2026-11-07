package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.json.generated.Json;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.function.Function;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;
import org.jspecify.annotations.Nullable;

/**
 * Thread-confined decode context: one native parser, padded input buffer and arena, reused across
 * documents, so a small document costs no native allocation in steady state. Sessions are explicit:
 * open one per thread that decodes ({@link #open()} in a static image, {@code
 * JsonJvm.openSession(Path, JsonLimits)} on the JVM), and close it when done. {@link
 * JsonSvm#decode} does not use a session.
 *
 * <ul>
 *   <li>{@link #parse} decodes into the session and exports through a sink; nothing is retained.
 *   <li>{@link #with} lends the root to a reader; views must not escape the call and throw if used
 *       afterwards.
 *   <li>{@link #decode} moves the decoded storage into a {@link JsonMessage} that outlives the
 *       session (no copy of the tape).
 * </ul>
 *
 * <p>While a borrow is in progress (inside a sink event or a reader) every other call on the
 * session throws {@link IllegalStateException}.
 */
public final class JsonSession implements AutoCloseable {
  /**
   * Inputs of at most this many bytes may decode through a bounded critical (native-leaf) call on
   * the JVM (performance plan Task E2; {@code TD_JSON_CRITICAL_LIMIT}).
   */
  public static final int CRITICAL_LIMIT = 4096;

  /** One runtime's native session calls: static bindings on SVM, Myna FFM bindings on the JVM. */
  abstract static class Backend {
    /**
     * Decodes {@code length} bytes at {@code offset} into the session ({@code input}: 0 for UTF-8,
     * else {@link #UTF16_INPUT} or {@link #LATIN1_INPUT}), and its text arena into {@code text}
     * when given. Throws on failure, leaving the session with no document.
     */
    abstract void decode(
        byte[] data, int offset, int length, int input, @Nullable JsonTextArena text);

    /** Decodes {@code length} bytes of a native segment the caller keeps alive for the call. */
    abstract void decode(MemorySegment data, long length);

    /** A text arena over {@code array}, or null when this runtime writes none. */
    abstract @Nullable JsonTextArena textArena(byte[] array);

    /** Borrowed owner of the decoded document; the session ends it before its next call. */
    abstract JsonBorrow borrow();

    /** Moves the decoded document into an owned message; on failure the session holds none. */
    abstract JsonMessage retain();

    /** Ends the borrowed document (O(1), frees nothing). */
    abstract void reset();

    /** Frees the session; retained messages stay valid. */
    abstract void release();

    /** Decodes that took the bounded critical path (JVM, Task E2); 0 elsewhere. */
    long criticalDecodes() {
      return 0;
    }
  }

  private final Thread thread = Thread.currentThread();
  private final JsonLimits limits;
  // Null once closed.
  private @Nullable Backend backend;
  private @Nullable JsonBorrow borrow;
  private @Nullable JsonText text;
  // Text array for shared-text parses; replaced once a string shares it.
  private @Nullable JsonTextArena arena;

  JsonSession(JsonLimits limits, Backend backend) {
    this.limits = Objects.requireNonNull(limits);
    this.backend = backend;
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

  private Backend check() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("JSON session thread mismatch");
    Backend open = backend;
    if (open == null) throw new IllegalStateException("JSON session closed");
    if (borrow != null) throw new IllegalStateException("JSON session is borrowed");
    return open;
  }

  /** Decodes into the session; the borrowed result is valid until the next session call. */
  private Backend decodeInto(
      byte[] data, int offset, int length, int input, @Nullable JsonTextArena shared) {
    Backend open = check();
    Objects.checkFromIndexSize(offset, length, data.length);
    if (length == 0) throw new IllegalArgumentException("Empty JSON input");
    // UTF-16 input is at least half as long in UTF-8; the native side checks the exact length.
    if (length > (input == UTF16_INPUT ? 2L : 1L) * limits.maxInputBytes())
      throw new IllegalArgumentException("JSON input limit");
    open.decode(data, offset, length, input, shared);
    return open;
  }

  /** The text arena for the next shared-text parse of {@code length} input bytes, if any. */
  private @Nullable JsonTextArena textArena(int length) {
    Backend open = check();
    // 2 × length + 16 bytes hold any document's text (json/native/text_arena.h).
    long need = 2L * length + 16;
    if (need > Integer.MAX_VALUE) throw new IllegalArgumentException("JSON input limit");
    JsonTextArena text = arena;
    // A string still references the previous array: never overwrite it.
    if (text == null || text.shared || text.array.length < need) {
      text = open.textArena(new byte[(int) need]);
      arena = text;
    }
    return text;
  }

  private JsonBorrow lend(Backend open, @Nullable JsonTextArena shared) {
    JsonBorrow lent = open.borrow();
    // One text cursor (and export stack) per session, rebound to each borrow.
    JsonText cursor = text;
    if (cursor == null) text = cursor = new JsonText(lent);
    lent.adoptCursor(cursor);
    cursor.arena(shared);
    borrow = lent;
    return lent;
  }

  private void giveBack(Backend open, JsonBorrow lent) {
    borrow = null;
    JsonText cursor = text;
    if (cursor != null) cursor.arena(null);
    try {
      lent.end();
    } finally {
      open.reset();
    }
  }

  private void export(Backend open, @Nullable JsonTextArena shared, JsonSink sink) {
    JsonBorrow lent = lend(open, shared);
    try {
      lent.export(sink);
    } finally {
      giveBack(open, lent);
    }
  }

  /** Decodes {@code utf8} and exports it through {@code sink}; nothing outlives the call. */
  @TruffleBoundary
  public void parse(byte[] utf8, JsonSink sink) {
    parse(utf8, 0, utf8.length, sink);
  }

  /**
   * Decodes {@code length} bytes at {@code offset} and exports them through {@code sink}. When the
   * sink asks for {@linkplain JsonSink#sharedText() shared text} in a static image, the decode also
   * writes every string once into a per-document array that the sink's {@code TruffleString}s
   * share; on the JVM strings are copied.
   */
  @TruffleBoundary
  public void parse(byte[] utf8, int offset, int length, JsonSink sink) {
    parse(utf8, offset, length, 0, sink);
  }

  // TD_JSON_UTF16_INPUT and TD_JSON_LATIN1_INPUT.
  static final int UTF16_INPUT = 2, LATIN1_INPUT = 8;

  /**
   * Decodes a guest string and exports it through {@code sink}; nothing outlives the call. The
   * string's own storage is passed without a Java copy (on the JVM, into the session's native
   * staging buffer): ASCII text as UTF-8, Latin-1 text as Latin-1 and other UTF-16 text as UTF-16,
   * transcoded natively (Task D3); an unpaired surrogate is rejected like any other invalid JSON.
   * Strings in other encodings are first transcoded to UTF-8 by {@code TruffleString}; invalid
   * UTF-8 is rejected.
   */
  @TruffleBoundary
  public void parse(TruffleString text, JsonSink sink) {
    Objects.requireNonNull(sink);
    TruffleString.Encoding stored;
    int input;
    if (text.isCompatibleToUncached(TruffleString.Encoding.UTF_16)) {
      TruffleString.CodeRange range = text.getCodeRangeUncached(TruffleString.Encoding.UTF_16);
      if (range == TruffleString.CodeRange.ASCII) {
        stored = TruffleString.Encoding.UTF_8;
        input = 0;
      } else if (range == TruffleString.CodeRange.LATIN_1) {
        stored = TruffleString.Encoding.ISO_8859_1;
        input = LATIN1_INPUT;
      } else {
        stored = TruffleString.Encoding.UTF_16;
        input = UTF16_INPUT;
      }
    } else {
      stored = TruffleString.Encoding.UTF_8;
      input = 0;
    }
    // ASCII and Latin-1 UTF-16 strings are stored compactly, so switching to their byte encoding
    // shares the string's array; UTF-16 strings above Latin-1 are stored as UTF-16 already.
    TruffleString view = text.switchEncodingUncached(stored);
    if (input == 0 && !view.isValidUncached(TruffleString.Encoding.UTF_8))
      throw new IllegalArgumentException("Invalid UTF-8 JSON input");
    InternalByteArray bytes = view.getInternalByteArrayUncached(stored);
    parse(bytes.getArray(), bytes.getOffset(), bytes.getLength(), input, sink);
  }

  private void parse(byte[] data, int offset, int length, int input, JsonSink sink) {
    Objects.requireNonNull(sink);
    Objects.checkFromIndexSize(offset, length, data.length);
    JsonTextArena shared = sink.sharedText() ? textArena(length) : null;
    export(decodeInto(data, offset, length, input, shared), shared, sink);
  }

  /**
   * JVM: decodes {@code length} bytes of a live native segment the calling thread can access
   * (copied into the session; nothing is retained) and exports them through {@code sink}. Heap
   * segments are rejected; static images take byte arrays only.
   */
  @TruffleBoundary
  public void parse(MemorySegment data, long length, JsonSink sink) {
    Objects.requireNonNull(sink);
    Backend open = check();
    if (length <= 0) throw new IllegalArgumentException("Empty JSON input");
    if (length > data.byteSize()) throw new IllegalArgumentException("JSON length exceeds the segment");
    if (length > limits.maxInputBytes()) throw new IllegalArgumentException("JSON input limit");
    open.decode(data, length);
    export(open, null, sink);
  }

  /**
   * Decodes {@code utf8} and applies {@code reader} to its root. The root and every view derived
   * from it are valid only during the call.
   */
  @TruffleBoundary
  public <R> R with(byte[] utf8, Function<JsonValue, R> reader) {
    Objects.requireNonNull(reader);
    Backend open = decodeInto(utf8, 0, utf8.length, 0, null);
    JsonBorrow lent = lend(open, null);
    try {
      return reader.apply(lent.root());
    } finally {
      giveBack(open, lent);
    }
  }

  /** Decodes {@code utf8} into a message that owns its storage and outlives this session. */
  @TruffleBoundary
  public JsonMessage decode(byte[] utf8) {
    return decode(utf8, 0, utf8.length);
  }

  /** Decodes {@code length} bytes at {@code offset} into an independently owned message. */
  @TruffleBoundary
  public JsonMessage decode(byte[] utf8, int offset, int length) {
    return decodeInto(utf8, offset, length, 0, null).retain();
  }

  /** Validates {@code utf8} under the session's limits and retains nothing. */
  @TruffleBoundary
  public void validate(byte[] utf8) {
    decodeInto(utf8, 0, utf8.length, 0, null).reset();
  }

  /** Diagnostic: decodes that took the JVM's bounded critical path (Task E2); 0 on SVM. */
  @TruffleBoundary
  public long criticalDecodes() {
    return check().criticalDecodes();
  }

  /** Frees the session. Messages it decoded stay valid. Closing during a borrow throws. */
  @Override
  @TruffleBoundary
  public void close() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("JSON session thread mismatch");
    if (borrow != null) throw new IllegalStateException("JSON session is borrowed");
    Backend open = backend;
    if (open != null) {
      backend = null;
      open.release();
    }
  }

  /** Static-image backend: generated @CFunction bindings, pinned arrays, one unmanaged result. */
  private static final class SvmBackend extends Backend {
    // TD_JSON_WANT_TEXT.
    private static final int TEXT = 1;
    private final VoidPointer session;
    // Reusable JsonResult for borrowed decodes.
    private final VoidPointer result;

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
    void decode(byte[] data, int offset, int length, int input, @Nullable JsonTextArena shared) {
      SvmTextArena text = (SvmTextArena) shared;
      byte[] textArray = text == null ? null : text.array;
      int status;
      try (PinnedObject pinned = PinnedObject.create(data);
          PinnedObject out = textArray == null ? null : PinnedObject.create(textArray)) {
        status =
            Json.session_decode(
                session,
                pinned.addressOfArrayElement(offset),
                length,
                input | (textArray == null ? 0 : TEXT),
                out == null ? WordFactory.<CCharPointer>nullPointer() : out.addressOfArrayElement(0),
                textArray == null ? 0 : textArray.length,
                result);
      }
      JsonSvm.checkStatus(status);
      if (text != null) text.table = (Pointer) Json.JsonResult.text_table(result);
    }

    @Override
    void decode(MemorySegment data, long length) {
      throw new UnsupportedOperationException("Static-image sessions decode byte arrays");
    }

    @Override
    JsonTextArena textArena(byte[] array) {
      return new SvmTextArena(array);
    }

    @Override
    JsonBorrow borrow() {
      return JsonSvm.borrow(result);
    }

    @Override
    JsonMessage retain() {
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
    void reset() {
      Json.session_reset(session);
    }

    @Override
    void release() {
      Json.session_release(session);
      UnmanagedMemory.free(result);
    }
  }

  /** Text arena whose per-node table lives in the session's native arena (Task D2). */
  private static final class SvmTextArena extends JsonTextArena {
    Pointer table = WordFactory.nullPointer();

    SvmTextArena(byte[] array) {
      super(array);
    }

    @Override
    long entry(long node) {
      return table.readLong(WordFactory.signed(node * 8));
    }
  }
}
