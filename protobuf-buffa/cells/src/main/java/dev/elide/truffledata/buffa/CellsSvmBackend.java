package dev.elide.truffledata.buffa;

import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;
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
 * order.
 *
 * <p>The cell is one adopted node per backend (a backend is confined to its decoder's thread),
 * called directly from the {@code @TruffleBoundary} decode entries, as the WS11 probe's {@code
 * cell} route did. Heap arrays are pinned here, around the call, with plain {@code try/finally}
 * (javac's try-with-resources reaches {@code Throwable.addSuppressed}, which the
 * runtime-compilation blocklist rejects), and lent to the cell as pointers.
 *
 * <p>Byte regions cross as {@link NativePointer}, not Myna's buffer objects, on purpose. {@code
 * HeapBytes} would make Myna copy heap arrays below {@code Borrow.pinThreshold()} (256 B by
 * default) into scratch memory and copy the output back, where the static backend pins them in
 * place. {@code NativeBytes.of(address, size)} builds a {@code MemorySegment} view per call, more
 * allocation than a pointer, and none of these imports has a {@code borrow} declaration (Myna's
 * borrows take one buffer and scalar arguments only), so the cell uses only the address either way.
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
  private final BuffaCellsTruffle.decodeSlab_Cell decodeSlab;

  // Per-call argument cost. NativeCell's only entries take an Object[] of guest values, so each
  // call needs an argument array, a NativePointer per pointer and a box per scalar outside the
  // JDK's small-value caches. This call runs in AOT-compiled host code below a @TruffleBoundary,
  // not in partially evaluated guest code, so none of that is scalar-replaced unless the AOT
  // inliner happens to inline the whole cell (CellsAcceptance prints the measured bytes per call).
  // What does not change from call to call is reused: one argument array per backend (the cell only
  // reads arguments[i] into its coercions and keeps nothing; a backend is confined to its decoder's
  // thread and beginSlab's busy check excludes reentry), and one cached pointer or box per slot
  // whose value repeats: the compiled layout, the projection mask, the backend's own result slot and
  // the limits always; the input and output addresses, the input length and the capacity when the
  // caller repeats them (a slab session's arena and its capacity are stable from call to call, and
  // an input buffer is often reused; the heap entries pin fresh arrays, so their addresses miss).
  // A cached pointer is only the address in a box: it is compared with the current address before
  // every use and nothing reads through it, so it extends no borrow and keeps no memory reachable.
  // Each slot still varies freely: a changed value is boxed afresh, never reused stale. What
  // remains per call is the cell's own Borrow objects for its byte-pointer parameters (Myna's
  // BytesCoercion: compiled, data and a non-null out), which Myna allocates internally.
  private final Object[] arguments = new Object[10];
  private NativePointer compiledPointer = NativePointer.NULL;
  private NativePointer dataPointer = NativePointer.NULL;
  private NativePointer projectionPointer = NativePointer.NULL;
  private NativePointer outPointer = NativePointer.NULL;
  private NativePointer resultPointer = NativePointer.NULL;
  private Long lengthBox = 0L;
  private Long capacityBox = 0L;
  private Integer maxDepth = 0;
  private Long maxSlabBytes = 0L;

  CellsSvmBackend(SvmBackend backend) {
    this.backend = backend;
    decodeSlab = new BuffaCellsTruffle.decodeSlab_Cell();
    // A cell holds call-site profiles and is adopted once, under its own language-neutral root.
    new NativeCellRootNode(decodeSlab).adoptChildren();
  }

  /** {@code td_buffa_decode_slab} through the cell; arguments in seam order. */
  private int decodeSlab(
      long compiled,
      long data,
      long length,
      int flags,
      long projection,
      long out,
      long capacity,
      SlabLimits limits,
      VoidPointer result) {
    if (compiledPointer.address() != compiled) compiledPointer = NativePointer.of(compiled);
    if (dataPointer.address() != data) dataPointer = NativePointer.of(data);
    if (projectionPointer.address() != projection) projectionPointer = NativePointer.of(projection);
    if (outPointer.address() != out) outPointer = NativePointer.of(out);
    if (lengthBox != length) lengthBox = length;
    if (capacityBox != capacity) capacityBox = capacity;
    if (resultPointer.address() != result.rawValue())
      resultPointer = NativePointer.of(result.rawValue());
    if (maxDepth != limits.maxDepth()) maxDepth = limits.maxDepth();
    if (maxSlabBytes != limits.maxSlabBytes()) maxSlabBytes = (long) limits.maxSlabBytes();
    Object[] arguments = this.arguments;
    arguments[0] = compiledPointer;
    arguments[1] = dataPointer;
    arguments[2] = lengthBox;
    arguments[3] = flags;
    arguments[4] = projectionPointer;
    arguments[5] = outPointer;
    arguments[6] = capacityBox;
    arguments[7] = maxDepth;
    arguments[8] = maxSlabBytes;
    arguments[9] = resultPointer;
    // Specialized call (Myna 0.14.0): the concrete cell's i32 result overrides executeInt, so
    // there is no result box and no UnexpectedResultException handler.
    return decodeSlab.executeInt(arguments);
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
