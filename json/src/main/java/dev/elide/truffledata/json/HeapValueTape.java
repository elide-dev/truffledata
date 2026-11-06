package dev.elide.truffledata.json;

import dev.elide.myna.values.ValueTape;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/** Immutable heap copy of a tape: nodes, bytes and every region, with offsets preserved. */
final class HeapValueTape extends ValueTape implements TapeSource {
  private static final VarHandle INT =
      MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.nativeOrder());
  private static final VarHandle LONG =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());
  private final byte[] nodes;
  private final byte[] bytes;
  private final byte[][] regions;

  private HeapValueTape(byte[] nodes, byte[] bytes, byte[][] regions) {
    super(nodes.length / NODE_SIZE, bytes.length);
    this.nodes = nodes;
    this.bytes = bytes;
    this.regions = regions;
  }

  /** Copies the subtree rooted at {@code root}, re-basing subtree extents to start at node 0. */
  static HeapValueTape copy(ValueTape tape, TapeSource source, long root) {
    long end = tape.next(root);
    long count = end - root;
    byte[] nodes = new byte[Math.toIntExact(count * NODE_SIZE)];
    for (long node = root; node < end; node++) {
      long from = node * NODE_SIZE, to = (node - root) * NODE_SIZE;
      INT.set(nodes, (int) to, source.nodeInt(from));
      INT.set(nodes, (int) to + 4, source.nodeInt(from + 4));
      LONG.set(nodes, (int) to + 8, source.nodeLong(from + 8));
      long next = source.nodeLong(from + 16);
      LONG.set(nodes, (int) to + 16, tape.isInlineText(node) ? next : next - root);
    }
    byte[] bytes = new byte[Math.toIntExact(source.regionSize(0))];
    source.copyRegion(0, 0, bytes, 0, bytes.length);
    byte[][] regions = new byte[Math.toIntExact(source.regionCount())][];
    for (int i = 0; i < regions.length; i++) {
      regions[i] = new byte[Math.toIntExact(source.regionSize(i + 1))];
      source.copyRegion(i + 1, 0, regions[i], 0, regions[i].length);
    }
    return new HeapValueTape(nodes, bytes, regions);
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
    return region(region)[Math.toIntExact(offset)];
  }

  @Override
  protected void copyRegionBytes(long region, long start, byte[] output, int offset, int len) {
    System.arraycopy(region(region), Math.toIntExact(start), output, offset, len);
  }

  @Override
  protected int readInt(long offset) {
    return (int) INT.get(nodes, (int) offset);
  }

  @Override
  protected long readLong(long offset) {
    return (long) LONG.get(nodes, (int) offset);
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
    return readLong(byteOffset);
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
