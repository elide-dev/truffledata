package dev.elide.truffledata.tape;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.Assumption;
import dev.elide.myna.values.ValueTape;
import java.nio.charset.StandardCharsets;
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
}
