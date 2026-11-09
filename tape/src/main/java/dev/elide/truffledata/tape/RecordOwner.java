package dev.elide.truffledata.tape;

import dev.elide.truffledata.Schema;

/**
 * Owner of an owned {@link TypedRecord} (a scalar-only plan's decode), created when the record's
 * {@link TypedRecord#owner()} is first requested. The record holds its values and lifetime state:
 * closing either closes both, and {@link #root()} returns the record itself.
 */
final class RecordOwner extends TypedOwner {
  private final TypedRecord record;

  RecordOwner(TypedRecord record, Schema schema, Thread thread) {
    super(new Schema[] {schema}, thread);
    this.record = record;
  }

  @Override
  TypedRecord newRoot() {
    return record;
  }

  @Override
  protected TapeFormat format() {
    return record.ownedFormat();
  }

  // The owned record answers for entry 0 only; it has no text slots and no other entries.
  @Override
  protected long bits(long slot) {
    return record.ownedBits(slot);
  }

  @Override
  protected String text(long slot) {
    throw new IllegalStateException("Owned typed record has no text");
  }

  @Override
  protected long present(long entry) {
    requireRoot(entry);
    return record.ownedPresent();
  }

  @Override
  protected long nulls(long entry) {
    requireRoot(entry);
    return record.ownedNulls();
  }

  @Override
  protected int slotBase(long entry) {
    requireRoot(entry);
    return 0;
  }

  @Override
  protected int plan(long entry) {
    requireRoot(entry);
    return 0;
  }

  private static void requireRoot(long entry) {
    if (entry != 0) throw new IndexOutOfBoundsException("Owned typed record entry");
  }

  @Override
  protected void release() {
    record.markClosed();
  }
}
