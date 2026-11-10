package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.json.generated.Json;
import dev.elide.truffledata.json.generated.JsonAbi;
import dev.elide.truffledata.tape.SvmTape;
import dev.elide.truffledata.tape.TapeBorrow;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
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
      // The image's ABI class (Platform.includedIn folds at image build time): one constant.
      String expected = JsonAbi.fingerprint(Platform.includedIn(Platform.LINUX_AARCH64.class));
      if (!CTypeConversion.toJavaString(Json.fingerprint()).equals(expected))
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
      // Folded at image build (JsonCellsRoute): an image without the flag compiles the else arm only.
      if (JsonCellsRoute.ENABLED) status = cellsDecode(utf8, limits, result);
      else try (PinnedObject pinned = PinnedObject.create(utf8)) {
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
          JsonCellsRoute.ENABLED
              ? JsonCellsRoute.svm()
                  .decodeAdopted(address, length, capacity, limits, result.rawValue())
              : Json.decode_adopted(
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
   * {@code td_json_decode} through the cells route. The array is pinned here, around the call, with
   * plain {@code try/finally} (try-with-resources reaches {@code Throwable.addSuppressed}, which the
   * runtime-compilation blocklist rejects), and lent as a pointer: the cell neither copies nor
   * retains it, and the native decode copies the input into its own padded storage, as the baseline.
   */
  private static int cellsDecode(byte[] utf8, JsonLimits limits, VoidPointer result) {
    PinnedObject pinned = PinnedObject.create(utf8);
    try {
      return JsonCellsRoute.svm()
          .decode(
              pinned.addressOfArrayElement(0).rawValue(), utf8.length, limits, result.rawValue());
    } finally {
      pinned.close();
    }
  }

  private static Pointer descriptor(VoidPointer result) {
    Pointer descriptor = (Pointer) Json.JsonResult.view(result);
    if (descriptor.isNull() || descriptor.readInt(0) != 1)
      throw new IllegalStateException("Unsupported compact tape ABI");
    return descriptor;
  }

  private static JsonMessage publish(VoidPointer result, @org.jspecify.annotations.Nullable Runnable onClose) {
    return onClose == null ? new SvmJsonMessage(descriptor(result), result.rawValue())
        : new SvmJsonMessage.Adopted(descriptor(result), result.rawValue(), onClose);
  }

  static JsonMessage adoptResult(VoidPointer result) {
    try { return publish(result, null); }
    catch (RuntimeException | Error failure) { release(result.rawValue()); throw failure; }
  }

  /** A borrow ends before the next rebind, so one reader serves the session. */
  static final class Borrows {
    private @org.jspecify.annotations.Nullable SvmTape tape;
    TapeBorrow borrow(VoidPointer result) {
      Pointer descriptor = descriptor(result);
      SvmTape reader = tape;
      if (reader == null) tape = reader = new SvmTape(descriptor);
      else reader.rebind(descriptor);
      return new TapeBorrow(reader, JsonMessage.FORMAT);
    }
  }

  static void release(long address) {
    if (address == 0) return;
    VoidPointer result = WordFactory.pointer(address);
    if (JsonCellsRoute.ENABLED) JsonCellsRoute.svm().release(address);
    else Json.release(result);
    UnmanagedMemory.free(result);
  }
}
