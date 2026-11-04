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
  private final boolean @Nullable [] present;

  private static final class Scalars {
    final long first;
    final long second;

    Scalars(long first, long second) {
      this.first = first;
      this.second = second;
    }
  }

  final byte[] unknown;

  private ProtoRow(
      ProtoPlan plan, Object[] values, boolean[] present, byte[] unknown, boolean owned) {
    super(plan.schema());
    this.plan = plan;
    if (plan.compactScalars) {
      long first = (Long) Values.validate(plan.schema().field(0), values[0]);
      long second =
          values.length == 2 ? (Long) Values.validate(plan.schema().field(1), values[1]) : 0;
      storage = new Scalars(first, second);
      this.present = null;
    } else {
      storage = plan.storage(values);
      this.present = owned ? present : present.clone();
    }
    this.unknown = unknown.length == 0 ? NO_UNKNOWN_FIELDS : owned ? unknown : unknown.clone();
  }

  /** Only the bounded scalar decoder calls this constructor after validating wire values. */
  ProtoRow(ProtoPlan plan, long first, long second) {
    super(plan.schema());
    this.plan = plan;
    storage = new Scalars(first, second);
    present = null;
    unknown = NO_UNKNOWN_FIELDS;
  }

  long scalar(int index) {
    Objects.checkIndex(index, descriptor.size());
    Scalars scalars = (Scalars) storage;
    return index == 0 ? scalars.first : scalars.second;
  }

  public ProtoPlan plan() {
    return plan;
  }

  @Override
  public Object get(int ordinal) {
    if (compact()) return scalar(ordinal);
    return plan.directValue(storage, ordinal);
  }

  @Override
  public long getLong(int ordinal) {
    if (compact()) return scalar(ordinal);
    return plan.directLong(storage, ordinal);
  }

  /** Explicit presence, or non-default/non-empty state for implicit and repeated fields. */
  public boolean has(int ordinal) {
    return present == null ? scalar(ordinal) != 0 : present[ordinal];
  }

  @ExportMessage
  Schema getSchema() {
    return descriptor;
  }

  @Idempotent
  boolean compact() {
    return present == null;
  }

  @ExportMessage
  static class Read {
    @Specialization(guards = "row.compact()")
    static Object scalar(ProtoRow row, int index) {
      return row.scalar(index);
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
    if (present == null) throw new UnexpectedResultException(scalar(index));
    return data.readDouble(storage, index);
  }

  @ExportMessage
  boolean readBoolean(int index, @CachedLibrary("this.storage") DataLibrary data)
      throws UnexpectedResultException {
    if (present == null) throw new UnexpectedResultException(scalar(index));
    return data.readBoolean(storage, index);
  }

  /** Host-side construction. Values use the same normalized representations as DataLibrary. */
  public static final class Builder {
    final ProtoPlan plan;
    final Object[] values;
    final boolean[] present;
    byte[] unknown = NO_UNKNOWN_FIELDS;

    Builder(ProtoPlan plan) {
      this.plan = plan;
      values = new Object[plan.size()];
      present = new boolean[plan.size()];
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
      present[ordinal] = field.explicitPresence() || nonDefault(field, value);
      return this;
    }

    private void validateType(ProtoPlan.FieldPlan field, Object value) {
      if (field.repeated()) {
        DataArray items = (DataArray) value;
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
        if (!(value instanceof ProtoRow row) || row.plan() != plan.model.type(field.typeName()))
          throw new ContractViolation("Nested message plan mismatch");
      } else if (field.kind() == ProtoPlan.Kind.BYTES && !((DataArray) value).isBytes()) {
        throw new ContractViolation("Protobuf bytes require compact binary storage");
      } else if (field.enumType() != null && !field.enumType().accepts(((Long) value).intValue())) {
        throw new ContractViolation("Unknown closed-enum value");
      }
    }

    void select(int ordinal) {
      int oneof = plan.field(ordinal).oneof();
      if (oneof >= 0) {
        for (int i = 0; i < values.length; i++)
          if (i != ordinal && plan.field(i).oneof() == oneof) clear(i);
      }
    }

    @TruffleBoundary
    public Builder clear(int ordinal) {
      values[ordinal] = defaultValue(plan.field(ordinal));
      present[ordinal] = false;
      return this;
    }

    public ProtoRow build() {
      return build(false);
    }

    /** Internal decoder-only transfer: the caller must never reuse or expose this builder. */
    ProtoRow buildDecoded() {
      return build(true);
    }

    @TruffleBoundary
    private ProtoRow build(boolean owned) {
      if (plan.definition().mapEntry()
          && plan.field(1).kind().message()
          && values[1] == Missing.INSTANCE) {
        if (plan.model.maxDepth < 2)
          throw new ContractViolation("Map value nesting budget exceeded");
        values[1] = plan.nested(1).newBuilder().build();
        present[1] = true;
      }
      check(new Budget(plan.model.limits), 1);
      return new ProtoRow(plan, values, present, unknown, owned);
    }

    private void check(Budget budget, int depth) {
      if (depth > plan.model.maxDepth)
        throw new ContractViolation("Protobuf nesting budget exceeded");
      budget.cells(values.length);
      budget.bytes(unknown.length);
      for (int i = 0; i < values.length; i++) {
        if (plan.field(i).required() && !present[i])
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
    if (value instanceof DataArray array) return array.size() != 0;
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
      else if (value instanceof DataArray array) {
        cells(array.size());
        if (!array.isBytes())
          for (int i = 0; i < array.size(); i++) value(array.get(i), depth, maxDepth);
      } else if (value instanceof ProtoRow row) {
        if (depth >= maxDepth) throw new ContractViolation("Protobuf nesting budget exceeded");
        cells(row.plan.size());
        bytes(row.unknown.length);
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
