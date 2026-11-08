package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives;
import dev.elide.truffledata.Limits;
import dev.elide.truffledata.buffa.generated.BuffaFFM;
import dev.elide.truffledata.protobuf.ProtoPlan;
import dev.elide.truffledata.protobuf.SlabLimits;
import dev.elide.truffledata.protobuf.SlabMessage;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** JVM-only adapter. Its arena owns the library, not input buffers or returned views. */
final class FfmBackend implements BuffaWire.Backend {
  // Derive the JVM's concrete FFM buffer class without retaining an arena or native allocation.
  private static final Class<? extends ByteBuffer> INDEX_BUFFER_TYPE =
      MemorySegment.NULL.asByteBuffer().getClass();

  private static final java.lang.invoke.VarHandle SLAB_INT =
      ValueLayout.JAVA_INT_UNALIGNED.withOrder(java.nio.ByteOrder.LITTLE_ENDIAN).varHandle();
  private static final java.lang.invoke.VarHandle SLAB_LONG =
      ValueLayout.JAVA_LONG_UNALIGNED.withOrder(java.nio.ByteOrder.LITTLE_ENDIAN).varHandle();
  private static final java.lang.invoke.VarHandle SLAB_BYTE = ValueLayout.JAVA_BYTE.varHandle();

  static long nativeAddress(WireInput memory) {
    return ((MemorySegment) Objects.requireNonNull(memory.lease.attachment)).address();
  }

  @CompilerDirectives.TruffleBoundary(allowInlining = true)
  static int readAddressInt(long address) {
    return (int) SLAB_INT.get(MemorySegment.ofAddress(address).reinterpret(4), 0L);
  }

  @CompilerDirectives.TruffleBoundary(allowInlining = true)
  static long readAddressLong(long address) {
    return (long) SLAB_LONG.get(MemorySegment.ofAddress(address).reinterpret(8), 0L);
  }

  @CompilerDirectives.TruffleBoundary(allowInlining = true)
  static int readAddressByte(long address) {
    return ((byte) SLAB_BYTE.get(MemorySegment.ofAddress(address).reinterpret(1), 0L)) & 255;
  }

  static long readIndexLong(ByteBuffer buffer, int offset) {
    if (buffer.getClass() == INDEX_BUFFER_TYPE)
      return CompilerDirectives.castExact(buffer, INDEX_BUFFER_TYPE).getLong(offset);
    return readOtherIndexLong(buffer, offset);
  }

  @CompilerDirectives.TruffleBoundary
  private static long readOtherIndexLong(ByteBuffer buffer, int offset) {
    return buffer.getLong(offset);
  }

  private final Arena libraryArena = Arena.ofConfined();
  private final MethodHandle decode;
  private final MethodHandle release;
  private final MethodHandle typed;
  private final MethodHandle scalars;
  private final MethodHandle compileView;
  private final MethodHandle releaseCompiled;
  private final MethodHandle decodeView;
  private final MethodHandle releaseView;
  private final MethodHandle compileLayout;
  private final MethodHandle releaseLayout;
  private final MethodHandle decodeSlab;
  private final MethodHandle decodeSlabSmall;
  private final MemorySegment slabResult;
  private final MemorySegment scalarResult;
  private final MemorySegment result;
  private final MemorySegment viewResult;
  private long copiedSlabInputBytes;

  @Override
  public void recordSlabInputCopy(int bytes) {
    copiedSlabInputBytes += bytes;
  }

  @Override
  public long copiedSlabInputBytes() {
    return copiedSlabInputBytes;
  }

  private boolean busy;
  private boolean cached;
  private boolean closed;
  // Live compiled plans and views keep the library mapped after this backend closes.
  private int detached;
  private final BuffaWire.Scalars scalarValues = new BuffaWire.Scalars();
  private final Runnable releaseAction = this::releaseResult;
  private final Runnable retainAction = this::retainResult;

  private MemorySegment acquireResult() {
    if (closed || busy || !libraryArena.scope().isAlive())
      throw new IllegalStateException("Native backend unavailable");
    busy = true;
    return result;
  }

  private void releaseResult() {
    if (!busy) throw new IllegalStateException("Native result already released");
    try {
      free(result);
      cached = false;
    } finally {
      busy = false;
    }
  }

  private void retainResult() {
    if (!busy) throw new IllegalStateException("Native result already released");
    cached = true;
    busy = false;
  }

  FfmBackend(Path library) {
    try {
      var symbols = SymbolLookup.libraryLookup(library.toAbsolutePath(), libraryArena);
      var fingerprint = BuffaFFM.fingerprint(symbols, true);
      MemorySegment address;
      try {
        address = (MemorySegment) fingerprint.invokeExact();
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      var actual =
          new String(
              address.reinterpret(64).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.US_ASCII);
      if (!actual.equals(BuffaFFM.ABI_FINGERPRINT))
        throw new IllegalStateException("Myna/Buffa ABI fingerprint mismatch");
      decode = BuffaFFM.decode(symbols);
      release = BuffaFFM.release(symbols);
      typed = BuffaFFM.typedReuse(symbols);
      scalars = BuffaFFM.scalars(symbols);
      compileView = BuffaFFM.compileView(symbols);
      releaseCompiled = BuffaFFM.releaseCompiled(symbols);
      decodeView = BuffaFFM.decodeView(symbols);
      releaseView = BuffaFFM.releaseView(symbols);
      compileLayout = BuffaFFM.compileLayout(symbols);
      releaseLayout = BuffaFFM.releaseLayout(symbols);
      decodeSlab = BuffaFFM.decodeSlab(symbols);
      // Myna's generated critical binding uses critical(false). Heap arrays need the explicit
      // allow-heap variant, using that same generated ABI descriptor. Calls are bounded below.
      decodeSlabSmall =
          Linker.nativeLinker()
              .downcallHandle(
                  symbols.find("td_buffa_decode_slab_small").orElseThrow(),
                  BuffaFFM.decodeSlabSmall_DESCRIPTOR,
                  Linker.Option.critical(true));
      slabResult = libraryArena.allocate(BuffaFFM.SlabResult_LAYOUT);
      scalarResult = libraryArena.allocate(BuffaFFM.Scalars_LAYOUT);
      result = libraryArena.allocate(BuffaFFM.Result_LAYOUT);
      viewResult = libraryArena.allocate(BuffaFFM.View_LAYOUT);
    } catch (RuntimeException | Error failure) {
      libraryArena.close();
      throw failure;
    }
  }

  @Override
  public WireInput allocate(int size) {
    var arena = Arena.ofConfined();
    try {
      var memory = arena.allocate(Math.max(1, size));
      return new WireInput(memory.asSlice(0, size).asByteBuffer(), arena::close, memory);
    } catch (RuntimeException | Error failure) {
      arena.close();
      throw failure;
    }
  }

  @Override
  public BuffaWire.Tape decode(WireInput input, Limits limits, int depth, int nativeBytes) {
    if (!(input.lease.attachment instanceof MemorySegment memory))
      throw new IllegalArgumentException("Input is not JVM native memory");
    MemorySegment result = acquireResult();
    try {
      if (cached) {
        free(result);
        cached = false;
      }
      int status;
      try {
        status =
            (int)
                decode.invokeExact(
                    memory,
                    (long) input.size(),
                    limits.maxCells(),
                    depth,
                    (long) nativeBytes,
                    result);
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      BuffaWire.status(status);
      long count = result.get(ValueLayout.JAVA_LONG, BuffaFFM.Result_count_OFFSET),
          capacity = result.get(ValueLayout.JAVA_LONG, BuffaFFM.Result_capacity_OFFSET);
      ByteBuffer tape =
          count == 0
              ? ByteBuffer.allocate(0)
              : result
                  .get(ValueLayout.ADDRESS, BuffaFFM.Result_data_OFFSET)
                  .reinterpret(
                      Math.multiplyExact(count, BuffaFFM.Field_LAYOUT.byteSize()),
                      libraryArena,
                      null)
                  .asByteBuffer();
      return new BuffaWire.Tape(tape, count, capacity, releaseAction);
    } catch (RuntimeException | Error failure) {
      releaseResult();
      throw failure;
    }
  }

  @Override
  public BuffaWire.Tape typed(
      WireInput schema,
      WireInput input,
      int format,
      WireInput payload,
      dev.elide.truffledata.protobuf.ProtoPlan plan,
      int nativeBytes) {
    if (!(schema.lease.attachment instanceof MemorySegment schemaMemory)
        || !(input.lease.attachment instanceof MemorySegment inputMemory)
        || !(payload.lease.attachment instanceof MemorySegment payloadMemory))
      throw new IllegalArgumentException("Inputs are not JVM native memory");
    MemorySegment result = acquireResult();
    try {
      int status;
      try {
        status =
            (int)
                typed.invokeExact(
                    schemaMemory,
                    (long) schema.size(),
                    inputMemory,
                    (long) input.size(),
                    format,
                    payloadMemory,
                    (long) payload.size(),
                    plan.limits().maxCells(),
                    plan.limits().maxStringBytes(),
                    plan.maxDepth(),
                    (long) nativeBytes,
                    result);
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      boolean viewReady = BuffaWire.typedStatus(status, format);
      long count = result.get(ValueLayout.JAVA_LONG, BuffaFFM.Result_count_OFFSET);
      long capacity = result.get(ValueLayout.JAVA_LONG, BuffaFFM.Result_capacity_OFFSET);
      ByteBuffer tape =
          count == 0
              ? ByteBuffer.allocate(0)
              : result
                  .get(ValueLayout.ADDRESS, BuffaFFM.Result_data_OFFSET)
                  .reinterpret(
                      Math.multiplyExact(count, BuffaFFM.Field_LAYOUT.byteSize()),
                      libraryArena,
                      null)
                  .asByteBuffer();
      return new BuffaWire.Tape(tape, count, capacity, retainAction, viewReady, 0);
    } catch (RuntimeException | Error failure) {
      releaseResult();
      throw failure;
    }
  }

  @Override
  public BuffaWire.Scalars scalars(
      WireInput schema, WireInput input, dev.elide.truffledata.protobuf.ProtoPlan plan) {
    if (!(schema.lease.attachment instanceof MemorySegment schemaMemory)
        || !(input.lease.attachment instanceof MemorySegment inputMemory))
      throw new IllegalArgumentException("Inputs are not JVM native memory");
    acquireResult();
    try {
      int status;
      try {
        status =
            (int)
                scalars.invokeExact(
                    schemaMemory,
                    (long) schema.size(),
                    inputMemory,
                    (long) input.size(),
                    plan.limits().maxCells(),
                    plan.maxDepth(),
                    scalarResult);
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      BuffaWire.status(status);
      return scalarValues.set(
          scalarResult.get(ValueLayout.JAVA_LONG, BuffaFFM.Scalars_first_OFFSET),
          scalarResult.get(ValueLayout.JAVA_LONG, BuffaFFM.Scalars_second_OFFSET),
          scalarResult.get(ValueLayout.JAVA_LONG, BuffaFFM.Scalars_present_OFFSET));
    } finally {
      busy = false;
    }
  }

  @Override
  public long compileView(WireInput schema) {
    if (!(schema.lease.attachment instanceof MemorySegment schemaMemory))
      throw new IllegalArgumentException("Schema is not JVM native memory");
    acquireResult();
    try {
      int status;
      try {
        status = (int) compileView.invokeExact(schemaMemory, (long) schema.size(), viewResult);
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      BuffaWire.status(status);
      long handle = viewResult.get(ValueLayout.ADDRESS, BuffaFFM.Result_data_OFFSET).address();
      if (handle == 0) throw new IllegalStateException("Native view compilation returned null");
      detached++;
      return handle;
    } finally {
      busy = false;
    }
  }

  @Override
  public void releaseCompiled(long compiled) {
    try {
      releaseCompiled.invokeExact(MemorySegment.ofAddress(compiled));
    } catch (Throwable failure) {
      throw callFailure(failure);
    } finally {
      detach();
    }
  }

  @Override
  public long compileLayout(WireInput schema) {
    if (!(schema.lease.attachment instanceof MemorySegment memory))
      throw new IllegalArgumentException("Schema is not JVM native memory");
    acquireResult();
    try {
      int status;
      try {
        status = (int) compileLayout.invokeExact(memory, (long) schema.size(), slabResult);
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      BuffaWire.status(status);
      long handle = slabResult.get(ValueLayout.ADDRESS, BuffaFFM.Result_data_OFFSET).address();
      if (handle == 0) throw new IllegalStateException("Native layout compilation returned null");
      detached++;
      return handle;
    } finally {
      busy = false;
    }
  }

  @Override
  public void releaseLayout(long compiled) {
    try {
      releaseLayout.invokeExact(MemorySegment.ofAddress(compiled));
    } catch (Throwable failure) {
      throw callFailure(failure);
    } finally {
      detach();
    }
  }

  @Override
  public Object prepareSlab(long compiled) {
    return MemorySegment.ofAddress(compiled);
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
        projection == null ? MemorySegment.NULL : (MemorySegment) projection.lease.attachment;
    acquireResult();
    try {
      int status;
      try {
        status =
            (int)
                decodeSlab.invokeExact(
                    (MemorySegment) compiled,
                    source,
                    (long) input.size(),
                    flags,
                    mask,
                    destination,
                    (long) output.size(),
                    limits.maxDepth(),
                    (long) limits.maxSlabBytes(),
                    slabResult);
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      long bytes = slabResult.get(ValueLayout.JAVA_LONG, BuffaFFM.SlabResult_bytes_OFFSET);
      if (status == 6 && bytes > output.size() && bytes <= limits.maxSlabBytes())
        return -Math.toIntExact(bytes);
      BuffaWire.status(status);
      if (bytes < 0 || bytes > output.size())
        throw new IllegalStateException("Invalid slab extent");
      return Math.toIntExact(bytes);
    } finally {
      busy = false;
    }
  }

  @Override
  public SlabMessage slab(
      long compiled, byte[] input, ProtoPlan plan, SlabLimits limits, int capacity) {
    if (limits.criticalBytes() == 0
        || input.length > limits.criticalBytes()
        || capacity > limits.criticalBytes())
      return BuffaWire.Backend.super.slab(compiled, input, plan, limits, capacity);
    acquireResult();
    boolean scratch = false;
    try {
      for (int attempt = 0; attempt < 2; attempt++) {
        byte[] output = new byte[capacity];
        int status;
        MemorySegment inputMemory =
            input.length == 0 ? slabResult.asSlice(0, 0) : MemorySegment.ofArray(input);
        try {
          status =
              (int)
                  decodeSlabSmall.invokeExact(
                      MemorySegment.ofAddress(compiled),
                      inputMemory,
                      (long) input.length,
                      1,
                      MemorySegment.NULL,
                      MemorySegment.ofArray(output),
                      (long) capacity,
                      limits.maxDepth(),
                      (long) limits.maxSlabBytes(),
                      slabResult);
        } catch (Throwable failure) {
          throw callFailure(failure);
        }
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
        return SlabMessage.takeOwnership(plan, output, Math.toIntExact(bytes), limits);
      }
    } finally {
      busy = false;
    }
    if (scratch) return BuffaWire.Backend.super.slab(compiled, input, plan, limits, capacity);
    throw new IllegalStateException("Native slab capacity retry failed");
  }

  @Override
  public SlabMessage slab(
      long compiled, WireInput input, ProtoPlan plan, SlabLimits limits, int capacity) {
    if (!(input.lease.attachment instanceof MemorySegment memory))
      throw new IllegalArgumentException("Input is not JVM native memory");
    acquireResult();
    try {
      for (int attempt = 0; attempt < 2; attempt++) {
        try (Arena arena = Arena.ofConfined()) {
          MemorySegment output = arena.allocate(Math.max(1, capacity), 8);
          int status;
          try {
            status =
                (int)
                    decodeSlab.invokeExact(
                        MemorySegment.ofAddress(compiled),
                        memory,
                        (long) input.size(),
                        1,
                        MemorySegment.NULL,
                        output,
                        (long) capacity,
                        limits.maxDepth(),
                        (long) limits.maxSlabBytes(),
                        slabResult);
          } catch (Throwable failure) {
            throw callFailure(failure);
          }
          long bytes = slabResult.get(ValueLayout.JAVA_LONG, BuffaFFM.SlabResult_bytes_OFFSET);
          if (status == 6 && attempt == 0 && bytes > capacity && bytes <= limits.maxSlabBytes()) {
            capacity = Math.toIntExact(bytes);
            continue;
          }
          BuffaWire.status(status);
          if (bytes < 0 || bytes > capacity)
            throw new IllegalStateException("Invalid native slab extent");
          return SlabMessage.copyOf(plan, output.asSlice(0, bytes).asByteBuffer(), limits);
        }
      }
      throw new IllegalStateException("Native slab capacity retry failed");
    } finally {
      busy = false;
    }
  }

  @Override
  public BuffaWire.@Nullable Tape view(
      long compiled,
      WireInput input,
      dev.elide.truffledata.protobuf.ProtoPlan plan,
      int nativeBytes,
      boolean budgetFallback) {
    if (!(input.lease.attachment instanceof MemorySegment inputMemory))
      throw new IllegalArgumentException("Input is not JVM native memory");
    MemorySegment scratch = acquireResult();
    try {
      int status;
      try {
        status =
            (int)
                decodeView.invokeExact(
                    MemorySegment.ofAddress(compiled),
                    inputMemory,
                    (long) input.size(),
                    plan.limits().maxCells(),
                    plan.limits().maxStringBytes(),
                    plan.maxDepth(),
                    (long) nativeBytes,
                    scratch,
                    viewResult);
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      // Scratch now holds the reusable index (or nothing after a failure).
      cached = true;
      if (budgetFallback && status == 2) return null;
      boolean viewReady = BuffaWire.typedStatus(status, 3);
      long count = viewResult.get(ValueLayout.JAVA_LONG, BuffaFFM.View_count_OFFSET);
      long words = viewResult.get(ValueLayout.JAVA_LONG, BuffaFFM.View_words_OFFSET);
      long bytes = viewResult.get(ValueLayout.JAVA_LONG, BuffaFFM.View_bytes_OFFSET);
      MemorySegment data = viewResult.get(ValueLayout.ADDRESS, BuffaFFM.View_data_OFFSET);
      ByteBuffer buffer =
          bytes == 0
              ? ByteBuffer.allocate(0)
              : data.reinterpret(bytes, libraryArena, null).asByteBuffer();
      if (bytes != 0) detached++;
      return BuffaWire.Tape.view(buffer, count, words, bytes, viewReady, data.address(), this);
    } finally {
      busy = false;
    }
  }

  @Override
  public void releaseView(long data, long bytes) {
    try {
      releaseView.invokeExact(MemorySegment.ofAddress(data), bytes);
    } catch (Throwable failure) {
      throw callFailure(failure);
    } finally {
      detach();
    }
  }

  private void detach() {
    detached--;
    if (closed && detached == 0) libraryArena.close();
  }

  private void free(MemorySegment result) {
    try {
      release.invokeExact(result);
    } catch (Throwable failure) {
      throw callFailure(failure);
    }
  }

  static RuntimeException callFailure(Throwable failure) {
    if (failure instanceof RuntimeException exception) return exception;
    if (failure instanceof Error error) throw error;
    return new IllegalStateException("Native invocation failed", failure);
  }

  @Override
  public void close() {
    if (busy) throw new IllegalStateException("Native result still in use");
    if (!closed) {
      free(result);
      closed = true;
      if (detached == 0) libraryArena.close();
    }
  }
}
