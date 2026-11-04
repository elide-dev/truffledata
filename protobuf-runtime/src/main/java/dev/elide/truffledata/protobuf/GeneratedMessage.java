package dev.elide.truffledata.protobuf;

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
  private final long presence;
  final byte[] unknown;

  protected GeneratedMessage(ProtoPlan plan, long presence, byte[] unknown) {
    super(plan.schema());
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

  @TruffleBoundary
  public final byte[] encode() {
    return ProtoCodec.encode(this);
  }

  @ExportMessage
  final Schema getSchema() {
    return descriptor;
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
