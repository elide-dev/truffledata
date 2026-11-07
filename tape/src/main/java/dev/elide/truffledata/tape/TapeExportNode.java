package dev.elide.truffledata.tape;

import com.oracle.truffle.api.CompilerDirectives;
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

  TapeExportNode() {}

  public static TapeExportNode create() {
    return new TapeExportNode();
  }

  /** Diagnostic for tests: deepest container stack any export through this node needed. */
  public int maxDepthSeen() {
    return maxDepthSeen;
  }

  public void execute(TapeValue value, TapeSink sink) {
    int depth = walk(value.owner(), value.node(), sink);
    if (depth > maxDepthSeen) maxDepthSeen = depth;
  }

  /** Returns the deepest container stack used. */
  static int walk(TapeOwner owner, long root, TapeSink sink) {
    owner.pin();
    TapeText text = owner.exportCursor();
    try {
      return loop(owner, root, sink, text);
    } finally {
      text.unbind();
      owner.unpin();
    }
  }

  /**
   * {@code text.ends} holds each open container's exclusive end node, sign bit set for objects;
   * {@code text} is bound to each key, string and decimal for exactly the duration of its event.
   */
  private static int loop(TapeOwner owner, long root, TapeSink sink, TapeText text) {
    long[] ends = text.ends;
    ValueTape tape = owner.tape;
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
      if (expectKey) {
        text.bind(cursor);
        sink.key(text);
        text.unbind();
        cursor++;
        expectKey = false;
        continue;
      }
      int tag = owner.tag(cursor);
      if (tag == ValueTape.OBJECT || tag == ValueTape.ARRAY) {
        if (depth == MAX_DEPTH) {
          CompilerDirectives.transferToInterpreter();
          throw new IllegalStateException(owner.format().name + " export depth limit");
        }
        long count = tape.length(cursor);
        boolean object = tag == ValueTape.OBJECT;
        if (object) sink.beginObject(count);
        else sink.beginArray(count);
        ends[depth++] = tape.next(cursor) | (object ? OBJECT_BIT : 0);
        if (depth > deepest) deepest = depth;
        cursor++;
        expectKey = object;
        continue;
      }
      switch (tag) {
        case ValueTape.NULL -> sink.nullValue();
        case ValueTape.BOOL -> sink.booleanValue(tape.booleanValue(cursor));
        case ValueTape.I64 -> sink.longValue(tape.signedValue(cursor));
        case ValueTape.U64 -> sink.unsignedValue(tape.unsignedBits(cursor));
        case ValueTape.F64 -> sink.doubleValue(tape.doubleValue(cursor));
        case ValueTape.STRING -> {
          text.bind(cursor);
          sink.stringValue(text);
          text.unbind();
        }
        case ValueTape.DECIMAL -> {
          text.bind(cursor);
          sink.decimalValue(text);
          text.unbind();
        }
        default -> {
          CompilerDirectives.transferToInterpreter();
          throw new IllegalStateException("Unknown " + owner.format().name + " node kind");
        }
      }
      cursor = tape.next(cursor);
      expectKey = depth > 0 && ends[depth - 1] < 0;
    }
  }
}
