package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.myna.values.ValueTape;
import dev.elide.truffledata.json.generated.Json;
import dev.elide.truffledata.tape.SvmValueTape;
import dev.elide.truffledata.tape.TapeBorrow;
import dev.elide.truffledata.tape.TapeLexical;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/**
 * Explicit static SVM backend; requires the matching truffledata_json C library at image build.
 * {@link #decode} decodes each call independently and keeps no per-thread state; open a {@link
 * JsonSession} to reuse native parser and arena storage across documents.
 */
public final class JsonSvm {
  private JsonSvm() {}

  // Only decode can initialize this, after the image-runtime guard. No runtime
  // native pointer or preverified build-process state enters the image heap.
  private static volatile boolean verified;

  private static void verifyAbi() {
    if (!verified) {
      if (!CTypeConversion.toJavaString(Json.fingerprint()).equals(Json.ABI_FINGERPRINT))
        throw new IllegalStateException("Myna/JSON ABI fingerprint mismatch");
      verified = true;
    }
  }

  static void requireRuntime() {
    if (!ImageInfo.inImageRuntimeCode())
      throw new UnsupportedOperationException("Static JSON requires SVM");
    verifyAbi();
  }

  static void checkStatus(int status) {
    switch (status) {
      case 0 -> {}
      case 1, 4, 6 ->
          throw new IllegalArgumentException("Invalid JSON input or contract: " + status);
      case 2 -> throw new IllegalArgumentException("JSON resource limit");
      case 3 -> throw new OutOfMemoryError("JSON native allocation");
      default -> throw new IllegalStateException("JSON native status " + status);
    }
  }

  /** Static backend handle. Decoding never depends on it, so closing it is a no-op. */
  @TruffleBoundary
  public static JsonDecoder open() {
    requireRuntime();
    return new JsonDecoder() {
      @Override
      public JsonMessage decode(byte[] utf8, JsonLimits limits) {
        return JsonSvm.decode(utf8, limits);
      }

      @Override
      public JsonMessage adopt(java.lang.foreign.MemorySegment data, long length, Runnable release) {
        throw new UnsupportedOperationException("Use JsonSvm.adopt(address, length, capacity, release)");
      }

      @Override
      public JsonPlan compile(
          dev.elide.truffledata.Schema root, java.util.Map<String, dev.elide.truffledata.Schema> types) {
        return JsonPlan.compile(root, types);
      }

      @Override
      public void close() {}
    };
  }

  /** Copies UTF-8 input into retained padded storage. Input must not mutate during this call. */
  @TruffleBoundary
  public static JsonMessage decode(byte[] utf8) {
    return decode(utf8, JsonLimits.DEFAULT);
  }

  @TruffleBoundary
  public static JsonMessage decode(byte[] utf8, JsonLimits limits) {
    requireRuntime();
    if (utf8.length == 0) throw new IllegalArgumentException("Empty JSON input");
    if (utf8.length > limits.maxInputBytes()) throw new IllegalArgumentException("JSON input limit");
    VoidPointer result = UnmanagedMemory.calloc(Json.JsonResult.SIZE);
    if (result.isNull()) throw new OutOfMemoryError("JSON result allocation");
    try {
      int status;
      try (PinnedObject pinned = PinnedObject.create(utf8)) {
        status =
            Json.decode(
                pinned.addressOfArrayElement(0),
                utf8.length,
                limits.maxInputBytes(),
                limits.maxIndexBytes(),
                limits.maxNodes(),
                limits.maxTextBytes(),
                limits.maxDepth(),
                result);
      }
      checkStatus(status);
      return publish(result, null);
    } catch (RuntimeException | Error failure) {
      release(result.rawValue());
      throw failure;
    }
  }

  /**
   * Adopts unmanaged memory at {@code address} holding {@code length} logical bytes within
   * {@code capacity}. Parsed in place when capacity leaves 64 padding bytes, else copied once.
   * The pointer API is explicitly unsafe: validity, exclusive ownership and keeping the region
   * alive until {@code release} runs (on close, exactly once) are caller obligations.
   */
  @TruffleBoundary
  public static JsonMessage adopt(long address, long length, long capacity, Runnable release) {
    requireRuntime();
    java.util.Objects.requireNonNull(release);
    if (address == 0 || length <= 0 || capacity < length)
      throw new IllegalArgumentException("Invalid adopted JSON region");
    JsonLimits limits = JsonLimits.DEFAULT;
    if (length > limits.maxInputBytes()) throw new IllegalArgumentException("JSON input limit");
    VoidPointer result = UnmanagedMemory.calloc(Json.JsonResult.SIZE);
    if (result.isNull()) throw new OutOfMemoryError("JSON result allocation");
    try {
      checkStatus(
          Json.decode_adopted(
              (org.graalvm.nativeimage.c.type.CCharPointer) WordFactory.pointer(address),
              length,
              capacity,
              limits.maxInputBytes(),
              limits.maxIndexBytes(),
              limits.maxNodes(),
              limits.maxTextBytes(),
              limits.maxDepth(),
              result));
      return publish(result, release);
    } catch (RuntimeException | Error failure) {
      release(result.rawValue());
      throw failure;
    }
  }

  /**
   * Native side tables and retained input of one published result, plus its release. One object
   * per message serves as both the lexical accessor and the close action; a session keeps one
   * rebindable accessor for its borrows ({@link Borrows}).
   */
  static class SvmLexical implements TapeLexical {
    private Pointer spans;
    private Pointer inputData;
    private long inputLength;

    SvmLexical(Pointer spans, Pointer inputData, long inputLength) {
      bind(spans, inputData, inputLength);
    }

    final void bind(Pointer spans, Pointer inputData, long inputLength) {
      this.spans = spans;
      this.inputData = inputData;
      this.inputLength = inputLength;
    }

    @Override
    public final long span(long node) {
      return spans.readLong(WordFactory.signed(node * 8));
    }

    @Override
    public final byte inputByte(long offset) {
      if (offset < 0 || offset >= inputLength) throw new IndexOutOfBoundsException("JSON lexical offset");
      return inputData.readByte(WordFactory.signed(offset));
    }
  }

  /** An owned result: its lexical accessor is also its close action (one object per message). */
  static class SvmResult extends SvmLexical implements JsonMessage.Storage {
    private final long address;

    SvmResult(Pointer spans, Pointer inputData, long inputLength, long address) {
      super(spans, inputData, inputLength);
      this.address = address;
    }

    @Override
    public void run() {
      release(address);
    }
  }

  /** An adopted region's result: its caller's release runs after the native one. */
  static final class AdoptedResult extends SvmResult {
    private final Runnable onClose;

    AdoptedResult(Pointer spans, Pointer inputData, long inputLength, long address, Runnable onClose) {
      super(spans, inputData, inputLength, address);
      this.onClose = onClose;
    }

    @Override
    public void run() {
      try {
        super.run();
      } finally {
        onClose.run();
      }
    }
  }

  /** The published result's descriptor: Myna value ABI v4 (ABI 6 of this library). */
  private static Pointer descriptor(VoidPointer result) {
    Pointer descriptor = (Pointer) Json.JsonResult.view(result);
    if (descriptor.isNull() || descriptor.readInt(0) != ValueTape.ABI_VERSION_V4)
      throw new IllegalStateException("Unsupported Myna value ABI");
    return descriptor;
  }

  private static Pointer hashes(VoidPointer result) {
    Pointer hashes = (Pointer) Json.JsonResult.key_hashes(result);
    if (hashes.isNull()) throw new IllegalStateException("JSON key-hash table missing");
    return hashes;
  }

  /** Validates a published result and builds its tape reader. */
  private static SvmValueTape tape(VoidPointer result) {
    return new SvmValueTape(descriptor(result), hashes(result));
  }

  private static Pointer spans(VoidPointer result, SvmValueTape tape) {
    Pointer spans = (Pointer) Json.JsonResult.lexical(result);
    long spanCount = Json.JsonResult.lexical_count(result);
    if (spanCount != tape.nodeCount() || (spanCount > 0 && spans.isNull()))
      throw new IllegalStateException("JSON lexical table mismatch");
    return spans;
  }

  // Region table entry 0 (region 1) is the retained input: {data, length}.
  private static Pointer inputRegion(VoidPointer result) {
    Pointer descriptor = (Pointer) Json.JsonResult.view(result);
    Pointer regions = (Pointer) descriptor.readWord(40);
    if (regions.isNull() || descriptor.readLong(48) < 1)
      throw new IllegalStateException("JSON input region missing");
    return regions;
  }

  /**
   * Lexical accessor of an owned result, released on close (then {@code onClose}, for adopted
   * regions). Results without a caller release carry no callback field: one is allocated per
   * retained message.
   */
  private static SvmResult tables(
      VoidPointer result, SvmValueTape tape, @org.jspecify.annotations.Nullable Runnable onClose) {
    Pointer spans = spans(result, tape);
    Pointer regions = inputRegion(result);
    return onClose == null
        ? new SvmResult(spans, regions.readWord(0), regions.readLong(8), result.rawValue())
        : new AdoptedResult(spans, regions.readWord(0), regions.readLong(8), result.rawValue(), onClose);
  }

  private static JsonMessage publish(VoidPointer result, @org.jspecify.annotations.Nullable Runnable onClose) {
    SvmValueTape tape = tape(result);
    return new JsonMessage(tape, tables(result, tape, onClose));
  }

  /** Message owning a retained session result; the result storage is released on failure too. */
  static JsonMessage adoptResult(VoidPointer result) {
    try {
      return publish(result, null);
    } catch (RuntimeException | Error failure) {
      release(result.rawValue());
      throw failure;
    }
  }

  /**
   * One session's reusable tape reader and lexical accessor (Myna 0.11.0's rebindable readers,
   * elide-dev/myna#47): every borrow of the session reads through them, rebound to the session's
   * current result, so a borrow allocates only its owner. A borrow ends (closing its owner) before
   * the session's next call, so no live owner ever sees a rebinding.
   */
  static final class Borrows {
    private @org.jspecify.annotations.Nullable SvmValueTape tape;
    private @org.jspecify.annotations.Nullable SvmLexical lexical;

    /** Borrowed owner over the session's current result; the session ends it before its next call. */
    TapeBorrow borrow(VoidPointer result) {
      Pointer descriptor = descriptor(result);
      Pointer hashes = hashes(result);
      SvmValueTape reader = tape;
      if (reader == null) tape = reader = new SvmValueTape(descriptor, hashes);
      else reader.rebind(descriptor, hashes);
      Pointer spans = spans(result, reader);
      Pointer regions = inputRegion(result);
      SvmLexical tables = lexical;
      if (tables == null) lexical = tables = new SvmLexical(spans, regions.readWord(0), regions.readLong(8));
      else tables.bind(spans, regions.readWord(0), regions.readLong(8));
      return new TapeBorrow(reader, tables, JsonMessage.FORMAT);
    }
  }


  private static void release(long address) {
    if (address == 0) return;
    VoidPointer result = WordFactory.pointer(address);
    Json.release(result);
    UnmanagedMemory.free(result);
  }
}
