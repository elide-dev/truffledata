package dev.elide.truffledata.tape;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import dev.elide.truffledata.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Owner of one typed decode result: its slot arena, record table and retained input. Thread
 * confined; closing invalidates every {@link TypedRecord} and {@link TypedArray} derived from it.
 *
 * <p>Each backend reads the immutable native tables its own way: the static SVM owner through
 * word accessors, the JVM owner through FFM segments. Slots and record entries are addressed by
 * index; callers check the owner before reading.
 */
public abstract class TypedOwner implements AutoCloseable {
  // Schema of each plan record ordinal; ordinal 0 is the root.
  @CompilationFinal(dimensions = 1)
  final Schema[] schemas;

  private final Thread thread;
  private boolean open = true;
  private @Nullable Assumption alive;

  /** An owner confined to the calling thread, reading records of {@code schemas} (by plan ordinal). */
  protected TypedOwner(Schema[] schemas) {
    this(schemas, Thread.currentThread());
  }

  // An owner confined to {@code thread}: the facade of an owned record, created on request.
  TypedOwner(Schema[] schemas, Thread thread) {
    this.schemas = schemas;
    this.thread = thread;
  }

  /** The format that produced this result, named in its messages. */
  protected abstract TapeFormat format();

  /** Rejects closed or cross-thread access. */
  final void check() {
    checkThread();
    if (!open) throw new OwnerStateException(this, OwnerStateException.RECORD_CLOSED);
  }

  // Failures name the format lazily, so owners hold no format reference.
  final void checkThread() {
    if (Thread.currentThread() != thread)
      throw new OwnerStateException(this, OwnerStateException.RECORD_THREAD);
  }

  /** Payload bits of slot {@code slot}. */
  protected abstract long bits(long slot);

  /** Decoded UTF-8 text of string slot {@code slot}, copied into a Java string. */
  protected abstract String text(long slot);

  /** Presence mask of record entry {@code entry}. */
  protected abstract long present(long entry);

  /** Null mask of record entry {@code entry}. */
  protected abstract long nulls(long entry);

  /** First slot index of record entry {@code entry}, as an unsigned 32-bit value. */
  protected abstract int slotBase(long entry);

  /** Plan record ordinal of record entry {@code entry}. */
  protected abstract int plan(long entry);

  /** Releases the native result; called once, on the owning thread. */
  protected abstract void release();

  /** Unsigned 64-bit payload as an exact integer; materialized only on request. */
  @TruffleBoundary
  static java.math.BigInteger unsigned(long bits) {
    return bits >= 0
        ? java.math.BigInteger.valueOf(bits)
        : java.math.BigInteger.valueOf(bits & Long.MAX_VALUE).setBit(63);
  }

  /** The root record (entry 0). */
  public final TypedRecord root() {
    check();
    return newRoot();
  }

  // Native results read entry 0 of their record table; an owned record's facade returns the record.
  TypedRecord newRoot() {
    return new TypedRecord(this, 0);
  }

  // A facade created for an owned record that was already closed: closed, with nothing to release.
  final void retired() {
    open = false;
  }

  public final boolean isOpen() {
    checkThread();
    return open;
  }

  /** Valid while this owner is open; closing invalidates it. */
  @TruffleBoundary
  public final Assumption assumption() {
    check();
    Assumption result = alive;
    if (result == null) alive = result = Truffle.getRuntime().createAssumption(format().name + " record alive");
    return result;
  }

  /** Releases the native result; idempotent on the owning thread. */
  @Override
  @TruffleBoundary
  public final void close() {
    checkThread();
    if (open) {
      open = false;
      if (alive != null) alive.invalidate();
      release();
    }
  }
}
