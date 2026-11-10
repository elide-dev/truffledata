package dev.elide.truffledata.buffa;

import dev.elide.myna.truffle.HeapBytes;
import dev.elide.myna.truffle.NativeCell;
import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;
import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.Limits;
import dev.elide.truffledata.TargetAbi;
import dev.elide.truffledata.buffa.cells.generated.BuffaCellsJvmFFM;
import dev.elide.truffledata.buffa.cells.generated.BuffaCellsJvmTruffle;
import dev.elide.truffledata.buffa.generated.BuffaAbi;
import dev.elide.truffledata.buffa.generated.BuffaFFM;
import dev.elide.truffledata.protobuf.ProtoPlan;
import dev.elide.truffledata.protobuf.SlabLimits;
import dev.elide.truffledata.protobuf.SlabMessage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The HotSpot backend with the Myna Truffle cells route ({@code -Dtruffledata.buffa.cells=true},
 * read once by {@link CellsRoute}; docs/BUFFA.md): every slab producer call goes through a cell
 * generated with {@code truffle_backend=ffm} — {@code decodeSlabSmall} for bounded heap input,
 * {@code decodeSlab} otherwise, the same split the FFM backend makes. Everything else is the FFM
 * backend's: this wraps an {@link FfmBackend}, which loads and fingerprint-checks the library
 * first, shares its result slot and busy state through the backend's own hooks, and keeps its
 * control flow, so the slabs are byte-identical and the errors are the same, in the same order. The
 * JVM is a correctness target.
 *
 * <p>Two call shapes. Where every buffer is native memory (a {@link WireInput} input, the arena of
 * {@link #writeSlab}, the confined output of the native-input {@link #slab}), the route calls the
 * {@code decodeSlab} cell's static {@code invoke} entry (Myna 0.16.0) with raw addresses, after the
 * same thread and liveness checks the FFM backend's downcall makes ({@link #live}): no argument
 * array, box, {@code NativePointer} or {@code Borrow}, and the caller keeps every address valid
 * for the call. The heap-array entry ({@link #slab(long, byte[], ProtoPlan, SlabLimits, int)})
 * stays on the {@code decodeSlabSmall} cell's typed {@code executeInt}: {@code invoke} takes only
 * raw addresses, and a heap array has no stable address on HotSpot, so lending one needs Myna's
 * borrow ({@link HeapBytes}, which copies it to scratch memory and copies the output back; the FFM
 * backend lends it in place).
 *
 * <p>Myna's FFM cells bind their symbols through the loader lookup, so the library is also loaded
 * into this class loader ({@link System#load}). One library per class loader: a second path is
 * rejected rather than silently called through the first.
 */
@NullMarked
final class CellsFfmBackend implements BuffaWire.Backend {
  private static @Nullable Path loaded;

  private final FfmBackend backend;
  private final BuffaCellsJvmTruffle.decodeSlabSmall_Cell decodeSlabSmall;

  CellsFfmBackend(Path library) {
    backend = new FfmBackend(library);
    try {
      bind(library.toAbsolutePath());
      decodeSlabSmall = adopt(new BuffaCellsJvmTruffle.decodeSlabSmall_Cell());
    } catch (RuntimeException | Error failure) {
      backend.close();
      throw failure;
    }
  }

  private static synchronized void bind(Path library) {
    if (loaded == null) {
      System.load(library.toString());
      String actual;
      try {
        actual =
            new String(
                BuffaCellsJvmFFM.fingerprint().reinterpret(64).toArray(ValueLayout.JAVA_BYTE),
                StandardCharsets.US_ASCII);
      } catch (Throwable failure) {
        throw FfmBackend.callFailure(failure);
      }
      // The cells' own constant folds in the cells options; the library carries the production ABI
      // of the host's class (Myna 0.14.0: Linux AArch64 has its own); never a fallback.
      if (!actual.equals(BuffaAbi.fingerprint(TargetAbi.HOST.charUnsigned())))
        throw new IllegalStateException(
            "Myna/Buffa ABI fingerprint mismatch (expected the " + TargetAbi.HOST + " class)");
      loaded = library;
    } else if (!loaded.equals(library))
      throw new IllegalStateException(
          "Buffa cells are bound to " + loaded + " in this class loader, not " + library);
  }

  private static <T extends NativeCell> T adopt(T created) {
    // A cell holds call-site profiles and is adopted once, under its own language-neutral root.
    new NativeCellRootNode(created).adoptChildren();
    return created;
  }

  // Per-call argument cost of the heap entry's typed call: one argument array per backend (the
  // cell only reads arguments[i] into its coercions; a backend is confined to its decoder's thread
  // and beginSlab's busy check excludes reentry), and one cached pointer or box per slot whose
  // value repeats. The buffer slots are cleared after each call, so the array never keeps a
  // caller's input or a produced slab reachable.
  private final @Nullable Object[] arguments = new Object[10];
  private NativePointer compiledPointer = NativePointer.NULL;
  private NativePointer projectionPointer = NativePointer.NULL;
  private NativePointer resultPointer = NativePointer.NULL;
  private Integer maxDepth = 0;
  private Long maxSlabBytes = 0L;

  /**
   * The raw address of a native region for the cell's {@code invoke}, after the check the FFM
   * backend's downcall makes on every segment it is handed and {@code invoke} cannot make: it takes
   * the raw address, so a region whose scope was closed behind an adopted input ({@link
   * BuffaJvm#adopt}: "region scope remains checked on every access"), or that is confined to
   * another thread, would otherwise reach native code as a dangling pointer. Same exceptions as the
   * downcall, in its order (thread, then liveness), raised before the call like the downcall's own.
   * The downcall additionally keeps a shared scope open for the duration of the call; {@code
   * invoke} has no way to (it takes an address and tracks no caller's scope), so a shared arena
   * closed by another thread during the call is the one case this cannot reject. CellsParity
   * compares both failures with the FFM backend's.
   */
  private static long live(MemorySegment region) {
    if (!region.isAccessibleBy(Thread.currentThread()))
      throw new WrongThreadException("Attempted access outside owning thread");
    if (!region.scope().isAlive()) throw new IllegalStateException("Already closed");
    return region.address();
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
      MemorySegment result) {
    return BuffaCellsJvmTruffle.decodeSlab_Cell.invoke(
        compiled,
        data,
        length,
        flags,
        projection,
        out,
        capacity,
        limits.maxDepth(),
        limits.maxSlabBytes(),
        result.address());
  }

  // Specialized NativeCell call (Myna 0.14.0): the cell is held as its concrete generated type,
  // whose i32 result overrides executeInt with no box and no UnexpectedResultException.
  private int call(
      BuffaCellsJvmTruffle.decodeSlabSmall_Cell cell,
      long compiled,
      Object data,
      long length,
      int flags,
      long projection,
      Object out,
      long capacity,
      SlabLimits limits,
      MemorySegment result) {
    @Nullable Object[] arguments =
        arguments(compiled, data, length, flags, projection, out, capacity, limits, result);
    try {
      return cell.executeInt(arguments);
    } finally {
      arguments[1] = null;
      arguments[5] = null;
    }
  }

  private @Nullable Object[] arguments(
      long compiled,
      Object data,
      long length,
      int flags,
      long projection,
      Object out,
      long capacity,
      SlabLimits limits,
      MemorySegment result) {
    if (compiledPointer.address() != compiled) compiledPointer = NativePointer.of(compiled);
    if (projectionPointer.address() != projection) projectionPointer = NativePointer.of(projection);
    if (resultPointer.address() != result.address())
      resultPointer = NativePointer.of(result.address());
    if (maxDepth != limits.maxDepth()) maxDepth = limits.maxDepth();
    if (maxSlabBytes != limits.maxSlabBytes()) maxSlabBytes = (long) limits.maxSlabBytes();
    @Nullable Object[] arguments = this.arguments;
    arguments[0] = compiledPointer;
    arguments[1] = data;
    arguments[2] = length;
    arguments[3] = flags;
    arguments[4] = projectionPointer;
    arguments[5] = out;
    arguments[6] = capacity;
    arguments[7] = maxDepth;
    arguments[8] = maxSlabBytes;
    arguments[9] = resultPointer;
    return arguments;
  }

  @Override
  public SlabMessage slab(
      long compiled, byte[] input, ProtoPlan plan, SlabLimits limits, int capacity) {
    if (limits.criticalBytes() == 0
        || input.length > limits.criticalBytes()
        || capacity > limits.criticalBytes())
      return BuffaWire.Backend.super.slab(compiled, input, plan, limits, capacity);
    MemorySegment slabResult = backend.beginSlab();
    boolean scratch = false;
    try {
      for (int attempt = 0; attempt < 2; attempt++) {
        byte[] output = new byte[capacity];
        int status =
            call(
                decodeSlabSmall,
                compiled,
                new HeapBytes(input, false),
                input.length,
                1,
                0,
                new HeapBytes(output, true),
                capacity,
                limits,
                slabResult);
        long bytes = slabResult.get(ValueLayout.JAVA_LONG, BuffaFFM.SlabResult_bytes_OFFSET);
        // Fixed stack scratch can overflow within the size bound; retry the same native
        // producer with its ordinary transition and bounded dynamic scratch, never Java.
        if (status == 2) {
          scratch = true;
          break;
        }
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
        // The same trim rule as FfmBackend: no high-water amplification of small messages.
        if (output.length - extent > Math.max(256, extent))
          output = java.util.Arrays.copyOf(output, extent);
        return backend.produced(plan, output, extent, limits);
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
    if (!(input.lease.attachment instanceof MemorySegment memory))
      throw new IllegalArgumentException("Input is not JVM native memory");
    MemorySegment slabResult = backend.beginSlab();
    try {
      for (int attempt = 0; attempt < 2; attempt++) {
        try (Arena arena = Arena.ofConfined()) {
          MemorySegment output = arena.allocate(Math.max(1, capacity), 8);
          int status =
              decodeSlab(
                  compiled,
                  live(memory),
                  input.size(),
                  1,
                  0,
                  live(output),
                  capacity,
                  limits,
                  slabResult);
          long bytes = slabResult.get(ValueLayout.JAVA_LONG, BuffaFFM.SlabResult_bytes_OFFSET);
          if (status == 6 && attempt == 0 && bytes > capacity && bytes <= limits.maxSlabBytes()) {
            capacity = Math.toIntExact(bytes);
            continue;
          }
          BuffaWire.status(status);
          if (bytes < 0 || bytes > capacity)
            throw new IllegalStateException("Invalid native slab extent");
          if (bytes > limits.maxSlabBytes())
            throw new ContractViolation("Slab byte budget exceeded");
          int extent = Math.toIntExact(bytes);
          byte[] owned = new byte[extent];
          MemorySegment.copy(output, ValueLayout.JAVA_BYTE, 0, owned, 0, extent);
          return backend.produced(plan, owned, extent, limits);
        }
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
    if (!(input.lease.attachment instanceof MemorySegment source)
        || !(output.lease.attachment instanceof MemorySegment destination))
      throw new IllegalArgumentException("Slab arena is not JVM native memory");
    MemorySegment mask =
        projection == null
            ? MemorySegment.NULL
            : (MemorySegment) java.util.Objects.requireNonNull(projection.lease.attachment);
    MemorySegment slabResult = backend.beginSlab();
    try {
      // Argument order is check order: source, mask, destination, as the typed call checked them.
      int status =
          decodeSlab(
              ((MemorySegment) compiled).address(),
              live(source),
              input.size(),
              flags,
              live(mask),
              live(destination),
              output.size(),
              limits,
              slabResult);
      long bytes = slabResult.get(ValueLayout.JAVA_LONG, BuffaFFM.SlabResult_bytes_OFFSET);
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

  // Everything but the slab producer call is the FFM backend's own.

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
