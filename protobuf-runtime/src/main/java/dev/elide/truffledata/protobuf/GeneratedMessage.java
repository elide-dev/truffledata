package dev.elide.truffledata.protobuf;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.*;
import java.util.Objects;

/** Immutable base for concrete schema-generated records. Field storage belongs to each subtype. */
@ExportLibrary(DataLibrary.class)
public abstract class GeneratedMessage extends AbstractRow implements ProtoAccess {
  private static final byte[] EMPTY = new byte[0];
  private final ProtoPlan plan;
  // For <=32 fields, the high bits can hold a decoder-proven encoded size plus one.
  // Zero means unknown; wider messages use every bit for presence and have no size cache.
  private final long presence;
  final byte[] unknown;

  protected GeneratedMessage(ProtoPlan plan, long presence, byte[] unknown) {
    super(plan.schema());
    if (plan.size() > 64) throw new ContractViolation("Generated presence bitmap overflow");
    this.plan = plan;
    this.presence = presence;
    this.unknown = unknown.length == 0 ? EMPTY : unknown.clone();
  }

  @Override
  public final ProtoPlan plan() {
    return plan;
  }

  @Override
  public final boolean has(int ordinal) {
    Objects.checkIndex(ordinal, plan.size());
    return (presence & (1L << ordinal)) != 0;
  }

  /** Validation shared by generated builders; never called by immutable field readers. */
  @TruffleBoundary
  public static void validateField(ProtoPlan plan, int ordinal, Object value) {
    var field = plan.field(ordinal);
    Values.validate(field.contract(), value);
    if (value instanceof String text) ProtoPlan.validateString(text);
    if (field.kind() == ProtoPlan.Kind.BYTES && !((OwnedArray) value).isBytes())
      throw new ContractViolation("Expected byte storage");
    var enumeration = field.enumType();
    if (enumeration != null && !enumeration.accepts(((Long) value).intValue()))
      throw new ContractViolation("Unknown closed enum value");
  }

  /** Implemented by the schema-generated codec behind its own Truffle boundary. */
  public abstract byte[] encode();

  protected final boolean isSet(long mask) {
    return (presence & mask) != 0;
  }

  /** Generated decoders can retain a proven wire size in spare, immutable presence bits. */
  protected final int decodedWireSize() {
    return plan.size() <= 32 ? (int) ((presence >>> 32) - 1) : -1;
  }

  protected final int unknownSize() {
    return unknown.length;
  }

  protected final byte[] allocateOutput(long size) {
    int count = checkedSize(size);
    return count == 0 ? EMPTY : new byte[count];
  }

  protected final int checkedSize(long size) {
    if (size < 0 || size > plan.maxWireBytes())
      throw new ContractViolation("Encoded wire budget exceeded");
    return (int) size;
  }

  /** Complete a schema-generated direct writer, copying unknown bytes only once. */
  protected final byte[] finishEncoded(byte[] target, int position) {
    if (position < 0 || unknown.length != target.length - position)
      throw new ContractViolation("Generated output size mismatch");
    System.arraycopy(unknown, 0, target, position, unknown.length);
    return target;
  }

  protected final void writeUnknown(GeneratedOutput output) {
    if (unknown.length != 0) output.raw(unknown);
  }

  @ExportMessage
  final Schema getSchema() {
    return descriptor;
  }

  /**
   * The generated class is the layout: an accessor binds (plan, generated class, ordinal) and reads
   * through the class's own ordinal switch, which folds to one field load once the ordinal and the
   * exact class are constants.
   */
  @Override
  public final Accessor accessor(int ordinal) {
    Class<? extends GeneratedMessage> type = getClass();
    return descriptor.accessor(type, ordinal, i -> new Generated(type, plan, i));
  }

  private static final class Generated extends Accessor {
    private final Class<? extends GeneratedMessage> type;
    private final ProtoPlan plan;

    Generated(Class<? extends GeneratedMessage> type, ProtoPlan plan, int ordinal) {
      super(plan.schema(), ordinal);
      this.type = type;
      this.plan = plan;
    }

    @Override
    public boolean accepts(Object row) {
      return row.getClass() == type && ((GeneratedMessage) row).plan == plan;
    }

    private GeneratedMessage message(Object row) {
      if (!accepts(row)) throw mismatch();
      return CompilerDirectives.castExact(row, type);
    }

    @Override
    public Object read(Object row) {
      return message(row).get(ordinal());
    }

    @Override
    public long readLong(Object row) throws UnexpectedResultException {
      var message = message(row);
      if (field().kind() == Field.Kind.INT64) return message.getLong(ordinal());
      Object value = message.get(ordinal());
      if (value instanceof Long number) return number;
      throw new UnexpectedResultException(value);
    }

    @Override
    public double readDouble(Object row) throws UnexpectedResultException {
      var message = message(row);
      if (field().kind() == Field.Kind.FLOAT64) return message.getDouble(ordinal());
      throw new UnexpectedResultException(message.get(ordinal()));
    }

    @Override
    public boolean readBoolean(Object row) throws UnexpectedResultException {
      var message = message(row);
      if (field().kind() == Field.Kind.BOOLEAN) return message.getBoolean(ordinal());
      throw new UnexpectedResultException(message.get(ordinal()));
    }
  }

  @ExportMessage
  static Object read(GeneratedMessage row, int index) {
    return row.get(index);
  }

  @ExportMessage
  static long readLong(GeneratedMessage row, int index) throws UnexpectedResultException {
    if (row.descriptor.field(index).kind() == Field.Kind.INT64) return row.getLong(index);
    Object value = row.get(index);
    if (value instanceof Long number) return number;
    throw new UnexpectedResultException(value);
  }

  @ExportMessage
  static double readDouble(GeneratedMessage row, int index) throws UnexpectedResultException {
    if (row.descriptor.field(index).kind() == Field.Kind.FLOAT64) return row.getDouble(index);
    throw new UnexpectedResultException(row.get(index));
  }

  @ExportMessage
  static boolean readBoolean(GeneratedMessage row, int index) throws UnexpectedResultException {
    if (row.descriptor.field(index).kind() == Field.Kind.BOOLEAN) return row.getBoolean(index);
    throw new UnexpectedResultException(row.get(index));
  }
}
