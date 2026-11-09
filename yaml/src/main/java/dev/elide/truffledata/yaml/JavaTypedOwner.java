package dev.elide.truffledata.yaml;

import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TapeFormat;
import dev.elide.truffledata.tape.TypedOwner;
import org.jspecify.annotations.Nullable;

/** Typed result of the Java kernel: the slot arena and record table as plain arrays. */
final class JavaTypedOwner extends TypedOwner {
  private final long[] bits;
  private final @Nullable String[] text;
  private final long[] present, nulls;
  private final int[] slotBase, plan;

  JavaTypedOwner(
      Schema[] schemas,
      long[] bits,
      @Nullable String[] text,
      long[] present,
      long[] nulls,
      int[] slotBase,
      int[] plan) {
    super(schemas);
    this.bits = bits;
    this.text = text;
    this.present = present;
    this.nulls = nulls;
    this.slotBase = slotBase;
    this.plan = plan;
  }

  @Override
  protected long bits(long slot) {
    return bits[Math.toIntExact(slot)];
  }

  @Override
  protected String text(long slot) {
    String value = text[Math.toIntExact(slot)];
    return value == null ? "" : value;
  }

  @Override
  protected long present(long entry) {
    return present[Math.toIntExact(entry)];
  }

  @Override
  protected long nulls(long entry) {
    return nulls[Math.toIntExact(entry)];
  }

  @Override
  protected int slotBase(long entry) {
    return slotBase[Math.toIntExact(entry)];
  }

  @Override
  protected int plan(long entry) {
    return plan[Math.toIntExact(entry)];
  }

  @Override
  protected void release() {}

  @Override
  protected TapeFormat format() {
    return YamlPlan.FORMAT;
  }
}
