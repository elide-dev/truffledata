package dev.elide.truffledata.tape;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.myna.values.ValueTape;
import java.nio.charset.StandardCharsets;

/**
 * Stateless reader of a validated compact tape. Indices name words; second words are never nodes.
 * Storage hooks let native owners embed their descriptor fields without a second reader object.
 */
public interface TapeReader extends TapeLexical {
  long WIDE = 1L << 59, OWNED = 1L << 58, KEY = 1L << 57, ASCII = 1L << 56;
  int MAX_COUNT = 0xffffff;
  long nodeCount();
  long poolSize();
  long inputSize();
  long directorySize();
  int flags();

  /** Raw storage hooks: callers supply validated node/directory offsets and text extents. */
  long readWord(long index);
  default long word(long index) {
    if (index < 0 || index >= nodeCount()) throw new IndexOutOfBoundsException("Tape word");
    return readWord(index);
  }
  byte textByte(boolean owned, long offset);
  long textWord(boolean owned, long offset, int length);
  /** Copies a validated text extent into caller-owned storage without exposing native memory. */
  default void copyText(boolean owned, long offset, byte[] output, int at, int length) {
    long size = owned ? poolSize() : inputSize();
    if (offset < 0 || length < 0 || offset > size - length
        || at < 0 || at > output.length - length) throw new IndexOutOfBoundsException("Tape text copy");
    for (int i = 0; i < length; i++) output[at + i] = textByte(owned, offset + i);
  }

  long directoryEntry(long index);
  int metadata(long index);

  static int kind(long word) { return (int) (word >>> 60); }
  static int field(long word) { return (int) (word >>> 32) & MAX_COUNT; }
  static long low(long word) { return word & 0xffffffffL; }
  static long step(long index, long word) { return index + ((word & WIDE) == 0 ? 1 : 2); }
  default long step(long node) { return step(node, word(node)); }
  // Compact kinds differ from Myna only in their scalar ordering and the two boolean words.
  // A packed map keeps typed interop reads straight-line instead of expanding a switch per read.
  long VALUE_KINDS = 0xfffff97658432110L;
  default int kindAt(long node) {
    int mapped = (int) (VALUE_KINDS >>> (kind(word(node)) * 4)) & 15;
    if (mapped == 15) throw new IllegalArgumentException("Invalid compact tape kind");
    return mapped;
  }
  default long next(long node) {
    long w = word(node);
    int k = kind(w);
    return k == 8 || k == 9 ? low(w) : step(node, w);
  }
  default long first(long node) { return node + 2; }
  default long length(long node) {
    long w = word(node);
    int length = field(w);
    return length == MAX_COUNT && (kind(w) == 8 || kind(w) == 9) ? countChildren(node) : length;
  }
  @TruffleBoundary
  private long countChildren(long node) {
    boolean object = kind(word(node)) == 9;
    long n = 0, end = next(node);
    for (long i = first(node); i < end; n++) i = next(object ? next(i) : i);
    return n;
  }
  default boolean booleanValue(long node) {
    int k = kind(word(node));
    if (k != 1 && k != 2) throw new IllegalStateException("Not a boolean");
    return k == 2;
  }
  default long signedValue(long node) { require(node, 3); return word(node + 1); }
  default long unsignedBits(long node) { require(node, 4); return word(node + 1); }
  default double doubleValue(long node) { require(node, 5); return Double.longBitsToDouble(word(node + 1)); }
  private void require(long node, int k) {
    if (kind(word(node)) != k) throw new IllegalStateException("Wrong tape kind");
  }
  default boolean isAscii(long node) { return (word(node) & ASCII) != 0; }
  default int hash(long node) {
    long w = word(node);
    if ((w & KEY) == 0) return 0;
    int hash = 0x811c9dc5;
    for (int i = 0, n = field(w); i < n; i++) hash = (hash ^ (textByte((w & OWNED) != 0, low(w) + i) & 255)) * 0x01000193;
    return hash;
  }
  default long directory(long node) { return kind(word(node)) == 9 ? word(node + 1) >>> 32 : 0; }
  default long textWord(long node, int offset) {
    return textWordFromHeader(word(node), offset);
  }
  private long textWordFromHeader(long w, int offset) {
    int length = Math.max(0, Math.min(8, field(w) - offset));
    return textWord((w & OWNED) != 0, low(w) + offset, length);
  }
  default boolean keyMatches(long node, TapeKey key) {
    long w = word(node);
    if (kind(w) != 7 || field(w) != key.length) return false;
    if (key.length <= 16)
      return textWordFromHeader(w, 0) == key.low && (key.length <= 8 || textWordFromHeader(w, 8) == key.high);
    for (int i = 0; i < key.length; i++) if (textByte((w & OWNED) != 0, low(w) + i) != key.bytes[i]) return false;
    return true;
  }
  /** A hint over a valid owner node; validated object/key headers prove their derived word extents. */
  default long directoryKey(long object, int rank, TapeKey key) {
    long w = readWord(object);
    if (kind(w) != 9 || rank < 0 || rank >= field(w) || key.length > 16) return -1;
    long start = readWord(object + 1) >>> 32;
    if (start == 0) return -1;
    // Validation proves the directory extent and each relative key offset. Only the cached
    // rank and key identity can vary here; raw storage loads retain their array checks on heap.
    long entry = directoryEntry(start - 1 + rank);
    long k = object + (entry >>> 32);
    long kw = readWord(k);
    // The complete decoded bytes below verify identity; the stored hash is redundant for a
    // rank hint (it still selects the binary-search range on a miss).
    // Hints cover clean single-word keys only. Escaped keys use the checked miss path;
    // this keeps the hot path to the directory, key header and one or two input loads.
    long header = (7L << 28) | (KEY >>> 32) | key.length;
    if (((kw & ~ASCII) >>> 32) != header) return -1;
    long offset = low(kw);
    if (textWord(false, offset, Math.min(8, key.length)) != key.low) return -1;
    if (key.length > 8 && textWord(false, offset + 8, key.length - 8) != key.high) return -1;
    return k;
  }
  default long resolveReference(long node) {
    long w = word(node);
    while (kind(w) == 10) {
      long target = low(w);
      if (target >= node) throw new IllegalArgumentException("Invalid tape reference");
      node = target;
      w = word(node);
    }
    return node;
  }
  @Override default long span(long node) {
    long w = word(node);
    return rawSpan(w, (kind(w) == 6 || kind(w) == 7) && (w & WIDE) != 0 ? word(node + 1) : 0, flags());
  }
  /** Lexical extent from an already loaded compact header and its text payload, if any. */
  static long rawSpan(long w, long second, int flags) {
    int k = kind(w);
    if (k == 7 || k == 6) {
      if ((w & WIDE) != 0) return second;
      return k == 7 && (flags & 8) == 0 ? ((low(w) - 1) << 32) | (field(w) + 2L) : (low(w) << 32) | field(w);
    }
    return k <= 5 ? (low(w) << 32) | field(w) : 0;
  }
  @Override default byte inputByte(long offset) {
    if (offset < 0 || offset >= inputSize()) throw new IndexOutOfBoundsException("Input offset");
    return textByte(false, offset);
  }
  default int copyStringUtf8(long node, byte[] output, int offset) {
    long w = word(node);
    if (kind(w) != 7 && kind(w) != 6) throw new IllegalStateException("Not text");
    int n = field(w);
    copyText((w & OWNED) != 0, low(w), output, offset, n);
    return n;
  }
  default String decimalText(long node) { return stringValue(node); }
  default String stringValue(long node) {
    byte[] bytes = new byte[Math.toIntExact(length(node))];
    copyStringUtf8(node, bytes, 0);
    return new String(bytes, StandardCharsets.UTF_8);
  }
  default boolean stringEquals(long node, byte[] bytes) {
    long w = word(node);
    if (field(w) != bytes.length) return false;
    for (int i = 0; i < bytes.length; i++) if (textByte((w & OWNED) != 0, low(w) + i) != bytes[i]) return false;
    return true;
  }
}
