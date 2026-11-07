package dev.elide.truffledata.tape;

import static org.junit.jupiter.api.Assertions.*;

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
  private static final int ANCHORED = 1 << 3;

  /** A confined owner over a hand-built heap tape; it has no lexical spans. */
  private static final class Owner extends TapeOwner {
    private final TapeFormat format;

    Owner(ValueTape tape, TapeFormat format) {
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

  /** {@code [42, *a]} with {@code &a 42}: node 0 the array, node 1 the I64, node 2 the alias. */
  private static HeapValueTape aliasDocument() {
    long[] nodes = {
      ValueTape.ARRAY | (2L << 32), 0, 3,
      ValueTape.I64, 42, 2,
      ValueTape.NULL, 0, 3,
    };
    return new HeapValueTape(nodes, new byte[0], new byte[0][], new int[3]);
  }

  private static int[] aliasMeta() {
    return new int[] {0, ANCHORED, TapeFormat.ALIAS | (0 << TapeFormat.ORDINAL_SHIFT)};
  }

  @Test
  void resolveIsTheIdentityForFormatsWithoutAliases() {
    TapeFormat json = TapeFormat.of("JSON");
    assertFalse(json.hasAliases());
    Owner owner = new Owner(aliasDocument(), json);
    for (long node = 0; node < 3; node++) assertEquals(node, owner.resolve(node));
    // Without tables an alias node is the NULL it is stored as.
    assertEquals(TapeValue.Kind.NULL, owner.root().element(1).kind());
    assertEquals("JSON", owner.format().name());
  }

  @Test
  void ownerWithAliasTableResolvesThroughIt() {
    TapeFormat yaml = TapeFormat.of("YAML").withAliases(new long[] {1}, aliasMeta());
    assertTrue(yaml.hasAliases());
    Owner owner = new Owner(aliasDocument(), yaml);
    assertEquals(0, owner.resolve(0));
    assertEquals(1, owner.resolve(1));
    assertEquals(1, owner.resolve(2));
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
    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> outside.resolve(2));
    assertEquals("YAML alias target outside the tape", failure.getMessage());
    assertThrows(IllegalStateException.class, () -> outside.root().element(1));
    Owner negative = new Owner(aliasDocument(), TapeFormat.of("YAML").withAliases(new long[] {-1}, aliasMeta()));
    assertThrows(IllegalStateException.class, () -> negative.resolve(2));
    // An ordinal past the target table names no node either.
    Owner missing = new Owner(aliasDocument(), TapeFormat.of("YAML").withAliases(new long[0], aliasMeta()));
    assertThrows(IllegalStateException.class, () -> missing.resolve(2));
    // A subtree snapshot whose alias target lies before the subtree keeps the reference, which
    // then names no node of the snapshot.
    Owner nested = new Owner(nestedDocument(), TapeFormat.of("YAML").withAliases(new long[] {1}, nestedMeta()));
    assertEquals(42, nested.root().element(1).element(0).longValue());
    TapeSnapshot subtree = nested.root().element(1).snapshot();
    assertThrows(IllegalStateException.class, () -> subtree.root().element(0));
  }

  /** {@code [&a 42, [*a]]}: node 2 the inner array, node 3 an alias of node 1. */
  private static HeapValueTape nestedDocument() {
    long[] nodes = {
      ValueTape.ARRAY | (2L << 32), 0, 4,
      ValueTape.I64, 42, 2,
      ValueTape.ARRAY | (1L << 32), 0, 4,
      ValueTape.NULL, 0, 4,
    };
    return new HeapValueTape(nodes, new byte[0], new byte[0][], new int[4]);
  }

  private static int[] nestedMeta() {
    return new int[] {0, ANCHORED, 0, TapeFormat.ALIAS};
  }

  private static long inlineWord(String text) {
    long word = 0;
    byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    for (int i = 0; i < bytes.length; i++) word |= (bytes[i] & 255L) << (8 * i);
    return word;
  }

  /** Directory entry: the key's FNV-1a hash and its offset from the object. */
  private static long entry(String key, long offset) {
    return (ValueTape.hashUtf8(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)) & 0xffffffffL) | offset << 32;
  }

  /** {@code {"b":1,"a":{"x":2}}} as a hand-built Myna ABI v4 heap tape with both directories. */
  private static HeapValueTape directoryDocument() {
    long b = entry("b", 1), a = entry("a", 3), x = entry("x", 1);
    boolean bFirst = Integer.compareUnsigned((int) b, (int) a) < 0;
    long[] nodes = {
      ValueTape.OBJECT | (2L << 32), 2, 7,
      ValueTape.STRING | (1L << 32), inlineWord("b"), 0,
      ValueTape.I64, 1, 3,
      ValueTape.STRING | (1L << 32), inlineWord("a"), 0,
      ValueTape.OBJECT | (1L << 32), 1, 7,
      ValueTape.STRING | (1L << 32), inlineWord("x"), 0,
      ValueTape.I64, 2, 7,
    };
    // Directories close in post order: the inner object's entry first, then the root's two.
    long[] directory = {x, bFirst ? b : a, bFirst ? a : b};
    return new HeapValueTape(nodes, new byte[0], new byte[0][], new int[7], directory);
  }

  // Myna ABI v4 heap tapes (JSON performance ledger V1): Myna's own validate() and find() read the
  // same directory as the owner's lookups, rank hints and subtree snapshots.
  @Test
  void heapTapesServeMynaDirectories() {
    HeapValueTape tape = directoryDocument();
    assertEquals(ValueTape.ABI_VERSION_V4, tape.version());
    tape.validate();
    assertEquals(4, tape.find(0, ValueTape.Utf8Key.of("a")));
    assertEquals(2, tape.find(0, ValueTape.Utf8Key.of("b")));
    assertEquals(-1, tape.find(0, ValueTape.Utf8Key.of("x")));
    assertEquals(6, tape.find(4, ValueTape.Utf8Key.of("x")));
    Owner owner = new Owner(tape, TapeFormat.of("JSON"));
    assertEquals(2, owner.directories());
    assertEquals(1, owner.root().member("b").longValue());
    assertEquals(2, owner.root().member("a").member("x").longValue());
    TapeKey a = TapeKey.of("a");
    int[] rank = new int[1];
    assertEquals(4, owner.find(0, a, rank));
    assertTrue(rank[0] > 0);
    // A rank names a key only for an object with that many entries, and only its own keys.
    assertEquals(3, owner.hinted(0, rank[0] - 1, a));
    assertEquals(-1, owner.hinted(0, 2, a));
    assertEquals(-1, owner.hinted(2, 0, a));
    assertEquals(-1, owner.hinted(4, 0, a));
    assertEquals(-1, owner.hinted(0, rank[0] - 1, TapeKey.of("b")));
    // A subtree snapshot copies and re-bases exactly its own directory.
    TapeSnapshot inner = owner.root().member("a").snapshot();
    assertEquals(1, inner.directories());
    assertEquals(2, inner.root().member("x").longValue());
    // ABI v3 heap tapes have no directory: lookups scan.
    assertEquals(0, new Owner(aliasDocument(), TapeFormat.of("JSON")).directories());
  }

  @Test
  void aliasTableMustCoverTheTape() {
    TapeFormat short_ = TapeFormat.of("YAML").withAliases(new long[] {1}, new int[2]);
    assertThrows(IllegalArgumentException.class, () -> new Owner(aliasDocument(), short_));
  }
}
