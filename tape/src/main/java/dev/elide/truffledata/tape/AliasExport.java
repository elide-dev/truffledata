package dev.elide.truffledata.tape;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.myna.values.ValueTape;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/** YAML export with explicit traversal frames and a bound on alias expansion. */
final class AliasExport {
  private AliasExport() {}

  private static final int OBJECT = 1, CLOSE = 2, EXPANDED = 4, KEY = 8;

  /** Scratch belongs to the reusable export cursor, including nested/reentrant export isolation. */
  static final class State {
    long[] cursors = new long[16];
    long[] ends = new long[16];
    int[] flags = new int[16];
    int[] seen = new int[0];
    int generation;
    @Nullable TapeFormat format;
    @Nullable TapeText[] tags = new TapeText[0];

    void prepare(TapeFormat next) {
      if (seen.length < next.anchorNodes.length) seen = new int[next.anchorNodes.length];
      if (++generation == 0) { Arrays.fill(seen, 0); generation = 1; }
      if (format != next) {
        if (tags.length < next.tagUris.length) tags = new TapeText[next.tagUris.length];
        else Arrays.fill(tags, null);
        format = next;
      }
    }

    void push(int depth, long cursor, long end, int bits) {
      if (depth == cursors.length) {
        int capacity = Math.multiplyExact(depth, 2);
        cursors = Arrays.copyOf(cursors, capacity);
        ends = Arrays.copyOf(ends, capacity);
        flags = Arrays.copyOf(flags, capacity);
      }
      cursors[depth] = cursor; ends[depth] = end; flags[depth] = bits;
    }
  }

  @TruffleBoundary
  static int walk(TapeOwner owner, long root, TapeSink sink, TapeText text) {
    TapeReader tape = owner.tape;
    TapeFormat format = owner.format();
    int[] meta = java.util.Objects.requireNonNull(format.meta);
    State stack = text.aliasState;
    if (stack == null) text.aliasState = stack = new State();
    stack.prepare(format);
    int frames = 1;
    stack.push(0, root, tape.next(root), 0);
    long expanded = 0;
    int depth = 0, deepest = 0;
    while (frames != 0) {
      int top = frames - 1, bits = stack.flags[top];
      if (stack.cursors[top] >= stack.ends[top]) {
        frames--;
        if ((bits & CLOSE) != 0) { sink.end(); depth--; }
        continue;
      }
      long node = stack.cursors[top];
      stack.cursors[top] = tape.next(node);
      if ((bits & EXPANDED) != 0 && ++expanded > format.maxAliasExpansion)
        throw new IllegalArgumentException("YAML alias expansion limit");
      if ((bits & KEY) != 0) {
        text.bind(node);
        try { sink.key(text); } finally { text.unbind(); }
        stack.flags[top] = bits & ~KEY;
        continue;
      }
      if ((bits & OBJECT) != 0) stack.flags[top] = bits | KEY;
      int word = meta[Math.toIntExact(node)];
      if ((word & TapeFormat.ALIAS) != 0) {
        long target = owner.resolve(node);
        int id = format.anchorId(target);
        if (id >= 0 && stack.seen[id] == stack.generation && sink.alias(id)) continue;
        if (++expanded > format.maxAliasExpansion)
          throw new IllegalArgumentException("YAML alias expansion limit");
        // The frame resumes at the alias's successor after the referenced subtree finishes.
        stack.push(frames++, target, tape.next(target), EXPANDED);
        continue;
      }
      int tag = word >>> TapeFormat.ORDINAL_SHIFT;
      if (tag != 0) emitTag(stack, format, tag - 1, sink);
      int anchor = format.anchorId(node);
      if (anchor >= 0) { sink.anchor(anchor); stack.seen[anchor] = stack.generation; }
      int kind = tape.kindAt(node);
      if (kind == ValueTape.OBJECT || kind == ValueTape.ARRAY) {
        boolean object = kind == ValueTape.OBJECT;
        if (object) sink.beginObject(tape.length(node)); else sink.beginArray(tape.length(node));
        depth++;
        deepest = Math.max(deepest, depth);
        stack.push(frames++, tape.first(node), tape.next(node),
            CLOSE | (object ? OBJECT | KEY : 0) | (bits & EXPANDED));
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

  private static void emitTag(State state, TapeFormat format, int index, TapeSink sink) {
    TapeText text = state.tags[index];
    if (text == null) state.tags[index] = text = tagCursor(format.tagUris[index]);
    text.bind(0);
    try { sink.tag(text); } finally { text.unbind(); }
  }

  private static TapeText tagCursor(String uri) {
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
    return new TapeText(owner);
  }
}
