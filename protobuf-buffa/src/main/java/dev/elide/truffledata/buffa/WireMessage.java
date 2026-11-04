package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.Node;
import dev.elide.truffledata.*;
import org.jspecify.annotations.Nullable;

/**
 * A decoded view owns a lease, not the payload. Snapshot to detach; close after guest consumption.
 */
public final class WireMessage implements AutoCloseable {
  private static final DataArray EMPTY_FIELDS = new DataArray(new Object[0]);
  final WireInput.Lease lease;
  private @Nullable Assumption alive;
  private boolean open = true;
  private DataArray fields = EMPTY_FIELDS;
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
    if (!open) throw new IllegalStateException("Wire view closed");
  }

  @CompilerDirectives.TruffleBoundary
  Assumption assumption() {
    check();
    if (alive == null)
      alive = com.oracle.truffle.api.Truffle.getRuntime().createAssumption("wire view alive");
    return alive;
  }

  /** Cache only retained owners; uncached ingestion and megamorphic sites use ordinary checks. */
  @GenerateUncached
  @GenerateInline(false)
  abstract static class CheckOpenNode extends Node {
    abstract void execute(WireMessage owner);

    @NeverDefault
    static CheckOpenNode create() {
      return WireMessageFactory.CheckOpenNodeGen.create();
    }

    @Specialization(guards = "owner == cachedOwner", assumptions = "alive", limit = "3")
    static void cached(
        WireMessage owner,
        @Cached("owner") WireMessage cachedOwner,
        @Cached("cachedOwner.assumption()") Assumption alive) {
      owner.lease.check();
    }

    @Specialization(replaces = "cached")
    static void generic(WireMessage owner) {
      owner.check();
    }
  }

  @Override
  @CompilerDirectives.TruffleBoundary
  public void close() {
    lease.thread();
    if (open) {
      if (alive != null) alive.invalidate();
      open = false;
      lease.release();
    }
  }
}
