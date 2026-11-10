package dev.elide.truffledata.json;

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
 * <p>The route is host code, compiled ahead of time and called below Truffle boundaries, so it uses
 * each cell's static {@code invoke} entry (Myna 0.16.0): native carriers and raw addresses, with no
 * cell instance, argument array, box, {@code NativePointer} or {@code Borrow}. That entry coerces
 * nothing and allocates nothing, so a decode and its release allocate no more than the baseline's
 * and release is allocation-free, as on the baseline. The cells' {@code execute} methods, which
 * coerce guest values and then call the same {@code invoke}, are for guest code and are not used
 * here. The caller keeps each address valid for the call: {@link JsonSvm} keeps the input pinned
 * (or adopted) and the result allocated until the call returns. Nothing the cells' coercions would
 * have refused can reach the call: {@link JsonLimits} admits only positive limits and a depth within
 * 1..128, and {@link JsonSvm} rejects empty input and a null adopted region before the call.
 */
@NullMarked
final class CellsSvm extends JsonCellsRoute.Svm {
  // Images built with -ea only (//json/cells/tests:native): the native calls made through the
  // cells, so the acceptance can prove that JsonSvm takes this route and releases every result
  // exactly once. Without -ea the assert statements below, and these fields with them, fold away.
  private static long decodes;
  private static long releases;

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

  @Override
  int decode(long data, long length, JsonLimits limits, long result) {
    assert decoded();
    return JsonCellsTruffle.decode_Cell.invoke(
        data,
        length,
        limits.maxInputBytes(),
        limits.maxIndexBytes(),
        limits.maxNodes(),
        limits.maxTextBytes(),
        limits.maxDepth(),
        result);
  }

  @Override
  int decodeAdopted(long data, long length, long capacity, JsonLimits limits, long result) {
    assert decoded();
    return JsonCellsTruffle.decode_adopted_Cell.invoke(
        data,
        length,
        capacity,
        limits.maxInputBytes(),
        limits.maxIndexBytes(),
        limits.maxNodes(),
        limits.maxTextBytes(),
        limits.maxDepth(),
        result);
  }

  @Override
  void release(long result) {
    assert released();
    // Release runs while a message closes and while a failed decode unwinds (after an
    // OutOfMemoryError too): invoke allocates nothing, so it cannot fail for want of heap.
    JsonCellsTruffle.release_Cell.invoke(result);
  }
}
