package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.json.generated.Json;
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
    check();
    Objects.checkFromIndexSize(offset, length, utf8.length);
    if (length == 0) throw new IllegalArgumentException("Empty JSON input");
    if (length > limits.maxInputBytes()) throw new IllegalArgumentException("JSON input limit");
    int status;
    try (PinnedObject pinned = PinnedObject.create(utf8)) {
      status =
          Json.session_decode(
              handle(),
              pinned.addressOfArrayElement(offset),
              length,
              0,
              WordFactory.<CCharPointer>nullPointer(),
              0,
              WordFactory.pointer(result));
    }
    JsonSvm.checkStatus(status);
  }

  private JsonBorrow lend() {
    JsonBorrow lent = JsonSvm.borrow(WordFactory.pointer(result));
    borrow = lent;
    return lent;
  }

  private void giveBack(JsonBorrow lent) {
    borrow = null;
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

  /** Decodes {@code length} bytes at {@code offset} and exports them through {@code sink}. */
  @TruffleBoundary
  public void parse(byte[] utf8, int offset, int length, JsonSink sink) {
    Objects.requireNonNull(sink);
    decodeInto(utf8, offset, length);
    JsonBorrow lent = lend();
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
    JsonBorrow lent = lend();
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
