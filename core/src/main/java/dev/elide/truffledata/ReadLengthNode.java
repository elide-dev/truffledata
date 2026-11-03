package dev.elide.truffledata;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

/**
 * Exact lengths fold to constants after the receiver has enforced its contract (including live
 * views).
 */
@GenerateInline(false)
@GenerateUncached
@Introspectable
public abstract class ReadLengthNode extends Node {
  public abstract int execute(Object row, int index);

  public static ReadLengthNode create() {
    return ReadLengthNodeGen.create();
  }

  @Specialization(
      guards = {"data.getSchema(row) == schema", "index == cachedIndex"},
      limit = "3")
  static int cached(
      Object row,
      int index,
      @CachedLibrary("row") DataLibrary data,
      @Cached("data.getSchema(row)") Schema schema,
      @Cached("index") int cachedIndex,
      @Cached.Shared("arrays") @CachedLibrary(limit = "3") InteropLibrary arrays) {
    CompilerAsserts.partialEvaluationConstant(schema);
    CompilerAsserts.partialEvaluationConstant(cachedIndex);
    return length(data.read(row, cachedIndex), schema.field(cachedIndex), arrays);
  }

  @Specialization(replaces = "cached")
  static int generic(
      Object row,
      int index,
      @CachedLibrary(limit = "3") DataLibrary data,
      @Cached.Shared("arrays") @CachedLibrary(limit = "3") InteropLibrary arrays) {
    return length(data.read(row, index), data.getSchema(row).field(index), arrays);
  }

  private static int length(Object value, Field field, InteropLibrary arrays) {
    var bounds = field.constraints().length();
    if (value instanceof DataArray array)
      return bounds != null && bounds.exact() ? bounds.min() : array.size();
    if (value instanceof String text) {
      if (bounds != null && bounds.exact()) return bounds.min();
      return Values.stringLength(
          text, bounds == null ? Constraints.LengthUnit.UTF16 : bounds.unit());
    }
    if (arrays.hasArrayElements(value)) {
      try {
        // Read even exact lengths: scoped arrays must enforce their lifetime first.
        int size = Math.toIntExact(arrays.getArraySize(value));
        return bounds != null && bounds.exact() ? bounds.min() : size;
      } catch (UnsupportedMessageException e) {
        throw new ContractViolation("Array length unavailable");
      }
    }
    throw new ContractViolation("Expected a present non-null string or array");
  }
}
