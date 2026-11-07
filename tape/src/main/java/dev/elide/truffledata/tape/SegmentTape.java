package dev.elide.truffledata.tape;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/** JVM reader of the compact descriptor; a session reuses one reader between borrows. */
public final class SegmentTape extends TapeReader {
  private MemorySegment words = MemorySegment.NULL, pool = MemorySegment.NULL,
      input = MemorySegment.NULL, directory = MemorySegment.NULL, meta = MemorySegment.NULL;
  public SegmentTape(MemorySegment descriptor) { rebind(descriptor); }
  public void rebind(MemorySegment descriptor) {
    if (descriptor.address() == 0 || (descriptor.address() & 7) != 0)
      throw new IllegalArgumentException("Invalid compact tape descriptor");
    MemorySegment d = descriptor.reinterpret(88);
    if (d.get(ValueLayout.JAVA_INT, 0) != 1 || (d.get(ValueLayout.JAVA_INT, 4) & ~3) != 0)
      throw new IllegalArgumentException("Unsupported compact tape descriptor");
    long n = d.get(ValueLayout.JAVA_LONG, 16), p = d.get(ValueLayout.JAVA_LONG, 32), i = d.get(ValueLayout.JAVA_LONG, 48);
    long e = d.get(ValueLayout.JAVA_LONG, 64), m = d.get(ValueLayout.JAVA_LONG, 80);
    int f = d.get(ValueLayout.JAVA_INT, 4);
    if (n <= 0 || n > 0xffffffffL || p < 0 || p > 0xffffffffL || i < 0 || i > 0xffffffffL
        || e < 0 || e > 0xffffffffL || (((f & 2) != 0) ? m != n : m != 0))
      throw new IllegalArgumentException("Invalid compact tape extent");
    MemorySegment w = region(d, 8, n * 8, 8), pb = region(d, 24, p, 1), ib = region(d, 40, i, 1);
    MemorySegment dir = region(d, 56, e * 8, 8), mt = region(d, 72, m * 4, 4);
    words = w; pool = pb; input = ib; directory = dir; meta = mt;
    count = n; poolBytes = p; inputBytes = i; directoryCount = e; flags = f;
  }
  private static MemorySegment region(MemorySegment d, long at, long size, int alignment) {
    MemorySegment address = d.get(ValueLayout.ADDRESS, at);
    if (size > 0 && (address.address() == 0 || (address.address() & (alignment - 1)) != 0))
      throw new IllegalArgumentException("Invalid compact tape pointer");
    return address.reinterpret(size);
  }
  @Override protected long readWord(long index) { return words.get(ValueLayout.JAVA_LONG, Math.multiplyExact(index, 8)); }
  @Override protected byte textByte(boolean owned, long offset) { return (owned ? pool : input).get(ValueLayout.JAVA_BYTE, offset); }
  @Override protected long textWord(boolean owned, long offset, int length) {
    if (length == 0) return 0;
    MemorySegment bytes = owned ? pool : input;
    if (offset >= 0 && offset <= bytes.byteSize() - 8) {
      long value = bytes.get(ValueLayout.JAVA_LONG_UNALIGNED, offset);
      return length == 8 ? value : value & (-1L >>> (64 - length * 8));
    }
    long value = 0;
    for (int j = 0; j < length; j++) value |= (bytes.get(ValueLayout.JAVA_BYTE, offset + j) & 255L) << (j * 8);
    return value;
  }
  @Override public long directoryEntry(long index) { return directory.get(ValueLayout.JAVA_LONG, Math.multiplyExact(index, 8)); }
  @Override public int metadata(long index) { return (flags & 2) == 0 ? 0 : meta.get(ValueLayout.JAVA_INT, Math.multiplyExact(index, 4)); }
}
