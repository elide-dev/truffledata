package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.library.*;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.truffledata.*;
import dev.elide.truffledata.json.generated.Json;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;

/** Validated scalar slots with allocation-free primitive reads and explicit scoped ownership. */
@ExportLibrary(DataLibrary.class)
public final class JsonScalarRecord extends AbstractRow implements AutoCloseable {
  private final VoidPointer result;
  private final Thread thread = Thread.currentThread();
  private boolean open = true;

  JsonScalarRecord(Schema schema, VoidPointer result) {
    super(schema);
    this.result = result;
  }

  @Override
  protected boolean scoped() {
    return true;
  }

  @ExportMessage
  @Idempotent
  Schema getSchema() {
    return descriptor;
  }

  @ExportMessage
  Object read(int index) {
    if (!isPresent(index)) return Missing.INSTANCE;
    if (isNull(index)) return DataNull.INSTANCE;
    return switch (descriptor.field(index).kind()) {
      case INT64 -> longValue(index);
      case FLOAT64 -> doubleValue(index);
      case BOOLEAN -> booleanValue(index);
      case STRING -> stringValue(index);
      case EXACT -> Values.normalize(unsignedValue(index));
      default -> throw new IllegalStateException("Unsupported JSON scalar kind");
    };
  }

  static JsonScalarAccess access(Schema schema, int index) {
    return new JsonScalarAccess(schema, index);
  }

  @ExportMessage
  static class ReadLong {
    @Specialization(
        guards = {
          "row.getSchema() == schema",
          "index == cachedIndex",
          "schema.requiredLong(cachedIndex)"
        },
        limit = "3")
    static long cached(
        JsonScalarRecord row,
        int index,
        @Cached("row.getSchema()") Schema schema,
        @Cached("index") int cachedIndex,
        @Cached("access(schema, cachedIndex)") JsonScalarAccess binding) {
      return binding.longValue(row);
    }

    @Specialization(replaces = "cached")
    static long generic(JsonScalarRecord row, int index) throws UnexpectedResultException {
      Object value = row.read(index);
      if (value instanceof Long result) return result;
      throw new UnexpectedResultException(value);
    }
  }

  @ExportMessage
  static class ReadDouble {
    @Specialization(
        guards = {
          "row.getSchema() == schema",
          "index == cachedIndex",
          "schema.requiredDouble(cachedIndex)"
        },
        limit = "3")
    static double cached(
        JsonScalarRecord row,
        int index,
        @Cached("row.getSchema()") Schema schema,
        @Cached("index") int cachedIndex,
        @Cached("access(schema, cachedIndex)") JsonScalarAccess binding) {
      return binding.doubleValue(row);
    }

    @Specialization(replaces = "cached")
    static double generic(JsonScalarRecord row, int index) throws UnexpectedResultException {
      Object value = row.read(index);
      if (value instanceof Double result) return result;
      throw new UnexpectedResultException(value);
    }
  }

  @ExportMessage
  static class ReadBoolean {
    @Specialization(
        guards = {
          "row.getSchema() == schema",
          "index == cachedIndex",
          "schema.requiredBoolean(cachedIndex)"
        },
        limit = "3")
    static boolean cached(
        JsonScalarRecord row,
        int index,
        @Cached("row.getSchema()") Schema schema,
        @Cached("index") int cachedIndex,
        @Cached("access(schema, cachedIndex)") JsonScalarAccess binding) {
      return binding.booleanValue(row);
    }

    @Specialization(replaces = "cached")
    static boolean generic(JsonScalarRecord row, int index) throws UnexpectedResultException {
      Object value = row.read(index);
      if (value instanceof Boolean result) return result;
      throw new UnexpectedResultException(value);
    }
  }

  private void check(int index) {
    checkThread();
    if (!open) throw new IllegalStateException("JSON record closed");
    if (index < 0 || index >= descriptor.size()) throw new IndexOutOfBoundsException(index);
  }

  private void checkThread() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("JSON record thread mismatch");
  }

  @ExportMessage
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
    if (descriptor.field(index).kind() != kind)
      throw new IllegalArgumentException("JSON scalar kind mismatch");
    if (!isPresent(index) || isNull(index))
      throw new IllegalStateException("JSON scalar missing or null");
    return (VoidPointer)
        ((Pointer) Json.JsonTypedResult.slots(result)).add(index * Json.JsonScalarSlot.SIZE);
  }

  long bits(JsonScalarAccess access) {
    checkThread();
    if (!open) throw new IllegalStateException("JSON record closed");
    if (descriptor != access.schema)
      throw new IllegalArgumentException("JSON accessor schema mismatch");
    long bit = 1L << access.index;
    if ((access.optional && (Json.JsonTypedResult.present(result) & bit) == 0)
        || (access.nullable && (Json.JsonTypedResult.nulls(result) & bit) != 0))
      throw new IllegalStateException("JSON scalar missing or null");
    return Json.JsonScalarSlot.bits(
        (VoidPointer)
            ((Pointer) Json.JsonTypedResult.slots(result))
                .add(access.index * Json.JsonScalarSlot.SIZE));
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
