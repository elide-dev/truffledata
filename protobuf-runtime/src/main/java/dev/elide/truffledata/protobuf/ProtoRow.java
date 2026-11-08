package dev.elide.truffledata.protobuf;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.*;
import java.util.*;
import org.jspecify.annotations.Nullable;

/** Immutable native message: readable defaults, separate protobuf presence, no retained Message. */
@ExportLibrary(DataLibrary.class)
public final class ProtoRow extends AbstractRow implements ProtoAccess {
  private static final byte[] NO_UNKNOWN_FIELDS = new byte[0];
  private static final DataArray EMPTY_REPEATED = new DataArray(new Object[0]);
  private final ProtoPlan plan;
  final Object storage;
  // Compact integer rows use this word for their first value; other rows use presence bits.
  private final long presence;
  private final Object wireState;

  /** Only schemas wider than 64 fields need a separate presence object. */
  private static final class WidePresence {
    final byte[] unknown;
    final long[] spill;

    WidePresence(byte[] unknown, long[] spill) {
      this.unknown = unknown;
      this.spill = spill;
    }
  }

  private ProtoRow(
      ProtoPlan plan,
      Object[] values,
      long presence,
      long @Nullable [] spill,
      byte[] unknown,
      boolean owned) {
    super(plan.schema());
    this.plan = plan;
    if (plan.compactScalars) {
      long first = (Long) Values.validate(plan.schema().field(0), values[0]);
      storage =
          values.length == 2 ? Values.validate(plan.schema().field(1), values[1]) : Long.valueOf(0);
      this.presence = first;
    } else {
      storage = plan.storage(values);
      this.presence = presence;
    }
    byte[] retained = unknown.length == 0 ? NO_UNKNOWN_FIELDS : owned ? unknown : unknown.clone();
    wireState =
        spill == null ? retained : new WidePresence(retained, owned ? spill : spill.clone());
  }

  /** Only the bounded scalar decoder calls this constructor after validating wire values. */
  ProtoRow(ProtoPlan plan, long first, long second) {
    super(plan.schema());
    this.plan = plan;
    storage = second;
    presence = first;
    wireState = NO_UNKNOWN_FIELDS;
  }

  /**
   * Internal decoder-only transfer, after State.finish checks required fields and aggregate
   * retained budgets. The caller must never reuse or expose these arrays.
   */
  static ProtoRow decoded(
      ProtoPlan plan,
      Object[] values,
      long presence,
      long @Nullable [] spill,
      byte @Nullable [] unknown) {
    return new ProtoRow(
        plan, values, presence, spill, unknown == null ? NO_UNKNOWN_FIELDS : unknown, true);
  }

  static boolean present(long presence, long @Nullable [] spill, int ordinal) {
    long word = ordinal < 64 ? presence : Objects.requireNonNull(spill)[(ordinal >>> 6) - 1];
    return (word & (1L << ordinal)) != 0;
  }

  static long withPresence(long presence, long @Nullable [] spill, int ordinal, boolean set) {
    long mask = 1L << ordinal;
    if (ordinal < 64) return set ? presence | mask : presence & ~mask;
    long[] words = Objects.requireNonNull(spill);
    int index = (ordinal >>> 6) - 1;
    words[index] = set ? words[index] | mask : words[index] & ~mask;
    return presence;
  }

  static long selectOneof(
      ProtoPlan plan, Object[] values, long presence, long @Nullable [] spill, int ordinal) {
    int oneof = plan.field(ordinal).oneof();
    if (oneof >= 0) {
      for (int i = 0; i < values.length; i++)
        if (i != ordinal && plan.field(i).oneof() == oneof) {
          values[i] = Builder.defaultValue(plan.field(i));
          presence = withPresence(presence, spill, i, false);
        }
    }
    return presence;
  }

  /**
   * A defensive copy of the preserved unknown wire fields of this record, in wire order. Includes
   * unknown numbers, mismatched wire types, rejected closed-enum values and map entries, and
   * unknown groups. Declared child messages expose their own unknown bytes. Bytes are not
   * canonicalized: equality policies may parse these fields independently.
   */
  public byte[] unknownFieldBytes() {
    return unknown().clone();
  }

  byte[] unknown() {
    return wireState instanceof WidePresence wide ? wide.unknown : (byte[]) wireState;
  }

  long scalar(int index) {
    Objects.checkIndex(index, descriptor.size());
    return scalarUnchecked(index);
  }

  /** Internal primitive access after the caller proves compact shape and ordinal bounds. */
  long scalarUnchecked(int index) {
    assert plan.compactScalars && index >= 0 && index < plan.size();
    return index == 0 ? presence : (Long) storage;
  }

  private Object boxedScalar(int index) {
    Objects.checkIndex(index, descriptor.size());
    // The second scalar already owns an immutable box; generic reads can reuse it.
    return index == 0 ? Long.valueOf(presence) : storage;
  }

  public ProtoPlan plan() {
    return plan;
  }

  @Override
  protected @Nullable SnapshotStorage payloadStorage() {
    return storage instanceof SnapshotStorage snapshot ? snapshot : null;
  }

  @Override
  public Object get(int ordinal) {
    if (compact()) return boxedScalar(ordinal);
    return plan.directValue(storage, ordinal);
  }

  @Override
  public long getLong(int ordinal) {
    if (compact()) return scalar(ordinal);
    return plan.directLong(storage, ordinal);
  }

  /** Explicit presence, or non-default/non-empty state for implicit and repeated fields. */
  public boolean has(int ordinal) {
    if (compact()) return scalar(ordinal) != 0;
    Objects.checkIndex(ordinal, descriptor.size());
    if (ordinal < 64) return (presence & (1L << ordinal)) != 0;
    return present(presence, ((WidePresence) wireState).spill, ordinal);
  }

  @ExportMessage
  Schema getSchema() {
    return descriptor;
  }

  @Idempotent
  boolean compact() {
    return plan.compactScalars;
  }

  /**
   * Bound per plan: compact rows read their inline scalars; stored rows delegate to the constant
   * accessor of their storage layout (a static shape, or the plan schema's batch columns).
   */
  @Override
  public Accessor accessor(int ordinal) {
    Object rowStorage = storage;
    return descriptor.accessor(
        ProtoRow.class,
        ordinal,
        i ->
            new Owned(
                plan, i, plan.compactScalars ? null : ((AbstractRow) rowStorage).accessor(i)));
  }

  private static final class Owned extends Accessor {
    private final ProtoPlan plan;
    private final @Nullable Accessor stored;

    Owned(ProtoPlan plan, int ordinal, @Nullable Accessor stored) {
      super(plan.schema(), ordinal);
      this.plan = plan;
      this.stored = stored;
    }

    @Override
    public boolean accepts(Object row) {
      return row instanceof ProtoRow r && r.plan == plan;
    }

    private ProtoRow row(Object row) {
      if (row instanceof ProtoRow r && r.plan == plan) return r;
      throw mismatch();
    }

    @Override
    public Object read(Object row) {
      var r = row(row);
      return stored == null ? r.boxedScalar(ordinal()) : stored.read(r.storage);
    }

    @Override
    public boolean present(Object row) {
      var r = row(row);
      return stored == null || stored.present(r.storage);
    }

    @Override
    public long readLong(Object row) throws UnexpectedResultException {
      var r = row(row);
      return stored == null ? r.scalarUnchecked(ordinal()) : stored.readLong(r.storage);
    }

    @Override
    public double readDouble(Object row) throws UnexpectedResultException {
      var r = row(row);
      if (stored == null) throw new UnexpectedResultException(r.scalarUnchecked(ordinal()));
      return stored.readDouble(r.storage);
    }

    @Override
    public boolean readBoolean(Object row) throws UnexpectedResultException {
      var r = row(row);
      if (stored == null) throw new UnexpectedResultException(r.scalarUnchecked(ordinal()));
      return stored.readBoolean(r.storage);
    }

    @Override
    public int length(Object row) {
      var r = row(row);
      return stored == null ? lengthOf(r.boxedScalar(ordinal())) : stored.length(r.storage);
    }
  }

  @ExportMessage
  static class Read {
    @Specialization(guards = "row.compact()")
    static Object scalar(ProtoRow row, int index) {
      return row.boxedScalar(index);
    }

    @Specialization(guards = "!row.compact()")
    static Object stored(ProtoRow row, int index, @CachedLibrary("row.storage") DataLibrary data) {
      return data.read(row.storage, index);
    }
  }

  @ExportMessage
  static class ReadLong {
    @Specialization(guards = "row.compact()")
    static long scalar(ProtoRow row, int index) {
      return row.scalar(index);
    }

    @Specialization(guards = "!row.compact()")
    static long stored(ProtoRow row, int index, @CachedLibrary("row.storage") DataLibrary data)
        throws UnexpectedResultException {
      return data.readLong(row.storage, index);
    }
  }

  @ExportMessage
  double readDouble(int index, @CachedLibrary("this.storage") DataLibrary data)
      throws UnexpectedResultException {
    if (compact()) throw new UnexpectedResultException(scalar(index));
    return data.readDouble(storage, index);
  }

  @ExportMessage
  boolean readBoolean(int index, @CachedLibrary("this.storage") DataLibrary data)
      throws UnexpectedResultException {
    if (compact()) throw new UnexpectedResultException(scalar(index));
    return data.readBoolean(storage, index);
  }

  /** Host-side construction. Values use the same normalized representations as DataLibrary. */
  public static final class Builder {
    final ProtoPlan plan;
    final Object[] values;
    long presence;
    final long @Nullable [] spill;
    byte[] unknown = NO_UNKNOWN_FIELDS;

    Builder(ProtoPlan plan) {
      this.plan = plan;
      values = new Object[plan.size()];
      spill = plan.size() > 64 ? new long[(plan.size() - 1) >>> 6] : null;
      for (int i = 0; i < values.length; i++) values[i] = defaultValue(plan.field(i));
    }

    static Object defaultValue(ProtoPlan.FieldPlan f) {
      return f.repeated() ? EMPTY_REPEATED : f.defaultValue();
    }

    @TruffleBoundary
    public Builder set(int ordinal, Object value) {
      var field = plan.field(ordinal);
      value = Values.normalize(value);
      if (value == Missing.INSTANCE) return clear(ordinal);
      Values.validate(plan.schema().field(ordinal), value);
      validateType(field, value);
      select(ordinal);
      values[ordinal] = value;
      presence =
          withPresence(
              presence, spill, ordinal, field.explicitPresence() || nonDefault(field, value));
      return this;
    }

    private void validateType(ProtoPlan.FieldPlan field, Object value) {
      if (field.repeated()) {
        OwnedArray items = (OwnedArray) value;
        if (items.size() > plan.model.limits.maxCells())
          throw new ContractViolation("Repeated budget");
        Set<Object> keys = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
          Object item = items.get(i);
          validateScalar(field, item);
          if (field.map() && !keys.add(mapKey(DataLibrary.factory().getUncached().read(item, 0))))
            throw new ContractViolation("Duplicate map key in construction");
        }
      } else validateScalar(field, value);
    }

    private void validateScalar(ProtoPlan.FieldPlan field, Object value) {
      if (value instanceof String text) ProtoPlan.validateString(text);
      if (field.kind().message()) {
        if (!(value instanceof ProtoAccess row)
            || !(row instanceof ProtoRow
                || row instanceof GeneratedMessage
                || row instanceof SlabRow)
            || row.plan() != plan.model.type(field.typeName()))
          throw new ContractViolation("Nested message plan mismatch");
      } else if (field.kind() == ProtoPlan.Kind.BYTES && !((OwnedArray) value).isBytes()) {
        throw new ContractViolation("Protobuf bytes require compact binary storage");
      } else if (field.enumType() != null && !field.enumType().accepts(((Long) value).intValue())) {
        throw new ContractViolation("Unknown closed-enum value");
      }
    }

    void select(int ordinal) {
      presence = selectOneof(plan, values, presence, spill, ordinal);
    }

    boolean has(int ordinal) {
      return present(presence, spill, ordinal);
    }

    @TruffleBoundary
    public Builder clear(int ordinal) {
      values[ordinal] = defaultValue(plan.field(ordinal));
      presence = withPresence(presence, spill, ordinal, false);
      return this;
    }

    public ProtoRow build() {
      return build(false);
    }

    @TruffleBoundary
    private ProtoRow build(boolean owned) {
      if (plan.definition().mapEntry()
          && plan.field(1).kind().message()
          && values[1] == Missing.INSTANCE) {
        if (plan.model.maxDepth < 2)
          throw new ContractViolation("Map value nesting budget exceeded");
        values[1] = plan.nested(1).newBuilder().build();
        presence = withPresence(presence, spill, 1, true);
      }
      check(new Budget(plan.model.limits), 1);
      return new ProtoRow(plan, values, presence, spill, unknown, owned);
    }

    private void check(Budget budget, int depth) {
      if (depth > plan.model.maxDepth)
        throw new ContractViolation("Protobuf nesting budget exceeded");
      budget.cells(values.length);
      budget.bytes(unknown.length);
      for (int i = 0; i < values.length; i++) {
        if (plan.field(i).required() && !has(i))
          throw new ContractViolation("Missing required field");
        budget.value(values[i], depth, plan.model.maxDepth);
      }
    }
  }

  static Object mapKey(Object value) {
    // ExactNumber intentionally has identity equality; protobuf integer map keys have value
    // equality.
    return value instanceof ExactNumber number ? number.decimal().toBigIntegerExact() : value;
  }

  static boolean nonDefault(ProtoPlan.FieldPlan field, Object value) {
    if (value instanceof OwnedArray array) return array.size() != 0;
    if (value instanceof ExactNumber number && field.defaultValue() instanceof ExactNumber other)
      return number.decimal().compareTo(other.decimal()) != 0;
    return !value.equals(field.defaultValue());
  }

  static final class Budget {
    private final Limits limits;
    private long cells;
    private long bytes;

    Budget(Limits limits) {
      this.limits = limits;
    }

    long cellCount() {
      return cells;
    }

    long byteCount() {
      return bytes;
    }

    void cells(long count) {
      if (count > limits.maxCells() - cells)
        throw new ContractViolation("Protobuf cell budget exceeded");
      cells += count;
    }

    void bytes(long count) {
      if (count > limits.maxStringBytes() - bytes)
        throw new ContractViolation("Protobuf payload budget exceeded");
      bytes += count;
    }

    void value(Object value, int depth, int maxDepth) {
      if (value instanceof String s) bytes(2L * s.length());
      else if (value instanceof OwnedArray array) {
        cells(array.size());
        if (!array.isBytes())
          for (int i = 0; i < array.size(); i++) value(array.get(i), depth, maxDepth);
      } else if (value instanceof GeneratedMessage row) {
        if (depth >= maxDepth) throw new ContractViolation("Protobuf nesting budget exceeded");
        cells(row.plan().size());
        bytes(row.unknown.length);
        for (int i = 0; i < row.plan().size(); i++) value(row.get(i), depth + 1, maxDepth);
      } else if (value instanceof SlabRow row) {
        if (depth >= maxDepth) throw new ContractViolation("Protobuf nesting budget exceeded");
        cells(row.plan().size());
        bytes(row.unknownFieldBytes().length);
        for (int i = 0; i < row.plan().size(); i++) value(row.get(i), depth + 1, maxDepth);
      } else if (value instanceof ProtoRow row) {
        if (depth >= maxDepth) throw new ContractViolation("Protobuf nesting budget exceeded");
        cells(row.plan.size());
        bytes(row.unknown().length);
        // Owned storage is immutable and has a fixed export class. Avoid redispatching through
        // the guest-facing ProtoRow facade for each field in this host-side accounting walk.
        // Compact rows contain only primitive integers, so have no nested or string payload.
        if (!row.compact()) {
          Object storage = row.storage;
          var data = DataLibrary.factory().getUncached(storage);
          for (int i = 0; i < row.plan.size(); i++)
            value(data.read(storage, i), depth + 1, maxDepth);
        }
      }
    }
  }
}
