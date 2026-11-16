package dev.elide.truffledata.tape;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.Assumption;
import dev.elide.myna.values.ValueTape;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

  // TD_TAPE_HAS_META (flag bit 2): the compact tape carries one uint32 per word (0 on second
  // words). toMyna() must re-index that to one uint32 per node and serve it through the Myna
  // ABI v4 readMetadata hook, mirroring the native c::to_myna converter pinned by
  // tape/native/ctape_test.cc::to_myna_refs_and_metadata. Previously it masked the flag off
  // and never implemented readMetadata, so metadata(node) returned 0 on every interchange tape.

  @Test void toMynaPreservesPerNodeMetadataOnAWideScalar() {
    // The exact bug-report repro: a wide I64 node carrying metadata 777 over the value 42.
    long[] words = {w(3, TapeReader.WIDE, 2, 0), 42};
    int[] meta = {777, 0};
    HeapTape heap = new HeapTape(words, new byte[0], "42".getBytes(StandardCharsets.UTF_8),
        new long[0], meta, 2);
    assertEquals(777, heap.metadata(0));                       // source preserves it
    assertEquals(777, HeapTape.copy(heap, 0).metadata(0));     // the copy path already did
    ValueTape myna = heap.toMyna();
    myna.validate();
    assertEquals(1, myna.nodeCount());
    assertEquals(42, myna.signedValue(0));                     // the payload survives
    assertEquals(777, myna.metadata(0));                       // the interchange must preserve it
  }

  @Test void toMynaReindexesMetadataFromWordIndexToNodeIndex() {
    // Array [i64 42, true, null]: the wide i64 sits at word 2 (node 1) and the single-word true at
    // word 4 (node 2). Metadata set on words 2 and 4 must land on nodes 1 and 2; second words and
    // nodes without metadata read 0.
    long[] words = {
        w(8, TapeReader.WIDE, 3, 6), 0,                // array root, next 6
        w(3, TapeReader.WIDE, 2, 0), 42,               // i64 42 (wide, words 2-3)
        w(2, 0, 0, 0),                                  // true, word 4
        w(0, 0, 0, 0),                                  // null, word 5
    };
    int[] meta = {0, 0, 0x77, 0, 0x11, 0};            // per-word: word 2 -> 0x77, word 4 -> 0x11
    HeapTape heap = new HeapTape(words, new byte[0], "42".getBytes(StandardCharsets.UTF_8),
        new long[0], meta, 2);
    ValueTape myna = heap.toMyna();
    myna.validate();
    assertEquals(4, myna.nodeCount());
    assertEquals(0, myna.metadata(0));                 // array root: word 0 held no metadata
    assertEquals(0x77, myna.metadata(1));               // i64: re-indexed from word 2
    assertEquals(0x11, myna.metadata(2));              // true: re-indexed from word 4
    assertEquals(0, myna.metadata(3));                 // null: word 5 held 0
    assertEquals(42, myna.signedValue(1));
  }

  @Test void toMynaReportsZeroMetadataForTapesWithoutTheMetaFlag() {
    // JSON tapes never set TD_TAPE_HAS_META; the fix must not disturb that path, and
    // metadata(node) stays 0 for every node (the common, regression-sensitive case).
    ValueTape myna = document().toMyna();
    myna.validate();
    for (long n = 0; n < myna.nodeCount(); n++) assertEquals(0, myna.metadata(n));
  }

  @Test void endThrowsInsteadOfHangingWhenAnInnerArrayIsStillOpen() {
    TapeBuilder builder = new TapeBuilder(new byte[0]);
    builder.begin(false, 0);
    builder.begin(false, 0);
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> assertTimeoutPreemptively(Duration.ofSeconds(2), () -> builder.end(0)));
    assertEquals("Tape container is not closed", failure.getMessage());
  }

  @Test void endThrowsWhenAnInnerContainerValueIsStillOpenInAnObject() {
    TapeBuilder builder = new TapeBuilder("k".getBytes(StandardCharsets.UTF_8));
    builder.begin(true, 0);
    builder.stringRef(1L, true, true, 0);
    builder.begin(false, 0);
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> assertTimeoutPreemptively(Duration.ofSeconds(2), () -> builder.end(0)));
    assertEquals("Tape container is not closed", failure.getMessage());
  }

  @Test void builderProducesAReadableObjectWithADirectoryWhenBeginEndArePaired() {
    byte[] input = "ba".getBytes(StandardCharsets.UTF_8);
    TapeBuilder builder = new TapeBuilder(input);
    builder.begin(true, 0);
    builder.stringRef(1L, true, true, 0);
    builder.longValue(1, 0, 0);
    builder.stringRef((1L << 32) | 1, true, true, 0);
    int inner = builder.begin(false, 0);
    builder.longValue(2, 0, 0);
    builder.end(inner);
    builder.end(0);
    HeapTape tape = builder.build(0);
    Owner owner = new Owner(tape);
    assertEquals(1, owner.root().member("b").longValue());
    assertEquals(2, owner.root().member("a").element(0).longValue());
    tape.toMyna().validate();
  }
}
