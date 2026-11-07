package dev.elide.truffledata.tape;

import com.oracle.truffle.api.Assumption;

/** Immutable, thread-safe, heap-owned tape index. Never closes; reads never touch native memory. */
public final class TapeSnapshot extends TapeOwner {
  private final long[] spans;
  // The heap tape is the owner's `tape`; holding the format instead keeps snapshots at their size.
  private final TapeFormat format;
  private final byte @org.jspecify.annotations.Nullable [] lexicalInput;

  private TapeSnapshot(TapeReader tape, long[] spans, TapeFormat format) {
    this(tape, spans, format, null);
  }

  private TapeSnapshot(TapeReader tape, long[] spans, TapeFormat format, byte @org.jspecify.annotations.Nullable [] lexicalInput) {
    super(tape, null);
    this.lexicalInput = lexicalInput;
    this.spans = spans;
    this.format = checkFormat(tape, format);
  }

  /**
   * Detaches the subtree at {@code root}; lexical spans keep their offsets into the copied input
   * and the heap tape copies the key-hash side table alongside the nodes. Alias tables (YAML) are
   * copied as references, never expanded: per-node words are sliced to the subtree and targets
   * re-based to it, so an alias whose target lies outside the subtree throws when read.
   */
  static TapeSnapshot copy(TapeOwner owner, long root) {
    owner.check();
    if (!(owner.tape instanceof LegacyTapeReader legacy))
      return new TapeSnapshot(HeapTape.copy(owner.tape, root), new long[0], owner.format());
    TapeReader heap = new LegacyTapeReader(HeapValueTape.copy(legacy.value, legacy.source, root));
    int count = Math.toIntExact(heap.nodeCount());
    long[] spans = new long[count];
    for (int i = 0; i < count; i++) spans[i] = owner.lexical(root + i);
    TapeFormat format = owner.format().slice(root, count);
    byte[] source = null;
    if (format.hasAliases()) {
      long extent = 0;
      for (long span : spans) extent = Math.max(extent, (span >>> 32) + (span & 0xffffffffL));
      source = new byte[Math.toIntExact(extent)];
      for (int i = 0; i < source.length; i++) source[i] = owner.lexicalByte(i);
    }
    return new TapeSnapshot(heap, spans, format, source);
  }

  public long nodeCount() {
    if (tape instanceof LegacyTapeReader) return tape.nodeCount();
    long count = 0;
    for (long i = 0; i < tape.nodeCount(); i = tape.step(i)) count++;
    return count;
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
    return tape instanceof LegacyTapeReader ? spans[Math.toIntExact(node)] : tape.span(node);
  }

  @Override
  protected byte lexicalByte(long offset) {
    byte[] source = lexicalInput;
    if (source != null) return source[Math.toIntExact(offset)];
    return tape instanceof LegacyTapeReader legacy ? ((HeapValueTape) legacy.value).inputByte(offset) : tape.inputByte(offset);
  }

  /** A snapshot of a snapshot shares nothing mutable, so the same storage is returned. */
  @Override
  public TapeSnapshot snapshot() {
    return this;
  }
}
