package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import dev.elide.myna.truffle.NativePointer;
import dev.elide.truffledata.buffa.generated.Buffa;
import dev.elide.truffledata.protobuf.ProtoPlan;
import dev.elide.truffledata.protobuf.SlabLimits;
import dev.elide.truffledata.protobuf.SlabMessage;
import java.util.Arrays;
import java.util.List;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.WordFactory;
import org.jspecify.annotations.NullMarked;

/**
 * Benchmark-only body of the heap-input owning-slab decode ({@code BuffaPlan.decode(byte[])} on
 * SVM, {@code SvmBackend.slab(long, byte[], ...)}) with no {@code @TruffleBoundary} of its own, so a
 * runtime-compiled Truffle root can partially evaluate the Java publication code around the native
 * call (plan 2026-11-08-beat-stock-svm WS11). The native call is reached either directly through
 * the shipped {@code Buffa.decodeSlab} binding or through a Myna Truffle cell handed in by the
 * caller. Lives in the benchmark target only (split package for the backend's package-private
 * plumbing); no production class can reach it, and the shipped bindings are unchanged.
 *
 * <p>What stays behind a boundary is exactly what the production path keeps there:
 * {@link SlabMessage#takeOwnership} (the verifier and owner publication) is itself a
 * {@code @TruffleBoundary} public API, so every route pays it as one AOT call.
 */
@NullMarked
public final class CellProbe implements AutoCloseable {
  /** Routes the probe measures, in the order the results table reports them. */
  public static final List<String> ROUTES =
      List.of("boundary", "boundary-body", "direct", "cell", "cell-interop");

  /** How a route reaches {@code td_buffa_decode_slab}; the {@code cell} routes adopt one. */
  public abstract static class Invoker extends Node {
    /** Arguments in seam order, as a guest would pass them to the {@code decodeSlab} cell. */
    public abstract int invoke(Object[] arguments);
  }

  private final BuffaWire.Backend backend;
  private final ProtoPlan plan;
  private final SlabLimits limits;
  private final long compiled;
  private final long result;
  private final int maxWireBytes;
  private int capacity;
  private boolean busy;

  /**
   * @param capacity the plan's learned first-attempt capacity (the production steady state: the
   *     fixture's largest slab extent), so every probe decode succeeds on its first attempt.
   */
  public CellProbe(BuffaWire decoder, ProtoPlan plan, int maxSlabBytes, int capacity) {
    if (!ImageInfo.inImageRuntimeCode())
      throw new UnsupportedOperationException("Cell probes are SVM-only");
    backend = java.util.Objects.requireNonNull(decoder.backend);
    this.plan = plan;
    limits = new SlabLimits(plan.maxDepth() - 1, maxSlabBytes);
    this.capacity = capacity;
    maxWireBytes = plan.maxWireBytes();
    // The schema program of BuffaPlan's native slab layout (binary Any payloads stay bytes).
    byte[] program =
        NativeSchema.encode(NativeSchema.types(plan, List.of(), 65536), List.of(), 65536);
    try (var schema = decoder.allocate(program.length).put(program)) {
      compiled = backend.compileLayout(schema);
    }
    VoidPointer slot = UnmanagedMemory.calloc(Buffa.SlabResult.SIZE);
    if (slot.isNull()) throw new OutOfMemoryError("Native result allocation");
    result = slot.rawValue();
  }

  public SlabLimits limits() {
    return limits;
  }

  /** The learned capacity after the last decode (production: {@code BuffaPlan.slabCapacity}). */
  public int capacity() {
    return capacity;
  }

  /**
   * The production heap-input path, partial-evaluation visible: budget check, busy flag, one zeroed
   * {@code byte[capacity]}, both arrays pinned around one ordinary {@code @CFunction} transition,
   * extent checks, the trim rule and the boundary publication.
   */
  public SlabMessage decode(byte[] input) {
    if (input.length > maxWireBytes) throw new dev.elide.truffledata.ContractViolation("Typed input budget");
    if (busy) throw new IllegalStateException("Native backend unavailable");
    busy = true;
    try {
      int capacity = this.capacity;
      byte[] output = new byte[capacity];
      int status;
      long bytes;
      VoidPointer slot = WordFactory.pointer(result);
      // Neither address escapes this call. Empty buffers use a valid non-null address and zero size.
      // Plain try/finally, not try-with-resources: javac's addSuppressed is on the runtime
      // compilation blocklist (measured: the only violation the first image build reported).
      PinnedObject pinnedInput = PinnedObject.create(input);
      try {
        PinnedObject pinnedOutput = PinnedObject.create(output);
        try {
          status =
              Buffa.decodeSlab(
                  WordFactory.pointer(compiled),
                  input.length == 0
                      ? WordFactory.pointer(result)
                      : pinnedInput.addressOfArrayElement(0),
                  input.length,
                  1,
                  WordFactory.nullPointer(),
                  pinnedOutput.addressOfArrayElement(0),
                  capacity,
                  limits.maxDepth(),
                  limits.maxSlabBytes(),
                  slot);
          bytes = Buffa.SlabResult.bytes(slot);
        } finally {
          pinnedOutput.close();
        }
      } finally {
        pinnedInput.close();
      }
      return publish(input, output, status, bytes);
    } finally {
      busy = false;
    }
  }

  /** The same body with the native call made through a Myna cell (glue or interop wrapper). */
  public SlabMessage decode(Invoker invoker, byte[] input) {
    if (input.length > maxWireBytes) throw new dev.elide.truffledata.ContractViolation("Typed input budget");
    if (busy) throw new IllegalStateException("Native backend unavailable");
    busy = true;
    try {
      int capacity = this.capacity;
      byte[] output = new byte[capacity];
      int status;
      long bytes;
      PinnedObject pinnedInput = PinnedObject.create(input);
      try {
        PinnedObject pinnedOutput = PinnedObject.create(output);
        try {
          // Pointer arguments as a guest holding native memory passes them: interop pointers lent
          // in place (Borrow.direct), integers as boxed values the coercions accept without interop.
          status =
              invoker.invoke(
                  new Object[] {
                    NativePointer.of(compiled),
                    NativePointer.of(
                        input.length == 0
                            ? result
                            : pinnedInput.addressOfArrayElement(0).rawValue()),
                    (long) input.length,
                    1,
                    NativePointer.NULL,
                    NativePointer.of(pinnedOutput.addressOfArrayElement(0).rawValue()),
                    (long) capacity,
                    limits.maxDepth(),
                    (long) limits.maxSlabBytes(),
                    NativePointer.of(result)
                  });
          bytes = Buffa.SlabResult.bytes(WordFactory.pointer(result));
        } finally {
          pinnedOutput.close();
        }
      } finally {
        pinnedInput.close();
      }
      return publish(input, output, status, bytes);
    } finally {
      busy = false;
    }
  }

  private SlabMessage publish(byte[] input, byte[] output, int status, long bytes) {
    if (status == 6 && bytes > output.length && bytes <= limits.maxSlabBytes()) {
      // A larger message than the learned capacity: the production retry, outside the fast path.
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return retry(input, Math.toIntExact(bytes));
    }
    BuffaWire.status(status);
    if (bytes < 0 || bytes > output.length)
      throw new IllegalStateException("Invalid native slab extent");
    int extent = (int) bytes;
    // The plan-wide learned capacity must not amplify small messages decoded after a large one:
    // spare room beyond the slab's own size (plus a small slack) is trimmed away.
    if (output.length - extent > Math.max(256, extent)) output = Arrays.copyOf(output, extent);
    return SlabMessage.takeOwnership(plan, output, extent, limits);
  }

  @TruffleBoundary
  private SlabMessage retry(byte[] input, int capacity) {
    this.capacity = Math.max(this.capacity, capacity);
    busy = false;
    try {
      return decode(input);
    } finally {
      busy = true;
    }
  }

  @Override
  public void close() {
    backend.releaseLayout(compiled);
    UnmanagedMemory.free(WordFactory.pointer(result));
  }
}
