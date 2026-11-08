package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
import dev.elide.truffledata.*;
import dev.elide.truffledata.protobuf.*;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Schema-stable native typed view. The enclosing message owns every borrowed payload. */
@ExportLibrary(DataLibrary.class)
public abstract sealed class TypedRow extends AbstractRow implements ProtoAccess
    permits ScalarRow, NativeRow, TypedRow.ArrayRow {
  final ViewOwner owner;
  private final ProtoPlan plan;

  TypedRow(ViewOwner owner, ProtoPlan plan) {
    super(plan.schema());
    this.owner = owner;
    this.plan = plan;
  }

  /** Eager storage is not carried by native-index or compact scalar views. */
  static final class ArrayRow extends TypedRow {
    @CompilationFinal(dimensions = 1)
    private final Object[] values;

    @CompilationFinal(dimensions = 1)
    private final boolean[] present;

    private final long presenceBits;

    ArrayRow(
        ViewOwner owner, ProtoPlan plan, Object[] values, long presenceBits, boolean[] present) {
      super(owner, plan);
      this.values = values;
      this.present = present;
      this.presenceBits = presenceBits;
    }

    /** Bound per plan: an owner check and one element load; strings resolve once per row. */
    @Override
    public Accessor accessor(int ordinal) {
      ProtoPlan rowPlan = plan();
      return descriptor.accessor(ArrayRow.class, ordinal, i -> new Eager(rowPlan, i));
    }
  }

  private static final class Eager extends Accessor {
    private final ProtoPlan plan;

    Eager(ProtoPlan plan, int ordinal) {
      super(plan.schema(), ordinal);
      this.plan = plan;
    }

    @Override
    public boolean accepts(Object row) {
      return row instanceof ArrayRow r && r.plan() == plan;
    }

    private ArrayRow open(Object row) {
      if (!(row instanceof ArrayRow r && r.plan() == plan)) throw mismatch();
      r.owner.check();
      return r;
    }

    private Object resolved(ArrayRow row) {
      Object value = row.values[ordinal()];
      return value instanceof Utf8 text ? text.value(row.owner) : value;
    }

    @Override
    public Object read(Object row) {
      return resolved(open(row));
    }

    @Override
    public long readLong(Object row) throws UnexpectedResultException {
      var r = open(row);
      Object value = r.values[ordinal()];
      if (value instanceof Long number) return number;
      throw new UnexpectedResultException(resolved(r));
    }

    /** Scoped arrays and bytes report their size directly; strings resolve once per row. */
    @Override
    public int length(Object row) {
      Object value = resolved(open(row));
      if (value instanceof TypedArray array) return arrayLength(array.size());
      if (value instanceof BorrowedBytes bytes) return arrayLength(bytes.size());
      return lengthOf(value);
    }

    @Override
    public double readDouble(Object row) throws UnexpectedResultException {
      Object value = resolved(open(row));
      if (value instanceof Double number) return number;
      throw new UnexpectedResultException(value);
    }

    @Override
    public boolean readBoolean(Object row) throws UnexpectedResultException {
      Object value = resolved(open(row));
      if (value instanceof Boolean flag) return flag;
      throw new UnexpectedResultException(value);
    }
  }

  @Idempotent
  final boolean nativeIndex() {
    return this instanceof NativeRow;
  }

  @Override
  protected final boolean scoped() {
    return true;
  }

  @Override
  public final long getLong(int ordinal) {
    owner.check();
    if (this instanceof ScalarRow scalar) return scalar.scalar(ordinal);
    if (this instanceof NativeRow nativeRow) {
      if (nativeRow.integer(ordinal)) return nativeRow.integerValue(ordinal);
      throw new ContractViolation("Expected INT64");
    }
    Object value = ((ArrayRow) this).values[ordinal];
    if (value instanceof Long number) return number;
    throw new ContractViolation("Expected INT64");
  }

  @Idempotent
  public final ProtoPlan plan() {
    return plan;
  }

  public final boolean has(int ordinal) {
    owner.check();
    Objects.checkIndex(ordinal, descriptor.size());
    return present(ordinal);
  }

  @Override
  public final Object get(int ordinal) {
    owner.check();
    Objects.checkIndex(ordinal, descriptor.size());
    return this instanceof ScalarRow scalar ? scalar.scalar(ordinal) : resolved(ordinal);
  }

  @Override
  public final int getSize(int ordinal) {
    owner.check();
    Objects.checkIndex(ordinal, descriptor.size());
    if (this instanceof NativeRow nativeRow && plan.field(ordinal).repeated())
      return nativeRow.repeatedSize(ordinal);
    Object value = this instanceof ScalarRow scalar ? scalar.scalar(ordinal) : resolved(ordinal);
    if (value instanceof TypedArray array) return array.size();
    if (value instanceof NativeRow.NativeArray array) return array.size();
    if (value instanceof BorrowedBytes bytes) return bytes.size();
    if (value instanceof OwnedArray array) return array.size();
    throw new ContractViolation("Expected array");
  }

  private boolean present(int ordinal) {
    if (this instanceof NativeRow nativeRow) return nativeRow.present(ordinal);
    if (this instanceof ScalarRow scalar) return scalar.present(ordinal);
    var row = (ArrayRow) this;
    return row.present.length == 0
        ? (row.presenceBits & (1L << ordinal)) != 0
        : row.present[ordinal];
  }

  @ExportMessage
  Schema getSchema() {
    return descriptor;
  }

  @ExportMessage
  static Object read(
      TypedRow row,
      int index,
      @Cached.Shared("owner") @Cached("create()") ViewOwner.CheckOpenNode check,
      @Bind Node node,
      @Cached.Shared("indexType") @Cached InlinedExactClassProfile indexType) {
    check.execute(row.owner);
    return row instanceof NativeRow nativeRow
        ? nativeRow.value(index, indexType.profile(node, nativeRow.indexBuffer()))
        : row.resolved(index);
  }

  @ExportMessage
  static class ReadLong {
    @Specialization(
        guards = {
          "row.nativeIndex()",
          "row.plan() == cachedPlan",
          "index == cachedIndex",
          "integer"
        },
        limit = "3")
    static long nativeInteger(
        TypedRow row,
        int index,
        @Cached("row.plan()") ProtoPlan cachedPlan,
        @Cached("index") int cachedIndex,
        @Cached("cachedPlan.field(cachedIndex)") ProtoPlan.FieldPlan field,
        @Cached("cachedPlan.schema().requiredLong(cachedIndex)") boolean integer,
        @Cached.Shared("owner") @Cached("create()") ViewOwner.CheckOpenNode check,
        @Bind Node node,
        @Cached.Shared("indexType") @Cached InlinedExactClassProfile indexType) {
      check.execute(row.owner);
      var nativeRow = (NativeRow) row;
      return nativeRow.integerValue(
          cachedIndex, field, indexType.profile(node, nativeRow.indexBuffer()));
    }

    @Specialization(replaces = "nativeInteger")
    static long generic(
        TypedRow row,
        int index,
        @Cached.Shared("owner") @Cached("create()") ViewOwner.CheckOpenNode check,
        @Bind Node node,
        @Cached.Shared("indexType") @Cached InlinedExactClassProfile indexType)
        throws UnexpectedResultException {
      check.execute(row.owner);
      if (row instanceof NativeRow nativeRow) {
        var buffer = indexType.profile(node, nativeRow.indexBuffer());
        if (nativeRow.integer(index)) return nativeRow.integerValue(index, buffer);
        throw new UnexpectedResultException(nativeRow.value(index, buffer));
      }
      Object value = ((ArrayRow) row).values[index];
      if (value instanceof Long number) return number;
      throw new UnexpectedResultException(row.resolved(index));
    }
  }

  @ExportMessage
  static double readDouble(
      TypedRow row,
      int index,
      @Cached.Shared("owner") @Cached("create()") ViewOwner.CheckOpenNode check,
      @Bind Node node,
      @Cached.Shared("indexType") @Cached InlinedExactClassProfile indexType)
      throws UnexpectedResultException {
    check.execute(row.owner);
    Object value =
        row instanceof NativeRow nativeRow
            ? nativeRow.value(index, indexType.profile(node, nativeRow.indexBuffer()))
            : row.resolved(index);
    if (value instanceof Double number) return number;
    throw new UnexpectedResultException(value);
  }

  @ExportMessage
  static class ReadBoolean {
    @Specialization(
        guards = {"row.nativeIndex()", "row.plan() == cachedPlan", "index == cachedIndex", "bool"},
        limit = "3")
    static boolean nativeBoolean(
        TypedRow row,
        int index,
        @Cached("row.plan()") ProtoPlan cachedPlan,
        @Cached("index") int cachedIndex,
        @Cached("cachedPlan.field(cachedIndex)") ProtoPlan.FieldPlan field,
        @Cached("cachedPlan.schema().requiredBoolean(cachedIndex)") boolean bool,
        @Cached.Shared("owner") @Cached("create()") ViewOwner.CheckOpenNode check,
        @Bind Node node,
        @Cached.Shared("indexType") @Cached InlinedExactClassProfile indexType) {
      check.execute(row.owner);
      var nativeRow = (NativeRow) row;
      return nativeRow.booleanValue(
          cachedIndex, field, indexType.profile(node, nativeRow.indexBuffer()));
    }

    @Specialization(replaces = "nativeBoolean")
    static boolean generic(
        TypedRow row,
        int index,
        @Cached.Shared("owner") @Cached("create()") ViewOwner.CheckOpenNode check,
        @Bind Node node,
        @Cached.Shared("indexType") @Cached InlinedExactClassProfile indexType)
        throws UnexpectedResultException {
      check.execute(row.owner);
      Object value =
          row instanceof NativeRow nativeRow
              ? nativeRow.value(index, indexType.profile(node, nativeRow.indexBuffer()))
              : row.resolved(index);
      if (value instanceof Boolean flag) return flag;
      throw new UnexpectedResultException(value);
    }
  }

  private Object resolved(int index) {
    if (this instanceof NativeRow nativeRow) return nativeRow.value(index);
    Object value = ((ArrayRow) this).values[index];
    return value instanceof Utf8 text ? text.value(owner) : value;
  }

  /** Internal placeholder: never exported, and only backed by sealed, native-validated UTF-8. */
  static final class Utf8 {
    final boolean[] readProfile;
    final int ordinal;
    final int offset;
    final int length;
    private @Nullable String cached;

    Utf8(ViewOwner owner, int offset, int length, boolean[] readProfile, int ordinal) {
      owner.lease.check();
      Objects.checkFromIndexSize(offset, length, owner.lease.bytes.capacity());
      Objects.checkIndex(ordinal, readProfile.length);
      this.readProfile = readProfile;
      this.ordinal = ordinal;
      this.offset = offset;
      this.length = length;
    }

    String value(ViewOwner owner) {
      String result = cached;
      return result == null ? decode(owner) : result;
    }

    @TruffleBoundary
    private String decode(ViewOwner owner) {
      String result = owner.utf8(offset, length);
      readProfile[ordinal] = true;
      cached = result;
      return result;
    }
  }

  @TruffleBoundary
  ProtoRow snapshot() {
    owner.check();
    var builder = plan.newBuilder();
    for (int i = 0; i < descriptor.size(); i++) if (present(i)) builder.set(i, detach(get(i)));
    return builder.build();
  }

  static Object detach(Object value) {
    if (value instanceof BorrowedArray array) return array.snapshot();
    if (value instanceof TypedRow row) return row.snapshot();
    return value;
  }
}
