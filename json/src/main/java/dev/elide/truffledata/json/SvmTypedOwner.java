package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.json.generated.Json;
import dev.elide.truffledata.tape.TapeFormat;
import dev.elide.truffledata.tape.TypedOwner;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/**
 * Static SVM typed owner. Native addresses are read once at publication, so reads touch only final
 * fields and the immutable arenas through generated word accessors.
 */
final class SvmTypedOwner extends TypedOwner {
  private final VoidPointer result;
  // Raw addresses of the immutable slot arena and record table; valid until release.
  private final long slots;
  private final long records;

  SvmTypedOwner(VoidPointer result, Schema[] schemas) {
    super(schemas);
    this.result = result;
    this.slots = Json.JsonTypedResult.slots(result).rawValue();
    long slotCount = Json.JsonTypedResult.count(result);
    this.records = Json.JsonTypedResult.records(result).rawValue();
    long recordCount = Json.JsonTypedResult.record_count(result);
    if (records == 0 || recordCount < 1 || (slotCount > 0 && slots == 0))
      throw new IllegalStateException("JSON typed result tables missing");
  }

  private long slot(long index) {
    return slots + index * Json.JsonScalarSlot.SIZE;
  }

  private long entry(long index) {
    return records + index * Json.JsonRecordEntry.SIZE;
  }

  @Override
  protected long bits(long slot) {
    return Json.JsonScalarSlot.bits(WordFactory.<VoidPointer>pointer(slot(slot)));
  }

  @Override
  protected String text(long slot) {
    return copyText(slot(slot));
  }

  // Static over primitives: no view or owner escapes into the boundary.
  @TruffleBoundary
  private static String copyText(long address) {
    VoidPointer pointer = WordFactory.pointer(address);
    int length = Math.toIntExact(Json.JsonScalarSlot.text_length(pointer));
    Pointer text = (Pointer) Json.JsonScalarSlot.text(pointer);
    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) bytes[i] = text.readByte(i);
    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
  }

  @Override
  protected long present(long entry) {
    return Json.JsonRecordEntry.present(WordFactory.<VoidPointer>pointer(entry(entry)));
  }

  @Override
  protected long nulls(long entry) {
    return Json.JsonRecordEntry.nulls(WordFactory.<VoidPointer>pointer(entry(entry)));
  }

  @Override
  protected int slotBase(long entry) {
    return Json.JsonRecordEntry.slots(WordFactory.<VoidPointer>pointer(entry(entry)));
  }

  @Override
  protected int plan(long entry) {
    return Json.JsonRecordEntry.plan(WordFactory.<VoidPointer>pointer(entry(entry)));
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
