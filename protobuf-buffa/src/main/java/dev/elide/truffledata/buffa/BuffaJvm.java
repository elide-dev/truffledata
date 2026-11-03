package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/** JVM ownership transfer for wire data originating in unmanaged memory. */
public final class BuffaJvm {
  private BuffaJvm() {}

  /**
   * Transfer an immutable native region and its release obligation. The caller must not mutate,
   * close, or recycle it until release runs. Region scope remains checked on every access. The
   * release callback runs on this thread when both input handle and all views are closed.
   */
  @TruffleBoundary
  public static WireInput adopt(MemorySegment region, Runnable release) {
    Objects.requireNonNull(release);
    if (!region.isNative()
        || !region.scope().isAlive()
        || region.byteSize() > Integer.MAX_VALUE
        || region.address() == 0)
      throw new IllegalArgumentException(
          "Expected a live native region within the wire-size limit");
    var input = new WireInput(region.asByteBuffer().asReadOnlyBuffer(), release, region);
    input.lease.sealed = true;
    return input;
  }
}
