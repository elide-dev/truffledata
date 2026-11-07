package dev.elide.truffledata.tape;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Documents decoded one at a time through a {@link TapeSession}: the stream contract shared by
 * {@code JsonStream} and, in the YAML plan, {@code YamlStream} (their native {@code td_*_stream_*}
 * calls have the same create/feed/finish/next/release shape and statuses). A stream is confined to
 * the session's thread and closed with the session.
 *
 * <p>Input goes into a bounded carry-over buffer: {@link #feed} returns how many bytes it accepted,
 * and fewer than offered means "drain with {@link #next} until it returns {@code false}/{@code null},
 * then feed the rest". {@link #finish()} marks the end of input. Each document is decoded into the
 * session and borrowed: {@link #next(TapeSink)} exports it, {@link #next()} lends its root until the
 * next call on this stream, another stream of the session or the session itself, and {@link
 * #retain()} detaches it into an owned message. Strings are copied per string: streams never share
 * a text array with {@code TruffleString}s.
 *
 * <p>An invalid document throws {@link IllegalArgumentException} from {@code next}, and a finished
 * stream that ends inside a document throws {@link IllegalArgumentException} ("truncated").
 *
 * @param <M> the format's retained message type
 */
public abstract class TapeStream<M extends TapeOwner> implements AutoCloseable {
  private final TapeSession<M> session;
  private final int maxFeed;
  // Native stream address; 0 once closed.
  private long handle;
  private long documents;

  /**
   * A stream over the native stream at {@code handle}, which {@code session} created; each {@link
   * #feed} offers at most {@code maxFeed} bytes.
   */
  protected TapeStream(TapeSession<M> session, long handle, int maxFeed) {
    this.session = Objects.requireNonNull(session);
    this.handle = handle;
    this.maxFeed = maxFeed;
  }

  /** The session's backend after the session's and this stream's checks. */
  protected final TapeSession.Backend<M> backend() {
    TapeSession.Backend<M> open = session.check();
    if (handle == 0) throw new IllegalStateException(session.format() + " stream closed");
    return open;
  }

  /** The native stream address; call after {@link #backend()}. */
  protected final long handle() {
    return handle;
  }

  /**
   * Appends up to {@code length} bytes at {@code offset} and returns how many were accepted (at
   * most the format's per-call bound). A finished or failed stream accepts nothing.
   */
  @TruffleBoundary
  public final int feed(byte[] chunk, int offset, int length) {
    Objects.checkFromIndexSize(offset, length, chunk.length);
    TapeSession.Backend<M> open = backend();
    if (length == 0) return 0;
    // The carry-over buffer never holds more than the format's bound.
    int offered = Math.min(length, maxFeed);
    return open.streamFeed(handle, chunk, offset, offered);
  }

  /** Appends all of {@code chunk} that fits; see {@link #feed(byte[], int, int)}. */
  @TruffleBoundary
  public final int feed(byte[] chunk) {
    return feed(chunk, 0, chunk.length);
  }

  /**
   * JVM: appends bytes of a live native segment the calling thread can access (copied; nothing is
   * retained) and returns how many were accepted. Heap segments are rejected; static images take
   * byte arrays only.
   */
  @TruffleBoundary
  public final long feed(MemorySegment chunk) {
    TapeSession.Backend<M> open = backend();
    if (chunk.byteSize() == 0) return 0;
    return open.streamFeed(handle, chunk);
  }

  /** No more input: a final scalar is complete; an incomplete document is truncated. */
  @TruffleBoundary
  public final void finish() {
    backend().streamFinish(handle);
  }

  /** Decodes the next document, or returns null after a status that means "no document". */
  private TapeSession.@Nullable Backend<M> advance() {
    TapeSession.Backend<M> open = backend();
    int status = open.streamNext(handle);
    if (status == TapeSession.STREAM_EMPTY) return null;
    if (status == TapeSession.STREAM_TRUNCATED)
      throw new IllegalArgumentException(
          "Truncated " + session.format() + " document at the end of the stream");
    session.checkStatus(status);
    documents++;
    return open;
  }

  /**
   * Decodes the next complete document and exports it through {@code sink}; nothing outlives the
   * call. Returns false when no complete document is buffered (feed more, or the finished stream is
   * exhausted).
   */
  @TruffleBoundary
  public final boolean next(TapeSink sink) {
    Objects.requireNonNull(sink);
    TapeSession.Backend<M> open = advance();
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
  public final @Nullable TapeValue next() {
    TapeSession.Backend<M> open = advance();
    return open == null ? null : session.lendStreamed(open, this);
  }

  /**
   * Detaches the document lent by {@link #next()} into a message that owns its storage (no copy of
   * the tape) and outlives the stream and session. Views of the lent root throw afterwards.
   */
  @TruffleBoundary
  public final M retain() {
    if (handle == 0) throw new IllegalStateException(session.format() + " stream closed");
    return session.retainStreamed(this);
  }

  /** Documents decoded so far. */
  public final long documents() {
    return documents;
  }

  /** Frees the stream's buffers; a lent document is ended, retained messages stay valid. */
  @Override
  @TruffleBoundary
  public final void close() {
    if (handle == 0) return;
    TapeSession.Backend<M> open = session.check();
    long stream = handle;
    handle = 0;
    session.streamClosed(this);
    open.streamRelease(stream);
  }
}
