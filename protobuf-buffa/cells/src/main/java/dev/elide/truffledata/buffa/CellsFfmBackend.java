package dev.elide.truffledata.buffa;

import dev.elide.myna.truffle.HeapBytes;
import dev.elide.myna.truffle.NativeCell;
import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;
import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.Limits;
import dev.elide.truffledata.buffa.cells.generated.BuffaCellsJvmFFM;
import dev.elide.truffledata.buffa.cells.generated.BuffaCellsJvmTruffle;
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
 * JVM is a correctness target; the cells copy heap arrays (Myna's HotSpot borrow rule), which the
 * FFM backend lends in place.
 *
 * <p>Myna's FFM cells bind their symbols through the loader lookup, so the library is also loaded
 * into this class loader ({@link System#load}). One library per class loader: a second path is
 * rejected rather than silently called through the first.
 */
@NullMarked
final class CellsFfmBackend implements BuffaWire.Backend {
  private static @Nullable Path loaded;

  private final FfmBackend backend;
  private final BuffaCellsJvmTruffle.decodeSlab_Cell decodeSlab;
  private final BuffaCellsJvmTruffle.decodeSlabSmall_Cell decodeSlabSmall;

  CellsFfmBackend(Path library) {
    backend = new FfmBackend(library);
    try {
      bind(library.toAbsolutePath());
      decodeSlab = adopt(new BuffaCellsJvmTruffle.decodeSlab_Cell());
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
      // The cells' own constant folds in the cells options; the library carries the production ABI.
      if (!actual.equals(BuffaFFM.ABI_FINGERPRINT))
        throw new IllegalStateException("Myna/Buffa ABI fingerprint mismatch");
      loaded = library;
    } else if (!loaded.equals(library))
      throw new IllegalStateException(
          "Buffa cells are bound to " + loaded + " in this class loader, not " + library);
  }

  private static <T extends NativeCell> T adopt(T cell) {
    // A cell holds call-site profiles and is adopted once, under its own language-neutral root.
    new NativeCellRootNode(cell).adoptChildren();
    return cell;
  }

  private static int call(
      NativeCell cell,
      long compiled,
      Object data,
      long length,
      int flags,
      long projection,
      Object out,
      long capacity,
      SlabLimits limits,
      MemorySegment result) {
    return (Integer)
        cell.execute(
            new Object[] {
              NativePointer.of(compiled),
              data,
              length,
              flags,
              NativePointer.of(projection),
              out,
              capacity,
              limits.maxDepth(),
              (long) limits.maxSlabBytes(),
              NativePointer.of(result.address())
            });
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
        return backend.produced(plan, output, Math.toIntExact(bytes), limits);
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
              call(
                  decodeSlab,
                  compiled,
                  NativePointer.of(memory.address()),
                  input.size(),
                  1,
                  0,
                  NativePointer.of(output.address()),
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
    long mask =
        projection == null
            ? 0
            : ((MemorySegment) java.util.Objects.requireNonNull(projection.lease.attachment))
                .address();
    MemorySegment slabResult = backend.beginSlab();
    try {
      int status =
          call(
              decodeSlab,
              ((MemorySegment) compiled).address(),
              NativePointer.of(source.address()),
              input.size(),
              flags,
              mask,
              NativePointer.of(destination.address()),
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
