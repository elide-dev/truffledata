package dev.elide.truffledata.json;

import static dev.elide.truffledata.tape.TapeReader.*;
import dev.elide.truffledata.tape.SvmTape;
import dev.elide.truffledata.tape.TapeReader;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/** Native result, lifetime, and compact storage in one allocation. */
class SvmJsonMessage extends JsonMessage implements TapeReader {
  private final Pointer words, pool, input, directory;
  private final int count, poolBytes, inputBytes, directoryCount, flags;
  private final long resultAddress;

  SvmJsonMessage(Pointer d, long resultAddress) {
    if (d.isNull() || (d.rawValue() & 7) != 0 || d.readInt(0) != 1 || (d.readInt(4) & ~4) != 0)
      throw new IllegalArgumentException("Invalid JSON compact descriptor");
    long n = d.readLong(16), p = d.readLong(32), i = d.readLong(48), e = d.readLong(64);
    Pointer w = d.readWord(8), pb = d.readWord(24), ib = d.readWord(40), dir = d.readWord(56);
    if (n <= 0 || n > 0xffffffffL || p < 0 || p > 0xffffffffL || i < 0 || i > 0xffffffffL
        || e < 0 || e > 0xffffffffL || w.isNull() || (w.rawValue() & 7) != 0
        || (p > 0 && pb.isNull()) || (i > 0 && ib.isNull()) || (e > 0 && (dir.isNull() || (dir.rawValue() & 7) != 0))
        || d.readLong(80) != 0)
      throw new IllegalArgumentException("Invalid JSON compact storage");
    words = w; pool = pb; input = ib; directory = dir;
    count = (int) n; poolBytes = (int) p; inputBytes = (int) i; directoryCount = (int) e;
    flags = d.readInt(4); this.resultAddress = resultAddress;
  }
  @Override protected void release() { JsonSvm.release(resultAddress); }
  @Override public long nodeCount() { return Integer.toUnsignedLong(count); }
  @Override public long poolSize() { return Integer.toUnsignedLong(poolBytes); }
  @Override public long inputSize() { return Integer.toUnsignedLong(inputBytes); }
  @Override public long directorySize() { return Integer.toUnsignedLong(directoryCount); }
  @Override public int flags() { return flags; }
  @Override public long readWord(long index) { return words.readLong(WordFactory.signed(index * 8)); }
  @Override public byte textByte(boolean owned, long offset) {
    if (offset < 0 || offset >= (owned ? poolSize() : inputSize())) throw new IndexOutOfBoundsException("Tape text");
    return (owned ? pool : input).readByte(WordFactory.signed(offset));
  }
  @Override public long textWord(boolean owned, long offset, int length) {
    if (length == 0) return 0;
    long size = owned ? poolSize() : inputSize();
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
    if (index < 0 || index >= nodeCount()) throw new IndexOutOfBoundsException("Tape metadata");
    return 0;
  }
  @Override public String stringValue(long node) {
    long w = word(node); int length = field(w);
    if (kind(w) != 7 || length < 256 || (w & ASCII) != 0) return TapeReader.super.stringValue(node);
    return SvmTape.decodeUtf8(((w & OWNED) == 0 ? input : pool).add(WordFactory.signed(low(w))), length);
  }
  static final class Adopted extends SvmJsonMessage {
    private final Runnable onClose;
    Adopted(Pointer descriptor, long address, Runnable onClose) { super(descriptor, address); this.onClose = onClose; }
    @Override protected void release() { try { super.release(); } finally { onClose.run(); } }
  }
}
