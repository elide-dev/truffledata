package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.myna.values.ValueTape;
import dev.elide.truffledata.json.generated.Json;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/** Explicit static SVM backend; requires the matching truffledata_json C library at image build. */
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
      return publish(result, () -> {});
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

  private static JsonMessage publish(VoidPointer result, Runnable onClose) {
    {
      Pointer descriptor = (Pointer) Json.JsonResult.view(result);
      if (descriptor.isNull() || descriptor.readInt(0) != ValueTape.ABI_VERSION)
        throw new IllegalStateException("Unsupported Myna value ABI");
      SvmValueTape tape = new SvmValueTape(descriptor);
      long address = result.rawValue();
      Pointer spans = (Pointer) Json.JsonResult.lexical(result);
      long spanCount = Json.JsonResult.lexical_count(result);
      if (spanCount != tape.nodeCount() || (spanCount > 0 && spans.isNull()))
        throw new IllegalStateException("JSON lexical table mismatch");
      // Region table entry 0 (region 1) is the retained input: {data, length}.
      Pointer regions = (Pointer) descriptor.readWord(40);
      if (regions.isNull() || descriptor.readLong(48) < 1)
        throw new IllegalStateException("JSON input region missing");
      Pointer inputData = regions.readWord(0);
      long inputLength = regions.readLong(8);
      JsonMessage.Lexical lexical =
          new JsonMessage.Lexical() {
            @Override
            public long span(long node) {
              return spans.readLong(WordFactory.signed(node * 8));
            }

            @Override
            public byte inputByte(long offset) {
              if (offset < 0 || offset >= inputLength)
                throw new IndexOutOfBoundsException("JSON lexical offset");
              return inputData.readByte(WordFactory.signed(offset));
            }
          };
      return new JsonMessage(
          tape,
          lexical,
          () -> {
            try {
              release(address);
            } finally {
              onClose.run();
            }
          });
    }
  }

  private static void release(long address) {
    VoidPointer result = WordFactory.pointer(address);
    Json.release(result);
    UnmanagedMemory.free(result);
  }
}
