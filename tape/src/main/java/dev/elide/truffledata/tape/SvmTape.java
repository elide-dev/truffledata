package dev.elide.truffledata.tape;

import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/** Rebindable static-image reader of the 88-byte compact descriptor. */
public final class SvmTape extends TapeReader {
  private Pointer words = WordFactory.nullPointer(), pool = WordFactory.nullPointer(),
      input = WordFactory.nullPointer(), directory = WordFactory.nullPointer(), meta = WordFactory.nullPointer();
  public SvmTape(Pointer descriptor) { rebind(descriptor); }
  public void rebind(Pointer d) {
    if (d.isNull() || (d.rawValue() & 7) != 0 || d.readInt(0) != 1 || (d.readInt(4) & ~3) != 0)
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
    if (kind(w) != 7 || length < 256 || (w & ASCII) != 0) return super.stringValue(node);
    char[] chars = new char[length];
    Pointer data = (w & OWNED) == 0 ? input : pool;
    long written;
    try (var pinned = org.graalvm.nativeimage.PinnedObject.create(chars)) {
      written = transcode(data.add(WordFactory.signed(low(w))), length, pinned.addressOfArrayElement(0), length);
    }
    if (written < 0 || written > length) throw new IllegalArgumentException("Invalid UTF-8 or output extent");
    return new String(chars, 0, (int) written);
  }

  @Override protected long readWord(long index) {
    return words.readLong(WordFactory.signed(index * 8));
  }
  @Override protected byte textByte(boolean owned, long offset) {
    if (offset < 0 || offset >= (owned ? poolBytes : inputBytes)) throw new IndexOutOfBoundsException("Tape text");
    return (owned ? pool : input).readByte(WordFactory.signed(offset));
  }
  @Override protected long textWord(boolean owned, long offset, int length) {
    if (length == 0) return 0;
    long size = owned ? poolBytes : inputBytes;
    if (offset < 0 || offset > size - length) throw new IndexOutOfBoundsException("Tape text");
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
    if (index < 0 || index >= directoryCount) throw new IndexOutOfBoundsException("Tape directory");
    return directory.readLong(WordFactory.signed(index * 8));
  }
  @Override public int metadata(long index) {
    if (index < 0 || index >= count) throw new IndexOutOfBoundsException("Tape metadata");
    return (flags & 2) == 0 ? 0 : meta.readInt(WordFactory.signed(index * 4));
  }
}
