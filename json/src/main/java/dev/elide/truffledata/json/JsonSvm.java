package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.myna.values.NativeValueTape;
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

  /** Copies UTF-8 input into retained padded storage. Input must not mutate during this call. */
  @TruffleBoundary
  public static JsonMessage decode(byte[] utf8) {
    if (!ImageInfo.inImageRuntimeCode())
      throw new UnsupportedOperationException("Static JSON requires SVM");
    if (utf8.length == 0) throw new IllegalArgumentException("Empty JSON input");
    if (utf8.length > 64 * 1024 * 1024) throw new IllegalArgumentException("JSON input limit");
    verifyAbi();
    VoidPointer result = UnmanagedMemory.calloc(Json.JsonResult.SIZE);
    if (result.isNull()) throw new OutOfMemoryError("JSON result allocation");
    try {
      int status;
      try (PinnedObject pinned = PinnedObject.create(utf8)) {
        status =
            Json.decode(
                pinned.addressOfArrayElement(0),
                utf8.length,
                64 * 1024 * 1024,
                64 * 1024 * 1024,
                1_000_000,
                64 * 1024 * 1024,
                128,
                result);
      }
      switch (status) {
        case 0 -> {}
        case 2 -> throw new IllegalArgumentException("JSON resource limit");
        case 3 -> throw new OutOfMemoryError("JSON native allocation");
        case 4 -> throw new IllegalArgumentException("Invalid JSON");
        default -> throw new IllegalStateException("JSON native status " + status);
      }
      Pointer descriptor = (Pointer) Json.JsonResult.view(result);
      if (descriptor.isNull() || descriptor.readInt(0) != ValueTape.ABI_VERSION)
        throw new IllegalStateException("Unsupported Myna value ABI");
      NativeValueTape tape = new NativeValueTape(descriptor);
      long address = result.rawValue();
      return new JsonMessage(tape, () -> release(address));
    } catch (RuntimeException | Error failure) {
      release(result.rawValue());
      throw failure;
    }
  }

  private static void release(long address) {
    VoidPointer result = WordFactory.pointer(address);
    Json.release(result);
    UnmanagedMemory.free(result);
  }
}
