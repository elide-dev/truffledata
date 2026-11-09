package dev.elide.truffledata.buffa;

import dev.elide.truffledata.Limits;
import dev.elide.truffledata.buffa.generated.Buffa;
import dev.elide.truffledata.protobuf.ProtoPlan;
import dev.elide.truffledata.protobuf.SlabLimits;
import dev.elide.truffledata.protobuf.SlabMessage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.graalvm.nativeimage.*;
import org.graalvm.nativeimage.c.type.*;
import org.graalvm.word.*;
import org.jspecify.annotations.Nullable;

/** Direct static C bindings. No FFM downcalls or dynamic loading are reachable in this path. */
final class SvmBackend implements BuffaWire.Backend {
  private static final int SLAB = Buffa.Result.SIZE;

  /** One allocation: reusable typed-index and slab result slots. */
  private final long resultAddress;

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
  private final Runnable releaseAction = this::releaseResult;
  private final Runnable retainAction = this::retainResult;
  private boolean closed;

  private VoidPointer acquireResult() {
    if (closed || busy) throw new IllegalStateException("Native backend unavailable");
    busy = true;
    return WordFactory.pointer(resultAddress);
  }

  private void releaseResult() {
    if (!busy) throw new IllegalStateException("Native result already released");
    Buffa.release(WordFactory.pointer(resultAddress));
    cached = false;
    busy = false;
  }

  private void retainResult() {
    if (!busy) throw new IllegalStateException("Native result already released");
    cached = true;
    busy = false;
  }

  static final class Factory implements BuffaWire.NativeFactory {
    @Override
    public BuffaWire.Backend create() {
      return new SvmBackend();
    }
  }

  SvmBackend() {
    byte[] fingerprint = new byte[64];
    CTypeConversion.asByteBuffer(Buffa.fingerprint(), 64).get(fingerprint);
    if (!new String(fingerprint, StandardCharsets.US_ASCII).equals(Buffa.ABI_FINGERPRINT))
      throw new IllegalStateException("Myna/Buffa ABI fingerprint mismatch");
    VoidPointer result = UnmanagedMemory.calloc(SLAB + Buffa.SlabResult.SIZE);
    if (result.isNull()) throw new OutOfMemoryError("Native result allocation");
    resultAddress = result.rawValue();
  }

  @Override
  public WireInput allocate(int size) {
    CCharPointer pointer = UnmanagedMemory.malloc(Math.max(1, size));
    if (pointer.isNull()) throw new OutOfMemoryError("Wire input allocation");
    long address = pointer.rawValue();
    return BuffaSvm.owned(
        pointer, size, () -> UnmanagedMemory.free(WordFactory.pointer(address)), false);
  }

  @Override
  public BuffaWire.Tape decode(WireInput input, Limits limits, int depth, int nativeBytes) {
    if (!(input.lease.attachment instanceof BuffaSvm.Address address))
      throw new IllegalArgumentException("Input is not SVM native memory");
    VoidPointer result = acquireResult();
    try {
      if (cached) {
        Buffa.release(result);
        cached = false;
      }
      BuffaWire.status(
          Buffa.decode(
              WordFactory.pointer(address.value()),
              input.size(),
              limits.maxCells(),
              depth,
              nativeBytes,
              result));
      long count = Buffa.Result.count(result), capacity = Buffa.Result.capacity(result);
      ByteBuffer bytes =
          count == 0
              ? ByteBuffer.allocate(0)
              : CTypeConversion.asByteBuffer(
                  Buffa.Result.data(result),
                  Math.toIntExact(Math.multiplyExact(count, Buffa.Field.SIZE)));
      return new BuffaWire.Tape(bytes, count, capacity, releaseAction);
    } catch (RuntimeException | Error failure) {
      releaseResult();
      throw failure;
    }
  }

  static ByteBuffer indexBuffer(long address, int count) {
    return CTypeConversion.asByteBuffer(
        WordFactory.pointer(address), Math.multiplyExact(count, Buffa.Field.SIZE));
  }

  @Override
  public BuffaWire.Tape typed(
      WireInput schema,
      WireInput input,
      int format,
      WireInput payload,
      dev.elide.truffledata.protobuf.ProtoPlan plan,
      int nativeBytes) {
    if (!(schema.lease.attachment instanceof BuffaSvm.Address schemaAddress)
        || !(input.lease.attachment instanceof BuffaSvm.Address inputAddress)
        || !(payload.lease.attachment instanceof BuffaSvm.Address payloadAddress))
      throw new IllegalArgumentException("Inputs are not SVM native memory");
    VoidPointer result = acquireResult();
    try {
      int status =
          Buffa.typedReuse(
              WordFactory.pointer(schemaAddress.value()),
              schema.size(),
              WordFactory.pointer(inputAddress.value()),
              input.size(),
              format,
              WordFactory.pointer(payloadAddress.value()),
              payload.size(),
              plan.limits().maxCells(),
              plan.limits().maxStringBytes(),
              plan.maxDepth(),
              nativeBytes,
              result);
      BuffaWire.status(status);
      long count = Buffa.Result.count(result), capacity = Buffa.Result.capacity(result);
      ByteBuffer bytes =
          count == 0
              ? ByteBuffer.allocate(0)
              : CTypeConversion.asByteBuffer(
                  Buffa.Result.data(result),
                  Math.toIntExact(Math.multiplyExact(count, Buffa.Field.SIZE)));
      return new BuffaWire.Tape(
          bytes, count, capacity, retainAction, Buffa.Result.data(result).rawValue());
    } catch (RuntimeException | Error failure) {
      releaseResult();
      throw failure;
    }
  }

  @Override
  public long compileLayout(WireInput schema) {
    if (!(schema.lease.attachment instanceof BuffaSvm.Address address))
      throw new IllegalArgumentException("Schema is not SVM native memory");
    acquireResult();
    try {
      VoidPointer output = WordFactory.pointer(resultAddress + SLAB);
      BuffaWire.status(
          Buffa.compileLayout(WordFactory.pointer(address.value()), schema.size(), output));
      long compiled = Buffa.Result.data(output).rawValue();
      if (compiled == 0) throw new IllegalStateException("Native layout compilation returned null");
      return compiled;
    } finally {
      busy = false;
    }
  }

  @Override
  public void releaseLayout(long compiled) {
    Buffa.releaseLayout(WordFactory.pointer(compiled));
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
    acquireResult();
    try {
      VoidPointer result = WordFactory.pointer(resultAddress + SLAB);
      int status =
          Buffa.decodeSlab(
              WordFactory.pointer((Long) compiled),
              WordFactory.pointer(source.value()),
              input.size(),
              flags,
              projection == null
                  ? WordFactory.nullPointer()
                  : WordFactory.pointer(
                      ((BuffaSvm.Address)
                              java.util.Objects.requireNonNull(projection.lease.attachment))
                          .value()),
              WordFactory.pointer(destination.value()),
              output.size(),
              limits.maxDepth(),
              limits.maxSlabBytes(),
              result);
      long bytes = Buffa.SlabResult.bytes(result);
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
      VoidPointer result = WordFactory.pointer(resultAddress + SLAB);
      for (int attempt = 0; attempt < 2; attempt++) {
        byte[] output = new byte[capacity];
        int status;
        // Neither address escapes this call. Empty buffers use a valid null address and zero size.
        try (var pinnedInput = PinnedObject.create(input);
            var pinnedOutput = PinnedObject.create(output)) {
          status =
              Buffa.decodeSlab(
                  WordFactory.pointer(compiled),
                  input.length == 0
                      ? WordFactory.pointer(resultAddress)
                      : pinnedInput.addressOfArrayElement(0),
                  input.length,
                  1,
                  WordFactory.nullPointer(),
                  capacity == 0 ? WordFactory.nullPointer() : pinnedOutput.addressOfArrayElement(0),
                  capacity,
                  limits.maxDepth(),
                  limits.maxSlabBytes(),
                  result);
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
        // The plan-wide learned capacity must not amplify small messages decoded after a large
        // one: spare room beyond the slab's own size (plus a small slack) is trimmed away.
        if (output.length - extent > Math.max(256, extent))
          output = java.util.Arrays.copyOf(output, extent);
        return SlabMessage.takeOwnership(plan, output, extent, limits);
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
    if (!(input.lease.attachment instanceof BuffaSvm.Address address))
      throw new IllegalArgumentException("Input is not SVM native memory");
    acquireResult();
    try {
      VoidPointer result = WordFactory.pointer(resultAddress + SLAB);
      for (int attempt = 0; attempt < 2; attempt++) {
        // The native producer writes straight into the heap array the durable message will own:
        // no unmanaged scratch, no direct-buffer wrapper and no second copy per message.
        byte[] output = new byte[Math.max(1, capacity)];
        int status;
        long bytes;
        try (PinnedObject pinned = PinnedObject.create(output)) {
          status =
              Buffa.decodeSlab(
                  WordFactory.pointer(compiled),
                  WordFactory.pointer(address.value()),
                  input.size(),
                  1,
                  WordFactory.nullPointer(),
                  (CCharPointer) pinned.addressOfArrayElement(0),
                  capacity,
                  limits.maxDepth(),
                  limits.maxSlabBytes(),
                  result);
          bytes = Buffa.SlabResult.bytes(result);
        }
        if (status == 6 && attempt == 0 && bytes > capacity && bytes <= limits.maxSlabBytes()) {
          capacity = Math.toIntExact(bytes);
          continue;
        }
        BuffaWire.status(status);
        if (bytes < 0 || bytes > capacity)
          throw new IllegalStateException("Invalid native slab extent");
        int extent = Math.toIntExact(bytes);
        // The plan-wide learned capacity must not amplify small messages decoded after a large
        // one: spare room beyond the slab's own size (plus a small slack) is trimmed away.
        if (output.length - extent > Math.max(256, extent))
          output = java.util.Arrays.copyOf(output, extent);
        return SlabMessage.takeOwnership(plan, output, extent, limits);
      }
      throw new IllegalStateException("Native slab capacity retry failed");
    } finally {
      busy = false;
    }
  }

  /** Ordinary VM transition: input length is unbounded and must not delay safepoints. */
  static String utf8(long address, int length) {
    char[] characters = new char[length];
    long written;
    try (var pinned = PinnedObject.create(characters)) {
      written =
          Buffa.utf8_to_utf16(
              WordFactory.pointer(address), length, pinned.addressOfArrayElement(0), length);
    }
    if (written < 0 || written > length)
      throw new dev.elide.truffledata.ContractViolation("Invalid native UTF-8 or output extent");
    return new String(characters, 0, (int) written);
  }

  @Override
  public void close() {
    if (busy) throw new IllegalStateException("Native result still in use");
    if (!closed) {
      closed = true;
      Buffa.release(WordFactory.pointer(resultAddress));
      UnmanagedMemory.free(WordFactory.pointer(resultAddress));
    }
  }
}
