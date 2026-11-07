package dev.elide.truffledata.json;

import dev.elide.myna.values.NativeValueTape;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/** Static-image tape exposing the copy hooks snapshots need. */
final class SvmValueTape extends NativeValueTape implements TapeSource {
  private final long regions;
  // One int per node: the native key-hash side table (JsonResult.key_hashes).
  private final Pointer hashes;

  SvmValueTape(Pointer descriptor, Pointer hashes) {
    super(descriptor);
    regions = descriptor.readLong(48);
    this.hashes = hashes;
  }

  @Override
  public int keyMeta(long node) {
    return hashes.readInt(WordFactory.signed(node * 4));
  }

  @Override
  public boolean keyNodeEquals(long k, int meta, long header, long low, long high, boolean wide) {
    long at = k * NODE_SIZE;
    return (hashes.readInt(WordFactory.signed(k * 4)) & 0x7fffffff) == meta
        && readLong(at) == header
        && readLong(at + 8) == low
        && (!wide || readLong(at + 16) == high);
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
    return regions;
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
