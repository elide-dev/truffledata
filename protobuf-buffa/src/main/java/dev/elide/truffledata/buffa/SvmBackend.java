package dev.elide.truffledata.buffa;

import dev.elide.truffledata.Limits;
import dev.elide.truffledata.buffa.generated.Buffa;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.graalvm.nativeimage.*;
import org.graalvm.nativeimage.c.type.*;
import org.graalvm.word.*;

/** Direct static C bindings. No FFM downcalls or dynamic loading are reachable in this path. */
final class SvmBackend implements BuffaWire.Backend {
  private final long resultAddress;
  private boolean busy;
  private boolean cached;
  private final BuffaWire.Scalars scalarValues = new BuffaWire.Scalars();
  private final Runnable releaseAction = this::releaseResult;
  private final Runnable retainAction = this::retainResult;
  private final java.util.function.Supplier<Runnable> detachAction = this::detachResult;
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

  private Runnable detachResult() {
    if (!busy || closed) throw new IllegalStateException("Native result unavailable");
    VoidPointer owned = UnmanagedMemory.calloc(Buffa.Result.SIZE);
    if (owned.isNull()) throw new OutOfMemoryError("Native result ownership allocation");
    long address = owned.rawValue();
    Runnable cleanup;
    try {
      cleanup =
          () -> {
            VoidPointer result = WordFactory.pointer(address);
            if (!closed && !busy && !cached) {
              VoidPointer scratch = WordFactory.pointer(resultAddress);
              Buffa.Result.data(scratch, Buffa.Result.data(result));
              Buffa.Result.count(scratch, Buffa.Result.count(result));
              Buffa.Result.capacity(scratch, Buffa.Result.capacity(result));
              cached = true;
            } else Buffa.release(result);
            UnmanagedMemory.free(result);
          };
    } catch (RuntimeException | Error failure) {
      UnmanagedMemory.free(owned);
      throw failure;
    }
    VoidPointer source = WordFactory.pointer(resultAddress);
    Buffa.Result.data(owned, Buffa.Result.data(source));
    Buffa.Result.count(owned, Buffa.Result.count(source));
    Buffa.Result.capacity(owned, Buffa.Result.capacity(source));
    Buffa.Result.data(source, WordFactory.nullPointer());
    Buffa.Result.count(source, 0);
    Buffa.Result.capacity(source, 0);
    cached = false;
    busy = false;
    return cleanup;
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
    VoidPointer result = UnmanagedMemory.calloc(Buffa.Result.SIZE + Buffa.Scalars.SIZE);
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
      return new BuffaWire.Tape(bytes, count, capacity, retainAction, detachAction, viewReady);
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
    VoidPointer output = WordFactory.pointer(resultAddress + Buffa.Result.SIZE);
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
