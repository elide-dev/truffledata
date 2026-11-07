package dev.elide.truffledata.tape;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.myna.values.ValueTape;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

/** YAML export with explicit traversal frames and a bound on alias expansion. */
final class AliasExport {
  private AliasExport() {}

  private static final class Frame {
    long cursor;
    final long end;
    final boolean object;
    final boolean close;
    final boolean expanded;
    boolean key;
    Frame(long cursor, long end, boolean object, boolean close, boolean expanded) {
      this.cursor = cursor; this.end = end; this.object = object; this.close = close;
      this.expanded = expanded; this.key = object;
    }
  }

  @TruffleBoundary
  static int walk(TapeOwner owner, long root, TapeSink sink, TapeText text) {
    TapeReader tape = owner.tape;
    TapeFormat format = owner.format();
    int[] meta = java.util.Objects.requireNonNull(format.meta);
    boolean[] seen = new boolean[format.anchorNodes.length];
    ArrayDeque<Frame> stack = new ArrayDeque<>();
    stack.push(new Frame(root, tape.next(root), false, false, false));
    long expanded = 0;
    int depth = 0, deepest = 0;
    while (!stack.isEmpty()) {
      Frame frame = stack.peek();
      if (frame.cursor >= frame.end) {
        stack.pop();
        if (frame.close) { sink.end(); depth--; }
        continue;
      }
      long node = frame.cursor;
      frame.cursor = tape.next(node);
      if (frame.expanded && ++expanded > format.maxAliasExpansion)
        throw new IllegalArgumentException("YAML alias expansion limit");
      if (frame.key) {
        text.bind(node);
        try { sink.key(text); } finally { text.unbind(); }
        frame.key = false;
        continue;
      }
      frame.key = frame.object;
      int word = meta[Math.toIntExact(node)];
      if ((word & TapeFormat.ALIAS) != 0) {
        long target = format.alias(meta, node, tape.nodeCount());
        int id = format.anchorId(target);
        if (id >= 0 && seen[id] && sink.alias(id)) continue;
        if (++expanded > format.maxAliasExpansion)
          throw new IllegalArgumentException("YAML alias expansion limit");
        // The frame resumes at the alias's successor after the referenced subtree finishes.
        stack.push(new Frame(target, tape.next(target), false, false, true));
        continue;
      }
      int tag = word >>> TapeFormat.ORDINAL_SHIFT;
      if (tag != 0) emitTag(format.tagUris[tag - 1], sink);
      int anchor = format.anchorId(node);
      if (anchor >= 0) { sink.anchor(anchor); seen[anchor] = true; }
      int kind = tape.kindAt(node);
      if (kind == ValueTape.OBJECT || kind == ValueTape.ARRAY) {
        boolean object = kind == ValueTape.OBJECT;
        if (object) sink.beginObject(tape.length(node)); else sink.beginArray(tape.length(node));
        depth++;
        deepest = Math.max(deepest, depth);
        stack.push(new Frame(tape.first(node), tape.next(node), object, true, frame.expanded));
        continue;
      }
      switch (kind) {
        case ValueTape.NULL -> sink.nullValue();
        case ValueTape.BOOL -> sink.booleanValue(tape.booleanValue(node));
        case ValueTape.I64 -> sink.longValue(tape.signedValue(node));
        case ValueTape.U64 -> sink.unsignedValue(tape.unsignedBits(node));
        case ValueTape.F64 -> sink.doubleValue(tape.doubleValue(node));
        case ValueTape.STRING, ValueTape.DECIMAL -> {
          text.bind(node);
          try {
            if (kind == ValueTape.STRING) sink.stringValue(text); else sink.decimalValue(text);
          } finally { text.unbind(); }
        }
        default -> throw new IllegalArgumentException("Unsupported YAML tape kind " + kind);
      }
    }
    return deepest;
  }

  private static void emitTag(String uri, TapeSink sink) {
    byte[] utf8 = uri.getBytes(StandardCharsets.UTF_8);
    HeapValueTape tape = new HeapValueTape(
        new long[] {ValueTape.STRING | ValueTape.REGION_TEXT | ((long) utf8.length << 32), 0, 1},
        utf8, new byte[0][], new int[1]);
    TapeOwner owner = new TapeOwner(tape, null) {
      @Override public TapeFormat format() { return TapeFormat.of("YAML tag"); }
      @Override public Assumption assumption() { return Assumption.ALWAYS_VALID; }
      @Override protected long lexical(long node) { return 0; }
      @Override protected byte lexicalByte(long offset) { return utf8[Math.toIntExact(offset)]; }
    };
    TapeText text = new TapeText(owner);
    text.bind(0);
    try { sink.tag(text); } finally { text.unbind(); }
  }
}
