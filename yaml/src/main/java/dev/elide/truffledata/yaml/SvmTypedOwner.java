package dev.elide.truffledata.yaml;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TapeFormat;
import dev.elide.truffledata.tape.TypedOwner;
import dev.elide.truffledata.yaml.generated.Yaml;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.word.WordFactory;

/**
 * Static SVM typed owner. Native addresses are read once at publication, so reads touch only final
 * fields and the immutable arenas through generated word accessors.
 */
final class SvmTypedOwner extends TypedOwner {
  private final Yaml.YamlTypedResult result;
  // Raw addresses of the immutable slot arena and record table; valid until release.
  private final long slots;
  private final long records;

  SvmTypedOwner(Yaml.YamlTypedResult result, Schema[] schemas) {
    super(schemas);
    this.result = result;
    this.slots = result.slots().rawValue();
    long slotCount = result.count();
    this.records = result.records().rawValue();
    long recordCount = result.record_count();
    if (records == 0 || recordCount < 1 || (slotCount > 0 && slots == 0))
      throw new IllegalStateException("YAML typed result tables missing");
  }

  // Elements of the counted views slots[count] and records[record_count], from the addresses read
  // at publication (the generated YamlTypedResult_*At would load the table pointer on every read).
  private Yaml.YamlScalarSlot slot(long index) {
    return WordFactory.<Yaml.YamlScalarSlot>pointer(slots).addressOf(index);
  }

  private Yaml.YamlRecordEntry entry(long index) {
    return WordFactory.<Yaml.YamlRecordEntry>pointer(records).addressOf(index);
  }

  @Override
  protected long bits(long slot) {
    return slot(slot).bits();
  }

  @Override
  protected String text(long slot) {
    return copyText(slot(slot).rawValue());
  }

  // Static over primitives: no view or owner escapes into the boundary.
  @TruffleBoundary
  private static String copyText(long address) {
    Yaml.YamlScalarSlot slot = WordFactory.pointer(address);
    int length = Math.toIntExact(slot.text_length());
    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) bytes[i] = Yaml.YamlScalarSlot_textAt(slot, i);
    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
  }

  @Override
  protected long present(long entry) {
    return entry(entry).present();
  }

  @Override
  protected long nulls(long entry) {
    return entry(entry).nulls();
  }

  @Override
  protected int slotBase(long entry) {
    return entry(entry).slots();
  }

  @Override
  protected int plan(long entry) {
    return entry(entry).plan();
  }

  @Override
  protected void release() {
    Yaml.typed_release(result);
    UnmanagedMemory.free(result);
  }

  @Override
  protected TapeFormat format() {
    return YamlPlan.FORMAT;
  }
}
