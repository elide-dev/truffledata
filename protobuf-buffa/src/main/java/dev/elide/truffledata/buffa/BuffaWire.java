package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.*;
import dev.elide.truffledata.buffa.generated.Buffa;
import dev.elide.truffledata.buffa.generated.BuffaFFM;
import dev.elide.truffledata.protobuf.ProtoWire;
import java.math.BigInteger;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.graalvm.nativeimage.*;
import org.jspecify.annotations.Nullable;

/**
 * Optional, explicitly selected native wire parser. Backend selection happens once, before parsing.
 */
public final class BuffaWire implements AutoCloseable {
  public enum Mode {
    JAVA,
    AUTO,
    NATIVE
  }

  public enum Selected {
    JAVA,
    JVM_FFM,
    SVM_STATIC
  }

  interface Backend extends AutoCloseable {
    WireInput allocate(int size);

    Tape decode(WireInput input, Limits limits, int depth, int nativeBytes);

    @Override
    void close();
  }

  interface NativeFactory {
    Backend create();
  }

  static final Schema SCHEMA =
      Schema.of(
          Field.requiredLong("number"),
          Field.requiredLong("wireType"),
          new Field("value", Field.Kind.ANY, false, false));
  private final Thread owner = Thread.currentThread();
  private final @Nullable Backend backend;
  private final Selected selected;
  private boolean open = true;

  private BuffaWire(@Nullable Backend backend, Selected selected) {
    this.backend = backend;
    this.selected = selected;
  }

  /**
   * JVM native loading requires an explicit library path. SVM native support is a build-time
   * Feature.
   */
  @TruffleBoundary
  public static BuffaWire open(Mode mode, @Nullable Path library) {
    Objects.requireNonNull(mode);
    if (mode == Mode.JAVA) return new BuffaWire(null, Selected.JAVA);
    if (ImageInfo.inImageCode()) {
      if (ImageSingletons.contains(NativeFactory.class))
        return new BuffaWire(
            ImageSingletons.lookup(NativeFactory.class).create(), Selected.SVM_STATIC);
    } else if (library != null && Files.isRegularFile(library)) {
      try {
        return new BuffaWire(new FfmBackend(library), Selected.JVM_FFM);
      } catch (UnsatisfiedLinkError
          | IllegalCallerException
          | UnsupportedOperationException
          | IllegalArgumentException unavailable) {
        if (mode == Mode.NATIVE) throw unavailable;
      }
    }
    if (mode == Mode.NATIVE)
      throw new UnsupportedOperationException(
          "Buffa backend unavailable; enable the SVM Feature or supply a JVM library");
    return new BuffaWire(null, Selected.JAVA);
  }

  public Selected selected() {
    check();
    return selected;
  }

  @TruffleBoundary
  public WireInput allocate(int bytes) {
    check();
    if (bytes < 0) throw new IllegalArgumentException("Negative buffer size");
    return backend == null
        ? new WireInput(ByteBuffer.allocate(bytes), () -> {}, null)
        : backend.allocate(bytes);
  }

  /**
   * Budgets are enforced before expansion. Parse/contract failures are never retried through Java.
   */
  @TruffleBoundary
  public WireMessage decode(
      WireInput input, Limits limits, int maxDepth, int maxWireBytes, int maxNativeBytes) {
    check();
    Objects.requireNonNull(limits);
    if (maxDepth < 1
        || maxDepth > 128
        || maxWireBytes < 0
        || maxNativeBytes < 0
        || input.size() > maxWireBytes) throw new ContractViolation("Invalid wire budgets");
    WireInput.Lease lease = input.retain();
    WireMessage message = null;
    try {
      if (backend == null) {
        byte[] copy = new byte[input.size()];
        lease.bytes.duplicate().get(copy);
        var fields = ProtoWire.decode(copy, limits, maxDepth, maxWireBytes);
        message = new WireMessage(lease, 0, limits);
        message.fields(fields);
        return message;
      }
      try (Tape tape = backend.decode(input, limits, maxDepth, maxNativeBytes)) {
        message = new WireMessage(lease, tape.allocated, limits);
        message.fields(materialize(message, tape, 0, tape.count, limits));
        return message;
      }
    } catch (RuntimeException | Error failure) {
      if (message == null) lease.release();
      else message.close();
      throw failure;
    }
  }

  private static DataArray materialize(
      WireMessage message, Tape tape, int start, int end, Limits limits) {
    List<Object> rows = new ArrayList<>();
    for (int index = start; index < end; ) {
      int offset = index * Buffa.Field.SIZE;
      int number = tape.bytes.getInt(offset + (int) BuffaFFM.Field_number_OFFSET),
          wire = tape.bytes.getInt(offset + (int) BuffaFFM.Field_wire_OFFSET);
      long value = tape.bytes.getLong(offset + (int) BuffaFFM.Field_value_OFFSET),
          length = tape.bytes.getLong(offset + (int) BuffaFFM.Field_length_OFFSET);
      int next = Math.toIntExact(tape.bytes.getLong(offset + (int) BuffaFFM.Field_end_OFFSET));
      if (next <= index || next > end)
        throw new IllegalStateException("Invalid native field index");
      Object decoded =
          switch (wire) {
            case 0, 1 ->
                value >= 0
                    ? value
                    : Values.normalize(BigInteger.valueOf(value & Long.MAX_VALUE).setBit(63));
            case 5 -> value;
            case 2 -> new BorrowedBytes(message, Math.toIntExact(value), Math.toIntExact(length));
            case 3 -> materialize(message, tape, index + 1, next, limits);
            default -> throw new IllegalStateException("Invalid native wire type");
          };
      limits.check(1, 3);
      rows.add(new WireField(message, number, wire, decoded));
      index = next;
    }
    return new DataArray(rows.toArray());
  }

  private void check() {
    if (Thread.currentThread() != owner || !open)
      throw new IllegalStateException("Decoder closed or accessed from another thread");
  }

  @Override
  @TruffleBoundary
  public void close() {
    if (Thread.currentThread() != owner) throw new IllegalStateException("Wrong decoder thread");
    if (open) {
      open = false;
      if (backend != null) backend.close();
    }
  }

  static void status(int code) {
    if (code == 1) throw new ContractViolation("Malformed protobuf wire input");
    if (code == 2)
      throw new ContractViolation("Buffa structural or native allocation budget exceeded");
    if (code != 0) throw new IllegalStateException("Buffa decoder failure: " + code);
  }

  static final class Tape implements AutoCloseable {
    final ByteBuffer bytes;
    final int count;
    final long allocated;
    private final Runnable release;

    Tape(ByteBuffer bytes, long count, long capacity, Runnable release) {
      this.bytes = bytes.order(ByteOrder.nativeOrder());
      this.count = Math.toIntExact(count);
      allocated = Math.multiplyExact(capacity, Buffa.Field.SIZE);
      this.release = release;
    }

    @Override
    public void close() {
      release.run();
    }
  }
}
