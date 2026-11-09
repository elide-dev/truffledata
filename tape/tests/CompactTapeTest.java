package dev.elide.truffledata.tape;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.Assumption;
import dev.elide.myna.values.ValueTape;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompactTapeTest {
  private static long w(int kind, long flags, int field, long low) {
    return ((long) kind << 60) | flags | ((long) field << 32) | low;
  }

  private static final class Owner extends TapeOwner {
    Owner(TapeReader tape) { super(tape, Thread.currentThread()); }
    @Override public TapeFormat format() { return TapeFormat.of("JSON"); }
    @Override public Assumption assumption() { return Assumption.ALWAYS_VALID; }
    @Override public long lexical(long node) { return tape.span(node); }
    @Override public byte lexicalByte(long offset) { return tape.inputByte(offset); }
  }

  private static Owner document() { return document(true); }

  private static Owner document(boolean directories) {
    byte[] input = "{\"n\":42,\"s\":\"\\u0041\"}".getBytes(StandardCharsets.UTF_8);
    long[] words = {
      w(9, TapeReader.WIDE, 2, 7), 1L << 32,
      w(7, TapeReader.ASCII | TapeReader.KEY, 1, 2),
      w(3, TapeReader.WIDE, 2, 5), 42,
      w(7, TapeReader.ASCII | TapeReader.KEY, 1, 9),
      w(7, TapeReader.ASCII | TapeReader.OWNED | TapeReader.WIDE, 1, 0), (12L << 32) | 8,
    };
    words[0] = w(9, TapeReader.WIDE, 2, words.length);
    long n = (2L << 32) | Integer.toUnsignedLong(ValueTape.hashUtf8(new byte[]{'n'}));
    long s = (5L << 32) | Integer.toUnsignedLong(ValueTape.hashUtf8(new byte[]{'s'}));
    long[] dirs = Integer.compareUnsigned((int) n, (int) s) < 0 ? new long[]{n, s} : new long[]{s, n};
    if (!directories) words[1] = 0;
    return new Owner(new HeapTape(words, new byte[]{'A'}, input, directories ? dirs : new long[0], new int[0], directories ? 0 : 4));
  }

  @Test void directorylessObjectsReadAndDetachThroughTheLinearPath() {
    Owner owner = document(false);
    assertEquals(0, owner.directories());
    assertEquals(42, owner.root().member("n").longValue());
    assertEquals("A", owner.root().member("s").stringValue());
    assertThrows(java.util.NoSuchElementException.class, () -> owner.root().member("absent"));
    TapeSnapshot snapshot = owner.snapshot();
    assertEquals(0, snapshot.directories());
    assertEquals(42, snapshot.root().member("n").longValue());
    ValueTape myna = snapshot.toMyna();
    myna.validate();
    assertEquals("A", myna.stringValue(myna.find(0, ValueTape.Utf8Key.of("s"))));
  }

  @Test void heapMynaCopiesExposePooledTextBytesAndTextWords() {
    byte[] pooled = "an owned string of twenty-four".getBytes(StandardCharsets.UTF_8);
    HeapTape heap = new HeapTape(new long[]{w(7, TapeReader.ASCII | TapeReader.OWNED | TapeReader.WIDE, pooled.length, 0), 0},
        pooled, new byte[0], new long[0], new int[0], 0);
    assertEquals((long) pooled[0] | ((long) pooled[1] << 8) | ((long) pooled[2] << 16) | ((long) pooled[3] << 24)
        | ((long) pooled[4] << 32) | ((long) pooled[5] << 40) | ((long) pooled[6] << 48) | ((long) pooled[7] << 56),
        heap.textWord(0, 0));
    assertEquals(heap.textWord(true, 8, 8), heap.textWord(0, 8));
    Owner owner = new Owner(heap);
    ValueTape myna = owner.snapshot().toMyna();
    myna.validate();
    assertEquals(pooled.length, myna.length(0));
    assertEquals('a', (char) myna.stringByte(0, 0));
    assertEquals('r', (char) myna.stringByte(0, pooled.length - 1));
    assertEquals(new String(pooled, StandardCharsets.UTF_8), myna.stringValue(0));
  }

  @Test void asciiMetadataMatchesTextCursorForInputAndOwnedStrings() {
    for (String value : new String[]{"", "plain", "café", "中😀"}) {
      byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
      byte[] input = ("\"" + value + "\"").getBytes(StandardCharsets.UTF_8);
      boolean ascii = value.chars().allMatch(c -> c < 128);
      for (boolean owned : new boolean[]{false, true}) {
        long flags = (ascii ? TapeReader.ASCII : 0) | (owned ? TapeReader.OWNED | TapeReader.WIDE : 0);
        long header = w(7, flags, utf8.length, owned ? 0 : 1);
        HeapTape tape = new HeapTape(owned ? new long[]{header, input.length} : new long[]{header},
            owned ? utf8 : new byte[0], input, new long[0], new int[0], 0);
        assertEquals(ascii, tape.isAscii(0), value);
        TapeText cursor = new TapeText(new Owner(tape));
        // Export supplies its loaded header; other text events load it lazily.
        for (boolean preloaded : new boolean[]{false, true}) {
          if (preloaded) cursor.bind(0, header); else cursor.bind(0);
          assertEquals(tape.isAscii(0), cursor.isAscii(), value);
          assertEquals(value, cursor.toJavaString());
          cursor.unbind();
        }
      }
    }
  }

  @Test void cachedScalarAndArrayInteropChecksTheValueKind() throws Exception {
    Owner owner = new Owner(new HeapTape(new long[]{w(8, TapeReader.WIDE, 2, 5), 0,
        w(2, 0, 0, 0), w(5, TapeReader.WIDE, 0, 0), Double.doubleToRawLongBits(2.5)},
        new byte[0], new byte[0], new long[0], new int[0], 0));
    TapeValue array = owner.root(), bool = array.element(0), number = array.element(1);
    var root = new com.oracle.truffle.api.nodes.RootNode(null) {
      @Child com.oracle.truffle.api.interop.InteropLibrary interop =
          com.oracle.truffle.api.interop.InteropLibrary.getFactory().createDispatched(3);
      @Override public Object execute(com.oracle.truffle.api.frame.VirtualFrame frame) {
        try {
          assertEquals(2, interop.getArraySize(array));
          assertTrue(interop.asBoolean(bool));
          assertEquals(2.5, interop.asDouble(number));
          assertThrows(com.oracle.truffle.api.interop.UnsupportedMessageException.class,
              () -> interop.asBoolean(number));
          assertThrows(com.oracle.truffle.api.interop.UnsupportedMessageException.class,
              () -> interop.asDouble(bool));
          return true;
        } catch (com.oracle.truffle.api.interop.InteropException failure) {
          throw new AssertionError(failure);
        }
      }
    };
    assertEquals(true, root.getCallTarget().call());
    var uncached = com.oracle.truffle.api.interop.InteropLibrary.getUncached();
    assertEquals(2, uncached.getArraySize(array));
    assertThrows(com.oracle.truffle.api.interop.UnsupportedMessageException.class,
        () -> uncached.getArraySize(bool));
  }

  @Test void preparedKeysUseStrictUtf8AndTheInterchangeHash() {
    for (String name : new String[]{"", "a", "abcdefgh", "abcdefghijklmnop", "abcdefghijklmnopq", "é中😀"}) {
      TapeKey key = TapeKey.of(name);
      byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
      assertArrayEquals(bytes, key.bytes);
      assertEquals(bytes.length, key.utf8Length());
      assertEquals(ValueTape.hashUtf8(bytes), key.hash);
      TapeKey copied = TapeKey.of(ValueTape.Utf8Key.of(name));
      assertEquals(key.low, copied.low);
      assertEquals(key.high, copied.high);
      assertEquals(key.hash, copied.hash);
    }
    assertThrows(IllegalArgumentException.class, () -> TapeKey.of("\uD800"));
    assertThrows(IllegalArgumentException.class, () -> TapeKey.of("\uDC00"));
    assertThrows(IllegalArgumentException.class, () -> TapeKey.of("\uD800x"));
  }

  @Test void yamlPlainAndConstantSpansSurviveSubtreeSnapshots() {
    byte[] input = "prefix plain yes null".getBytes(StandardCharsets.UTF_8);
    Owner owner = new Owner(new HeapTape(new long[]{w(8, TapeReader.WIDE, 3, 5), 0,
        w(7, TapeReader.ASCII, 5, 7), w(2, 0, 3, 13), w(0, 0, 4, 17)},
        new byte[0], input, new long[0], new int[0], 8));
    String[] text = {"plain", "yes", "null"};
    for (int i = 0; i < text.length; i++) {
      assertEquals(text[i], owner.root().element(i).rawText());
      assertEquals(text[i], owner.root().element(i).snapshot().root().rawText());
    }
    assertEquals("plain", owner.root().element(0).stringValue());
    assertTrue(owner.root().element(1).booleanValue());
    assertEquals(TapeValue.Kind.NULL, owner.root().element(2).kind());
  }

  @Test void yamlInputTextKeepsIndependentRawSpansInSubtreeSnapshots() {
    byte[] input = "prefix decoded raw-token".getBytes(StandardCharsets.UTF_8);
    for (long raw : new long[]{0, (15L << 32) | 9}) {
      Owner owner = new Owner(new HeapTape(new long[]{w(8, TapeReader.WIDE, 1, 4), 0,
          w(7, TapeReader.WIDE | TapeReader.ASCII, 7, 7), raw},
          new byte[0], input, new long[0], new int[0], 8));
      var live = owner.root().element(0);
      var copy = live.snapshot();
      assertEquals("decoded", live.stringValue());
      assertEquals("decoded", copy.root().stringValue());
      var myna = copy.toMyna();
      myna.validate();
      assertEquals("decoded", myna.stringValue(0));
      if (raw == 0) {
        assertThrows(IllegalStateException.class, live::rawText);
        assertThrows(IllegalStateException.class, copy.root()::rawText);
      } else {
        assertEquals("raw-token", live.rawText());
        assertEquals("raw-token", copy.root().rawText());
      }
    }
  }

  @Test void bulkTextCopiesOnlyTheRequestedExtent() {
    HeapTape heap = new HeapTape(new long[]{0}, "escaped text".getBytes(StandardCharsets.UTF_8),
        "prefix\"plain text\"suffix".getBytes(StandardCharsets.UTF_8), new long[0], new int[0], 0);
    TapeReader fallback = new TapeReader() {
      @Override public long nodeCount() { return heap.nodeCount(); }
      @Override public long poolSize() { return heap.poolSize(); }
      @Override public long inputSize() { return heap.inputSize(); }
      @Override public long directorySize() { return 0; }
      @Override public int flags() { return 0; }
      @Override public long readWord(long i) { return heap.readWord(i); }
      @Override public byte textByte(boolean owned, long i) { return heap.textByte(owned, i); }
      @Override public long textWord(boolean owned, long i, int n) { return heap.textWord(owned, i, n); }
      @Override public long directoryEntry(long i) { throw new AssertionError(); }
      @Override public int metadata(long i) { return 0; }
    };
    for (TapeReader reader : new TapeReader[]{heap, fallback}) {
      for (boolean owned : new boolean[]{false, true}) {
        int size = Math.toIntExact(owned ? reader.poolSize() : reader.inputSize());
        for (int offset = 0; offset <= size; offset++) {
          for (int length = 0; length <= size - offset; length++) {
            byte[] output = new byte[length + 2];
            java.util.Arrays.fill(output, (byte) 123);
            reader.copyText(owned, offset, output, 1, length);
            assertEquals(123, output[0]);
            assertEquals(123, output[output.length - 1]);
            for (int i = 0; i < length; i++) assertEquals(reader.textByte(owned, offset + i), output[i + 1]);
          }
        }
        assertThrows(IndexOutOfBoundsException.class, () -> reader.copyText(owned, -1, new byte[1], 0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.copyText(owned, size, new byte[1], 0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.copyText(owned, 0, new byte[1], -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.copyText(owned, 0, new byte[1], 1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.copyText(owned, 0, new byte[1], 0, -1));
      }
    }
  }

  @Test void readsWideScalarsAndEscapedStrings() {
    Owner owner = document();
    assertEquals(2, owner.root().size());
    assertEquals(42, owner.root().member("n").longValue());
    assertEquals("42", owner.root().member("n").numberText());
    assertEquals("A", owner.root().member("s").stringValue());
    assertEquals("\"\\u0041\"", owner.root().member("s").rawText());
    assertEquals("s", owner.root().keyAt(1).stringValue());
    assertEquals("A", owner.root().memberAt(1).stringValue());
  }

  @Test void rankHintsVerifyWordsAndDirectorylessSingletons() {
    Owner owner = document();
    TapeKey key = TapeKey.of("s");
    int[] hint = new int[1];
    long value = TapeValue.memberNode(owner, 0, key, hint);
    assertEquals(6, value);
    assertTrue(hint[0] > 0);
    assertEquals(value, TapeValue.memberNode(owner, 0, key, hint));
    assertEquals(-1, TapeValue.memberNode(owner, 0, TapeKey.of("x"), hint));
    Owner one = new Owner(new HeapTape(new long[]{w(9, TapeReader.WIDE, 1, 4), 0,
        w(7, TapeReader.KEY | TapeReader.ASCII, 1, 2), w(2, 0, 0, 0)}, new byte[0],
        "{\"x\":true}".getBytes(StandardCharsets.UTF_8), new long[0], new int[0], 0));
    assertTrue(one.root().member("x").booleanValue());
    assertEquals(0, one.directories());
  }

  @Test void detachedSubtreeKeepsExactLexicalSpelling() {
    Owner owner = document();
    TapeSnapshot string = owner.root().member("s").snapshot();
    assertEquals("A", string.root().stringValue());
    assertEquals("\"\\u0041\"", string.root().rawText());
    TapeSnapshot number = owner.root().member("n").snapshot();
    assertEquals(42, number.root().longValue());
    assertEquals("42", number.root().numberText());
    assertEquals("A", owner.snapshot().root().member("s").stringValue());
  }

  @Test void mynaInterchangePreservesValuesAndDirectories() {
    ValueTape myna = document().toMyna();
    myna.validate();
    assertEquals(42, myna.signedValue(myna.find(0, ValueTape.Utf8Key.of("n"))));
    assertEquals("A", myna.stringValue(myna.find(0, ValueTape.Utf8Key.of("s"))));
  }

  @Test void exportsSkipSecondWords() {
    JavaSink sink = new JavaSink();
    Owner owner = document();
    TapeExportNode node = TapeExportNode.create();
    node.execute(owner.root(), sink);
    assertEquals(java.util.Map.of("n", 42L, "s", "A"), sink.result());
    sink = new JavaSink();
    node.execute(owner.snapshot().root(), sink);  // A different owner class takes the safe fallback.
    assertEquals(java.util.Map.of("n", 42L, "s", "A"), sink.result());
  }

  @Test void directorylessObjectsUseLinearLookupAndConvertWithoutDirectories() {
    Owner withDirectory = document();
    HeapTape copy = HeapTape.copy(withDirectory.tape, 0);
    long[] words = new long[Math.toIntExact(copy.nodeCount())];
    for (int i = 0; i < words.length; i++) words[i] = copy.word(i);
    words[1] = 0;
    byte[] input = new byte[Math.toIntExact(copy.inputSize())];
    for (int i = 0; i < input.length; i++) input[i] = copy.inputByte(i);
    Owner owner = new Owner(new HeapTape(words, new byte[]{'A'}, input, new long[0], new int[0], 4));
    assertEquals(42, owner.root().member("n").longValue());
    assertEquals("A", owner.root().member("s").stringValue());
    assertEquals(0, owner.directories());
    assertEquals(0, owner.snapshot().directories());
    owner.toMyna().validate();
  }

  @Test void syntheticNumbersKeepAbsentSpansWhenOtherInputIsCopied() {
    HeapTape source = new HeapTape(new long[]{w(8, TapeReader.WIDE, 2, 5), 0,
        w(3, TapeReader.WIDE, 0, 0), 42, w(7, TapeReader.ASCII, 1, 4)},
        new byte[0], "   \"x\"".getBytes(StandardCharsets.UTF_8), new long[0], new int[0], 0);
    HeapTape snapshot = HeapTape.copy(source, 0);
    assertEquals(0, snapshot.span(2));
    assertEquals(42, snapshot.signedValue(2));
    assertEquals("x", snapshot.stringValue(4));
    snapshot.toMyna().validate();
  }

  @Test void saturatedContainerCountsWalkHeadersWithoutAllocatingAnIndex() {
    long children = (long) TapeReader.MAX_COUNT + 1;
    TapeReader tape = new TapeReader() {
      @Override public long nodeCount() { return children + 2; }
      @Override public long poolSize() { return 0; }
      @Override public long inputSize() { return 0; }
      @Override public long directorySize() { return 0; }
      @Override public int flags() { return 0; }
      @Override public long readWord(long index) {
        return index == 0 ? w(8, WIDE, MAX_COUNT, children + 2) : 0;
      }
      @Override public byte textByte(boolean owned, long offset) { throw new AssertionError(); }
      @Override public long textWord(boolean owned, long offset, int length) { throw new AssertionError(); }
      @Override public long directoryEntry(long index) { throw new AssertionError(); }
      @Override public int metadata(long index) { return 0; }
    };
    assertEquals(children, tape.length(0));
  }

  /** A reader with only the element-wise default copy hooks: an element-wise view of the heap tape. */
  private static TapeReader elementWise(TapeReader tape) {
    return new TapeReader() {
      @Override public long nodeCount() { return tape.nodeCount(); }
      @Override public long poolSize() { return tape.poolSize(); }
      @Override public long inputSize() { return tape.inputSize(); }
      @Override public long directorySize() { return tape.directorySize(); }
      @Override public int flags() { return tape.flags(); }
      @Override public long readWord(long index) { return tape.readWord(index); }
      @Override public byte textByte(boolean owned, long offset) { return tape.textByte(owned, offset); }
      @Override public long textWord(boolean owned, long offset, int length) { return tape.textWord(owned, offset, length); }
      @Override public long directoryEntry(long index) { return tape.directoryEntry(index); }
      @Override public int metadata(long index) { return tape.metadata(index); }
    };
  }

  @Test void bulkCopyHooksMatchTheElementWiseDefaultsAndStayBounded() {
    Owner owner = document();
    for (long root : new long[] {0, 3, 6}) {
      HeapTape bulk = HeapTape.copy(owner.tape, root), slow = HeapTape.copy(elementWise(owner.tape), root);
      assertEquals(bulk.nodeCount(), slow.nodeCount());
      for (long i = 0; i < bulk.nodeCount(); i++) assertEquals(slow.word(i), bulk.word(i), "word " + i);
      assertEquals(slow.directorySize(), bulk.directorySize());
      for (long i = 0; i < bulk.directorySize(); i++) assertEquals(slow.directoryEntry(i), bulk.directoryEntry(i));
      assertEquals(slow.inputSize(), bulk.inputSize());
      for (long i = 0; i < bulk.inputSize(); i++) assertEquals(slow.inputByte(i), bulk.inputByte(i));
      assertEquals(slow.poolSize(), bulk.poolSize());
      for (long i = 0; i < bulk.poolSize(); i++) assertEquals(slow.textByte(true, i), bulk.textByte(true, i));
    }
    HeapTape tape = HeapTape.copy(owner.tape, 0);
    long[] out = new long[4];
    int[] meta = new int[4];
    assertThrows(IndexOutOfBoundsException.class, () -> tape.copyWords(tape.nodeCount() - 2, out, 0, 4));
    assertThrows(IndexOutOfBoundsException.class, () -> tape.copyWords(-1, out, 0, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> tape.copyWords(0, out, 2, 4));
    assertThrows(IndexOutOfBoundsException.class, () -> tape.copyDirectory(1, out, 0, 2));
    assertThrows(IndexOutOfBoundsException.class, () -> tape.copyMetadata(tape.nodeCount(), meta, 0, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> elementWise(tape).copyWords(0, out, 0, 5));
    tape.copyWords(0, out, 0, 4);
    assertEquals(tape.word(0), out[0]);
    tape.copyMetadata(0, meta, 0, 4);
    assertArrayEquals(new int[4], meta);
  }

  /**
   * {"k0":0,"k1":1,…,"in":{"k0":0,…}} with Myna v4 directories (entries ordered by unsigned hash,
   * then offset), the last outer key escaped into the pool (OWNED|WIDE) when {@code escaped}.
   */
  private static HeapTape directoryDocument(List<String> keys, boolean escaped) {
    StringBuilder text = new StringBuilder("{");
    List<Long> words = new ArrayList<>();
    List<Long> dirs = new ArrayList<>();
    byte[] pool = "pooled".getBytes(StandardCharsets.UTF_8);
    words.add(0L);
    words.add(0L);
    List<long[]> outer = new ArrayList<>();
    for (int i = 0; i < keys.size(); i++) {
      byte[] key = keys.get(i).getBytes(StandardCharsets.UTF_8);
      text.append('"');
      int at = utf8Length(text);
      text.append(keys.get(i)).append("\":");
      int index = words.size();
      boolean owned = escaped && i == keys.size() - 1;
      boolean ascii = key.length == keys.get(i).length();
      if (owned) {
        words.add(w(7, TapeReader.KEY | TapeReader.OWNED | TapeReader.WIDE, pool.length, 0));
        words.add(((long) (at - 1) << 32) | (key.length + 2L));
      } else words.add(w(7, (ascii ? TapeReader.ASCII : 0) | TapeReader.KEY, key.length, at));
      outer.add(new long[] {index, Integer.toUnsignedLong(ValueTape.hashUtf8(owned ? pool : key))});
      int value = utf8Length(text);
      text.append(i).append(',');
      words.add(w(3, TapeReader.WIDE, Integer.toString(i).length(), value));
      words.add((long) i);
    }
    text.append("\"in\":");
    int inKey = words.size();
    words.add(w(7, TapeReader.ASCII | TapeReader.KEY, 2, utf8Length(text) - 4));
    outer.add(new long[] {inKey, Integer.toUnsignedLong(ValueTape.hashUtf8("in".getBytes(StandardCharsets.UTF_8)))});
    int inner = words.size();
    words.add(0L);
    words.add(0L);
    text.append('{');
    List<long[]> nested = new ArrayList<>();
    for (int i = 0; i < Math.min(5, keys.size()); i++) {
      text.append('"');
      int at = utf8Length(text);
      byte[] key = keys.get(i).getBytes(StandardCharsets.UTF_8);
      text.append(keys.get(i)).append("\":true,");
      nested.add(new long[] {words.size() - inner, Integer.toUnsignedLong(ValueTape.hashUtf8(key))});
      words.add(w(7, (key.length == keys.get(i).length() ? TapeReader.ASCII : 0) | TapeReader.KEY, key.length, at));
      words.add(w(2, 0, 4, utf8Length(text) - 5));
    }
    text.setLength(text.length() - 1);
    text.append("}}");
    words.set(inner, w(9, TapeReader.WIDE, nested.size(), words.size()));
    words.set(0, w(9, TapeReader.WIDE, outer.size(), words.size()));
    words.set(1, 1L << 32);
    for (long[] e : sortedEntries(outer)) dirs.add((e[0] << 32) | e[1]);
    words.set(inner + 1, (long) (dirs.size() + 1) << 32);
    for (long[] e : sortedEntries(nested)) dirs.add((e[0] << 32) | e[1]);
    return new HeapTape(words.stream().mapToLong(Long::longValue).toArray(), pool,
        text.toString().getBytes(StandardCharsets.UTF_8), dirs.stream().mapToLong(Long::longValue).toArray(), new int[0], 0);
  }

  private static int utf8Length(CharSequence text) {
    return text.toString().getBytes(StandardCharsets.UTF_8).length;
  }

  private static List<long[]> sortedEntries(List<long[]> entries) {
    List<long[]> sorted = new ArrayList<>(entries);
    sorted.sort((a, b) -> a[1] != b[1] ? Integer.compareUnsigned((int) a[1], (int) b[1]) : Long.compare(a[0], b[0]));
    return sorted;
  }

  private static String outcome(java.util.function.LongSupplier read) {
    try {
      return Long.toString(read.getAsLong());
    } catch (RuntimeException e) {
      return e.getClass().getName();
    }
  }

  @Test void heapDirectoryKeyMatchesTheElementWiseDefault() {
    List<String> keys = new ArrayList<>();
    for (int n = 0; n <= 20; n++) {
      StringBuilder key = new StringBuilder();
      for (int i = 0; i < n; i++) key.append((char) ('a' + (n * 7 + i) % 26));
      keys.add(key.toString());
    }
    keys.addAll(List.of("\u00e9", "\u043a\u043b\u044e\u0447", "key-with-\u00fc-16b", "x".repeat(8) + "\u00e9"));
    List<HeapTape> tapes = new ArrayList<>();
    for (boolean escaped : new boolean[] {false, true}) {
      HeapTape tape = directoryDocument(keys, escaped);
      tapes.add(tape);
      tapes.add(HeapTape.copy(tape, 0));
      long inner = -1;
      for (long k = tape.first(0); k < tape.next(0); k = tape.next(tape.next(k)))
        if (tape.kindAt(tape.step(k)) == ValueTape.OBJECT) inner = tape.step(k);
      assertTrue(inner > 0);
      tapes.add(HeapTape.copy(tape, inner));
    }
    Owner owner = document();
    tapes.add((HeapTape) owner.tape);
    tapes.add(HeapTape.copy(owner.tape, 0));
    List<TapeKey> probes = new ArrayList<>();
    for (String key : keys) {
      probes.add(TapeKey.of(key));
      if (!key.isEmpty()) {
        probes.add(TapeKey.of(key.substring(0, key.length() - 1)));
        probes.add(TapeKey.of(key.substring(0, key.length() - 1) + '~'));
        probes.add(TapeKey.of('~' + key.substring(1)));
      }
    }
    for (String key : List.of("n", "s", "in", "pooled", "absent", "N")) probes.add(TapeKey.of(key));
    int hits = 0, misses = 0, thrown = 0;
    for (HeapTape tape : tapes) {
      TapeReader slow = elementWise(tape);
      for (long node = -1; node <= tape.nodeCount(); node++) {
        for (int rank = -1; rank <= keys.size() + 2; rank++) {
          for (TapeKey key : probes) {
            long object = node;
            int r = rank;
            String expected = outcome(() -> slow.directoryKey(object, r, key));
            assertEquals(expected, outcome(() -> tape.directoryKey(object, r, key)),
                "node " + node + " rank " + rank + " key " + new String(key.bytes, StandardCharsets.UTF_8));
            if (expected.equals("-1")) misses++;
            else if (Character.isDigit(expected.charAt(0))) hits++;
            else thrown++;
          }
        }
      }
    }
    // Every reachable outcome is exercised: hits on every short key, misses, and invalid nodes.
    assertTrue(hits >= 2 * (17 + 4), "hits " + hits);
    assertTrue(misses > hits && thrown > 0, "misses " + misses + " thrown " + thrown);
  }
}
