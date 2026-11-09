package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.json.generated.Json;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.tape.TypedRecord;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/**
 * Reusable, thread-confined native typed plan: a closed root record plus the record types it
 * reaches. Results own their storage independently of the plan. Compile through {@link
 * JsonDecoder#compile(Schema, Map)} on either backend, or {@link #compile(Schema, Map)} on SVM.
 *
 * <p>A plan whose root has only INT64, FLOAT64, BOOLEAN and EXACT fields decodes into an owned
 * {@link TypedRecord}: the root's values are copied once and the native result is released before
 * {@link #decode} returns. Plans with STRING, RECORD or ARRAY fields return records over the
 * retained native result. Both are confined to the decoding thread and fail every read once
 * closed.
 */
public final class JsonPlan implements AutoCloseable {
  /** Maximum record nesting, counting the root as 1. */
  static final int MAX_DEPTH = TypedLayout.MAX_DEPTH;

  /** Maximum number of distinct records (root included) in one plan. */
  static final int MAX_RECORDS = TypedLayout.MAX_RECORDS;

  /** Field-name bytes accepted per plan, enforced here and by the native plan. */
  static final long MAX_NAME_BYTES = TypedLayout.MAX_NAME_BYTES;

  /** Typed input and index caps passed to every typed decode. */
  static final long MAX_INPUT_BYTES = 64 * 1024 * 1024;

  static final long MAX_INDEX_BYTES = 64 * 1024 * 1024;

  /**
   * Backend half of a plan: the native plan handle and typed decode. Static images contain only
   * the SVM implementation; the FFM one lives in the JVM backend library.
   */
  abstract static class Backend {
    /**
     * Decodes bounded input into a root record: an owned copy when {@code scalars} is non-null
     * (the native result is released before returning), else over a new owner that takes the
     * native result.
     */
    abstract TypedRecord decode(
        byte[] utf8, Schema[] schemas, TypedRecord.@Nullable Scalars scalars);

    /** Releases the native plan; called once, on the plan's thread. */
    abstract void release();
  }

  private final Schema[] schemas;
  // Owned-record factory of a scalar-only plan; null when results stay native.
  private final TypedRecord.@Nullable Scalars scalars;
  private final Backend backend;
  private final Thread thread = Thread.currentThread();
  private boolean open = true;

  JsonPlan(TypedLayout layout, Backend backend) {
    this.schemas = layout.records;
    this.scalars = layout.scalars();
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
    TypedLayout layout = new TypedLayout(root, recordTypes, JsonMessage.FORMAT);
    return new JsonPlan(layout, SvmBackend.create(layout));
  }

  /** Static SVM plan: an unmanaged plan handle and direct {@code @CFunction} calls. */
  private static final class SvmBackend extends Backend {
    private final VoidPointer handle;

    private SvmBackend(VoidPointer handle) {
      this.handle = handle;
    }

    static SvmBackend create(TypedLayout layout) {
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
          Json.JsonScalarField.unsigned_maximum(field, TypedLayout.UNSIGNED_MAXIMUM);
          Json.JsonScalarField.max_utf8_bytes(field, TypedLayout.MAX_UTF8_BYTES);
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
    TypedRecord decode(byte[] utf8, Schema[] schemas, TypedRecord.@Nullable Scalars scalars) {
      VoidPointer result = UnmanagedMemory.calloc(Json.JsonTypedResult.SIZE);
      if (result.isNull()) throw new OutOfMemoryError("JSON result allocation");
      // Set once an owner has taken the result; otherwise it is released before returning.
      boolean taken = false;
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
        if (scalars != null) return SvmTypedOwner.copyRoot(result, scalars);
        SvmTypedOwner owner = new SvmTypedOwner(result, schemas);
        taken = true;
        return owner.root();
      } finally {
        if (!taken) {
          Json.typed_release(result);
          UnmanagedMemory.free(result);
        }
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
  public TypedRecord decode(byte[] utf8) {
    checkThread();
    if (!open) throw new IllegalStateException("JSON plan closed");
    if (utf8.length == 0 || utf8.length > MAX_INPUT_BYTES)
      throw new IllegalArgumentException("JSON input limit");
    return backend.decode(utf8, schemas, scalars);
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
