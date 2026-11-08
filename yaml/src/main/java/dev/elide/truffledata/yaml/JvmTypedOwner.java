package dev.elide.truffledata.yaml;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TapeFormat;
import dev.elide.truffledata.tape.TypedOwner;
import dev.elide.truffledata.yaml.generated.YamlFFM;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

/**
 * JVM typed owner: bounded FFM segments over the immutable slot arena and record table, read with
 * the same exact-class cast as the Buffa FFM index reads so compiled code needs no interface
 * dispatch.
 */
final class JvmTypedOwner extends TypedOwner {
  // The JVM's concrete native segment class, derived without retaining any allocation.
  private static final Class<? extends MemorySegment> NATIVE_SEGMENT_TYPE =
      MemorySegment.NULL.getClass();
  private static final long SLOT_SIZE = YamlFFM.YamlScalarSlot_LAYOUT.byteSize();
  private static final long ENTRY_SIZE = YamlFFM.YamlRecordEntry_LAYOUT.byteSize();

  private final MemorySegment slots;
  private final MemorySegment records;
  private final Runnable release;

  /** Takes {@code result}; {@code release} frees it and runs once, from {@link #close()}. */
  JvmTypedOwner(MemorySegment result, Schema[] schemas, Runnable release) {
    super(schemas);
    long slotCount = result.get(ValueLayout.JAVA_LONG, YamlFFM.YamlTypedResult_count_OFFSET);
    long recordCount =
        result.get(ValueLayout.JAVA_LONG, YamlFFM.YamlTypedResult_record_count_OFFSET);
    MemorySegment slotTable = result.get(ValueLayout.ADDRESS, YamlFFM.YamlTypedResult_slots_OFFSET);
    MemorySegment recordTable =
        result.get(ValueLayout.ADDRESS, YamlFFM.YamlTypedResult_records_OFFSET);
    if (recordTable.address() == 0
        || recordCount < 1
        || slotCount < 0
        || (slotCount > 0 && slotTable.address() == 0)
        || slotCount > Long.MAX_VALUE / SLOT_SIZE
        || recordCount > Long.MAX_VALUE / ENTRY_SIZE)
      throw new IllegalStateException("YAML typed result tables missing");
    this.slots = slotTable.reinterpret(slotCount * SLOT_SIZE);
    this.records = recordTable.reinterpret(recordCount * ENTRY_SIZE);
    this.release = release;
  }

  private static long readLong(MemorySegment segment, long offset) {
    if (segment.getClass() == NATIVE_SEGMENT_TYPE)
      return CompilerDirectives.castExact(segment, NATIVE_SEGMENT_TYPE)
          .get(ValueLayout.JAVA_LONG, offset);
    return readOtherLong(segment, offset);
  }

  @TruffleBoundary
  private static long readOtherLong(MemorySegment segment, long offset) {
    return segment.get(ValueLayout.JAVA_LONG, offset);
  }

  private static int readInt(MemorySegment segment, long offset) {
    if (segment.getClass() == NATIVE_SEGMENT_TYPE)
      return CompilerDirectives.castExact(segment, NATIVE_SEGMENT_TYPE)
          .get(ValueLayout.JAVA_INT, offset);
    return readOtherInt(segment, offset);
  }

  @TruffleBoundary
  private static int readOtherInt(MemorySegment segment, long offset) {
    return segment.get(ValueLayout.JAVA_INT, offset);
  }

  @Override
  protected long bits(long slot) {
    return readLong(slots, slot * SLOT_SIZE + YamlFFM.YamlScalarSlot_bits_OFFSET);
  }

  @Override
  protected String text(long slot) {
    return copyText(slots, slot * SLOT_SIZE);
  }

  @TruffleBoundary
  private static String copyText(MemorySegment slots, long offset) {
    long length =
        slots.get(ValueLayout.JAVA_LONG, offset + YamlFFM.YamlScalarSlot_text_length_OFFSET);
    if (length < 0 || length > Integer.MAX_VALUE - 8)
      throw new IllegalStateException("YAML typed text length");
    MemorySegment text =
        slots
            .get(ValueLayout.ADDRESS, offset + YamlFFM.YamlScalarSlot_text_OFFSET)
            .reinterpret(length);
    return new String(text.toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
  }

  @Override
  protected long present(long entry) {
    return readLong(records, entry * ENTRY_SIZE + YamlFFM.YamlRecordEntry_present_OFFSET);
  }

  @Override
  protected long nulls(long entry) {
    return readLong(records, entry * ENTRY_SIZE + YamlFFM.YamlRecordEntry_nulls_OFFSET);
  }

  @Override
  protected int slotBase(long entry) {
    return readInt(records, entry * ENTRY_SIZE + YamlFFM.YamlRecordEntry_slots_OFFSET);
  }

  @Override
  protected int plan(long entry) {
    return readInt(records, entry * ENTRY_SIZE + YamlFFM.YamlRecordEntry_plan_OFFSET);
  }

  @Override
  protected void release() {
    release.run();
  }

  @Override
  protected TapeFormat format() {
    return YamlPlan.FORMAT;
  }
}
