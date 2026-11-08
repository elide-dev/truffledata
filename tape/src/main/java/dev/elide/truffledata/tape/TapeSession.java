package dev.elide.truffledata.tape;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Thread-confined decode context shared by the format sessions ({@code JsonSession}, and
 * {@code YamlSession} in the YAML plan): one native parser, padded input buffer and arena, reused
 * across documents, so a small document costs no native allocation in steady state. A format
 * subclass supplies the native {@link Backend} and its status mapping; this class owns the borrow,
 * cursor, text-arena and stream bookkeeping.
 *
 * <ul>
 *   <li>{@link #parse} decodes into the session and exports through a sink; nothing is retained.
 *   <li>{@link #with} lends the root to a reader; views must not escape the call and throw if used
 *       afterwards.
 *   <li>{@link #decode} moves the decoded storage into a message of type {@code M} that outlives
 *       the session (no copy of the tape).
 * </ul>
 *
 * <p>While a borrow is in progress (inside a sink event or a reader) every other call on the
 * session throws {@link IllegalStateException}.
 *
 * @param <M> the format's retained message type
 */
public abstract class TapeSession<M extends TapeOwner> implements AutoCloseable {
  /** {@link Backend#decode} input encodings; each backend maps them to its native flags. */
  public static final int UTF8 = 0, UTF16 = 1, LATIN1 = 2;

  // Native stream statuses shared by td_json_stream_next and td_yaml_stream_next.
  static final int STREAM_EMPTY = 8, STREAM_TRUNCATED = 9;

  /**
   * One runtime's native session calls (static {@code @CFunction} bindings on SVM, FFM bindings on
   * the JVM). Every method runs on the session's thread after the session's own checks.
   */
  public abstract static class Backend<M extends TapeOwner> {
    protected Backend() {}

    /**
     * Decodes {@code length} bytes at {@code offset} into the session ({@code encoding}: {@link
     * #UTF8}, {@link #UTF16} or {@link #LATIN1}), and its text arena into {@code text} when given.
     * Throws on failure, leaving the session with no document.
     */
    protected abstract void decode(
        byte[] data, int offset, int length, int encoding, @Nullable TapeTextArena text);

    /** Decodes {@code length} bytes of a native segment the caller keeps alive for the call. */
    protected abstract void decode(MemorySegment data, long length);

    /** A text arena over {@code array}, or null when this runtime writes none. */
    protected abstract @Nullable TapeTextArena textArena(byte[] array);

    /** Borrowed owner of the decoded document; the session ends it before its next call. */
    protected abstract TapeBorrow borrow();

    /** Moves the decoded document into an owned message; on failure the session holds none. */
    protected abstract M retain();

    /** Ends the borrowed document (O(1), frees nothing). */
    protected abstract void reset();

    /** Frees the session; retained messages stay valid. */
    protected abstract void release();

    /** Decodes that took a bounded critical path (JVM); 0 elsewhere. */
    protected long criticalDecodes() {
      return 0;
    }

    // Streams over the format's td_*_stream_* calls: `stream` is the native handle's address.

    /**
     * Creates a native stream over this session; {@code growth} is the format's second size (the
     * simdjson batch for JSON, the buffer growth step for YAML). Throws on allocation failure.
     */
    protected abstract long streamCreate(int maxDocumentBytes, int growth);

    /** Feeds {@code length} bytes at {@code offset}; returns the bytes accepted. */
    protected abstract int streamFeed(long stream, byte[] data, int offset, int length);

    /** Feeds a native segment the caller keeps alive for the call; returns the bytes accepted. */
    protected abstract long streamFeed(long stream, MemorySegment data);

    protected abstract void streamFinish(long stream);

    /**
     * Decodes the next document into the session's borrowed result ({@link #borrow()} reads it);
     * returns the native status (0; 8 no complete document; 9 truncated; else the failure).
     */
    protected abstract int streamNext(long stream);

    protected abstract void streamRelease(long stream);
  }

  private final Thread thread = Thread.currentThread();
  private final String format;
  private final long maxInputBytes;
  // Null once closed.
  private @Nullable Backend<M> backend;
  private @Nullable TapeBorrow borrow;
  private @Nullable TapeText text;
  // Text array for shared-text parses; replaced once a string shares it.
  private @Nullable TapeTextArena arena;
  // The document a stream's next() lent: valid until the next call on the session or any of its
  // streams, which ends it first.
  private @Nullable TapeBorrow streamed;
  private @Nullable TapeStream<M> streamedBy;
  private final List<TapeStream<M>> streams = new ArrayList<>(1);

  /**
   * A session confined to the calling thread over {@code backend}; {@code format} names it in
   * messages and {@code maxInputBytes} bounds each document's input before any native call.
   */
  protected TapeSession(String format, long maxInputBytes, Backend<M> backend) {
    this.format = Objects.requireNonNull(format);
    this.maxInputBytes = maxInputBytes;
    this.backend = Objects.requireNonNull(backend);
  }

  /** Whether empty input is a document in this format. */
  protected boolean allowsEmptyInput() { return false; }

  /** Maps a native status to the format's exception; returns normally for status 0. */
  protected abstract void checkStatus(int status);

  /** Confinement, liveness and pin checks; a document lent by a stream stays lent. */
  final Backend<M> guard() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException(format + " session thread mismatch");
    Backend<M> open = backend;
    if (open == null) throw new IllegalStateException(format + " session closed");
    if (borrow != null) throw new IllegalStateException(format + " session is borrowed");
    return open;
  }

  /** {@link #guard()}, then ends a document lent by a stream: every call overwrites it. */
  protected final Backend<M> check() {
    Backend<M> open = guard();
    endStreamed(open);
    return open;
  }

  /** The format name used in messages. */
  final String format() {
    return format;
  }

  private void endStreamed(Backend<M> open) {
    TapeBorrow lent = streamed;
    if (lent == null) return;
    streamed = null;
    streamedBy = null;
    try {
      lent.end();
    } finally {
      open.reset();
    }
  }

  /** Decodes into the session; the borrowed result is valid until the next session call. */
  private Backend<M> decodeInto(
      byte[] data, int offset, int length, int encoding, @Nullable TapeTextArena shared) {
    Backend<M> open = check();
    Objects.checkFromIndexSize(offset, length, data.length);
    if (length == 0 && !allowsEmptyInput()) throw new IllegalArgumentException("Empty " + format + " input");
    // UTF-16 input is at least half as long in UTF-8; the native side checks the exact length.
    if (length > (encoding == UTF16 ? 2L : 1L) * maxInputBytes)
      throw new IllegalArgumentException(format + " input limit");
    open.decode(data, offset, length, encoding, shared);
    return open;
  }

  /** The text arena for the next shared-text parse of {@code length} input bytes, if any. */
  private @Nullable TapeTextArena textArena(int length) {
    Backend<M> open = check();
    // Initial estimate; JSON proves this bound. Other producers negotiate decoded text
    // capacity and grow the arena before exposing any strings (tape/native/text_arena.h).
    long need = 2L * length + 16;
    if (need > Integer.MAX_VALUE) throw new IllegalArgumentException(format + " input limit");
    TapeTextArena text = arena;
    // A string still references the previous array: never overwrite it.
    if (text == null || text.shared || text.array.length < need) {
      text = open.textArena(new byte[(int) need]);
      arena = text;
    }
    return text;
  }

  private TapeBorrow lend(Backend<M> open, @Nullable TapeTextArena shared) {
    TapeBorrow lent = open.borrow();
    // One text cursor (and export stack) per session, rebound to each borrow.
    TapeText cursor = text;
    if (cursor == null) text = cursor = new TapeText(lent);
    lent.adoptCursor(cursor);
    cursor.arena(shared);
    borrow = lent;
    return lent;
  }

  // ---- Streams ---------------------------------------------------------------------------------

  /** Creates a native stream for a format subclass's stream factory. */
  protected final long createStream(int maxDocumentBytes, int growth) {
    return check().streamCreate(maxDocumentBytes, growth);
  }

  /** Registers a stream the subclass created: the session closes it on close. */
  protected final <S extends TapeStream<M>> S register(S stream) {
    streams.add(stream);
    return stream;
  }

  /** Exports the stream document just decoded into the session through {@code sink}. */
  final void exportStreamed(Backend<M> open, TapeSink sink) {
    // Streaming modes never share a text array (user decision 1): strings are copied per string.
    export(open, null, sink);
  }

  /** Lends the stream document just decoded until the next call on the session or its streams. */
  final TapeValue lendStreamed(Backend<M> open, TapeStream<M> by) {
    TapeBorrow lent = lend(open, null);
    // Not a pin: the next call ends this borrow instead of failing.
    borrow = null;
    streamed = lent;
    streamedBy = by;
    return lent.root();
  }

  /** Detaches the document {@code by}'s {@link TapeStream#next()} lent into an owned message. */
  final M retainStreamed(TapeStream<M> by) {
    Backend<M> open = guard();
    TapeBorrow lent = streamed;
    if (lent == null || streamedBy != by)
      throw new IllegalStateException("No " + format + " stream document to retain");
    streamed = null;
    streamedBy = null;
    lent.end();
    return open.retain();
  }

  final void streamClosed(TapeStream<M> stream) {
    streams.remove(stream);
  }

  private void giveBack(Backend<M> open, TapeBorrow lent) {
    borrow = null;
    TapeText cursor = text;
    if (cursor != null) cursor.arena(null);
    try {
      lent.end();
    } finally {
      open.reset();
    }
  }

  private void export(Backend<M> open, @Nullable TapeTextArena shared, TapeSink sink) {
    TapeBorrow lent = lend(open, shared);
    try {
      lent.export(sink);
    } finally {
      giveBack(open, lent);
    }
  }

  /** Decodes {@code utf8} and exports it through {@code sink}; nothing outlives the call. */
  @TruffleBoundary
  public final void parse(byte[] utf8, TapeSink sink) {
    parse(utf8, 0, utf8.length, sink);
  }

  /**
   * Decodes {@code length} bytes at {@code offset} and exports them through {@code sink}. When the
   * sink asks for {@linkplain TapeSink#sharedText() shared text} in a static image, the decode also
   * writes every string once into a per-document array that the sink's {@code TruffleString}s
   * share; on the JVM strings are copied.
   */
  @TruffleBoundary
  public final void parse(byte[] utf8, int offset, int length, TapeSink sink) {
    parse(utf8, offset, length, UTF8, sink);
  }

  /**
   * Decodes a guest string and exports it through {@code sink}; nothing outlives the call. The
   * string's own storage is passed without a Java copy (on the JVM, into the session's native
   * staging buffer): ASCII text as UTF-8, Latin-1 text as Latin-1 and other UTF-16 text as UTF-16,
   * transcoded natively; an unpaired surrogate is rejected like any other invalid input. Strings
   * in other encodings are first transcoded to UTF-8 by {@code TruffleString}; invalid UTF-8 is
   * rejected.
   */
  @TruffleBoundary
  public final void parse(TruffleString text, TapeSink sink) {
    Objects.requireNonNull(sink);
    TruffleString.Encoding stored;
    int encoding;
    if (text.isCompatibleToUncached(TruffleString.Encoding.UTF_16)) {
      TruffleString.CodeRange range = text.getCodeRangeUncached(TruffleString.Encoding.UTF_16);
      if (range == TruffleString.CodeRange.ASCII) {
        stored = TruffleString.Encoding.UTF_8;
        encoding = UTF8;
      } else if (range == TruffleString.CodeRange.LATIN_1) {
        stored = TruffleString.Encoding.ISO_8859_1;
        encoding = LATIN1;
      } else {
        stored = TruffleString.Encoding.UTF_16;
        encoding = UTF16;
      }
    } else {
      stored = TruffleString.Encoding.UTF_8;
      encoding = UTF8;
    }
    // ASCII and Latin-1 UTF-16 strings are stored compactly, so switching to their byte encoding
    // shares the string's array; UTF-16 strings above Latin-1 are stored as UTF-16 already.
    TruffleString view = text.switchEncodingUncached(stored);
    if (encoding == UTF8 && !view.isValidUncached(TruffleString.Encoding.UTF_8))
      throw new IllegalArgumentException("Invalid UTF-8 " + format + " input");
    InternalByteArray bytes = view.getInternalByteArrayUncached(stored);
    parse(bytes.getArray(), bytes.getOffset(), bytes.getLength(), encoding, sink);
  }

  private void parse(byte[] data, int offset, int length, int encoding, TapeSink sink) {
    Objects.requireNonNull(sink);
    Objects.checkFromIndexSize(offset, length, data.length);
    TapeTextArena shared = sink.sharedText() ? textArena(length) : null;
    export(decodeInto(data, offset, length, encoding, shared), shared, sink);
  }

  /**
   * JVM: decodes {@code length} bytes of a live native segment the calling thread can access
   * (copied into the session; nothing is retained) and exports them through {@code sink}. Heap
   * segments are rejected; static images take byte arrays only.
   */
  @TruffleBoundary
  public final void parse(MemorySegment data, long length, TapeSink sink) {
    Objects.requireNonNull(sink);
    Backend<M> open = check();
    if (length < 0 || (length == 0 && !allowsEmptyInput()))
      throw new IllegalArgumentException("Empty " + format + " input");
    if (length > data.byteSize())
      throw new IllegalArgumentException(format + " length exceeds the segment");
    if (length > maxInputBytes) throw new IllegalArgumentException(format + " input limit");
    open.decode(data, length);
    export(open, null, sink);
  }

  /**
   * Decodes {@code utf8} and applies {@code reader} to its root. The root and every view derived
   * from it are valid only during the call.
   */
  @TruffleBoundary
  public final <R> R with(byte[] utf8, Function<TapeValue, R> reader) {
    Objects.requireNonNull(reader);
    Backend<M> open = decodeInto(utf8, 0, utf8.length, UTF8, null);
    TapeBorrow lent = lend(open, null);
    try {
      return reader.apply(lent.root());
    } finally {
      giveBack(open, lent);
    }
  }

  /** Decodes {@code utf8} into a message that owns its storage and outlives this session. */
  @TruffleBoundary
  public final M decode(byte[] utf8) {
    return decode(utf8, 0, utf8.length);
  }

  /** Decodes {@code length} bytes at {@code offset} into an independently owned message. */
  @TruffleBoundary
  public final M decode(byte[] utf8, int offset, int length) {
    return decodeInto(utf8, offset, length, UTF8, null).retain();
  }

  /** Validates {@code utf8} under the session's limits and retains nothing. */
  @TruffleBoundary
  public final void validate(byte[] utf8) {
    decodeInto(utf8, 0, utf8.length, UTF8, null).reset();
  }

  /** Diagnostic: decodes that took the JVM's bounded critical path; 0 on SVM. */
  @TruffleBoundary
  public final long criticalDecodes() {
    return check().criticalDecodes();
  }

  /** Frees the session. Messages it decoded stay valid. Closing during a borrow throws. */
  @Override
  @TruffleBoundary
  public final void close() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException(format + " session thread mismatch");
    if (borrow != null) throw new IllegalStateException(format + " session is borrowed");
    Backend<M> open = backend;
    if (open != null) {
      try {
        endStreamed(open);
        // Streams borrow the native session: close them first (their views fail from now on).
        for (TapeStream<M> stream : List.copyOf(streams)) stream.close();
      } finally {
        backend = null;
        open.release();
      }
    }
  }
}
