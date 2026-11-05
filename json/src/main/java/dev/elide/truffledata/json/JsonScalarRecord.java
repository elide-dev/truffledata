package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.json.generated.Json;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;

/** Validated scalar slots with allocation-free primitive reads and explicit scoped ownership. */
public final class JsonScalarRecord implements AutoCloseable {
  private final Schema schema;
  private final VoidPointer result;
  private final Thread thread = Thread.currentThread();
  private boolean open = true;

  JsonScalarRecord(Schema schema, VoidPointer result) {
    this.schema = schema;
    this.result = result;
  }

  private void check(int index) {
    checkThread();
    if (!open) throw new IllegalStateException("JSON record closed");
    if (index < 0 || index >= schema.size()) throw new IndexOutOfBoundsException(index);
  }

  private void checkThread() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("JSON record thread mismatch");
  }

  public boolean isPresent(int index) {
    check(index);
    return (Json.JsonTypedResult.present(result) & (1L << index)) != 0;
  }

  public boolean isNull(int index) {
    check(index);
    return (Json.JsonTypedResult.nulls(result) & (1L << index)) != 0;
  }

  private VoidPointer slot(int index, Field.Kind kind) {
    check(index);
    if (schema.field(index).kind() != kind)
      throw new IllegalArgumentException("JSON scalar kind mismatch");
    if (!isPresent(index) || isNull(index))
      throw new IllegalStateException("JSON scalar missing or null");
    return (VoidPointer)
        ((Pointer) Json.JsonTypedResult.slots(result)).add(index * Json.JsonScalarSlot.SIZE);
  }

  public long longValue(int index) {
    return Json.JsonScalarSlot.bits(slot(index, Field.Kind.INT64));
  }

  public double doubleValue(int index) {
    return Double.longBitsToDouble(Json.JsonScalarSlot.bits(slot(index, Field.Kind.FLOAT64)));
  }

  public boolean booleanValue(int index) {
    return Json.JsonScalarSlot.bits(slot(index, Field.Kind.BOOLEAN)) != 0;
  }

  /** Unsigned 64-bit value, materialized only when requested. */
  @TruffleBoundary
  public BigInteger unsignedValue(int index) {
    long bits = Json.JsonScalarSlot.bits(slot(index, Field.Kind.EXACT));
    return bits >= 0
        ? BigInteger.valueOf(bits)
        : BigInteger.valueOf(bits & Long.MAX_VALUE).setBit(63);
  }

  @TruffleBoundary
  public String stringValue(int index) {
    VoidPointer slot = slot(index, Field.Kind.STRING);
    int length = Math.toIntExact(Json.JsonScalarSlot.text_length(slot));
    Pointer text = (Pointer) Json.JsonScalarSlot.text(slot);
    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) bytes[i] = text.readByte(i);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  @Override
  @TruffleBoundary
  public void close() {
    checkThread();
    if (open) {
      open = false;
      Json.typed_release(result);
      UnmanagedMemory.free(result);
    }
  }
}
