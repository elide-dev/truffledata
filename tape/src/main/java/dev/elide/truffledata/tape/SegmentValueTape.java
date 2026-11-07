package dev.elide.truffledata.tape;

import dev.elide.myna.values.ValueTape;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * JVM reader over a native Myna v3 descriptor; the owner keeps every referenced region alive. Lives
 * in {@code //tape:jvm} so static images never carry it.
 */
public final class SegmentValueTape extends ValueTape implements TapeSource {
  private static final long REGION_SIZE = 16;
  private final MemorySegment nodes;
  private final MemorySegment bytes;
  private final MemorySegment regions;
  private final long regionCount;
  // One int per node: the native key-hash side table; sized by of(descriptor, hashes).
  private MemorySegment hashes = MemorySegment.NULL;

  SegmentValueTape(MemorySegment descriptor) {
    super(descriptor.get(ValueLayout.JAVA_LONG, 16), descriptor.get(ValueLayout.JAVA_LONG, 32),
        descriptor.get(ValueLayout.JAVA_INT, 0));
    if (descriptor.get(ValueLayout.JAVA_INT, 4) != 0)
      throw new IllegalArgumentException("Unsupported value ABI flags");
    nodes = descriptor.get(ValueLayout.ADDRESS, 8).reinterpret(nodeCount() * NODE_SIZE);
    bytes = descriptor.get(ValueLayout.ADDRESS, 24).reinterpret(byteCount());
    regionCount = descriptor.get(ValueLayout.JAVA_LONG, 48);
    if (regionCount < 0 || regionCount > 0xffffffffL)
      throw new IllegalArgumentException("Invalid region table");
    regions = descriptor.get(ValueLayout.ADDRESS, 40).reinterpret(regionCount * REGION_SIZE);
    if (nodes.address() == 0 || (regionCount > 0 && regions.address() == 0))
      throw new IllegalArgumentException("Invalid value storage pointer");
  }

  static SegmentValueTape of(MemorySegment descriptor) {
    if (descriptor.address() == 0 || (descriptor.address() & 7) != 0)
      throw new IllegalArgumentException("Invalid value descriptor pointer");
    return new SegmentValueTape(descriptor.reinterpret(56));
  }

  /** Reader over a decoded result: the descriptor plus its key-hash side table (one int per node). */
  public static SegmentValueTape of(MemorySegment descriptor, MemorySegment hashes) {
    SegmentValueTape tape = of(descriptor);
    if (hashes.address() == 0) throw new IllegalStateException("Key-hash table missing");
    tape.hashes = hashes.reinterpret(tape.nodeCount() * 4);
    return tape;
  }

  @Override
  public int keyMeta(long node) {
    return hashes.get(ValueLayout.JAVA_INT, node * 4);
  }

  @Override
  public boolean keyNodeEquals(long k, int meta, long header, long low, long high, boolean wide) {
    long at = k * NODE_SIZE;
    return (hashes.get(ValueLayout.JAVA_INT, k * 4) & 0x7fffffff) == meta
        && readLong(at) == header
        && readLong(at + 8) == low
        && (!wide || readLong(at + 16) == high);
  }

  /** Region 1 is the retained input of decoded documents: {data, length}. */
  public MemorySegment region(long region) {
    long length = regionLength(region);
    if (region == 0) return bytes;
    return regions.get(ValueLayout.ADDRESS, (region - 1) * REGION_SIZE).reinterpret(length);
  }

  @Override
  protected long regionLength(long region) {
    if (region == 0) return byteCount();
    if (region < 1 || region > regionCount) throw new IllegalArgumentException("Unknown text region");
    long length = regions.get(ValueLayout.JAVA_LONG, (region - 1) * REGION_SIZE + 8);
    if (length < 0) throw new IllegalArgumentException("Invalid region length");
    return length;
  }

  @Override
  protected byte readRegionByte(long region, long offset) {
    if (region == 0) return readByte(offset);
    return region(region).get(ValueLayout.JAVA_BYTE, offset);
  }

  @Override
  protected void copyRegionBytes(long region, long start, byte[] output, int offset, int len) {
    MemorySegment.copy(region(region), ValueLayout.JAVA_BYTE, start, output, offset, len);
  }

  @Override
  public int nodeInt(long byteOffset) {
    return readInt(byteOffset);
  }

  @Override
  public long nodeLong(long byteOffset) {
    return readLong(byteOffset);
  }

  @Override
  public long regionCount() {
    return regionCount;
  }

  @Override
  public long regionSize(long region) {
    return regionLength(region);
  }

  @Override
  public void copyRegion(long region, long start, byte[] output, int offset, int length) {
    copyRegionBytes(region, start, output, offset, length);
  }

  @Override
  protected int readInt(long offset) {
    return nodes.get(ValueLayout.JAVA_INT, offset);
  }

  @Override
  protected long readLong(long offset) {
    return nodes.get(ValueLayout.JAVA_LONG, offset);
  }

  @Override
  protected byte readByte(long offset) {
    return bytes.get(ValueLayout.JAVA_BYTE, offset);
  }
}
