package dev.elide.truffledata.json;

import dev.elide.truffledata.TargetAbi;
import dev.elide.truffledata.json.cells.generated.JsonCellsJvmFFM;
import dev.elide.truffledata.json.cells.generated.JsonCellsJvmTruffle;
import dev.elide.truffledata.json.generated.JsonAbi;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The HotSpot cells route ({@code -Dtruffledata.json.cells=true}, read once by {@link
 * JsonCellsRoute}): {@link JsonJvm}'s {@code td_json_decode_adopted} and {@code td_json_release}
 * calls go through cells generated with {@code truffle_backend=ffm}. {@link JsonJvm} keeps every
 * check, the result arena, the status mapping and the owner lifetimes, so messages are identical
 * and errors are the same, in the same order. Both buffers are native segments, so the route calls
 * each cell's static {@code invoke} entry (Myna 0.16.0) with their raw addresses: no cell instance,
 * argument array, box, {@code NativePointer} or {@code Borrow}. The entry is a {@code
 * @TruffleBoundary} static over the same FFM downcall that the cells' {@code execute} methods reach
 * after coercion, and it wraps a downcall failure in the cell's guest error exactly as they do. It
 * neither copies nor retains anything (the adopted input is retained by the native result, exactly
 * as on the baseline route), and it does not track the caller's segment scope: the route checks
 * that itself, before the call.
 *
 * <p>Myna's FFM cells bind their symbols through the loader lookup, so the library is also loaded
 * into this class loader ({@link System#load}) and its fingerprint checked against the production
 * one. One library per class loader: a second path is rejected rather than silently called through
 * the first.
 */
@NullMarked
final class CellsFfm implements JsonCellsRoute.Jvm {
  private static @Nullable Path loaded;

  // With -ea only (the test lanes): the native calls made through these cells, so the tests can
  // prove that JsonJvm takes this route and releases every result exactly once.
  private long decodes;
  private long releases;

  CellsFfm(Path library) {
    bind(library.toAbsolutePath().normalize());
  }

  private static synchronized void bind(Path library) {
    if (loaded == null) {
      System.load(library.toString());
      String actual;
      try {
        actual =
            new String(
                JsonCellsJvmFFM.fingerprint().reinterpret(64).toArray(ValueLayout.JAVA_BYTE),
                StandardCharsets.US_ASCII);
      } catch (RuntimeException | Error failure) {
        throw failure;
      } catch (Throwable failure) {
        throw new IllegalStateException("Native invocation failed", failure);
      }
      // The cells' own constant folds in the cells options; the library carries the production ABI
      // of the host's class; never a fallback.
      if (!actual.equals(JsonAbi.fingerprint(TargetAbi.HOST.charUnsigned())))
        throw new IllegalStateException(
            "Myna/JSON ABI fingerprint mismatch (expected the " + TargetAbi.HOST + " class)");
      loaded = library;
    } else if (!loaded.equals(library))
      throw new IllegalStateException(
          "JSON cells are bound to " + loaded + " in this class loader, not " + library);
  }

  /** {decodes, releases} made through these cells so far; counted only with assertions enabled. */
  long[] calls() {
    return new long[] {decodes, releases};
  }

  private boolean decoded() {
    decodes++;
    return true;
  }

  private boolean released() {
    releases++;
    return true;
  }

  @Override
  public int decodeAdopted(
      MemorySegment data, long length, long capacity, JsonLimits limits, MemorySegment result) {
    // Counted on entry: JsonJvm releases the result of every call, the refused ones included.
    assert decoded();
    // The cells' invoke lends pointers as MemorySegment.ofAddress(address): the downcall no longer
    // sees the caller's segment, so it cannot make the scope check the baseline downcall makes. Make
    // it here, at the same point (after the result is allocated, before native code), through an
    // access of the segment: a foreign confined segment throws WrongThreadException and a closed one
    // IllegalStateException, from the same session check and with the same messages. (length > 0
    // is checked by every caller, so the first byte is in bounds.) Residual difference: the baseline
    // downcall also holds a *shared* arena open for the call's extent; nothing public can, so a
    // shared arena closed by another thread during the call is not caught here. Closing an adopted
    // region before its message closes already breaks JsonDecoder#adopt's contract on both routes.
    data.get(ValueLayout.JAVA_BYTE, 0);
    // The checks the cell's coercions made cannot fail here: JsonLimits admits only positive limits
    // and a depth within 1..128, and JsonJvm passes non-null segments.
    return JsonCellsJvmTruffle.decode_adopted_Cell.invoke(
        data.address(),
        length,
        capacity,
        limits.maxInputBytes(),
        limits.maxIndexBytes(),
        limits.maxNodes(),
        limits.maxTextBytes(),
        limits.maxDepth(),
        result.address());
  }

  @Override
  public void release(MemorySegment result) {
    assert released();
    JsonCellsJvmTruffle.release_Cell.invoke(result.address());
  }
}
