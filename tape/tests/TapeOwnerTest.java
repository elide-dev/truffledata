package dev.elide.truffledata.tape;

import static org.junit.jupiter.api.Assertions.*;
import static dev.elide.truffledata.tape.TapeReader.*;

import com.oracle.truffle.api.Assumption;
import dev.elide.myna.values.ValueTape;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The alias-resolution hook of {@link TapeOwner}: identity for formats without alias tables (JSON),
 * resolution through YAML-style side tables otherwise. Lives in the tape package to build heap
 * tapes by hand.
 */
class TapeOwnerTest {
  @Test
  void legacyStreamBackendKeepsItsPolicyForBothAccessModes() {
    class Legacy extends TapeSession.Backend<TapeOwner> {
      long last;
      int calls;
      @Override protected int streamNext(long stream) { last = stream; return ++calls; }
      @Override protected void decode(byte[] b, int o, int n, int e, TapeTextArena t) { throw new AssertionError(); }
      @Override protected void decode(java.lang.foreign.MemorySegment b, long n) { throw new AssertionError(); }
      @Override protected TapeTextArena textArena(byte[] b) { throw new AssertionError(); }
      @Override protected TapeBorrow borrow() { throw new AssertionError(); }
      @Override protected TapeOwner retain() { throw new AssertionError(); }
      @Override protected void reset() { throw new AssertionError(); }
      @Override protected void release() { throw new AssertionError(); }
      @Override protected long streamCreate(int a, int b) { throw new AssertionError(); }
      @Override protected int streamFeed(long s, byte[] b, int o, int n) { throw new AssertionError(); }
      @Override protected long streamFeed(long s, java.lang.foreign.MemorySegment b) { throw new AssertionError(); }
      @Override protected void streamFinish(long s) { throw new AssertionError(); }
      @Override protected void streamRelease(long s) { throw new AssertionError(); }
    }
    Legacy backend = new Legacy();
    assertEquals(1, backend.streamNext(19, true));
    assertEquals(19, backend.last);
    assertEquals(2, backend.streamNext(23, false));
    assertEquals(23, backend.last);
  }

  @Test
  void textArenaGrowsOnlyBeforeSharing() {
    class Arena extends TapeTextArena {
      Arena() { super(new byte[2]); }
      @Override protected long entry(long node) { return 2; }
      void grow(int capacity) { ensureCapacity(capacity); }
      byte[] bytes() { return array(); }
    }
    Arena arena = new Arena();
    arena.grow(32);
    assertEquals(32, arena.bytes().length);
    arena.bytes()[0] = 'x';
    assertEquals("x", arena.string(0, com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16).toJavaStringUncached());
    assertThrows(IllegalStateException.class, () -> arena.grow(64));
    assertThrows(IllegalArgumentException.class, () -> new Arena().grow(-1));
  }

  @Test
  void exportedTagViewsOwnTheirResolvedUri() {
    int[] meta = aliasMeta();
    meta[0] = 1 << 8;
    Owner owner = new Owner(aliasDocument(), TapeFormat.of("YAML")
        .withAliases(new long[] {2}, meta)
        .withEvents(new long[] {2}, new String[] {"a"}, new String[] {"!custom"}, 100));
    List<TapeValue> tags = new ArrayList<>();
    owner.export(new TapeSink() {
      public void tag(TapeText uri) { tags.add(uri.view()); }
      public void beginObject(long n) {}
      public void key(TapeText text) {}
      public void beginArray(long n) {}
      public void end() {}
      public void nullValue() {}
      public void booleanValue(boolean b) {}
      public void longValue(long n) {}
      public void unsignedValue(long n) {}
      public void doubleValue(double d) {}
      public void decimalValue(TapeText text) {}
      public void stringValue(TapeText text) {}
    });
    assertEquals(1, tags.size());
    TapeValue uri = tags.get(0);
    assertEquals("!custom", uri.stringValue());
    TapeOwner.CheckOpenNode.create().execute(uri.owner());
    assertEquals("YAML tag", uri.owner().format().name());
    assertThrows(IllegalStateException.class, uri::rawText);
    assertEquals("!custom", uri.snapshot().root().stringValue());
  }

  private static final int ANCHORED = 1 << 3;

  /** A confined owner over a hand-built heap tape; it has no lexical spans. */
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

  /** [42, *a], with value at word 2 and alias placeholder at word 4. */
  private static HeapTape aliasDocument() {
    return new HeapTape(new long[] {(8L << 60) | WIDE | (2L << 32) | 5, 0,
        (3L << 60) | WIDE, 42, 0}, new byte[0], new byte[0], new long[0], new int[0], 0);
  }

  private static int[] aliasMeta() {
    return new int[] {0, 0, ANCHORED, 0, TapeFormat.ALIAS};
  }

  @Test
  void resolveIsTheIdentityForFormatsWithoutAliases() {
    TapeFormat json = TapeFormat.of("JSON");
    assertFalse(json.hasAliases());
    Owner owner = new Owner(aliasDocument(), json);
    for (long node : new long[] {0, 2, 4}) assertEquals(node, owner.resolve(node));
    // Without tables an alias node is the NULL it is stored as.
    assertEquals(TapeValue.Kind.NULL, owner.root().element(1).kind());
    assertEquals("JSON", owner.format().name());
  }

  @Test
  void ownerWithAliasTableResolvesThroughIt() {
    TapeFormat yaml = TapeFormat.of("YAML").withAliases(new long[] {2}, aliasMeta());
    assertTrue(yaml.hasAliases());
    Owner owner = new Owner(aliasDocument(), yaml);
    assertEquals(0, owner.resolve(0));
    assertEquals(2, owner.resolve(2));
    assertEquals(2, owner.resolve(4));
    TapeValue root = owner.root();
    assertEquals(42, root.element(0).longValue());
    assertEquals(42, root.element(1).longValue());
    List<Long> values = new ArrayList<>();
    for (TapeValue element : root.elements()) values.add(element.longValue());
    assertEquals(List.of(42L, 42L), values);
    // Snapshots copy the references; reads through them resolve the same way.
    TapeSnapshot snapshot = owner.snapshot();
    assertTrue(snapshot.format().hasAliases());
    assertEquals(42, snapshot.root().element(1).longValue());
  }

  @Test
  void aliasPointingOutsideTheTapeThrows() {
    Owner outside = new Owner(aliasDocument(), TapeFormat.of("YAML").withAliases(new long[] {7}, aliasMeta()));
    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> outside.resolve(4));
    assertEquals("YAML alias target outside the tape", failure.getMessage());
    assertThrows(IllegalStateException.class, () -> outside.root().element(1));
    Owner negative = new Owner(aliasDocument(), TapeFormat.of("YAML").withAliases(new long[] {-1}, aliasMeta()));
    assertThrows(IllegalStateException.class, () -> negative.resolve(4));
    // An ordinal past the target table names no node either.
    Owner missing = new Owner(aliasDocument(), TapeFormat.of("YAML").withAliases(new long[0], aliasMeta()));
    assertThrows(IllegalStateException.class, () -> missing.resolve(4));
    // A subtree snapshot whose alias target lies before the subtree keeps the reference, which
    // then names no node of the snapshot.
    Owner nested = new Owner(nestedDocument(), TapeFormat.of("YAML").withAliases(new long[] {2}, nestedMeta()));
    assertEquals(42, nested.root().element(1).element(0).longValue());
    TapeSnapshot subtree = nested.root().element(1).snapshot();
    assertThrows(IllegalStateException.class, () -> subtree.root().element(0));
  }

  /** [&a 42, [*a]]: inner array at word 4, alias placeholder at word 6. */
  private static HeapTape nestedDocument() {
    return new HeapTape(new long[] {(8L << 60) | WIDE | (2L << 32) | 7, 0,
        (3L << 60) | WIDE, 42, (8L << 60) | WIDE | (1L << 32) | 7, 0, 0},
        new byte[0], new byte[0], new long[0], new int[0], 0);
  }

  private static int[] nestedMeta() {
    return new int[] {0, 0, ANCHORED, 0, 0, 0, TapeFormat.ALIAS};
  }

  /** Directory entry: the key's FNV-1a hash and its offset from the object. */
  private static long entry(String key, long offset) {
    return (ValueTape.hashUtf8(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)) & 0xffffffffL) | offset << 32;
  }

  /** {"b":1,"a":{"x":2}} with compact words and postorder object directories. */
  private static HeapTape directoryDocument() {
    long b = entry("b", 2), a = entry("a", 5), x = entry("x", 2);
    boolean bFirst = Integer.compareUnsigned((int) b, (int) a) < 0;
    long key = (7L << 60) | KEY | ASCII | (1L << 32);
    long[] words = {
      (9L << 60) | WIDE | (2L << 32) | 11, 2L << 32,
      key | 1, (3L << 60) | WIDE, 1,
      key | 4, (9L << 60) | WIDE | (1L << 32) | 11, 1L << 32,
      key | 7, (3L << 60) | WIDE, 2,
    };
    return new HeapTape(words, new byte[0], "\"b\"\"a\"\"x\"".getBytes(java.nio.charset.StandardCharsets.UTF_8),
        new long[] {x, bFirst ? b : a, bFirst ? a : b}, new int[0], 0);
  }

  @Test
  void heapTapesServeMynaDirectories() {
    HeapTape compact = directoryDocument();
    ValueTape tape = compact.toMyna();
    assertEquals(ValueTape.ABI_VERSION_V4, tape.version());
    tape.validate();
    assertEquals(4, tape.find(0, ValueTape.Utf8Key.of("a")));
    assertEquals(2, tape.find(0, ValueTape.Utf8Key.of("b")));
    assertEquals(-1, tape.find(0, ValueTape.Utf8Key.of("x")));
    assertEquals(6, tape.find(4, ValueTape.Utf8Key.of("x")));
    Owner owner = new Owner(compact, TapeFormat.of("JSON"));
    // Compiled hook identity must agree with the interpreter's direct final-field path.
    assertSame(compact, owner.reader());
    assertSame(compact, owner.storage());
    assertTrue(owner.confined());
    assertEquals(2, owner.directories());
    assertEquals(1, owner.root().member("b").longValue());
    assertEquals(2, owner.root().member("a").member("x").longValue());
    TapeKey a = TapeKey.of("a");
    int[] rank = new int[1];
    assertEquals(6, owner.find(0, a, rank));
    assertTrue(rank[0] > 0);
    // A rank names a key only for an object with that many entries, and only its own keys.
    assertEquals(5, TapeOwner.hinted(owner.tape, 0, rank[0] - 1, a, false));
    assertEquals(-1, TapeOwner.hinted(owner.tape, 0, 2, a, false));
    assertEquals(-1, TapeOwner.hinted(owner.tape, 2, 0, a, false));
    assertEquals(-1, TapeOwner.hinted(owner.tape, 6, 0, a, false));
    assertEquals(-1, TapeOwner.hinted(owner.tape, 0, rank[0] - 1, TapeKey.of("b"), false));
    // A subtree snapshot copies and re-bases exactly its own directory.
    TapeSnapshot inner = owner.root().member("a").snapshot();
    assertSame(inner.tape, inner.reader());
    assertFalse(inner.confined());
    assertEquals(1, inner.directories());
    assertEquals(2, inner.root().member("x").longValue());
    // Arrays have no object directories.
    assertEquals(0, new Owner(aliasDocument(), TapeFormat.of("JSON")).directories());
  }

  @Test
  void aliasTableMustCoverTheTape() {
    TapeFormat short_ = TapeFormat.of("YAML").withAliases(new long[] {2}, new int[2]);
    assertThrows(IllegalArgumentException.class, () -> new Owner(aliasDocument(), short_));
  }
}
