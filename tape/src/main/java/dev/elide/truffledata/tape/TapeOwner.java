package dev.elide.truffledata.tape;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.Node;
import dev.elide.myna.values.ValueTape;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * Owner of a validated native tape, shared by the JSON and YAML producers. Views read the tape only
 * after {@link #check()}; owners that can close expose an {@link Assumption} so compiled reads cache
 * liveness without a field load. Format modules subclass it for their retained messages; borrowed
 * documents ({@link TapeBorrow}) and snapshots ({@link TapeSnapshot}) are shared.
 *
 * <p>Alias resolution: every read that reaches a value through a member or element passes the node
 * through {@link #resolve(long)}, which is the identity unless the owner's {@link #format()} holds
 * alias tables (YAML). Resolution runs only inside the read paths' existing boundaries (scans,
 * element walks, view-returning methods), so formats without aliases compile exactly as before; the
 * one inline path, a position-hinted member hit, is alias-free by contract (see {@link
 * TapeSource#keyNodeEquals}). The export walk does not resolve: exports handle aliases themselves.
 *
 * <p>The format is a virtual property rather than a field so that format-specific owners (a JSON
 * message) carry no extra reference; liveness and confinement failures name it lazily ({@link
 * OwnerStateException}).
 */
public abstract class TapeOwner {
  protected final ValueTape tape;
  // Same object as tape; raw node reads keep ValueTape.tag (a trivially inlined one-liner whose
  // deopt target SVM runtime compilation cannot resolve) out of partially evaluated code.
  private final TapeSource source;
  // Null means unconfined (immutable snapshot). Final fields keep every check inlinable.
  private final @Nullable Thread thread;
  private boolean open = true;
  // Exports in progress on the owning thread; close is refused while a walk reads the tape.
  private int pins;
  // Text cursor and container stack reused by the outermost export on the owning thread; nested
  // and unconfined exports allocate their own.
  private @Nullable TapeText cursor;

  /**
   * An owner of {@code tape}, which must be one of this module's tapes ({@link SvmValueTape},
   * {@code SegmentValueTape} or a snapshot's heap tape), confined to {@code thread} (null: an
   * immutable owner that never closes).
   */
  protected TapeOwner(ValueTape tape, @Nullable Thread thread) {
    this.tape = tape;
    this.source = (TapeSource) tape;
    this.thread = thread;
  }

  /** Throws when {@code format}'s alias tables (if any) do not cover {@code tape}. */
  protected static TapeFormat checkFormat(ValueTape tape, TapeFormat format) {
    int[] meta = format.meta;
    if (meta != null && meta.length != tape.nodeCount())
      throw new IllegalArgumentException("Alias side table does not cover the tape");
    return format;
  }

  /** Rejects closed or cross-thread access; immutable owners never fail. */
  protected final void check() {
    checkThread();
    if (!open) throw new OwnerStateException(this, OwnerStateException.CLOSED);
  }

  /** Thread confinement only; liveness is covered by the assumption at cached sites. */
  protected final void checkThread() {
    if (thread != null && Thread.currentThread() != thread)
      throw new OwnerStateException(this, OwnerStateException.THREAD);
  }

  /** The format that produced this owner's tape: its name for messages and any alias tables. */
  public abstract TapeFormat format();

  /**
   * The node an alias stands for, or {@code node} itself (always, for formats without aliases).
   * Throws {@link IllegalStateException} when an alias names no node of this tape. Called from
   * boundaries only; see the class comment.
   */
  public final long resolve(long node) {
    TapeFormat format = format();
    int[] meta = format.meta;
    if (meta == null) return node;
    return format.alias(meta, node, tape.nodeCount());
  }

  /** Value kind of a node; equal to {@code tape.tag(node)} for ABI v2 and v3 storage tags. */
  final int tag(long node) {
    if (node < 0 || node >= tape.nodeCount()) throw new IndexOutOfBoundsException("Node index");
    return source.nodeInt(node * ValueTape.NODE_SIZE) & ValueTape.KIND_MASK;
  }

  /** Holds a confined owner open for an export; immutable owners never close and are not counted. */
  final void pin() {
    check();
    if (thread != null) pins++;
  }

  /** Text cursor (and container stack) for an export that already holds a pin. */
  final TapeText exportCursor() {
    TapeText reused = cursor;
    if (thread != null && pins == 1 && reused != null) return reused;
    return newCursor(this);
  }

  /** First, nested or unconfined export: allocates outside compiled export loops. */
  @TruffleBoundary
  private static TapeText newCursor(TapeOwner owner) {
    TapeText fresh = new TapeText(owner);
    if (owner.thread != null && owner.pins == 1) owner.cursor = fresh;
    return fresh;
  }

  /** Lends a session's cursor to a borrowed owner, so borrowed exports allocate none. */
  final void adoptCursor(TapeText lent) {
    lent.rebind(this);
    cursor = lent;
  }

  final void unpin() {
    if (thread != null) pins--;
  }

  /** Single-pass export of the whole document; see {@link TapeSink}. */
  @TruffleBoundary
  public final void export(TapeSink sink) {
    TapeExportNode.walk(this, 0, sink);
  }

  /** Marks the owner closed; returns false when it already was. */
  protected final boolean markClosed() {
    checkThread();
    if (pins != 0) throw new IllegalStateException(format().name + " message is being exported");
    if (!open) return false;
    open = false;
    return true;
  }

  /** Valid while this owner is open; closing invalidates it. */
  @TruffleBoundary
  public abstract Assumption assumption();

  /** Packed lexical span (offset {@code << 32 | length} into the retained input), or 0. */
  protected abstract long lexical(long node);

  /** Reads the raw UTF-8 bytes of a lexical span from the retained input region. */
  protected abstract byte lexicalByte(long offset);

  /**
   * Native side-table entry of a node: bit 31 marks string and key nodes whose bytes are all ASCII;
   * key nodes carry the low 15 bits of their FNV-1a hash in bits 16-30 and their offset from the
   * object node in bits 0-15 (0 beyond 65535). Other nodes carry 0 in bits 0-30.
   */
  final int meta(long node) {
    if (node < 0 || node >= tape.nodeCount()) throw new IndexOutOfBoundsException("Node index");
    return source.keyMeta(node);
  }

  /**
   * Whether the node at {@code offset} (1..65535) from {@code object} is that object's own key equal
   * to {@code key}, which must be inline (at most 16 bytes). The side table publishes each key's
   * offset from its object, so one compare rejects value strings, keys of nested objects and other
   * positions; the inline node words are then compared exactly (Myna zero-pads inline text). No
   * loop, no call beyond the tape's word reads, no allocation.
   */
  final boolean keyMatches(long object, int offset, TapeKey key) {
    long k = object + offset;
    // Keys of at most 8 bytes have an all-zero high word (zero padding); the site's key is a
    // constant, so `wide` folds and short keys skip the third word.
    return k < tape.nodeCount()
        && source.keyNodeEquals(
            k, (key.hash << 16) | offset, key.header, key.low, key.high, key.length > 8);
  }

  /** Whether key node {@code k} decodes to exactly {@code key}'s bytes (hash first). */
  final boolean keyEquals(long k, TapeKey key) {
    return ((meta(k) >>> 16) & 0x7fff) == key.hash && tape.stringEquals(k, key.bytes);
  }

  /** Objects with more members than this get a hash directory on first lookup. */
  static final int DIRECTORY_THRESHOLD = 32;

  private volatile java.util.concurrent.@Nullable ConcurrentHashMap<Long, int[]> directories;

  /** Diagnostic: number of objects given a member directory within this owner's lifetime. */
  @TruffleBoundary
  public final int directories() {
    check();
    var built = directories;
    return built == null ? 0 : built.size();
  }

  /**
   * Open-addressing table of an object's key offsets (key node minus object, 0 = empty), probed by
   * the native key hash. Built once per object and cached while the owner lives; a hash directory
   * is what Myna ABI v4 container directories (elide-dev/myna#45) would publish natively.
   */
  @TruffleBoundary
  final int[] directory(long object, long end) {
    var built = directories;
    if (built == null) {
      synchronized (this) {
        built = directories;
        if (built == null) directories = built = new java.util.concurrent.ConcurrentHashMap<>();
      }
    }
    return built.computeIfAbsent(
        object,
        key -> {
          int members = Math.toIntExact(tape.length(key));
          int[] table = new int[Integer.highestOneBit(Math.max(members, 1) * 2) * 2];
          int mask = table.length - 1;
          for (long k = key + 1; k < end; k = tape.next(k + 1)) {
            int slot = ((meta(k) >>> 16) & 0x7fff) & mask;
            while (table[slot] != 0) slot = (slot + 1) & mask;
            table[slot] = Math.toIntExact(k - key);
          }
          return table;
        });
  }

  /** True when a string or key node's decoded bytes are all ASCII; false for other nodes. */
  final boolean ascii(long node) {
    return meta(node) < 0;
  }

  @TruffleBoundary
  final String lexicalText(long node) {
    check();
    long span = lexical(node);
    if (span == 0) throw new IllegalStateException(format().name + " node has no lexical text");
    int length = (int) span;
    long offset = span >>> 32;
    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) bytes[i] = lexicalByte(offset + i);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /** Arrays with more elements than this get a child-offset index on first random access. */
  static final int INDEX_THRESHOLD = 8;

  // Snapshots are shared across threads; computeIfAbsent publishes each fully built index. Created
  // on first random access to a large array, so owners that never index pay nothing for it.
  private volatile java.util.concurrent.@Nullable ConcurrentHashMap<Long, long[]> arrayIndexes;

  private java.util.concurrent.ConcurrentHashMap<Long, long[]> arrayIndexes() {
    var indexes = arrayIndexes;
    if (indexes != null) return indexes;
    synchronized (this) {
      indexes = arrayIndexes;
      if (indexes == null) arrayIndexes = indexes = new java.util.concurrent.ConcurrentHashMap<>();
      return indexes;
    }
  }

  /** Diagnostic: number of arrays indexed for random access within this owner's lifetime. */
  @TruffleBoundary
  public final int indexedArrays() {
    check();
    var indexes = arrayIndexes;
    return indexes == null ? 0 : indexes.size();
  }

  // A plain holder, not a record: identity is all the cache needs, so no equals/hashCode/toString.
  private static final class ArrayIndex {
    final long array;
    final long[] children;

    ArrayIndex(long array, long[] children) {
      this.array = array;
      this.children = children;
    }
  }

  // Most recently used index: repeated reads of one array skip the map and its key boxing.
  private volatile @Nullable ArrayIndex lastIndex;

  /** Child node offsets of an array, built once and cached while the owner lives. */
  @TruffleBoundary
  final long[] arrayIndex(long array) {
    ArrayIndex last = lastIndex;
    if (last != null && last.array == array) return last.children;
    long[] children = buildArrayIndex(array);
    lastIndex = new ArrayIndex(array, children);
    return children;
  }

  private long[] buildArrayIndex(long array) {
    return arrayIndexes().computeIfAbsent(
        array,
        key -> {
          long[] built = new long[Math.toIntExact(tape.length(key))];
          long child = built.length == 0 ? -1 : tape.first(key);
          for (int i = 0; i < built.length; i++) {
            built[i] = child;
            child = tape.next(child);
          }
          return built;
        });
  }

  public final TapeValue root() {
    check();
    return new TapeValue(this, 0);
  }

  /** Independently owned immutable copy of the whole document. */
  @TruffleBoundary
  public TapeSnapshot snapshot() {
    return snapshot(0);
  }

  @TruffleBoundary
  final TapeSnapshot snapshot(long root) {
    check();
    return TapeSnapshot.copy(this, (TapeSource) tape, root);
  }

  /** Cache only retained owners; uncached ingestion and megamorphic sites use ordinary checks. */
  @GenerateUncached
  @GenerateInline(false)
  public abstract static class CheckOpenNode extends Node {
    public abstract void execute(TapeOwner owner);

    @NeverDefault
    public static CheckOpenNode create() {
      return TapeOwnerFactory.CheckOpenNodeGen.create();
    }

    @Specialization(guards = "owner == cachedOwner", assumptions = "alive", limit = "3")
    static void cached(
        TapeOwner owner,
        @Cached("owner") TapeOwner cachedOwner,
        @Cached("cachedOwner.assumption()") Assumption alive) {
      owner.checkThread();
    }

    @Specialization(replaces = "cached")
    static void generic(TapeOwner owner) {
      owner.check();
    }
  }

}
