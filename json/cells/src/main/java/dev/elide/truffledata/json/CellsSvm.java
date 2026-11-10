package dev.elide.truffledata.json;

import dev.elide.myna.truffle.NativeCell;
import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;
import dev.elide.truffledata.json.cells.generated.JsonCellsTruffle;
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
 * nodes of any multi-threaded Truffle AST; no borrow is involved (pointers are lent as {@link
 * NativePointer}s), so the per-thread borrow stack is never touched.
 */
@NullMarked
final class CellsSvm extends JsonCellsRoute.Svm {
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

  // Specialized calls (Myna 0.14.0): each i32 result overrides executeInt on the concrete cell, so
  // there is no result box and no UnexpectedResultException handler.

  @Override
  int decode(long data, long length, JsonLimits limits, long result) {
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
    // A void result: the generic call is the only one the cell has.
    release.execute(new Object[] {NativePointer.of(result)});
  }
}
