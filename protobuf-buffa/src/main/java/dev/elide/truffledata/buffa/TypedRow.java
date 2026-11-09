package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.*;
import dev.elide.truffledata.protobuf.*;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Schema-stable native typed view. The enclosing message owns every borrowed payload. */
@ExportLibrary(DataLibrary.class)
public abstract sealed class TypedRow extends AbstractRow implements ProtoAccess
    permits TypedRow.ArrayRow {
  final ViewOwner owner;
  private final ProtoPlan plan;

  TypedRow(ViewOwner owner, ProtoPlan plan) {
    super(plan.schema());
    this.owner = owner;
    this.plan = plan;
  }

  /** Scoped text/ProtoJSON values, with lazily decoded validated string payloads. */
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

  /**
   * Layout (plan): ArrayRow is final and each plan owns its schema, so the layout is exactly
   * (class, schema identity). Every read, bound or checked, still checks the owner's lifetime.
   */
  private static final class Eager extends Accessor {
    private final ProtoPlan plan;

    Eager(ProtoPlan plan, int ordinal) {
      super(plan.schema(), ordinal, ArrayRow.class);
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

    /** A row the read node's guard already admitted: only the layout check is skipped. */
    private ArrayRow trusted(Object row) {
      assertAccepted(row);
      var r = (ArrayRow) row;
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
      return longOf(open(row));
    }

    @Override
    protected long readLongBound(Object row) throws UnexpectedResultException {
      return longOf(trusted(row));
    }

    private long longOf(ArrayRow r) throws UnexpectedResultException {
      Object value = r.values[ordinal()];
      if (value instanceof Long number) return number;
      throw new UnexpectedResultException(resolved(r));
    }

    /** Scoped arrays and bytes report their size directly; strings resolve once per row. */
    @Override
    public int length(Object row) {
      return lengthIn(open(row));
    }

    @Override
    protected int lengthBound(Object row) {
      return lengthIn(trusted(row));
    }

    private int lengthIn(ArrayRow r) {
      Object value = resolved(r);
      if (value instanceof TypedArray array) return arrayLength(array.size());
      if (value instanceof BorrowedBytes bytes) return arrayLength(bytes.size());
      return lengthOf(value);
    }

    @Override
    public double readDouble(Object row) throws UnexpectedResultException {
      return doubleOf(open(row));
    }

    @Override
    protected double readDoubleBound(Object row) throws UnexpectedResultException {
      return doubleOf(trusted(row));
    }

    private double doubleOf(ArrayRow r) throws UnexpectedResultException {
      Object value = resolved(r);
      if (value instanceof Double number) return number;
      throw new UnexpectedResultException(value);
    }

    @Override
    public boolean readBoolean(Object row) throws UnexpectedResultException {
      return booleanOf(open(row));
    }

    @Override
    protected boolean readBooleanBound(Object row) throws UnexpectedResultException {
      return booleanOf(trusted(row));
    }

    private boolean booleanOf(ArrayRow r) throws UnexpectedResultException {
      Object value = resolved(r);
      if (value instanceof Boolean flag) return flag;
      throw new UnexpectedResultException(value);
    }
  }

  @Override
  protected final boolean scoped() {
    return true;
  }

  @Override
  public final long getLong(int ordinal) {
    owner.check();
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
    return resolved(ordinal);
  }

  @Override
  public final int getSize(int ordinal) {
    owner.check();
    Objects.checkIndex(ordinal, descriptor.size());
    Object value = resolved(ordinal);
    if (value instanceof TypedArray array) return array.size();
    if (value instanceof BorrowedBytes bytes) return bytes.size();
    if (value instanceof OwnedArray array) return array.size();
    throw new ContractViolation("Expected array");
  }

  private boolean present(int ordinal) {
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
      @Cached.Shared("owner") @Cached("create()") ViewOwner.CheckOpenNode check) {
    check.execute(row.owner);
    return row.resolved(index);
  }

  @ExportMessage
  static long readLong(
      TypedRow row,
      int index,
      @Cached.Shared("owner") @Cached("create()") ViewOwner.CheckOpenNode check)
      throws UnexpectedResultException {
    check.execute(row.owner);
    Object value = ((ArrayRow) row).values[index];
    if (value instanceof Long number) return number;
    throw new UnexpectedResultException(row.resolved(index));
  }

  @ExportMessage
  static double readDouble(
      TypedRow row,
      int index,
      @Cached.Shared("owner") @Cached("create()") ViewOwner.CheckOpenNode check)
      throws UnexpectedResultException {
    check.execute(row.owner);
    Object value = row.resolved(index);
    if (value instanceof Double number) return number;
    throw new UnexpectedResultException(value);
  }

  @ExportMessage
  static boolean readBoolean(
      TypedRow row,
      int index,
      @Cached.Shared("owner") @Cached("create()") ViewOwner.CheckOpenNode check)
      throws UnexpectedResultException {
    check.execute(row.owner);
    Object value = row.resolved(index);
    if (value instanceof Boolean flag) return flag;
    throw new UnexpectedResultException(value);
  }

  private Object resolved(int index) {
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
