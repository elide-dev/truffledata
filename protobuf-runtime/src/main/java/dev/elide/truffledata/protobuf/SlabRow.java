package dev.elide.truffledata.protobuf;

import static dev.elide.truffledata.protobuf.SlabFormat.*;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.*;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Immutable record flyweight over a durable owning slab. Schema-generated subclasses (one final
 * class per layout type of an unprojected owning slab, see {@link ProtoPlan.SlabRows}) override
 * the ordinal-dispatched reads with constant-offset loads through the protected slot readers; the
 * generic reads below serve runtime plans, projected slabs and lazily expanded rows.
 */
@ExportLibrary(DataLibrary.class)
public class SlabRow extends AbstractRow implements ProtoAccess {
  final SlabMessage owner;
  final Layout layout;
  final int type;
  final int record;
  final boolean expanded;

  SlabRow(Layout layout) {
    super(layout.plan(0).schema());
    this.layout = layout;
    type = 0;
    record = ROOT;
    expanded = false;
    owner = (SlabMessage) this;
  }

  SlabRow(SlabMessage owner, int type, int record) {
    this(owner, type, record, false);
  }

  /** A child record of {@code owner}; generated subclasses are created only with {@code expanded} false. */
  protected SlabRow(SlabMessage owner, int type, int record, boolean expanded) {
    super(owner.layout.plan(type).schema());
    this.owner = owner;
    layout = owner.layout;
    this.type = type;
    this.record = record;
    this.expanded = expanded;
  }

  @Override
  public final ProtoPlan plan() {
    return layout.plan(type);
  }

  // Slot readers for generated subclasses. Offsets are per-type record offsets (identical in every
  // layout containing the type); they never consult the projection, which is why generated rows
  // exist only on unprojected owners.

  /** Presence bit of {@code ordinal} in this record. */
  protected final boolean present(int ordinal) {
    return SlabFormat.present(owner.bytes, record, ordinal);
  }

  protected final int int32At(int slot) {
    return getInt(owner.bytes, record + slot);
  }

  protected final long int64At(int slot) {
    return SlabFormat.getLong(owner.bytes, record + slot);
  }

  /** The owner's cached string view of a present string slot. */
  protected final String textAt(int slot) {
    return owner.text(int32At(slot));
  }

  /** The owner's cached bytes view of a present bytes slot. */
  protected final OwnedArray blobAt(int slot) {
    return owner.blob(int32At(slot));
  }

  /** The owner's cached child row of a present message slot. */
  protected final SlabRow rowAt(int slot, int ordinal) {
    return owner.row(layout.childType(type, ordinal), int32At(slot), false);
  }

  /** The owner's cached list view of a present repeated slot. */
  protected final OwnedArray listAt(int slot, int ordinal) {
    return owner.array(type, ordinal, int32At(slot), false);
  }

  /** The owner's cached boxed unsigned 64-bit value of a present slot. */
  protected final Object unsignedAt(int slot) {
    return owner.unsigned(record + slot, int64At(slot));
  }

  /** UTF-16 length of a present string slot, from its validated header. */
  protected final int utf16LengthAt(int slot) {
    return utf16Length(owner.bytes, int32At(slot));
  }

  /** Payload length of a present bytes slot. */
  protected final int bytesLengthAt(int slot) {
    return SlabFormat.stringLength(owner.bytes, int32At(slot));
  }

  /** Element count of a present repeated slot. */
  protected final int listCountAt(int slot) {
    return listCount(owner.bytes, int32At(slot));
  }

  /** The shared empty list of an absent repeated field. */
  protected static OwnedArray emptyList() {
    return SlabArray.EMPTY;
  }

  @Override
  public boolean has(int ordinal) {
    Objects.checkIndex(ordinal, plan().size());
    return SlabFormat.present(owner.bytes, record, ordinal);
  }

  @Override
  public Object get(int ordinal) {
    Objects.checkIndex(ordinal, plan().size());
    var field = plan().field(ordinal);
    if (!field.repeated()) owner.requireValue(type, ordinal, expanded);
    if (!has(ordinal)) return field.repeated() ? SlabArray.EMPTY : field.defaultValue();
    int at = record + layout.slot(type, ordinal);
    if (field.repeated()) return owner.array(type, ordinal, getInt(owner.bytes, at), expanded);
    return value(owner, type, ordinal, at, expanded);
  }

  static Object value(SlabMessage owner, int type, int ordinal, int at, boolean expanded) {
    byte[] slab = owner.bytes;
    var field = owner.layout.plan(type).field(ordinal);
    var kind = field.kind();
    long bits =
        switch (kind) {
          case DOUBLE, INT64, UINT64, FIXED64, SFIXED64, SINT64 -> SlabFormat.getLong(slab, at);
          case BOOL -> getUnsignedByte(slab, at);
          default -> getInt(slab, at);
        };
    return switch (kind) {
      case STRING -> owner.text((int) bits);
      case BYTES -> owner.blob((int) bits);
      case MESSAGE, GROUP ->
          owner.row(
              owner.layout.childType(type, ordinal),
              (int) bits,
              expanded || owner.expandChild(type, ordinal));
      case UINT64, FIXED64 -> owner.unsigned(at, bits);
      case UINT32, FIXED32 -> bits & 0xffffffffL;
      case FLOAT -> (double) Float.intBitsToFloat((int) bits);
      case DOUBLE -> Double.longBitsToDouble(bits);
      case BOOL -> bits != 0;
      default -> bits;
    };
  }

  static Object unsigned(long bits) {
    return Values.normalize(
        bits >= 0
            ? BigInteger.valueOf(bits)
            : BigInteger.valueOf(bits & Long.MAX_VALUE).setBit(63));
  }

  @Override
  public long getLong(int ordinal) {
    owner.requireValue(type, ordinal, expanded);
    var field = plan().field(ordinal);
    if (field.repeated()) throw new ContractViolation("Expected INT64");
    if (!has(ordinal)) {
      if (field.defaultValue() instanceof Long n) return n;
      throw new ContractViolation("Expected INT64");
    }
    int at = record + layout.slot(type, ordinal);
    return switch (field.kind()) {
      case INT32, SINT32, SFIXED32, ENUM -> getInt(owner.bytes, at);
      case UINT32, FIXED32 -> Integer.toUnsignedLong(getInt(owner.bytes, at));
      case INT64, SINT64, SFIXED64 -> SlabFormat.getLong(owner.bytes, at);
      default -> throw new ContractViolation("Expected INT64");
    };
  }

  @Override
  public double getDouble(int ordinal) {
    owner.requireValue(type, ordinal, expanded);
    var field = plan().field(ordinal);
    if (field.repeated()) throw new ContractViolation("Expected FLOAT64");
    if (!has(ordinal) && field.defaultValue() instanceof Double value) return value;
    int at = record + layout.slot(type, ordinal);
    return switch (field.kind()) {
      case FLOAT -> (double) Float.intBitsToFloat(getInt(owner.bytes, at));
      case DOUBLE -> Double.longBitsToDouble(SlabFormat.getLong(owner.bytes, at));
      default -> throw new ContractViolation("Expected FLOAT64");
    };
  }

  @Override
  public boolean getBoolean(int ordinal) {
    owner.requireValue(type, ordinal, expanded);
    var field = plan().field(ordinal);
    if (field.repeated() || field.kind() != ProtoPlan.Kind.BOOL)
      throw new ContractViolation("Expected BOOLEAN");
    return has(ordinal)
        ? getInt(owner.bytes, record + layout.slot(type, ordinal)) != 0
        : (Boolean) field.defaultValue();
  }

  @Override
  public int getSize(int ordinal) {
    var field = plan().field(ordinal);
    if (!field.repeated()) owner.requireValue(type, ordinal, expanded);
    if (!field.repeated() && field.kind() != ProtoPlan.Kind.BYTES)
      throw new ContractViolation("Expected array");
    if (!has(ordinal)) return field.repeated() ? 0 : ((DataArray) field.defaultValue()).size();
    int offset = getInt(owner.bytes, record + layout.slot(type, ordinal));
    return field.repeated()
        ? listCount(owner.bytes, offset)
        : SlabFormat.stringLength(owner.bytes, offset);
  }

  /** UTF-16 length, read directly from the validated string header. */
  public int stringLength(int ordinal) {
    owner.requireValue(type, ordinal, expanded);
    var field = plan().field(ordinal);
    if (field.repeated() || field.kind() != ProtoPlan.Kind.STRING)
      throw new ContractViolation("Expected string");
    return has(ordinal)
        ? utf16Length(owner.bytes, getInt(owner.bytes, record + layout.slot(type, ordinal)))
        : ((String) field.defaultValue()).length();
  }

  /** Defensive copy of this record's preserved unknown fields. */
  public final byte[] unknownFieldBytes() {
    return owner.unknownCopy(record);
  }

  /** Whether this record retains raw unknown wire bytes, without copying or allocating. */
  public final boolean hasUnknownFields() {
    return unknownFieldByteCount() != 0;
  }

  /**
   * Number of raw unknown wire bytes retained by this record, without copying or allocating.
   * Includes empty unknown groups; excludes declared child records' unknown fields. This is a
   * storage query, not a semantic equality or canonicalization operation.
   */
  public final int unknownFieldByteCount() {
    int span = SlabFormat.unknown(owner.bytes, record);
    return span == 0 ? 0 : unknownLength(owner.bytes, span);
  }

  @Override
  public Accessor accessor(int ordinal) {
    return layout.accessor(type, ordinal);
  }

  static final class Slot extends Accessor {
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
      return row instanceof SlabRow r && r.layout == layout && r.type == type;
    }

    private SlabRow row(Object value) {
      if (value instanceof SlabRow r && r.layout == layout && r.type == type) return r;
      throw mismatch();
    }

    /**
     * A row the read node's guard already admitted: only the layout check is skipped, projection
     * ({@code requireValue}), presence and kind handling stay in the shared readers.
     */
    private SlabRow trusted(Object value) {
      assertAccepted(value);
      return (SlabRow) value;
    }

    @Override
    public Object read(Object value) {
      return row(value).get(ordinal());
    }

    private boolean has(SlabRow row) {
      return SlabFormat.present(row.owner.bytes, row.record, ordinal());
    }

    @Override
    public long readLong(Object value) throws UnexpectedResultException {
      return longOf(row(value));
    }

    @Override
    protected long readLongBound(Object value) throws UnexpectedResultException {
      return longOf(trusted(value));
    }

    private long longOf(SlabRow r) throws UnexpectedResultException {
      r.owner.requireValue(type, ordinal(), r.expanded);
      if (planField.repeated()) throw new UnexpectedResultException(r.get(ordinal()));
      if (!has(r) && planField.defaultValue() instanceof Long n) return n;
      int at = r.record + offset;
      return switch (planField.kind()) {
        case INT32, SINT32, SFIXED32, ENUM -> getInt(r.owner.bytes, at);
        case UINT32, FIXED32 -> Integer.toUnsignedLong(getInt(r.owner.bytes, at));
        case INT64, SINT64, SFIXED64 -> SlabFormat.getLong(r.owner.bytes, at);
        default -> throw new UnexpectedResultException(r.get(ordinal()));
      };
    }

    @Override
    public double readDouble(Object value) throws UnexpectedResultException {
      return doubleOf(row(value));
    }

    @Override
    protected double readDoubleBound(Object value) throws UnexpectedResultException {
      return doubleOf(trusted(value));
    }

    private double doubleOf(SlabRow r) throws UnexpectedResultException {
      r.owner.requireValue(type, ordinal(), r.expanded);
      if (planField.repeated()) throw new UnexpectedResultException(r.get(ordinal()));
      if (!has(r) && planField.defaultValue() instanceof Double n) return n;
      int at = r.record + offset;
      return switch (planField.kind()) {
        case FLOAT -> (double) Float.intBitsToFloat(getInt(r.owner.bytes, at));
        case DOUBLE -> Double.longBitsToDouble(SlabFormat.getLong(r.owner.bytes, at));
        default -> throw new UnexpectedResultException(r.get(ordinal()));
      };
    }

    @Override
    public boolean readBoolean(Object value) throws UnexpectedResultException {
      return booleanOf(row(value));
    }

    @Override
    protected boolean readBooleanBound(Object value) throws UnexpectedResultException {
      return booleanOf(trusted(value));
    }

    private boolean booleanOf(SlabRow r) throws UnexpectedResultException {
      r.owner.requireValue(type, ordinal(), r.expanded);
      if (planField.repeated() || planField.kind() != ProtoPlan.Kind.BOOL)
        throw new UnexpectedResultException(r.get(ordinal()));
      return has(r)
          ? getInt(r.owner.bytes, r.record + offset) != 0
          : (Boolean) planField.defaultValue();
    }

    @Override
    public int length(Object value) {
      return lengthIn(row(value));
    }

    @Override
    protected int lengthBound(Object value) {
      return lengthIn(trusted(value));
    }

    private int lengthIn(SlabRow r) {
      if (!planField.repeated()) r.owner.requireValue(type, ordinal(), r.expanded);
      if (planField.repeated())
        return arrayLength(
            has(r) ? listCount(r.owner.bytes, getInt(r.owner.bytes, r.record + offset)) : 0);
      if (planField.kind() == ProtoPlan.Kind.BYTES)
        return arrayLength(
            has(r)
                ? SlabFormat.stringLength(r.owner.bytes, getInt(r.owner.bytes, r.record + offset))
                : ((DataArray) planField.defaultValue()).size());
      var bounds = field().constraints().length();
      if (planField.kind() == ProtoPlan.Kind.STRING && bounds == null)
        return has(r)
            ? utf16Length(r.owner.bytes, getInt(r.owner.bytes, r.record + offset))
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
