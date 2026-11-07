package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.json.generated.Json;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import java.util.Objects;
import java.util.function.Function;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.WordFactory;
import org.jspecify.annotations.Nullable;

/**
 * Thread-confined decode context on SVM: one native parser, padded input buffer and arena, reused
 * across documents, so a small document costs no native allocation in steady state. Sessions are
 * explicit: open one per thread that decodes, and close it when done. {@link JsonSvm#decode} does
 * not use a session.
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
  private final Thread thread = Thread.currentThread();
  private final JsonLimits limits;
  // td_json_session*, 0 once closed.
  private long session;
  // Reusable JsonResult for borrowed decodes.
  private final long result;
  private @Nullable JsonBorrow borrow;
  private @Nullable JsonText text;
  // Text array for shared-text parses; replaced once a string shares it.
  private @Nullable SvmTextArena arena;

  private JsonSession(JsonLimits limits, long session, long result) {
    this.limits = limits;
    this.session = session;
    this.result = result;
  }

  /** Opens a session with {@link JsonLimits#DEFAULT}. */
  @TruffleBoundary
  public static JsonSession open() {
    return open(JsonLimits.DEFAULT);
  }

  /** Opens a session confined to the calling thread; {@code limits} apply to each document. */
  @TruffleBoundary
  public static JsonSession open(JsonLimits limits) {
    JsonSvm.requireRuntime();
    Objects.requireNonNull(limits);
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
      return new JsonSession(limits, created.rawValue(), result.rawValue());
    } catch (RuntimeException | Error failure) {
      if (result.isNonNull()) UnmanagedMemory.free(result);
      throw failure;
    } finally {
      UnmanagedMemory.free(struct);
    }
  }

  private void check() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("JSON session thread mismatch");
    if (session == 0) throw new IllegalStateException("JSON session closed");
    if (borrow != null) throw new IllegalStateException("JSON session is borrowed");
  }

  private VoidPointer handle() {
    return WordFactory.pointer(session);
  }

  /** Decodes into the session; the borrowed result is valid until the next session call. */
  private void decodeInto(byte[] utf8, int offset, int length) {
    decodeInto(utf8, offset, length, 0, null);
  }

  /**
   * Decodes into the session and, when {@code text} is given, writes the document's text arena
   * into its array (Task D2).
   */
  private void decodeInto(
      byte[] utf8, int offset, int length, int input, @Nullable SvmTextArena text) {
    check();
    Objects.checkFromIndexSize(offset, length, utf8.length);
    if (length == 0) throw new IllegalArgumentException("Empty JSON input");
    // UTF-16 input is at least half as long in UTF-8; the native side checks the exact length.
    if (length > (input == UTF16_INPUT ? 2L : 1L) * limits.maxInputBytes())
      throw new IllegalArgumentException("JSON input limit");
    int status;
    byte[] textArray = text == null ? null : text.array;
    try (PinnedObject pinned = PinnedObject.create(utf8);
        PinnedObject out = textArray == null ? null : PinnedObject.create(textArray)) {
      status =
          Json.session_decode(
              handle(),
              pinned.addressOfArrayElement(offset),
              length,
              input | (textArray == null ? 0 : TEXT),
              out == null ? WordFactory.<CCharPointer>nullPointer() : out.addressOfArrayElement(0),
              textArray == null ? 0 : textArray.length,
              WordFactory.pointer(result));
    }
    JsonSvm.checkStatus(status);
    if (text != null)
      text.table = (org.graalvm.word.Pointer) Json.JsonResult.text_table(WordFactory.pointer(result));
  }

  // TD_JSON_WANT_TEXT.
  private static final int TEXT = 1;

  /** The session's text arena for the next shared-text parse of {@code length} input bytes. */
  private SvmTextArena textArena(int length) {
    long need = 2L * length + 16;
    if (need > Integer.MAX_VALUE) throw new IllegalArgumentException("JSON input limit");
    SvmTextArena text = arena;
    // A string still references the previous array: never overwrite it.
    if (text == null || text.shared || text.array.length < need) arena = text = new SvmTextArena(new byte[(int) need]);
    return text;
  }

  /** Text arena whose per-node table lives in the session's native arena (Task D2). */
  private static final class SvmTextArena extends JsonTextArena {
    org.graalvm.word.Pointer table = WordFactory.nullPointer();

    SvmTextArena(byte[] array) {
      super(array);
    }

    @Override
    long entry(long node) {
      return table.readLong(WordFactory.signed(node * 8));
    }
  }

  private JsonBorrow lend(@Nullable SvmTextArena shared) {
    JsonBorrow lent = JsonSvm.borrow(WordFactory.pointer(result));
    // One text cursor (and export stack) per session, rebound to each borrow.
    JsonText cursor = text;
    if (cursor == null) text = cursor = new JsonText(lent);
    lent.adoptCursor(cursor);
    cursor.arena(shared);
    borrow = lent;
    return lent;
  }

  private void giveBack(JsonBorrow lent) {
    borrow = null;
    JsonText cursor = text;
    if (cursor != null) cursor.arena(null);
    try {
      lent.end();
    } finally {
      Json.session_reset(handle());
    }
  }

  /** Decodes {@code utf8} and exports it through {@code sink}; nothing outlives the call. */
  @TruffleBoundary
  public void parse(byte[] utf8, JsonSink sink) {
    parse(utf8, 0, utf8.length, sink);
  }

  /**
   * Decodes {@code length} bytes at {@code offset} and exports them through {@code sink}. When the
   * sink asks for {@linkplain JsonSink#sharedText() shared text}, the decode also writes every
   * string once into a per-document array that the sink's {@code TruffleString}s share.
   */
  @TruffleBoundary
  public void parse(byte[] utf8, int offset, int length, JsonSink sink) {
    parse(utf8, offset, length, 0, sink);
  }

  // TD_JSON_UTF16_INPUT and TD_JSON_LATIN1_INPUT.
  static final int UTF16_INPUT = 2, LATIN1_INPUT = 8;

  /**
   * Decodes a guest string and exports it through {@code sink}; nothing outlives the call. The
   * string's own storage is passed without a Java copy: ASCII text as UTF-8, Latin-1 text as
   * Latin-1 and other UTF-16 text as UTF-16, transcoded natively (Task D3); an unpaired surrogate
   * is rejected like any other invalid JSON. Strings in other encodings are first transcoded to
   * UTF-8 by {@code TruffleString}; invalid UTF-8 is rejected.
   */
  @TruffleBoundary
  public void parse(TruffleString text, JsonSink sink) {
    Objects.requireNonNull(sink);
    TruffleString.Encoding stored;
    int flags;
    if (text.isCompatibleToUncached(TruffleString.Encoding.UTF_16)) {
      TruffleString.CodeRange range = text.getCodeRangeUncached(TruffleString.Encoding.UTF_16);
      if (range == TruffleString.CodeRange.ASCII) {
        stored = TruffleString.Encoding.UTF_8;
        flags = 0;
      } else if (range == TruffleString.CodeRange.LATIN_1) {
        stored = TruffleString.Encoding.ISO_8859_1;
        flags = LATIN1_INPUT;
      } else {
        stored = TruffleString.Encoding.UTF_16;
        flags = UTF16_INPUT;
      }
    } else {
      stored = TruffleString.Encoding.UTF_8;
      flags = 0;
    }
    // ASCII and Latin-1 UTF-16 strings are stored compactly, so switching to their byte encoding
    // shares the string's array; UTF-16 strings above Latin-1 are stored as UTF-16 already.
    TruffleString view = text.switchEncodingUncached(stored);
    if (flags == 0 && !view.isValidUncached(TruffleString.Encoding.UTF_8))
      throw new IllegalArgumentException("Invalid UTF-8 JSON input");
    InternalByteArray bytes = view.getInternalByteArrayUncached(stored);
    parse(bytes.getArray(), bytes.getOffset(), bytes.getLength(), flags, sink);
  }

  private void parse(byte[] data, int offset, int length, int input, JsonSink sink) {
    Objects.requireNonNull(sink);
    Objects.checkFromIndexSize(offset, length, data.length);
    SvmTextArena shared = sink.sharedText() ? textArena(length) : null;
    decodeInto(data, offset, length, input, shared);
    JsonBorrow lent = lend(shared);
    try {
      lent.export(sink);
    } finally {
      giveBack(lent);
    }
  }

  /**
   * Decodes {@code utf8} and applies {@code reader} to its root. The root and every view derived
   * from it are valid only during the call.
   */
  @TruffleBoundary
  public <R> R with(byte[] utf8, Function<JsonValue, R> reader) {
    Objects.requireNonNull(reader);
    decodeInto(utf8, 0, utf8.length);
    JsonBorrow lent = lend(null);
    try {
      return reader.apply(lent.root());
    } finally {
      giveBack(lent);
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
    decodeInto(utf8, offset, length);
    VoidPointer owned = UnmanagedMemory.calloc(Json.JsonResult.SIZE);
    if (owned.isNull()) {
      Json.session_reset(handle());
      throw new OutOfMemoryError("JSON result allocation");
    }
    int status = Json.session_retain(handle(), owned);
    if (status != 0) {
      UnmanagedMemory.free(owned);
      Json.session_reset(handle());
      JsonSvm.checkStatus(status);
    }
    return JsonSvm.adoptResult(owned);
  }

  /** Validates {@code utf8} under the session's limits and retains nothing. */
  @TruffleBoundary
  public void validate(byte[] utf8) {
    decodeInto(utf8, 0, utf8.length);
    Json.session_reset(handle());
  }

  /** Frees the session. Messages it decoded stay valid. Closing during a borrow throws. */
  @Override
  @TruffleBoundary
  public void close() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("JSON session thread mismatch");
    if (borrow != null) throw new IllegalStateException("JSON session is borrowed");
    if (session != 0) {
      Json.session_release(handle());
      session = 0;
      UnmanagedMemory.free(WordFactory.pointer(result));
    }
  }
}
