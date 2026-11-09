package dev.elide.truffledata.yaml;

import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.tape.TypedOwner;
import dev.elide.truffledata.yaml.generated.YamlFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

/** JVM native plan and result lifetimes, each holding the loaded library independently. */
final class JvmPlanBackend extends YamlPlan.Backend {
  private final YamlFFM.LibraryBinding calls;
  private final Arena arena = Arena.ofConfined();
  private final MemorySegment handle;
  private final Runnable retain;
  private final Runnable dereference;

  JvmPlanBackend(
      YamlFFM.LibraryBinding calls,
      TypedLayout layout,
      YamlOptions options,
      Runnable retain,
      Runnable dereference) {
    this.calls = calls;
    this.retain = retain;
    this.dereference = dereference;
    retain.run();
    try {
      handle = arena.allocate(YamlFFM.YamlPlan_LAYOUT);
    } catch (RuntimeException | Error failure) {
      arena.close();
      dereference.run();
      throw failure;
    }
    boolean published = false;
    try (Arena scratch = Arena.ofConfined()) {
      long fieldSize = YamlFFM.YamlScalarField_LAYOUT.byteSize();
      long recordSize = YamlFFM.YamlRecordPlan_LAYOUT.byteSize();
      MemorySegment fields =
          scratch.allocate(
              Math.max(1, layout.fieldCount) * fieldSize,
              YamlFFM.YamlScalarField_LAYOUT.byteAlignment());
      MemorySegment records =
          scratch.allocate(
              layout.records.length * recordSize, YamlFFM.YamlRecordPlan_LAYOUT.byteAlignment());
      for (int r = 0; r < layout.records.length; r++) {
        long at = r * recordSize;
        records.set(
            ValueLayout.JAVA_INT,
            at + YamlFFM.YamlRecordPlan_fields_offset_OFFSET,
            layout.fieldsOffset[r]);
        records.set(
            ValueLayout.JAVA_INT,
            at + YamlFFM.YamlRecordPlan_field_count_OFFSET,
            layout.records[r].size());
      }
      for (int i = 0; i < layout.fieldCount; i++) {
        long at = i * fieldSize;
        byte[] name = layout.names[i];
        // An empty key still needs an addressable backing allocation.
        MemorySegment bytes = scratch.allocate(Math.max(1, name.length));
        MemorySegment.copy(name, 0, bytes, ValueLayout.JAVA_BYTE, 0, name.length);
        fields.set(ValueLayout.ADDRESS, at + YamlFFM.YamlScalarField_name_OFFSET, bytes);
        fields.set(
            ValueLayout.JAVA_LONG, at + YamlFFM.YamlScalarField_name_length_OFFSET, name.length);
        fields.set(ValueLayout.JAVA_INT, at + YamlFFM.YamlScalarField_kind_OFFSET, layout.kinds[i]);
        fields.set(
            ValueLayout.JAVA_INT, at + YamlFFM.YamlScalarField_flags_OFFSET, layout.flags[i]);
        fields.set(
            ValueLayout.JAVA_INT, at + YamlFFM.YamlScalarField_child_OFFSET, layout.children[i]);
        fields.set(
            ValueLayout.JAVA_LONG, at + YamlFFM.YamlScalarField_minimum_OFFSET, layout.minimums[i]);
        fields.set(
            ValueLayout.JAVA_LONG, at + YamlFFM.YamlScalarField_maximum_OFFSET, layout.maximums[i]);
        fields.set(
            ValueLayout.JAVA_LONG,
            at + YamlFFM.YamlScalarField_unsigned_maximum_OFFSET,
            TypedLayout.UNSIGNED_MAXIMUM);
        fields.set(
            ValueLayout.JAVA_LONG,
            at + YamlFFM.YamlScalarField_max_utf8_bytes_OFFSET,
            TypedLayout.MAX_UTF8_BYTES);
        fields.set(
            ValueLayout.JAVA_INT,
            at + YamlFFM.YamlScalarField_float_bits_OFFSET,
            layout.floatBits[i]);
        fields.set(
            ValueLayout.JAVA_INT,
            at + YamlFFM.YamlScalarField_length_unit_OFFSET,
            layout.lengthUnits[i]);
        fields.set(
            ValueLayout.JAVA_LONG,
            at + YamlFFM.YamlScalarField_minimum_length_OFFSET,
            layout.minimumLengths[i]);
        fields.set(
            ValueLayout.JAVA_LONG,
            at + YamlFFM.YamlScalarField_maximum_length_OFFSET,
            layout.maximumLengths[i]);
      }

      MemorySegment opts = scratch.allocate(YamlFFM.YamlOptionsStruct_LAYOUT);
      JvmSessionBackend.writeOptions(opts, options);
      int status;
      try {
        status =
            calls.plan_create(
                records,
                layout.records.length,
                fields,
                layout.fieldCount,
                TypedLayout.MAX_NAME_BYTES,
                opts,
                handle);
      } catch (Throwable failure) {
        throw YamlJvm.callFailure(failure);
      }
      YamlSvm.checkStatus(status);
      published = true;
    } finally {
      if (!published) release();
    }
  }

  @Override
  TypedOwner decode(byte[] utf8, Schema[] schemas) {
    Arena resultArena = Arena.ofConfined();
    MemorySegment result;
    try {
      result = resultArena.allocate(YamlFFM.YamlTypedResult_LAYOUT);
    } catch (RuntimeException | Error failure) {
      resultArena.close();
      throw failure;
    }
    retain.run();
    boolean published = false;
    try (Arena input = Arena.ofConfined()) {
      MemorySegment data = input.allocate(Math.max(1, utf8.length));
      MemorySegment.copy(utf8, 0, data, ValueLayout.JAVA_BYTE, 0, utf8.length);
      int status;
      try {
        status = calls.typed_decode(handle, data, utf8.length, result);
      } catch (Throwable failure) {
        throw YamlJvm.callFailure(failure);
      }
      if (status == 2 || status == 4 || status == 6 || status == 7) {
        int length = Math.min(255, result.get(ValueLayout.JAVA_INT, 128));
        String text =
            new String(
                result.asSlice(136, length).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
        YamlSvm.checkDetailed(
            status,
            text,
            result.get(ValueLayout.JAVA_LONG, 112),
            result.get(ValueLayout.JAVA_LONG, 120));
      }
      YamlSvm.checkStatus(status);
      TypedOwner owner = new JvmTypedOwner(result, schemas, () -> retire(result, resultArena));
      published = true;
      return owner;
    } finally {
      if (!published) retire(result, resultArena);
    }
  }

  private void retire(MemorySegment result, Arena resultArena) {
    try {
      calls.typed_release(result);
    } catch (Throwable failure) {
      throw YamlJvm.callFailure(failure);
    } finally {
      resultArena.close();
      dereference.run();
    }
  }

  @Override
  void release() {
    try {
      calls.plan_release(handle);
    } catch (Throwable failure) {
      throw YamlJvm.callFailure(failure);
    } finally {
      arena.close();
      dereference.run();
    }
  }
}
