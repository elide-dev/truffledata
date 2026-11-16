package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.json.generated.Json;
import dev.elide.truffledata.tape.TapeFormat;
import dev.elide.truffledata.tape.TypedOwner;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

/**
 * Static SVM typed owner. Native addresses are read once at publication, so reads touch only final
 * fields and the immutable arenas through generated record accessors: the slot arena and record
 * table are json.seam counted views ({@code JsonTypedResult.slots}/{@code count}, {@code
 * records}/{@code record_count}), indexed with the element interfaces' {@code addressOf}.
 */
final class SvmTypedOwner extends TypedOwner {
  private final Json.JsonTypedResult result;
  // The immutable slot arena and record table; valid until release.
  private final Json.JsonScalarSlot slots;
  private final Json.JsonRecordEntry records;

  SvmTypedOwner(Json.JsonTypedResult result, Schema[] schemas) {
    super(schemas);
    this.result = result;
    this.slots = result.slots();
    long slotCount = result.count();
    this.records = result.records();
    long recordCount = result.record_count();
    if (records.isNull() || recordCount < 1 || (slotCount > 0 && slots.isNull()))
      throw new IllegalStateException("JSON typed result tables missing");
  }

  @Override
  protected long bits(long slot) {
    return slots.addressOf(slot).bits();
  }

  @Override
  protected String text(long slot) {
    return copyText(slots.addressOf(slot).rawValue());
  }

  // Static over primitives: no view or owner escapes into the boundary.
  @TruffleBoundary
  private static String copyText(long address) {
    Json.JsonScalarSlot slot = WordFactory.pointer(address);
    int length = Math.toIntExact(slot.text_length());
    CCharPointer text = slot.text();
    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) bytes[i] = text.read(i);
    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
  }

  @Override
  protected long present(long entry) {
    return records.addressOf(entry).present();
  }

  @Override
  protected long nulls(long entry) {
    return records.addressOf(entry).nulls();
  }

  @Override
  protected int slotBase(long entry) {
    return records.addressOf(entry).slots();
  }

  @Override
  protected int plan(long entry) {
    return records.addressOf(entry).plan();
  }

  @Override
  protected void release() {
    Json.typed_release(result);
    UnmanagedMemory.free(result);
  }

  @Override
  protected TapeFormat format() {
    return JsonMessage.FORMAT;
  }
}
