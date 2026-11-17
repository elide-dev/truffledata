package dev.elide.truffledata.tape;

import static dev.elide.truffledata.tape.TapeReader.*;
import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.Assumption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mapping keys carry the same anchor and tag metadata as values, so the single-pass export walk
 * emits {@link TapeSink#tag} and {@link TapeSink#anchor} before {@link TapeSink#key(TapeText)} and
 * records key anchors as seen for {@link TapeSink#alias(int)} reuse. Anchor-on-key, tag-on-key,
 * alias-as-key, and alias-to-anchored-key reuse are exercised on hand-built heap tapes.
 */
final class AliasExportKeyTest {
  private static final int ANCHORED = 1 << 3;

  private static final class Owner extends TapeOwner {
    private final TapeFormat format;

    Owner(TapeReader tape, TapeFormat format) {
      super(tape, Thread.currentThread());
      this.format = checkFormat(tape, format);
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
      return 0;
    }

    @Override
    protected byte lexicalByte(long offset) {
      throw new IndexOutOfBoundsException("No retained input");
    }
  }

  private static long w(int kind, long flags, int field, long low) {
    return ((long) kind << 60) | flags | ((long) field << 32) | low;
  }

  private static long obj(int members, int end) {
    return w(9, WIDE, members, end);
  }

  private static long keyStr(int length, int offset) {
    return w(7, KEY | ASCII, length, offset);
  }

  private static long str(int length, int offset) {
    return w(7, ASCII, length, offset);
  }

  /** A sink that records every event and answers {@link #alias(int)} from a flag. */
  private static final class Recording implements TapeSink {
    final List<String> out = new ArrayList<>();
    final boolean reuseAliases;

    Recording(boolean reuseAliases) {
      this.reuseAliases = reuseAliases;
    }

    @Override public void beginObject(long n) { out.add("{" + n); }
    @Override public void beginArray(long n) { out.add("[" + n); }
    @Override public void end() { out.add("end"); }
    @Override public void key(TapeText name) { out.add("key:" + name.toJavaString()); }
    @Override public void nullValue() { out.add("null"); }
    @Override public void booleanValue(boolean b) { out.add("bool:" + b); }
    @Override public void longValue(long n) { out.add("i64:" + n); }
    @Override public void unsignedValue(long n) { out.add("u64:" + Long.toUnsignedString(n)); }
    @Override public void doubleValue(double d) { out.add("f64:" + d); }
    @Override public void decimalValue(TapeText t) { out.add("dec:" + t.toJavaString()); }
    @Override public void stringValue(TapeText t) { out.add("string:" + t.toJavaString()); }
    @Override public void tag(TapeText uri) { out.add("tag:" + uri.toJavaString()); }
    @Override public void anchor(int id) { out.add("anchor:" + id); }
    @Override
    public boolean alias(int id) {
      out.add("alias:" + id + ":" + reuseAliases);
      return reuseAliases;
    }
  }

  /** {@code {&a k: v, other: *a}} — anchored key at word 2, alias placeholder at word 5. */
  private static HeapTape anchoredKeyDocument() {
    byte[] input = "kvother".getBytes(StandardCharsets.UTF_8);
    long[] words = {
        obj(2, 6), 0L,
        keyStr(1, 0), str(1, 1),
        keyStr(5, 2), 0L,
    };
    return new HeapTape(words, new byte[0], input, new long[0], new int[0], 0);
  }

  private static int[] anchoredKeyMeta() {
    return new int[] {0, 0, ANCHORED, 0, 0, TapeFormat.ALIAS};
  }

  private static Owner anchoredKeyOwner() {
    return new Owner(anchoredKeyDocument(),
        TapeFormat.of("YAML")
            .withAliases(new long[] {2}, anchoredKeyMeta())
            .withEvents(new long[] {2}, new String[] {"a"}, new String[0], 100));
  }

  @Test
  void anchoredKeyEmitsAnchorBeforeKeyAndMarksAnchorSeen() {
    Owner owner = anchoredKeyOwner();
    Recording sink = new Recording(true);
    owner.export(sink);
    assertEquals(List.of(
        "{2",
        "anchor:0",
        "key:k",
        "string:v",
        "key:other",
        "alias:0:true",
        "end"), sink.out);
  }

  @Test
  void anchoredKeyAliasExpandsWhenSinkDeclinesReuse() {
    Owner owner = anchoredKeyOwner();
    Recording sink = new Recording(false);
    owner.export(sink);
    assertEquals(List.of(
        "{2",
        "anchor:0",
        "key:k",
        "string:v",
        "key:other",
        "alias:0:false",
        "anchor:0",
        "string:k",
        "end"), sink.out);
  }

  @Test
  void javaSinkReusesAnchoredKeyStringThroughAlias() {
    Owner owner = anchoredKeyOwner();
    JavaSink sink = new JavaSink();
    owner.export(sink);
    Object result = sink.result();
    assertInstanceOf(Map.class, result);
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) result;
    assertEquals("v", map.get("k"));
    assertEquals("k", map.get("other"));
    assertSame(map.keySet().iterator().next(), map.get("other"));
  }

  @Test
  void javaSinkReusesAnchoredKeyStringThroughRepeatedAliases() {
    byte[] input = "kvothernext".getBytes(StandardCharsets.UTF_8);
    long[] words = {
        obj(3, 8), 0L,
        keyStr(1, 0), str(1, 1),
        keyStr(5, 2), 0L,
        keyStr(4, 7), 0L,
    };
    int[] meta = {
        0, 0, ANCHORED, 0, 0, TapeFormat.ALIAS, 0, TapeFormat.ALIAS | (1 << TapeFormat.ORDINAL_SHIFT),
    };
    Owner owner = new Owner(
        new HeapTape(words, new byte[0], input, new long[0], new int[0], 0),
        TapeFormat.of("YAML")
            .withAliases(new long[] {2, 2}, meta)
            .withEvents(new long[] {2}, new String[] {"a"}, new String[0], 100));
    JavaSink sink = new JavaSink();
    owner.export(sink);
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) sink.result();
    assertEquals("v", map.get("k"));
    String anchored = map.keySet().iterator().next();
    assertEquals("k", anchored);
    assertSame(anchored, map.get("other"));
    assertSame(anchored, map.get("next"));
  }

  /** {@code {!!str 1: v}} — tagged key at word 2. */
  private static Owner taggedKeyOwner() {
    byte[] input = "1v".getBytes(StandardCharsets.UTF_8);
    long[] words = {
        obj(1, 4), 0L,
        keyStr(1, 0), str(1, 1),
    };
    int[] meta = {0, 0, 1 << TapeFormat.ORDINAL_SHIFT, 0};
    return new Owner(
        new HeapTape(words, new byte[0], input, new long[0], new int[0], 0),
        TapeFormat.of("YAML")
            .withMetadata(meta)
            .withEvents(new long[0], new String[0],
                new String[] {"tag:yaml.org,2002:str"}, 100));
  }

  @Test
  void taggedKeyEmitsTagBeforeKey() {
    Owner owner = taggedKeyOwner();
    Recording sink = new Recording(true);
    owner.export(sink);
    assertEquals(List.of(
        "{1",
        "tag:tag:yaml.org,2002:str",
        "key:1",
        "string:v",
        "end"), sink.out);
  }

  /** {@code {&a !!str 1: v}} — key carries both an anchor and a tag. */
  @Test
  void taggedAndAnchoredKeyEmitsTagThenAnchorBeforeKey() {
    byte[] input = "1v".getBytes(StandardCharsets.UTF_8);
    long[] words = {
        obj(1, 4), 0L,
        keyStr(1, 0), str(1, 1),
    };
    int[] meta = {0, 0, ANCHORED | (1 << TapeFormat.ORDINAL_SHIFT), 0};
    Owner owner = new Owner(
        new HeapTape(words, new byte[0], input, new long[0], new int[0], 0),
        TapeFormat.of("YAML")
            .withMetadata(meta)
            .withEvents(new long[] {2}, new String[] {"a"},
                new String[] {"tag:yaml.org,2002:str"}, 100));
    Recording sink = new Recording(true);
    owner.export(sink);
    assertEquals(List.of(
        "{1",
        "tag:tag:yaml.org,2002:str",
        "anchor:0",
        "key:1",
        "string:v",
        "end"), sink.out);
  }

  /** {@code {*a : v}} with the alias-as-key already resolved to its target's canonical text. */
  @Test
  void aliasUsedAsKeyEmitsResolvedTextAndNoAliasEvent() {
    byte[] input = "31v".getBytes(StandardCharsets.UTF_8);
    long[] words = {
        obj(1, 4), 0L,
        keyStr(2, 0), str(1, 2),
    };
    int[] meta = {0, 0, TapeFormat.ALIAS, 0};
    Owner owner = new Owner(
        new HeapTape(words, new byte[0], input, new long[0], new int[0], 0),
        TapeFormat.of("YAML").withMetadata(meta));
    Recording sink = new Recording(true);
    owner.export(sink);
    assertEquals(List.of(
        "{1",
        "key:31",
        "string:v",
        "end"), sink.out);
  }

  /** A plain mapping key (no anchor, no tag) is unaffected: only the key event fires. */
  @Test
  void plainKeyIsUnchangedByTheFix() {
    byte[] input = "kvotherx".getBytes(StandardCharsets.UTF_8);
    long[] words = {
        obj(2, 6), 0L,
        keyStr(1, 0), str(1, 1),
        keyStr(5, 2), str(1, 7),
    };
    int[] meta = new int[words.length];
    Owner owner = new Owner(
        new HeapTape(words, new byte[0], input, new long[0], new int[0], 0),
        TapeFormat.of("YAML").withMetadata(meta));
    Recording sink = new Recording(true);
    owner.export(sink);
    assertEquals(List.of(
        "{2",
        "key:k",
        "string:v",
        "key:other",
        "string:x",
        "end"), sink.out);
  }

  /** An anchored mapping value aliased from a value position still reuses through the value path. */
  @Test
  void anchoredValueAliasReuseStillWorks() {
    byte[] input = "k".getBytes(StandardCharsets.UTF_8);
    long[] words = {
        w(8, WIDE, 2, 4), 0L,
        str(1, 0), 0L,
    };
    int[] meta = {0, 0, ANCHORED, TapeFormat.ALIAS};
    Owner owner = new Owner(
        new HeapTape(words, new byte[0], input, new long[0], new int[0], 0),
        TapeFormat.of("YAML")
            .withAliases(new long[] {2}, meta)
            .withEvents(new long[] {2}, new String[] {"a"}, new String[0], 100));
    Recording sink = new Recording(true);
    owner.export(sink);
    assertEquals(List.of(
        "[2",
        "anchor:0",
        "string:k",
        "alias:0:true",
        "end"), sink.out);
  }

  /** When the sink declines value-alias reuse, the target is expanded (unchanged behavior). */
  @Test
  void anchoredValueAliasExpandsWhenSinkDeclinesReuse() {
    byte[] input = "k".getBytes(StandardCharsets.UTF_8);
    long[] words = {
        w(8, WIDE, 2, 4), 0L,
        str(1, 0), 0L,
    };
    int[] meta = {0, 0, ANCHORED, TapeFormat.ALIAS};
    Owner owner = new Owner(
        new HeapTape(words, new byte[0], input, new long[0], new int[0], 0),
        TapeFormat.of("YAML")
            .withAliases(new long[] {2}, meta)
            .withEvents(new long[] {2}, new String[] {"a"}, new String[0], 100));
    Recording sink = new Recording(false);
    owner.export(sink);
    assertEquals(List.of(
        "[2",
        "anchor:0",
        "string:k",
        "alias:0:false",
        "anchor:0",
        "string:k",
        "end"), sink.out);
  }
}
