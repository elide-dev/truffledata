package dev.elide.truffledata.tape;

import dev.elide.myna.values.ValueTape;
import org.jspecify.annotations.Nullable;

/**
 * Immutable heap copy of a tape: nodes, bytes, every region and the Myna v4 object directory, with
 * offsets preserved. Nodes are three words each ({@code tag | length << 32}, bits, next), and a
 * directory entry is one word ({@code hash | key offset << 32}), so reads are plain array loads
 * with no VarHandle dispatch or boxing in AOT-compiled boundaries.
 */
final class HeapValueTape extends ValueTape implements TapeSource {
  private static final int WORDS = NODE_SIZE / Long.BYTES;
  private static final long[] NO_DIRECTORY = new long[0];
  private final long[] nodes;
  private final byte[] bytes;
  private final byte[][] regions;
  private final int[] meta;
  private final long[] directory;

  // Package-private for tests that build ABI v3 tapes by hand.
  HeapValueTape(long[] nodes, byte[] bytes, byte[][] regions, int[] meta) {
    this(nodes, bytes, regions, meta, null);
  }

  /** An ABI v4 tape when {@code directory} is given (possibly empty), else v3. */
  HeapValueTape(long[] nodes, byte[] bytes, byte[][] regions, int[] meta, long @Nullable [] directory) {
    super(nodes.length / WORDS, bytes.length, directory == null ? ABI_VERSION : ABI_VERSION_V4);
    this.nodes = nodes;
    this.bytes = bytes;
    this.regions = regions;
    this.meta = meta;
    this.directory = directory == null ? NO_DIRECTORY : directory;
  }

  @Override
  public int keyMeta(long node) {
    return meta[(int) node];
  }

  // TapeSource's directory access goes through Myna's reader hooks, so both serve one layout.
  @Override
  public long directorySize() {
    return directoryCount();
  }

  @Override
  public long directoryEntry(long index) {
    return (directoryHash(index) & 0xffffffffL) | (directoryKeyOffset(index) << 32);
  }

  @Override
  protected long directoryCount() {
    return directory.length;
  }

  @Override
  protected int directoryHash(long index) {
    return (int) directory[Math.toIntExact(index)];
  }

  @Override
  protected long directoryKeyOffset(long index) {
    return directory[Math.toIntExact(index)] >>> 32;
  }

  @Override
  public long directoryKey(long object, int rank, int hash, long header, long low, long high, boolean wide) {
    int at = (int) object * WORDS;
    long word = nodes[at];
    long payload = nodes[at + 1];
    if ((int) word != OBJECT || rank >= (word >>> 32) || payload == 0) return -1;
    long index = payload - 1 + rank;
    if (index >= directory.length) return -1;
    long entry = directory[(int) index];
    if ((int) entry != hash) return -1;
    long k = object + (entry >>> 32);
    if (k >= nodes.length / WORDS) return -1;
    int key = (int) k * WORDS;
    return nodes[key] == header && nodes[key + 1] == low && (!wide || nodes[key + 2] == high) ? k : -1;
  }

  /**
   * Copies the subtree rooted at {@code root}, re-basing subtree extents to start at node 0. Object
   * directories close in post order, so the subtree's directories are one contiguous range of the
   * source directory: it is copied and the subtree's object payloads are re-based to it.
   */
  static HeapValueTape copy(ValueTape tape, TapeSource source, long root) {
    long end = tape.next(root);
    long count = end - root;
    long[] nodes = new long[Math.toIntExact(count * WORDS)];
    int[] meta = new int[Math.toIntExact(count)];
    long first = Long.MAX_VALUE, last = 0;
    for (long node = root; node < end; node++) {
      long from = node * NODE_SIZE;
      int to = (int) ((node - root) * WORDS);
      long word = (source.nodeInt(from) & 0xffffffffL) | ((long) source.nodeInt(from + 4) << 32);
      nodes[to] = word;
      long payload = source.nodeLong(from + 8);
      nodes[to + 1] = payload;
      long next = source.nodeLong(from + 16);
      nodes[to + 2] = tape.isInlineText(node) ? next : next - root;
      meta[(int) (node - root)] = source.keyMeta(node);
      if ((int) word == OBJECT && payload != 0) {
        first = Math.min(first, payload - 1);
        last = Math.max(last, payload - 1 + (word >>> 32));
      }
    }
    long[] directory = null;
    if (tape.version() == ABI_VERSION_V4) {
      long size = source.directorySize();
      if (first == Long.MAX_VALUE) first = last = 0;
      if (last > size) throw new IllegalArgumentException("Invalid directory extent");
      directory = new long[Math.toIntExact(last - first)];
      for (int i = 0; i < directory.length; i++) directory[i] = source.directoryEntry(first + i);
      for (int at = 0; at < nodes.length; at += WORDS)
        if ((int) nodes[at] == OBJECT && nodes[at + 1] != 0) nodes[at + 1] -= first;
    }
    byte[] bytes = new byte[Math.toIntExact(source.regionSize(0))];
    source.copyRegion(0, 0, bytes, 0, bytes.length);
    byte[][] regions = new byte[Math.toIntExact(source.regionCount())][];
    for (int i = 0; i < regions.length; i++) {
      regions[i] = new byte[Math.toIntExact(source.regionSize(i + 1))];
      source.copyRegion(i + 1, 0, regions[i], 0, regions[i].length);
    }
    return new HeapValueTape(nodes, bytes, regions, meta, directory);
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
    // Raw node words are always 8-aligned here; the array access is the bounds check, without
    // readLong's alignment branch.
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
