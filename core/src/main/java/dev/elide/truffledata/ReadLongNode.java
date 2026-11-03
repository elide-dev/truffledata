package dev.elide.truffledata;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.*;

/** Bounded schema/ordinal PIC. The generic path preserves all null/type checks. */
// This public accessor owns an independent bounded PIC and is adopted as a child by callers.
@GenerateInline(false)
@Introspectable
@GenerateUncached
public abstract class ReadLongNode extends Node {
  public abstract long execute(Object row, int index);

  public static ReadLongNode create() {
    return ReadLongNodeGen.create();
  }

  @Specialization(
      guards = {
        "data.getSchema(row) == schema",
        "index == cachedIndex",
        "schema.requiredLong(cachedIndex)"
      },
      limit = "3")
  static long required(
      Object row,
      int index,
      @CachedLibrary("row") DataLibrary data,
      @Cached("data.getSchema(row)") Schema schema,
      @Cached("index") int cachedIndex) {
    com.oracle.truffle.api.CompilerAsserts.partialEvaluationConstant(schema);
    com.oracle.truffle.api.CompilerAsserts.partialEvaluationConstant(cachedIndex);
    return checked(data, row, cachedIndex);
  }

  @Specialization(replaces = "required")
  static long generic(Object row, int index, @CachedLibrary(limit = "3") DataLibrary data) {
    return checked(data, row, index);
  }

  private static long checked(DataLibrary data, Object row, int index) {
    try {
      return data.readLong(row, index);
    } catch (UnexpectedResultException e) {
      throw new ContractViolation("Expected a present, non-null INT64");
    }
  }
}
