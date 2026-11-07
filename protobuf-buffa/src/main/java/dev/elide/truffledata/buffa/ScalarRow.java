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
  private final long first;
  private final long second;
  private final long presence;

  ScalarRow(ViewOwner owner, ProtoPlan plan, long first, long second, long presence) {
    super(owner, plan);
    this.presence = presence;
    this.first = first;
    this.second = second;
  }

  boolean present(int index) {
    return (presence & (1L << index)) != 0;
  }

  long scalar(int index) {
    Objects.checkIndex(index, descriptor.size());
    return index == 0 ? first : second;
  }

  /** Bound per plan: an owner check and one inline field; no node, so fresh owners never churn. */
  @Override
  public Accessor accessor(int ordinal) {
    ProtoPlan rowPlan = plan();
    return descriptor.accessor(ScalarRow.class, ordinal, i -> new Inline(rowPlan, i));
  }

  private static final class Inline extends Accessor {
    private final ProtoPlan plan;

    Inline(ProtoPlan plan, int ordinal) {
      super(plan.schema(), ordinal);
      this.plan = plan;
    }

    @Override
    public boolean accepts(Object row) {
      return row instanceof ScalarRow r && r.plan() == plan;
    }

    private long scalar(Object row) {
      if (!(row instanceof ScalarRow r && r.plan() == plan)) throw mismatch();
      r.owner.check();
      return ordinal() == 0 ? r.first : r.second;
    }

    @Override
    public Object read(Object row) {
      return scalar(row);
    }

    @Override
    public boolean present(Object row) {
      scalar(row);
      return true;
    }

    @Override
    public long readLong(Object row) {
      return scalar(row);
    }

    @Override
    public double readDouble(Object row) throws UnexpectedResultException {
      throw new UnexpectedResultException(scalar(row));
    }

    @Override
    public boolean readBoolean(Object row) throws UnexpectedResultException {
      throw new UnexpectedResultException(scalar(row));
    }
  }

  @ExportMessage
  static Object read(
      ScalarRow row,
      int index,
      @Cached.Shared("owner") @Cached("create()") ViewOwner.CheckOpenNode check) {
    check.execute(row.owner);
    return row.scalar(index);
  }

  @ExportMessage
  static long readLong(
      ScalarRow row,
      int index,
      @Cached.Shared("owner") @Cached("create()") ViewOwner.CheckOpenNode check) {
    check.execute(row.owner);
    return row.scalar(index);
  }

  @ExportMessage
  static double readDouble(
      ScalarRow row,
      int index,
      @Cached.Shared("owner") @Cached("create()") ViewOwner.CheckOpenNode check)
      throws UnexpectedResultException {
    check.execute(row.owner);
    throw new UnexpectedResultException(row.scalar(index));
  }

  @ExportMessage
  static boolean readBoolean(
      ScalarRow row,
      int index,
      @Cached.Shared("owner") @Cached("create()") ViewOwner.CheckOpenNode check)
      throws UnexpectedResultException {
    check.execute(row.owner);
    throw new UnexpectedResultException(row.scalar(index));
  }
}
