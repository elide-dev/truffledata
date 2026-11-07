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
  // Same object as tape: the raw access hinted member reads and snapshots need, through one
  // interface call.
  private final TapeSource source;
  // Null means unconfined (immutable snapshot). Final fields keep every check inlinable.
  private final @Nullable Thread thread;
  // Exports in progress on the owning thread; close is refused while a walk reads the tape.
  // Exports in progress (>= 0) while open; CLOSED once closed. One int keeps per-document owners
  // at their size (they are allocated for every retained document).
  private int pins;
  private static final int CLOSED = Integer.MIN_VALUE;
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

  /** The tape's raw access (the same object as {@link #tape}). */
  final TapeSource source() {
    return source;
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
    if (pins < 0) throw new OwnerStateException(this, OwnerStateException.CLOSED);
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

  /**
   * Value kind of a node: Myna's checked {@link ValueTape#kindAt} (bounds and tag checks in one
   * call, elide-dev/myna#47), which replaced the raw node read that kept {@code ValueTape.tag} out
   * of partially evaluated code.
   */
  final int tag(long node) {
    return tape.kindAt(node);
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
    if (pins > 0) throw new IllegalStateException(format().name + " message is being exported");
    if (pins < 0) return false;
    pins = CLOSED;
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
   * The key node of {@code object} at entry {@code rank} of its Myna v4 directory when that is
   * exactly {@code key} (inline, at most 16 bytes), else -1. A directory lists only its own object's
   * keys and its order (unsigned hash, then offset) does not depend on member order, so a site's
   * remembered rank holds for every member order of one key set. One straight-line tape call: the
   * object word, payload, entry and the key's inline words, compared exactly (Myna zero-pads inline
   * text). No loop, no allocation; {@code object} must be a valid node.
   */
  final long hinted(long object, int rank, TapeKey key) {
    // Keys of at most 8 bytes have an all-zero high word (zero padding); the site's key is a
    // constant, so `wide` folds and short keys skip the third word.
    return source.directoryKey(object, rank, key.hash, key.header, key.low, key.high, key.length > 8);
  }

  /**
   * Value node of {@code key} in {@code object}, or -1. With a v4 directory: a binary search on
   * the unsigned hash, then Myna's checked {@link ValueTape#keyMatches} over equal hashes in offset
   * order (first match, as Myna's own {@code find}); {@code rank[0]}, when given, receives the
   * matching entry's rank plus one. Without one (v3 tapes, objects below the producer's threshold):
   * a linear scan filtered by the side table's 15-bit hash.
   */
  final long find(long object, TapeKey key, int @Nullable [] rank) {
    long count = tape.length(object);
    long payload = tape.bits(object);
    if (payload != 0 && tape.version() == ValueTape.ABI_VERSION_V4) {
      long start = payload - 1;
      if (start > source.directorySize() || count > source.directorySize() - start)
        throw new IllegalArgumentException("Invalid directory extent");
      long low = start, high = start + count;
      while (low < high) {
        long mid = (low + high) >>> 1;
        if (Integer.compareUnsigned((int) source.directoryEntry(mid), key.hash) < 0) low = mid + 1;
        else high = mid;
      }
      long end = tape.next(object);
      for (long entry = low; entry < start + count; entry++) {
        long word = source.directoryEntry(entry);
        if ((int) word != key.hash) break;
        long k = object + (word >>> 32);
        if (k <= object || k >= end - 1) throw new IllegalArgumentException("Invalid directory entry");
        if (tape.keyMatches(k, key.prepared)) {
          if (rank != null && entry - start < Integer.MAX_VALUE) rank[0] = (int) (entry - start) + 1;
          return k + 1;
        }
      }
      return -1;
    }
    long end = tape.next(object);
    int side = key.sideHash();
    for (long k = object + 1; k < end; k = tape.next(k + 1))
      if (((meta(k) >>> 16) & 0x7fff) == side && tape.keyMatches(k, key.prepared)) return k + 1;
    return -1;
  }

  /**
   * Diagnostic: objects of this owner's tape that carry a native Myna v4 member directory (since
   * the v4 adoption, every JSON object with a member; before it, objects above 32 members got a
   * Java directory on first lookup).
   */
  @TruffleBoundary
  public final int directories() {
    check();
    if (tape.version() != ValueTape.ABI_VERSION_V4) return 0;
    int count = 0;
    for (long node = 0; node < tape.nodeCount(); node++)
      if (tape.kindAt(node) == ValueTape.OBJECT && tape.bits(node) != 0) count++;
    return count;
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

  /** Diagnostic: number of arrays indexed for random access within this owner's lifetime. */
  @TruffleBoundary
  public final int indexedArrays() {
    check();
    var last = lastIndex;
    return last == null ? 0 : last.all.size();
  }

  // A plain holder, not a record: identity is all the cache needs, so no equals/hashCode/toString.
  // It also carries the owner's map of every index, so owners hold one field for both.
  private static final class ArrayIndex {
    final long array;
    final long[] children;
    final java.util.concurrent.ConcurrentHashMap<Long, long[]> all;

    ArrayIndex(long array, long[] children, java.util.concurrent.ConcurrentHashMap<Long, long[]> all) {
      this.array = array;
      this.children = children;
      this.all = all;
    }
  }

  // Most recently used index (and the map of all of them): repeated reads of one array skip the
  // map and its key boxing. Snapshots are shared across threads; computeIfAbsent publishes each
  // fully built index. Created on first random access to a large array, so owners that never
  // index pay nothing for it.
  private volatile @Nullable ArrayIndex lastIndex;

  private java.util.concurrent.ConcurrentHashMap<Long, long[]> arrayIndexes() {
    var last = lastIndex;
    if (last != null) return last.all;
    synchronized (this) {
      last = lastIndex;
      if (last == null) lastIndex = last = new ArrayIndex(-1, new long[0], new java.util.concurrent.ConcurrentHashMap<>());
      return last.all;
    }
  }

  /** Child node offsets of an array, built once and cached while the owner lives. */
  @TruffleBoundary
  final long[] arrayIndex(long array) {
    ArrayIndex last = lastIndex;
    if (last != null && last.array == array) return last.children;
    var all = arrayIndexes();
    long[] children = buildArrayIndex(all, array);
    lastIndex = new ArrayIndex(array, children, all);
    return children;
  }

  private long[] buildArrayIndex(java.util.concurrent.ConcurrentHashMap<Long, long[]> all, long array) {
    return all.computeIfAbsent(
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
