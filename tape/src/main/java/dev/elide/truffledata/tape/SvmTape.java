package dev.elide.truffledata.tape;

import static dev.elide.truffledata.tape.TapeReader.*;

import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/** Rebindable static-image reader of the 88-byte compact descriptor. */
public final class SvmTape implements TapeReader {
  private long count, poolBytes, inputBytes, directoryCount;
  private int flags;
  private Pointer words = WordFactory.nullPointer(), pool = WordFactory.nullPointer(),
      input = WordFactory.nullPointer(), directory = WordFactory.nullPointer(), meta = WordFactory.nullPointer();
  public SvmTape(Pointer descriptor) { rebind(descriptor); }
  public void rebind(Pointer d) {
    if (d.isNull() || (d.rawValue() & 7) != 0 || d.readInt(0) != 1 || (d.readInt(4) & ~15) != 0)
      throw new IllegalArgumentException("Invalid compact tape descriptor");
    long n = d.readLong(16), p = d.readLong(32), i = d.readLong(48), e = d.readLong(64), m = d.readLong(80);
    Pointer w = d.readWord(8), pb = d.readWord(24), ib = d.readWord(40), dir = d.readWord(56), mt = d.readWord(72);
    if (n <= 0 || n > 0xffffffffL || p < 0 || p > 0xffffffffL || i < 0 || i > 0xffffffffL
        || e < 0 || e > 0xffffffffL || w.isNull() || (w.rawValue() & 7) != 0
        || (p > 0 && pb.isNull()) || (i > 0 && ib.isNull()) || (e > 0 && (dir.isNull() || (dir.rawValue() & 7) != 0))
        || (((d.readInt(4) & 2) != 0) ? m != n || mt.isNull() : m != 0))
      throw new IllegalArgumentException("Invalid compact tape storage");
    words = w; pool = pb; input = ib; directory = dir; meta = mt;
    count = n; poolBytes = p; inputBytes = i; directoryCount = e; flags = d.readInt(4);
  }
  @org.graalvm.nativeimage.c.function.CFunction("td_tape_utf8_to_utf16")
  private static native long transcode(Pointer input, long length, org.graalvm.nativeimage.c.type.CShortPointer output, long capacity);

  @Override public String stringValue(long node) {
    long w = word(node);
    int length = field(w);
    if (kind(w) != 7 || length < 256 || (w & ASCII) != 0) return TapeReader.super.stringValue(node);
    Pointer data = (w & OWNED) == 0 ? input : pool;
    return decodeUtf8(data.add(WordFactory.signed(low(w))), length);
  }

  /** Bulk strict UTF-8 conversion shared by native readers and fused document owners. */
  public static String decodeUtf8(Pointer data, int length) {
    char[] chars = new char[length];
    long written;
    try (var pinned = org.graalvm.nativeimage.PinnedObject.create(chars)) {
      written = transcode(data, length, pinned.addressOfArrayElement(0), length);
    }
    if (written < 0 || written > length) throw new IllegalArgumentException("Invalid UTF-8 or output extent");
    return new String(chars, 0, (int) written);
  }

  @Override public long readWord(long index) {
    return words.readLong(WordFactory.signed(index * 8));
  }
  // A hinted member read is one call of straight-line loads instead of one call per hook.
  // (TapeReader.word stays a single default implementation so compiled generic reads inline it.)
  @Override public long directoryKey(long object, int rank, TapeKey key) {
    return directoryKey(words, directory, input, object, rank, key);
  }

  /**
   * {@link TapeReader#directoryKey} over raw native storage, shared by native readers so a hinted
   * member read is one call of straight-line loads. Same contract and checks as the default:
   * validation proved the directory extent, relative key offsets and the padded input.
   */
  public static long directoryKey(Pointer words, Pointer directory, Pointer input, long object, int rank, TapeKey key) {
    long w = words.readLong(WordFactory.signed(object * 8));
    if (kind(w) != 9 || rank < 0 || rank >= field(w) || key.length > 16) return -1;
    long start = words.readLong(WordFactory.signed(object * 8 + 8)) >>> 32;
    if (start == 0) return -1;
    long entry = directory.readLong(WordFactory.signed((start - 1 + rank) * 8));
    long k = object + (entry >>> 32);
    long kw = words.readLong(WordFactory.signed(k * 8));
    long header = (7L << 28) | (KEY >>> 32) | key.length;
    if (((kw & ~ASCII) >>> 32) != header) return -1;
    long offset = low(kw);
    int first = Math.min(8, key.length);
    long low = first == 0 ? 0 : input.readLong(WordFactory.signed(offset));
    if ((first >= 8 || first == 0 ? low : low & (-1L >>> (64 - first * 8))) != key.low) return -1;
    if (key.length > 8) {
      long high = input.readLong(WordFactory.signed(offset + 8));
      if ((key.length == 16 ? high : high & (-1L >>> (128 - key.length * 8))) != key.high) return -1;
    }
    return k;
  }
  @Override public byte textByte(boolean owned, long offset) {
    if (offset < 0 || offset >= (owned ? poolBytes : inputBytes)) throw new IndexOutOfBoundsException("Tape text");
    return (owned ? pool : input).readByte(WordFactory.signed(offset));
  }
  @Override public void copyText(boolean owned, long offset, byte[] output, int at, int length) {
    copyText(owned ? pool : input, owned ? poolBytes : inputBytes, offset, output, at, length);
  }

  /** Checked word-at-a-time copy; no pinning object or temporary native/Java buffer is needed. */
  public static void copyText(Pointer data, long size, long offset, byte[] output, int at, int length) {
    if (offset < 0 || length < 0 || offset > size - length
        || at < 0 || at > output.length - length) throw new IndexOutOfBoundsException("Tape text copy");
    int i = 0;
    for (; i <= length - 8; i += 8)
      com.oracle.truffle.api.memory.ByteArraySupport.littleEndian().putLong(
          output, at + i, data.readLong(WordFactory.signed(offset + i)));
    for (; i < length; i++) output[at + i] = data.readByte(WordFactory.signed(offset + i));
  }

  @Override public long textWord(boolean owned, long offset, int length) {
    if (length == 0) return 0;
    long size = owned ? poolBytes : inputBytes;
    Pointer data = owned ? pool : input;
    if (!owned || offset <= size - 8) {
      long value = data.readLong(WordFactory.signed(offset));
      return length == 8 ? value : value & (-1L >>> (64 - length * 8));
    }
    long value = 0;
    for (int j = 0; j < length; j++) value |= (data.readByte(WordFactory.signed(offset + j)) & 255L) << (j * 8);
    return value;
  }
  @Override public long directoryEntry(long index) {
    return directory.readLong(WordFactory.signed(index * 8));
  }
  @Override public int metadata(long index) {
    if (index < 0 || index >= count) throw new IndexOutOfBoundsException("Tape metadata");
    return (flags & 2) == 0 ? 0 : meta.readInt(WordFactory.signed(index * 4));
  }
  @Override public long nodeCount() { return count; }
  @Override public long poolSize() { return poolBytes; }
  @Override public long inputSize() { return inputBytes; }
  @Override public long directorySize() { return directoryCount; }
  @Override public int flags() { return flags; }
}
