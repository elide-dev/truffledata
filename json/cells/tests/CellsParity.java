package dev.elide.truffledata.json;

import dev.elide.truffledata.json.generated.JsonFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * HotSpot parity of the JSON cells route. Three routes see the same inputs and limits:
 *
 * <ul>
 *   <li>{@code route}: {@link JsonJvm} as configured ({@code -Dtruffledata.json.cells}: the cells
 *       when on, the FFM handles when off);
 *   <li>{@code cells}: {@link CellsFfm} called directly, so the cells are compared in both lanes;
 *   <li>{@code baseline}: the production {@code td_json_decode_adopted} FFM binding called directly,
 *       with {@link JsonJvm}'s own checks, padding and status mapping.
 * </ul>
 *
 * Every outcome (the canonical message, or the exception's type and message) must be identical.
 */
final class CellsParity {
  static final long PADDING = 64;

  private CellsParity() {}

  /** Returns {ok outcomes, error outcomes}; throws on the first difference. */
  static int[] jvm(Path library) throws Exception {
    List<byte[]> inputs = CellsInputs.inputs();
    int ok = 0;
    int errors = 0;
    try (Arena lookup = Arena.ofConfined();
        JsonDecoder decoder = JsonJvm.open(library)) {
      SymbolLookup symbols = SymbolLookup.libraryLookup(library.toAbsolutePath(), lookup);
      Baseline baseline =
          new Baseline(JsonFFM.decode_adopted(symbols), JsonFFM.release(symbols));
      CellsFfm cells = new CellsFfm(library);
      Baseline direct = new Baseline(cells);
      for (int i = 0; i < inputs.size(); i++) {
        byte[] input = inputs.get(i);
        for (JsonLimits limits : CellsInputs.LIMITS) {
          String expected = CellsInputs.outcome(() -> baseline.decode(input, limits));
          same(i, "decode", expected, CellsInputs.outcome(() -> decoder.decode(input, limits)));
          same(i, "decode/cells", expected, CellsInputs.outcome(() -> direct.decode(input, limits)));
          if (expected.startsWith("ok")) ok++;
          else errors++;
        }
        // Adopted regions: padded (parsed in place) and unpadded (copied once natively).
        for (long padding : new long[] {0, PADDING}) {
          String expected = adopted(input, padding, baseline::adopt);
          same(i, "adopt+" + padding, expected, adopted(input, padding, decoder::adopt));
          same(i, "adopt+" + padding + "/cells", expected, adopted(input, padding, direct::adopt));
          if (expected.startsWith("ok")) ok++;
          else errors++;
        }
      }
      // Every message is closed by now. Each result a cell decoded into was released through the
      // release cell exactly once (a leak or a double release breaks the equality), and with the
      // flag on JsonJvm made exactly as many cell calls as the cells called directly: none of its
      // decodes took another route.
      long[] directCalls = cells.calls();
      if (directCalls[0] == 0 || directCalls[0] != directCalls[1])
        throw new AssertionError(
            "Cells called directly: " + directCalls[0] + " decodes, " + directCalls[1] + " releases");
      CellsFfm route = CellsRouteTest.cells(decoder);
      if (JsonCellsRoute.ENABLED != (route != null))
        throw new AssertionError("Route cells " + route + " with the flag " + JsonCellsRoute.ENABLED);
      if (route != null && !java.util.Arrays.equals(directCalls, route.calls()))
        throw new AssertionError(
            "JsonJvm made "
                + java.util.Arrays.toString(route.calls())
                + " cell calls, the cells called directly "
                + java.util.Arrays.toString(directCalls));
    }
    if (ok < 100 || errors < 100)
      throw new AssertionError("Parity inputs too one-sided: " + ok + " ok, " + errors + " errors");
    return new int[] {ok, errors};
  }

  private interface Adopt {
    JsonMessage adopt(MemorySegment data, long length, Runnable release);
  }

  private static String adopted(byte[] input, long padding, Adopt adopt) {
    try (Arena region = Arena.ofConfined()) {
      MemorySegment data = region.allocate(Math.max(1, input.length + padding), 8);
      MemorySegment.copy(input, 0, data, ValueLayout.JAVA_BYTE, 0, input.length);
      boolean[] released = {false};
      String outcome =
          CellsInputs.outcome(
              () ->
                  adopt.adopt(
                      data.asSlice(0, input.length + padding),
                      input.length,
                      () -> {
                        if (released[0]) throw new AssertionError("Adopted region released twice");
                        released[0] = true;
                      }));
      // The release action runs exactly once, when a published message closes (never on failure).
      if (released[0] != outcome.startsWith("ok"))
        throw new AssertionError("Adopted release ran " + released[0] + " for " + outcome);
      return outcome;
    }
  }

  private static void same(int input, String what, String expected, String actual) {
    if (!expected.equals(actual))
      throw new AssertionError(
          "JSON cells parity: input #"
              + input
              + " "
              + what
              + "\n expected "
              + abbreviate(expected)
              + "\n   actual "
              + abbreviate(actual));
  }

  private static String abbreviate(String text) {
    return text.length() <= 400 ? text : text.substring(0, 400) + "...";
  }

  /** {@link JsonJvm}'s decode and adopt over either native call: the handles or the cells. */
  private static final class Baseline {
    private final JsonCellsRoute.Jvm calls;

    Baseline(MethodHandle decodeAdopted, MethodHandle release) {
      calls =
          new JsonCellsRoute.Jvm() {
            @Override
            public int decodeAdopted(
                MemorySegment data,
                long length,
                long capacity,
                JsonLimits limits,
                MemorySegment result) {
              try {
                return (int)
                    decodeAdopted.invokeExact(
                        data,
                        length,
                        capacity,
                        limits.maxInputBytes(),
                        limits.maxIndexBytes(),
                        limits.maxNodes(),
                        limits.maxTextBytes(),
                        limits.maxDepth(),
                        result);
              } catch (Throwable failure) {
                throw JsonJvm.callFailure(failure);
              }
            }

            @Override
            public void release(MemorySegment result) {
              try {
                release.invokeExact(result);
              } catch (Throwable failure) {
                throw JsonJvm.callFailure(failure);
              }
            }
          };
    }

    Baseline(JsonCellsRoute.Jvm cells) {
      calls = cells;
    }

    JsonMessage decode(byte[] utf8, JsonLimits limits) {
      if (utf8.length == 0) throw new IllegalArgumentException("Empty JSON input");
      if (utf8.length > limits.maxInputBytes())
        throw new IllegalArgumentException("JSON input limit");
      Arena input = Arena.ofConfined();
      try {
        MemorySegment padded = input.allocate(utf8.length + PADDING);
        MemorySegment.copy(utf8, 0, padded, ValueLayout.JAVA_BYTE, 0, utf8.length);
        return publish(padded, utf8.length, limits, input::close);
      } catch (RuntimeException | Error failure) {
        input.close();
        throw failure;
      }
    }

    JsonMessage adopt(MemorySegment data, long length, Runnable release) {
      Objects.requireNonNull(release);
      if (!data.isNative() || !data.scope().isAlive() || data.address() == 0)
        throw new IllegalArgumentException("Expected a live native region");
      if (length <= 0 || length > data.byteSize())
        throw new IllegalArgumentException("JSON length exceeds adopted capacity");
      return publish(data, length, JsonLimits.DEFAULT, release);
    }

    private JsonMessage publish(
        MemorySegment data, long length, JsonLimits limits, Runnable onClose) {
      Arena arena = Arena.ofConfined();
      MemorySegment result = arena.allocate(JsonFFM.JsonResult_LAYOUT);
      boolean published = false;
      try {
        JsonSvm.checkStatus(calls.decodeAdopted(data, length, data.byteSize(), limits, result));
        JsonMessage message =
            new JsonMessage.Managed(
                JsonJvm.tape(result),
                () -> {
                  try {
                    calls.release(result);
                  } finally {
                    arena.close();
                    onClose.run();
                  }
                });
        published = true;
        return message;
      } finally {
        if (!published) {
          calls.release(result);
          arena.close();
        }
      }
    }
  }
}
