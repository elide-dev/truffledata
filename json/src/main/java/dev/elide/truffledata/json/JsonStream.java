package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Whitespace-separated JSON documents (NDJSON, JSON Lines, concatenated JSON) decoded one at a time
 * through a {@link JsonSession} (performance plan Task F2; json/native/stream.h). Open one with
 * {@link JsonSession#stream(JsonStreamOptions)}; it is confined to the session's thread and closed
 * with the session.
 *
 * <p>Input goes into a bounded carry-over buffer: {@link #feed} returns how many bytes it accepted,
 * and fewer than offered means "drain with {@link #next} until it returns {@code false}/{@code null},
 * then feed the rest". {@link #finish()} marks the end of input. Each document is decoded into the
 * session and borrowed: {@link #next(JsonSink)} exports it, {@link #next()} lends its root until the
 * next call on this stream, another stream of the session or the session itself, and {@link
 * #retain()} detaches it into an owned {@link JsonMessage}. Strings are copied per string: streams
 * never share a text array with {@code TruffleString}s.
 *
 * <p>Failures: an invalid document throws {@link IllegalArgumentException} from {@code next}, and
 * the stream stays failed until {@link #skip()} resumes after that document (only document-level
 * errors can be skipped; invalid UTF-8 or raw control bytes fail a whole simdjson batch). A finished
 * stream that ends inside a document throws {@link IllegalArgumentException} ("truncated"), with the
 * incomplete bytes in {@link #truncatedBytes()}.
 */
public final class JsonStream implements AutoCloseable {
  // td_json_status stream codes (json/native/api.h).
  private static final int EMPTY = 8, TRUNCATED = 9;
  private final JsonSession session;
  private final JsonStreamOptions options;
  // Native stream address; 0 once closed.
  private long handle;
  private long documents;

  JsonStream(JsonSession session, long handle, JsonStreamOptions options) {
    this.session = session;
    this.handle = handle;
    this.options = options;
  }

  private JsonSession.Backend check() {
    JsonSession.Backend open = session.check();
    if (handle == 0) throw new IllegalStateException("JSON stream closed");
    return open;
  }

  /**
   * Appends up to {@code length} bytes at {@code offset} and returns how many were accepted (at
   * most {@code 2 × maxDocumentBytes} per call). A finished or failed stream accepts nothing.
   */
  @TruffleBoundary
  public int feed(byte[] chunk, int offset, int length) {
    Objects.checkFromIndexSize(offset, length, chunk.length);
    JsonSession.Backend open = check();
    if (length == 0) return 0;
    // The carry-over buffer never holds more than twice the largest document.
    int offered = Math.min(length, 2 * options.maxDocumentBytes());
    return open.streamFeed(handle, chunk, offset, offered);
  }

  /** Appends all of {@code chunk} that fits; see {@link #feed(byte[], int, int)}. */
  @TruffleBoundary
  public int feed(byte[] chunk) {
    return feed(chunk, 0, chunk.length);
  }

  /**
   * JVM: appends bytes of a live native segment the calling thread can access (copied; nothing is
   * retained) and returns how many were accepted. Heap segments are rejected; static images take
   * byte arrays only.
   */
  @TruffleBoundary
  public long feed(MemorySegment chunk) {
    JsonSession.Backend open = check();
    if (chunk.byteSize() == 0) return 0;
    return open.streamFeed(handle, chunk);
  }

  /** No more input: a final number or literal is complete; an incomplete document is truncated. */
  @TruffleBoundary
  public void finish() {
    check().streamFinish(handle);
  }

  /** Decodes the next document, or returns the backend after a status that means "no document". */
  private JsonSession.@Nullable Backend advance() {
    JsonSession.Backend open = check();
    int status = open.streamNext(handle);
    if (status == EMPTY) return null;
    if (status == TRUNCATED)
      throw new IllegalArgumentException("Truncated JSON document at the end of the stream");
    JsonSvm.checkStatus(status);
    documents++;
    return open;
  }

  /**
   * Decodes the next complete document and exports it through {@code sink}; nothing outlives the
   * call. Returns false when no complete document is buffered (feed more, or the finished stream is
   * exhausted).
   */
  @TruffleBoundary
  public boolean next(JsonSink sink) {
    Objects.requireNonNull(sink);
    JsonSession.Backend open = advance();
    if (open == null) return false;
    session.exportStreamed(open, sink);
    return true;
  }

  /**
   * Decodes the next complete document and lends its root until the next call on this stream,
   * another stream of the session or the session; views of it throw afterwards. Returns null when
   * no complete document is buffered.
   */
  @TruffleBoundary
  public @Nullable JsonValue next() {
    JsonSession.Backend open = advance();
    return open == null ? null : session.lendStreamed(open, this);
  }

  /**
   * Detaches the document lent by {@link #next()} into a message that owns its storage (no copy of
   * the tape) and outlives the stream and session. Views of the lent root throw afterwards.
   */
  @TruffleBoundary
  public JsonMessage retain() {
    if (handle == 0) throw new IllegalStateException("JSON stream closed");
    return session.retainStreamed(this);
  }

  /**
   * Resumes a stream that failed on an invalid document after that document. Returns false when the
   * stream has not failed or its failure cannot be skipped (invalid UTF-8 or raw control bytes,
   * truncation, allocation failure, an incomplete document above {@code maxDocumentBytes}).
   */
  @TruffleBoundary
  public boolean skip() {
    return check().streamSkip(handle) == 0;
  }

  /** Documents decoded so far. */
  public long documents() {
    return documents;
  }

  /**
   * Buffered bytes past the last decoded document; after a truncation failure, the incomplete final
   * document.
   */
  @TruffleBoundary
  public long truncatedBytes() {
    return check().streamTruncatedBytes(handle);
  }

  /** The options this stream was opened with. */
  public JsonStreamOptions options() {
    return options;
  }

  /** Frees the stream's buffers; a lent document is ended, retained messages stay valid. */
  @Override
  @TruffleBoundary
  public void close() {
    if (handle == 0) return;
    JsonSession.Backend open = session.check();
    long stream = handle;
    handle = 0;
    session.streamClosed(this);
    open.streamRelease(stream);
  }
}
