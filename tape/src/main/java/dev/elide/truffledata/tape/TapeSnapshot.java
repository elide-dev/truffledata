package dev.elide.truffledata.tape;

import com.oracle.truffle.api.Assumption;

/** Immutable, thread-safe, heap-owned tape index. Never closes; reads never touch native memory. */
public class TapeSnapshot extends TapeOwner {
  // The heap tape is the owner's `tape`; holding the format instead keeps snapshots at their size.
  private final TapeFormat format;
  private final byte @org.jspecify.annotations.Nullable [] lexicalInput;

  private TapeSnapshot(TapeReader tape, TapeFormat format) {
    this(tape, format, null);
  }

  private TapeSnapshot(TapeReader tape, TapeFormat format, byte @org.jspecify.annotations.Nullable [] lexicalInput) {
    super(tape, null);
    this.lexicalInput = lexicalInput;
    this.format = checkFormat(tape, format);
  }

  // Metadata-bearing snapshots use this class; plain snapshots make the absence of alias
  // behavior a class property so fresh child views stay scalar-replaceable in compiled reads.
  @Override protected boolean aliases() { return true; }

  private static final class Plain extends TapeSnapshot {
    Plain(TapeReader tape, TapeFormat format) { super(tape, format); }
    @Override protected boolean aliases() { return false; }
  }

  private static TapeSnapshot create(TapeReader tape, TapeFormat format,
      byte @org.jspecify.annotations.Nullable [] source) {
    return format.hasAliases() ? new TapeSnapshot(tape, format, source) : new Plain(tape, format);
  }

  /**
   * Detaches the subtree at {@code root}; lexical spans keep their offsets into the copied input
   * and the heap tape copies the key-hash side table alongside the nodes. Alias tables (YAML) are
   * copied as references, never expanded: per-node words are sliced to the subtree and targets
   * re-based to it, so an alias whose target lies outside the subtree throws when read.
   */
  static TapeSnapshot copy(TapeOwner owner, long root) {
    owner.check();
    HeapTape heap = HeapTape.copy(owner.tape, root);
    TapeFormat format = owner.format().slice(root, Math.toIntExact(heap.nodeCount()));
    byte[] source = null;
    if (format.hasAliases()) {
      long inputFirst = 0, extent = 0;
      for (long node = 0; node < heap.nodeCount(); node = heap.step(node)) {
        long span = heap.span(node);
        if (span == 0) continue;
        inputFirst = (owner.lexical(root + node) >>> 32) - (span >>> 32);
        extent = Math.max(extent, (span >>> 32) + (span & 0xffffffffL));
      }
      source = new byte[Math.toIntExact(extent)];
      for (int i = 0; i < source.length; i++) source[i] = owner.lexicalByte(inputFirst + i);
    }
    return create(heap, format, source);
  }

  public long nodeCount() {
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
    return tape.span(node);
  }

  @Override
  protected byte lexicalByte(long offset) {
    byte[] source = lexicalInput;
    if (source != null) return source[Math.toIntExact(offset)];
    return tape.inputByte(offset);
  }

  /** A snapshot of a snapshot shares nothing mutable, so the same storage is returned. */
  @Override
  public TapeSnapshot snapshot() {
    return this;
  }
}
