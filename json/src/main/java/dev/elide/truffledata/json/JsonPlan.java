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
 * reaches. Results own their storage independently of the plan. Compile through {@link
 * JsonDecoder#compile(Schema, Map)} on either backend, or {@link #compile(Schema, Map)} on SVM.
 */
public final class JsonPlan implements AutoCloseable {
  /** Maximum record nesting, counting the root as 1. */
  static final int MAX_DEPTH = 32;

  /** Maximum number of distinct records (root included) in one plan. */
  static final int MAX_RECORDS = 65536;

  /** Field-name bytes accepted per plan, enforced here and by the native plan. */
  static final long MAX_NAME_BYTES = 1 << 20;

  /** Typed input and index caps passed to every typed decode. */
  static final long MAX_INPUT_BYTES = 64 * 1024 * 1024;

  static final long MAX_INDEX_BYTES = 64 * 1024 * 1024;

  /**
   * Backend half of a plan: the native plan handle and typed decode. Static images contain only
   * the SVM implementation; the FFM one lives in the JVM backend library.
   */
  abstract static class Backend {
    /** Decodes bounded input into a new owner, which takes the native result. */
    abstract JsonTypedOwner decode(byte[] utf8, Schema[] schemas);

    /** Releases the native plan; called once, on the plan's thread. */
    abstract void release();
  }

  private final Schema[] schemas;
  private final Backend backend;
  private final Thread thread = Thread.currentThread();
  private boolean open = true;

  JsonPlan(Schema[] schemas, Backend backend) {
    this.schemas = schemas;
    this.backend = backend;
  }

  /** Compiles a closed scalar schema on SVM; unsupported contracts fail before allocation. */
  @TruffleBoundary
  public static JsonPlan compile(Schema schema) {
    return compile(schema, Map.of());
  }

  /**
   * Compiles a closed root schema on the static SVM backend. RECORD fields, and ARRAY fields with
   * RECORD elements, name their schema by {@code Constraints.recordType()}: a key of {@code
   * recordTypes} whose schema has that same {@code typeName}. ARRAY fields take their element
   * contract from {@code Constraints.element()}; elements are scalar or RECORD, never null,
   * missing or nested arrays. Recursive record types and nesting deeper than 32 records are
   * rejected.
   */
  @TruffleBoundary
  public static JsonPlan compile(Schema root, Map<String, Schema> recordTypes) {
    JsonSvm.requireRuntime();
    Layout layout = new Layout(root, recordTypes);
    return new JsonPlan(layout.records, SvmBackend.create(layout));
  }

  /**
   * Backend-neutral native plan table: records in ordinal order (0 is the root; each type name gets
   * one ordinal on first reach, breadth first) and one descriptor row per field. Every backend
   * writes these values unchanged, so all of them hand native code the identical table.
   */
  static final class Layout {
    static final int NO_CHILD = -1;
    static final long UNSIGNED_MAXIMUM = -1L;
    static final long MAX_UTF8_BYTES = 64 * 1024 * 1024;

    final Schema[] records;
    // First field-table row of each record; records partition the table in order.
    final int[] fieldsOffset;
    final int fieldCount;
    final byte[][] names;
    final int[] kinds;
    final int[] flags;
    final int[] children;
    final long[] minimums;
    final long[] maximums;
    final int[] floatBits;
    // 0 when the field has no length contract.
    final int[] lengthUnits;
    final long[] minimumLengths;
    final long[] maximumLengths;

    Layout(Schema root, Map<String, Schema> recordTypes) {
      var order = new ArrayList<Schema>();
      var ordinals = new HashMap<String, Integer>();
      order.add(root);
      int total = 0;
      for (int r = 0; r < order.size(); r++) {
        Schema schema = order.get(r);
        if (schema.size() > 64) throw new IllegalArgumentException("JSON scalar field limit");
        total += schema.size();
        var seen = new HashSet<String>();
        for (int i = 0; i < schema.size(); i++) {
          Field f = schema.field(i);
          if (!seen.add(f.name())) throw new IllegalArgumentException("Duplicate JSON field");
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
              if (order.size() == MAX_RECORDS)
                throw new IllegalArgumentException("JSON plan record limit");
              ordinals.put(type, order.size());
              order.add(child);
            }
          }
        }
      }
      records = order.toArray(Schema[]::new);
      fieldCount = total;
      fieldsOffset = new int[records.length];
      names = new byte[total][];
      kinds = new int[total];
      flags = new int[total];
      children = new int[total];
      minimums = new long[total];
      maximums = new long[total];
      floatBits = new int[total];
      lengthUnits = new int[total];
      minimumLengths = new long[total];
      maximumLengths = new long[total];
      long nameBytes = 0;
      int next = 0;
      for (int r = 0; r < records.length; r++) {
        Schema schema = records[r];
        fieldsOffset[r] = next;
        for (int i = 0; i < schema.size(); i++, next++) {
          Field f = schema.field(i);
          byte[] name = f.name().getBytes(StandardCharsets.UTF_8);
          nameBytes += name.length;
          if (nameBytes > MAX_NAME_BYTES)
            throw new IllegalArgumentException("JSON schema name limit");
          names[next] = name;
          row(next, f, ordinals);
        }
      }
    }

    private void row(int at, Field f, Map<String, Integer> ordinals) {
      Field value = contract(f);
      var c = value.constraints();
      int kind = kind(value);
      kinds[at] = kind;
      flags[at] =
          (f.optional() ? 0 : 1) | (f.nullable() ? 2 : 0) | (f.kind() == Field.Kind.ARRAY ? 4 : 0);
      children[at] =
          kind == 5
              ? Objects.requireNonNull(ordinals.get(Objects.requireNonNull(c.recordType())))
              : NO_CHILD;
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
      minimums[at] = min;
      maximums[at] = max;
      floatBits[at] = c.floatBits();
      var length = c.length();
      if (length != null) {
        lengthUnits[at] =
            length.unit() == dev.elide.truffledata.Constraints.LengthUnit.UTF16 ? 1 : 2;
        minimumLengths[at] = length.min();
        maximumLengths[at] = length.max();
      }
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

  /** Static SVM plan: an unmanaged plan handle and direct {@code @CFunction} calls. */
  private static final class SvmBackend extends Backend {
    private final VoidPointer handle;

    private SvmBackend(VoidPointer handle) {
      this.handle = handle;
    }

    static SvmBackend create(Layout layout) {
      int fieldCount = layout.fieldCount;
      PinnedObject[] pins = new PinnedObject[fieldCount];
      VoidPointer handle = UnmanagedMemory.calloc(Json.JsonPlan.SIZE);
      if (handle.isNull()) throw new OutOfMemoryError("JSON plan allocation");
      // The record table follows the field table in the same allocation.
      long tableBytes = (long) Math.max(1, fieldCount) * Json.JsonScalarField.SIZE;
      Pointer fields =
          UnmanagedMemory.calloc(
              WordFactory.unsigned(
                  tableBytes + (long) layout.records.length * Json.JsonRecordPlan.SIZE));
      if (fields.isNull()) {
        UnmanagedMemory.free(handle);
        throw new OutOfMemoryError("JSON schema allocation");
      }
      try {
        for (int r = 0; r < layout.records.length; r++) {
          VoidPointer plan =
              (VoidPointer)
                  fields.add(
                      WordFactory.unsigned(tableBytes + (long) r * Json.JsonRecordPlan.SIZE));
          Json.JsonRecordPlan.fields_offset(plan, layout.fieldsOffset[r]);
          Json.JsonRecordPlan.field_count(plan, layout.records[r].size());
        }
        for (int i = 0; i < fieldCount; i++) {
          byte[] name = layout.names[i];
          // An empty key still needs an addressable backing allocation.
          pins[i] = PinnedObject.create(name.length == 0 ? new byte[1] : name);
          VoidPointer field =
              (VoidPointer) fields.add(WordFactory.unsigned((long) i * Json.JsonScalarField.SIZE));
          Json.JsonScalarField.name(field, pins[i].addressOfArrayElement(0));
          Json.JsonScalarField.name_length(field, name.length);
          Json.JsonScalarField.kind(field, layout.kinds[i]);
          Json.JsonScalarField.flags(field, layout.flags[i]);
          Json.JsonScalarField.child(field, layout.children[i]);
          Json.JsonScalarField.minimum(field, layout.minimums[i]);
          Json.JsonScalarField.maximum(field, layout.maximums[i]);
          Json.JsonScalarField.unsigned_maximum(field, Layout.UNSIGNED_MAXIMUM);
          Json.JsonScalarField.max_utf8_bytes(field, Layout.MAX_UTF8_BYTES);
          Json.JsonScalarField.float_bits(field, layout.floatBits[i]);
          Json.JsonScalarField.length_unit(field, layout.lengthUnits[i]);
          Json.JsonScalarField.minimum_length(field, layout.minimumLengths[i]);
          Json.JsonScalarField.maximum_length(field, layout.maximumLengths[i]);
        }
        VoidPointer table = (VoidPointer) fields.add(WordFactory.unsigned(tableBytes));
        JsonSvm.checkStatus(
            Json.plan_create(
                table,
                layout.records.length,
                (VoidPointer) fields,
                fieldCount,
                MAX_NAME_BYTES,
                handle));
        return new SvmBackend(handle);
      } catch (RuntimeException | Error failure) {
        Json.plan_release(handle);
        UnmanagedMemory.free(handle);
        throw failure;
      } finally {
        for (PinnedObject pin : pins) if (pin != null) pin.close();
        UnmanagedMemory.free(fields);
      }
    }

    @Override
    JsonTypedOwner decode(byte[] utf8, Schema[] schemas) {
      VoidPointer result = UnmanagedMemory.calloc(Json.JsonTypedResult.SIZE);
      if (result.isNull()) throw new OutOfMemoryError("JSON result allocation");
      try {
        try (PinnedObject input = PinnedObject.create(utf8)) {
          JsonSvm.checkStatus(
              Json.typed_decode(
                  handle,
                  input.addressOfArrayElement(0),
                  utf8.length,
                  MAX_INPUT_BYTES,
                  MAX_INDEX_BYTES,
                  result));
        }
        return new SvmTypedOwner(result, schemas);
      } catch (RuntimeException | Error failure) {
        Json.typed_release(result);
        UnmanagedMemory.free(result);
        throw failure;
      }
    }

    @Override
    void release() {
      Json.plan_release(handle);
      UnmanagedMemory.free(handle);
    }
  }

  private void checkThread() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("JSON plan thread mismatch");
  }

  /**
   * Decodes one document into a scoped root record that outlives this plan. A plan compiled by a
   * {@link JsonDecoder} that has since closed rejects decoding.
   */
  @TruffleBoundary
  public JsonRecord decode(byte[] utf8) {
    checkThread();
    if (!open) throw new IllegalStateException("JSON plan closed");
    if (utf8.length == 0 || utf8.length > MAX_INPUT_BYTES)
      throw new IllegalArgumentException("JSON input limit");
    return backend.decode(utf8, schemas).root();
  }

  @Override
  @TruffleBoundary
  public void close() {
    checkThread();
    if (open) {
      open = false;
      backend.release();
    }
  }
}
