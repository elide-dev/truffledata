package dev.elide.truffledata.json;

import dev.elide.truffledata.json.generated.Json;
import dev.elide.truffledata.json.tests.SvmAcceptance;
import java.util.List;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/**
 * Native Image acceptance of the JSON cells route, in an image built with {@code
 * -Dtruffledata.json.cells=true} and {@code JsonCellsFeature}: the route check, parity of {@link
 * JsonSvm#decode} and {@link JsonSvm#adopt} (now through the cells) against the production static
 * bindings called directly with {@link JsonSvm}'s own checks and status mapping, over the session
 * corpus, fixed and fuzzed inputs, and then the whole shared JSON native acceptance suite on the
 * cells route.
 */
public final class CellsAcceptance {
  private CellsAcceptance() {}

  public static void main(String[] args) throws Exception {
    if (!JsonCellsRoute.ENABLED) throw new AssertionError("Image built without the JSON cells route");
    if (!(ImageSingletons.lookup(JsonCellsRoute.Svm.class) instanceof CellsSvm))
      throw new AssertionError("JSON cells route is not CellsSvm");
    // The cells count their native calls in an image built with -ea, as this one must be.
    boolean counting = false;
    assert counting = true;
    if (!counting) throw new AssertionError("Build the JSON cells acceptance image with -ea");
    List<byte[]> inputs = CellsInputs.inputs();
    int ok = 0;
    int errors = 0;
    // JsonSvm decodes that pass its own checks and so reach the native call.
    long reached = 0;
    long[] before = CellsSvm.calls();
    for (int i = 0; i < inputs.size(); i++) {
      byte[] input = inputs.get(i);
      for (JsonLimits limits : CellsInputs.LIMITS) {
        String expected = CellsInputs.outcome(() -> decode(input, limits));
        same(i, "decode", expected, CellsInputs.outcome(() -> JsonSvm.decode(input, limits)));
        if (input.length > 0 && input.length <= limits.maxInputBytes()) reached++;
        if (expected.startsWith("ok")) ok++;
        else errors++;
      }
      for (long padding : new long[] {0, 64}) {
        String expected = adopted(input, padding, false);
        same(i, "adopt+" + padding, expected, adopted(input, padding, true));
        if (input.length > 0) reached++;
        if (expected.startsWith("ok")) ok++;
        else errors++;
      }
    }
    if (ok < 100 || errors < 100)
      throw new AssertionError("Parity inputs too one-sided: " + ok + " ok, " + errors + " errors");
    // Every message is closed by now. Each JsonSvm decode that reached native code went through a
    // decode cell, exactly once (none took the production binding), and each of those results was
    // released through the release cell, exactly once (a leak or a double release breaks it).
    long[] after = CellsSvm.calls();
    long decodes = after[0] - before[0];
    long releases = after[1] - before[1];
    if (reached == 0 || decodes != reached || releases != reached)
      throw new AssertionError(
          "JSON cells route: " + reached + " decodes reached native code, the cells made "
              + decodes + " decodes and " + releases + " releases");
    System.out.println(
        "JSON cells parity (SVM): " + ok + " ok, " + errors + " errors; " + decodes
            + " cell decodes, " + releases + " cell releases");
    SvmAcceptance.main(args);
    System.out.println("JSON cells native acceptance passed");
  }

  private static void same(int input, String what, String expected, String actual) {
    if (!expected.equals(actual))
      throw new AssertionError(
          "JSON cells parity (SVM): input #" + input + " " + what + "\n expected " + expected
              + "\n   actual " + actual);
  }

  /** An unmanaged region holding the input; the route or the baseline adopts it. */
  private static String adopted(byte[] input, long padding, boolean route) {
    long capacity = input.length + padding;
    Pointer region = UnmanagedMemory.calloc(WordFactory.unsigned(Math.max(1, capacity)));
    if (region.isNull()) throw new OutOfMemoryError();
    for (int b = 0; b < input.length; b++) region.writeByte(b, input[b]);
    // Lambdas capture the raw address, never a word.
    long address = region.rawValue();
    boolean[] released = {false};
    Runnable release =
        () -> {
          if (released[0]) throw new AssertionError("Adopted region released twice");
          released[0] = true;
        };
    try {
      String outcome =
          CellsInputs.outcome(
              () ->
                  route
                      ? JsonSvm.adopt(address, input.length, capacity, release)
                      : adopt(address, input.length, capacity, release));
      if (released[0] != outcome.startsWith("ok"))
        throw new AssertionError("Adopted release ran " + released[0] + " for " + outcome);
      return outcome;
    } finally {
      UnmanagedMemory.free(region);
    }
  }

  // ---- The baseline: JsonSvm's code over the production static bindings, called directly. ----

  private static JsonMessage decode(byte[] utf8, JsonLimits limits) {
    if (utf8.length == 0) throw new IllegalArgumentException("Empty JSON input");
    if (utf8.length > limits.maxInputBytes()) throw new IllegalArgumentException("JSON input limit");
    VoidPointer result = UnmanagedMemory.calloc(Json.JsonResult.SIZE);
    if (result.isNull()) throw new OutOfMemoryError("JSON result allocation");
    try {
      int status;
      PinnedObject pinned = PinnedObject.create(utf8);
      try {
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
      } finally {
        pinned.close();
      }
      JsonSvm.checkStatus(status);
      return message(result, null);
    } catch (RuntimeException | Error failure) {
      free(result);
      throw failure;
    }
  }

  private static JsonMessage adopt(long address, long length, long capacity, Runnable release) {
    java.util.Objects.requireNonNull(release);
    if (address == 0 || length <= 0 || capacity < length)
      throw new IllegalArgumentException("Invalid adopted JSON region");
    JsonLimits limits = JsonLimits.DEFAULT;
    if (length > limits.maxInputBytes()) throw new IllegalArgumentException("JSON input limit");
    VoidPointer result = UnmanagedMemory.calloc(Json.JsonResult.SIZE);
    if (result.isNull()) throw new OutOfMemoryError("JSON result allocation");
    try {
      JsonSvm.checkStatus(
          Json.decode_adopted(
              (CCharPointer) WordFactory.pointer(address),
              length,
              capacity,
              limits.maxInputBytes(),
              limits.maxIndexBytes(),
              limits.maxNodes(),
              limits.maxTextBytes(),
              limits.maxDepth(),
              result));
      return message(result, release);
    } catch (RuntimeException | Error failure) {
      free(result);
      throw failure;
    }
  }

  private static JsonMessage message(
      VoidPointer result, @org.jspecify.annotations.Nullable Runnable onClose) {
    Pointer descriptor = (Pointer) Json.JsonResult.view(result);
    if (descriptor.isNull() || descriptor.readInt(0) != 1)
      throw new IllegalStateException("Unsupported compact tape ABI");
    long address = result.rawValue();
    return new SvmJsonMessage(descriptor, address) {
      @Override
      protected void release() {
        try {
          free(WordFactory.pointer(address));
        } finally {
          if (onClose != null) onClose.run();
        }
      }
    };
  }

  private static void free(VoidPointer result) {
    Json.release(result);
    UnmanagedMemory.free(result);
  }
}
