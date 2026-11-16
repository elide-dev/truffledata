package dev.elide.truffledata.yaml;

import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.tape.TypedOwner;
import dev.elide.truffledata.yaml.generated.Yaml;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/** Static SVM plan: an unmanaged plan handle and direct {@code @CFunction} calls. */
final class SvmPlanBackend extends YamlPlan.Backend {

  private final Yaml.YamlPlan handle;

  private SvmPlanBackend(Yaml.YamlPlan handle) {
    this.handle = handle;
  }

  static YamlPlan create(TypedLayout layout, YamlOptions options) {
    int fieldCount = layout.fieldCount;
    PinnedObject[] pins = new PinnedObject[fieldCount];
    Yaml.YamlPlan handle = UnmanagedMemory.calloc(Yaml.YamlPlan.SIZE);
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
      // plan_create's arrays are import parameters, not counted record fields, so their element
      // records have no generated addressOf(long): index them by SIZE.
      Yaml.YamlScalarField table = (Yaml.YamlScalarField) fields;
      Yaml.YamlRecordPlan plans = (Yaml.YamlRecordPlan) fields.add(WordFactory.unsigned(tableBytes));
      for (int r = 0; r < layout.records.length; r++) {
        Yaml.YamlRecordPlan plan =
            (Yaml.YamlRecordPlan)
                fields.add(
                    WordFactory.unsigned(tableBytes + (long) r * Yaml.YamlRecordPlan.SIZE));
        plan.fields_offset(layout.fieldsOffset[r]);
        plan.field_count(layout.records[r].size());
      }
      for (int i = 0; i < fieldCount; i++) {
        byte[] name = layout.names[i];
        // An empty key still needs an addressable backing allocation.
        pins[i] = PinnedObject.create(name.length == 0 ? new byte[1] : name);
        Yaml.YamlScalarField field =
            (Yaml.YamlScalarField)
                fields.add(WordFactory.unsigned((long) i * Yaml.YamlScalarField.SIZE));
        field.name(pins[i].<CCharPointer>addressOfArrayElement(0));
        field.name_length(name.length);
        field.kind(layout.kinds[i]);
        field.flags(layout.flags[i]);
        field.child(layout.children[i]);
        field.minimum(layout.minimums[i]);
        field.maximum(layout.maximums[i]);
        field.unsigned_maximum(TypedLayout.UNSIGNED_MAXIMUM);
        field.max_utf8_bytes(TypedLayout.MAX_UTF8_BYTES);
        field.float_bits(layout.floatBits[i]);
        field.length_unit(layout.lengthUnits[i]);
        field.minimum_length(layout.minimumLengths[i]);
        field.maximum_length(layout.maximumLengths[i]);
      }
      Yaml.YamlOptionsStruct opts =
          (Yaml.YamlOptionsStruct)
              fields.add(
                  WordFactory.unsigned(
                      tableBytes + (long) layout.records.length * Yaml.YamlRecordPlan.SIZE));
      SvmSessionBackend.writeOptions(opts, options);
      YamlStatus.checkStatus(
          Yaml.plan_create(
              plans,
              layout.records.length,
              table,
              fieldCount,
              YamlPlan.MAX_NAME_BYTES,
              opts,
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
    Yaml.YamlTypedResult result = UnmanagedMemory.calloc(Yaml.YamlTypedResult.SIZE);
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

  // Hook for the opt-in Myna cells route (yaml/cells, CellsSvmPlanBackend); never called here.
  Yaml.YamlPlan handle() {
    return handle;
  }

  @Override
  void release() {
    Yaml.plan_release(handle);
    UnmanagedMemory.free(handle);
  }
}
