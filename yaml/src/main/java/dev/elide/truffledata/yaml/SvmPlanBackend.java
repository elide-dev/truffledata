package dev.elide.truffledata.yaml;

import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.tape.TypedOwner;
import dev.elide.truffledata.yaml.generated.Yaml;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/** Static SVM plan: an unmanaged plan handle and direct {@code @CFunction} calls. */
final class SvmPlanBackend extends YamlPlan.Backend {

  private final VoidPointer handle;

  private SvmPlanBackend(VoidPointer handle) {
    this.handle = handle;
  }

  static YamlPlan create(TypedLayout layout, YamlOptions options) {
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
      YamlStatus.checkStatus(
          Yaml.plan_create(
              table,
              layout.records.length,
              (VoidPointer) fields,
              fieldCount,
              YamlPlan.MAX_NAME_BYTES,
              (VoidPointer) opts,
              handle));
      return new YamlPlan(layout.records, new SvmPlanBackend(handle), options.limits().maxInputBytes());
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
