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
  private static final int SCALARS = Buffa.Result.SIZE;
  private static final int VIEW = SCALARS + Buffa.Scalars.SIZE;

  /** One allocation: the reusable index result, scalar output and ABI 9 view output slots. */
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
  private final BuffaWire.Scalars scalarValues = new BuffaWire.Scalars();
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
    VoidPointer result = UnmanagedMemory.calloc(VIEW + Buffa.View.SIZE);
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
      boolean viewReady = BuffaWire.typedStatus(status, format);
      long count = Buffa.Result.count(result), capacity = Buffa.Result.capacity(result);
      ByteBuffer bytes =
          count == 0
              ? ByteBuffer.allocate(0)
              : CTypeConversion.asByteBuffer(
                  Buffa.Result.data(result),
                  Math.toIntExact(Math.multiplyExact(count, Buffa.Field.SIZE)));
      return new BuffaWire.Tape(
          bytes, count, capacity, retainAction, viewReady, Buffa.Result.data(result).rawValue());
    } catch (RuntimeException | Error failure) {
      releaseResult();
      throw failure;
    }
  }

  @Override
  public BuffaWire.Scalars scalars(
      WireInput schema, WireInput input, dev.elide.truffledata.protobuf.ProtoPlan plan) {
    if (!(schema.lease.attachment instanceof BuffaSvm.Address schemaAddress)
        || !(input.lease.attachment instanceof BuffaSvm.Address inputAddress))
      throw new IllegalArgumentException("Inputs are not SVM native memory");
    acquireResult();
    VoidPointer output = WordFactory.pointer(resultAddress + SCALARS);
    try {
      BuffaWire.status(
          Buffa.scalars(
              WordFactory.pointer(schemaAddress.value()),
              schema.size(),
              WordFactory.pointer(inputAddress.value()),
              input.size(),
              plan.limits().maxCells(),
              plan.maxDepth(),
              output));
      return scalarValues.set(
          Buffa.Scalars.first(output), Buffa.Scalars.second(output), Buffa.Scalars.present(output));
    } finally {
      busy = false;
    }
  }

  @Override
  public long compileView(WireInput schema) {
    if (!(schema.lease.attachment instanceof BuffaSvm.Address schemaAddress))
      throw new IllegalArgumentException("Schema is not SVM native memory");
    acquireResult();
    try {
      // The view slot is idle outside decodeView; it is large enough for one Result.
      VoidPointer output = WordFactory.pointer(resultAddress + VIEW);
      BuffaWire.status(
          Buffa.compileView(WordFactory.pointer(schemaAddress.value()), schema.size(), output));
      long handle = Buffa.Result.data(output).rawValue();
      if (handle == 0) throw new IllegalStateException("Native view compilation returned null");
      return handle;
    } finally {
      busy = false;
    }
  }

  @Override
  public void releaseCompiled(long compiled) {
    // Static symbol: valid after this backend closes, like view releases.
    Buffa.releaseCompiled(WordFactory.pointer(compiled));
  }

  @Override
  public long compileLayout(WireInput schema) {
    if (!(schema.lease.attachment instanceof BuffaSvm.Address address))
      throw new IllegalArgumentException("Schema is not SVM native memory");
    acquireResult();
    try {
      VoidPointer output = WordFactory.pointer(resultAddress + VIEW);
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
  public int writeSlab(Object compiled, WireInput input, WireInput output, SlabLimits limits) {
    if (!(input.lease.attachment instanceof BuffaSvm.Address source)
        || !(output.lease.attachment instanceof BuffaSvm.Address destination))
      throw new IllegalArgumentException("Slab arena is not SVM native memory");
    acquireResult();
    try {
      VoidPointer result = WordFactory.pointer(resultAddress + VIEW);
      int status =
          Buffa.decodeSlab(
              WordFactory.pointer((Long) compiled),
              WordFactory.pointer(source.value()),
              input.size(),
              1,
              WordFactory.nullPointer(),
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
      VoidPointer result = WordFactory.pointer(resultAddress + VIEW);
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
    if (!(input.lease.attachment instanceof BuffaSvm.Address address))
      throw new IllegalArgumentException("Input is not SVM native memory");
    acquireResult();
    try {
      VoidPointer result = WordFactory.pointer(resultAddress + VIEW);
      for (int attempt = 0; attempt < 2; attempt++) {
        CCharPointer output = UnmanagedMemory.malloc(Math.max(1, capacity));
        if (output.isNull()) throw new OutOfMemoryError("Native slab allocation");
        try {
          int status =
              Buffa.decodeSlab(
                  WordFactory.pointer(compiled),
                  WordFactory.pointer(address.value()),
                  input.size(),
                  1,
                  WordFactory.nullPointer(),
                  output,
                  capacity,
                  limits.maxDepth(),
                  limits.maxSlabBytes(),
                  result);
          long bytes = Buffa.SlabResult.bytes(result);
          if (status == 6 && attempt == 0 && bytes > capacity && bytes <= limits.maxSlabBytes()) {
            capacity = Math.toIntExact(bytes);
            continue;
          }
          BuffaWire.status(status);
          if (bytes < 0 || bytes > capacity)
            throw new IllegalStateException("Invalid native slab extent");
          return SlabMessage.copyOf(
              plan, CTypeConversion.asByteBuffer(output, Math.toIntExact(bytes)), limits);
        } finally {
          UnmanagedMemory.free(output);
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
    if (!(input.lease.attachment instanceof BuffaSvm.Address inputAddress))
      throw new IllegalArgumentException("Input is not SVM native memory");
    VoidPointer scratch = acquireResult();
    try {
      VoidPointer output = WordFactory.pointer(resultAddress + VIEW);
      int status =
          Buffa.decodeView(
              WordFactory.pointer(compiled),
              WordFactory.pointer(inputAddress.value()),
              input.size(),
              plan.limits().maxCells(),
              plan.limits().maxStringBytes(),
              plan.maxDepth(),
              nativeBytes,
              scratch,
              output);
      // Scratch now holds the reusable index (or nothing after a failure).
      cached = true;
      if (budgetFallback && status == 2) return null;
      boolean viewReady = BuffaWire.typedStatus(status, 3);
      return BuffaWire.Tape.view(
          null,
          Buffa.View.count(output),
          Buffa.View.words(output),
          Buffa.View.bytes(output),
          viewReady,
          Buffa.View.data(output).rawValue(),
          this);
    } finally {
      busy = false;
    }
  }

  @Override
  public void releaseView(long data, long bytes) {
    Buffa.releaseView(WordFactory.pointer(data), bytes);
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
