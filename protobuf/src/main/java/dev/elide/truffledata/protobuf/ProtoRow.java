package dev.elide.truffledata.protobuf;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.*;
import java.util.*;
import org.jspecify.annotations.Nullable;

/** Immutable native message: readable defaults, separate protobuf presence, no retained Message. */
@ExportLibrary(DataLibrary.class)
public final class ProtoRow extends AbstractRow {
  private static final byte[] NO_UNKNOWN_FIELDS = new byte[0];
  private final ProtoPlan plan;
  final Object storage;
  private final boolean @Nullable [] present;

  private record Scalars(long first, long second) {}

  final byte[] unknown;

  ProtoRow(ProtoPlan plan, Object[] values, boolean[] present, byte[] unknown) {
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
      this.present = present.clone();
    }
    this.unknown = unknown.length == 0 ? NO_UNKNOWN_FIELDS : unknown.clone();
  }

  /** Only the bounded scalar decoder calls this constructor after validating wire values. */
  ProtoRow(ProtoPlan plan, long first, long second) {
    super(plan.schema());
    this.plan = plan;
    storage = new Scalars(first, second);
    present = null;
    unknown = NO_UNKNOWN_FIELDS;
  }

  private long scalar(int index) {
    Objects.checkIndex(index, plan.size());
    Scalars scalars = (Scalars) storage;
    return index == 0 ? scalars.first() : scalars.second();
  }

  public ProtoPlan plan() {
    return plan;
  }

  /** Explicit presence, or non-default/non-empty state for implicit and repeated fields. */
  public boolean has(int ordinal) {
    return present == null ? scalar(ordinal) != 0 : present[ordinal];
  }

  @ExportMessage
  Schema getSchema() {
    return descriptor;
  }

  @ExportMessage
  Object read(int index, @CachedLibrary("this.storage") DataLibrary data) {
    return present == null ? scalar(index) : data.read(storage, index);
  }

  @ExportMessage
  long readLong(int index, @CachedLibrary("this.storage") DataLibrary data)
      throws UnexpectedResultException {
    return present == null ? scalar(index) : data.readLong(storage, index);
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
      return f.repeated() ? new DataArray(new Object[0]) : f.defaultValue();
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

    @TruffleBoundary
    public ProtoRow build() {
      if (plan.definition().mapEntry()
          && plan.field(1).kind().message()
          && values[1] == Missing.INSTANCE) {
        if (plan.model.maxDepth < 2)
          throw new ContractViolation("Map value nesting budget exceeded");
        values[1] = plan.nested(1).newBuilder().build();
        present[1] = true;
      }
      check(new Budget(plan.model.limits), 1);
      return new ProtoRow(plan, values, present, unknown);
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
        var data = DataLibrary.factory().getUncached();
        for (int i = 0; i < row.plan.size(); i++) value(data.read(row, i), depth + 1, maxDepth);
      }
    }
  }
}
