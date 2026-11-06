package dev.elide.truffledata.json;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.json.generated.Json;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;
import org.jspecify.annotations.Nullable;

/**
 * Owner of one typed decode result: its slot arena, record table and retained input. Thread
 * confined; closing invalidates every {@link JsonRecord} and {@link JsonArray} derived from it.
 * Native addresses are read once at publication, so reads touch only final fields and the arenas.
 */
public final class JsonTypedOwner implements AutoCloseable {
  private final VoidPointer result;
  // Raw addresses of the immutable slot arena and record table; valid until release.
  final long slots;
  final long records;
  final long slotCount;
  final long recordCount;

  // Schema of each plan record ordinal; ordinal 0 is the root.
  @CompilationFinal(dimensions = 1)
  final Schema[] schemas;

  private final Thread thread = Thread.currentThread();
  private boolean open = true;
  private @Nullable Assumption alive;

  JsonTypedOwner(VoidPointer result, Schema[] schemas) {
    this.result = result;
    this.schemas = schemas;
    this.slots = Json.JsonTypedResult.slots(result).rawValue();
    this.slotCount = Json.JsonTypedResult.count(result);
    this.records = Json.JsonTypedResult.records(result).rawValue();
    this.recordCount = Json.JsonTypedResult.record_count(result);
    if (records == 0 || recordCount < 1 || (slotCount > 0 && slots == 0))
      throw new IllegalStateException("JSON typed result tables missing");
  }

  /** Rejects closed or cross-thread access. */
  void check() {
    checkThread();
    if (!open) throw new IllegalStateException("JSON record closed");
  }

  void checkThread() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("JSON record thread mismatch");
  }

  /** Address of slot {@code index} in the arena. */
  long slot(long index) {
    return slots + index * Json.JsonScalarSlot.SIZE;
  }

  /** Address of record entry {@code index} in the record table. */
  long entry(long index) {
    return records + index * Json.JsonRecordEntry.SIZE;
  }

  static long bits(long slot) {
    return Json.JsonScalarSlot.bits(WordFactory.<VoidPointer>pointer(slot));
  }

  static long present(long entry) {
    return Json.JsonRecordEntry.present(WordFactory.<VoidPointer>pointer(entry));
  }

  static long nulls(long entry) {
    return Json.JsonRecordEntry.nulls(WordFactory.<VoidPointer>pointer(entry));
  }

  static int slotBase(long entry) {
    return Json.JsonRecordEntry.slots(WordFactory.<VoidPointer>pointer(entry));
  }

  static int plan(long entry) {
    return Json.JsonRecordEntry.plan(WordFactory.<VoidPointer>pointer(entry));
  }

  /** Decoded UTF-8 text of a string slot, copied into a Java string. */
  @TruffleBoundary
  static String text(long slot) {
    VoidPointer pointer = WordFactory.pointer(slot);
    int length = Math.toIntExact(Json.JsonScalarSlot.text_length(pointer));
    Pointer text = (Pointer) Json.JsonScalarSlot.text(pointer);
    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) bytes[i] = text.readByte(i);
    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
  }

  /** Unsigned 64-bit payload as an exact integer; materialized only on request. */
  @TruffleBoundary
  static java.math.BigInteger unsigned(long bits) {
    return bits >= 0
        ? java.math.BigInteger.valueOf(bits)
        : java.math.BigInteger.valueOf(bits & Long.MAX_VALUE).setBit(63);
  }

  /** The root record (entry 0). */
  public JsonRecord root() {
    check();
    return new JsonRecord(this, 0);
  }

  public boolean isOpen() {
    checkThread();
    return open;
  }

  /** Valid while this owner is open; closing invalidates it. */
  @TruffleBoundary
  public Assumption assumption() {
    check();
    Assumption result = alive;
    if (result == null) alive = result = Truffle.getRuntime().createAssumption("JSON record alive");
    return result;
  }

  /** Releases the native result; idempotent on the owning thread. */
  @Override
  @TruffleBoundary
  public void close() {
    checkThread();
    if (open) {
      open = false;
      if (alive != null) alive.invalidate();
      Json.typed_release(result);
      UnmanagedMemory.free(result);
    }
  }
}
