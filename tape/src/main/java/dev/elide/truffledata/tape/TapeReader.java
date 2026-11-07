package dev.elide.truffledata.tape;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.myna.values.ValueTape;
import java.nio.charset.StandardCharsets;

/** Reader of a validated compact tape. Indices name words; second words are never nodes. */
public abstract class TapeReader implements TapeLexical {
  public static final long WIDE = 1L << 59, OWNED = 1L << 58, KEY = 1L << 57, ASCII = 1L << 56;
  public static final int MAX_COUNT = 0xffffff;
  protected long count, poolBytes, inputBytes, directoryCount;
  protected int flags;

  protected abstract long readWord(long index);
  public final long word(long index) {
    if (index < 0 || index >= count) throw new IndexOutOfBoundsException("Tape word");
    return readWord(index);
  }
  protected abstract byte textByte(boolean owned, long offset);
  protected abstract long textWord(boolean owned, long offset, int length);
  public abstract long directoryEntry(long index);
  public abstract int metadata(long index);

  public long nodeCount() { return count; }
  public static int kind(long word) { return (int) (word >>> 60); }
  public static int field(long word) { return (int) (word >>> 32) & MAX_COUNT; }
  public static long low(long word) { return word & 0xffffffffL; }
  public static long step(long index, long word) { return index + ((word & WIDE) == 0 ? 1 : 2); }
  public long step(long node) { return step(node, word(node)); }
  // Compact kinds differ from Myna only in their scalar ordering and the two boolean words.
  // A packed map keeps typed interop reads straight-line instead of expanding a switch per read.
  private static final long VALUE_KINDS = 0xfffff97658432110L;
  public int kindAt(long node) {
    int mapped = (int) (VALUE_KINDS >>> (kind(word(node)) * 4)) & 15;
    if (mapped == 15) throw new IllegalArgumentException("Invalid compact tape kind");
    return mapped;
  }
  public long next(long node) {
    long w = word(node);
    int k = kind(w);
    return k == 8 || k == 9 ? low(w) : step(node, w);
  }
  public long first(long node) { return node + 2; }
  public long length(long node) {
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
  public boolean booleanValue(long node) {
    int k = kind(word(node));
    if (k != 1 && k != 2) throw new IllegalStateException("Not a boolean");
    return k == 2;
  }
  public long signedValue(long node) { require(node, 3); return word(node + 1); }
  public long unsignedBits(long node) { require(node, 4); return word(node + 1); }
  public double doubleValue(long node) { require(node, 5); return Double.longBitsToDouble(word(node + 1)); }
  private void require(long node, int k) {
    if (kind(word(node)) != k) throw new IllegalStateException("Wrong tape kind");
  }
  public boolean ascii(long node) { return (word(node) & ASCII) != 0; }
  public int hash(long node) {
    long w = word(node);
    if ((w & KEY) == 0) return 0;
    int hash = 0x811c9dc5;
    for (int i = 0, n = field(w); i < n; i++) hash = (hash ^ (textByte((w & OWNED) != 0, low(w) + i) & 255)) * 0x01000193;
    return hash;
  }
  public long directory(long node) { return kind(word(node)) == 9 ? word(node + 1) >>> 32 : 0; }
  public long directorySize() { return directoryCount; }
  public long textWord(long node, int offset) {
    return textWordFromHeader(word(node), offset);
  }
  private long textWordFromHeader(long w, int offset) {
    int length = Math.max(0, Math.min(8, field(w) - offset));
    return textWord((w & OWNED) != 0, low(w) + offset, length);
  }
  public boolean keyMatches(long node, TapeKey key) {
    long w = word(node);
    if (kind(w) != 7 || field(w) != key.length) return false;
    if (key.length <= 16)
      return textWordFromHeader(w, 0) == key.low && (key.length <= 8 || textWordFromHeader(w, 8) == key.high);
    for (int i = 0; i < key.length; i++) if (textByte((w & OWNED) != 0, low(w) + i) != key.bytes[i]) return false;
    return true;
  }
  public long directoryKey(long object, int rank, TapeKey key) {
    long w = word(object);
    if (kind(w) != 9 || rank < 0 || rank >= field(w) || key.length > 16) return -1;
    long start = word(object + 1) >>> 32;
    if (start == 0 || start - 1 + rank >= directoryCount) return -1;
    long entry = directoryEntry(start - 1 + rank);
    long k = object + (entry >>> 32);
    if ((int) entry != key.hash || k >= count) return -1;
    long kw = word(k);
    // Hints cover clean single-word keys only. Escaped keys use the checked miss path;
    // this keeps the hot path to the directory, key header and one or two input loads.
    long header = (7L << 28) | (KEY >>> 32) | key.length;
    if (((kw & ~ASCII) >>> 32) != header) return -1;
    long offset = low(kw);
    if (textWord(false, offset, Math.min(8, key.length)) != key.low) return -1;
    if (key.length > 8 && textWord(false, offset + 8, key.length - 8) != key.high) return -1;
    return k;
  }
  public long resolve(long node) {
    long w = word(node);
    while (kind(w) == 10) {
      long target = low(w);
      if (target >= node) throw new IllegalArgumentException("Invalid tape reference");
      node = target;
      w = word(node);
    }
    return node;
  }
  @Override public long span(long node) {
    long w = word(node);
    int k = kind(w);
    if (k == 7 || k == 6) {
      if ((w & OWNED) != 0) return word(node + 1);
      return k == 7 ? ((low(w) - 1) << 32) | (field(w) + 2L) : (low(w) << 32) | field(w);
    }
    return k >= 3 && k <= 5 ? (low(w) << 32) | field(w) : 0;
  }
  @Override public byte inputByte(long offset) {
    if (offset < 0 || offset >= inputBytes) throw new IndexOutOfBoundsException("Input offset");
    return textByte(false, offset);
  }
  public int copyStringUtf8(long node, byte[] output, int offset) {
    long w = word(node);
    if (kind(w) != 7 && kind(w) != 6) throw new IllegalStateException("Not text");
    int n = field(w);
    java.util.Objects.checkFromIndexSize(offset, n, output.length);
    for (int i = 0; i < n; i++) output[offset + i] = textByte((w & OWNED) != 0, low(w) + i);
    return n;
  }
  public String decimalText(long node) { return stringValue(node); }
  public String stringValue(long node) {
    byte[] bytes = new byte[Math.toIntExact(length(node))];
    copyStringUtf8(node, bytes, 0);
    return new String(bytes, StandardCharsets.UTF_8);
  }
  public boolean stringEquals(long node, byte[] bytes) {
    long w = word(node);
    if (field(w) != bytes.length) return false;
    for (int i = 0; i < bytes.length; i++) if (textByte((w & OWNED) != 0, low(w) + i) != bytes[i]) return false;
    return true;
  }
}
