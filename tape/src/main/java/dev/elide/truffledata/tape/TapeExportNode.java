package dev.elide.truffledata.tape;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.jspecify.annotations.Nullable;
import com.oracle.truffle.api.nodes.Node;
import dev.elide.myna.values.ValueTape;

/**
 * Single-pass export of a tape subtree into a {@link TapeSink}. The walk is one loop over the tape
 * in source order with an explicit container stack: no recursion, no per-node native calls, one
 * liveness check per export, and one reusable {@link TapeText} cursor (which also holds the stack)
 * instead of a view per key, string or decimal. The owner is pinned open for the duration so a sink that closes
 * it fails instead of freeing the tape under the walk.
 */
public final class TapeExportNode extends Node {
  /** Open containers the walk can hold: the decoder's depth limit admits 128 levels below the root. */
  static final int MAX_DEPTH = 129;

  private static final long OBJECT_BIT = Long.MIN_VALUE;
  private int maxDepthSeen;
  @CompilationFinal private @Nullable Class<? extends TapeOwner> ownerClass;
  @CompilationFinal private @Nullable Class<? extends TapeReader> readerClass;
  @CompilationFinal private boolean aliases;

  TapeExportNode() {}

  public static TapeExportNode create() {
    return new TapeExportNode();
  }

  /** Diagnostic for tests: deepest container stack any export through this node needed. */
  public int maxDepthSeen() {
    return maxDepthSeen;
  }

  public void execute(TapeValue value, TapeSink sink) {
    TapeOwner owner = value.owner();
    if (ownerClass == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      ownerClass = owner.getClass();
      readerClass = owner.tape.getClass();
      aliases = owner.format().hasAliases();
    }
    Class<? extends TapeOwner> ownerType = java.util.Objects.requireNonNull(ownerClass);
    Class<? extends TapeReader> readerType = java.util.Objects.requireNonNull(readerClass);
    int depth;
    if (owner.getClass() == ownerType && owner.tape.getClass() == readerType) {
      TapeOwner exact = CompilerDirectives.castExact(owner, ownerType);
      depth = exact.format().hasAliases() == aliases
          ? walk(exact, CompilerDirectives.castExact(owner.tape, readerType), value.node(), sink, aliases)
          : generic(owner, value.node(), sink);
    } else {
      depth = generic(owner, value.node(), sink);
    }
    if (depth > maxDepthSeen) maxDepthSeen = depth;
  }

  /** Returns the deepest container stack used. */
  static int walk(TapeOwner owner, long root, TapeSink sink) {
    return walk(owner, owner.tape, root, sink, owner.format().hasAliases());
  }

  @TruffleBoundary
  private static int generic(TapeOwner owner, long root, TapeSink sink) {
    return walk(owner, root, sink);
  }

  private static int walk(TapeOwner owner, TapeReader tape, long root, TapeSink sink, boolean aliases) {
    owner.pin();
    TapeText text = owner.exportCursor();
    try {
      return aliases ? AliasExport.walk(owner, root, sink, text) : loop(owner, tape, root, sink, text);
    } finally {
      text.unbind();
      owner.unpin();
    }
  }

  /**
   * {@code text.ends} holds each open container's exclusive end node, sign bit set for objects;
   * {@code text} is bound to each key, string and decimal for exactly the duration of its event.
   */
  private static int loop(TapeOwner owner, TapeReader tape, long root, TapeSink sink, TapeText text) {
    long[] ends = text.ends;
    int depth = 0;
    int deepest = 0;
    boolean expectKey = false;
    long end = tape.next(root);
    long cursor = root;
    while (true) {
      while (depth > 0 && cursor == (ends[depth - 1] & ~OBJECT_BIT)) {
        sink.end();
        depth--;
        expectKey = depth > 0 && ends[depth - 1] < 0;
      }
      if (cursor >= end) return deepest;
      // One header load per node: kind, count, extent and step decode from it statically, so the
      // walk makes one storage call per node (two for 8-byte scalars) instead of one per accessor.
      long word = tape.word(cursor);
      if (expectKey) {
        text.bind(cursor, word);
        sink.key(text);
        text.unbind();
        cursor = TapeReader.step(cursor, word);
        expectKey = false;
        continue;
      }
      int tag = TapeReader.valueKind(word);
      if (tag == ValueTape.OBJECT || tag == ValueTape.ARRAY) {
        if (depth == MAX_DEPTH) {
          CompilerDirectives.transferToInterpreter();
          throw new IllegalStateException(owner.format().name + " export depth limit");
        }
        int field = TapeReader.field(word);
        long count = field == TapeReader.MAX_COUNT ? tape.length(cursor) : field;
        boolean object = tag == ValueTape.OBJECT;
        if (object) sink.beginObject(count);
        else sink.beginArray(count);
        ends[depth++] = TapeReader.low(word) | (object ? OBJECT_BIT : 0);
        if (depth > deepest) deepest = depth;
        cursor = TapeReader.step(cursor, word);
        expectKey = object;
        continue;
      }
      switch (tag) {
        case ValueTape.NULL -> sink.nullValue();
        case ValueTape.BOOL -> sink.booleanValue(TapeReader.kind(word) == 2);
        case ValueTape.I64 -> sink.longValue(tape.word(cursor + 1));
        case ValueTape.U64 -> sink.unsignedValue(tape.word(cursor + 1));
        case ValueTape.F64 -> sink.doubleValue(Double.longBitsToDouble(tape.word(cursor + 1)));
        case ValueTape.STRING -> {
          text.bind(cursor, word);
          sink.stringValue(text);
          text.unbind();
        }
        case ValueTape.DECIMAL -> {
          text.bind(cursor, word);
          sink.decimalValue(text);
          text.unbind();
        }
        default -> {
          CompilerDirectives.transferToInterpreter();
          throw new IllegalStateException("Unknown " + owner.format().name + " node kind");
        }
      }
      cursor = TapeReader.step(cursor, word);
      expectKey = depth > 0 && ends[depth - 1] < 0;
    }
  }
}
