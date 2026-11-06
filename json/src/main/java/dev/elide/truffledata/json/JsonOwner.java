package dev.elide.truffledata.json;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.Node;
import dev.elide.myna.values.ValueTape;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * Owner of a validated JSON index. Views read the tape only after {@link #check()}; owners that
 * can close expose an {@link Assumption} so compiled reads cache liveness without a field load.
 */
public abstract sealed class JsonOwner permits JsonMessage, JsonSnapshot {
  final ValueTape tape;
  // Null means unconfined (immutable snapshot). Final fields keep every check inlinable.
  private final @Nullable Thread thread;
  private boolean open = true;

  JsonOwner(ValueTape tape, @Nullable Thread thread) {
    this.tape = tape;
    this.thread = thread;
  }

  /** Rejects closed or cross-thread access; immutable owners never fail. */
  final void check() {
    checkThread();
    if (!open) throw new IllegalStateException("JSON message closed");
  }

  /** Thread confinement only; liveness is covered by the assumption at cached sites. */
  final void checkThread() {
    if (thread != null && Thread.currentThread() != thread)
      throw new IllegalStateException("JSON message thread mismatch");
  }

  /** Marks the owner closed; returns false when it already was. */
  final boolean markClosed() {
    checkThread();
    if (!open) return false;
    open = false;
    return true;
  }

  /** Valid while this owner is open; closing invalidates it. */
  @TruffleBoundary
  public abstract Assumption assumption();

  /** Packed lexical span (offset {@code << 32 | length} into the retained input), or 0. */
  abstract long lexical(long node);

  /** Reads the raw UTF-8 bytes of a lexical span from the retained input region. */
  abstract byte lexicalByte(long offset);

  @TruffleBoundary
  final String lexicalText(long node) {
    check();
    long span = lexical(node);
    if (span == 0) throw new IllegalStateException("JSON node has no lexical text");
    int length = (int) span;
    long offset = span >>> 32;
    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) bytes[i] = lexicalByte(offset + i);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /** Arrays with more elements than this get a child-offset index on first random access. */
  static final int INDEX_THRESHOLD = 8;

  // Snapshots are shared across threads; computeIfAbsent publishes each fully built index.
  private final java.util.concurrent.ConcurrentHashMap<Long, long[]> arrayIndexes =
      new java.util.concurrent.ConcurrentHashMap<>();

  /** Diagnostic: number of arrays indexed for random access within this owner's lifetime. */
  @TruffleBoundary
  public final int indexedArrays() {
    check();
    return arrayIndexes.size();
  }

  /** Child node offsets of an array, built once and cached while the owner lives. */
  @TruffleBoundary
  final long[] arrayIndex(long array) {
    return arrayIndexes.computeIfAbsent(
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

  public final JsonValue root() {
    check();
    return new JsonValue(this, 0);
  }

  /** Independently owned immutable copy of the whole document. */
  @TruffleBoundary
  public JsonSnapshot snapshot() {
    return snapshot(0);
  }

  @TruffleBoundary
  final JsonSnapshot snapshot(long root) {
    check();
    return JsonSnapshot.copy(this, (TapeSource) tape, root);
  }

  /** Cache only retained owners; uncached ingestion and megamorphic sites use ordinary checks. */
  @GenerateUncached
  @GenerateInline(false)
  public abstract static class CheckOpenNode extends Node {
    public abstract void execute(JsonOwner owner);

    @NeverDefault
    public static CheckOpenNode create() {
      return JsonOwnerFactory.CheckOpenNodeGen.create();
    }

    @Specialization(guards = "owner == cachedOwner", assumptions = "alive", limit = "3")
    static void cached(
        JsonOwner owner,
        @Cached("owner") JsonOwner cachedOwner,
        @Cached("cachedOwner.assumption()") Assumption alive) {
      owner.checkThread();
    }

    @Specialization(replaces = "cached")
    static void generic(JsonOwner owner) {
      owner.check();
    }
  }

}
