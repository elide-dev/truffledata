package dev.elide.truffledata.tape;

import com.oracle.truffle.api.Assumption;

/** Immutable, thread-safe, heap-owned tape index. Never closes; reads never touch native memory. */
public final class TapeSnapshot extends TapeOwner {
  private final long[] spans;
  // The heap tape is the owner's `tape`; holding the format instead keeps snapshots at their size.
  private final TapeFormat format;

  private TapeSnapshot(HeapValueTape tape, long[] spans, TapeFormat format) {
    super(tape, null);
    this.spans = spans;
    this.format = checkFormat(tape, format);
  }

  /**
   * Detaches the subtree at {@code root}; lexical spans keep their offsets into the copied input
   * and the heap tape copies the key-hash side table alongside the nodes. Alias tables (YAML) are
   * copied as references, never expanded: per-node words are sliced to the subtree and targets
   * re-based to it, so an alias whose target lies outside the subtree throws when read.
   */
  static TapeSnapshot copy(TapeOwner owner, TapeSource source, long root) {
    owner.check();
    HeapValueTape heap = HeapValueTape.copy(owner.tape, source, root);
    int count = Math.toIntExact(heap.nodeCount());
    long[] spans = new long[count];
    for (int i = 0; i < count; i++) spans[i] = owner.lexical(root + i);
    TapeFormat format = owner.format();
    int[] meta = format.meta;
    long[] targets = format.aliasTargets;
    if (meta != null && targets != null) {
      int[] slice = new int[count];
      System.arraycopy(meta, Math.toIntExact(root), slice, 0, count);
      long[] rebased = new long[targets.length];
      for (int i = 0; i < targets.length; i++) rebased[i] = targets[i] - root;
      format = format.withAliases(rebased, slice);
    }
    return new TapeSnapshot(heap, spans, format);
  }

  public long nodeCount() {
    return tape.nodeCount();
  }

  @Override
  public TapeFormat format() {
    return format;
  }

  @Override
  public Assumption assumption() {
    return Assumption.ALWAYS_VALID;
  }

  @Override
  protected long lexical(long node) {
    return spans[Math.toIntExact(node)];
  }

  @Override
  protected byte lexicalByte(long offset) {
    return ((HeapValueTape) tape).inputByte(offset);
  }

  /** A snapshot of a snapshot shares nothing mutable, so the same storage is returned. */
  @Override
  public TapeSnapshot snapshot() {
    return this;
  }
}
