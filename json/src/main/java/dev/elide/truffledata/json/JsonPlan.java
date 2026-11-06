package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.json.generated.Json;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;

/** Reusable, thread-confined native scalar plan. Results own their storage independently. */
public final class JsonPlan implements AutoCloseable {
  private final Schema schema;
  private final VoidPointer handle;
  private final Thread thread = Thread.currentThread();
  private boolean open = true;

  private JsonPlan(Schema schema, VoidPointer handle) {
    this.schema = schema;
    this.handle = handle;
  }

  /** Compiles a closed scalar schema; unsupported contracts fail before native allocation. */
  @TruffleBoundary
  public static JsonPlan compile(Schema schema) {
    JsonSvm.requireRuntime();
    if (schema.size() > 64) throw new IllegalArgumentException("JSON scalar field limit");
    var names = new HashSet<String>();
    for (int i = 0; i < schema.size(); i++) {
      Field f = schema.field(i);
      if (!names.add(f.name())) throw new IllegalArgumentException("Duplicate JSON field");
      // Encoding must never silently replace an unpaired surrogate in a field name.
      for (int p = 0; p < f.name().length(); p++) {
        char c = f.name().charAt(p);
        if (Character.isHighSurrogate(c)) {
          if (++p == f.name().length() || !Character.isLowSurrogate(f.name().charAt(p)))
            throw new IllegalArgumentException("Invalid schema Unicode");
        } else if (Character.isLowSurrogate(c))
          throw new IllegalArgumentException("Invalid schema Unicode");
      }
      kind(f);
    }
    PinnedObject[] pins = new PinnedObject[schema.size()];
    VoidPointer handle = UnmanagedMemory.calloc(Json.JsonPlan.SIZE);
    if (handle.isNull()) throw new OutOfMemoryError("JSON plan allocation");
    Pointer fields = UnmanagedMemory.calloc(Math.max(1, schema.size()) * Json.JsonScalarField.SIZE);
    if (fields.isNull()) {
      UnmanagedMemory.free(handle);
      throw new OutOfMemoryError("JSON schema allocation");
    }
    try {
      long nameBytes = 0;
      for (int i = 0; i < schema.size(); i++) {
        Field f = schema.field(i);
        var c = f.constraints();
        byte[] name = f.name().getBytes(StandardCharsets.UTF_8);
        nameBytes += name.length;
        if (nameBytes > 1 << 20) throw new IllegalArgumentException("JSON schema name limit");
        // An empty key still needs an addressable backing allocation.
        pins[i] = PinnedObject.create(name.length == 0 ? new byte[1] : name);
        VoidPointer field = (VoidPointer) fields.add(i * Json.JsonScalarField.SIZE);
        Json.JsonScalarField.name(field, pins[i].addressOfArrayElement(0));
        Json.JsonScalarField.name_length(field, name.length);
        Json.JsonScalarField.kind(field, kind(f));
        Json.JsonScalarField.flags(field, (f.optional() ? 0 : 1) | (f.nullable() ? 2 : 0));
        long min = Long.MIN_VALUE, max = Long.MAX_VALUE;
        var width = c.integer();
        if (width != null && f.kind() == Field.Kind.INT64) {
          min = width.minimum();
          max = width.maximum();
        }
        var range = c.integerRange();
        if (range != null) {
          min = Math.max(min, range.min());
          max = Math.min(max, range.max());
        }
        Json.JsonScalarField.minimum(field, min);
        Json.JsonScalarField.maximum(field, max);
        Json.JsonScalarField.unsigned_maximum(field, -1L);
        Json.JsonScalarField.max_utf8_bytes(field, 64 * 1024 * 1024);
        Json.JsonScalarField.float_bits(field, c.floatBits());
        var length = c.length();
        if (length != null) {
          Json.JsonScalarField.length_unit(
              field, length.unit() == dev.elide.truffledata.Constraints.LengthUnit.UTF16 ? 1 : 2);
          Json.JsonScalarField.minimum_length(field, length.min());
          Json.JsonScalarField.maximum_length(field, length.max());
        }
      }
      JsonSvm.checkStatus(Json.plan_create((VoidPointer) fields, schema.size(), 1 << 20, handle));
      return new JsonPlan(schema, handle);
    } catch (RuntimeException | Error failure) {
      Json.plan_release(handle);
      UnmanagedMemory.free(handle);
      throw failure;
    } finally {
      for (PinnedObject pin : pins) if (pin != null) pin.close();
      UnmanagedMemory.free(fields);
    }
  }

  private static int kind(Field field) {
    return switch (field.kind()) {
      case BOOLEAN -> 0;
      case INT64 -> 1;
      case FLOAT64 -> 3;
      case STRING -> 4;
      case EXACT -> {
        var width = field.constraints().integer();
        if (width == null
            || width.signed()
            || width.bits() != 64
            || field.constraints().decimal() != null)
          throw new IllegalArgumentException("Unsupported JSON exact contract");
        yield 2;
      }
      default ->
          throw new IllegalArgumentException("Unsupported JSON scalar kind: " + field.kind());
    };
  }

  private void checkThread() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("JSON plan thread mismatch");
  }

  @TruffleBoundary
  public JsonRecord decode(byte[] utf8) {
    checkThread();
    if (!open) throw new IllegalStateException("JSON plan closed");
    if (utf8.length == 0 || utf8.length > 64 * 1024 * 1024)
      throw new IllegalArgumentException("JSON input limit");
    VoidPointer result = UnmanagedMemory.calloc(Json.JsonTypedResult.SIZE);
    if (result.isNull()) throw new OutOfMemoryError("JSON result allocation");
    try {
      try (PinnedObject input = PinnedObject.create(utf8)) {
        JsonSvm.checkStatus(
            Json.typed_decode(
                handle,
                input.addressOfArrayElement(0),
                utf8.length,
                64 * 1024 * 1024,
                64 * 1024 * 1024,
                result));
      }
      return new JsonRecord(schema, result);
    } catch (RuntimeException | Error failure) {
      Json.typed_release(result);
      UnmanagedMemory.free(result);
      throw failure;
    }
  }

  @Override
  @TruffleBoundary
  public void close() {
    checkThread();
    if (open) {
      open = false;
      Json.plan_release(handle);
      UnmanagedMemory.free(handle);
    }
  }
}
