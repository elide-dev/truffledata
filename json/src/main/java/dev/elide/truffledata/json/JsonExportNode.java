package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import dev.elide.myna.values.ValueTape;

/**
 * Single-pass export of a JSON subtree into a {@link JsonSink}. The walk is one loop over the tape
 * in source order with an explicit container stack: no recursion, no per-node native calls, and
 * one liveness check per export. The owner is pinned open for the duration so a sink that closes
 * it fails instead of freeing the tape under the walk.
 */
public final class JsonExportNode extends Node {
  /** Open containers the walk can hold: the decoder's depth limit admits 128 levels below the root. */
  static final int MAX_DEPTH = 129;

  private static final long OBJECT_BIT = Long.MIN_VALUE;
  private int maxDepthSeen;

  JsonExportNode() {}

  public static JsonExportNode create() {
    return new JsonExportNode();
  }

  /** Diagnostic for tests: deepest container stack any export through this node needed. */
  public int maxDepthSeen() {
    return maxDepthSeen;
  }

  public void execute(JsonValue value, JsonSink sink) {
    int depth = walk(value.owner(), value.node(), sink);
    if (depth > maxDepthSeen) maxDepthSeen = depth;
  }

  /** Returns the deepest container stack used. */
  static int walk(JsonOwner owner, long root, JsonSink sink) {
    owner.pin();
    try {
      return loop(owner, root, sink, owner.exportStack());
    } finally {
      owner.unpin();
    }
  }

  /** {@code ends} holds each open container's exclusive end node, sign bit set for objects. */
  private static int loop(JsonOwner owner, long root, JsonSink sink, long[] ends) {
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
        sink.key(new JsonValue(owner, cursor));
        cursor++;
        expectKey = false;
        continue;
      }
      int tag = owner.tag(cursor);
      if (tag == ValueTape.OBJECT || tag == ValueTape.ARRAY) {
        if (depth == MAX_DEPTH) {
          CompilerDirectives.transferToInterpreter();
          throw new IllegalStateException("JSON export depth limit");
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
        case ValueTape.STRING -> sink.stringValue(new JsonValue(owner, cursor));
        case ValueTape.DECIMAL -> sink.decimalValue(new JsonValue(owner, cursor));
        default -> {
          CompilerDirectives.transferToInterpreter();
          throw new IllegalStateException("Unknown JSON node kind");
        }
      }
      cursor = tape.next(cursor);
      expectKey = depth > 0 && ends[depth - 1] < 0;
    }
  }
}
