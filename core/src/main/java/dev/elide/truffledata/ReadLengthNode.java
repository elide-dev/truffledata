package dev.elide.truffledata;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.*;
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
      @Cached("index") int cachedIndex) {
    CompilerAsserts.partialEvaluationConstant(schema);
    CompilerAsserts.partialEvaluationConstant(cachedIndex);
    return length(data.read(row, cachedIndex), schema.field(cachedIndex));
  }

  @Specialization(replaces = "cached")
  static int generic(Object row, int index, @CachedLibrary(limit = "3") DataLibrary data) {
    return length(data.read(row, index), data.getSchema(row).field(index));
  }

  private static int length(Object value, Field field) {
    var bounds = field.constraints().length();
    if (!(value instanceof String) && !(value instanceof DataArray))
      throw new ContractViolation("Expected a present non-null string or array");
    if (bounds != null && bounds.exact()) return bounds.min();
    if (value instanceof DataArray array) return array.size();
    return Values.stringLength(
        (String) value, bounds == null ? Constraints.LengthUnit.UTF16 : bounds.unit());
  }
}
