package dev.elide.truffledata.json;

import com.oracle.truffle.api.Assumption;

/** Immutable, thread-safe, heap-owned JSON index. Never closes; reads never touch native memory. */
public final class JsonSnapshot extends JsonOwner {
  private final long[] spans;
  private final HeapValueTape heap;

  private JsonSnapshot(HeapValueTape tape, long[] spans) {
    super(tape, null);
    this.heap = tape;
    this.spans = spans;
  }

  /**
   * Detaches the subtree at {@code root}; lexical spans keep their offsets into the copied input
   * and the heap tape copies the key-hash side table alongside the nodes.
   */
  static JsonSnapshot copy(JsonOwner owner, TapeSource source, long root) {
    owner.check();
    HeapValueTape heap = HeapValueTape.copy(owner.tape, source, root);
    int count = Math.toIntExact(heap.nodeCount());
    long[] spans = new long[count];
    for (int i = 0; i < count; i++) spans[i] = owner.lexical(root + i);
    return new JsonSnapshot(heap, spans);
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
  byte lexicalByte(long offset) {
    return heap.inputByte(offset);
  }

  /** A snapshot of a snapshot shares nothing mutable, so the same storage is returned. */
  @Override
  public JsonSnapshot snapshot() {
    return this;
  }
}
