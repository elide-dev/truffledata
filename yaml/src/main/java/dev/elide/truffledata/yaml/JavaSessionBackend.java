package dev.elide.truffledata.yaml;

import dev.elide.truffledata.tape.HeapTape;
import dev.elide.truffledata.tape.TapeBorrow;
import dev.elide.truffledata.tape.TapeTextArena;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.yaml.events.YamlEventSource;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The Java kernel behind {@link YamlSession}: input preparation, the event source, the decoder and
 * heap-backed compact tapes. A decoded document is plain Java arrays, so retaining it moves the
 * arrays into the message and borrowing it costs no copy either.
 */
final class JavaSessionBackend extends YamlSession.Backend {
  private final YamlOptions options;
  private final YamlEventSource source;
  private JavaDecoder.@Nullable Document current;
  private final Map<Long, JavaStream> streams = new HashMap<>();
  private long nextStream = 1;

  JavaSessionBackend(YamlOptions options, YamlEventSource source) {
    this.options = options;
    this.source = source;
  }

  /** One document of {@code input} (raw caller bytes, any supported encoding). */
  static JavaDecoder.Document decodeDocument(
      YamlEventSource source, byte[] input, YamlOptions options, boolean retain, boolean lastWins) {
    byte[] utf8 = JavaInput.prepare(input, options.limits().maxInputBytes());
    return JavaDecoder.decode(source, utf8, options, retain, lastWins);
  }

  private JavaDecoder.Document decodeChunk(byte[] input) {
    return decodeDocument(
        source, input, options, options.retainSource(), options.duplicateKeys() == DuplicateKeys.LAST_WINS);
  }

  @Override
  YamlPlan compile(TypedLayout layout, YamlOptions options) {
    return new YamlPlan(
        layout.records, new JavaPlanBackend(source, layout, options), options.limits().maxInputBytes());
  }

  @Override
  protected void decode(
      byte[] data, int offset, int length, int encoding, @Nullable TapeTextArena text) {
    current = null;
    byte[] input = YamlSvm.utf8(data, offset, length, encoding);
    try {
      current = decodeChunk(input);
    } catch (JavaFailure failure) {
      throw failure.raise();
    }
  }

  @Override
  protected void decode(MemorySegment data, long length) {
    if (length < 0 || length > data.byteSize() || length > Integer.MAX_VALUE - 8)
      throw new IllegalArgumentException("Expected a bounded YAML segment");
    byte[] bytes = data.asSlice(0, length).toArray(ValueLayout.JAVA_BYTE);
    decode(bytes, 0, bytes.length, YamlSession.UTF8, null);
  }

  // Strings are copied per string: the Java kernel writes no shared text array.
  @Override
  protected @Nullable TapeTextArena textArena(byte[] array) {
    return null;
  }

  private JavaDecoder.Document document() {
    JavaDecoder.Document document = current;
    if (document == null) throw new IllegalStateException("No YAML session document");
    return document;
  }

  @Override
  protected TapeBorrow borrow() {
    JavaDecoder.Document document = document();
    return new TapeBorrow(document.tape(), document.metadata().format);
  }

  @Override
  protected YamlMessage retain() {
    JavaDecoder.Document document = document();
    current = null;
    return YamlMessage.create(document.tape(), new Storage(document.tape()), document.metadata());
  }

  /** Lexical spans and source bytes of a heap tape; nothing to free. */
  private static final class Storage implements YamlMessage.Storage {
    private final HeapTape tape;

    Storage(HeapTape tape) {
      this.tape = tape;
    }

    @Override
    public long span(long node) {
      return tape.span(node);
    }

    @Override
    public byte inputByte(long offset) {
      return tape.inputByte(offset);
    }

    @Override
    public void run() {}
  }

  @Override
  protected void reset() {
    current = null;
  }

  @Override
  protected void release() {
    current = null;
    streams.clear();
  }

  // ---- streams ----------------------------------------------------------------------------------

  private JavaStream stream(long handle) {
    JavaStream stream = streams.get(handle);
    if (stream == null) throw new IllegalStateException("YAML stream closed");
    return stream;
  }

  @Override
  protected long streamCreate(int maxDocumentBytes, int growth) {
    long handle = nextStream++;
    streams.put(handle, new JavaStream(maxDocumentBytes, growth));
    return handle;
  }

  @Override
  protected int streamFeed(long stream, byte[] data, int offset, int length) {
    return stream(stream).feed(data, offset, length);
  }

  @Override
  protected long streamFeed(long stream, MemorySegment data) {
    long length = Math.min(data.byteSize(), Integer.MAX_VALUE - 8);
    byte[] bytes = data.asSlice(0, length).toArray(ValueLayout.JAVA_BYTE);
    return stream(stream).feed(bytes, 0, bytes.length);
  }

  @Override
  protected void streamFinish(long stream) {
    stream(stream).finish();
  }

  @Override
  protected int streamNext(long handle) {
    current = null;
    JavaStream stream = stream(handle);
    int status = stream.next(this::decodeChunk);
    if (status == JavaStream.OK) {
      current = stream.document();
      return status;
    }
    if (status == JavaStream.EMPTY || status == JavaStream.TRUNCATED) return status;
    JavaFailure failure = stream.lastError();
    // Native streams report the failing document's message again on every later call.
    if (failure != null && failure.status == status) throw failure.raise();
    throw new JavaFailure(status, "", 0, 0, false).raise();
  }

  @Override
  protected void streamRelease(long stream) {
    streams.remove(stream);
  }
}
