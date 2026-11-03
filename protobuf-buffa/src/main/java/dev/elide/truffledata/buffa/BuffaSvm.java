package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.Objects;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.c.type.*;
import org.graalvm.word.WordFactory;

/** SVM ownership transfer for producer-owned unmanaged wire buffers. */
public final class BuffaSvm {
  private BuffaSvm() {}

  record Address(long value) {}

  /**
   * Transfer a live, immutable native region. This unsafe API trusts address/length and exclusive
   * ownership: caller must neither free nor mutate/recycle the region until release runs. The
   * callback runs exactly once on this thread after the last handle/view closes.
   */
  @TruffleBoundary
  public static WireInput adopt(long address, int length, Runnable release) {
    if (!ImageInfo.inImageRuntimeCode())
      throw new UnsupportedOperationException("SVM-only native ownership API");
    if (address == 0 || length < 0) throw new IllegalArgumentException("Invalid native region");
    return owned(WordFactory.pointer(address), length, Objects.requireNonNull(release), true);
  }

  static WireInput owned(CCharPointer pointer, int size, Runnable release, boolean sealed) {
    try {
      var input =
          new WireInput(
              CTypeConversion.asByteBuffer(pointer, size),
              release,
              new Address(pointer.rawValue()));
      input.lease.sealed = sealed;
      return input;
    } catch (RuntimeException | Error failure) {
      release.run();
      throw failure;
    }
  }
}
