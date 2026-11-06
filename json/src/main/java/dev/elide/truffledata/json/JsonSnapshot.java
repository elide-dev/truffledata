package dev.elide.truffledata.json;

import com.oracle.truffle.api.Assumption;

/** Immutable, thread-safe, heap-owned JSON index. Never closes; reads never touch native memory. */
public final class JsonSnapshot extends JsonOwner {
  private final long[] spans;
  private final int[] meta;
  private final HeapValueTape heap;

  private JsonSnapshot(HeapValueTape tape, long[] spans, int[] meta) {
    super(tape, null);
    this.heap = tape;
    this.spans = spans;
    this.meta = meta;
  }

  /**
   * Detaches the subtree at {@code root}; lexical spans keep their offsets into the copied input
   * and the key-hash side table is copied alongside them.
   */
  static JsonSnapshot copy(JsonOwner owner, TapeSource source, long root) {
    owner.check();
    HeapValueTape heap = HeapValueTape.copy(owner.tape, source, root);
    int count = Math.toIntExact(heap.nodeCount());
    long[] spans = new long[count];
    int[] meta = new int[count];
    for (int i = 0; i < count; i++) {
      spans[i] = owner.lexical(root + i);
      meta[i] = owner.meta(root + i);
    }
    return new JsonSnapshot(heap, spans, meta);
  }

  public long nodeCount() {
    return heap.nodeCount();
  }

  @Override
  public Assumption assumption() {
    return Assumption.ALWAYS_VALID;
  }

  @Override
  long lexical(long node) {
    return spans[Math.toIntExact(node)];
  }

  @Override
  int meta(long node) {
    return meta[Math.toIntExact(node)];
  }

  @Override
  byte lexicalByte(long offset) {
    return heap.inputByte(offset);
  }

  /** A snapshot of a snapshot shares nothing mutable, so the same storage is returned. */
  @Override
  public JsonSnapshot snapshot() {
    return this;
  }
}
