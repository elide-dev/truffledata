package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives;
import dev.elide.truffledata.*;

/**
 * A decoded view owns a lease, not the payload. Snapshot to detach; close after guest consumption.
 */
public final class WireMessage implements AutoCloseable {
  final WireInput.Lease lease;
  private final com.oracle.truffle.api.Assumption alive =
      com.oracle.truffle.api.Truffle.getRuntime().createAssumption("wire view alive");
  private boolean open = true;
  private DataArray fields = new DataArray(new Object[0]);
  private final long nativeBytes;
  private final Limits limits;

  WireMessage(WireInput.Lease lease, long nativeBytes, Limits limits) {
    this.lease = lease;
    this.nativeBytes = nativeBytes;
    this.limits = limits;
  }

  @CompilerDirectives.TruffleBoundary
  public DataArray snapshot() {
    check();
    return copy(fields);
  }

  private DataArray copy(DataArray fields) {
    Object[] rows = new Object[fields.size()];
    var data = DataLibrary.factory().getUncached();
    for (int i = 0; i < rows.length; i++) {
      Object row = fields.get(i), value = data.read(row, 2);
      if (value instanceof BorrowedBytes bytes) value = bytes.snapshot();
      else if (value instanceof DataArray group && !group.isBytes()) value = copy(group);
      rows[i] =
          Batch.copyOf(
                  BuffaWire.SCHEMA,
                  java.util.Collections.singletonList(
                      new Object[] {data.read(row, 0), data.read(row, 1), value}),
                  limits)
              .row(0);
    }
    return new DataArray(rows);
  }

  void fields(DataArray fields) {
    this.fields = fields;
  }

  public DataArray fields() {
    check();
    return fields;
  }

  /**
   * Peak retained native index capacity; excludes the caller-owned input and allocator metadata.
   */
  public long nativeIndexBytes() {
    check();
    return nativeBytes;
  }

  void check() {
    lease.check();
    if (!alive.isValid()) throw new IllegalStateException("Wire view closed");
  }

  @Override
  @CompilerDirectives.TruffleBoundary
  public void close() {
    lease.thread();
    if (open) {
      alive.invalidate();
      open = false;
      lease.release();
    }
  }
}
