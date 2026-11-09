package dev.elide.truffledata.yaml;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TapeFormat;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.tape.TypedOwner;
import dev.elide.truffledata.tape.TypedRecord;
import dev.elide.truffledata.yaml.generated.Yaml;
import java.util.Map;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/**
 * Reusable, thread-confined native typed plan: a closed root record plus the record types it
 * reaches. Results own their storage independently of the plan. Compile through {@link
 * YamlSession#compile(Schema, Map)} on either backend, or {@link #compile(Schema, Map)} on SVM.
 */
public final class YamlPlan implements AutoCloseable {
  static final TapeFormat FORMAT = TapeFormat.of("YAML");
  static final long MAX_NAME_BYTES = TypedLayout.MAX_NAME_BYTES;

  /**
   * Backend half of a plan: the native plan handle and typed decode. Static images contain only the
   * SVM implementation; the FFM one lives in the JVM backend library.
   */
  abstract static class Backend {
    /** Decodes bounded input into a new owner, which takes the native result. */
    abstract TypedOwner decode(byte[] utf8, Schema[] schemas);

    /** Releases the native plan; called once, on the plan's thread. */
    abstract void release();
  }

  private final long maxInputBytes;
  private final Schema[] schemas;
  private final Backend backend;
  private final Thread thread = Thread.currentThread();
  private boolean open = true;

  YamlPlan(Schema[] schemas, Backend backend, long maxInputBytes) {
    this.maxInputBytes = maxInputBytes;
    this.schemas = schemas;
    this.backend = backend;
  }

  /** Compiles a closed scalar schema on SVM; unsupported contracts fail before allocation. */
  @TruffleBoundary
  public static YamlPlan compile(Schema schema) {
    return compile(schema, Map.of());
  }

  /**
   * Compiles a closed root schema on the static SVM backend. RECORD fields, and ARRAY fields with
   * RECORD elements, name their schema by {@code Constraints.recordType()}: a key of {@code
   * recordTypes} whose schema has that same {@code typeName}. ARRAY fields take their element
   * contract from {@code Constraints.element()}; elements are scalar or RECORD, never null, missing
   * or nested arrays. Recursive record types and nesting deeper than 32 records are rejected.
   */
  @TruffleBoundary
  public static YamlPlan compile(Schema root, Map<String, Schema> recordTypes) {
    YamlSvm.requireRuntime();
    TypedLayout layout = new TypedLayout(root, recordTypes, YamlPlan.FORMAT);
    return create(layout, YamlOptions.DEFAULT);
  }

  static YamlPlan create(TypedLayout layout, YamlOptions options) {
    return new YamlPlan(
        layout.records, SvmBackend.create(layout, options), options.limits().maxInputBytes());
  }

  /** Static SVM plan: an unmanaged plan handle and direct {@code @CFunction} calls. */
  private static final class SvmBackend extends Backend {
    private final VoidPointer handle;

    private SvmBackend(VoidPointer handle) {
      this.handle = handle;
    }

    static SvmBackend create(TypedLayout layout, YamlOptions options) {
      int fieldCount = layout.fieldCount;
      PinnedObject[] pins = new PinnedObject[fieldCount];
      VoidPointer handle = UnmanagedMemory.calloc(Yaml.YamlPlan.SIZE);
      if (handle.isNull()) throw new OutOfMemoryError("YAML plan allocation");
      // The record table follows the field table in the same allocation.
      long tableBytes = (long) Math.max(1, fieldCount) * Yaml.YamlScalarField.SIZE;
      Pointer fields =
          UnmanagedMemory.calloc(
              WordFactory.unsigned(
                  tableBytes
                      + (long) layout.records.length * Yaml.YamlRecordPlan.SIZE
                      + Yaml.YamlOptionsStruct.SIZE));
      if (fields.isNull()) {
        UnmanagedMemory.free(handle);
        throw new OutOfMemoryError("YAML schema allocation");
      }
      try {
        for (int r = 0; r < layout.records.length; r++) {
          VoidPointer plan =
              (VoidPointer)
                  fields.add(
                      WordFactory.unsigned(tableBytes + (long) r * Yaml.YamlRecordPlan.SIZE));
          Yaml.YamlRecordPlan.fields_offset(plan, layout.fieldsOffset[r]);
          Yaml.YamlRecordPlan.field_count(plan, layout.records[r].size());
        }
        for (int i = 0; i < fieldCount; i++) {
          byte[] name = layout.names[i];
          // An empty key still needs an addressable backing allocation.
          pins[i] = PinnedObject.create(name.length == 0 ? new byte[1] : name);
          VoidPointer field =
              (VoidPointer) fields.add(WordFactory.unsigned((long) i * Yaml.YamlScalarField.SIZE));
          Yaml.YamlScalarField.name(field, pins[i].addressOfArrayElement(0));
          Yaml.YamlScalarField.name_length(field, name.length);
          Yaml.YamlScalarField.kind(field, layout.kinds[i]);
          Yaml.YamlScalarField.flags(field, layout.flags[i]);
          Yaml.YamlScalarField.child(field, layout.children[i]);
          Yaml.YamlScalarField.minimum(field, layout.minimums[i]);
          Yaml.YamlScalarField.maximum(field, layout.maximums[i]);
          Yaml.YamlScalarField.unsigned_maximum(field, TypedLayout.UNSIGNED_MAXIMUM);
          Yaml.YamlScalarField.max_utf8_bytes(field, TypedLayout.MAX_UTF8_BYTES);
          Yaml.YamlScalarField.float_bits(field, layout.floatBits[i]);
          Yaml.YamlScalarField.length_unit(field, layout.lengthUnits[i]);
          Yaml.YamlScalarField.minimum_length(field, layout.minimumLengths[i]);
          Yaml.YamlScalarField.maximum_length(field, layout.maximumLengths[i]);
        }
        VoidPointer table = (VoidPointer) fields.add(WordFactory.unsigned(tableBytes));
        Pointer opts =
            fields.add(
                WordFactory.unsigned(
                    tableBytes + (long) layout.records.length * Yaml.YamlRecordPlan.SIZE));
        SvmSessionBackend.writeOptions(opts, options);
        YamlSvm.checkStatus(
            Yaml.plan_create(
                table,
                layout.records.length,
                (VoidPointer) fields,
                fieldCount,
                MAX_NAME_BYTES,
                (VoidPointer) opts,
                handle));
        return new SvmBackend(handle);
      } catch (RuntimeException | Error failure) {
        Yaml.plan_release(handle);
        UnmanagedMemory.free(handle);
        throw failure;
      } finally {
        for (PinnedObject pin : pins) if (pin != null) pin.close();
        UnmanagedMemory.free(fields);
      }
    }

    @Override
    TypedOwner decode(byte[] utf8, Schema[] schemas) {
      VoidPointer result = UnmanagedMemory.calloc(Yaml.YamlTypedResult.SIZE);
      if (result.isNull()) throw new OutOfMemoryError("YAML result allocation");
      try {
        try (PinnedObject input = PinnedObject.create(utf8)) {
          SvmSessionBackend.checkTyped(
              Yaml.typed_decode(handle, input.addressOfArrayElement(0), utf8.length, result),
              result);
        }
        return new SvmTypedOwner(result, schemas);
      } catch (RuntimeException | Error failure) {
        Yaml.typed_release(result);
        UnmanagedMemory.free(result);
        throw failure;
      }
    }

    @Override
    void release() {
      Yaml.plan_release(handle);
      UnmanagedMemory.free(handle);
    }
  }

  private void checkThread() {
    if (Thread.currentThread() != thread)
      throw new IllegalStateException("YAML plan thread mismatch");
  }

  /**
   * Decodes one document into a scoped root record that outlives this plan. The plan and its
   * results remain valid after the compiling session closes.
   */
  @TruffleBoundary
  public TypedRecord decode(byte[] utf8) {
    checkThread();
    if (!open) throw new IllegalStateException("YAML plan closed");
    if (utf8.length == 0 || utf8.length > maxInputBytes)
      throw new IllegalArgumentException("YAML input limit");
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
