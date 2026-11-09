package dev.elide.truffledata.tape;

import dev.elide.myna.values.ValueTape;

/** Detached Myna ABI v4 interchange produced explicitly by TapeOwner.toMyna(). */
final class HeapValueTape extends ValueTape {
  private final long[] nodes, directory;
  private final byte[] bytes;
  private final byte[][] regions;
  // Per-node metadata re-indexed from the compact tape's per-word table under HAS_META;
  // empty (length 0) for tapes without that flag, so readMetadata returns 0 unchanged.
  private final int[] meta;

  HeapValueTape(long[] nodes, byte[] bytes, byte[][] regions, long[] directory, int flags, int[] meta) {
    super(nodes.length / (NODE_SIZE / Long.BYTES), bytes.length, ABI_VERSION_V4, flags);
    this.nodes = nodes;
    this.bytes = bytes;
    this.regions = regions;
    this.directory = directory;
    this.meta = meta;
  }

  @Override protected long directoryCount() { return directory.length; }
  @Override protected int directoryHash(long index) { return (int) directory[Math.toIntExact(index)]; }
  @Override protected long directoryKeyOffset(long index) { return directory[Math.toIntExact(index)] >>> 32; }

  @Override protected int readMetadata(long node) {
    return meta.length == 0 ? 0 : meta[(int) node];
  }

  private byte[] region(long region) {
    if (region == 0) return bytes;
    if (region < 1 || region > regions.length) throw new IllegalArgumentException("Unknown text region");
    return regions[(int) region - 1];
  }

  @Override
  protected long regionLength(long region) {
    return region(region).length;
  }

  @Override
  protected byte readRegionByte(long region, long offset) {
    if (region == 0) return readByte(offset);
    return region(region)[Math.toIntExact(offset)];
  }

  @Override
  protected void copyRegionBytes(long region, long start, byte[] output, int offset, int len) {
    System.arraycopy(region(region), Math.toIntExact(start), output, offset, len);
  }

  @Override
  protected int readInt(long offset) {
    long word = nodes[(int) (offset >>> 3)];
    return (offset & 4) == 0 ? (int) word : (int) (word >>> 32);
  }

  @Override
  protected long readLong(long offset) {
    if ((offset & 7) != 0) throw new IllegalArgumentException("Unaligned node word");
    return nodes[(int) (offset >>> 3)];
  }

  @Override
  protected byte readByte(long offset) {
    return bytes[Math.toIntExact(offset)];
  }

}
