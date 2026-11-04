package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.Node;
import org.jspecify.annotations.Nullable;

/** Shared lifetime mechanics; each message itself owns its lease and compiled-read assumption. */
abstract sealed class ViewOwner permits WireMessage, TypedMessage {
  final WireInput.Lease lease;
  private @Nullable Assumption alive;
  private boolean open = true;

  ViewOwner(WireInput.Lease lease) {
    this.lease = lease;
  }

  final void check() {
    lease.check();
    if (!open) throw new IllegalStateException("Wire view closed");
  }

  @CompilerDirectives.TruffleBoundary
  final Assumption assumption() {
    check();
    if (alive == null)
      alive = com.oracle.truffle.api.Truffle.getRuntime().createAssumption("wire view alive");
    return alive;
  }

  /** Cache only retained owners; uncached ingestion and megamorphic sites use ordinary checks. */
  @GenerateUncached
  @GenerateInline(false)
  abstract static class CheckOpenNode extends Node {
    abstract void execute(ViewOwner owner);

    @NeverDefault
    static CheckOpenNode create() {
      return ViewOwnerFactory.CheckOpenNodeGen.create();
    }

    @Specialization(guards = "owner == cachedOwner", assumptions = "alive", limit = "3")
    static void cached(
        ViewOwner owner,
        @Cached("owner") ViewOwner cachedOwner,
        @Cached("cachedOwner.assumption()") Assumption alive) {
      owner.lease.check();
    }

    @Specialization(replaces = "cached")
    static void generic(ViewOwner owner) {
      owner.check();
    }
  }

  final void closeOwner() {
    lease.thread();
    if (open) {
      if (alive != null) alive.invalidate();
      open = false;
      lease.release();
    }
  }
}
