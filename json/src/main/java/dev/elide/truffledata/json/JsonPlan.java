package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.json.generated.Json;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/**
 * Reusable, thread-confined native typed plan: a closed root record plus the record types it
 * reaches. Results own their storage independently of the plan.
 */
public final class JsonPlan implements AutoCloseable {
  /** Maximum record nesting, counting the root as 1. */
  static final int MAX_DEPTH = 32;

  /** Maximum number of distinct records (root included) in one plan. */
  static final int MAX_RECORDS = 65536;

  private final Schema[] schemas;
  private final VoidPointer handle;
  private final Thread thread = Thread.currentThread();
  private boolean open = true;

  private JsonPlan(Schema[] schemas, VoidPointer handle) {
    this.schemas = schemas;
    this.handle = handle;
  }

  /** Compiles a closed scalar schema; unsupported contracts fail before native allocation. */
  @TruffleBoundary
  public static JsonPlan compile(Schema schema) {
    return compile(schema, Map.of());
  }

  /**
   * Compiles a closed root schema whose RECORD fields, and ARRAY fields with RECORD elements, name
   * their schema by {@code Constraints.recordType()}: a key of {@code recordTypes} whose schema has
   * that same {@code typeName}. ARRAY fields take their element contract from {@code
   * Constraints.element()}; elements are scalar or RECORD, never null, missing or nested arrays.
   * Recursive record types and nesting deeper than 32 records are rejected.
   */
  @TruffleBoundary
  public static JsonPlan compile(Schema root, Map<String, Schema> recordTypes) {
    JsonSvm.requireRuntime();
    // Records in ordinal order (0 is the root); each type name gets one ordinal on first use.
    var records = new ArrayList<Schema>();
    var ordinals = new HashMap<String, Integer>();
    records.add(root);
    int fieldCount = 0;
    for (int r = 0; r < records.size(); r++) {
      Schema schema = records.get(r);
      if (schema.size() > 64) throw new IllegalArgumentException("JSON scalar field limit");
      fieldCount += schema.size();
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
        Field contract = contract(f);
        if (contract.kind() == Field.Kind.RECORD) {
          String type = Objects.requireNonNull(contract.constraints().recordType());
          if (!ordinals.containsKey(type)) {
            Schema child = recordTypes.get(type);
            if (child == null)
              throw new IllegalArgumentException("Unknown JSON record type: " + type);
            if (!type.equals(child.typeName()))
              throw new IllegalArgumentException("JSON record type name differs: " + type);
            if (records.size() == MAX_RECORDS)
              throw new IllegalArgumentException("JSON plan record limit");
            ordinals.put(type, records.size());
            records.add(child);
          }
        }
      }
    }
    PinnedObject[] pins = new PinnedObject[fieldCount];
    VoidPointer handle = UnmanagedMemory.calloc(Json.JsonPlan.SIZE);
    if (handle.isNull()) throw new OutOfMemoryError("JSON plan allocation");
    // The record table follows the field table in the same allocation.
    long tableBytes = (long) Math.max(1, fieldCount) * Json.JsonScalarField.SIZE;
    Pointer fields =
        UnmanagedMemory.calloc(
            WordFactory.unsigned(tableBytes + (long) records.size() * Json.JsonRecordPlan.SIZE));
    if (fields.isNull()) {
      UnmanagedMemory.free(handle);
      throw new OutOfMemoryError("JSON schema allocation");
    }
    try {
      long nameBytes = 0;
      int next = 0;
      for (int r = 0; r < records.size(); r++) {
        Schema schema = records.get(r);
        VoidPointer plan =
            (VoidPointer) fields.add(WordFactory.unsigned(tableBytes + (long) r * Json.JsonRecordPlan.SIZE));
        Json.JsonRecordPlan.fields_offset(plan, next);
        Json.JsonRecordPlan.field_count(plan, schema.size());
        for (int i = 0; i < schema.size(); i++, next++) {
          Field f = schema.field(i);
          byte[] name = f.name().getBytes(StandardCharsets.UTF_8);
          nameBytes += name.length;
          if (nameBytes > 1 << 20) throw new IllegalArgumentException("JSON schema name limit");
          // An empty key still needs an addressable backing allocation.
          pins[next] = PinnedObject.create(name.length == 0 ? new byte[1] : name);
          VoidPointer field =
              (VoidPointer) fields.add(WordFactory.unsigned((long) next * Json.JsonScalarField.SIZE));
          Json.JsonScalarField.name(field, pins[next].addressOfArrayElement(0));
          Json.JsonScalarField.name_length(field, name.length);
          write(field, f, ordinals);
        }
      }
      VoidPointer table = (VoidPointer) fields.add(WordFactory.unsigned(tableBytes));
      JsonSvm.checkStatus(
          Json.plan_create(table, records.size(), (VoidPointer) fields, fieldCount, 1 << 20, handle));
      return new JsonPlan(records.toArray(Schema[]::new), handle);
    } catch (RuntimeException | Error failure) {
      Json.plan_release(handle);
      UnmanagedMemory.free(handle);
      throw failure;
    } finally {
      for (PinnedObject pin : pins) if (pin != null) pin.close();
      UnmanagedMemory.free(fields);
    }
  }

  /** Value contract of a field: the element contract for ARRAY, else the field itself. */
  private static Field contract(Field field) {
    if (field.kind() != Field.Kind.ARRAY) {
      kind(field);
      return field;
    }
    if (field.constraints().length() != null)
      throw new IllegalArgumentException("Unsupported JSON array length contract");
    Field element = field.constraints().element();
    if (element == null) throw new IllegalArgumentException("JSON array requires an element contract");
    if (element.nullable() || element.optional())
      throw new IllegalArgumentException("JSON array elements are never null or missing");
    kind(element);
    return element;
  }

  private static void write(VoidPointer field, Field f, Map<String, Integer> ordinals) {
    Field value = contract(f);
    var c = value.constraints();
    int kind = kind(value);
    Json.JsonScalarField.kind(field, kind);
    Json.JsonScalarField.flags(
        field,
        (f.optional() ? 0 : 1) | (f.nullable() ? 2 : 0) | (f.kind() == Field.Kind.ARRAY ? 4 : 0));
    Json.JsonScalarField.child(
        field,
        kind == 5 ? Objects.requireNonNull(ordinals.get(Objects.requireNonNull(c.recordType()))) : -1);
    long min = Long.MIN_VALUE, max = Long.MAX_VALUE;
    var width = c.integer();
    if (width != null && value.kind() == Field.Kind.INT64) {
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

  private static int kind(Field field) {
    return switch (field.kind()) {
      case BOOLEAN -> 0;
      case INT64 -> 1;
      case FLOAT64 -> 3;
      case STRING -> 4;
      case RECORD -> {
        if (field.constraints().recordType() == null)
          throw new IllegalArgumentException("JSON record field requires a record type");
        yield 5;
      }
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
      return new JsonTypedOwner(result, schemas).root();
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
