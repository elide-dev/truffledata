package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.*;
import dev.elide.truffledata.protobuf.*;

/** A lazily decoded child remains scoped until explicitly retained. */
@ExportLibrary(DataLibrary.class)
public final class BorrowedMaterializedRow extends AbstractRow implements ProtoAccess {
  private final SlabSession owner;
  private final long epoch;
  private final SlabRow row;

  BorrowedMaterializedRow(SlabSession owner, long epoch, SlabRow row) {
    super(row.schema());
    this.owner = owner;
    this.epoch = epoch;
    this.row = row;
  }

  @Override
  protected boolean scoped() {
    return true;
  }

  public SlabRow snapshot() {
    owner.check(epoch);
    return row;
  }

  @Override
  public ProtoPlan plan() {
    return row.plan();
  }

  @Override
  public boolean has(int ordinal) {
    owner.check(epoch);
    return row.has(ordinal);
  }

  @Override
  public Object get(int ordinal) {
    owner.check(epoch);
    Object value = row.get(ordinal);
    return value instanceof SlabRow || value instanceof OwnedArray
        ? owner.wrap(epoch, value)
        : value;
  }

  @Override
  public long getLong(int ordinal) {
    owner.check(epoch);
    return row.getLong(ordinal);
  }

  @Override
  public double getDouble(int ordinal) {
    owner.check(epoch);
    return row.getDouble(ordinal);
  }

  @Override
  public boolean getBoolean(int ordinal) {
    owner.check(epoch);
    return row.getBoolean(ordinal);
  }

  @Override
  public int getSize(int ordinal) {
    owner.check(epoch);
    return row.getSize(ordinal);
  }

  public byte[] unknownFieldBytes() {
    owner.check(epoch);
    return row.unknownFieldBytes();
  }

  @ExportMessage
  Schema getSchema() {
    return descriptor;
  }

  @ExportMessage
  Object read(int index) {
    return get(index);
  }

  @ExportMessage
  long readLong(int index) throws UnexpectedResultException {
    try {
      return getLong(index);
    } catch (ContractViolation invalid) {
      throw new UnexpectedResultException(get(index));
    }
  }

  @ExportMessage
  double readDouble(int index) throws UnexpectedResultException {
    try {
      return getDouble(index);
    } catch (ContractViolation invalid) {
      throw new UnexpectedResultException(get(index));
    }
  }

  @ExportMessage
  boolean readBoolean(int index) throws UnexpectedResultException {
    try {
      return getBoolean(index);
    } catch (ContractViolation invalid) {
      throw new UnexpectedResultException(get(index));
    }
  }
}
