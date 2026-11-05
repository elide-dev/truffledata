package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
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
public sealed class TypedRow extends AbstractRow implements ProtoAccess
    permits ScalarRow, NativeRow {
  final ViewOwner owner;
  private final ProtoPlan plan;

  @CompilationFinal(dimensions = 1)
  private final Object[] values;

  @CompilationFinal(dimensions = 1)
  private final boolean[] present;

  private final long presenceBits;

  TypedRow(ViewOwner owner, ProtoPlan plan, Object[] values, long presenceBits, boolean[] present) {
    super(plan.schema());
    this.owner = owner;
    this.plan = plan;
    this.values = values;
    this.present = present;
    this.presenceBits = presenceBits;
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
    Object value = values[ordinal];
    if (value instanceof Long number) return number;
    throw new ContractViolation("Expected INT64");
  }

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
    Object value = get(ordinal);
    if (value instanceof BorrowedArray array) return array.size();
    if (value instanceof DataArray array) return array.size();
    throw new ContractViolation("Expected array");
  }

  private boolean present(int ordinal) {
    if (this instanceof NativeRow nativeRow) return nativeRow.present(ordinal);
    return present.length == 0 ? (presenceBits & (1L << ordinal)) != 0 : present[ordinal];
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
  static long readLong(
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
    Object value = row.values[index];
    if (value instanceof Long number) return number;
    throw new UnexpectedResultException(row.resolved(index));
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
  static boolean readBoolean(
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

  private Object resolved(int index) {
    if (this instanceof NativeRow nativeRow) return nativeRow.value(index);
    Object value = values[index];
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
