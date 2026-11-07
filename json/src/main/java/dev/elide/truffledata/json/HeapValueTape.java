package dev.elide.truffledata.json;

import dev.elide.myna.values.ValueTape;

/**
 * Immutable heap copy of a tape: nodes, bytes and every region, with offsets preserved. Nodes are
 * three words each ({@code tag | length << 32}, bits, next), so reads are plain array loads with
 * no VarHandle dispatch or boxing in AOT-compiled boundaries.
 */
final class HeapValueTape extends ValueTape implements TapeSource {
  private static final int WORDS = NODE_SIZE / Long.BYTES;
  private final long[] nodes;
  private final byte[] bytes;
  private final byte[][] regions;
  private final int[] meta;

  private HeapValueTape(long[] nodes, byte[] bytes, byte[][] regions, int[] meta) {
    super(nodes.length / WORDS, bytes.length);
    this.nodes = nodes;
    this.bytes = bytes;
    this.regions = regions;
    this.meta = meta;
  }

  @Override
  public int keyMeta(long node) {
    return meta[(int) node];
  }

  @Override
  public boolean keyNodeEquals(long k, int meta, long header, long low, long high, boolean wide) {
    int at = (int) k * WORDS;
    return (this.meta[(int) k] & 0x7fffffff) == meta
        && nodes[at] == header
        && nodes[at + 1] == low
        && (!wide || nodes[at + 2] == high);
  }

  /** Copies the subtree rooted at {@code root}, re-basing subtree extents to start at node 0. */
  static HeapValueTape copy(ValueTape tape, TapeSource source, long root) {
    long end = tape.next(root);
    long count = end - root;
    long[] nodes = new long[Math.toIntExact(count * WORDS)];
    int[] meta = new int[Math.toIntExact(count)];
    for (long node = root; node < end; node++) {
      long from = node * NODE_SIZE;
      int to = (int) ((node - root) * WORDS);
      nodes[to] = (source.nodeInt(from) & 0xffffffffL) | ((long) source.nodeInt(from + 4) << 32);
      nodes[to + 1] = source.nodeLong(from + 8);
      long next = source.nodeLong(from + 16);
      nodes[to + 2] = tape.isInlineText(node) ? next : next - root;
      meta[(int) (node - root)] = source.keyMeta(node);
    }
    byte[] bytes = new byte[Math.toIntExact(source.regionSize(0))];
    source.copyRegion(0, 0, bytes, 0, bytes.length);
    byte[][] regions = new byte[Math.toIntExact(source.regionCount())][];
    for (int i = 0; i < regions.length; i++) {
      regions[i] = new byte[Math.toIntExact(source.regionSize(i + 1))];
      source.copyRegion(i + 1, 0, regions[i], 0, regions[i].length);
    }
    return new HeapValueTape(nodes, bytes, regions, meta);
  }

  /** Byte of region 1 (the retained input copy), used for lexical spans. */
  byte inputByte(long offset) {
    return regions[0][Math.toIntExact(offset)];
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

  @Override
  public int nodeInt(long byteOffset) {
    return readInt(byteOffset);
  }

  @Override
  public long nodeLong(long byteOffset) {
    // Raw node words are always 8-aligned here (compiled member hints read them); the array access
    // is the bounds check, without readLong's alignment branch.
    return nodes[(int) (byteOffset >>> 3)];
  }

  @Override
  public long regionCount() {
    return regions.length;
  }

  @Override
  public long regionSize(long region) {
    return regionLength(region);
  }

  @Override
  public void copyRegion(long region, long start, byte[] output, int offset, int length) {
    copyRegionBytes(region, start, output, offset, length);
  }
}
