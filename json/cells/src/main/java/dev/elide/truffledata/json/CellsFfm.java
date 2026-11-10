package dev.elide.truffledata.json;

import dev.elide.myna.truffle.NativeCell;
import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;
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
 * and errors are the same, in the same order. Pointers are lent as {@link NativePointer}s over the
 * same native segments the baseline passes: the cells neither copy nor retain anything themselves
 * (the adopted input is retained by the native result, exactly as on the baseline route).
 *
 * <p>Myna's FFM cells bind their symbols through the loader lookup, so the library is also loaded
 * into this class loader ({@link System#load}) and its fingerprint checked against the production
 * one. One library per class loader: a second path is rejected rather than silently called through
 * the first.
 */
@NullMarked
final class CellsFfm implements JsonCellsRoute.Jvm {
  private static @Nullable Path loaded;

  // Adopted once per decoder; the decoder is confined to its thread.
  private final JsonCellsJvmTruffle.decode_adopted_Cell decodeAdopted;
  private final JsonCellsJvmTruffle.release_Cell release;

  CellsFfm(Path library) {
    bind(library.toAbsolutePath().normalize());
    decodeAdopted = adopt(new JsonCellsJvmTruffle.decode_adopted_Cell());
    release = adopt(new JsonCellsJvmTruffle.release_Cell());
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

  private static <T extends NativeCell> T adopt(T cell) {
    // A cell holds call-site profiles and is adopted once, under its own language-neutral root.
    new NativeCellRootNode(cell).adoptChildren();
    return cell;
  }

  @Override
  public int decodeAdopted(
      MemorySegment data, long length, long capacity, JsonLimits limits, MemorySegment result) {
    // Specialized call (Myna 0.14.0): the concrete cell's i32 result overrides executeInt, with no
    // result box and no UnexpectedResultException handler.
    return decodeAdopted.executeInt(
        new Object[] {
          NativePointer.of(data.address()),
          length,
          capacity,
          limits.maxInputBytes(),
          limits.maxIndexBytes(),
          limits.maxNodes(),
          limits.maxTextBytes(),
          limits.maxDepth(),
          NativePointer.of(result.address())
        });
  }

  @Override
  public void release(MemorySegment result) {
    // A void result: the generic call is the only one the cell has.
    release.execute(new Object[] {NativePointer.of(result.address())});
  }
}
