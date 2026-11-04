package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.*;
import dev.elide.truffledata.protobuf.*;
import java.util.Objects;

/** Small integer view with inline primitive storage; ordinary typed rows retain a plain array. */
@ExportLibrary(DataLibrary.class)
final class ScalarRow extends TypedRow {
  private static final Object[] NO_VALUES = new Object[0];
  private static final boolean[] NO_PRESENCE = new boolean[0];
  private final long first;
  private final long second;

  ScalarRow(WireMessage owner, ProtoPlan plan, long first, long second, long presence) {
    super(owner, plan, NO_VALUES, presence, NO_PRESENCE);
    this.first = first;
    this.second = second;
  }

  private long scalar(int index) {
    Objects.checkIndex(index, descriptor.size());
    return index == 0 ? first : second;
  }

  @Override
  public Object get(int ordinal) {
    return getLong(ordinal);
  }

  @Override
  public long getLong(int ordinal) {
    owner.check();
    return scalar(ordinal);
  }

  @Override
  @ExportMessage
  Object read(
      int index, @Cached.Shared("owner") @Cached("create()") WireMessage.CheckOpenNode check) {
    check.execute(owner);
    return scalar(index);
  }

  @Override
  @ExportMessage
  long readLong(
      int index, @Cached.Shared("owner") @Cached("create()") WireMessage.CheckOpenNode check) {
    check.execute(owner);
    return scalar(index);
  }

  @Override
  @ExportMessage
  double readDouble(
      int index, @Cached.Shared("owner") @Cached("create()") WireMessage.CheckOpenNode check)
      throws UnexpectedResultException {
    check.execute(owner);
    throw new UnexpectedResultException(scalar(index));
  }

  @Override
  @ExportMessage
  boolean readBoolean(
      int index, @Cached.Shared("owner") @Cached("create()") WireMessage.CheckOpenNode check)
      throws UnexpectedResultException {
    check.execute(owner);
    throw new UnexpectedResultException(scalar(index));
  }
}
