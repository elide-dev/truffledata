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
 * <p>What Myna's cell API costs per call. An -O2 image does not inline the generated {@code
 * executeInt} (//json/cells/tests:aot_shape prints the compiled shape), so its arguments are real
 * objects: per decode the {@code Object[]} argument array, a {@code Long} for the length (and one
 * for an adopted region's capacity), a {@link NativePointer} for the input and one for the result,
 * and the {@code Borrow} the generated cell makes for the {@code ptr<u8>} input (a {@link
 * NativePointer} takes {@code BytesCoercion}'s slow path); per release an {@code Object[1]} and a
 * {@link NativePointer}. The baseline makes none of these. The five limit arguments would be five
 * more boxes per call; they are boxed once per limits object instead ({@link Limits}).
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

  // The boxes of the last limits that were not the default. The cells are shared by every thread
  // and this field is racy on purpose: the holder is immutable, and each caller uses only a holder
  // it has compared with its own limits (or made), so a lost update costs one allocation.
  private Limits last = Limits.DEFAULT;

  /**
   * A {@link JsonLimits}' five native arguments as the boxes the cell API takes. The values are
   * above the box caches (the default depth, 128, included), so boxing them per call would allocate
   * five objects per decode.
   */
  private static final class Limits {
    static final Limits DEFAULT = new Limits(JsonLimits.DEFAULT);

    final JsonLimits limits;
    final Long maxInputBytes;
    final Long maxIndexBytes;
    final Long maxNodes;
    final Long maxTextBytes;
    final Integer maxDepth;

    Limits(JsonLimits limits) {
      this.limits = limits;
      maxInputBytes = limits.maxInputBytes();
      maxIndexBytes = limits.maxIndexBytes();
      maxNodes = limits.maxNodes();
      maxTextBytes = limits.maxTextBytes();
      maxDepth = limits.maxDepth();
    }
  }

  private Limits boxed(JsonLimits limits) {
    if (limits == JsonLimits.DEFAULT) return Limits.DEFAULT;
    Limits cached = last;
    if (cached.limits != limits && !cached.limits.equals(limits)) last = cached = new Limits(limits);
    return cached;
  }

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
    Limits boxed = boxed(limits);
    return decode.executeInt(
        new Object[] {
          NativePointer.of(data),
          length,
          boxed.maxInputBytes,
          boxed.maxIndexBytes,
          boxed.maxNodes,
          boxed.maxTextBytes,
          boxed.maxDepth,
          NativePointer.of(result)
        });
  }

  @Override
  int decodeAdopted(long data, long length, long capacity, JsonLimits limits, long result) {
    assert decoded();
    Limits boxed = boxed(limits);
    return decodeAdopted.executeInt(
        new Object[] {
          NativePointer.of(data),
          length,
          capacity,
          boxed.maxInputBytes,
          boxed.maxIndexBytes,
          boxed.maxNodes,
          boxed.maxTextBytes,
          boxed.maxDepth,
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
