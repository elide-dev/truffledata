package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.Node;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
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

  /** Materialize validated native text only on demand; the owner retains its sealed input. */
  @CompilerDirectives.TruffleBoundary
  final String utf8(int offset, int length) {
    check();
    Objects.checkFromIndexSize(offset, length, lease.bytes.capacity());
    if (length == 0) return "";
    // Match Myna's conservative bulk crossover. Static SVM conversion does not depend on a
    // decoder handle, so messages remain readable after their decoder has closed. JVM/Java-only
    // images retain the JDK route; no optional native symbol is reachable from fallback images.
    if (ImageInfo.inImageCode()
        && ImageSingletons.contains(BuffaWire.NativeFactory.class)
        && length >= 256
        && lease.attachment instanceof BuffaSvm.Address address)
      return SvmBackend.utf8(address.value() + offset, length);
    byte[] bytes = new byte[length];
    lease.bytes.get(offset, bytes);
    return new String(bytes, StandardCharsets.UTF_8);
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
