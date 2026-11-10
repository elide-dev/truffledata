package dev.elide.truffledata.buffa;

import dev.elide.truffledata.Limits;
import dev.elide.truffledata.buffa.cells.generated.BuffaCellsTruffle;
import dev.elide.truffledata.buffa.generated.Buffa;
import dev.elide.truffledata.protobuf.ProtoPlan;
import dev.elide.truffledata.protobuf.SlabLimits;
import dev.elide.truffledata.protobuf.SlabMessage;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Native Image backend with the Myna Truffle cells route ({@code
 * -Dtruffledata.buffa.cells=true} at image build time, docs/BUFFA.md): every slab producer call
 * ({@code td_buffa_decode_slab}) goes through the generated {@code decodeSlab} cell instead of the
 * hand-written {@code @CFunction} call. Everything else is the static backend's: this wraps an
 * {@link SvmBackend}, shares its result slot and busy state through the backend's own hooks, and
 * keeps its exact control flow — budget checks, the learned-capacity retry, the critical-size
 * scratch fallback, the extent checks, the trim rule and the trusted publication through {@link
 * SlabMessage#produced} — so the slabs are byte-identical and the errors are the same, in the same
 * order. The static backend calls {@code decodeSlab} (never the leaf {@code decodeSlabSmall}) on
 * every entry, and so does this route: the two differ only in which generated binding they call.
 *
 * <p>This backend is host code, compiled ahead of time and called below the decode entries'
 * {@code @TruffleBoundary}, so it uses the cell's static {@code invoke} entry (Myna 0.16.0): native
 * carriers and raw addresses, with no cell instance, argument array, box, {@code NativePointer} or
 * {@code Borrow}. That entry allocates nothing; the cell's {@code execute} methods, which coerce
 * guest values and then call the same {@code invoke}, are for guest code and are not used here.
 * The caller keeps every address valid for the call: heap arrays are pinned around it with plain
 * {@code try/finally} (javac's try-with-resources reaches {@code Throwable.addSuppressed}, which
 * the runtime-compilation blocklist rejects), and native inputs, arenas and the result slot are
 * held by their leases and the backend for the duration of the call.
 */
@NullMarked
final class CellsSvmBackend implements BuffaWire.Backend {
  /** Registered by {@link BuffaFeature} when the image is built with the cells flag. */
  static final class Factory implements BuffaWire.NativeFactory {
    @Override
    public BuffaWire.Backend create() {
      return new CellsSvmBackend(new SvmBackend());
    }
  }

  private final SvmBackend backend;

  CellsSvmBackend(SvmBackend backend) {
    this.backend = backend;
  }

  /** {@code td_buffa_decode_slab} through the cell's static entry; arguments in seam order. */
  private static int decodeSlab(
      long compiled,
      long data,
      long length,
      int flags,
      long projection,
      long out,
      long capacity,
      SlabLimits limits,
      VoidPointer result) {
    return BuffaCellsTruffle.decodeSlab_Cell.invoke(
        compiled,
        data,
        length,
        flags,
        projection,
        out,
        capacity,
        limits.maxDepth(),
        limits.maxSlabBytes(),
        result.rawValue());
  }

  @Override
  public SlabMessage slab(
      long compiled, byte[] input, ProtoPlan plan, SlabLimits limits, int capacity) {
    if (limits.criticalBytes() == 0
        || input.length > limits.criticalBytes()
        || capacity > limits.criticalBytes())
      return BuffaWire.Backend.super.slab(compiled, input, plan, limits, capacity);
    VoidPointer result = backend.beginSlab();
    boolean scratch = false;
    try {
      for (int attempt = 0; attempt < 2; attempt++) {
        byte[] output = new byte[capacity];
        int status;
        // Neither address escapes this call. Empty buffers use a valid address and zero size.
        PinnedObject pinnedInput = PinnedObject.create(input);
        try {
          PinnedObject pinnedOutput = PinnedObject.create(output);
          try {
            status =
                decodeSlab(
                    compiled,
                    input.length == 0
                        ? backend.emptyInput()
                        : pinnedInput.addressOfArrayElement(0).rawValue(),
                    input.length,
                    1,
                    0,
                    capacity == 0 ? 0 : pinnedOutput.addressOfArrayElement(0).rawValue(),
                    capacity,
                    limits,
                    result);
          } finally {
            pinnedOutput.close();
          }
        } finally {
          pinnedInput.close();
        }
        long bytes = Buffa.SlabResult.bytes(result);
        if (status == 6 && attempt == 0 && bytes > capacity && bytes <= limits.maxSlabBytes()) {
          capacity = Math.toIntExact(bytes);
          if (capacity > limits.criticalBytes()) {
            scratch = true;
            break;
          }
          continue;
        }
        BuffaWire.status(status);
        if (bytes < 0 || bytes > capacity)
          throw new IllegalStateException("Invalid native slab extent");
        int extent = Math.toIntExact(bytes);
        if (output.length - extent > Math.max(256, extent))
          output = java.util.Arrays.copyOf(output, extent);
        return SvmBackend.produced(plan, output, extent, limits, result);
      }
    } finally {
      backend.endSlab();
    }
    if (scratch) return BuffaWire.Backend.super.slab(compiled, input, plan, limits, capacity);
    throw new IllegalStateException("Native slab capacity retry failed");
  }

  @Override
  public SlabMessage slab(
      long compiled, WireInput input, ProtoPlan plan, SlabLimits limits, int capacity) {
    if (!(input.lease.attachment instanceof BuffaSvm.Address address))
      throw new IllegalArgumentException("Input is not SVM native memory");
    VoidPointer result = backend.beginSlab();
    try {
      for (int attempt = 0; attempt < 2; attempt++) {
        byte[] output = new byte[Math.max(1, capacity)];
        int status;
        long bytes;
        PinnedObject pinned = PinnedObject.create(output);
        try {
          status =
              decodeSlab(
                  compiled,
                  address.value(),
                  input.size(),
                  1,
                  0,
                  pinned.addressOfArrayElement(0).rawValue(),
                  capacity,
                  limits,
                  result);
          bytes = Buffa.SlabResult.bytes(result);
        } finally {
          pinned.close();
        }
        if (status == 6 && attempt == 0 && bytes > capacity && bytes <= limits.maxSlabBytes()) {
          capacity = Math.toIntExact(bytes);
          continue;
        }
        BuffaWire.status(status);
        if (bytes < 0 || bytes > capacity)
          throw new IllegalStateException("Invalid native slab extent");
        int extent = Math.toIntExact(bytes);
        if (output.length - extent > Math.max(256, extent))
          output = java.util.Arrays.copyOf(output, extent);
        return SvmBackend.produced(plan, output, extent, limits, result);
      }
      throw new IllegalStateException("Native slab capacity retry failed");
    } finally {
      backend.endSlab();
    }
  }

  @Override
  public int writeSlab(
      Object compiled,
      WireInput input,
      WireInput output,
      SlabLimits limits,
      int flags,
      @Nullable WireInput projection) {
    if (!(input.lease.attachment instanceof BuffaSvm.Address source)
        || !(output.lease.attachment instanceof BuffaSvm.Address destination))
      throw new IllegalArgumentException("Slab arena is not SVM native memory");
    VoidPointer result = backend.beginSlab();
    try {
      int status =
          decodeSlab(
              (Long) compiled,
              source.value(),
              input.size(),
              flags,
              projection == null
                  ? 0
                  : ((BuffaSvm.Address)
                          java.util.Objects.requireNonNull(projection.lease.attachment))
                      .value(),
              destination.value(),
              output.size(),
              limits,
              result);
      long bytes = Buffa.SlabResult.bytes(result);
      if (status == 6 && bytes > output.size() && bytes <= limits.maxSlabBytes())
        return -Math.toIntExact(bytes);
      BuffaWire.status(status);
      if (bytes < 0 || bytes > output.size())
        throw new IllegalStateException("Invalid slab extent");
      return Math.toIntExact(bytes);
    } finally {
      backend.endSlab();
    }
  }

  // Everything but the slab producer call is the static backend's own.

  @Override
  public WireInput allocate(int size) {
    return backend.allocate(size);
  }

  @Override
  public BuffaWire.Tape decode(WireInput input, Limits limits, int depth, int nativeBytes) {
    return backend.decode(input, limits, depth, nativeBytes);
  }

  @Override
  public BuffaWire.Tape typed(
      WireInput schema,
      WireInput input,
      int format,
      WireInput payload,
      ProtoPlan plan,
      int nativeBytes) {
    return backend.typed(schema, input, format, payload, plan, nativeBytes);
  }

  @Override
  public long compileLayout(WireInput schema) {
    return backend.compileLayout(schema);
  }

  @Override
  public void releaseLayout(long compiled) {
    backend.releaseLayout(compiled);
  }

  @Override
  public Object prepareSlab(long compiled) {
    return backend.prepareSlab(compiled);
  }

  @Override
  public void recordSlabInputCopy(int bytes) {
    backend.recordSlabInputCopy(bytes);
  }

  @Override
  public long copiedSlabInputBytes() {
    return backend.copiedSlabInputBytes();
  }

  @Override
  public void close() {
    backend.close();
  }
}
