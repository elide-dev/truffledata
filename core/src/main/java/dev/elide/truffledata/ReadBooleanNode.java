package dev.elide.truffledata;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.*;

/** Bounded schema/ordinal PIC. The generic path preserves all null/type checks. */
// This public accessor owns an independent bounded PIC and is adopted as a child by callers.
@GenerateInline(false)
@Introspectable
@GenerateUncached
@ImportStatic(Accessor.class)
public abstract class ReadBooleanNode extends Node {
  public abstract boolean execute(Object row, int index);

  public static ReadBooleanNode create() {
    return ReadBooleanNodeGen.create();
  }

  /**
   * A layout-bound accessor: one identity guard and the representation's own loads. Rows that only
   * offer the library-backed default accessor fall through to the library specializations.
   */
  @Specialization(
      guards = {"index == cachedIndex", "bound(accessor, row)"},
      limit = "3")
  static boolean accessed(
      Object row,
      int index,
      @Cached("index") int cachedIndex,
      @Cached("bind(row, cachedIndex)") Accessor accessor) {
    com.oracle.truffle.api.CompilerAsserts.partialEvaluationConstant(accessor);
    try {
      boolean value = accessor.readBoolean(row);
      if (Accessor.STRICT) Accessor.audit(accessor, value);
      return value;
    } catch (UnexpectedResultException e) {
      throw new ContractViolation("Expected a present, non-null BOOLEAN");
    }
  }

  @Specialization(
      guards = {
        "data.getSchema(row) == schema",
        "index == cachedIndex",
        "schema.requiredBoolean(cachedIndex)"
      },
      limit = "3")
  static boolean required(
      Object row,
      int index,
      @CachedLibrary("row") DataLibrary data,
      @Cached("data.getSchema(row)") Schema schema,
      @Cached("index") int cachedIndex) {
    com.oracle.truffle.api.CompilerAsserts.partialEvaluationConstant(schema);
    com.oracle.truffle.api.CompilerAsserts.partialEvaluationConstant(cachedIndex);
    return checked(data, row, cachedIndex);
  }

  @Specialization(replaces = {"accessed", "required"})
  static boolean generic(Object row, int index, @CachedLibrary(limit = "3") DataLibrary data) {
    return checked(data, row, index);
  }

  private static boolean checked(DataLibrary data, Object row, int index) {
    try {
      return data.readBoolean(row, index);
    } catch (UnexpectedResultException e) {
      throw new ContractViolation("Expected a present, non-null BOOLEAN");
    }
  }

  /** Bounded reader whose DSL caches can be stored in a caller node. */
  @GenerateInline
  @GenerateCached(false)
  @GenerateUncached
  @ImportStatic(Accessor.class)
  public abstract static class InlineReader extends Node {
    public abstract boolean execute(Node node, Object row, int index);

    @Specialization(
        guards = {"index == cachedIndex", "bound(accessor, row)"},
        limit = "3")
    static boolean accessedInline(
        Object row,
        int index,
        @Cached("index") int cachedIndex,
        @Cached("bind(row, cachedIndex)") Accessor accessor) {
      return ReadBooleanNode.accessed(row, index, cachedIndex, accessor);
    }

    @Specialization(
        guards = {
          "data.getSchema(row) == schema",
          "index == cachedIndex",
          "schema.requiredBoolean(cachedIndex)"
        },
        limit = "3")
    static boolean requiredInline(
        Object row,
        int index,
        @CachedLibrary("row") DataLibrary data,
        @Cached("data.getSchema(row)") Schema schema,
        @Cached("index") int cachedIndex) {
      return ReadBooleanNode.required(row, index, data, schema, cachedIndex);
    }

    @Specialization(replaces = {"accessedInline", "requiredInline"})
    static boolean genericInline(
        Object row, int index, @CachedLibrary(limit = "3") DataLibrary data) {
      return ReadBooleanNode.generic(row, index, data);
    }
  }
}
