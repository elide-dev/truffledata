package dev.elide.truffledata.json;

import dev.elide.myna.truffle.NativeCell;
import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;
import dev.elide.truffledata.json.cells.generated.JsonCells;
import dev.elide.truffledata.json.cells.generated.JsonCellsTruffle;
import org.graalvm.word.WordFactory;
import org.jspecify.annotations.NullMarked;

/**
 * The Native Image cells route ({@code -Dtruffledata.json.cells=true} at image build time, selected
 * by {@link JsonCellsFeature}): {@link JsonSvm}'s {@code td_json_decode}, {@code
 * td_json_decode_adopted} and {@code td_json_release} calls go through cells generated with {@code
 * truffle_backend=native truffle_transitions=direct}, which call the derived static {@code
 * @CFunction} bindings. {@link JsonSvm} keeps every check, the pinning, the result allocation, the
 * status mapping and the owner lifetimes; this class only makes the call, so messages are identical
 * and errors are the same, in the same order.
 *
 * <p>The cells are created and adopted once, at image build time (the Feature's singleton lives in
 * the image heap; the generated cells and this class initialize at build time and hold no address).
 * {@link JsonSvm#decode} is not thread-confined, so the cells are shared by every thread, like the
 * nodes of any multi-threaded Truffle AST. Nothing is copied: every pointer is lent as a {@link
 * NativePointer}, which the cells' coercions take without touching the per-thread scratch stack (a
 * {@code HeapBytes} input would be copied there, NUL-terminated, because the seam declares no
 * {@code borrow}) and without inserting an interop library, so the shared nodes are never mutated.
 *
 * <p>What Myna's cell API costs per call, when the compiler does not scalar-replace it: the {@code
 * Object[]} argument array, one box per integer argument outside the box caches, one {@link
 * NativePointer} per pointer, and the {@code Borrow} the generated cell makes for the {@code
 * ptr<u8>} input (a {@link NativePointer} takes {@code BytesCoercion}'s slow path).
 */
@NullMarked
final class CellsSvm extends JsonCellsRoute.Svm {
  // Images built with -ea only (//json/cells/tests:native): the native calls made through the
  // cells, so the acceptance can prove that JsonSvm takes this route and releases every result
  // exactly once. Without -ea the assert statements below, and these fields with them, fold away.
  private static long decodes;
  private static long releases;

  private final JsonCellsTruffle.decode_Cell decode;
  private final JsonCellsTruffle.decode_adopted_Cell decodeAdopted;
  private final JsonCellsTruffle.release_Cell release;

  CellsSvm() {
    decode = adopt(new JsonCellsTruffle.decode_Cell());
    decodeAdopted = adopt(new JsonCellsTruffle.decode_adopted_Cell());
    release = adopt(new JsonCellsTruffle.release_Cell());
  }

  private static <T extends NativeCell> T adopt(T cell) {
    // A cell holds call-site profiles and is adopted once, under its own language-neutral root.
    new NativeCellRootNode(cell).adoptChildren();
    return cell;
  }

  /** {decodes, releases} made through the cells so far; counted only with assertions enabled. */
  static long[] calls() {
    return new long[] {decodes, releases};
  }

  private static boolean decoded() {
    decodes++;
    return true;
  }

  private static boolean released() {
    releases++;
    return true;
  }

  // Specialized calls (Myna 0.14.0): each i32 result overrides executeInt on the concrete cell, so
  // there is no result box and no UnexpectedResultException handler.

  @Override
  int decode(long data, long length, JsonLimits limits, long result) {
    assert decoded();
    return decode.executeInt(
        new Object[] {
          NativePointer.of(data),
          length,
          limits.maxInputBytes(),
          limits.maxIndexBytes(),
          limits.maxNodes(),
          limits.maxTextBytes(),
          limits.maxDepth(),
          NativePointer.of(result)
        });
  }

  @Override
  int decodeAdopted(long data, long length, long capacity, JsonLimits limits, long result) {
    assert decoded();
    return decodeAdopted.executeInt(
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
  }

  @Override
  void release(long result) {
    assert released();
    Object[] arguments;
    try {
      arguments = new Object[] {NativePointer.of(result)};
    } catch (OutOfMemoryError exhausted) {
      // Release runs while a message closes and while a failed decode unwinds (after an
      // OutOfMemoryError too), where the baseline allocates nothing. A heap that cannot hold the
      // cell's two argument objects must not leak the native result: make the cell's own native
      // call, the same derived binding, without them. Nothing below this point allocates.
      JsonCells.release(WordFactory.pointer(result));
      return;
    }
    // A void result: the generic call is the only one the cell has.
    release.execute(arguments);
  }
}
