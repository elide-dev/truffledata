package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.RootNode;
import dev.elide.myna.truffle.NativePointer;
import dev.elide.truffledata.json.cells.generated.JsonCellsTruffle;
import dev.elide.truffledata.json.generated.Json;
import dev.elide.truffledata.json.tests.SvmAcceptance;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.graalvm.polyglot.Context;
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
    compiled();
    SvmAcceptance.main(args);
    System.out.println("JSON cells native acceptance passed");
  }

  /**
   * JSON cells called from runtime-compiled Truffle code, the way a language's function node calls
   * them: the node holds the concrete cells, builds the arguments in compiled code and calls the
   * scalar cell through {@code executeInt}. The root must reach compiled code and stay there
   * (the image is built with the runtime-compilation blocklist checks). The allocation per
   * decode-and-release pair is printed, not budgeted: it is a fact for the measurement step.
   */
  private static void compiled() {
    byte[] document = "{\"a\":[1,true,\"text\"],\"b\":{\"c\":-2.5}}".getBytes(StandardCharsets.UTF_8);
    long length = document.length;
    long capacity = length + 64;
    Pointer region = UnmanagedMemory.calloc(WordFactory.unsigned(capacity));
    VoidPointer result = UnmanagedMemory.calloc(Json.JsonResult.SIZE);
    try (Context context =
        Context.newBuilder("json-test")
            .allowExperimentalOptions(true)
            .option("engine.BackgroundCompilation", "false")
            .option("engine.CompilationFailureAction", "Throw")
            .build()) {
      if (region.isNull() || result.isNull()) throw new OutOfMemoryError();
      for (int b = 0; b < document.length; b++) region.writeByte(b, document[b]);
      context.initialize("json-test");
      context.enter();
      try {
        CellCalls root = new CellCalls();
        var target = root.getCallTarget();
        Object[] arguments = {region.rawValue(), length, capacity, result.rawValue(), 1000};
        for (int i = 0; i < 200 && !root.compiled; i++)
          if ((Integer) target.call(arguments) != 0) throw new AssertionError("Cell decode status");
        if (!root.compiled) throw new AssertionError("JSON cells must execute in compiled code");
        var bean =
            (com.sun.management.ThreadMXBean)
                java.lang.management.ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported())
          throw new AssertionError("JSON cells allocation counter required");
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        int rounds = 100000;
        arguments[4] = rounds;
        long before = bean.getThreadAllocatedBytes(thread);
        Object status = target.call(arguments);
        double bytes = (bean.getThreadAllocatedBytes(thread) - before) / (double) rounds;
        if ((Integer) status != 0) throw new AssertionError("Compiled cell decode status");
        if (!root.last) throw new AssertionError("JSON cells left compiled code");
        System.out.println(
            "JSON cells compiled=true bytes/decode+release=" + bytes + " (not budgeted)");
      } finally {
        context.leave();
      }
    } finally {
      UnmanagedMemory.free(result);
      UnmanagedMemory.free(region);
    }
  }

  /** {@code td_json_decode_adopted} then {@code td_json_release} through their cells, in a loop. */
  private static final class CellCalls extends RootNode {
    @Child private JsonCellsTruffle.decode_adopted_Cell decode = new JsonCellsTruffle.decode_adopted_Cell();
    @Child private JsonCellsTruffle.release_Cell release = new JsonCellsTruffle.release_Cell();
    boolean compiled;
    boolean last;

    CellCalls() {
      super(null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object[] arguments = frame.getArguments();
      long data = (Long) arguments[0];
      long length = (Long) arguments[1];
      long capacity = (Long) arguments[2];
      long result = (Long) arguments[3];
      int rounds = (Integer) arguments[4];
      JsonLimits limits = JsonLimits.DEFAULT;
      int status = 0;
      for (int i = 0; i < rounds; i++) {
        status |=
            decode.executeInt(
                new Object[] {
                  NativePointer.of(data),
                  length,
                  capacity,
                  limits.maxInputBytes(),
                  limits.maxIndexBytes(),
                  limits.maxNodes(),
                  limits.maxTextBytes(),
                  limits.maxDepth(),
                  NativePointer.of(result)
                });
        release.execute(new Object[] {NativePointer.of(result)});
      }
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, rounds);
      last = CompilerDirectives.inCompiledCode();
      compiled |= last;
      return status;
    }
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
