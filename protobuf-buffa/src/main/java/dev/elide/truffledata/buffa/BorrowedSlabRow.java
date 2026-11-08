package dev.elide.truffledata.buffa;

import static dev.elide.truffledata.protobuf.SlabFormat.*;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.*;
import dev.elide.truffledata.protobuf.*;
import java.util.Objects;

/** Scoped record flyweight over a session arena; reads check its decode epoch. */
@ExportLibrary(DataLibrary.class)
public class BorrowedSlabRow extends AbstractRow implements ProtoAccess {
  private static final DataArray EMPTY = new DataArray(new Object[0]);
  final SlabSession owner;
  final Layout layout;
  final int type;
  final int record;
  final long epoch;

  BorrowedSlabRow(SlabSession owner, long epoch, int type, int record) {
    super(owner.layout.plan(type).schema());
    this.owner = owner;
    this.epoch = epoch;
    layout = owner.layout;
    this.type = type;
    this.record = record;
  }

  @Override
  protected final boolean scoped() {
    return true;
  }

  public SlabRow snapshot() {
    return owner.retain(epoch, type, record);
  }

  @Override
  public final ProtoPlan plan() {
    return layout.plan(type);
  }

  @Override
  public final boolean has(int ordinal) {
    owner.check(epoch);
    Objects.checkIndex(ordinal, plan().size());
    return owner.present(record, ordinal);
  }

  @Override
  public final Object get(int ordinal) {
    owner.check(epoch);
    Objects.checkIndex(ordinal, plan().size());
    return getChecked(ordinal);
  }

  private Object getChecked(int ordinal) {
    var field = plan().field(ordinal);
    if (!owner.present(record, ordinal)) return field.repeated() ? EMPTY : field.defaultValue();
    int at = record + layout.slot(type, ordinal);
    if (field.repeated()) return owner.array(epoch, type, ordinal, owner.word(at));
    return owner.value(epoch, type, ordinal, at);
  }

  @Override
  public final long getLong(int ordinal) {
    var field = plan().field(ordinal);
    if (field.repeated()) throw new ContractViolation("Expected INT64");
    if (!has(ordinal)) {
      if (field.defaultValue() instanceof Long n) return n;
      throw new ContractViolation("Expected INT64");
    }
    int at = record + layout.slot(type, ordinal);
    return switch (field.kind()) {
      case INT32, SINT32, SFIXED32, ENUM -> owner.word(at);
      case UINT32, FIXED32 -> Integer.toUnsignedLong(owner.word(at));
      case INT64, SINT64, SFIXED64 -> owner.wide(at);
      default -> throw new ContractViolation("Expected INT64");
    };
  }

  @Override
  public final double getDouble(int ordinal) {
    var field = plan().field(ordinal);
    if (field.repeated()) throw new ContractViolation("Expected FLOAT64");
    if (!has(ordinal) && field.defaultValue() instanceof Double value) return value;
    int at = record + layout.slot(type, ordinal);
    return switch (field.kind()) {
      case FLOAT -> (double) Float.intBitsToFloat(owner.word(at));
      case DOUBLE -> Double.longBitsToDouble(owner.wide(at));
      default -> throw new ContractViolation("Expected FLOAT64");
    };
  }

  @Override
  public final boolean getBoolean(int ordinal) {
    var field = plan().field(ordinal);
    if (field.repeated() || field.kind() != ProtoPlan.Kind.BOOL)
      throw new ContractViolation("Expected BOOLEAN");
    return has(ordinal)
        ? owner.word(record + layout.slot(type, ordinal)) != 0
        : (Boolean) field.defaultValue();
  }

  @Override
  public final int getSize(int ordinal) {
    var field = plan().field(ordinal);
    if (!field.repeated() && field.kind() != ProtoPlan.Kind.BYTES)
      throw new ContractViolation("Expected array");
    if (!has(ordinal)) return field.repeated() ? 0 : ((OwnedArray) field.defaultValue()).size();
    int offset = owner.word(record + layout.slot(type, ordinal));
    return field.repeated() ? owner.word(offset) : owner.length(offset);
  }

  /** UTF-16 length, read directly from the validated string header. */
  public final int stringLength(int ordinal) {
    var field = plan().field(ordinal);
    if (field.repeated() || field.kind() != ProtoPlan.Kind.STRING)
      throw new ContractViolation("Expected string");
    return has(ordinal)
        ? owner.utf16(owner.word(record + layout.slot(type, ordinal)))
        : ((String) field.defaultValue()).length();
  }

  /** Defensive copy of this record's preserved unknown fields. */
  public final byte[] unknownFieldBytes() {
    owner.check(epoch);
    int span = owner.unknown(record);
    return span == 0 ? new byte[0] : owner.copy(span + UNKNOWN_BYTES, owner.word(span));
  }

  @Override
  public final Accessor accessor(int ordinal) {
    return descriptor.accessor(BorrowedSlabRow.class, ordinal, i -> new Slot(layout, type, i));
  }

  private static final class Slot extends Accessor {
    private final Layout layout;
    private final int type;
    private final int offset;
    private final ProtoPlan.FieldPlan planField;

    Slot(Layout layout, int type, int ordinal) {
      super(layout.plan(type).schema(), ordinal);
      this.layout = layout;
      this.type = type;
      offset = layout.slot(type, ordinal);
      planField = layout.plan(type).field(ordinal);
    }

    @Override
    public boolean accepts(Object row) {
      return row instanceof BorrowedSlabRow r && r.layout == layout && r.type == type;
    }

    private BorrowedSlabRow row(Object value) {
      if (value instanceof BorrowedSlabRow r && r.layout == layout && r.type == type) {
        r.owner.check(r.epoch);
        return r;
      }
      throw mismatch();
    }

    @Override
    public Object read(Object value) {
      return row(value).getChecked(ordinal());
    }

    private boolean has(BorrowedSlabRow row) {
      return row.owner.present(row.record, ordinal());
    }

    @Override
    public long readLong(Object value) throws UnexpectedResultException {
      BorrowedSlabRow r = row(value);
      if (planField.repeated()) throw new UnexpectedResultException(r.get(ordinal()));
      if (!has(r) && planField.defaultValue() instanceof Long n) return n;
      int at = r.record + offset;
      return switch (planField.kind()) {
        case INT32, SINT32, SFIXED32, ENUM -> r.owner.word(at);
        case UINT32, FIXED32 -> Integer.toUnsignedLong(r.owner.word(at));
        case INT64, SINT64, SFIXED64 -> r.owner.wide(at);
        default -> throw new UnexpectedResultException(r.get(ordinal()));
      };
    }

    @Override
    public double readDouble(Object value) throws UnexpectedResultException {
      BorrowedSlabRow r = row(value);
      if (planField.repeated()) throw new UnexpectedResultException(r.get(ordinal()));
      if (!has(r) && planField.defaultValue() instanceof Double n) return n;
      int at = r.record + offset;
      return switch (planField.kind()) {
        case FLOAT -> (double) Float.intBitsToFloat(r.owner.word(at));
        case DOUBLE -> Double.longBitsToDouble(r.owner.wide(at));
        default -> throw new UnexpectedResultException(r.get(ordinal()));
      };
    }

    @Override
    public boolean readBoolean(Object value) throws UnexpectedResultException {
      BorrowedSlabRow r = row(value);
      if (planField.repeated() || planField.kind() != ProtoPlan.Kind.BOOL)
        throw new UnexpectedResultException(r.get(ordinal()));
      return has(r) ? r.owner.word(r.record + offset) != 0 : (Boolean) planField.defaultValue();
    }

    @Override
    public int length(Object value) {
      BorrowedSlabRow r = row(value);
      if (planField.repeated())
        return arrayLength(has(r) ? r.owner.word(r.owner.word(r.record + offset)) : 0);
      if (planField.kind() == ProtoPlan.Kind.BYTES)
        return arrayLength(
            has(r)
                ? r.owner.length(r.owner.word(r.record + offset))
                : ((DataArray) planField.defaultValue()).size());
      var bounds = field().constraints().length();
      if (planField.kind() == ProtoPlan.Kind.STRING && bounds == null)
        return has(r)
            ? r.owner.utf16(r.owner.word(r.record + offset))
            : ((String) planField.defaultValue()).length();
      return lengthOf(r.get(ordinal()));
    }
  }

  @ExportMessage
  final Schema getSchema() {
    return descriptor;
  }

  @ExportMessage
  final Object read(int index) {
    return get(index);
  }

  @ExportMessage
  final long readLong(int index) throws UnexpectedResultException {
    try {
      return getLong(index);
    } catch (ContractViolation invalid) {
      throw new UnexpectedResultException(get(index));
    }
  }

  @ExportMessage
  final double readDouble(int index) throws UnexpectedResultException {
    try {
      return getDouble(index);
    } catch (ContractViolation invalid) {
      throw new UnexpectedResultException(get(index));
    }
  }

  @ExportMessage
  final boolean readBoolean(int index) throws UnexpectedResultException {
    try {
      return getBoolean(index);
    } catch (ContractViolation invalid) {
      throw new UnexpectedResultException(get(index));
    }
  }
}
