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
  private final Runnable releaseAction = this::releaseResult;
  private boolean closed;

  private VoidPointer acquireResult() {
    if (closed || busy) throw new IllegalStateException("Native backend unavailable");
    busy = true;
    return WordFactory.pointer(resultAddress);
  }

  private void releaseResult() {
    if (!busy) throw new IllegalStateException("Native result already released");
    Buffa.release(WordFactory.pointer(resultAddress));
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
      BuffaWire.status(
          Buffa.typed(
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
      return new BuffaWire.Scalars(
          Buffa.Scalars.first(output), Buffa.Scalars.second(output), Buffa.Scalars.present(output));
    } finally {
      busy = false;
    }
  }

  @Override
  public void close() {
    if (busy) throw new IllegalStateException("Native result still in use");
    if (!closed) {
      closed = true;
      UnmanagedMemory.free(WordFactory.pointer(resultAddress));
    }
  }
}
